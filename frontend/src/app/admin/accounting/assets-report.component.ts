import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { AssetsReport } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > Assets — a simple listing of Asset-type accounts and their
 * as-of-date balances (Fourier's own scoping choice over a full fixed-asset register with
 * depreciation), reusing the same computation the Balance Sheet's own Assets section already
 * does — see backend AccountingReportService.assets. Each account row can be expanded to show
 * every journal-entry line behind its balance (Fourier: "Add all details"), and the whole report
 * can be exported as Excel/PDF.
 */
@Component({
  selector: 'app-assets-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Assets</h3>
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
            <tr *ngIf="r.assets.length === 0">
              <td colspan="2" class="text-muted">No asset balances as of this date.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th>Total assets</th>
              <th class="text-end">{{ r.totalAssets | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class AssetsReportComponent implements OnInit {
  report: AssetsReport | null = null;
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
    this.reportApi.assets(this.asOf).subscribe({
      next: (r) => (this.report = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load assets report'),
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
    this.reportApi.download('assets', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `assets.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
