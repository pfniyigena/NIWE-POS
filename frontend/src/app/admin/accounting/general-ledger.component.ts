import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { GeneralLedgerAccount } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Reports > General Ledger (Fourier: "General ledger ... add them") — every
 * account's own ledger, back to back, for the chosen date range. Distinct from the "Ledger" entry
 * under Journals Reports, which jumps into Chart of Accounts and shows exactly one account at a
 * time (see AccountLedgerComponent) — this is the traditional, all-accounts general ledger book,
 * reuses the exact same per-line shape (LedgerLine) that single-account view already does, and can
 * be exported the same Excel/PDF way every other report here can.
 */
@Component({
  selector: 'app-general-ledger',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>General ledger</h3>
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

      <div *ngFor="let a of accounts" class="card" style="padding:1rem; margin-bottom:1rem;">
        <h4 class="mb-2" style="font-size:1rem;">
          {{ a.accountCode }} — {{ a.accountName }}
          <span class="text-muted" style="font-size:0.85rem; font-weight:400;">({{ a.accountType }})</span>
        </h4>
        <table class="table-sm">
          <thead>
            <tr>
              <th>Date</th>
              <th>Entry</th>
              <th>Memo / description</th>
              <th class="text-end">Debit</th>
              <th class="text-end">Credit</th>
              <th class="text-end">Balance</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td colspan="5" class="text-muted">Opening balance</td>
              <td class="text-end">{{ a.openingBalance | number: '1.2-2' }}</td>
            </tr>
            <tr *ngFor="let line of a.lines">
              <td>{{ line.entryDate | date: 'mediumDate' }}</td>
              <td><a [routerLink]="['/admin/accounting/journal-entries', line.journalEntryId]">{{ line.entryNumber }}</a></td>
              <td class="text-muted">{{ line.description || line.memo || '—' }}</td>
              <td class="text-end">{{ line.debit > 0 ? (line.debit | number: '1.2-2') : '' }}</td>
              <td class="text-end">{{ line.credit > 0 ? (line.credit | number: '1.2-2') : '' }}</td>
              <td class="text-end">{{ line.runningBalance | number: '1.2-2' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th colspan="5" class="text-end">Closing balance</th>
              <th class="text-end">{{ a.closingBalance | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>
      </div>
      <p *ngIf="!loading && accounts.length === 0" class="text-muted">No activity in this date range.</p>
    </app-shell>
  `,
})
export class GeneralLedgerComponent implements OnInit {
  accounts: GeneralLedgerAccount[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;
  loading = false;
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
    this.loading = true;
    this.error = null;
    this.reportApi.generalLedger(this.from, this.to).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load general ledger';
        this.loading = false;
      },
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    this.error = null;
    this.exporting = true;
    const params: Record<string, string> = {};
    if (this.from) params['from'] = this.from;
    if (this.to) params['to'] = this.to;
    this.reportApi.download('general-ledger', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `general-ledger.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
