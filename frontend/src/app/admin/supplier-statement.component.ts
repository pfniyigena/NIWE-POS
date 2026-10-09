import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ShellComponent } from '../shared/shell.component';
import { ReportApiService, ReportFilter } from '../core/api/report-api.service';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { Supplier } from '../core/models';

interface SupplierStatementRow {
  orderId: number;
  orderNumber: string;
  status: string;
  orderDate: string;
  expectedDate: string | null;
  lineCount: number;
  quantityOrdered: number;
  quantityReceived: number;
  totalCost: number;
}

/**
 * Supplier Statement (Task 5: "Add Supplier Statement") — every purchase order placed with one
 * chosen supplier in a date range, plus running totals. Hand-built rather than plugged into the
 * generic ReportViewerComponent (see report-defs.ts), same reasoning as CustomerStatementComponent:
 * it needs a supplier picker, a filter shape none of the other Inventory reports have, and being
 * scoped to one supplier means there's no register/category filter to show alongside it. Reuses
 * the same backend (ReportController/ReportService/ReportExportService) and the same
 * ReportApiService every other report uses — this is effectively another Inventory report, just
 * with its own dedicated screen reached from Admin > Inventory > Suppliers instead of the
 * Inventory Reports picker (mirroring how Customer Statement is reached from the Customers group,
 * not the general Reports picker).
 */
@Component({
  selector: 'app-supplier-statement',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Supplier statement</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;">
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="margin:0; min-width:220px;">
            <label class="form-label">Supplier</label>
            <select class="form-select" [(ngModel)]="supplierId">
              <option [ngValue]="null">Choose a supplier…</option>
              <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
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
          <button class="btn btn-primary" (click)="run()" [disabled]="loading || !supplierId">
            {{ loading ? 'Running…' : 'Run statement' }}
          </button>
          <span class="grow"></span>
          <button class="btn btn-outline-secondary" (click)="exportAs('xlsx')" [disabled]="exporting || !supplierId">Export Excel</button>
          <button class="btn btn-outline-secondary" (click)="exportAs('pdf')" [disabled]="exporting || !supplierId">Export PDF</button>
        </div>
      </div>

      <div style="overflow-x:auto;">
        <table>
          <thead>
            <tr>
              <th>Order #</th>
              <th>Status</th>
              <th>Order date</th>
              <th>Expected</th>
              <th class="text-end">Lines</th>
              <th class="text-end">Qty ordered</th>
              <th class="text-end">Qty received</th>
              <th class="text-end">Total cost</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of rows">
              <td>{{ row.orderNumber }}</td>
              <td>{{ row.status.replace('_', ' ') }}</td>
              <td>{{ row.orderDate | date: 'medium' }}</td>
              <td>{{ row.expectedDate ? (row.expectedDate | date: 'mediumDate') : '—' }}</td>
              <td class="text-end">{{ row.lineCount }}</td>
              <td class="text-end">{{ row.quantityOrdered }}</td>
              <td class="text-end">{{ row.quantityReceived }}</td>
              <td class="text-end">{{ row.totalCost | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="rows.length === 0">
              <td colspan="8" class="text-muted">
                {{ hasRun ? 'No purchase orders for this supplier in this range.' : 'Choose a supplier and click "Run statement".' }}
              </td>
            </tr>
          </tbody>
          <tfoot *ngIf="rows.length > 0">
            <tr style="font-weight:700;">
              <td colspan="5" class="text-end">Totals</td>
              <td class="text-end">{{ totalQuantityOrdered() }}</td>
              <td class="text-end">{{ totalQuantityReceived() }}</td>
              <td class="text-end">{{ totalCost() | number: '1.2-2' }}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </app-shell>
  `,
})
export class SupplierStatementComponent implements OnInit {
  suppliers: Supplier[] = [];
  supplierId: number | null = null;
  rows: SupplierStatementRow[] = [];

  filter: ReportFilter = { from: null, to: null, registerId: null };

  loading = false;
  exporting = false;
  hasRun = false;
  error: string | null = null;

  constructor(private reportApi: ReportApiService, private supplierApi: SupplierApiService) {}

  ngOnInit(): void {
    this.supplierApi.listAll().subscribe((s) => (this.suppliers = s));

    // Default to the last 30 days, matching every other report screen.
    const today = new Date();
    const past = new Date();
    past.setDate(past.getDate() - 30);
    this.filter.to = today.toISOString().slice(0, 10);
    this.filter.from = past.toISOString().slice(0, 10);
  }

  run(): void {
    if (!this.supplierId) return;
    this.error = null;
    this.loading = true;
    this.reportApi.run('supplier-statement', { ...this.filter, supplierId: this.supplierId }).subscribe({
      next: (rows) => {
        this.loading = false;
        this.hasRun = true;
        this.rows = rows as SupplierStatementRow[];
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to run statement';
      },
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    if (!this.supplierId) return;
    this.error = null;
    this.exporting = true;
    this.reportApi.download('supplier-statement', { ...this.filter, supplierId: this.supplierId }, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `supplier-statement.${format}`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }

  totalQuantityOrdered(): number {
    return this.rows.reduce((sum, r) => sum + r.quantityOrdered, 0);
  }

  totalQuantityReceived(): number {
    return this.rows.reduce((sum, r) => sum + r.quantityReceived, 0);
  }

  totalCost(): number {
    return this.rows.reduce((sum, r) => sum + r.totalCost, 0);
  }
}
