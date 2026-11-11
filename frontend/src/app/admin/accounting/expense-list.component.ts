import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { AuthService } from '../../core/auth.service';
import { downloadBlob } from '../../core/download-file';
import { branchNameOf } from '../../core/branch-util';
import { Account, Branch, Expense } from '../../core/models';

import { T } from '../../core/i18n/t';
/**
 * Admin > Accounting > Expenses — every expense with search, filters and quick views (pending, unpaid, overdue, due soon, mine),
 * approve or reject several at once, and a download of what is on screen. Each row opens its detail page for the single-expense actions.
 */
@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin-bottom:0;">{{ 'Expenses' | translate }}</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/overview">{{ 'Overview' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/budgets">{{ 'Budgets' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/recurring-expenses">{{ 'Recurring expenses' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/accounting/expenses/new">{{ '+ New expense' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
        <button type="button" class="btn btn-sm" *ngFor="let v of views" [ngClass]="view === v.key ? 'btn-primary' : 'btn-outline-primary'" (click)="setView(v.key)">{{ v.label }}</button>
      </div>
      <div class="flex gap-2 mb-3 ex-filters" style="flex-wrap:wrap; align-items:flex-end;">
        <input class="form-control" style="width:14rem;" [placeholder]="'Search payee, memo, number, reference' | translate" [(ngModel)]="q" (keyup.enter)="reload()" name="q" />
        <select class="form-select" style="width:auto;" [(ngModel)]="status" (ngModelChange)="reload()" name="status" [title]="'Status' | translate">
          <option value="">{{ 'Any status' | translate }}</option>
          <option value="PENDING">{{ 'Pending' | translate }}</option>
          <option value="APPROVED">{{ 'Approved' | translate }}</option>
          <option value="REJECTED">{{ 'Rejected' | translate }}</option>
          <option value="VOIDED">{{ 'Voided' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto; max-width:14rem;" [(ngModel)]="accountId" (ngModelChange)="reload()" name="accountId" [title]="'Category' | translate">
          <option [ngValue]="null">{{ 'Any category' | translate }}</option>
          <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
        </select>
        <label class="text-muted" style="font-size:0.8rem;">{{ 'From' | translate }} <input class="form-control" type="date" [(ngModel)]="from" (ngModelChange)="reload()" name="from" /></label>
        <label class="text-muted" style="font-size:0.8rem;">{{ 'To' | translate }} <input class="form-control" type="date" [(ngModel)]="to" (ngModelChange)="reload()" name="to" /></label>
        <input class="form-control" style="width:7rem;" type="number" [placeholder]="'Min amount' | translate" [(ngModel)]="minAmount" (change)="reload()" name="minAmount" />
        <input class="form-control" style="width:7rem;" type="number" [placeholder]="'Max amount' | translate" [(ngModel)]="maxAmount" (change)="reload()" name="maxAmount" />
        <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchFilter" (ngModelChange)="reload()" name="branch" [title]="'Branch' | translate">
          <option [ngValue]="null">{{ 'All branches' | translate }}</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
        <button type="button" class="btn btn-outline-secondary" (click)="reload()">{{ 'Search' | translate }}</button>
        <button type="button" class="btn btn-link" (click)="clear()">{{ 'Clear' | translate }}</button>
        <span style="flex:1;"></span>
        <button type="button" class="btn btn-outline-primary" (click)="download('xlsx')" [disabled]="busy">{{ 'Excel' | translate }}</button>
        <button type="button" class="btn btn-outline-primary" (click)="download('pdf')" [disabled]="busy">{{ 'PDF' | translate }}</button>
      </div>

      <div class="flex gap-2 align-center mb-2" *ngIf="canApprove && selectedCount > 0" style="flex-wrap:wrap;">
        <strong>{{ '{p0} selected' | translate: { p0: (selectedCount) } }}</strong>
        <button type="button" class="btn btn-sm btn-primary" (click)="bulk('APPROVE')" [disabled]="busy">{{ 'Approve selected' | translate }}</button>
        <button type="button" class="btn btn-sm btn-outline-danger" (click)="bulk('REJECT')" [disabled]="busy">{{ 'Reject selected' | translate }}</button>
        <button type="button" class="btn btn-sm btn-link" (click)="selected = {}">{{ 'Clear selection' | translate }}</button>
      </div>
      <div class="alert alert-warning" *ngIf="skipped.length">
        <strong>{{ 'Some were left as they were:' | translate }}</strong>
        <div *ngFor="let s of skipped">{{ s.expenseNumber || ('#' + s.id) }} — {{ s.reason }}</div>
      </div>

      <table class="table-sm">
        <thead>
          <tr>
            <th style="width:2rem;" *ngIf="canApprove"><input type="checkbox" [checked]="allPendingSelected" (change)="toggleAll($event)" [title]="'Select all pending' | translate" /></th>
            <th>{{ 'Number' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Payee' | translate }}</th>
            <th>{{ 'Category' | translate }}</th>
            <th class="text-end">{{ 'Amount' | translate }}</th>
            <th>{{ 'Payment' | translate }}</th>
            <th>{{ 'Due' | translate }}</th>
            <th>{{ 'Approval' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of expenses" class="clickable-row" [routerLink]="['/admin/accounting/expenses', e.id]">
            <td *ngIf="canApprove" (click)="$event.stopPropagation()"><input type="checkbox" *ngIf="e.status === 'PENDING'" [checked]="!!selected[e.id]" (change)="toggle(e.id, $event)" /></td>
            <td>{{ e.expenseNumber }} <span [title]="'Files attached' | translate" *ngIf="e.attachmentCount">📎{{ e.attachmentCount }}</span></td>
            <td>{{ e.expenseDate }}</td>
            <td>{{ e.payeeName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="e.reference">{{ 'Ref {p0}' | translate: { p0: (e.reference) } }}</div></td>
            <td>{{ e.accountName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="e.costCenterName">{{ e.costCenterName }}</div></td>
            <td class="text-end">{{ e.amount | number: '1.2-2' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="e.taxAmount">{{ 'incl. VAT {p0}' | translate: { p0: (e.taxAmount | number: '1.2-2') } }}</div></td>
            <td>
              <span class="badge badge-success" *ngIf="e.paid">{{ 'Paid' | translate }}</span>
              <ng-container *ngIf="!e.paid">
                <span class="badge badge-success" *ngIf="e.settledAt">{{ 'Settled' | translate }}</span>
                <ng-container *ngIf="!e.settledAt">
                  <span class="badge badge-warning" *ngIf="!e.amountPaid">{{ 'Unpaid' | translate }}</span>
                  <span class="badge badge-warning" *ngIf="e.amountPaid">{{ 'Part paid' | translate }}</span>
                  <div class="text-muted" style="font-size:0.75rem;" *ngIf="e.status === 'APPROVED'">{{ 'owed {p0}' | translate: { p0: (e.balance | number: '1.2-2') } }}</div>
                </ng-container>
              </ng-container>
            </td>
            <td>
              <ng-container *ngIf="e.dueDate && e.status === 'APPROVED' && !e.paid && !e.settledAt">
                {{ e.dueDate }}
                <span class="badge badge-danger" *ngIf="e.dueState === 'OVERDUE'">{{ 'Overdue' | translate }}</span>
                <span class="badge badge-warning" *ngIf="e.dueState === 'DUE_SOON'">{{ 'Due soon' | translate }}</span>
              </ng-container>
              <span *ngIf="!(e.dueDate && e.status === 'APPROVED' && !e.paid && !e.settledAt)" class="text-muted">—</span>
            </td>
            <td>
              <span class="badge badge-secondary" *ngIf="e.status === 'PENDING'">{{ 'Pending' | translate }}</span>
              <span class="badge badge-success" *ngIf="e.status === 'APPROVED'">{{ 'Approved' | translate }}</span>
              <span class="badge badge-danger" *ngIf="e.status === 'REJECTED'">{{ 'Rejected' | translate }}</span>
              <span class="badge badge-danger" *ngIf="e.status === 'VOIDED'">{{ 'Voided' | translate }}</span>
            </td>
            <td *ngIf="branches.length > 1">{{ branchName(e.branchId) }}</td>
          </tr>
          <tr *ngIf="!expenses.length && !loading"><td [attr.colspan]="canApprove ? 10 : 9" class="text-muted">{{ filtered ? ('Nothing matches these filters.' | translate) : ('No expenses recorded yet.' | translate) }}</td></tr>
        </tbody>
      </table>
      <p class="text-muted mt-2" *ngIf="expenses.length">{{ '{p0} expense(s) · total {p1}' | translate: { p0: (expenses.length), p1: (total | number: '1.2-2') } }}</p>
    </app-shell>
  `,
  styles: [
    `
      .clickable-row {
        cursor: pointer;
      }
      .clickable-row:hover {
        background: var(--hover, rgba(0, 0, 0, 0.04));
      }
    `,
  ],
})
export class ExpenseListComponent implements OnInit {
  expenses: Expense[] = [];
  accounts: Account[] = [];
  error: string | null = null;
  notice: string | null = null;
  loading = false;
  busy = false;
  skipped: { id: number; expenseNumber: string | null; reason: string }[] = [];
  branches: Branch[] = [];
  branchFilter: number | null = null;

  readonly views = [
    { key: '', label: T('All') },
    { key: 'pending', label: T('Awaiting approval') },
    { key: 'unpaid', label: T('Unpaid bills') },
    { key: 'overdue', label: T('Overdue') },
    { key: 'due-soon', label: T('Due soon') },
    { key: 'mine', label: T('Entered by me') },
  ];
  view = '';
  q = '';
  status = '';
  accountId: number | null = null;
  from = '';
  to = '';
  minAmount: number | null = null;
  maxAmount: number | null = null;
  selected: Record<number, boolean> = {};

  constructor(
    private expenseApi: ExpenseApiService,
    private accountApi: AccountApiService,
    private branchApi: BranchApiService,
    private auth: AuthService,
    private route: ActivatedRoute
  ) {}

  get canApprove(): boolean {
    return this.auth.isAdmin() || this.auth.hasPermission('APPROVE_EXPENSE');
  }

  get filtered(): boolean {
    return !!(this.view || this.q || this.status || this.accountId || this.from || this.to || this.minAmount != null || this.maxAmount != null || this.branchFilter);
  }

  get total(): number {
    return this.expenses.reduce((s, e) => s + e.amount, 0);
  }

  get selectedCount(): number {
    return Object.keys(this.selected).filter((k) => this.selected[+k]).length;
  }

  get pendingRows(): Expense[] {
    return this.expenses.filter((e) => e.status === 'PENDING');
  }

  get allPendingSelected(): boolean {
    return this.pendingRows.length > 0 && this.pendingRows.every((e) => this.selected[e.id]);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a) });
    const v = this.route.snapshot.queryParamMap.get('view');
    if (v) this.view = v;
    this.reload();
  }

  setView(v: string): void {
    this.view = v;
    this.reload();
  }

  clear(): void {
    this.view = '';
    this.q = '';
    this.status = '';
    this.accountId = null;
    this.from = '';
    this.to = '';
    this.minAmount = null;
    this.maxAmount = null;
    this.branchFilter = null;
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = null;
    this.selected = {};
    this.expenseApi
      .list({ view: this.view, q: this.q.trim(), status: this.status, accountId: this.accountId, from: this.from, to: this.to, minAmount: this.minAmount, maxAmount: this.maxAmount, branchId: this.branchFilter })
      .subscribe({
        next: (e) => {
          this.expenses = e;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? T('Failed to load expenses');
        },
      });
  }

  toggle(id: number, ev: Event): void {
    this.selected = { ...this.selected, [id]: (ev.target as HTMLInputElement).checked };
  }

  toggleAll(ev: Event): void {
    const on = (ev.target as HTMLInputElement).checked;
    const next: Record<number, boolean> = {};
    if (on) this.pendingRows.forEach((e) => (next[e.id] = true));
    this.selected = next;
  }

  bulk(action: 'APPROVE' | 'REJECT'): void {
    const ids = Object.keys(this.selected).filter((k) => this.selected[+k]).map(Number);
    if (!ids.length) return;
    let reason: string | null = null;
    if (action === 'REJECT') {
      reason = prompt('Reason for rejecting these ' + ids.length + ' expense(s) (optional):');
      if (reason === null) return;
    } else if (!confirm(T('Approve {p0} expense(s)? Each one is posted to the accounts.', { p0: ids.length }))) {
      return;
    }
    this.busy = true;
    this.notice = null;
    this.skipped = [];
    this.expenseApi.bulk(ids, action, reason || null).subscribe({
      next: (r) => {
        this.busy = false;
        this.notice = r.done + ' expense(s) ' + (action === 'APPROVE' ? 'approved' : 'rejected') + '.';
        this.skipped = r.skipped;
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? T('Could not complete that');
      },
    });
  }

  download(format: 'xlsx' | 'pdf'): void {
    const today = new Date().toISOString().slice(0, 10);
    const from = this.from || today.slice(0, 8) + '01';
    const to = this.to || today;
    this.busy = true;
    this.expenseApi.registerExport(format, from, to, this.status || null).subscribe({
      next: (b) => {
        this.busy = false;
        downloadBlob(b, `expense-register.${format}`);
      },
      error: () => {
        this.busy = false;
        this.error = T('Could not download the register');
      },
    });
  }
}
