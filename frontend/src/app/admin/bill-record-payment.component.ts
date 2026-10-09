import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { BillApiService } from '../core/api/bill-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { Bill, SupplierPayment, PaymentMethodOption } from '../core/models';

/**
 * Admin > Bills > detail > Record payment — `/admin/bills/:id/record-payment`. Fourier: "The
 * Purchase order should flow same workflow as Sale order" — the accounts-payable mirror of
 * InvoiceRecordPaymentComponent: there is exactly one bill to pay here, so the whole form is just
 * amount/method/note, capped at this bill's own outstanding amount — no allocation table, no way
 * to spread the payment onto anything else. {@code RecordSupplierPaymentComponent} (the
 * supplier-wide screen) still exists for a purchase order that owes money before it has a bill of
 * its own (e.g. one that's been sent but nothing has arrived against yet), but is no longer how a
 * bill's own payment gets started — see the backend SupplierPaymentService.listOutstandingOrders's
 * javadoc.
 *
 * <p>Fourier: "Add payment method when recording bill payment" — the one point of divergence from
 * RecordSupplierPaymentComponent (which still has no method field at all — see SupplierPayment
 * .method's backend javadoc); this screen's method select mirrors InvoiceRecordPaymentComponent's
 * own exactly, sourced from the same admin-configurable PaymentMethodApiService.listActive().
 */
@Component({
  selector: 'app-bill-record-payment',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Record payment{{ bill ? ' — ' + bill.billNumber : '' }}</h3>
        <a class="btn btn-link" *ngIf="bill" [routerLink]="['/admin/bills', bill.id]">Back to bill</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="bill as b">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>Supplier:</strong> {{ b.supplierName }}</div>
          <div><strong>Bill total:</strong> {{ b.totalAmount | number: '1.2-2' }}</div>
          <div><strong>Paid so far:</strong> {{ b.amountPaid | number: '1.2-2' }}</div>
          <div><strong>Outstanding:</strong> {{ b.amountOutstanding | number: '1.2-2' }}</div>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem; max-width:420px;" *ngIf="bill && !receipt && bill.amountOutstanding > 0">
        <div class="form-group">
          <label class="form-label">Payment amount</label>
          <input class="form-control" type="number" min="0.01" [max]="bill.amountOutstanding" step="0.01" [(ngModel)]="amount" />
          <div class="text-muted" style="font-size:0.8rem;">Up to the outstanding amount — {{ bill.amountOutstanding | number: '1.2-2' }}.</div>
        </div>
        <div class="form-group">
          <label class="form-label">Payment method</label>
          <select class="form-select" [(ngModel)]="method">
            <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Note (optional)</label>
          <input class="form-control" [(ngModel)]="note" />
        </div>
        <button class="btn btn-primary" (click)="submit()" [disabled]="submitting || !canSubmit()">
          {{ submitting ? 'Recording…' : 'Record payment' }}
        </button>
      </div>

      <div class="alert alert-secondary" *ngIf="bill && bill.amountOutstanding <= 0 && !receipt">
        This bill has nothing left outstanding.
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="receipt">
        <div class="alert alert-success">Payment recorded.</div>
        <button class="btn btn-link" (click)="startAnother()" *ngIf="bill && bill.amountOutstanding > 0">Record another payment</button>
      </div>

      <h4 class="mt-3 mb-2">Payment history for this bill</h4>
      <table>
        <thead>
          <tr>
            <th>Receipt #</th>
            <th>Date</th>
            <th class="text-end">Amount</th>
            <th>Method</th>
            <th>Recorded by</th>
            <th>Note</th>
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
            <td colspan="6" class="text-muted">No payments recorded against this bill yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BillRecordPaymentComponent implements OnInit {
  billId!: number;
  bill: Bill | null = null;
  history: SupplierPayment[] = [];

  amount: number | null = null;
  method = '';
  paymentMethods: PaymentMethodOption[] = [];
  note = '';
  receipt: SupplierPayment | null = null;

  submitting = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private billApi: BillApiService,
    private paymentMethodApi: PaymentMethodApiService
  ) {}

  ngOnInit(): void {
    this.billId = Number(this.route.snapshot.paramMap.get('id'));
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      if (methods.length > 0 && !this.method) {
        this.method = methods[0].code;
      }
    });
    this.loadAll();
  }

  private loadAll(): void {
    this.billApi.getById(this.billId).subscribe({
      next: (b) => {
        this.bill = b;
        this.amount = b.amountOutstanding > 0 ? b.amountOutstanding : null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bill'),
    });
    this.billApi.paymentHistory(this.billId).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment history'),
    });
  }

  canSubmit(): boolean {
    return !!this.amount && this.amount > 0 && !!this.bill && this.amount <= this.bill.amountOutstanding && !!this.method;
  }

  submit(): void {
    this.error = null;
    if (!this.canSubmit() || !this.amount) {
      this.error = 'Enter a valid payment amount, up to the outstanding balance, and a payment method.';
      return;
    }
    this.submitting = true;
    this.billApi.recordPayment(this.billId, { amount: this.amount, method: this.method, note: this.note || null }).subscribe({
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
    this.amount = this.bill && this.bill.amountOutstanding > 0 ? this.bill.amountOutstanding : null;
  }
}
