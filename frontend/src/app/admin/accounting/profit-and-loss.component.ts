import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch, ProfitAndLoss } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > Profit &amp; Loss — Revenue and Expense accounts, net of
 * debits/credits, for a date range. Each account row can be expanded to show every journal-entry
 * line that rolled into its amount (Fourier: "Add all details"), and the whole report can be
 * exported as Excel/PDF.
 */
@Component({
  selector: 'app-profit-and-loss',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Profit &amp; loss</h3>
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
        <!-- Branch P&L: shown once a second branch exists. Entries with no branch (older, or payments/adjustments) count as head office's. -->
        <div class="form-group" *ngIf="branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-select" [(ngModel)]="branchId" (ngModelChange)="load()">
            <option [ngValue]="null">All branches</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>
      </div>
      <ng-container *ngIf="report as r">
        <h4 class="mb-2">Revenue</h4>
        <table class="table-sm mb-3">
          <tbody>
            <ng-container *ngFor="let a of r.revenue">
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
            <tr *ngIf="r.revenue.length === 0">
              <td colspan="2" class="text-muted">No revenue in this range.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th>Total revenue</th>
              <th class="text-end">{{ r.totalRevenue | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <h4 class="mb-2">Expenses</h4>
        <table class="table-sm mb-3">
          <tbody>
            <ng-container *ngFor="let a of r.expenses">
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
            <tr *ngIf="r.expenses.length === 0">
              <td colspan="2" class="text-muted">No expenses in this range.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th>Total expenses</th>
              <th class="text-end">{{ r.totalExpenses | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>

        <div class="stat-tile">
          <div class="stat-label">Net income</div>
          <div class="stat-value" style="font-size:1.3rem;">{{ r.netIncome | number: '1.2-2' }}</div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class ProfitAndLossComponent implements OnInit {
  report: ProfitAndLoss | null = null;
  from: string | null = null;
  to: string | null = null;
  branches: Branch[] = [];
  branchId: number | null = null;
  error: string | null = null;
  exporting = false;
  private expandedRows = new Set<unknown>();

  constructor(private reportApi: AccountingReportApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.to = today.toISOString().slice(0, 10);
    this.from = thirtyDaysAgo.toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.expandedRows.clear();
    this.reportApi.profitAndLoss(this.from, this.to, this.branchId).subscribe({
      next: (r) => (this.report = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load profit & loss'),
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
    const params: Record<string, string> = { reportTitle: 'Profit and Loss' };
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    if (this.branchId != null) params['branchId'] = String(this.branchId);
    this.reportApi.download('profit-and-loss', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `profit-and-loss.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
