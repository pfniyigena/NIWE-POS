import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnlineStoreApiService, Shop, ShopReturn, ShopReturnOptions, ShopReviewItem, ShopReviewOptions, ShopTrack, shopToken } from '../core/api/online-store-api.service';
import { FormsModule } from '@angular/forms';
import { T } from '../core/i18n/t';
import { ShopMoneyPipe } from './shop-money.pipe';
import { PayProviderPickerComponent } from './pay-provider-picker.component';

/** Where a customer follows a shop order by its code. Refreshes by itself. */
@Component({
  selector: 'app-shop-track',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, FormsModule, ShopMoneyPipe, PayProviderPickerComponent],
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
              <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'This page updates by itself.' | translate }}</div>
              <button *ngIf="t.payment.canRetry && !otherWay" type="button" class="btn btn-outline-secondary mt-2 st-other-way" (click)="otherWay = true">{{ 'Pay another way' | translate }}</button>
            </div>
            <div *ngSwitchCase="'FAILED'" class="st-pay-failed" style="color:#b91c1c; font-weight:600;">{{ 'Your payment did not go through.' | translate }}</div>
            <div *ngSwitchCase="'PAID'" class="st-pay-paid" style="color:#15803d; font-weight:600;">{{ 'Paid {p0}. Thank you.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
            <div *ngSwitchCase="'REFUND_DUE'" class="st-pay-refund" style="font-weight:600;">{{ 'We will refund {p0} to the account you paid with.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
            <div *ngSwitchCase="'REFUNDED'" class="st-pay-refunded" style="font-weight:600;">{{ 'Refunded {p0}.' | translate: { p0: (t.payment.paidAmount | shopMoney: cur) } }}</div>
          </ng-container>
          <div *ngIf="t.payment.canRetry && shop && (otherWay || t.payment.status !== 'AWAITING')" class="mt-2 st-retry">
            <div class="flex gap-2" style="justify-content:center; flex-wrap:wrap; align-items:flex-end;">
              <ng-container *ngIf="shop.payMethods.includes('MOBILE_MONEY')">
                <app-pay-provider-picker *ngIf="momoProviders().length > 1" [providers]="momoProviders()" [(value)]="payProvider"></app-pay-provider-picker>
                <input class="form-control st-pay-phone" style="max-width:200px;" maxlength="40" inputmode="tel" [(ngModel)]="payPhone" name="paphone" [placeholder]="'Mobile money number' | translate" />
                <button class="btn btn-primary st-pay-momo" [disabled]="busy" (click)="again('MOBILE_MONEY')">{{ 'Pay with mobile money' | translate }}</button>
              </ng-container>
              <button *ngIf="shop.payMethods.includes('CARD')" class="btn btn-outline-primary st-pay-card" [disabled]="busy" (click)="again('CARD')">{{ 'Pay by card' | translate }}</button>
            </div>
          </div>
        </div>

        <div class="card mb-3" style="padding:0.75rem;">
          <div *ngFor="let l of t.lines" class="flex justify-between" style="padding:0.2rem 0;"><span>{{ l.quantity }} × {{ l.name }}</span><span>{{ l.lineTotal | shopMoney: cur }}</span></div>
          <div class="flex justify-between st-discount" *ngIf="t.discount - t.loyaltyDiscount > 0" style="padding-top:0.3rem; color:#15803d;"><span>{{ 'Discount ({p0})' | translate: { p0: t.couponCode } }}</span><span>−{{ t.discount - t.loyaltyDiscount | shopMoney: cur }}</span></div>
          <div class="flex justify-between st-points" *ngIf="t.loyaltyDiscount > 0" style="padding-top:0.3rem; color:#15803d;"><span>{{ 'Loyalty points' | translate }}</span><span>−{{ t.loyaltyDiscount | shopMoney: cur }}</span></div>
          <div class="st-earned text-muted" *ngIf="t.pointsEarned > 0" style="padding-top:0.3rem; font-size:0.85rem;">{{ 'You earned {p0} loyalty points on this order.' | translate: { p0: t.pointsEarned } }}</div>
          <div class="flex justify-between" *ngIf="t.feeWaived > 0" style="padding-top:0.3rem; color:#15803d;"><span>{{ 'Free delivery ({p0})' | translate: { p0: t.couponCode } }}</span><span>{{ 0 | shopMoney: cur }}</span></div>
          <div class="flex justify-between" *ngIf="t.deliveryFee > 0" style="padding-top:0.3rem;"><span>{{ 'Delivery fee ({p0})' | translate: { p0: (t.zoneName) } }}</span><span>{{ t.deliveryFee | shopMoney: cur }}</span></div>
          <hr />
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="st-total">{{ t.total | shopMoney: cur }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.payment?.method !== 'ONLINE'">{{ t.deliveryMethod === 'DELIVERY' ? ('Pay in cash when your order arrives.' | translate) : ('Pay in cash when you collect.' | translate) }}</div>
        </div>

        <div class="card mb-3 st-returns" *ngIf="t.status === 'COMPLETED' && ro && (ro.returns.length || ro.allowed || ro.message)" style="padding:0.75rem;">
          <h4 class="mb-2">{{ 'Returns' | translate }}</h4>
          <div class="alert alert-success st-return-sent" *ngIf="returnSent">{{ returnSent }}</div>
          <div *ngFor="let r of ro.returns" class="st-return" style="border-top:1px solid #e5e7eb; padding:0.4rem 0;">
            <div class="flex justify-between"><strong>{{ r.code }}</strong><span class="st-return-status" style="font-weight:600;" [style.color]="returnColor(r)">{{ returnLabel(r.status) | translate }}</span></div>
            <div class="text-muted" style="font-size:0.85rem;">{{ returnWhat(r) }}</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="r.status === 'REQUESTED'">{{ 'We will answer as soon as we can.' | translate }}</div>
            <div *ngIf="r.staffNote" style="font-size:0.85rem;">{{ r.staffNote }}</div>
            <div *ngIf="r.status === 'APPROVED'" style="font-size:0.85rem;">{{ 'Please bring the goods back to us. We refund you when we have them.' | translate }}</div>
            <div *ngIf="r.status === 'REFUNDED'" class="st-return-done" style="color:#15803d; font-size:0.85rem;">{{ (r.refundMethod === 'CASH' ? 'Handed back {p0} in cash.' : 'Refunded {p0}.') | translate: { p0: (r.refundAmount | shopMoney: cur) } }}<span *ngIf="r.refundReference && r.refundMethod !== 'CASH'"> {{ 'Reference {p0}' | translate: { p0: r.refundReference } }}</span></div>
          </div>
          <ng-container *ngIf="ro.allowed">
            <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="ro.lastDay && !returning">{{ 'You can return goods until {p0}.' | translate: { p0: (ro.lastDay | date: 'mediumDate') } }}</div>
            <button class="btn btn-outline-primary mt-2 st-return-open" *ngIf="!returning" (click)="startReturn()">{{ 'Return goods' | translate }}</button>
            <form *ngIf="returning" class="st-return-form mt-2" (ngSubmit)="sendReturn()">
              <div class="text-muted" style="font-size:0.85rem;">{{ 'Choose what you are sending back.' | translate }}</div>
              <div *ngFor="let l of ro.lines" class="flex justify-between align-center" style="padding:0.25rem 0; gap:0.5rem;" [style.opacity]="l.returnable > 0 ? 1 : 0.5">
                <span>{{ l.name }}<span class="text-muted" style="font-size:0.8rem;"> · {{ l.unitPaid | shopMoney: cur }} · {{ 'up to {p0}' | translate: { p0: (l.returnable | number: '1.0-4') } }}</span></span>
                <input class="form-control st-return-qty" type="number" min="0" [max]="l.returnable" step="any" style="width:90px;" [disabled]="l.returnable <= 0" [(ngModel)]="rq[l.lineId]" [name]="'rq' + l.lineId" />
              </div>
              <div class="form-group mt-2"><label class="form-label">{{ 'Phone number of this order' | translate }}<span class="text-muted" *ngIf="signedIn"> ({{ 'not needed when you are signed in' | translate }})</span></label>
                <input class="form-control st-return-phone" maxlength="40" inputmode="tel" name="rphone" [(ngModel)]="rphone" /></div>
              <div class="form-group"><label class="form-label">{{ 'Why are you returning them?' | translate }}</label>
                <textarea class="form-control st-return-reason" rows="2" maxlength="300" name="rreason" [(ngModel)]="rreason"></textarea></div>
              <div class="st-return-worth" *ngIf="returnWorth() > 0" style="font-weight:600;">{{ 'Worth {p0}. The delivery fee is not refunded.' | translate: { p0: (returnWorth() | shopMoney: cur) } }}</div>
              <div class="alert alert-danger mt-1 st-return-error" *ngIf="returnError">{{ returnError }}</div>
              <div class="flex gap-2 mt-2">
                <button class="btn btn-primary st-return-send" type="submit" [disabled]="busy">{{ 'Send the return request' | translate }}</button>
                <button class="btn btn-outline-secondary" type="button" (click)="returning = false">{{ 'Cancel' | translate }}</button>
              </div>
            </form>
          </ng-container>
          <div class="text-muted st-return-why" *ngIf="!ro.allowed && ro.message" style="font-size:0.85rem;">{{ ro.message }}</div>
        </div>

        <div class="card mb-3 st-reviews" *ngIf="t.status === 'COMPLETED' && rvo && rvo.canReview && rvo.items.length > 0" style="padding:0.75rem;">
          <h4 class="mb-2">{{ 'Review your products' | translate }}</h4>
          <p class="text-muted" style="font-size:0.85rem;">{{ 'Tell other customers what you think. Your review is shown after we have looked at it.' | translate }}</p>
          <div class="alert alert-success st-review-sent" *ngIf="reviewSent">{{ reviewSent }}</div>
          <div *ngFor="let i of rvo.items" class="st-review" style="border-top:1px solid #e5e7eb; padding:0.5rem 0;">
            <strong>{{ i.name }}</strong>
            <div *ngIf="i.status" class="text-muted st-review-done" style="font-size:0.85rem;"><span style="color:#d97706;">{{ stars(i.rating ?? 0) }}</span> {{ i.status === 'APPROVED' ? ('Published. Thank you.' | translate) : i.status === 'HIDDEN' ? ('Thank you. Your review is not shown.' | translate) : ('Thank you. Your review is waiting to be checked.' | translate) }}<span *ngIf="i.text"> · {{ i.text }}</span></div>
            <div *ngIf="!i.status">
              <div class="flex gap-1" style="font-size:1.5rem; cursor:pointer; line-height:1.2;">
                <span *ngFor="let n of [1, 2, 3, 4, 5]" class="st-star" role="button" [attr.aria-label]="n + ' / 5'" [style.color]="(rr[i.productId] || 0) >= n ? '#d97706' : '#9ca3af'" (click)="rr[i.productId] = n">{{ (rr[i.productId] || 0) >= n ? '★' : '☆' }}</span>
              </div>
              <textarea class="form-control st-review-text mt-1" rows="2" maxlength="1000" [name]="'rt' + i.productId" [(ngModel)]="rt[i.productId]" [placeholder]="'Your review (optional)' | translate"></textarea>
              <div class="mt-1" *ngIf="!signedIn"><input class="form-control st-review-phone" style="max-width:260px;" maxlength="40" inputmode="tel" [name]="'rp' + i.productId" [(ngModel)]="reviewPhone" [placeholder]="'Phone number of this order' | translate" /></div>
              <div class="alert alert-danger mt-1 st-review-error" *ngIf="reviewError[i.productId]">{{ reviewError[i.productId] }}</div>
              <button class="btn btn-sm btn-primary mt-1 st-review-send" type="button" [disabled]="busy" (click)="sendReview(i)">{{ 'Send my review' | translate }}</button>
            </div>
          </div>
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
  /** The mobile money provider chosen when the shop offers several. */
  payProvider = '';
  /** While a payment is waiting, the other-way choices stay hidden until the customer asks for them. */
  otherWay = false;
  error: string | null = null;
  busy = false;
  /** Returns after completion: what may still be sent back, and the form to ask. */
  ro: ShopReturnOptions | null = null;
  returning = false;
  rq: Record<number, number | null> = {};
  rphone = '';
  rreason = '';
  returnError: string | null = null;
  returnSent: string | null = null;
  /** Reviews of the products of a completed order: what can be reviewed, and the forms. */
  rvo: ShopReviewOptions | null = null;
  rr: Record<number, number> = {};
  rt: Record<number, string> = {};
  reviewPhone = '';
  reviewError: Record<number, string | null> = {};
  reviewSent: string | null = null;
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
        if (t.status === 'COMPLETED' && !this.returning) this.loadReturns();
        if (t.status === 'COMPLETED' && !this.rvo) this.loadReviews();
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

  momoProviders() {
    return (this.shop?.payProviders ?? []).filter((p) => p.methods.includes('MOBILE_MONEY'));
  }

  /** Try again, with the same way or the other. */
  again(way: 'MOBILE_MONEY' | 'CARD'): void {
    this.busy = true;
    this.error = null;
    const list = way === 'MOBILE_MONEY' ? this.momoProviders() : (this.shop?.payProviders ?? []).filter((p) => p.methods.includes(way));
    const provider = list.some((p) => p.code === this.payProvider) ? this.payProvider : (list[0]?.code ?? null);
    this.api.payAgain(this.code, way, way === 'MOBILE_MONEY' ? this.payPhone : null, provider).subscribe({
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

  get signedIn(): boolean {
    return !!shopToken.get();
  }

  private loadReturns(): void {
    this.api.returnOptions(this.code).subscribe({ next: (o) => (this.ro = o), error: () => {} });
  }

  private loadReviews(): void {
    this.api.reviewOptions(this.code).subscribe({ next: (o) => (this.rvo = o), error: () => {} });
  }

  stars(r: number): string {
    const n = Math.max(0, Math.min(5, Math.round(r)));
    return '★'.repeat(n) + '☆'.repeat(5 - n);
  }

  sendReview(i: ShopReviewItem): void {
    this.reviewError[i.productId] = null;
    this.reviewSent = null;
    const rating = this.rr[i.productId] ?? 0;
    if (rating < 1) { this.reviewError[i.productId] = T('Choose a rating from 1 to 5 stars.'); return; }
    if (!this.signedIn && this.reviewPhone.trim().length < 6) { this.reviewError[i.productId] = T('Enter the phone number of this order.'); return; }
    this.busy = true;
    this.api.submitReview(this.code, { phone: this.reviewPhone.trim() || null, productId: i.productId, rating, text: (this.rt[i.productId] ?? '').trim() || null, name: null }).subscribe({
      next: () => {
        this.busy = false;
        this.reviewSent = T('Thank you. Your review is waiting to be checked.');
        this.loadReviews();
      },
      error: (e) => { this.busy = false; this.reviewError[i.productId] = e?.error?.message ?? T('That did not work'); },
    });
  }

  startReturn(): void {
    this.returning = true;
    this.returnError = null;
    this.returnSent = null;
    this.rq = {};
  }

  /** What the customer gets back for the quantities typed so far (the server works it out again for real). */
  returnWorth(): number {
    let sum = 0;
    for (const l of this.ro?.lines ?? []) sum += Math.min(Math.max(Number(this.rq[l.lineId]) || 0, 0), l.returnable) * l.unitPaid;
    return Math.round(sum * 100) / 100;
  }

  sendReturn(): void {
    this.returnError = null;
    const lines = (this.ro?.lines ?? []).map((l) => ({ lineId: l.lineId, quantity: Number(this.rq[l.lineId]) || 0 })).filter((l) => l.quantity > 0);
    if (!lines.length) { this.returnError = T('Choose at least one item to return.'); return; }
    if (this.rreason.trim().length < 3) { this.returnError = T('Tell us why you are returning the goods.'); return; }
    if (!this.signedIn && this.rphone.trim().length < 6) { this.returnError = T('Enter the phone number of this order.'); return; }
    this.busy = true;
    this.api.requestReturn(this.code, { phone: this.rphone.trim() || null, reason: this.rreason.trim(), lines }).subscribe({
      next: (r) => {
        this.busy = false;
        this.returning = false;
        this.rq = {};
        this.rreason = '';
        this.returnSent = T('Your return request {p0} was sent. We will answer as soon as we can.', { p0: r.code });
        this.loadReturns();
        this.load();
      },
      error: (e) => { this.busy = false; this.returnError = e?.error?.message ?? T('That did not work'); },
    });
  }

  returnLabel(s: string): string {
    return ({ REQUESTED: 'Waiting for an answer', APPROVED: 'Approved', REFUSED: 'Refused', REFUNDED: 'Refunded' } as Record<string, string>)[s] ?? s;
  }

  returnColor(r: ShopReturn): string {
    return r.status === 'REFUSED' ? '#b91c1c' : r.status === 'REFUNDED' ? '#15803d' : r.status === 'APPROVED' ? '#1d4ed8' : '#b45309';
  }

  returnWhat(r: ShopReturn): string {
    return r.lines.map((l) => `${l.quantity} × ${l.name}`).join(', ');
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
