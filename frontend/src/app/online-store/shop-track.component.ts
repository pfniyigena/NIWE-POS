import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnlineStoreApiService, Shop, ShopTrack } from '../core/api/online-store-api.service';
import { FormsModule } from '@angular/forms';
import { T } from '../core/i18n/t';
import { ShopMoneyPipe } from './shop-money.pipe';

/** Where a customer follows a shop order by its code. Refreshes by itself. */
@Component({
  selector: 'app-shop-track',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, FormsModule, ShopMoneyPipe],
  template: `
    <div class="st-page" style="max-width:520px; margin:0 auto; padding:1rem 0.75rem 3rem;">
      <div class="alert alert-danger st-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!t && !error" class="text-muted">{{ 'Looking for your order…' | translate }}</div>
      <ng-container *ngIf="t">
        <div style="text-align:center;" class="mb-3">
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Your order code' | translate }}</div>
          <div class="st-code" style="font-size:2rem; font-weight:700; letter-spacing:0.12em;">{{ t.code }}</div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Keep this code to follow your order.' | translate }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem; text-align:center;" [style.borderColor]="bad() ? '#dc2626' : t.status === 'COMPLETED' ? '#16a34a' : ''">
          <div class="st-stage" style="font-weight:700;">{{ stageTitle() | translate }}</div>
          <div class="st-reason mt-1" *ngIf="t.rejectReason" style="color:#b91c1c;">{{ t.rejectReason }}</div>
          <div class="flex gap-1 mt-2" *ngIf="!bad()" style="justify-content:center;">
            <span *ngFor="let s of steps()" [style.background]="s.reached ? '#16a34a' : '#d1d5db'" style="height:6px; flex:1; border-radius:3px;" [title]="s.label | translate"></span>
          </div>
        </div>

        <div class="card mb-3 st-payment" *ngIf="t.payment && t.payment.method === 'ONLINE'" style="padding:0.75rem; text-align:center;">
          <ng-container [ngSwitch]="t.payment.status">
            <div *ngSwitchCase="'AWAITING'">
              <div class="st-pay-wait" style="font-weight:600;">{{ (t.payment.channel === 'CARD' ? 'Waiting for your card payment…' : 'Waiting for your payment. Approve it on your phone.') | translate }}</div>
              <a *ngIf="t.payment.redirectUrl" class="btn btn-primary mt-2 st-pay-open" [href]="t.payment.redirectUrl">{{ 'Open the payment page' | translate }}</a>
              <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'This page updates by itself. You can also pay another way below.' | translate }}</div>
            </div>
            <div *ngSwitchCase="'FAILED'" class="st-pay-failed" style="color:#b91c1c; font-weight:600;">{{ 'Your payment did not go through.' | translate }}</div>
            <div *ngSwitchCase="'PAID'" class="st-pay-paid" style="color:#15803d; font-weight:600;">{{ 'Paid {p0}. Thank you.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
            <div *ngSwitchCase="'REFUND_DUE'" class="st-pay-refund" style="font-weight:600;">{{ 'We will refund {p0} to the account you paid with.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
            <div *ngSwitchCase="'REFUNDED'" class="st-pay-refunded" style="font-weight:600;">{{ 'Refunded {p0}.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
          </ng-container>
          <div *ngIf="t.payment.canRetry && shop" class="mt-2 st-retry">
            <div class="flex gap-2" style="justify-content:center; flex-wrap:wrap; align-items:flex-end;">
              <ng-container *ngIf="shop.payMethods.includes('MOBILE_MONEY')">
                <input class="form-control st-pay-phone" style="max-width:200px;" maxlength="40" inputmode="tel" [(ngModel)]="payPhone" name="paphone" [placeholder]="'Mobile money number' | translate" />
                <button class="btn btn-primary st-pay-momo" [disabled]="busy" (click)="again('MOBILE_MONEY')">{{ 'Pay with mobile money' | translate }}</button>
              </ng-container>
              <button *ngIf="shop.payMethods.includes('CARD')" class="btn btn-outline-primary st-pay-card" [disabled]="busy" (click)="again('CARD')">{{ 'Pay by card' | translate }}</button>
            </div>
          </div>
        </div>

        <div class="card mb-3" style="padding:0.75rem;">
          <div *ngFor="let l of t.lines" class="flex justify-between" style="padding:0.2rem 0;"><span>{{ l.quantity }} × {{ l.name }}</span><span>{{ l.lineTotal | shopMoney: cur }}</span></div>
          <div class="flex justify-between" *ngIf="t.deliveryFee > 0" style="padding-top:0.3rem;"><span>{{ 'Delivery fee ({p0})' | translate: { p0: (t.zoneName) } }}</span><span>{{ t.deliveryFee | shopMoney: cur }}</span></div>
          <hr />
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="st-total">{{ t.total | shopMoney: cur }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.payment?.method !== 'ONLINE'">{{ t.deliveryMethod === 'DELIVERY' ? ('Pay in cash when your order arrives.' | translate) : ('Pay in cash when you collect.' | translate) }}</div>
        </div>

        <div class="flex gap-2" style="flex-wrap:wrap; justify-content:center;">
          <button class="btn btn-outline-danger st-cancel" *ngIf="t.status === 'PLACED' || t.status === 'AWAITING_PAYMENT'" [disabled]="busy" (click)="cancel()">{{ 'Cancel this order' | translate }}</button>
          <a class="btn btn-primary" routerLink="/shop">{{ 'Order again' | translate }}</a>
        </div>
      </ng-container>
    </div>
  `,
})
export class ShopTrackComponent implements OnInit, OnDestroy {
  t: ShopTrack | null = null;
  shop: Shop | null = null;
  payPhone = '';
  error: string | null = null;
  busy = false;
  private code = '';
  private poll: ReturnType<typeof setTimeout> | null = null;
  private alive = true;

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    // The shop gives the currency written after the amounts (and the ways to pay again).
    this.api.shop().subscribe({ next: (sh) => (this.shop = sh), error: () => {} });
    this.load();
  }

  /** The shop's currency, written after every amount. */
  get cur(): string {
    return this.shop?.currency ?? '';
  }

  ngOnDestroy(): void {
    this.alive = false;
    if (this.poll) clearTimeout(this.poll);
  }

  /** Looks again soon while a payment is being confirmed (the customer is waiting), and every 15 seconds otherwise. */
  private schedule(): void {
    if (!this.alive) return;
    if (this.poll) clearTimeout(this.poll);
    this.poll = setTimeout(() => this.load(), this.t?.payment?.status === 'AWAITING' ? 4000 : 15000);
  }

  private load(): void {
    this.api.track(this.code).subscribe({
      next: (t) => {
        this.t = t;
        this.error = null;
        // The payment ways the shop offers are only needed once there is something to retry.
        if (t.payment?.canRetry && !this.shop) this.api.shop().subscribe({ next: (s) => (this.shop = s), error: () => {} });
        this.schedule();
      },
      error: (e) => {
        if (!this.t) this.error = e?.error?.message ?? T('We could not find that order. Check the code.');
        this.schedule();
      },
    });
  }

  /** Try again, with the same way or the other. */
  again(way: 'MOBILE_MONEY' | 'CARD'): void {
    this.busy = true;
    this.error = null;
    this.api.payAgain(this.code, way, way === 'MOBILE_MONEY' ? this.payPhone : null).subscribe({
      next: (t) => {
        this.t = t;
        this.busy = false;
        if (t.payment?.status === 'AWAITING' && t.payment.redirectUrl) {
          window.location.assign(t.payment.redirectUrl);
          return;
        }
        this.schedule();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }

  bad(): boolean {
    return this.t?.status === 'REJECTED' || this.t?.status === 'CANCELLED';
  }

  stageTitle(): string {
    switch (this.t?.status) {
      case 'AWAITING_PAYMENT': return 'Waiting for your payment.';
      case 'PLACED': return this.t?.payment?.status === 'PAID' ? 'Paid. We will confirm your order shortly.' : 'We received your order. We will confirm it shortly.';
      case 'ACCEPTED': return 'Accepted: we are preparing your order.';
      case 'PACKED': return 'Packed and ready to go.';
      case 'OUT_FOR_DELIVERY': return 'On its way to you.';
      case 'READY_FOR_PICKUP': return 'Ready for you to collect.';
      case 'COMPLETED': return 'Done. Thank you for your order.';
      case 'REJECTED': return 'Sorry, we could not take this order.';
      case 'CANCELLED': return 'This order was cancelled.';
      default: return this.t?.status ?? '';
    }
  }

  steps(): { label: string; reached: boolean }[] {
    const delivery = this.t?.deliveryMethod === 'DELIVERY';
    const online = this.t?.payment?.method === 'ONLINE';
    const order = ['PLACED', 'ACCEPTED', delivery ? 'OUT_FOR_DELIVERY' : 'READY_FOR_PICKUP', 'COMPLETED'];
    const labels = ['Received', 'Accepted', delivery ? 'On its way' : 'Ready', 'Done'];
    if (online) {
      order.unshift('AWAITING_PAYMENT');
      labels.unshift('Paid');
    }
    // An order waiting for its payment has reached nothing yet; an online order that is PLACED has been paid.
    const status = this.t?.status ?? '';
    const at = status === 'AWAITING_PAYMENT' ? -1 : Math.max(order.indexOf(status), status === 'PACKED' ? (online ? 2 : 1) : 0);
    return order.map((_, i) => ({ label: labels[i], reached: i <= at }));
  }

  cancel(): void {
    if (!confirm(T(this.t?.payment?.status === 'PAID' ? 'Cancel this order? Your payment will be refunded.' : 'Cancel this order?'))) return;
    this.busy = true;
    this.api.cancel(this.code).subscribe({
      next: (t) => { this.t = t; this.busy = false; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
