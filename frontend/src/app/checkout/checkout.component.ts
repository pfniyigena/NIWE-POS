import { Component, OnInit, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ModuleApiService } from '../core/api/module-api.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OrderApiService, CheckoutPaymentEntry } from '../core/api/order-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { CreditSummary, Order, PaymentMethod, PaymentMethodOption } from '../core/models';
import { HotelApiService, InHouseGuest } from '../core/api/hotel-api.service';
import { GiftCardRow, GiftCardsApiService } from '../core/api/giftcards-api.service';
import { LoyaltyApiService, LoyaltyBalance } from '../core/api/loyalty-api.service';

/** A gift card put on this sale: the card and how much of it is being spent. */
interface CardPayment {
  code: string;
  maskedCode: string;
  balance: number;
  amount: number;
}

/** One row per active payment method — see the class doc comment for how `selected` drives the UI. */
interface PaymentRow {
  method: PaymentMethod;
  name: string;
  selected: boolean;
  amount: number | null;
  /** True for the (normally single) method flagged store-credit — see PaymentMethodOption.credit. Drives the availableCredit cap and the due-date input. */
  credit: boolean;
  /** Hotel module: this method charges the amount to an in-house guest's room bill. */
  roomCharge: boolean;
}

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, DatePickerComponent],
  template: `
    <!-- Fourier: "Checkout need to be also mobile responsive" — the outer padding and the card's
         own fixed width used to be inline styles; moved into checkout-shell/checkout-card so the
         mobile media query in styles.scss can override them with normal stylesheet specificity
         (same approach as the POS terminal's own cart panel just above). Desktop is unchanged —
         the base (non-media) rules carry the exact same 2rem/720px/1.5rem values as before. -->
    <div class="flex-col align-center checkout-shell">
      <div class="card checkout-card" *ngIf="order as o">
        <h3 class="mb-1">{{ 'Checkout' | translate }}</h3>
        <p class="text-muted">{{ o.id }}</p>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <div class="flex gap-3" style="align-items:flex-start; flex-wrap:wrap;">
          <!-- Task: "Payment mode on left side, totals on right side" -->
          <div style="flex:1 1 60%; min-width:280px;">
            <h6 class="mb-2">{{ 'Payment (tap a method to enter an amount — split across several if needed)' | translate }}</h6>
            <!-- Task: "Replace Payment method label before input with the payment method button
                 (each payment method should be on the same line with its input)" — one row per
                 method, always rendered; the button both toggles selection and replaces the old
                 plain-text label, and the input next to it stays hidden until that row is selected
                 (Task, earlier pass: "Input for payment mode should be hidden and displayed once I
                 press on payment mode"). -->
            <div class="flex gap-2 mb-2" style="align-items:center; flex-wrap:wrap;" *ngFor="let row of paymentRows">
              <button
                type="button"
                class="btn"
                style="width:110px; flex-shrink:0;"
                [class.btn-primary]="row.selected"
                [class.btn-outline-secondary]="!row.selected"
                [disabled]="creditDisabled(row)"
                [title]="creditDisabled(row) ? 'Select a customer enabled for credit before using this payment method' : ''"
                (click)="togglePaymentMethod(row)"
              >
                {{ row.name }}
              </button>
              <input
                class="form-control"
                type="number"
                step="0.01"
                min="0"
                [max]="maxAmountFor(row)"
                [placeholder]="'Amount' | translate"
                [ngModel]="row.amount"
                (ngModelChange)="onAmountChange(row, $event)"
                [disabled]="!row.selected"
                [hidden]="!row.selected"
              />
            </div>
            <div class="mb-3 gc-box" *ngIf="features.feature('giftCards')" style="border-top:1px solid var(--border, #ddd); padding-top:0.75rem;">
              <h6 class="mb-2">{{ 'Gift card' | translate }}</h6>
              <div class="flex gap-2 mb-2" style="align-items:center; flex-wrap:wrap;">
                <input class="form-control gc-code" style="max-width:240px;" [placeholder]="'Gift card code' | translate" name="gcCode" [(ngModel)]="gcCode" (keyup.enter)="checkCard()" />
                <button type="button" class="btn btn-outline-secondary gc-check" (click)="checkCard()">{{ 'Check balance' | translate }}</button>
              </div>
              <div class="alert alert-danger gc-error" *ngIf="gcError">{{ gcError }}</div>
              <div class="flex gap-2 mb-2 gc-found" style="align-items:center; flex-wrap:wrap;" *ngIf="gcFound">
                <span>{{ 'Balance' | translate }} <strong class="gc-balance">{{ gcFound.balance | number: '1.2-2' }}</strong><span *ngIf="gcFound.expiresOn" class="text-muted"> {{ '· expires {p0}' | translate: { p0: (gcFound.expiresOn) } }}</span></span>
                <input class="form-control gc-amount" style="max-width:140px;" type="number" step="0.01" min="0" name="gcAmount" [(ngModel)]="gcAmount" />
                <button type="button" class="btn btn-primary gc-add" (click)="addCard()">{{ 'Use on this sale' | translate }}</button>
              </div>
              <div class="flex gap-2 mb-1 gc-applied" style="align-items:center;" *ngFor="let g of giftCards">
                <span>{{ g.maskedCode }}: <strong>{{ g.amount | number: '1.2-2' }}</strong></span>
                <button type="button" class="btn btn-sm btn-outline-danger gc-remove" (click)="removeCard(g)">{{ 'Remove' | translate }}</button>
              </div>
            </div>
            <div class="mb-3 lp-box" style="border-top:1px solid var(--border, #ddd); padding-top:0.75rem;" *ngIf="loyalty && loyalty.enabled && features.feature('loyalty')">
              <h6 class="mb-2">{{ 'Loyalty points' | translate }}</h6>
              <p class="mb-2" style="font-size:0.9rem;">
                <strong class="lp-balance">{{ loyalty.points }}</strong> {{ 'points (worth {p0}).' | translate: { p0: (loyalty.value | number: '1.2-2') } }}
                <span class="text-muted" *ngIf="maxPoints() > 0 || pointsApplied > 0">{{ 'Up to' | translate }} <span class="lp-max">{{ maxPoints() }}</span> {{ 'can be used on this sale' | translate }}<span *ngIf="loyalty.minRedeemPoints"> {{ '(at least {p0})' | translate: { p0: (loyalty.minRedeemPoints) } }}</span>.</span>
              </p>
              <p class="text-muted lp-none" style="font-size:0.85rem;" *ngIf="pointsApplied === 0 && maxPoints() === 0">{{ 'Points cannot pay for any of this sale (too small, or below the least that can be used).' | translate }}</p>
              <div class="flex gap-2 mb-2" style="align-items:center; flex-wrap:wrap;" *ngIf="pointsApplied === 0 && maxPoints() > 0">
                <input class="form-control lp-points" style="max-width:140px;" type="number" step="1" min="0" name="pointsToUse" [placeholder]="'Points' | translate" [(ngModel)]="pointsToUse" />
                <button type="button" class="btn btn-primary lp-use" (click)="usePoints()">{{ 'Use points' | translate }}</button>
              </div>
              <div class="flex gap-2 mb-1 lp-applied" style="align-items:center;" *ngIf="pointsApplied > 0">
                <span><strong>{{ pointsApplied }}</strong> {{ 'points =' | translate }} <strong>{{ pointsValue | number: '1.2-2' }}</strong></span>
                <button type="button" class="btn btn-sm btn-outline-danger lp-remove" (click)="removePoints()">{{ 'Remove' | translate }}</button>
              </div>
              <div class="alert alert-danger lp-error" *ngIf="lpError">{{ lpError }}</div>
            </div>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && !order?.customerId">
              {{ 'Select a customer on this sale before paying with store credit.' | translate }}
            </p>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && creditSummary && !creditSummary.creditEnabled">
              This customer is not enabled for credit — see Admin &gt; Customers.
            </p>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && creditSummary?.creditEnabled">
              {{ 'Available credit: {p0}' | translate: { p0: (creditSummary?.availableCredit | number: '1.2-2') } }}
            </p>
            <div class="form-group mb-2" *ngIf="roomRowSelected()">
              <label class="form-label">{{ 'Charge to which room?' | translate }}</label>
              <select class="form-control" name="roomBooking" [(ngModel)]="roomBookingId">
                <option [ngValue]="null">{{ 'Choose the guest…' | translate }}</option>
                <option *ngFor="let g of inHouse" [ngValue]="g.bookingId">{{ 'Room {p0} — {p1}' | translate: { p0: (g.roomNumber), p1: (g.guestName) } }}</option>
              </select>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="inHouse.length === 0">{{ 'No guest is checked in right now.' | translate }}</div>
            </div>
            <div class="form-group mb-2" *ngIf="creditRowSelected() && creditSummary?.creditEnabled">
              <label class="form-label">{{ 'Due date (optional — defaults to this customer\\'s own payment term, if any)' | translate }}</label>
              <app-date-picker [(ngModel)]="dueDate" ></app-date-picker>
            </div>
          </div>

          <div style="flex:1 1 32%; min-width:220px;">
            <table class="table-sm mb-3">
              <tbody>
                <tr>
                  <td>{{ 'Subtotal' | translate }}</td>
                  <td class="text-end">{{ o.subtotal | number: '1.2-2' }}</td>
                </tr>
                <tr>
                  <td>{{ 'Tax' | translate }}</td>
                  <td class="text-end">{{ o.taxTotal | number: '1.2-2' }}</td>
                </tr>
                <tr *ngIf="o.promoSavings && o.promoSavings > 0" class="checkout-promo-saved" style="color:var(--success, #1a7f37);">
                  <td>{{ 'Saved with promotions' | translate }}</td>
                  <td class="text-end">{{ o.promoSavings | number: '1.2-2' }}</td>
                </tr>
                <tr style="font-weight:700; font-size:1.1rem;">
                  <td>{{ 'Total due' | translate }}</td>
                  <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
                </tr>
              </tbody>
            </table>

            <p class="text-muted" style="font-size:0.85rem;" *ngIf="!o.customerId">
              {{ 'This sale has no customer — go back to the cart and select or add one before checking out.' | translate }}
            </p>
            <button class="btn btn-success btn-block mb-2" (click)="submit()" [disabled]="submitting || !o.customerId">
              {{ submitting ? ('Processing…' | translate) : ('Complete sale' | translate) }}
            </button>
            <a class="btn btn-link btn-block" [routerLink]="['/pos', o.sessionId]">{{ 'Back to cart' | translate }}</a>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class CheckoutComponent implements OnInit {
  orderId!: number;
  order: Order | null = null;
  error: string | null = null;
  submitting = false;

  paymentMethods: PaymentMethodOption[] = [];

  /** One row per active payment method (rebuilt once methods load — see ngOnInit). */
  paymentRows: PaymentRow[] = [];

  /** Task: "Customer balance on POS screen" (checkout side) — fetched once the order's customer is known, caps the credit row's max. */
  creditSummary: CreditSummary | null = null;
  /** Task: "Optional due date/payment terms" — only sent to checkout when a credit row is selected. */
  dueDate: string | null = null;
  /** Hotel module: the in-house guests a "Charge to room" payment can go to, and the one chosen. */
  inHouse: InHouseGuest[] = [];
  roomBookingId: number | null = null;

  /** Gift cards: the code being typed, the card found, the amount to take from it, and the cards already put on this sale. */
  gcCode = '';
  gcFound: GiftCardRow | null = null;
  gcAmount: number | null = null;
  gcError: string | null = null;
  giftCards: CardPayment[] = [];
  /** Loyalty points: the customer's balance and rules, the points typed, and the points put on this sale. */
  loyalty: LoyaltyBalance | null = null;
  readonly features = inject(ModuleApiService);
  pointsToUse: number | null = null;
  pointsApplied = 0;
  pointsValue = 0;
  lpError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderApi: OrderApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private customerCreditApi: CustomerCreditApiService,
    private hotelApi: HotelApiService,
    private giftCardsApi: GiftCardsApiService,
    private loyaltyApi: LoyaltyApiService
  ) {}

  ngOnInit(): void {
    this.orderId = Number(this.route.snapshot.paramMap.get('orderId'));
    this.orderApi.getOrder(this.orderId).subscribe((o) => {
      this.order = o;
      // Default the selected method's row to the full amount due, once methods have loaded.
      const defaultRow = this.paymentRows.find((r) => r.selected);
      if (defaultRow) {
        defaultRow.amount = o.total;
      }
      if (o.customerId) {
        this.customerCreditApi.creditSummary(o.customerId).subscribe((s) => (this.creditSummary = s));
        this.loyaltyApi.balance(o.customerId).subscribe({ next: (b) => (this.loyalty = b), error: () => (this.loyalty = null) });
      }
    });
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      // Task: "by default cash is selected" — default to whichever method is flagged cash-equivalent
      // (PaymentMethodOption.cash), falling back to the first method if none is flagged cash (e.g.
      // an admin deactivated/renamed away the seeded CASH method).
      const defaultIndex = Math.max(0, methods.findIndex((m) => m.cash));
      this.paymentRows = methods.map((m, i) => ({
        method: m.code,
        name: m.name,
        selected: i === defaultIndex,
        amount: i === defaultIndex ? (this.order?.total ?? null) : null,
        credit: m.credit,
        roomCharge: !!m.roomCharge,
      }));
      if (this.paymentRows.some((r) => r.roomCharge)) {
        this.hotelApi.inHouseGuests().subscribe({ next: (g) => (this.inHouse = g), error: () => (this.inHouse = []) });
      }
    });
  }

  roomRowSelected(): boolean {
    return this.paymentRows.some((r) => r.selected && r.roomCharge);
  }

  creditRowSelected(): boolean {
    return this.paymentRows.some((r) => r.selected && r.credit);
  }

  /**
   * Fourier's own request: "If the customer does not signed Credit then the credit is desabled on
   * checkout" — the credit row's button itself is now disabled (not just capped to a 0 amount via
   * maxAmountFor, which was the only gate before this) whenever there's no customer on the sale at
   * all, or the selected customer's own `creditEnabled` flag is off (see Customer.creditEnabled's
   * javadoc — ADMIN-only to turn on, Task 3/CustomerController). Non-credit rows are never
   * affected. `creditSummary` is only populated once the order's customer is known and the credit
   * summary GET resolves, so this also reads as disabled during that brief load — same "nothing to
   * offer yet" state `maxAmountFor`/the explanatory `<p>`s below already treat identically.
   */
  creditDisabled(row: PaymentRow): boolean {
    return row.credit && (!this.order?.customerId || !this.creditSummary?.creditEnabled);
  }

  /** Tapping a method button toggles it selected — its amount input only becomes enabled/visible
   * once selected. Defaults to whatever's still owed (not the full total), so turning on a second
   * method after the first already covers the sale doesn't pre-fill an amount that would overpay. */
  togglePaymentMethod(row: PaymentRow): void {
    if (this.creditDisabled(row)) return;
    row.selected = !row.selected;
    if (row.selected && row.amount == null) {
      row.amount = this.maxAmountFor(row);
    }
  }

  /** How much more a row could take without the sum of all selected rows exceeding the order
   * total — every other selected row's amount counts against it, this row's own doesn't (Task:
   * "if the total amount to pay is already input do not allow input extra amount"). */
  maxAmountFor(row: PaymentRow): number {
    const total = this.order?.total ?? 0;
    const othersSum = this.specialSum() + this.paymentRows
      .filter((r) => r.selected && r !== row)
      .reduce((sum, r) => sum + (r.amount ?? 0), 0);
    let max = Math.max(0, Number((total - othersSum).toFixed(2)));
    // Task: "Credit limit enforcement" (checkout side) — a credit row can never take more than
    // this customer's currently available credit, on top of the usual "can't exceed the total due" cap.
    if (row.credit) {
      if (!this.order?.customerId || !this.creditSummary?.creditEnabled) {
        return 0;
      }
      max = Math.min(max, this.creditSummary.availableCredit);
    }
    return max;
  }

  /** Clamps a typed amount to `maxAmountFor` so the total entered across every selected method can
   * never exceed the order total — once other methods already cover the sale, this row is capped
   * at 0 and no extra amount can be typed in. */
  onAmountChange(row: PaymentRow, value: number | null): void {
    if (value == null || (value as unknown as string) === '' || isNaN(value)) {
      row.amount = null;
      return;
    }
    row.amount = Math.min(value, this.maxAmountFor(row));
  }

  // ---- gift cards and loyalty points: they are taken off the total first, then the other methods cover the rest ----

  private round2(v: number): number {
    return Math.round(v * 100) / 100;
  }

  giftSum(): number {
    return this.giftCards.reduce((sum, g) => sum + g.amount, 0);
  }

  /** Everything the gift cards and points on this sale will pay. */
  specialSum(): number {
    return this.round2(this.giftSum() + this.pointsValue);
  }

  /** Cuts the amounts typed for the ordinary methods down so that, with the cards and points, they never pay more than the total; gives the first method the rest again when cards or points come off. */
  private rebalance(grow: boolean): void {
    for (const row of this.paymentRows) {
      if (!row.selected || row.amount == null) continue;
      const max = this.maxAmountFor(row);
      if (row.amount > max) row.amount = max > 0 ? max : null;
    }
    if (grow) {
      const first = this.paymentRows.find((r) => r.selected && !r.credit && !r.roomCharge);
      if (first) first.amount = this.maxAmountFor(first) || null;
    }
  }

  checkCard(): void {
    this.gcError = null;
    this.gcFound = null;
    const code = this.gcCode.trim();
    if (!code) {
      this.gcError = "Enter the gift card's code.";
      return;
    }
    this.giftCardsApi.lookup(code).subscribe({
      next: (c) => {
        if (c.status === 'BLOCKED') {
          this.gcError = 'This gift card is blocked.';
        } else if (c.expired) {
          this.gcError = 'This gift card expired on ' + c.expiresOn + '.';
        } else if (c.balance <= 0) {
          this.gcError = 'This gift card has nothing left on it.';
        } else {
          this.gcFound = c;
          const room = this.round2(Math.max(0, (this.order?.total ?? 0) - this.specialSum()));
          this.gcAmount = Math.min(c.balance, room);
        }
      },
      error: (err) => (this.gcError = err?.error?.message ?? 'Could not look the card up'),
    });
  }

  addCard(): void {
    this.gcError = null;
    const c = this.gcFound;
    if (!c) return;
    if (this.giftCards.some((g) => g.code === c.code)) {
      this.gcError = 'This card is already on the sale.';
      return;
    }
    const amount = this.round2(Number(this.gcAmount) || 0);
    const room = this.round2(Math.max(0, (this.order?.total ?? 0) - this.specialSum()));
    if (amount <= 0) {
      this.gcError = 'Enter the amount to take from the card.';
      return;
    }
    if (amount > c.balance) {
      this.gcError = 'The card only has ' + c.balance + ' left.';
      return;
    }
    if (amount > room) {
      this.gcError = 'Only ' + room + ' of this sale is still to be paid.';
      return;
    }
    this.giftCards = [...this.giftCards, { code: c.code, maskedCode: '****' + c.code.slice(-4), balance: c.balance, amount }];
    this.gcFound = null;
    this.gcCode = '';
    this.gcAmount = null;
    this.rebalance(false);
  }

  removeCard(g: CardPayment): void {
    this.giftCards = this.giftCards.filter((x) => x !== g);
    this.rebalance(true);
  }

  /** The most points that can go on this sale: what the customer has, the share of a sale points may pay, and what the gift cards left unpaid. */
  maxPoints(): number {
    if (!this.loyalty || !this.order || this.loyalty.pointValue <= 0) return 0;
    const cap = Math.floor((this.order.total * this.loyalty.maxRedeemPercent) / 100 * 100) / 100;
    const room = Math.max(0, this.order.total - this.giftSum());
    const money = Math.min(cap, room);
    const most = Math.max(0, Math.min(this.loyalty.points, Math.floor(money / this.loyalty.pointValue + 1e-9)));
    return most < this.loyalty.minRedeemPoints ? 0 : most;
  }

  usePoints(): void {
    this.lpError = null;
    const l = this.loyalty;
    if (!l) return;
    const points = Math.floor(Number(this.pointsToUse) || 0);
    if (points <= 0) {
      this.lpError = 'Enter the number of points to use.';
      return;
    }
    if (points > l.points) {
      this.lpError = 'The customer only has ' + l.points + ' points.';
      return;
    }
    if (points < l.minRedeemPoints) {
      this.lpError = 'At least ' + l.minRedeemPoints + ' points must be used at a time.';
      return;
    }
    if (points > this.maxPoints()) {
      this.lpError = 'At most ' + this.maxPoints() + ' points can be used on this sale.';
      return;
    }
    this.pointsApplied = points;
    this.pointsValue = this.round2(points * l.pointValue);
    this.pointsToUse = null;
    this.rebalance(false);
  }

  removePoints(): void {
    this.pointsApplied = 0;
    this.pointsValue = 0;
    this.lpError = null;
    this.rebalance(true);
  }

  submit(): void {
    if (!this.order) return;
    // Fourier: "Never allow any sale transaction even into POS without Customer information" —
    // the terminal's cart already defaults every order to the seeded Walk-in Customer (see
    // TerminalComponent.applyDefaultCustomer), so this should never actually trip in normal use;
    // it's a clear client-side message for the one edge case it could (e.g. this order's
    // setCustomer call never landed) rather than letting the request reach the backend and come
    // back as a raw error — the backend enforces this too either way (OrderService.checkout).
    if (!this.order.customerId) {
      this.error = 'This sale has no customer — go back to the cart and select or add a customer before checking out.';
      return;
    }
    const payments: CheckoutPaymentEntry[] = this.paymentRows
      .filter((r) => r.selected && r.amount && r.amount > 0)
      .map((r) => ({ method: r.method, amount: r.amount as number }));
    for (const g of this.giftCards) {
      payments.push({ method: 'GIFT_CARD', amount: g.amount, giftCardCode: g.code });
    }
    if (this.pointsApplied > 0) {
      payments.push({ method: 'LOYALTY', amount: this.pointsValue, points: this.pointsApplied });
    }

    if (payments.length === 0) {
      this.error = 'Select a payment method and enter an amount';
      return;
    }

    if (this.roomRowSelected() && !this.roomBookingId) {
      this.error = 'Choose the guest whose room this is charged to.';
      return;
    }
    this.error = null;
    this.submitting = true;
    const dueDate = this.creditRowSelected() ? this.dueDate : null;
    this.orderApi.checkout(this.orderId, payments, dueDate, this.roomRowSelected() ? this.roomBookingId : null).subscribe({
      next: () => {
        this.submitting = false;
        // Task: "Complete sale should automatically open the print dialog, then return to the
        // terminal once that's dismissed" — the `autoPrint` query param tells the receipt screen
        // this is a fresh checkout (not someone reprinting from the Receipts list), so it should
        // trigger the browser's print dialog on its own and navigate back once it closes.
        this.router.navigate(['/receipt', this.orderId], { queryParams: { autoPrint: '1' } });
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Checkout failed';
      },
    });
  }
}
