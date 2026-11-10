import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { AuthService } from '../../core/auth.service';
import { PAGE_SIZE } from '../../core/api/paged';
import { GiftCardDetail, GiftCardRow, GiftCardSummary, GiftCardsApiService } from '../../core/api/giftcards-api.service';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer, PaymentMethodOption } from '../../core/models';

/** Sales > Gift cards. A card is sold for money that is still owed back in goods, so it sits in the books as a liability until it is spent. Here: sell or give cards, add money, block, set expiry, see every movement. */
@Component({
  selector: 'app-gift-cards',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">{{ 'Gift cards' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary gc-release" *ngIf="canManage && summary && summary.expiredWithBalance > 0" (click)="release()">{{ 'Release expired balances' | translate }}</button>
          <button class="btn btn-primary gc-new" *ngIf="canManage && !selling" (click)="startSell()">{{ 'Sell a gift card' | translate }}</button>
        </div>
      </div>
      <p class="text-muted">{{ 'Money paid for a gift card is not sales yet: the shop owes it back as goods. It becomes sales when the card is spent at the till. A card can also be sold from the till itself.' | translate }}</p>
      <div class="alert alert-danger gc-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success gc-done" *ngIf="done">{{ done }}</div>

      <div class="grid-2 mb-3 gc-summary" *ngIf="summary">
        <div class="card" style="padding:0.75rem;"><div class="text-muted">{{ 'Owed on gift cards' | translate }}</div><div style="font-size:1.4rem; font-weight:700;" class="gc-liability">{{ summary.liability | number: '1.2-2' }}</div>
          <div class="text-muted" style="font-size:0.85rem;">{{ '{p0} card(s) with money left, {p1} in all' | translate: { p0: (summary.withBalance), p1: (summary.cards) } }}<span *ngIf="summary.blocked">{{ ', {p0} blocked' | translate: { p0: (summary.blocked) } }}</span></div></div>
        <div class="card" style="padding:0.75rem;" *ngIf="summary.expiredWithBalance > 0"><div class="text-muted">{{ 'Expired, not yet released' | translate }}</div><div style="font-size:1.4rem; font-weight:700;" class="gc-expired-balance">{{ summary.expiredBalance | number: '1.2-2' }}</div>
          <div class="text-muted" style="font-size:0.85rem;">{{ '{p0} card(s). Releasing moves this from what you owe into income.' | translate: { p0: (summary.expiredWithBalance) } }}</div></div>
      </div>

      <div class="card mb-3 gc-sell-form" *ngIf="selling" style="padding:1rem;">
        <h4>{{ 'Sell a gift card' | translate }}</h4>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control gc-sell-amount" type="number" min="0" step="0.01" name="sellAmount" [(ngModel)]="sell.amount" /></div>
          <div class="form-group"><label class="form-label">{{ 'Paid by' | translate }}</label>
            <select class="form-control gc-sell-method" name="sellMethod" [(ngModel)]="sell.paymentMethod">
              <option value="">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let m of payableMethods" [value]="m.code">{{ m.name }}</option>
              <option value="COMPLIMENTARY">{{ 'Given free (no payment)' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Bought by (optional)' | translate }}</label>
            <select class="form-control gc-sell-customer" name="sellCustomer" [(ngModel)]="sell.customerId">
              <option [ngValue]="null">{{ 'No customer' | translate }}</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'For (optional)' | translate }}</label><input class="form-control gc-sell-recipient" name="sellRecipient" [(ngModel)]="sell.recipientName" /></div>
          <div class="form-group"><label class="form-label">{{ 'Expires on (optional)' | translate }}</label><input class="form-control gc-sell-expiry" type="date" name="sellExpiry" [(ngModel)]="sell.expiresOn" /></div>
          <div class="form-group"><label class="form-label">{{ 'Card code (optional — made for you if left empty)' | translate }}</label><input class="form-control gc-sell-code" name="sellCode" [(ngModel)]="sell.code" /></div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary gc-sell-save" (click)="doSell()">{{ 'Sell card' | translate }}</button>
          <button class="btn btn-link gc-sell-cancel" (click)="selling = false">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <div class="card mb-3 gc-topup-form" *ngIf="topUpCard" style="padding:1rem;">
        <h4>{{ 'Add money to {p0}' | translate: { p0: (topUpCard.code) } }}</h4>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control gc-topup-amount" type="number" min="0" step="0.01" name="topAmount" [(ngModel)]="topUp.amount" /></div>
          <div class="form-group"><label class="form-label">{{ 'Paid by' | translate }}</label>
            <select class="form-control gc-topup-method" name="topMethod" [(ngModel)]="topUp.paymentMethod">
              <option value="">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let m of payableMethods" [value]="m.code">{{ m.name }}</option>
              <option value="COMPLIMENTARY">{{ 'Given free (no payment)' | translate }}</option>
            </select></div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary gc-topup-save" (click)="doTopUp()">{{ 'Add money' | translate }}</button>
          <button class="btn btn-link gc-topup-cancel" (click)="topUpCard = null">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <div class="card mb-3 gc-expiry-form" *ngIf="expiryCard" style="padding:1rem;">
        <h4>{{ 'Expiry of {p0}' | translate: { p0: (expiryCard.code) } }}</h4>
        <div class="form-group"><label class="form-label">{{ 'Expires on (empty = never)' | translate }}</label><input class="form-control gc-expiry-date" type="date" name="expiryDate" [(ngModel)]="expiryDate" style="max-width:220px;" /></div>
        <div class="flex gap-2">
          <button class="btn btn-primary gc-expiry-save" (click)="doExpiry()">{{ 'Save' | translate }}</button>
          <button class="btn btn-link gc-expiry-cancel" (click)="expiryCard = null">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <div class="card mb-3 gc-history" *ngIf="detail" style="padding:1rem;">
        <div class="flex justify-between align-center">
          <h4 style="margin:0;">{{ '{p0} — {p1} left of {p2}' | translate: { p0: (detail.card.code), p1: (detail.card.balance | number: '1.2-2'), p2: (detail.card.initialAmount | number: '1.2-2') } }}</h4>
          <button class="btn btn-link gc-history-close" (click)="detail = null">{{ 'Close' | translate }}</button>
        </div>
        <table class="mt-2">
          <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th class="text-end">{{ 'Balance after' | translate }}</th><th>{{ 'Sale' | translate }}</th><th>{{ 'Note' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of detail.history" class="gc-txn">
              <td data-label="When">{{ t.at | date: 'medium' }}</td>
              <td data-label="What">{{ typeLabel(t.type) }}<span *ngIf="t.paymentMethod" class="text-muted"> ({{ t.paymentMethod === 'COMPLIMENTARY' ? 'free' : t.paymentMethod }})</span></td>
              <td data-label="Amount" class="text-end">{{ t.amount | number: '1.2-2' }}</td>
              <td data-label="Balance after" class="text-end">{{ t.balanceAfter | number: '1.2-2' }}</td>
              <td data-label="Sale">{{ t.invoiceNumber || '' }}</td>
              <td data-label="Note">{{ t.note || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <input class="form-control gc-search" style="max-width:260px;" [placeholder]="'Search code, name or customer' | translate" name="gcSearch" [(ngModel)]="q" (ngModelChange)="reload()" />
        <select class="form-control gc-filter" style="max-width:220px;" name="gcFilter" [(ngModel)]="filter" (ngModelChange)="reload()">
          <option value="ALL">{{ 'All cards' | translate }}</option>
          <option value="USABLE">{{ 'Can be spent now' | translate }}</option>
          <option value="WITH_BALANCE">{{ 'Money left' | translate }}</option>
          <option value="EMPTY">{{ 'Empty' | translate }}</option>
          <option value="BLOCKED">{{ 'Blocked' | translate }}</option>
          <option value="EXPIRED">{{ 'Expired' | translate }}</option>
        </select>
      </div>
      <table>
        <thead><tr><th>{{ 'Code' | translate }}</th><th>{{ 'For' | translate }}</th><th class="text-end">{{ 'Sold for' | translate }}</th><th class="text-end">{{ 'Left' | translate }}</th><th>{{ 'Expires' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of rows" class="gc-row" [attr.data-code]="c.code">
            <td data-label="Code"><strong>{{ c.code }}</strong></td>
            <td data-label="For">{{ c.recipientName || c.customerName || '' }}</td>
            <td data-label="Sold for" class="text-end">{{ c.initialAmount | number: '1.2-2' }}</td>
            <td data-label="Left" class="text-end gc-left">{{ c.balance | number: '1.2-2' }}</td>
            <td data-label="Expires">{{ c.expiresOn || ('Never' | translate) }}</td>
            <td data-label="Status"><span [ngClass]="c.usable ? 'text-success' : 'text-muted'" class="gc-status">{{ c.status === 'BLOCKED' ? ('Blocked' | translate) : (c.expired ? ('Expired' | translate) : (c.balance > 0 ? ('Active' | translate) : ('Used up' | translate))) }}</span></td>
            <td data-label="" class="flex gap-2" style="flex-wrap:wrap;">
              <button class="btn btn-sm btn-outline-primary gc-history-btn" (click)="openHistory(c)">{{ 'History' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary gc-topup-btn" *ngIf="canManage" (click)="startTopUp(c)">{{ 'Add money' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary gc-expiry-btn" *ngIf="canManage" (click)="startExpiry(c)">{{ 'Expiry' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger gc-block-btn" *ngIf="canManage && c.status === 'ACTIVE'" (click)="setBlocked(c, true)">{{ 'Block' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary gc-unblock-btn" *ngIf="canManage && c.status === 'BLOCKED'" (click)="setBlocked(c, false)">{{ 'Unblock' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="rows.length === 0"><td colspan="7" class="text-muted">{{ 'No gift cards.' | translate }}</td></tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goTo($event)"></app-pager>
    </app-shell>
  `,
})
export class GiftCardsComponent implements OnInit {
  rows: GiftCardRow[] = [];
  summary: GiftCardSummary | null = null;
  detail: GiftCardDetail | null = null;
  error: string | null = null;
  done: string | null = null;
  q = '';
  filter = 'ALL';
  page = 0;
  size = PAGE_SIZE;
  total = 0;
  payableMethods: PaymentMethodOption[] = [];
  customers: Customer[] = [];

  selling = false;
  sell: { amount: number | null; paymentMethod: string; customerId: number | null; recipientName: string; expiresOn: string; code: string } = this.blankSell();
  topUpCard: GiftCardRow | null = null;
  topUp: { amount: number | null; paymentMethod: string } = { amount: null, paymentMethod: '' };
  expiryCard: GiftCardRow | null = null;
  expiryDate = '';

  constructor(private api: GiftCardsApiService, private auth: AuthService, private methods: PaymentMethodApiService, private customerApi: CustomerApiService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.methods.listActive().subscribe((m) => (this.payableMethods = m.filter((x) => !x.credit && !x.roomCharge)));
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !x.walkIn)), error: () => (this.customers = []) });
    this.reload();
  }

  private blankSell() {
    return { amount: null as number | null, paymentMethod: '', customerId: null as number | null, recipientName: '', expiresOn: '', code: '' };
  }

  reload(): void {
    this.page = 0;
    this.load();
  }

  goTo(p: number): void {
    this.page = p;
    this.load();
  }

  load(): void {
    this.api.list(this.q, this.filter, this.page, this.size).subscribe({
      next: (r) => {
        this.rows = r.items;
        this.total = r.total;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the gift cards'),
    });
    this.api.summary().subscribe({ next: (s) => (this.summary = s), error: () => (this.summary = null) });
  }

  typeLabel(t: string): string {
    return ({ SOLD: 'Sold', TOPUP: 'Money added', REDEEM: 'Spent', REFUND: 'Refunded onto card', RELEASED: 'Expired, released' } as Record<string, string>)[t] ?? t;
  }

  private fail(err: any, fallback: string): void {
    this.error = err?.error?.message ?? fallback;
  }

  startSell(): void {
    this.error = null;
    this.done = null;
    this.topUpCard = null;
    this.expiryCard = null;
    this.sell = this.blankSell();
    this.selling = true;
  }

  doSell(): void {
    this.error = null;
    this.done = null;
    this.api
      .sell({
        amount: Number(this.sell.amount) || 0,
        paymentMethod: this.sell.paymentMethod,
        customerId: this.sell.customerId,
        recipientName: this.sell.recipientName || null,
        expiresOn: this.sell.expiresOn || null,
        code: this.sell.code || null,
      })
      .subscribe({
        next: (c) => {
          this.done = 'Gift card ' + c.code + ' sold for ' + c.initialAmount + '.';
          this.selling = false;
          this.load();
        },
        error: (err) => this.fail(err, 'Could not sell the gift card'),
      });
  }

  startTopUp(c: GiftCardRow): void {
    this.error = null;
    this.done = null;
    this.selling = false;
    this.expiryCard = null;
    this.topUpCard = c;
    this.topUp = { amount: null, paymentMethod: '' };
  }

  doTopUp(): void {
    if (!this.topUpCard) return;
    this.error = null;
    const card = this.topUpCard;
    this.api.topUp(card.id, { amount: Number(this.topUp.amount) || 0, paymentMethod: this.topUp.paymentMethod }).subscribe({
      next: (c) => {
        this.done = 'Added money to ' + c.code + '. It now has ' + c.balance + '.';
        this.topUpCard = null;
        this.load();
      },
      error: (err) => this.fail(err, 'Could not add money'),
    });
  }

  startExpiry(c: GiftCardRow): void {
    this.error = null;
    this.done = null;
    this.selling = false;
    this.topUpCard = null;
    this.expiryCard = c;
    this.expiryDate = c.expiresOn ?? '';
  }

  doExpiry(): void {
    if (!this.expiryCard) return;
    this.error = null;
    const card = this.expiryCard;
    this.api.setExpiry(card.id, this.expiryDate || null).subscribe({
      next: (c) => {
        this.done = c.expiresOn ? c.code + ' now expires on ' + c.expiresOn + '.' : c.code + ' never expires.';
        this.expiryCard = null;
        this.load();
      },
      error: (err) => this.fail(err, 'Could not change the expiry date'),
    });
  }

  setBlocked(c: GiftCardRow, blocked: boolean): void {
    this.error = null;
    this.done = null;
    (blocked ? this.api.block(c.id) : this.api.unblock(c.id)).subscribe({
      next: (r) => {
        this.done = r.code + (blocked ? ' is blocked.' : ' is unblocked.');
        this.load();
      },
      error: (err) => this.fail(err, 'Could not change the card'),
    });
  }

  openHistory(c: GiftCardRow): void {
    this.error = null;
    this.api.detail(c.id).subscribe({ next: (d) => (this.detail = d), error: (err) => this.fail(err, 'Could not load the history') });
  }

  release(): void {
    this.error = null;
    this.done = null;
    if (!confirm('Move the money left on every expired gift card out of what you owe and into income? This cannot be undone.')) return;
    this.api.releaseExpired().subscribe({
      next: (r) => {
        this.done = 'Released ' + r.amount + ' from ' + r.cards + ' expired card(s).';
        this.load();
      },
      error: (err) => this.fail(err, 'Could not release the expired cards'),
    });
  }
}
