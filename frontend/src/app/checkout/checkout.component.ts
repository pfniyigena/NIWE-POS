import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OrderApiService, CheckoutPaymentEntry } from '../core/api/order-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { CreditSummary, Order, PaymentMethod, PaymentMethodOption } from '../core/models';
import { HotelApiService, InHouseGuest } from '../core/api/hotel-api.service';

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
  imports: [CommonModule, FormsModule, RouterLink, DatePickerComponent],
  template: `
    <!-- Fourier: "Checkout need to be also mobile responsive" — the outer padding and the card's
         own fixed width used to be inline styles; moved into checkout-shell/checkout-card so the
         mobile media query in styles.scss can override them with normal stylesheet specificity
         (same approach as the POS terminal's own cart panel just above). Desktop is unchanged —
         the base (non-media) rules carry the exact same 2rem/720px/1.5rem values as before. -->
    <div class="flex-col align-center checkout-shell">
      <div class="card checkout-card" *ngIf="order as o">
        <h3 class="mb-1">Checkout</h3>
        <p class="text-muted">{{ o.id }}</p>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <div class="flex gap-3" style="align-items:flex-start; flex-wrap:wrap;">
          <!-- Task: "Payment mode on left side, totals on right side" -->
          <div style="flex:1 1 60%; min-width:280px;">
            <h6 class="mb-2">Payment (tap a method to enter an amount — split across several if needed)</h6>
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
                placeholder="Amount"
                [ngModel]="row.amount"
                (ngModelChange)="onAmountChange(row, $event)"
                [disabled]="!row.selected"
                [hidden]="!row.selected"
              />
            </div>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && !order?.customerId">
              Select a customer on this sale before paying with store credit.
            </p>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && creditSummary && !creditSummary.creditEnabled">
              This customer is not enabled for credit — see Admin &gt; Customers.
            </p>
            <p class="text-muted" style="font-size:0.85rem;" *ngIf="creditRowSelected() && creditSummary?.creditEnabled">
              Available credit: {{ creditSummary?.availableCredit | number: '1.2-2' }}
            </p>
            <div class="form-group mb-2" *ngIf="roomRowSelected()">
              <label class="form-label">Charge to which room?</label>
              <select class="form-control" name="roomBooking" [(ngModel)]="roomBookingId">
                <option [ngValue]="null">Choose the guest…</option>
                <option *ngFor="let g of inHouse" [ngValue]="g.bookingId">Room {{ g.roomNumber }} — {{ g.guestName }}</option>
              </select>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="inHouse.length === 0">No guest is checked in right now.</div>
            </div>
            <div class="form-group mb-2" *ngIf="creditRowSelected() && creditSummary?.creditEnabled">
              <label class="form-label">Due date (optional — defaults to this customer's own payment term, if any)</label>
              <app-date-picker [(ngModel)]="dueDate" ></app-date-picker>
            </div>
          </div>

          <div style="flex:1 1 32%; min-width:220px;">
            <table class="table-sm mb-3">
              <tbody>
                <tr>
                  <td>Subtotal</td>
                  <td class="text-end">{{ o.subtotal | number: '1.2-2' }}</td>
                </tr>
                <tr>
                  <td>Tax</td>
                  <td class="text-end">{{ o.taxTotal | number: '1.2-2' }}</td>
                </tr>
                <tr style="font-weight:700; font-size:1.1rem;">
                  <td>Total due</td>
                  <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
                </tr>
              </tbody>
            </table>

            <p class="text-muted" style="font-size:0.85rem;" *ngIf="!o.customerId">
              This sale has no customer — go back to the cart and select or add one before checking out.
            </p>
            <button class="btn btn-success btn-block mb-2" (click)="submit()" [disabled]="submitting || !o.customerId">
              {{ submitting ? 'Processing…' : 'Complete sale' }}
            </button>
            <a class="btn btn-link btn-block" [routerLink]="['/pos', o.sessionId]">Back to cart</a>
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderApi: OrderApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private customerCreditApi: CustomerCreditApiService,
    private hotelApi: HotelApiService
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
    const othersSum = this.paymentRows
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
