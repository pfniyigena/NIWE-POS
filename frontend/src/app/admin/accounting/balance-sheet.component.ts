import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { BalanceSheet } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > Balance Sheet — a snapshot as of one date. `currentEarnings` is
 * a live "retained earnings not yet closed to Owner's Equity" figure (see the backend
 * BalanceSheetDto's own javadoc) — shown as its own line, the same way a real balance sheet always
 * carries current-period earnings separately from prior equity, rather than silently folding it in.
 * Each account row can be expanded to show every journal-entry line behind its balance (Fourier:
 * "Add all details"), and the whole report can be exported as Excel/PDF.
 */
@Component({
  selector: 'app-balance-sheet',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Balance sheet</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" (click)="exportAs('xlsx')" [disabled]="exporting">Export Excel</button>
          <button class="btn btn-outline-secondary" (click)="exportAs('pdf')" [disabled]="exporting">Export PDF</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="form-group mb-3">
        <label class="form-label">As of</label>
        <app-date-picker [(ngModel)]="asOf" (ngModelChange)="load()"></app-date-picker>
      </div>
      <ng-container *ngIf="report as r">
        <div class="alert alert-danger mb-3" *ngIf="!r.balanced">
          Assets do not equal liabilities + equity + current earnings — check the Journal Entries list for a Suspense-account posting (see the backend AccountingService.post's own javadoc on when that happens).
        </div>

        <h4 class="mb-2">Assets</h4>
        <table class="table-sm mb-3">
          <tbody>
            <ng-container *ngFor="let a of r.assets">
              <tr style="cursor:pointer;" (click)="toggleDetails(a)">
                <td>{{ a.accountCode }} — {{ a.accountName }} <span class="text-muted" *ngIf="a.details.length">{{ isExpanded(a) ? '▾' : '▸' }}</span></td>
                <td class="text-end">{{ a.amount | number: '1.2-2' }}</td>
              </tr>
              <tr *ngIf="isExpanded(a) && a.details.length">
                <td colspan="2">
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
                      <tr *ngFor="let d of a.details">
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
          </tbody>
          <tfoot>
            <tr>
              <th>Total assets</th>
              <th class="text-end">{{ r.totalAssets | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <h4 class="mb-2">Liabilities</h4>
        <table class="table-sm mb-3">
          <tbody>
            <ng-container *ngFor="let a of r.liabilities">
              <tr style="cursor:pointer;" (click)="toggleDetails(a)">
                <td>{{ a.accountCode }} — {{ a.accountName }} <span class="text-muted" *ngIf="a.details.length">{{ isExpanded(a) ? '▾' : '▸' }}</span></td>
                <td class="text-end">{{ a.amount | number: '1.2-2' }}</td>
              </tr>
              <tr *ngIf="isExpanded(a) && a.details.length">
                <td colspan="2">
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
                      <tr *ngFor="let d of a.details">
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
          </tbody>
          <tfoot>
            <tr>
              <th>Total liabilities</th>
              <th class="text-end">{{ r.totalLiabilities | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <h4 class="mb-2">Equity</h4>
        <table class="table-sm mb-3">
          <tbody>
            <ng-container *ngFor="let a of r.equity">
              <tr style="cursor:pointer;" (click)="toggleDetails(a)">
                <td>{{ a.accountCode }} — {{ a.accountName }} <span class="text-muted" *ngIf="a.details.length">{{ isExpanded(a) ? '▾' : '▸' }}</span></td>
                <td class="text-end">{{ a.amount | number: '1.2-2' }}</td>
              </tr>
              <tr *ngIf="isExpanded(a) && a.details.length">
                <td colspan="2">
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
                      <tr *ngFor="let d of a.details">
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
            <tr>
              <td class="text-muted">Current earnings (not yet closed)</td>
              <td class="text-end">{{ r.currentEarnings | number: '1.2-2' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th>Total equity</th>
              <th class="text-end">{{ (r.totalEquity + r.currentEarnings) | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class BalanceSheetComponent implements OnInit {
  report: BalanceSheet | null = null;
  asOf: string | null = null;
  error: string | null = null;
  exporting = false;
  private expandedRows = new Set<unknown>();

  constructor(private reportApi: AccountingReportApiService) {}

  ngOnInit(): void {
    this.asOf = new Date().toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.expandedRows.clear();
    this.reportApi.balanceSheet(this.asOf).subscribe({
      next: (r) => (this.report = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load balance sheet'),
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
    if (this.asOf) params['asOf'] = this.asOf;
    this.reportApi.download('balance-sheet', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `balance-sheet.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
