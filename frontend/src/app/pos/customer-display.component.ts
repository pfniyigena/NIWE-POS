import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { OrderApiService } from '../core/api/order-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { Order, StoreSettings } from '../core/models';

/**
 * A second, read-only, customer-facing screen (Task: "Add customer-facing double screen") —
 * opened from the terminal's **📺 Customer display** button, either as a second window dragged
 * onto a second monitor on the same machine, or as this same URL opened on entirely separate
 * hardware (a tablet, a small screen on the counter, whatever the store has). Either way this
 * component never talks to the terminal directly — there's no shared in-memory state to reach
 * across a window/device boundary — it only ever polls the backend, the one thing guaranteed to
 * be reachable from anywhere: `CashierSessionApiService.getById` for which order is currently on
 * screen (`activeOrderId`, kept up to date by the terminal — see TerminalComponent.pushActiveOrder),
 * then `OrderApiService.getOrder` for that order's own live detail.
 *
 * **Login requirement, not a new "public kiosk" access mode**: `GET /api/orders/{id}` is
 * ownership-or-admin gated while an order is still OPEN (see the backend's
 * `OrderService.requireOwnCashierSessionOrAdmin`, from the seventh pass) — that rule is
 * deliberately left untouched here rather than carving out a new unauthenticated read path just
 * for this screen. In practice that means: whoever sets up a customer display on separate
 * hardware logs into it with the *same* staff account working that till (or an admin account) —
 * the same way "Switch cashier"/PIN-gated terminal access already work in this app. A device
 * logged in as a different cashier gets a plain 403 (see the error view below) rather than
 * silently showing someone else's cart.
 *
 * **Polling, not push**: this app has no websocket/SSE infrastructure anywhere else, so this
 * screen just re-fetches on a short interval — the same "poll the existing REST endpoint" choice
 * this codebase already leans on for everything else (see `receipt.component.ts`'s afterprint
 * navigation for the one place anything here approaches "real-time", which is a DOM event, not a
 * network push). Good enough for a customer display, where a couple of seconds of staleness after
 * an item is scanned is unnoticeable.
 *
 * **No "change due"**: checkout in this app has never allowed a payment to add up to more than
 * the order total (see the sixteenth pass's amount cap) — there is no overpayment, so there is no
 * change to compute or show. The "payment step" this screen offers instead is the completed
 * order's actual payment breakdown (method + amount per row), which is the honest equivalent of
 * "what did the customer tender" in a checkout flow that never produces change.
 */
@Component({
  selector: 'app-customer-display',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cd-root">
      <div class="cd-brand" *ngIf="view !== 'error'">
        <img *ngIf="settings?.logoDataUrl" [src]="settings!.logoDataUrl" alt="" class="cd-logo" />
        <span class="cd-brand-name">{{ settings?.businessName || 'POS' }}</span>
      </div>

      <div class="cd-center" *ngIf="view === 'idle'">
        <img *ngIf="settings?.logoDataUrl" [src]="settings!.logoDataUrl" alt="" class="cd-logo-big" />
        <h1>Welcome{{ settings?.businessName ? ' to ' + settings?.businessName : '' }}!</h1>
        <p class="cd-subtitle">We'll show your order here as it's rung up.</p>
      </div>

      <div class="cd-cart" *ngIf="view === 'cart' && order as o">
        <div class="cd-lines">
          <div class="cd-line" *ngFor="let l of o.lines">
            <div class="cd-line-desc">
              {{ l.description }}
              <span class="cd-line-qty" *ngIf="l.quantity !== 1">&times; {{ l.quantity }}</span>
            </div>
            <div class="cd-line-total">{{ l.lineTotal | number: '1.2-2' }}</div>
          </div>
          <div class="cd-empty" *ngIf="o.lines.length === 0">Your items will appear here.</div>
        </div>
        <div class="cd-totals">
          <div class="cd-subrow">
            <span>Subtotal</span>
            <span>{{ o.subtotal | number: '1.2-2' }}</span>
          </div>
          <div class="cd-subrow">
            <span>Tax</span>
            <span>{{ o.taxTotal | number: '1.2-2' }}</span>
          </div>
          <div class="cd-total-due">
            <span>Total Due</span>
            <span>{{ o.total | number: '1.2-2' }}</span>
          </div>
        </div>
      </div>

      <div class="cd-center" *ngIf="view === 'thankyou' && order as o">
        <h1>Thank you!</h1>
        <div class="cd-paid">{{ o.total | number: '1.2-2' }}</div>
        <div class="cd-payment-row" *ngFor="let p of o.payments">
          <span>{{ p.method }}</span>
          <span>{{ p.amount | number: '1.2-2' }}</span>
        </div>
      </div>

      <div class="cd-center" *ngIf="view === 'error'">
        <h1>Display unavailable</h1>
        <p class="cd-subtitle">{{ errorMessage }}</p>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .cd-root {
        min-height: 100vh;
        background: #0c0e14;
        color: #f4f5f8;
        font-family: system-ui, -apple-system, sans-serif;
        display: flex;
        flex-direction: column;
        padding: 2rem 3rem;
        box-sizing: border-box;
      }
      .cd-brand {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 1.5rem;
      }
      .cd-logo {
        height: 40px;
        max-width: 160px;
        object-fit: contain;
      }
      .cd-brand-name {
        font-size: 1.1rem;
        font-weight: 600;
        opacity: 0.85;
      }
      .cd-center {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        gap: 0.75rem;
      }
      .cd-logo-big {
        max-height: 120px;
        max-width: 320px;
        object-fit: contain;
        margin-bottom: 1rem;
      }
      .cd-center h1 {
        font-size: 3rem;
        margin: 0;
      }
      .cd-subtitle {
        font-size: 1.3rem;
        opacity: 0.7;
        margin: 0;
      }
      .cd-paid {
        font-size: 4rem;
        font-weight: 700;
        margin: 0.5rem 0 1rem;
      }
      .cd-payment-row {
        display: flex;
        justify-content: space-between;
        width: 320px;
        font-size: 1.3rem;
        opacity: 0.85;
      }
      .cd-cart {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .cd-lines {
        flex: 1;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding-bottom: 1rem;
      }
      .cd-line {
        display: flex;
        justify-content: space-between;
        font-size: 1.8rem;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        padding-bottom: 0.75rem;
      }
      .cd-line-qty {
        opacity: 0.6;
        font-size: 1.2rem;
        margin-left: 8px;
      }
      .cd-empty {
        opacity: 0.5;
        font-size: 1.5rem;
        text-align: center;
        margin-top: 2rem;
      }
      .cd-totals {
        border-top: 2px solid rgba(255, 255, 255, 0.15);
        padding-top: 1rem;
      }
      .cd-subrow {
        display: flex;
        justify-content: space-between;
        font-size: 1.3rem;
        opacity: 0.75;
        margin-bottom: 0.4rem;
      }
      .cd-total-due {
        display: flex;
        justify-content: space-between;
        font-size: 2.5rem;
        font-weight: 700;
        margin-top: 0.5rem;
      }
    `,
  ],
})
export class CustomerDisplayComponent implements OnInit, OnDestroy {
  cashierSessionId!: number;
  settings: StoreSettings | null = null;
  order: Order | null = null;
  view: 'idle' | 'cart' | 'thankyou' | 'error' = 'idle';
  errorMessage: string | null = null;

  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private thankYouTimeout: ReturnType<typeof setTimeout> | null = null;
  private lastThankedOrderId: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private cashierSessionApi: CashierSessionApiService,
    private orderApi: OrderApiService,
    private storeSettingsApi: StoreSettingsApiService
  ) {}

  ngOnInit(): void {
    this.cashierSessionId = Number(this.route.snapshot.paramMap.get('cashierSessionId'));
    this.storeSettingsApi.get().subscribe({ next: (s) => (this.settings = s), error: () => {} });
    this.poll();
    this.pollHandle = setInterval(() => this.poll(), 2500);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
    if (this.thankYouTimeout) clearTimeout(this.thankYouTimeout);
  }

  private poll(): void {
    this.cashierSessionApi.getById(this.cashierSessionId).subscribe({
      next: (cs) => {
        if (cs.status !== 'OPEN' || !cs.activeOrderId) {
          this.showIdle();
          return;
        }
        this.orderApi.getOrder(cs.activeOrderId).subscribe({
          next: (o) => this.applyOrder(o),
          // A transient fetch failure on the order itself shouldn't flip the whole screen to an
          // error state — just leave whatever was showing and try again next poll.
          error: () => {},
        });
      },
      error: (err) => this.showError(err),
    });
  }

  private applyOrder(o: Order): void {
    if (o.status === 'OPEN') {
      this.order = o;
      this.view = 'cart';
      return;
    }
    if (o.status === 'COMPLETED') {
      this.order = o;
      this.view = 'thankyou';
      if (this.lastThankedOrderId !== o.id) {
        this.lastThankedOrderId = o.id;
        if (this.thankYouTimeout) clearTimeout(this.thankYouTimeout);
        // Fallback only — normally the terminal moves on to a new tab and the next poll's
        // activeOrderId change preempts this by showing the new cart first.
        this.thankYouTimeout = setTimeout(() => this.showIdle(), 8000);
      }
      return;
    }
    // VOIDED (discarded) — nothing to show for it.
    this.showIdle();
  }

  private showIdle(): void {
    this.view = 'idle';
    this.order = null;
  }

  private showError(err: any): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
    this.view = 'error';
    this.errorMessage =
      err?.status === 403
        ? "You're not signed in as the cashier working this till (or an admin) — log into this device with that account to use it as a customer display."
        : err?.error?.message ?? 'This session could not be found.';
  }
}
