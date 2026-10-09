import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountingReportApiService } from '../../core/api/accounting-report-api.service';
import { JournalEntry } from '../../core/models';
import { downloadBlob } from '../../core/download-file';

/**
 * Admin > Accounting > Journals Reports > Journals Report — every journal entry in a date range,
 * same rows as the plain Journal Entries list but date-scoped for the reports menu. Each entry
 * can be expanded to show its own lines inline (Fourier: "Add all details") — JournalEntryDto
 * already carries them as {@code lines}, so no extra backend call is needed to show them — and
 * the whole report can be exported as Excel/PDF.
 */
@Component({
  selector: 'app-journals-report',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Journals report</h3>
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
            <th>Entry #</th>
            <th>Date</th>
            <th>Source</th>
            <th>Memo</th>
            <th class="text-end">Total debit</th>
            <th class="text-end">Total credit</th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let e of entries">
            <tr style="cursor:pointer;" (click)="toggleDetails(e)">
              <td>
                <a [routerLink]="['/admin/accounting/journal-entries', e.id]" (click)="$event.stopPropagation()">{{ e.entryNumber }}</a>
                <span class="text-muted" *ngIf="e.lines.length">{{ isExpanded(e) ? '▾' : '▸' }}</span>
              </td>
              <td>{{ e.entryDate | date: 'medium' }}</td>
              <td>{{ e.sourceType || '—' }}</td>
              <td class="text-muted">{{ e.memo || '—' }}</td>
              <td class="text-end">{{ e.totalDebit | number: '1.2-2' }}</td>
              <td class="text-end">{{ e.totalCredit | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="isExpanded(e) && e.lines.length">
              <td colspan="6">
                <table class="table-sm" style="margin:0;">
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th>Description</th>
                      <th>Cost center</th>
                      <th class="text-end">Debit</th>
                      <th class="text-end">Credit</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let l of e.lines">
                      <td>{{ l.accountCode }} — {{ l.accountName }}</td>
                      <td class="text-muted">{{ l.description || '—' }}</td>
                      <td class="text-muted">{{ l.costCenterName || '—' }}</td>
                      <td class="text-end">{{ l.debit > 0 ? (l.debit | number: '1.2-2') : '' }}</td>
                      <td class="text-end">{{ l.credit > 0 ? (l.credit | number: '1.2-2') : '' }}</td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="entries.length === 0">
            <td colspan="6" class="text-muted">No journal entries in this date range.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class JournalsReportComponent implements OnInit {
  entries: JournalEntry[] = [];
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
    this.reportApi.journals(this.from, this.to).subscribe({
      next: (entries) => (this.entries = entries),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load journals report'),
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
    this.reportApi.download('journals', params, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `journals-report.${format}`);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? 'Export failed';
      },
    });
  }
}
