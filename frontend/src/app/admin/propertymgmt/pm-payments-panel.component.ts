import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentRow, PmAccountApiService } from '../../core/api/pm-account-api.service';
import { downloadBlob } from '../../core/download-file';

import { T } from '../../core/i18n/t';
/** The rent payments received (of one lease or one tenant), each with its receipt to download or e-mail to the tenant. */
@Component({
  selector: 'app-pm-payments-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;">
      <h4 class="mb-2">{{ 'Payments received' | translate }}</h4>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>
      <table class="stack table-sm" *ngIf="rows.length; else none">
        <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Receipt' | translate }}</th><th>{{ 'Paid by' | translate }}</th><th>{{ 'For' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th></th></tr></thead>
        <tbody><tr *ngFor="let p of rows">
          <td style="white-space:nowrap;" data-label="Date">{{ p.paidDate | date: 'd MMM y' }}</td>
          <td data-label="Receipt">{{ p.receiptNumber }}</td>
          <td data-label="Paid by"><div>{{ p.method }}<span class="text-muted" *ngIf="p.reference"> · {{ p.reference }}</span></div></td>
          <td data-label="For"><div>{{ p.leaseCode }}<span *ngIf="p.unit"> · {{ p.unit }}</span><div class="text-muted" style="font-size:0.75rem;">{{ p.invoices.join(', ') }}</div></div></td>
          <td class="text-end" data-label="Amount"><strong>{{ p.amount | number: '1.0-0' }}</strong></td>
          <td style="white-space:nowrap; text-align:right;" data-label="">
            <button class="btn btn-sm btn-outline-secondary" type="button" (click)="download(p)" [disabled]="busy">{{ 'Receipt' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="canManage" (click)="open(p)" [disabled]="busy" style="margin-left:0.3rem;">{{ 'E-mail' | translate }}</button>
          </td>
        </tr></tbody>
      </table>
      <ng-template #none><div class="text-muted">{{ loaded ? ('No payments received yet.' | translate) : ('Loading…' | translate) }}</div></ng-template>

      <form *ngIf="sending" class="mt-2" style="border-top:1px solid var(--border); padding-top:0.75rem;" (ngSubmit)="send()">
        <div class="mb-2"><strong>{{ 'E-mail receipt {p0}' | translate: { p0: (sending.receiptNumber) } }}</strong></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'To' | translate }}</label>
            <input class="form-control" type="email" name="to" [(ngModel)]="address" [placeholder]="'Leave empty to use the tenant\\'s address' | translate" /></div>
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Message (optional)' | translate }}</label>
            <input class="form-control" name="msg" [(ngModel)]="message" maxlength="1000" /></div>
        </div>
        <button class="btn btn-primary btn-sm" type="submit" [disabled]="busy">{{ busy ? ('Sending…' | translate) : ('Send receipt' | translate) }}</button>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="sending = null" style="margin-left:0.4rem;">{{ 'Cancel' | translate }}</button>
      </form>
    </div>
  `,
})
export class PmPaymentsPanelComponent implements OnChanges {
  @Input() leaseId: number | null = null;
  @Input() tenantId: number | null = null;
  @Input() canManage = false;
  rows: PaymentRow[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;
  note: string | null = null;
  sending: PaymentRow | null = null;
  address = '';
  message = '';

  constructor(private api: PmAccountApiService) {}

  ngOnChanges(): void {
    this.api.payments({ leaseId: this.leaseId ?? undefined, tenantId: this.tenantId ?? undefined }).subscribe({
      next: (l) => { this.rows = l; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the payments'); },
    });
  }

  download(p: PaymentRow): void {
    this.busy = true;
    this.error = null;
    this.api.receiptPdf(p.paymentId).subscribe({
      next: (b) => { this.busy = false; downloadBlob(b, `receipt-${p.receiptNumber ?? p.paymentId}.pdf`); },
      error: (e) => { this.busy = false; this.error = T('Could not make the receipt'); },
    });
  }

  open(p: PaymentRow): void { this.sending = p; this.address = ''; this.message = ''; this.error = null; this.note = null; }

  send(): void {
    if (!this.sending) return;
    this.busy = true;
    this.error = null;
    this.note = null;
    const body: { email?: string; message?: string } = {};
    if (this.address.trim()) body.email = this.address.trim();
    if (this.message.trim()) body.message = this.message.trim();
    this.api.emailReceipt(this.sending.paymentId, body).subscribe({
      next: (r) => { this.busy = false; this.sending = null; this.note = `Receipt sent to ${r.sentTo}.`; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the receipt'); },
    });
  }
}
