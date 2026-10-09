import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { ExpenseIncomeGroupBy, GroupedAmount } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * One component backs all sixteen "Expenses ..."/"Incomes ..." segmented+periodic report menu
 * items (by Category/Vendor/Staff/Client, and Daily/Weekly/Monthly/Yearly, for each direction) —
 * see the backend AccountingReportService.expensesGrouped/incomesGrouped javadoc for why one
 * method (per direction) already covers all eight of that direction's own reports; this is that
 * same "one generic component, driven by route data" pattern the original POS Reports package
 * established with ReportViewerComponent/REPORT_DEFS, just for these sixteen instead of a
 * REPORT_DEFS entry each. Each group row can be expanded to show every individual Expense/Income
 * entry that rolled into it (Fourier: "Add all details"), and the whole report can be exported
 * as Excel/PDF, same as every other report.
 */
@Component({
  selector: 'app-expense-income-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ title }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" (click)="exportAs('xlsx')" [disabled]="exporting">Export Excel</button>
          <button class="btn btn-outline-secondary" (click)="exportAs('pdf')" [disabled]="exporting">Export PDF</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3">
        <div class="form-group">
          <label class="form-label">From</label>
          <app-date-picker [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
        </div>
        <div class="form-group">
          <label class="form-label">To</label>
          <app-date-picker [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>{{ labelHeader }}</th>
            <th class="text-end">Count</th>
            <th class="text-end">Amount</th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let row of rows">
            <tr style="cursor:pointer;" (click)="toggleDetails(row)">
              <td>{{ row.label }} <span class="text-muted" *ngIf="row.details.length">{{ isExpanded(row) ? '▾' : '▸' }}</span></td>
              <td class="text-end">{{ row.count }}</td>
              <td class="text-end">{{ row.amount | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="isExpanded(row) && row.details.length">
              <td colspan="3">
                <table class="table-sm" style="margin:0;">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Payee</th>
                      <th>Memo</th>
                      <th class="text-end">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let d of row.details">
                      <td>{{ d.date | date: 'yyyy-MM-dd' }}</td>
                      <td>{{ d.reference || '—' }}</td>
                      <td class="text-muted">{{ d.description || '—' }}</td>
                      <td class="text-end">{{ d.amount | number: '1.2-2' }}</td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="rows.length === 0">
            <td colspan="3" class="text-muted">No {{ direction }}s in this date range.</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th class="text-end">Total</th>
            <th class="text-end">{{ totalCount() }}</th>
            <th class="text-end">{{ totalAmount() | number: '1.2-2' }}</th>
          </tr>
        </tfoot>
      </table>
    </app-shell>
  `,
})
export class ExpenseIncomeReportComponent implements OnInit {
  rows: GroupedAmount[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;
  exporting = false;
  private expandedRows = new Set<unknown>();

  direction: 'expense' | 'income' = 'expense';
  groupBy: ExpenseIncomeGroupBy = 'category';
  title = '';
  labelHeader = '';

  private static readonly LABEL_HEADERS: Record<ExpenseIncomeGroupBy, string> = {
    category: 'Category',
    vendor: 'Vendor',
    staff: 'Staff',
    client: 'Client',
    daily: 'Day',
    weekly: 'Week',
    monthly: 'Month',
    yearly: 'Year',
  };

  constructor(
    private reportApi: AccountingReportApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.direction = (this.route.snapshot.data['direction'] as 'expense' | 'income') ?? 'expense';
    this.groupBy = (this.route.snapshot.data['groupBy'] as ExpenseIncomeGroupBy) ?? 'category';
    this.title = (this.route.snapshot.data['title'] as string) ?? '';
    this.labelHeader = ExpenseIncomeReportComponent.LABEL_HEADERS[this.groupBy];

    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.to = today.toISOString().slice(0, 10);
    this.from = thirtyDaysAgo.toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.expandedRows.clear();
    const call =
      this.direction === 'expense'
        ? this.reportApi.expensesGrouped(this.from, this.to, this.groupBy)
        : this.reportApi.incomesGrouped(this.from, this.to, this.groupBy);
    call.subscribe({
      next: (rows) => (this.rows = rows),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load report'),
    });
  }

  totalCount(): number {
    return this.rows.reduce((sum, r) => sum + r.count, 0);
  }

  totalAmount(): number {
    return this.rows.reduce((sum, r) => sum + r.amount, 0);
  }

  toggleDetails(row: unknown): void {
    if (this.expandedRows.has(row)) this.expandedRows.delete(row);
    else this.expandedRows.add(row);
  }

  isExpanded(row: unknown): boolean {
    return this.expandedRows.has(row);
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    this.error = null;
    this.exporting = true;
    const params: Record<string, string> = { groupBy: this.groupBy, reportTitle: this.title || (this.direction === 'expense' ? 'Expenses' : 'Incomes') };
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    const endpoint = this.direction === 'expense' ? 'expenses' : 'incomes';
    this.reportApi.download(endpoint, params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `${endpoint}-${this.groupBy}.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
