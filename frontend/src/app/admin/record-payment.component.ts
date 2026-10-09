import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CustomerApiService } from '../core/api/customer-api.service';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Customer, CreditSummary, OutstandingOrder, CustomerPayment, PaymentMethodOption } from '../core/models';

interface AllocationRow {
  order: OutstandingOrder;
  amount: number | null;
}

/**
 * Task family: "Customer payment later" / "Partial settlement" / "Multiple payments against one
 * order" / "Payment allocation to orders" / "Payment receipt" / "Payment history". ADMIN-only
 * (per the user's "who settles" answer), reached from the Customer detail screen's "Record
 * payment" link.
 *
 * <p>Allocation is "manual, oldest-first suggested" (the user's explicit choice): every
 * outstanding order gets its own editable amount field; {@link suggestOldestFirst} fills them
 * greedily oldest-first whenever the payment amount changes, but the admin is free to edit any
 * row by hand before submitting — the backend only cares that the edited amounts sum to exactly
 * the payment amount (see CustomerCreditService.recordPayment's javadoc).
 *
 * <p>Fourier's own later follow-up: "Let record payment per invoice not per customer (all
 * outstanding invoices)" — the allocation table below now only ever lists POS credit sales
 * (checkout "pay on credit" tenders that were never invoiced); an Invoice is paid from its own
 * dedicated screen instead (see InvoiceRecordPaymentComponent), which always applies the whole
 * amount to that one invoice and can never spread it across any others. {@code
 * CustomerCreditService.listOutstandingOrders} still returns both kinds — this screen just
 * filters the Invoice rows back out, see {@link loadAll}.
 *
 * <p>Fourier: "Change payment receipt printout. Make a PDF with A4" — the receipt used to be its
 * own thermal-receipt-styled {@code window.print()} view; "Print receipt" now opens a real,
 * server-built A4 PDF in a new tab instead (see {@code CustomerCreditService.pdf}), same
 * convention as every other document's own "Print" button.
 */
@Component({
  selector: 'app-record-payment',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0;">Record payment{{ customer ? ' — ' + customer.name : '' }}</h3>
            <!-- Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — this screen now only ever settles a POS credit sale; an invoice has its own "Record payment" button on its own detail screen. -->
            <span class="text-muted" style="font-size:0.85rem;">For POS credit sales only — pay an invoice from its own detail screen.</span>
          </div>
          <a class="btn btn-link" [routerLink]="['/admin/customers', customerId]">Back to customer</a>
        </div>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="summary">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><strong>Credit limit:</strong> {{ summary.creditLimit | number: '1.2-2' }}</div>
            <div><strong>Outstanding balance:</strong> {{ summary.outstandingBalance | number: '1.2-2' }}</div>
            <div><strong>Available credit:</strong> {{ summary.availableCredit | number: '1.2-2' }}</div>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="!receipt">
          <div class="form-group">
            <label class="form-label">Payment amount</label>
            <input class="form-control" type="number" min="0.01" step="0.01" [(ngModel)]="amount" (ngModelChange)="suggestOldestFirst()" style="max-width:220px;" />
          </div>
          <div class="form-group">
            <!-- Task: "To record Payment in Sale Order add payment mode (CASH, CHEQUE, ETC)" — same active-method list checkout/refunds use. -->
            <label class="form-label">Payment method</label>
            <select class="form-select" style="max-width:220px;" [(ngModel)]="method">
              <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Note (optional)</label>
            <input class="form-control" [(ngModel)]="note" />
          </div>

          <table style="margin-top:0.5rem;">
            <thead>
              <tr>
                <th>Receipt #</th>
                <th>Sale date</th>
                <th>Due date</th>
                <th class="text-end">Outstanding</th>
                <th class="text-end">Allocate</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of allocationRows">
                <td>{{ row.order.invoiceNumber || '—' }}</td>
                <td>{{ row.order.completedAt | date: 'medium' }}</td>
                <td>
                  {{ row.order.dueDate || '—' }}
                  <span class="badge badge-danger" *ngIf="isOverdue(row.order)">Overdue</span>
                </td>
                <td class="text-end">{{ row.order.amountOutstanding | number: '1.2-2' }}</td>
                <td class="text-end">
                  <input
                    class="form-control text-end"
                    type="number"
                    min="0"
                    [max]="row.order.amountOutstanding"
                    step="0.01"
                    [(ngModel)]="row.amount"
                    style="max-width:140px; display:inline-block;"
                  />
                </td>
              </tr>
              <tr *ngIf="allocationRows.length === 0">
                <td colspan="5" class="text-muted">This customer has no outstanding POS credit sales. An invoice is paid from its own detail screen instead.</td>
              </tr>
            </tbody>
          </table>

          <div class="flex justify-between align-center mt-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-secondary" type="button" (click)="suggestOldestFirst()" [disabled]="allocationRows.length === 0">
              Suggest oldest-first
            </button>
            <div>
              Allocated: {{ allocatedTotal() | number: '1.2-2' }} / {{ (amount ?? 0) | number: '1.2-2' }}
              <span class="badge badge-danger" *ngIf="!allocationBalances()">Must equal the payment amount</span>
            </div>
          </div>

          <button class="btn btn-primary mt-3" (click)="submit()" [disabled]="submitting || !canSubmit()">
            {{ submitting ? 'Recording…' : 'Record payment' }}
          </button>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="receipt">
          <div class="alert alert-success">Payment recorded.</div>
          <button class="btn btn-outline-secondary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print receipt' }}</button>
          <button class="btn btn-link" (click)="startAnother()">Record another payment</button>
        </div>

        <!-- Fourier's own follow-up: "On Customer payment list no POS payment for cash sale" — this
             history now also merges in this customer's own POS checkout tenders, not just Record
             Payment settlements (see CustomerCreditService.paymentHistory(Long)'s javadoc). -->
        <h4 class="mt-3 mb-2">Payment history</h4>
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Receipt #</th>
              <th>Date</th>
              <th class="text-end">Amount</th>
              <th>Method</th>
              <th>Recorded by</th>
              <th>Note</th>
              <th>Applied to</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of history">
              <td>
                <span class="badge" [class.badge-secondary]="p.source === 'POS_SALE'">
                  {{ p.source === 'POS_SALE' ? 'POS sale' : 'Settlement' }}
                </span>
              </td>
              <td>{{ p.receiptNumber || '—' }}</td>
              <td>{{ p.paidAt | date: 'medium' }}</td>
              <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
              <td>{{ p.method || '—' }}</td>
              <td>{{ p.recordedByName || '—' }}</td>
              <td>{{ p.note || '—' }}</td>
              <td>
                <span *ngFor="let a of p.allocations" style="display:block; font-size:0.85rem;">
                  {{ a.invoiceNumber || '—' }}{{ a.orderType === 'SALES_ORDER' ? ' (Sales order)' : '' }}: {{ a.amountApplied | number: '1.2-2' }}
                </span>
              </td>
            </tr>
            <tr *ngIf="history.length === 0">
              <td colspan="8" class="text-muted">No payments recorded yet.</td>
            </tr>
          </tbody>
        </table>
    </app-shell>
  `,
})
export class RecordPaymentComponent implements OnInit {
  customerId!: number;
  customer: Customer | null = null;
  summary: CreditSummary | null = null;
  allocationRows: AllocationRow[] = [];
  history: CustomerPayment[] = [];

  amount: number | null = null;
  /** Task: "To record Payment in Sale Order add payment mode" — defaults to the first active method once loaded, same convention as ReturnFormComponent.refundMethod. */
  method = '';
  paymentMethods: PaymentMethodOption[] = [];
  note = '';
  receipt: CustomerPayment | null = null;
  printing = false;

  submitting = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private customerApi: CustomerApiService,
    private creditApi: CustomerCreditApiService,
    private paymentMethodApi: PaymentMethodApiService
  ) {}

  ngOnInit(): void {
    this.customerId = Number(this.route.snapshot.paramMap.get('id'));
    this.customerApi.list().subscribe((customers) => {
      this.customer = customers.find((c) => c.id === this.customerId) ?? null;
    });
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      if (methods.length > 0 && !this.method) {
        this.method = methods[0].code;
      }
    });
    this.loadAll();
  }

  private loadAll(): void {
    this.creditApi.creditSummary(this.customerId).subscribe({
      next: (s) => (this.summary = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load credit summary'),
    });
    // Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — an
    // Invoice row is paid from its own dedicated screen now (InvoiceRecordPaymentComponent), so
    // this table only ever offers POS credit sales, even though the backend still returns both.
    this.creditApi.outstandingOrders(this.customerId).subscribe({
      next: (orders) => (this.allocationRows = orders.filter((o) => o.orderType === 'POS_SALE').map((order) => ({ order, amount: null }))),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load outstanding orders'),
    });
    this.creditApi.paymentHistory(this.customerId).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment history'),
    });
  }

  isOverdue(order: OutstandingOrder): boolean {
    return !!order.dueDate && new Date(order.dueDate) < new Date(new Date().toDateString());
  }

  /** Greedily fills each row's allocation oldest-first (allocationRows is already oldest-first from the backend) up to the entered amount. */
  suggestOldestFirst(): void {
    let remaining = this.amount ?? 0;
    for (const row of this.allocationRows) {
      if (remaining <= 0) {
        row.amount = null;
        continue;
      }
      const applied = Math.min(remaining, row.order.amountOutstanding);
      row.amount = Math.round(applied * 100) / 100;
      remaining = Math.round((remaining - applied) * 100) / 100;
    }
  }

  allocatedTotal(): number {
    return this.allocationRows.reduce((sum, r) => sum + (r.amount ?? 0), 0);
  }

  allocationBalances(): boolean {
    const total = Math.round(this.allocatedTotal() * 100);
    const target = Math.round((this.amount ?? 0) * 100);
    return total === target && target > 0;
  }

  canSubmit(): boolean {
    return this.allocationBalances();
  }

  submit(): void {
    this.error = null;
    const allocations = this.allocationRows
      .filter((r) => (r.amount ?? 0) > 0)
      .map((r) => ({ orderId: r.order.orderId, orderType: r.order.orderType, amount: r.amount as number }));
    if (allocations.length === 0 || !this.amount) {
      this.error = 'Enter a payment amount and allocate it to at least one order.';
      return;
    }
    if (!this.method) {
      this.error = 'Select a payment method.';
      return;
    }
    this.submitting = true;
    this.creditApi.recordPayment(this.customerId, { amount: this.amount, method: this.method, note: this.note || null, allocations }).subscribe({
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
    this.amount = null;
    this.note = '';
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
