import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { Account, LedgerLine } from '../../core/models';

/** Admin > Accounting > Chart of Accounts > (an account) > Ledger — one account's own dated, running-balance history. */
@Component({
  selector: 'app-account-ledger',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ account ? account.code + ' — ' + account.name : 'Ledger' }}</h3>
        <a class="btn btn-link" routerLink="/admin/accounting/accounts">Back to chart of accounts</a>
      </div>
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
            <th>Date</th>
            <th>Entry</th>
            <th>Memo / description</th>
            <th class="text-end">Debit</th>
            <th class="text-end">Credit</th>
            <th class="text-end">Balance</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let line of lines">
            <td>{{ line.entryDate | date: 'mediumDate' }}</td>
            <td><a [routerLink]="['/admin/accounting/journal-entries', line.journalEntryId]">{{ line.entryNumber }}</a></td>
            <td class="text-muted">{{ line.description || line.memo || '—' }}</td>
            <td class="text-end">{{ line.debit > 0 ? (line.debit | number: '1.2-2') : '' }}</td>
            <td class="text-end">{{ line.credit > 0 ? (line.credit | number: '1.2-2') : '' }}</td>
            <td class="text-end">{{ line.runningBalance | number: '1.2-2' }}</td>
          </tr>
          <tr *ngIf="lines.length === 0">
            <td colspan="6" class="text-muted">No activity in this date range.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class AccountLedgerComponent implements OnInit {
  accountId!: number;
  account: Account | null = null;
  lines: LedgerLine[] = [];
  from: string | null = null;
  to: string | null = null;
  error: string | null = null;

  constructor(
    private accountApi: AccountApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.accountId = Number(this.route.snapshot.paramMap.get('id'));
    // Default range: the last 90 days — a wider window than the reports' own 30-day default,
    // since a single account's ledger is usually browsed further back than a store-wide report.
    const today = new Date();
    const ninetyDaysAgo = new Date(today);
    ninetyDaysAgo.setDate(today.getDate() - 90);
    this.to = today.toISOString().slice(0, 10);
    this.from = ninetyDaysAgo.toISOString().slice(0, 10);

    this.accountApi.getById(this.accountId).subscribe({
      next: (a) => (this.account = a),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load account'),
    });
    this.load();
  }

  load(): void {
    this.accountApi.ledger(this.accountId, this.from, this.to).subscribe({
      next: (lines) => (this.lines = lines),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load ledger'),
    });
  }
}
