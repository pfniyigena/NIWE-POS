import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Invoice, CustomerPayment, PaymentMethodOption } from '../core/models';

/**
 * Admin > Invoices > detail > Record payment — `/admin/invoices/:id/record-payment`. Fourier:
 * "Let record payment per invoice not per customer (all outstanding invoices)" — replaces the old
 * entry point from Invoice detail, which used to drop the admin onto the Customer-wide Record
 * Payment screen (an allocation table across every one of that customer's outstanding orders);
 * here there is exactly one invoice to pay, so the whole form is just amount/method/note, capped
 * at this invoice's own outstanding amount — no allocation table, no way to spread the payment
 * onto anything else. See {@code CustomerCreditService.recordInvoicePayment}'s backend javadoc.
 *
 * <p>{@code RecordPaymentComponent} (the customer-wide screen) still exists for settling a POS
 * credit sale that was never invoiced — see that component's own javadoc — but is no longer how a
 * new invoice payment gets started.
 *
 * <p>Fourier: "Change payment receipt printout. Make a PDF with A4" — the receipt used to be its
 * own thermal-receipt-styled {@code window.print()} view (380px, Courier New, print-only CSS);
 * "Print receipt" now opens a real, server-built A4 PDF in a new tab instead (see {@code
 * CustomerCreditService.pdf}), same convention as the Invoice/Sale Order detail screens' own
 * "Print" buttons.
 */
@Component({
  selector: 'app-invoice-record-payment',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Record payment{p0}' | translate: { p0: (inv ? ' — ' + inv.invoiceNumber : '') } }}</h3>
        <a class="btn btn-link" *ngIf="inv" [routerLink]="['/admin/invoices', inv.id]">{{ 'Back to invoice' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="inv as o">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>{{ 'Customer:' | translate }}</strong> {{ o.customerName }}</div>
          <div><strong>{{ 'Invoice total:' | translate }}</strong> {{ o.totalAmount | number: '1.2-2' }}</div>
          <div><strong>{{ 'Paid so far:' | translate }}</strong> {{ o.amountPaid | number: '1.2-2' }}</div>
          <div><strong>{{ 'Outstanding:' | translate }}</strong> {{ o.amountOutstanding | number: '1.2-2' }}</div>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem; max-width:420px;" *ngIf="inv && !receipt && inv.amountOutstanding > 0">
        <div class="form-group">
          <label class="form-label">{{ 'Payment amount' | translate }}</label>
          <input class="form-control" type="number" min="0.01" [max]="inv.amountOutstanding" step="0.01" [(ngModel)]="amount" />
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Up to the outstanding amount — {p0}.' | translate: { p0: (inv.amountOutstanding | number: '1.2-2') } }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Payment method' | translate }}</label>
          <select class="form-select" [(ngModel)]="method">
            <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Note (optional)' | translate }}</label>
          <input class="form-control" [(ngModel)]="note" />
        </div>
        <button class="btn btn-primary" (click)="submit()" [disabled]="submitting || !canSubmit()">
          {{ submitting ? ('Recording…' | translate) : ('Record payment' | translate) }}
        </button>
      </div>

      <div class="alert alert-secondary" *ngIf="inv && inv.amountOutstanding <= 0 && !receipt">
        {{ 'This invoice has nothing left outstanding.' | translate }}
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="receipt">
        <div class="alert alert-success">{{ 'Payment recorded.' | translate }}</div>
        <button class="btn btn-outline-secondary" [disabled]="printing" (click)="print()">{{ printing ? ('Opening…' | translate) : ('Print receipt' | translate) }}</button>
        <button class="btn btn-link" (click)="startAnother()" *ngIf="inv && inv.amountOutstanding > 0">{{ 'Record another payment' | translate }}</button>
      </div>

      <h4 class="mt-3 mb-2">{{ 'Payment history for this invoice' | translate }}</h4>
      <table>
        <thead>
          <tr>
            <th>{{ 'Receipt #' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th class="text-end">{{ 'Amount' | translate }}</th>
            <th>{{ 'Method' | translate }}</th>
            <th>{{ 'Recorded by' | translate }}</th>
            <th>{{ 'Note' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of history">
            <td>{{ p.receiptNumber || '—' }}</td>
            <td>{{ p.paidAt | date: 'medium' }}</td>
            <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
            <td>{{ p.method || '—' }}</td>
            <td>{{ p.recordedByName || '—' }}</td>
            <td>{{ p.note || '—' }}</td>
          </tr>
          <tr *ngIf="history.length === 0">
            <td colspan="6" class="text-muted">{{ 'No payments recorded against this invoice yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class InvoiceRecordPaymentComponent implements OnInit {
  invoiceId!: number;
  inv: Invoice | null = null;
  history: CustomerPayment[] = [];

  amount: number | null = null;
  method = '';
  paymentMethods: PaymentMethodOption[] = [];
  note = '';
  receipt: CustomerPayment | null = null;
  printing = false;

  submitting = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private invoiceApi: InvoiceApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private creditApi: CustomerCreditApiService
  ) {}

  ngOnInit(): void {
    this.invoiceId = Number(this.route.snapshot.paramMap.get('id'));
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      if (methods.length > 0 && !this.method) {
        this.method = methods[0].code;
      }
    });
    this.loadAll();
  }

  private loadAll(): void {
    this.invoiceApi.getById(this.invoiceId).subscribe({
      next: (inv) => {
        this.inv = inv;
        this.amount = inv.amountOutstanding > 0 ? inv.amountOutstanding : null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load invoice'),
    });
    this.invoiceApi.paymentHistory(this.invoiceId).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment history'),
    });
  }

  canSubmit(): boolean {
    return !!this.amount && this.amount > 0 && !!this.inv && this.amount <= this.inv.amountOutstanding && !!this.method;
  }

  submit(): void {
    this.error = null;
    if (!this.canSubmit() || !this.amount) {
      this.error = 'Enter a valid payment amount, up to the outstanding balance, and a payment method.';
      return;
    }
    this.submitting = true;
    this.invoiceApi.recordPayment(this.invoiceId, { amount: this.amount, method: this.method, note: this.note || null }).subscribe({
      next: (payment) => {
        this.submitting = false;
        this.receipt = payment;
        this.loadAll();
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Failed to record payment';
      },
    });
  }

  startAnother(): void {
    this.receipt = null;
    this.note = '';
    this.amount = this.inv && this.inv.amountOutstanding > 0 ? this.inv.amountOutstanding : null;
  }

  print(): void {
    if (!this.receipt?.id) return;
    this.printing = true;
    this.creditApi.pdf(this.receipt.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to open receipt PDF';
      },
    });
  }
}
