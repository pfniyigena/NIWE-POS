import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { CashFlow } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > Cash Flow Report — direct method (Fourier's own scoping choice
 * over the indirect method, which would need non-cash adjustments like depreciation this app
 * doesn't model). See the backend AccountingReportService.cashFlow's own javadoc for the
 * Operating/Investing/Financing classification rule.
 */
@Component({
  selector: 'app-cash-flow',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Cash flow report</h3>
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
      <ng-container *ngIf="report as r">
        <div class="stat-tile mb-3">
          <div class="stat-label">Opening cash</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ r.openingCash | number: '1.2-2' }}</div>
        </div>

        <h4 class="mb-2">Operating activities</h4>
        <table class="table-sm mb-3">
          <tbody>
            <tr *ngFor="let l of r.operating">
              <td>{{ l.date | date: 'yyyy-MM-dd' }}</td>
              <td>{{ l.memo || '—' }}</td>
              <td class="text-end">{{ l.amount | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="r.operating.length === 0">
              <td colspan="3" class="text-muted">No operating cash movements in this range.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th colspan="2">Net cash from operating activities</th>
              <th class="text-end">{{ r.netOperating | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <h4 class="mb-2">Investing activities</h4>
        <p class="text-muted mb-3">Not modeled in this app — always zero.</p>

        <h4 class="mb-2">Financing activities</h4>
        <table class="table-sm mb-3">
          <tbody>
            <tr *ngFor="let l of r.financing">
              <td>{{ l.date | date: 'yyyy-MM-dd' }}</td>
              <td>{{ l.memo || '—' }}</td>
              <td class="text-end">{{ l.amount | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="r.financing.length === 0">
              <td colspan="3" class="text-muted">No financing cash movements in this range.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th colspan="2">Net cash from financing activities</th>
              <th class="text-end">{{ r.netFinancing | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <div class="stat-tile mb-3">
          <div class="stat-label">Net change in cash</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ r.netChangeInCash | number: '1.2-2' }}</div>
        </div>
        <div class="stat-tile">
          <div class="stat-label">Closing cash</div>
          <div class="stat-value" style="font-size:1.3rem;">{{ r.closingCash | number: '1.2-2' }}</div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class CashFlowComponent implements OnInit {
  report: CashFlow | null = null;
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
    this.reportApi.cashFlow(this.from, this.to).subscribe({
      next: (r) => (this.report = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load cash flow report'),
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    this.error = null;
    this.exporting = true;
    const params: Record<string, string> = {};
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    this.reportApi.download('cash-flow', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `cash-flow-report.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
