import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { JournalEntryApiService } from '../../core/api/journal-entry-api.service';
import { JournalEntry } from '../../core/models';

/**
 * Admin > Accounting > Journal Entries — every entry ever posted, automatic (from POS, Sales,
 * Purchasing, Manufacturing — see the backend AccountingService's class javadoc for the full
 * caller list) or manual (entered here for adjustments like rent or owner contributions).
 */
@Component({
  selector: 'app-journal-entry-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Journal entries</h3>
        <a class="btn btn-primary" routerLink="/admin/accounting/journal-entries/new">+ New manual entry</a>
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
          <tr *ngFor="let e of entries">
            <td><a [routerLink]="['/admin/accounting/journal-entries', e.id]">{{ e.entryNumber }}</a></td>
            <td>{{ e.entryDate | date: 'medium' }}</td>
            <td>
              <span class="badge" [ngClass]="e.sourceType === 'MANUAL' ? 'badge-secondary' : 'badge-success'">{{ sourceLabel(e.sourceType) }}</span>
            </td>
            <td class="text-muted">{{ e.memo || '—' }}</td>
            <td class="text-end">{{ e.totalDebit | number: '1.2-2' }}</td>
            <td class="text-end">{{ e.totalCredit | number: '1.2-2' }}</td>
          </tr>
          <tr *ngIf="entries.length === 0">
            <td colspan="6" class="text-muted">
              No journal entries yet — they'll appear here automatically once Accounting is enabled (Admin &gt; Accounting &gt; Settings) and a sale, purchase, payment or production run is recorded.
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class JournalEntryListComponent implements OnInit {
  entries: JournalEntry[] = [];
  error: string | null = null;

  private static readonly LABELS: Record<string, string> = {
    MANUAL: 'Manual',
    POS_SALE: 'POS sale',
    POS_RETURN: 'POS return',
    SALES_ORDER_FULFILLMENT: 'Sales order fulfillment',
    SALES_ORDER_RETURN: 'Sales order return',
    PURCHASE_RECEIPT: 'Purchase receipt',
    PURCHASE_RETURN: 'Purchase return',
    SUPPLIER_PAYMENT: 'Supplier payment',
    STOCK_ADJUSTMENT: 'Stock adjustment',
    MANUFACTURING_RUN: 'Manufacturing run',
    CUSTOMER_PAYMENT: 'Customer payment',
    EXPENSE: 'Expense',
    INCOME: 'Income',
  };

  constructor(private journalEntryApi: JournalEntryApiService) {}

  ngOnInit(): void {
    this.journalEntryApi.listAll().subscribe({
      next: (entries) => (this.entries = entries),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load journal entries'),
    });
  }

  sourceLabel(sourceType: string | null): string {
    if (!sourceType) return '—';
    return JournalEntryListComponent.LABELS[sourceType] ?? sourceType;
  }
}
