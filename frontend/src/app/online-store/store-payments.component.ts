import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, PaymentRow, PaymentSummary, PayoutOverview, ProviderAccountRow, ProviderAccountsView } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Online payments: every try to pay an online order, so the money the provider took can be matched with the books. The summary
 * splits what was paid into money already recorded as invoice payments (the order was completed), money held for orders not completed yet, money
 * to give back and money already given back. Anything that needs a person (a payment under review, a refund due, a payment stuck pending) is flagged.
 */
@Component({
  selector: 'app-store-payments',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Online payments' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
      </div>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:flex-end;">
        <div class="form-group"><label class="form-label">{{ 'From' | translate }}</label><input class="form-control sp-from" type="date" [(ngModel)]="from" name="from" /></div>
        <div class="form-group"><label class="form-label">{{ 'To' | translate }}</label><input class="form-control sp-to" type="date" [(ngModel)]="to" name="to" /></div>
        <div class="form-group"><label class="form-label">{{ 'Show' | translate }}</label>
          <select class="form-select sp-status" [(ngModel)]="status" name="status" (ngModelChange)="load()">
            <option *ngFor="let s of statuses" [value]="s.key">{{ s.label | translate }}</option>
          </select>
        </div>
        <button class="btn btn-primary sp-refresh" (click)="load()">{{ 'Refresh' | translate }}</button>
      </div>

      <div class="flex gap-2 mb-3 sp-summary" style="flex-wrap:wrap;" *ngIf="sum">
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Paid' | translate }}</div><strong class="sp-paid">{{ sum.paid | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;"><div class="text-muted" style="font-size:0.8rem;">{{ 'In the books' | translate }}</div><strong class="sp-books">{{ sum.inBooks | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Held for open orders' | translate }}</div><strong class="sp-held">{{ sum.held | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;" [style.borderColor]="sum.refundDue > 0 ? '#b91c1c' : ''"><div class="text-muted" style="font-size:0.8rem;">{{ 'Refunds due' | translate }}</div><strong class="sp-due">{{ sum.refundDue | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Refunded' | translate }}</div><strong class="sp-refunded">{{ sum.refunded | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;" [style.borderColor]="sum.toReview > 0 ? '#b91c1c' : ''"><div class="text-muted" style="font-size:0.8rem;">{{ 'To review' | translate }}</div><strong class="sp-review">{{ sum.toReview }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:150px;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Failed or expired' | translate }}</div><strong>{{ sum.failed }}</strong></div>
      </div>
      <p class="text-muted" style="font-size:0.85rem;">{{ 'An online payment is recorded in the books when the provider confirms it: it is held for the order (Online customer deposits) against what the provider owes you. It becomes a payment on the invoice when the order is completed. A refund is possible until then; after the order is completed use a sales return. Record each payout from the provider below.' | translate }}</p>

      <div class="card mb-3 sp-accounts" style="padding:0.8rem 1rem;" *ngIf="pa">
        <h5 class="mb-1">{{ 'Accounts by provider' | translate }}</h5>
        <p class="text-muted mb-2" style="font-size:0.8rem;">{{ 'Choose the accounts each provider uses. Its payments and payouts are booked to them, so each balance can be matched to that provider\'s own statement. A provider with none uses the shared accounts, and its payouts arrive in Cash and Bank.' | translate }}</p>
        <table class="table-sm pa-table">
          <thead><tr><th>{{ 'Provider' | translate }}</th><th>{{ 'What the provider still owes you' | translate }}</th><th>{{ 'Payouts arrive in' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let r of pa.providers" [class]="'pa-row pa-' + r.code">
              <td><strong>{{ r.label }}</strong><br /><span class="text-muted" style="font-size:0.75rem;">{{ (r.active ? 'Switched on' : 'Not switched on') | translate }}</span></td>
              <td>
                <select class="form-select pa-clearing" style="min-width:220px;" [(ngModel)]="pe[r.code].clearing" [name]="'pc' + r.code">
                  <option [ngValue]="0">{{ 'None (shared account)' | translate }}</option>
                  <option *ngFor="let a of pa.accounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
                  <option [ngValue]="-1">{{ 'Create a new account...' | translate }}</option>
                </select>
                <div class="flex gap-1 mt-1" *ngIf="pe[r.code].clearing === -1">
                  <input class="form-control pa-clearing-name" style="width:170px;" maxlength="120" [(ngModel)]="pe[r.code].clearingName" [name]="'pcn' + r.code" [placeholder]="'Name' | translate" />
                  <input class="form-control pa-clearing-code" style="width:80px;" maxlength="20" [(ngModel)]="pe[r.code].clearingCode" [name]="'pcc' + r.code" [placeholder]="'Code' | translate" />
                </div>
              </td>
              <td>
                <select class="form-select pa-payout" style="min-width:220px;" [(ngModel)]="pe[r.code].payout" [name]="'pp' + r.code">
                  <option [ngValue]="0">{{ 'None (Cash and Bank)' | translate }}</option>
                  <option *ngFor="let a of pa.accounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
                  <option [ngValue]="-1">{{ 'Create a new account...' | translate }}</option>
                </select>
                <div class="flex gap-1 mt-1" *ngIf="pe[r.code].payout === -1">
                  <input class="form-control pa-payout-name" style="width:170px;" maxlength="120" [(ngModel)]="pe[r.code].payoutName" [name]="'ppn' + r.code" [placeholder]="'Name' | translate" />
                  <input class="form-control pa-payout-code" style="width:80px;" maxlength="20" [(ngModel)]="pe[r.code].payoutCode" [name]="'ppc' + r.code" [placeholder]="'Code' | translate" />
                </div>
              </td>
              <td><button class="btn btn-sm btn-primary pa-save" [disabled]="busy" (click)="saveAccounts(r)">{{ 'Save' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card mb-3 sp-payouts" style="padding:0.8rem 1rem;" *ngIf="payouts">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <h5 class="mb-0">{{ 'Payouts from the providers' | translate }}</h5>
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Fees paid so far' | translate }}: <strong class="sp-fees">{{ payouts.feesToDate | number: '1.0-2' }}</strong></span>
        </div>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.5rem 0.8rem; min-width:190px;" *ngFor="let b of payouts.balances">
            <div class="text-muted" style="font-size:0.8rem;">{{ chanLabel(b.channel, b.label) }}: {{ 'still owed by the provider' | translate }}</div>
            <strong [class]="'sp-owed-' + b.channel">{{ b.balance | number: '1.0-2' }}</strong>
          </div>
        </div>
        <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Way' | translate }}</label>
            <select class="form-select po-channel" [(ngModel)]="po.channel" name="poch" (ngModelChange)="suggest()">
              <option *ngFor="let b of payouts.balances" [value]="b.channel">{{ chanLabel(b.channel, b.label) }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Paid by customers (gross)' | translate }}</label><input class="form-control po-gross" type="number" min="0" step="0.01" style="width:140px;" [(ngModel)]="po.gross" name="pogross" /></div>
          <div class="form-group"><label class="form-label">{{ 'Provider fee' | translate }}</label><input class="form-control po-fee" type="number" min="0" step="0.01" style="width:110px;" [(ngModel)]="po.fee" name="pofee" /></div>
          <div class="form-group"><label class="form-label">{{ 'Reached the bank' | translate }}</label><div class="po-net" style="padding:0.45rem 0;"><strong>{{ (po.gross || 0) - (po.fee || 0) | number: '1.0-2' }}</strong></div></div>
          <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><input class="form-control po-date" type="date" [(ngModel)]="po.paidOn" name="podate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Reference' | translate }}</label><input class="form-control po-ref" maxlength="80" style="width:150px;" [(ngModel)]="po.reference" name="poref" /></div>
          <button class="btn btn-primary po-save" [disabled]="busy" (click)="savePayout()">{{ 'Record payout' | translate }}</button>
        </div>
        <p class="text-muted mt-2 mb-2" style="font-size:0.8rem;">{{ 'Gross is what customers paid that this payout covers. The fee is what the provider kept. The books move the gross out of what the provider owes you: the rest to the provider's own wallet or bank account, the fee to Payment Provider Fees.' | translate }}</p>
        <table class="table-sm po-table" *ngIf="payouts.payouts.length">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Way' | translate }}</th><th class="text-end">{{ 'Paid by customers (gross)' | translate }}</th><th class="text-end">{{ 'Provider fee' | translate }}</th><th class="text-end">{{ 'Reached the bank' | translate }}</th><th>{{ 'Reference' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let p of payouts.payouts">
            <td>{{ p.paidOn }}</td><td>{{ chanLabel(p.channel, p.label) }}</td>
            <td class="text-end">{{ p.gross | number: '1.0-2' }}</td><td class="text-end">{{ p.fee | number: '1.0-2' }}</td><td class="text-end">{{ p.net | number: '1.0-2' }}</td>
            <td>{{ p.reference }}</td><td>{{ p.createdBy }}</td>
          </tr></tbody>
        </table>
      </div>

      <table class="table-sm sp-table" *ngIf="rows.length">
        <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Order' | translate }}</th><th>{{ 'Way' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Payment' | translate }}</th><th>{{ 'Order status' | translate }}</th><th>{{ 'Needs attention' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of rows" [style.background]="r.flag ? '#fef2f2' : ''">
            <td>{{ r.createdAt | date: 'short' }}</td>
            <td>{{ r.orderCode }}<div class="text-muted" style="font-size:0.75rem;">{{ r.reference }}</div></td>
            <td>{{ r.method === 'CARD' ? ('Card' | translate) : ('Mobile money' | translate) }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.phone">{{ r.phone }}</div></td>
            <td class="text-end">{{ r.amount | number: '1.0-2' }} {{ r.currency }}</td>
            <td>{{ r.status }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.failureReason">{{ r.failureReason }}</div></td>
            <td>{{ r.orderStatus }}<div class="text-muted" style="font-size:0.75rem;">{{ r.orderPaymentStatus }}</div></td>
            <td>
              <span *ngIf="r.flag === 'REVIEW'" style="color:#b91c1c; font-weight:600;">{{ 'Money arrived that needs a person' | translate }}</span>
              <span *ngIf="r.flag === 'REFUND_DUE'" style="color:#b91c1c; font-weight:600;">{{ 'Refund due' | translate }}</span>
              <span *ngIf="r.flag === 'STUCK_PENDING'" style="color:#b45309; font-weight:600;">{{ 'Still pending long after it should have closed' | translate }}</span>
              <div *ngIf="r.flag === 'REVIEW'" class="flex gap-1 mt-1" style="align-items:center;">
                <input class="form-control sp-note" style="width:180px;" maxlength="160" [placeholder]="'What you did (e.g. refunded by hand)' | translate" [(ngModel)]="note[r.id]" [name]="'n' + r.id" />
                <button class="btn btn-sm btn-primary sp-resolve" [disabled]="busy" (click)="resolve(r)">{{ 'Mark as dealt with' | translate }}</button>
              </div>
              <a *ngIf="r.flag === 'REFUND_DUE'" routerLink="/admin/online-store/orders">{{ 'Go to the orders to refund it' | translate }}</a>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted sp-empty" *ngIf="loaded && rows.length === 0">{{ 'No online payments in this period.' | translate }}</p>
    </app-shell>
  `,
})
export class StorePaymentsComponent implements OnInit {
  statuses = [
    { key: '', label: 'Everything' },
    { key: 'REVIEW', label: 'Under review' },
    { key: 'REFUND_DUE', label: 'Refunds due' },
    { key: 'STUCK_PENDING', label: 'Stuck pending' },
    { key: 'PAID', label: 'Paid' },
    { key: 'PENDING', label: 'Pending' },
    { key: 'FAILED', label: 'Failed' },
    { key: 'EXPIRED', label: 'Expired' },
  ];
  from = '';
  to = '';
  status = '';
  sum: PaymentSummary | null = null;
  rows: PaymentRow[] = [];
  note: Record<number, string> = {};
  loaded = false;
  payouts: PayoutOverview | null = null;
  /** v1.75.0: what each ledger channel is called (MTN and Airtel are brand names, the others are translated). */
  chanLabel(c: string, label: string | null): string {
    return label ? label : c === 'CARD' ? T('Card') : T('Mobile money (earlier payments)');
  }

  po: { channel: string; gross: number | null; fee: number | null; paidOn: string; reference: string } = { channel: '', gross: null, fee: 0, paidOn: new Date().toISOString().slice(0, 10), reference: '' };
  /** v1.76.0: the accounts by provider, and what staff are choosing for each (0 = none, -1 = a new account). */
  pa: ProviderAccountsView | null = null;
  pe: Record<string, { clearing: number; payout: number; clearingName: string; clearingCode: string; payoutName: string; payoutCode: string }> = {};
  busy = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load();
    this.loadPayouts();
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.api.providerAccounts().subscribe({
      next: (v) => {
        this.pa = v;
        for (const r of v.providers) {
          this.pe[r.code] = { clearing: r.clearing?.id ?? 0, payout: r.payout?.id ?? 0, clearingName: r.label + ' ' + T('Clearing'), clearingCode: v.suggestedClearingCode ?? '', payoutName: r.label + ' ' + T('Wallet'), payoutCode: v.suggestedPayoutCode ?? '' };
        }
      },
      error: () => (this.pa = null),
    });
  }

  saveAccounts(r: ProviderAccountRow): void {
    const e = this.pe[r.code];
    this.busy = true;
    this.error = null;
    this.api.saveProviderAccounts(r.code, {
      clearingAccountId: e.clearing > 0 ? e.clearing : null, newClearingName: e.clearing === -1 ? e.clearingName.trim() : null, newClearingCode: e.clearing === -1 ? e.clearingCode.trim() : null,
      payoutAccountId: e.payout > 0 ? e.payout : null, newPayoutName: e.payout === -1 ? e.payoutName.trim() : null, newPayoutCode: e.payout === -1 ? e.payoutCode.trim() : null,
    }).subscribe({
      next: () => {
        this.busy = false;
        this.message = T('Accounts saved');
        this.loadAccounts();
        this.loadPayouts();
      },
      error: (er) => {
        this.busy = false;
        this.error = er?.error?.message ?? T('That did not work');
      },
    });
  }

  loadPayouts(): void {
    this.api.payoutOverview().subscribe({
      next: (o) => {
        this.payouts = o;
        this.suggest();
      },
      error: () => (this.payouts = null),
    });
  }

  /** The balance the provider still owes is the usual amount of the next payout. */
  suggest(): void {
    if (this.payouts && !this.payouts.balances.some((x) => x.channel === this.po.channel)) this.po.channel = this.payouts.balances[0]?.channel ?? '';
    const b = this.payouts?.balances.find((x) => x.channel === this.po.channel);
    if (b && b.balance > 0 && !this.po.gross) this.po.gross = b.balance;
  }

  savePayout(): void {
    const gross = Number(this.po.gross ?? 0);
    const fee = Number(this.po.fee ?? 0);
    if (!(gross > 0)) {
      this.error = T('Enter the amount customers paid that this payout covers');
      return;
    }
    if (fee < 0 || fee >= gross) {
      this.error = T('The fee must be zero or more, and less than the amount');
      return;
    }
    this.busy = true;
    this.error = null;
    this.api.recordPayout({ channel: this.po.channel, gross, fee, paidOn: this.po.paidOn || null, reference: this.po.reference.trim() || null, note: null }).subscribe({
      next: () => {
        this.busy = false;
        this.message = T('Payout recorded');
        this.po.gross = null;
        this.po.fee = 0;
        this.po.reference = '';
        this.loadPayouts();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }

  load(): void {
    this.error = null;
    this.api.payments(this.from, this.to, this.status).subscribe({
      next: (r) => {
        this.sum = r.summary;
        this.rows = r.rows;
        this.loaded = true;
      },
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the online payments')),
    });
  }

  resolve(r: PaymentRow): void {
    const n = (this.note[r.id] ?? '').trim();
    if (n.length < 3) {
      this.error = T('Write what was done (at least 3 characters)');
      return;
    }
    this.busy = true;
    this.error = null;
    this.api.resolvePayment(r.id, n).subscribe({
      next: () => {
        this.busy = false;
        this.message = T('Marked as dealt with');
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
