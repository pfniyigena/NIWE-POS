import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { AuthService } from '../../core/auth.service';
import { Account, CostCenter, ExpenseBudget, ExpenseBudgetStatus } from '../../core/models';

/** Expenses > Budgets — a monthly allowance per expense category or cost center, and how much of it is used in the month shown. */
@Component({
  selector: 'app-expense-budgets',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin-bottom:0;">{{ 'Expense budgets' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/overview">{{ 'Overview' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses">{{ 'All expenses' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <label class="text-muted">{{ 'Year' | translate }} <input class="form-control" style="width:6rem; display:inline-block;" type="number" name="year" [(ngModel)]="year" (change)="load()" /></label>
        <label class="text-muted">{{ 'Month' | translate }}
          <select class="form-select" style="width:auto; display:inline-block;" name="month" [(ngModel)]="month" (ngModelChange)="load()">
            <option *ngFor="let m of months; let i = index" [ngValue]="i + 1">{{ m }}</option>
          </select>
        </label>
      </div>

      <table class="table-sm mb-3">
        <thead><tr><th>{{ 'Budget for' | translate }}</th><th>{{ 'Type' | translate }}</th><th class="text-end">{{ 'Monthly budget' | translate }}</th><th class="text-end">{{ 'Spent' | translate }}</th><th class="text-end">{{ 'Waiting for approval' | translate }}</th><th class="text-end">{{ 'Left' | translate }}</th><th style="width:12rem;">{{ 'Used' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of rows">
            <td>{{ r.label }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="note(r)">{{ note(r) }}</div></td>
            <td>{{ r.kind === 'CATEGORY' ? ('Category' | translate) : ('Cost center' | translate) }}</td>
            <td class="text-end">
              <ng-container *ngIf="editId !== r.budgetId">{{ r.budget | number: '1.0-0' }}</ng-container>
              <input *ngIf="editId === r.budgetId" class="form-control" style="width:8rem; margin-left:auto;" type="number" min="1" name="editAmount" [(ngModel)]="editAmount" />
            </td>
            <td class="text-end">{{ r.spent | number: '1.0-0' }}</td>
            <td class="text-end">{{ r.pending | number: '1.0-0' }}</td>
            <td class="text-end">{{ r.remaining | number: '1.0-0' }}</td>
            <td>
              <div class="ex-track"><div class="ex-fill" [ngClass]="r.state" [style.width.%]="Math.min(100, r.percentUsed)"></div></div>
              <span class="badge" [ngClass]="r.state === 'OVER' ? 'badge-danger' : r.state === 'WARNING' ? 'badge-warning' : 'badge-success'">{{ r.percentUsed | number: '1.0-0' }}%</span>
            </td>
            <td style="white-space:nowrap;">
              <ng-container *ngIf="canManage">
                <button class="btn btn-sm btn-outline-primary" *ngIf="editId !== r.budgetId" (click)="startEdit(r)">{{ 'Change' | translate }}</button>
                <button class="btn btn-sm btn-primary" *ngIf="editId === r.budgetId" (click)="saveEdit(r)">{{ 'Save' | translate }}</button>
                <button class="btn btn-sm btn-link" *ngIf="editId === r.budgetId" (click)="editId = null">{{ 'Cancel' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" *ngIf="editId !== r.budgetId" (click)="remove(r)">{{ 'Remove' | translate }}</button>
              </ng-container>
            </td>
          </tr>
          <tr *ngIf="!rows.length"><td colspan="8" class="text-muted">{{ 'No budgets for {p0} yet.' | translate: { p0: (year) } }}</td></tr>
        </tbody>
      </table>
      <p class="text-muted" style="font-size:0.85rem;">{{ 'Spent counts approved expenses dated in the month, without recoverable VAT. Waiting for approval is not counted yet. Turn on "block approval when over budget" in Expenses settings to enforce it.' | translate }}</p>

      <div class="card" style="padding:1rem; max-width:44rem;" *ngIf="canManage">
        <h4 class="mb-2">{{ 'Add a budget for {p0}' | translate: { p0: (year) } }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <label class="text-muted" style="font-size:0.8rem;">{{ 'For' | translate }}
            <select class="form-select" name="kind" [(ngModel)]="newKind"><option value="CATEGORY">{{ 'An expense category' | translate }}</option><option value="COST_CENTER">{{ 'A cost center' | translate }}</option></select>
          </label>
          <label class="text-muted" style="font-size:0.8rem;" *ngIf="newKind === 'CATEGORY'">{{ 'Category' | translate }}
            <select class="form-select" name="newAccount" [(ngModel)]="newAccountId"><option [ngValue]="null" disabled>{{ 'Choose…' | translate }}</option><option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option></select>
          </label>
          <label class="text-muted" style="font-size:0.8rem;" *ngIf="newKind === 'COST_CENTER'">{{ 'Cost center' | translate }}
            <select class="form-select" name="newCc" [(ngModel)]="newCostCenterId"><option [ngValue]="null" disabled>{{ 'Choose…' | translate }}</option><option *ngFor="let c of costCenters" [ngValue]="c.id">{{ c.code }} — {{ c.name }}</option></select>
          </label>
          <label class="text-muted" style="font-size:0.8rem;">{{ 'Monthly budget' | translate }} <input class="form-control" style="width:9rem;" type="number" min="1" name="newAmount" [(ngModel)]="newAmount" /></label>
          <label class="text-muted" style="font-size:0.8rem;">{{ 'Note' | translate }} <input class="form-control" name="newNote" [(ngModel)]="newNote" /></label>
          <button class="btn btn-primary" (click)="add()">{{ 'Add budget' | translate }}</button>
        </div>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .ex-track { background: var(--border, #ddd); height: 0.5rem; border-radius: 3px; overflow: hidden; margin-bottom: 0.2rem; }
      .ex-fill { height: 100%; background: #3a9d5d; }
      .ex-fill.WARNING { background: #d9a400; }
      .ex-fill.OVER { background: #c0392b; }
    `,
  ],
})
export class ExpenseBudgetsComponent implements OnInit {
  Math = Math;
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  rows: ExpenseBudgetStatus[] = [];
  budgets: ExpenseBudget[] = [];
  accounts: Account[] = [];
  costCenters: CostCenter[] = [];
  error: string | null = null;
  editId: number | null = null;
  editAmount: number | null = null;
  newKind: 'CATEGORY' | 'COST_CENTER' = 'CATEGORY';
  newAccountId: number | null = null;
  newCostCenterId: number | null = null;
  newAmount: number | null = null;
  newNote = '';

  constructor(private api: ExpenseApiService, private accountApi: AccountApiService, private costCenterApi: CostCenterApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  ngOnInit(): void {
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a) });
    this.costCenterApi.listActive().subscribe({ next: (c) => (this.costCenters = c) });
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.budgets(this.year).subscribe({ next: (b) => (this.budgets = b), error: () => {} });
    this.api.budgetStatus(this.year, this.month).subscribe({
      next: (r) => (this.rows = r),
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the budgets'),
    });
  }

  note(r: ExpenseBudgetStatus): string | null {
    return this.budgets.find((b) => b.id === r.budgetId)?.note ?? null;
  }

  startEdit(r: ExpenseBudgetStatus): void {
    this.editId = r.budgetId;
    this.editAmount = r.budget;
  }

  saveEdit(r: ExpenseBudgetStatus): void {
    if (!this.editAmount) return;
    this.api.updateBudget(r.budgetId, { monthlyAmount: this.editAmount, note: this.note(r) }).subscribe({
      next: () => {
        this.editId = null;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save the budget'),
    });
  }

  remove(r: ExpenseBudgetStatus): void {
    if (!confirm('Remove the budget for ' + r.label + '?')) return;
    this.api.deleteBudget(r.budgetId).subscribe({ next: () => this.load(), error: (err) => (this.error = err?.error?.message ?? 'Could not remove it') });
  }

  add(): void {
    this.error = null;
    if (!this.newAmount) {
      this.error = 'Enter the monthly budget.';
      return;
    }
    const body = {
      year: this.year,
      monthlyAmount: this.newAmount,
      note: this.newNote || null,
      accountId: this.newKind === 'CATEGORY' ? this.newAccountId : null,
      costCenterId: this.newKind === 'COST_CENTER' ? this.newCostCenterId : null,
    };
    this.api.createBudget(body).subscribe({
      next: () => {
        this.newAmount = null;
        this.newNote = '';
        this.newAccountId = null;
        this.newCostCenterId = null;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not add the budget'),
    });
  }
}
