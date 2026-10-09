import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { JournalEntryApiService } from '../../core/api/journal-entry-api.service';
import { JournalEntry } from '../../core/models';

/** Admin > Accounting > Journal Entries > (an entry) — one entry's own lines, always balanced (debits == credits), see the backend AccountingService.post's own javadoc. */
@Component({
  selector: 'app-journal-entry-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="entry as e">
        <div class="flex justify-between align-center mb-3">
          <h3 style="margin-bottom:0;">{{ e.entryNumber }}</h3>
          <a class="btn btn-link" routerLink="/admin/accounting/journal-entries">Back to journal entries</a>
        </div>
        <p class="text-muted mb-1">{{ e.entryDate | date: 'medium' }}</p>
        <p class="mb-3" *ngIf="e.memo">{{ e.memo }}</p>
        <table class="table-sm mb-3">
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
            <tr *ngFor="let line of e.lines">
              <td>{{ line.accountCode }} — {{ line.accountName }}</td>
              <td class="text-muted">{{ line.description || '—' }}</td>
              <td class="text-muted">{{ line.costCenterName || '—' }}</td>
              <td class="text-end">{{ line.debit > 0 ? (line.debit | number: '1.2-2') : '' }}</td>
              <td class="text-end">{{ line.credit > 0 ? (line.credit | number: '1.2-2') : '' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th colspan="3" class="text-end">Total</th>
              <th class="text-end">{{ e.totalDebit | number: '1.2-2' }}</th>
              <th class="text-end">{{ e.totalCredit | number: '1.2-2' }}</th>
            </tr>
          </tfoot>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class JournalEntryDetailComponent implements OnInit {
  entry: JournalEntry | null = null;
  error: string | null = null;

  constructor(
    private journalEntryApi: JournalEntryApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.journalEntryApi.getById(id).subscribe({
      next: (e) => (this.entry = e),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load journal entry'),
    });
  }
}
