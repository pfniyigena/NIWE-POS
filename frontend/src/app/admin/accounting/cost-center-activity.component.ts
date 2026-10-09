import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { CostCenterActivity } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Journals Reports > Cost Centers — each cost center's own total
 * debit/credit/net activity in a date range. Distinct from the plain CRUD list under Admin >
 * Accounting > Cost Centers. Each cost center can be expanded to show every journal-entry line
 * tagged with it (Fourier: "Add all details"), and the whole report can be exported as
 * Excel/PDF.
 */
@Component({
  selector: 'app-cost-center-activity',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Cost centers activity</h3>
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
            <th>Code</th>
            <th>Cost center</th>
            <th class="text-end">Debit</th>
            <th class="text-end">Credit</th>
            <th class="text-end">Net</th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let row of rows">
            <tr style="cursor:pointer;" (click)="toggleDetails(row)">
              <td>{{ row.costCenterCode }} <span class="text-muted" *ngIf="row.details.length">{{ isExpanded(row) ? '▾' : '▸' }}</span></td>
              <td>{{ row.costCenterName }}</td>
              <td class="text-end">{{ row.totalDebit | number: '1.2-2' }}</td>
              <td class="text-end">{{ row.totalCredit | number: '1.2-2' }}</td>
              <td class="text-end">{{ row.net | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="isExpanded(row) && row.details.length">
              <td colspan="5">
                <table class="table-sm" style="margin:0;">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Entry #</th>
                      <th>Description</th>
                      <th class="text-end">Debit</th>
                      <th class="text-end">Credit</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let d of row.details">
                      <td>{{ d.date | date: 'medium' }}</td>
                      <td>{{ d.reference || '—' }}</td>
                      <td class="text-muted">{{ d.description || '—' }}</td>
                      <td class="text-end">{{ d.debit ? (d.debit | number: '1.2-2') : '' }}</td>
                      <td class="text-end">{{ d.credit ? (d.credit | number: '1.2-2') : '' }}</td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="rows.length === 0">
            <td colspan="5" class="text-muted">No cost-center-tagged activity in this date range.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CostCenterActivityComponent implements OnInit {
  rows: CostCenterActivity[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;
  exporting = false;
  private expandedRows = new Set<unknown>();

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
    this.expandedRows.clear();
    this.reportApi.costCenterActivity(this.from, this.to).subscribe({
      next: (rows) => (this.rows = rows),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load cost center activity'),
    });
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
    const params: Record<string, string> = {};
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    this.reportApi.download('cost-centers', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `cost-centers.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
