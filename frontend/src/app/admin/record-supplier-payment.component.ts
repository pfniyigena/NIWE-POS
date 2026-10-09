import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { SupplierPaymentApiService } from '../core/api/supplier-payment-api.service';
import { Supplier, OutstandingPurchaseOrder, SupplierPayment } from '../core/models';

interface AllocationRow {
  order: OutstandingPurchaseOrder;
  amount: number | null;
}

/**
 * "Add Supplier payment" — the accounts-payable mirror of RecordPaymentComponent, right down to
 * the "manual, oldest-first suggested" allocation model and the inline printable receipt panel
 * (there's no separate reprint-by-id screen on this side either — same reasoning as
 * CustomerPaymentController's equivalent not being used by any frontend print-by-id screen).
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ...
 * equivalent with Invoices)" — once a purchase order has its own Bill, the backend offers that
 * Bill here instead of the order directly (see the backend SupplierPaymentService
 * .listOutstandingOrders's javadoc), so this table's rows are now a mix of bare purchase orders
 * (e.g. one that's been sent but nothing has arrived against yet) and bills — `row.order.orderNumber`
 * already carries whichever number applies, and `row.order.payableType` is threaded straight back
 * into the submitted allocation so the backend knows which one each row actually targets. Still
 * scoped to this case only, same as `RecordPaymentComponent` stays scoped to POS credit sales now
 * that Invoice exists — a bill's own payment is normally recorded from its own dedicated screen
 * (`BillRecordPaymentComponent`), reached from the bill's own detail page.
 */
@Component({
  selector: 'app-record-supplier-payment',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="no-print">
        <div class="flex justify-between align-center mb-3">
          <h3>Record supplier payment{{ supplier ? ' — ' + supplier.name : '' }}</h3>
          <a class="btn btn-link" routerLink="/admin/suppliers">Back to suppliers</a>
        </div>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="!receipt">
          <div class="form-group">
            <label class="form-label">Payment amount</label>
            <input class="form-control" type="number" min="0.01" step="0.01" [(ngModel)]="amount" (ngModelChange)="suggestOldestFirst()" style="max-width:220px;" />
          </div>
          <div class="form-group">
            <label class="form-label">Note (optional)</label>
            <input class="form-control" [(ngModel)]="note" />
          </div>

          <table style="margin-top:0.5rem;">
            <thead>
              <tr>
                <th>Order / Bill #</th>
                <th>Date</th>
                <th class="text-end">Total cost</th>
                <th class="text-end">Outstanding</th>
                <th class="text-end">Allocate</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of allocationRows">
                <td>{{ row.order.orderNumber || '—' }} <span class="text-muted" *ngIf="row.order.payableType === 'BILL'">(Bill)</span></td>
                <td>{{ row.order.orderDate | date: 'medium' }}</td>
                <td class="text-end">{{ row.order.totalCost | number: '1.2-2' }}</td>
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
                <td colspan="5" class="text-muted">This supplier has no outstanding purchase orders.</td>
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
          <button class="btn btn-outline-secondary" (click)="print()">Print receipt</button>
          <button class="btn btn-link" (click)="startAnother()">Record another payment</button>
        </div>

        <h4 class="mt-3 mb-2">Payment history</h4>
        <table>
          <thead>
            <tr>
              <th>Receipt #</th>
              <th>Date</th>
              <th class="text-end">Amount</th>
              <th>Recorded by</th>
              <th>Note</th>
              <th>Applied to</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of history">
              <td>{{ p.receiptNumber || '—' }}</td>
              <td>{{ p.paidAt | date: 'medium' }}</td>
              <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
              <td>{{ p.recordedByName }}</td>
              <td>{{ p.note || '—' }}</td>
              <td>
                <span *ngFor="let a of p.allocations" style="display:block; font-size:0.85rem;">
                  {{ a.orderNumber || '—' }}: {{ a.amountApplied | number: '1.2-2' }}
                </span>
              </td>
            </tr>
            <tr *ngIf="history.length === 0">
              <td colspan="6" class="text-muted">No payments recorded yet.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Printable receipt panel — only this block is visible when printing (see @media print below). -->
      <div class="receipt-print" *ngIf="receipt">
        <h3 style="text-align:center;">Supplier Payment Receipt</h3>
        <p style="text-align:center;">{{ receipt.receiptNumber }}</p>
        <p>Supplier: {{ receipt.supplierName }}</p>
        <p>Date: {{ receipt.paidAt | date: 'medium' }}</p>
        <p>Recorded by: {{ receipt.recordedByName }}</p>
        <hr />
        <p style="font-weight:700;">Amount paid: {{ receipt.amount | number: '1.2-2' }}</p>
        <p style="font-weight:600; margin-top:12px;">Applied to:</p>
        <div *ngFor="let a of receipt.allocations" style="display:flex; justify-content:space-between;">
          <span>{{ a.orderNumber || '—' }}</span>
          <span>{{ a.amountApplied | number: '1.2-2' }}</span>
        </div>
        <hr />
        <p>Remaining balance: {{ receipt.supplierBalanceAfter | number: '1.2-2' }}</p>
        <p *ngIf="receipt.note">Note: {{ receipt.note }}</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .receipt-print {
        display: none;
      }
      @media print {
        .no-print {
          display: none !important;
        }
        .receipt-print {
          display: block;
          background: #fff;
          color: #111;
          font-family: 'Courier New', monospace;
          max-width: 380px;
          margin: 0 auto;
        }
      }
    `,
  ],
})
export class RecordSupplierPaymentComponent implements OnInit {
  supplierId!: number;
  supplier: Supplier | null = null;
  allocationRows: AllocationRow[] = [];
  history: SupplierPayment[] = [];

  amount: number | null = null;
  note = '';
  receipt: SupplierPayment | null = null;

  submitting = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private supplierApi: SupplierApiService,
    private paymentApi: SupplierPaymentApiService
  ) {}

  ngOnInit(): void {
    this.supplierId = Number(this.route.snapshot.paramMap.get('id'));
    this.supplierApi.listAll().subscribe((suppliers) => {
      this.supplier = suppliers.find((s) => s.id === this.supplierId) ?? null;
    });
    this.loadAll();
  }

  private loadAll(): void {
    this.paymentApi.outstandingOrders(this.supplierId).subscribe({
      next: (orders) => (this.allocationRows = orders.map((order) => ({ order, amount: null }))),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load outstanding purchase orders'),
    });
    this.paymentApi.paymentHistory(this.supplierId).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment history'),
    });
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
      .map((r) => ({ purchaseOrderId: r.order.purchaseOrderId, payableType: r.order.payableType, amount: r.amount as number }));
    if (allocations.length === 0 || !this.amount) {
      this.error = 'Enter a payment amount and allocate it to at least one purchase order.';
      return;
    }
    this.submitting = true;
    this.paymentApi.recordPayment(this.supplierId, { amount: this.amount, note: this.note || null, allocations }).subscribe({
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
    window.print();
  }
}
