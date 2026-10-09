import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { JournalEntryApiService } from '../../core/api/journal-entry-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { Account, CostCenter } from '../../core/models';

interface DraftLine {
  accountId: number | null;
  debit: number;
  credit: number;
  description: string;
  costCenterId: number | null;
}

/**
 * Admin > Accounting > Journal Entries > New manual entry — for one-off adjustments no other
 * module posts automatically (e.g. recording an owner contribution, rent, or a utility bill once
 * it's actually paid — see the backend AccountingService.postManufacturingRun's own javadoc for
 * why absorbed overhead is deliberately left as an accrued liability for an entry like this to
 * eventually settle). Must have at least two lines whose debits and credits add up to exactly the
 * same total — enforced client-side for immediate feedback and again server-side (see
 * JournalEntryService.createManual's own javadoc) as the actual source of truth.
 */
@Component({
  selector: 'app-journal-entry-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">New manual journal entry</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3">
        <div class="form-group">
          <label class="form-label">Date</label>
          <app-date-picker name="entryDate" [(ngModel)]="entryDate" ></app-date-picker>
        </div>
        <div class="form-group grow">
          <label class="form-label">Memo</label>
          <input class="form-control" name="memo" [(ngModel)]="memo" placeholder="e.g. Owner contribution, March rent" />
        </div>
      </div>

      <table class="table-sm mb-2">
        <thead>
          <tr>
            <th>Account</th>
            <th>Description</th>
            <th>Cost center</th>
            <th class="text-end">Debit</th>
            <th class="text-end">Credit</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let line of lines; let i = index">
            <td>
              <select class="form-select" [name]="'account' + i" [(ngModel)]="line.accountId">
                <option [ngValue]="null">— Select —</option>
                <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
              </select>
            </td>
            <td><input class="form-control" [name]="'desc' + i" [(ngModel)]="line.description" /></td>
            <td>
              <select class="form-select" [name]="'costCenter' + i" [(ngModel)]="line.costCenterId">
                <option [ngValue]="null">None</option>
                <option *ngFor="let c of costCenters" [ngValue]="c.id">{{ c.code }} — {{ c.name }}</option>
              </select>
            </td>
            <td>
              <input
                class="form-control text-end"
                type="number"
                min="0"
                step="0.01"
                style="width:120px;"
                [name]="'debit' + i"
                [(ngModel)]="line.debit"
                (ngModelChange)="line.credit = 0"
              />
            </td>
            <td>
              <input
                class="form-control text-end"
                type="number"
                min="0"
                step="0.01"
                style="width:120px;"
                [name]="'credit' + i"
                [(ngModel)]="line.credit"
                (ngModelChange)="line.debit = 0"
              />
            </td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeLine(i)">Remove</button></td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th colspan="3" class="text-end">Total</th>
            <th class="text-end">{{ totalDebit() | number: '1.2-2' }}</th>
            <th class="text-end">{{ totalCredit() | number: '1.2-2' }}</th>
            <th></th>
          </tr>
        </tfoot>
      </table>
      <button type="button" class="btn btn-sm btn-outline-secondary mb-3" (click)="addLine()">+ Add line</button>
      <div class="text-muted mb-3" *ngIf="totalDebit() !== totalCredit()">
        Debits and credits must add up to the same total before this entry can be saved (currently {{ totalDebit() | number: '1.2-2' }} vs {{ totalCredit() | number: '1.2-2' }}).
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save</button>
        <a class="btn btn-link" routerLink="/admin/accounting/journal-entries">Cancel</a>
      </div>
    </app-shell>
  `,
})
export class JournalEntryFormComponent implements OnInit {
  accounts: Account[] = [];
  costCenters: CostCenter[] = [];
  entryDate: string | null = null;
  memo = '';
  lines: DraftLine[] = [
    { accountId: null, debit: 0, credit: 0, description: '', costCenterId: null },
    { accountId: null, debit: 0, credit: 0, description: '', costCenterId: null },
  ];
  error: string | null = null;

  constructor(
    private journalEntryApi: JournalEntryApiService,
    private accountApi: AccountApiService,
    private costCenterApi: CostCenterApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.accountApi.listActive().subscribe({
      next: (accounts) => (this.accounts = accounts),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load chart of accounts'),
    });
    this.costCenterApi.listActive().subscribe({ next: (c) => (this.costCenters = c) });
  }

  addLine(): void {
    this.lines.push({ accountId: null, debit: 0, credit: 0, description: '', costCenterId: null });
  }

  removeLine(index: number): void {
    this.lines.splice(index, 1);
  }

  totalDebit(): number {
    return round2(this.lines.reduce((sum, l) => sum + (Number(l.debit) || 0), 0));
  }

  totalCredit(): number {
    return round2(this.lines.reduce((sum, l) => sum + (Number(l.credit) || 0), 0));
  }

  submit(): void {
    this.error = null;
    const usableLines = this.lines.filter((l) => l.accountId != null && ((Number(l.debit) || 0) > 0 || (Number(l.credit) || 0) > 0));
    if (usableLines.length < 2) {
      this.error = 'Add at least two lines, each with an account and either a debit or a credit amount';
      return;
    }
    if (this.totalDebit() !== this.totalCredit()) {
      this.error = 'Debits and credits must add up to exactly the same total';
      return;
    }
    const payload = {
      entryDate: this.entryDate || null,
      memo: this.memo || null,
      lines: usableLines.map((l) => ({
        accountId: l.accountId as number,
        debit: Number(l.debit) || 0,
        credit: Number(l.credit) || 0,
        description: l.description || null,
        costCenterId: l.costCenterId,
      })),
    };
    this.journalEntryApi.createManual(payload).subscribe({
      next: (e) => this.router.navigate(['/admin/accounting/journal-entries', e.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save journal entry'),
    });
  }
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
