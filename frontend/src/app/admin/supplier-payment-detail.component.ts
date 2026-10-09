import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SupplierPaymentApiService } from '../core/api/supplier-payment-api.service';
import { openPdfForPrint } from '../core/download-file';
import { SupplierPayment } from '../core/models';

/**
 * Fourier: "Add view details on supplier payments list" — the accounts-payable mirror of
 * CustomerPaymentDetailComponent: the store-wide Supplier Payments list already shows every
 * column inline per row, but there was no way to open one historical payment on its own screen,
 * let alone (re)print its receipt, the way the customer side already has. `/admin/supplier-
 * payments/:id` — reuses SupplierPaymentApiService.getReceipt/pdf, the same two calls the
 * record-payment screens already make right after recording a payment; this is just a routed,
 * revisitable view of that same SupplierPaymentDto shape.
 */
@Component({
  selector: 'app-supplier-payment-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Payment{{ payment ? ' — ' + (payment.receiptNumber || '#' + id) : '' }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-primary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
          <a class="btn btn-link" routerLink="/admin/supplier-payments">Back to supplier payments</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="payment as p">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div>
            <strong>Supplier:</strong>
            <a [routerLink]="['/admin/suppliers', p.supplierId, 'record-payment']">{{ p.supplierName }}</a>
          </div>
          <div><strong>Date:</strong> {{ p.paidAt | date: 'medium' }}</div>
          <div><strong>Amount:</strong> {{ p.amount | number: '1.2-2' }}</div>
          <div><strong>Method:</strong> {{ p.method || '—' }}</div>
          <div><strong>Receipt #:</strong> {{ p.receiptNumber || '—' }}</div>
          <div><strong>Recorded by:</strong> {{ p.recordedByName || '—' }}</div>
          <!-- Always today's outstanding balance, not frozen at the time of this payment — see SupplierPaymentDto's own class javadoc. -->
          <div><strong>Supplier balance:</strong> {{ p.supplierBalanceAfter | number: '1.2-2' }}</div>
        </div>
        <div class="text-muted mt-2" *ngIf="p.note">{{ p.note }}</div>

        <h4 class="mt-3 mb-2">Applied to</h4>
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th class="text-end">Amount applied</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let a of p.allocations">
              <td>{{ a.orderNumber || '—' }}{{ a.payableType === 'BILL' ? ' (Bill)' : '' }}</td>
              <td class="text-end">{{ a.amountApplied | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="p.allocations.length === 0">
              <td colspan="2" class="text-muted">Not applied to any specific order.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class SupplierPaymentDetailComponent implements OnInit {
  id!: number;
  payment: SupplierPayment | null = null;
  error: string | null = null;
  printing = false;

  constructor(private route: ActivatedRoute, private paymentApi: SupplierPaymentApiService) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.paymentApi.getReceipt(this.id).subscribe({
      next: (p) => (this.payment = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment'),
    });
  }

  print(): void {
    this.printing = true;
    this.paymentApi.pdf(this.id).subscribe({
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
