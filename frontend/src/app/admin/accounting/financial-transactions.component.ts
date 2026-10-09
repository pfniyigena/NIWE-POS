import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { FinancialTransaction } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/** Admin > Accounting > Journals Reports > Financial Transactions — every journal entry line across every account in a date range, flattened. See backend AccountingReportService.financialTransactions. */
@Component({
  selector: 'app-financial-transactions',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Financial transactions</h3>
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
            <th>Date</th>
            <th>Entry #</th>
            <th>Account</th>
            <th>Memo</th>
            <th>Description</th>
            <th class="text-end">Debit</th>
            <th class="text-end">Credit</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let row of rows">
            <td>{{ row.date | date: 'medium' }}</td>
            <td>{{ row.entryNumber }}</td>
            <td>{{ row.accountCode }} — {{ row.accountName }}</td>
            <td class="text-muted">{{ row.memo || '—' }}</td>
            <td class="text-muted">{{ row.description || '—' }}</td>
            <td class="text-end">{{ row.debit > 0 ? (row.debit | number: '1.2-2') : '' }}</td>
            <td class="text-end">{{ row.credit > 0 ? (row.credit | number: '1.2-2') : '' }}</td>
          </tr>
          <tr *ngIf="rows.length === 0">
            <td colspan="7" class="text-muted">No transactions in this date range.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class FinancialTransactionsComponent implements OnInit {
  rows: FinancialTransaction[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;
  exporting = false;

  constructor(private reportApi: AccountingReportApiService) {}

  ngOnInit(): void {
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.to = today.toISOString().slice(0, 10);
    this.from = thirtyDaysAgo.toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.reportApi.financialTransactions(this.from, this.to).subscribe({
      next: (rows) => (this.rows = rows),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load financial transactions'),
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    this.error = null;
    this.exporting = true;
    const params: Record<string, string> = {};
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    this.reportApi.download('financial-transactions', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `financial-transactions.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
