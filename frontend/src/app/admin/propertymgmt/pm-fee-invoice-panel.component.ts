import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FeeInvoice, FEE_INVOICE_STATUS_LABEL, PmFeeInvoiceApiService, feeInvoiceBadge } from '../../core/api/pm-fee-invoice-api.service';

import { T } from '../../core/i18n/t';
/** On an agreed owner statement: the invoice to the owner for the management fee (number, amounts, PDF), and a button to make it when an older statement has none. */
@Component({
  selector: 'app-pm-fee-invoice-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <div class="card mb-3" style="padding:1rem;" *ngIf="loaded && (inv || (fee > 0 && canManage))">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Invoice for the management fee' | translate }}</h4>
        <div class="flex gap-2" *ngIf="inv">
          <button class="btn btn-outline-secondary" type="button" (click)="download()" [disabled]="busy">{{ 'Download PDF' | translate }}</button>
        </div>
        <button class="btn btn-outline-primary" type="button" *ngIf="!inv && canManage" (click)="issue()" [disabled]="busy">{{ 'Make the invoice' | translate }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="inv">
        <table class="table-sm"><tbody>
          <tr><td class="text-muted" style="width:9rem;">{{ 'Invoice' | translate }}</td><td><strong>{{ inv.invoice.number }}</strong>
            <span class="badge" [ngClass]="badge(inv.invoice.status)" style="margin-left:0.4rem;">{{ statusName(inv.invoice.status) }}</span>
            <span class="text-muted" style="margin-left:0.4rem;">{{ inv.taxInvoice ? ('Tax invoice' | translate) : ('Invoice (no VAT)' | translate) }}</span></td></tr>
          <tr><td class="text-muted">{{ 'Dated' | translate }}</td><td>{{ inv.invoice.issueDate | date: 'd MMM y' }}{{ inv.issuedByName ? ' · ' + inv.issuedByName : '' }}</td></tr>
          <tr><td class="text-muted">{{ 'Fee' | translate }}</td><td>{{ inv.invoice.netAmount | number: '1.2-2' }}</td></tr>
          <tr *ngIf="inv.invoice.vatAmount > 0"><td class="text-muted">{{ 'VAT {p0}%' | translate: { p0: (inv.invoice.vatPercent | number: '1.0-2') } }}</td><td>{{ inv.invoice.vatAmount | number: '1.2-2' }}</td></tr>
          <tr><td class="text-muted">{{ 'Total' | translate }}</td><td><strong>{{ inv.invoice.totalAmount | number: '1.2-2' }}</strong></td></tr>
          <tr *ngIf="inv.voidReason"><td class="text-muted">{{ 'Voided' | translate }}</td><td>{{ inv.voidReason }}</td></tr>
        </tbody></table>
        <div class="text-muted mt-2" style="font-size:0.8rem;">{{ inv.invoice.status === 'VOID' ? ('This invoice was replaced or withdrawn. A new one is made when the statement is agreed again.' | translate) : ('The fee is already booked by the statement, so this invoice is a document only. It is settled by keeping the fee out of the rent collected, and it goes to the owner with the statement e-mail.' | translate) }}</div>
      </ng-container>
      <div class="text-muted" *ngIf="!inv">{{ 'This statement has no invoice yet.' | translate }}</div>
    </div>
  `,
})
export class PmFeeInvoicePanelComponent implements OnChanges {
  @Input() statementId = 0;
  /** Changes when the statement is agreed, reopened or paid, so the invoice is read again. */
  @Input() status = '';
  @Input() fee = 0;
  @Input() canManage = false;
  @Output() changed = new EventEmitter<void>();
  inv: FeeInvoice | null = null;
  loaded = false;
  busy = false;
  error: string | null = null;

  constructor(private api: PmFeeInvoiceApiService) {}

  ngOnChanges(): void { this.load(); }

  badge(s: string): string { return feeInvoiceBadge(s); }
  statusName(s: string): string { return (FEE_INVOICE_STATUS_LABEL as Record<string, string>)[s] ?? s; }

  load(): void {
    if (!this.statementId || this.status === 'DRAFT') { this.inv = null; this.loaded = false; return; }
    this.api.forStatement(this.statementId).subscribe({
      next: (v) => { this.inv = v ?? null; this.loaded = true; },
      error: () => { this.loaded = false; },
    });
  }

  issue(): void {
    this.busy = true;
    this.error = null;
    this.api.issue(this.statementId).subscribe({
      next: (v) => { this.busy = false; this.inv = v; this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not make the invoice'); },
    });
  }

  download(): void {
    if (!this.inv) return;
    this.busy = true;
    this.api.pdf(this.inv.invoice.id, this.inv.invoice.number).subscribe({
      next: () => (this.busy = false),
      error: () => { this.busy = false; this.error = T('Could not make the file'); },
    });
  }
}
