import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, PaymentRow, PaymentSummary } from '../core/api/online-store-api.service';
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
      <p class="text-muted" style="font-size:0.85rem;">{{ 'An online payment is recorded in the books (as a payment on the invoice) when the order is completed. Until then it is held for the order. A refund is possible until then; after the order is completed use a sales return.' | translate }}</p>

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
  busy = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load();
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
