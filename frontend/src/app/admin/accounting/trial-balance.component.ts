import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { TrialBalanceRow } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > Trial Balance / Trial Nets / Trial Balance &amp; Nets — all
 * three menu items read this exact same {@code /accounting/reports/trial-balance} endpoint (see
 * the backend AccountingReportController's own javadoc); they differ only in which columns this
 * component shows, driven by the route's own {@code data.mode} ('totals' hides the Balance
 * column, 'nets' hides Debit/Credit, 'full' — the default, used by the original Trial Balance
 * route — shows all of them). Each account row can be expanded to show every journal-entry line
 * that rolled into its totals (Fourier: "Add all details"), and the whole report can be exported
 * as Excel/PDF, same as every other report added in that pass.
 */
@Component({
  selector: 'app-trial-balance',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
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
            <th>Code</th>
            <th>Account</th>
            <th class="text-end" *ngIf="mode !== 'nets'">Debit</th>
            <th class="text-end" *ngIf="mode !== 'nets'">Credit</th>
            <th class="text-end" *ngIf="mode !== 'totals'">Balance</th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let r of rows">
            <tr style="cursor:pointer;" (click)="toggleDetails(r)">
              <td>{{ r.accountCode }} <span class="text-muted" *ngIf="r.details.length">{{ isExpanded(r) ? '▾' : '▸' }}</span></td>
              <td>{{ r.accountName }}</td>
              <td class="text-end" *ngIf="mode !== 'nets'">{{ r.totalDebit | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="mode !== 'nets'">{{ r.totalCredit | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="mode !== 'totals'">{{ r.balance | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="isExpanded(r) && r.details.length">
              <td [attr.colspan]="mode === 'full' ? 5 : 4">
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
                    <tr *ngFor="let d of r.details">
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
            <td [attr.colspan]="mode === 'full' ? 5 : 4" class="text-muted">No activity in this date range.</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th colspan="2" class="text-end">Total</th>
            <th class="text-end" *ngIf="mode !== 'nets'">{{ totalDebit() | number: '1.2-2' }}</th>
            <th class="text-end" *ngIf="mode !== 'nets'">{{ totalCredit() | number: '1.2-2' }}</th>
            <th *ngIf="mode !== 'totals'"></th>
          </tr>
        </tfoot>
      </table>
    </app-shell>
  `,
})
export class TrialBalanceComponent implements OnInit {
  rows: TrialBalanceRow[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;
  mode: 'full' | 'totals' | 'nets' = 'full';
  title = 'Trial balance';
  exporting = false;
  private expandedRows = new Set<unknown>();

  constructor(
    private reportApi: AccountingReportApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.mode = (this.route.snapshot.data['mode'] as 'full' | 'totals' | 'nets') ?? 'full';
    this.title = (this.route.snapshot.data['title'] as string) ?? 'Trial balance';
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.to = today.toISOString().slice(0, 10);
    this.from = thirtyDaysAgo.toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.expandedRows.clear();
    this.reportApi.trialBalance(this.from, this.to).subscribe({
      next: (rows) => (this.rows = rows),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load trial balance'),
    });
  }

  totalDebit(): number {
    return this.rows.reduce((sum, r) => sum + r.totalDebit, 0);
  }

  totalCredit(): number {
    return this.rows.reduce((sum, r) => sum + r.totalCredit, 0);
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
    const params: Record<string, string> = { reportTitle: this.title };
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    this.reportApi.download('trial-balance', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `${this.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
