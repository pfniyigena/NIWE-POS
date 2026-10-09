import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ShellComponent } from '../shared/shell.component';
import { ReportApiService, ReportFilter } from '../core/api/report-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { Customer } from '../core/models';

interface CustomerStatementRow {
  orderId: number;
  invoiceNumber: string | null;
  completedAt: string;
  registerName: string;
  cashierName: string;
  itemCount: number;
  subtotal: number;
  taxTotal: number;
  grandTotal: number;
  paymentMethods: string;
  /** Additive credit columns — null/zero for a sale with no credit portion. See backend CustomerStatementRow's javadoc. */
  creditAmount: number | null;
  dueDate: string | null;
  amountOutstanding: number | null;
}

/**
 * Customer Statement (Task: "Add Customers Report (Customer Statement)") — every completed sale
 * one chosen customer made in a date range, plus a running total. Hand-built rather than plugged
 * into the generic ReportViewerComponent (see report-defs.ts): it needs a customer picker, a
 * filter type the other seven POS Reports don't have, and the "one customer" scope means there's
 * no register/category filter to show alongside it. Reuses the same backend
 * (ReportController/ReportService/ReportExportService) and the same ReportApiService the seven
 * POS Reports use — this is effectively an eighth report, just with its own dedicated screen and
 * a home under the Customers sidebar group instead of the Reports picker.
 *
 * <p>Fourier: "on the customer ... view button where we can have button to view the list of
 * invoices, payments etc" — arriving from a customer's own row via ?customerId=&customerName=
 * (see CustomerListComponent's "Statement" link) pre-selects that customer and runs the statement
 * immediately, same ?productId=&productName= convention ReportViewerComponent's own Stock
 * Movement History link already uses.
 */
@Component({
  selector: 'app-customer-statement',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Customer statement</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;">
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="margin:0; min-width:220px;">
            <label class="form-label">Customer</label>
            <select class="form-select" [(ngModel)]="customerId">
              <option [ngValue]="null">Choose a customer…</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-group" style="margin:0;">
            <label class="form-label">From</label>
            <app-date-picker [(ngModel)]="filter.from" ></app-date-picker>
          </div>
          <div class="form-group" style="margin:0;">
            <label class="form-label">To</label>
            <app-date-picker [(ngModel)]="filter.to" ></app-date-picker>
          </div>
          <button class="btn btn-primary" (click)="run()" [disabled]="loading || !customerId">
            {{ loading ? 'Running…' : 'Run statement' }}
          </button>
          <span class="grow"></span>
          <button class="btn btn-outline-secondary" (click)="exportAs('xlsx')" [disabled]="exporting || !customerId">Export Excel</button>
          <button class="btn btn-outline-secondary" (click)="exportAs('pdf')" [disabled]="exporting || !customerId">Export PDF</button>
        </div>
      </div>

      <div style="overflow-x:auto;">
        <table>
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Date</th>
              <th>Register</th>
              <th>Cashier</th>
              <th class="text-end">Items</th>
              <th class="text-end">Subtotal</th>
              <th class="text-end">Tax</th>
              <th class="text-end">Grand total</th>
              <th>Payment</th>
              <th class="text-end">Credit</th>
              <th>Due date</th>
              <th class="text-end">Outstanding</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of rows">
              <td>{{ row.invoiceNumber || '—' }}</td>
              <td>{{ row.completedAt | date: 'medium' }}</td>
              <td>{{ row.registerName }}</td>
              <td>{{ row.cashierName }}</td>
              <td class="text-end">{{ row.itemCount }}</td>
              <td class="text-end">{{ row.subtotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ row.taxTotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ row.grandTotal | number: '1.2-2' }}</td>
              <td>{{ row.paymentMethods }}</td>
              <td class="text-end">{{ row.creditAmount ? (row.creditAmount | number: '1.2-2') : '—' }}</td>
              <td>{{ row.dueDate || '—' }}</td>
              <td class="text-end">{{ row.amountOutstanding ? (row.amountOutstanding | number: '1.2-2') : '—' }}</td>
            </tr>
            <tr *ngIf="rows.length === 0">
              <td colspan="12" class="text-muted">
                {{ hasRun ? 'No sales for this customer in this range.' : 'Choose a customer and click "Run statement".' }}
              </td>
            </tr>
          </tbody>
          <tfoot *ngIf="rows.length > 0">
            <tr style="font-weight:700;">
              <td colspan="5" class="text-end">Totals</td>
              <td class="text-end">{{ totalSubtotal() | number: '1.2-2' }}</td>
              <td class="text-end">{{ totalTax() | number: '1.2-2' }}</td>
              <td class="text-end">{{ totalGrand() | number: '1.2-2' }}</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>
    </app-shell>
  `,
})
export class CustomerStatementComponent implements OnInit {
  customers: Customer[] = [];
  customerId: number | null = null;
  rows: CustomerStatementRow[] = [];

  filter: ReportFilter = { from: null, to: null, registerId: null };

  loading = false;
  exporting = false;
  hasRun = false;
  error: string | null = null;

  constructor(private reportApi: ReportApiService, private customerApi: CustomerApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe((c) => (this.customers = c));

    // Default to the last 30 days, matching every other report screen.
    const today = new Date();
    const past = new Date();
    past.setDate(past.getDate() - 30);
    this.filter.to = today.toISOString().slice(0, 10);
    this.filter.from = past.toISOString().slice(0, 10);

    // Fourier: "on the customer ... view button" — arriving via ?customerId= pre-selects that
    // customer and runs the statement right away, instead of leaving the admin to pick it again.
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.run();
    }
  }

  run(): void {
    if (!this.customerId) return;
    this.error = null;
    this.loading = true;
    this.reportApi.run('customer-statement', { ...this.filter, customerId: this.customerId }).subscribe({
      next: (rows) => {
        this.loading = false;
        this.hasRun = true;
        this.rows = rows as CustomerStatementRow[];
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to run statement';
      },
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    if (!this.customerId) return;
    this.error = null;
    this.exporting = true;
    this.reportApi.download('customer-statement', { ...this.filter, customerId: this.customerId }, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `customer-statement.${format}`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }

  totalSubtotal(): number {
    return this.rows.reduce((sum, r) => sum + r.subtotal, 0);
  }

  totalTax(): number {
    return this.rows.reduce((sum, r) => sum + r.taxTotal, 0);
  }

  totalGrand(): number {
    return this.rows.reduce((sum, r) => sum + r.grandTotal, 0);
  }
}
