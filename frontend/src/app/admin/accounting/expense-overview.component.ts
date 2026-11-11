import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { ExpenseSummary } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Expenses > Overview — this month against last, where the money goes, what waits for approval, and which bills are unpaid or late. */
@Component({
  selector: 'app-expense-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin-bottom:0;">{{ 'Expenses overview' | translate }}</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/reports">{{ 'Reports' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/budgets">{{ 'Budgets' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses/settings">{{ 'Settings' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses">{{ 'All expenses' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="s">
        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">{{ 'This month' | translate }}</div>
            <div class="stat-value">{{ s.thisMonth | number: '1.0-0' }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} · cost without recoverable VAT' | translate: { p0: (changeText) } }}</div>
          </div>
          <div class="stat-tile"><div class="stat-label">{{ 'Last month' | translate }}</div><div class="stat-value">{{ s.lastMonth | number: '1.0-0' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'This year' | translate }}</div><div class="stat-value">{{ s.yearToDate | number: '1.0-0' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'VAT to claim back (this month)' | translate }}</div><div class="stat-value">{{ s.recoverableVatThisMonth | number: '1.0-0' }}</div></div>
        </div>
        <div class="stat-grid mb-3">
          <a class="stat-tile" routerLink="/admin/accounting/expenses" [queryParams]="{ view: 'pending' }" style="text-decoration:none; color:inherit;">
            <div class="stat-label">{{ 'Awaiting approval' | translate }}</div><div class="stat-value">{{ s.awaitingApprovalCount }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} in total' | translate: { p0: (s.awaitingApprovalAmount | number: '1.0-0') } }}</div>
          </a>
          <a class="stat-tile" routerLink="/admin/accounting/expenses" [queryParams]="{ view: 'unpaid' }" style="text-decoration:none; color:inherit;">
            <div class="stat-label">{{ 'Unpaid bills' | translate }}</div><div class="stat-value">{{ s.unpaidCount }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} owed' | translate: { p0: (s.unpaidBalance | number: '1.0-0') } }}</div>
          </a>
          <a class="stat-tile" [class.warn]="s.overdueCount > 0" routerLink="/admin/accounting/expenses" [queryParams]="{ view: 'overdue' }" style="text-decoration:none; color:inherit;">
            <div class="stat-label">{{ 'Overdue' | translate }}</div><div class="stat-value">{{ s.overdueCount }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} late' | translate: { p0: (s.overdueBalance | number: '1.0-0') } }}</div>
          </a>
          <a class="stat-tile" routerLink="/admin/accounting/expenses" [queryParams]="{ view: 'due-soon' }" style="text-decoration:none; color:inherit;">
            <div class="stat-label">{{ 'Due soon' | translate }}</div><div class="stat-value">{{ s.dueSoonCount }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} coming up' | translate: { p0: (s.dueSoonBalance | number: '1.0-0') } }}</div>
          </a>
        </div>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <div style="flex:1; min-width:300px;">
            <h4 class="mb-2">{{ 'Last six months' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let m of s.lastSixMonths">
                  <td style="width:5rem;">{{ m.month }}</td>
                  <td><div class="ex-bar" [style.width.%]="barWidth(m.amount)"></div></td>
                  <td class="text-end" style="width:6rem;">{{ m.amount | number: '1.0-0' }}</td>
                </tr>
              </tbody>
            </table>
            <h4 class="mb-2">{{ 'Top categories this month' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let c of s.topCategories"><td>{{ c.name }}</td><td class="text-muted">{{ c.count }}</td><td class="text-end">{{ c.amount | number: '1.0-0' }}</td></tr>
                <tr *ngIf="!s.topCategories.length"><td class="text-muted">{{ 'Nothing approved this month yet.' | translate }}</td></tr>
              </tbody>
            </table>
          </div>
          <div style="flex:1; min-width:300px;">
            <h4 class="mb-2">{{ 'Top vendors this month' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let c of s.topVendors"><td>{{ c.name }}</td><td class="text-muted">{{ c.count }}</td><td class="text-end">{{ c.amount | number: '1.0-0' }}</td></tr>
                <tr *ngIf="!s.topVendors.length"><td class="text-muted">{{ 'Nothing approved this month yet.' | translate }}</td></tr>
              </tbody>
            </table>
            <h4 class="mb-2">{{ 'Paid out this month, by account' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let c of s.byPaymentAccount"><td>{{ c.name }}</td><td class="text-muted">{{ c.count }}</td><td class="text-end">{{ c.amount | number: '1.0-0' }}</td></tr>
                <tr *ngIf="!s.byPaymentAccount.length"><td class="text-muted">{{ 'No payments yet this month.' | translate }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <h4 class="mb-2" *ngIf="s.budgetAlerts.length">{{ 'Budgets near or over the limit' | translate }}</h4>
        <table class="table-sm mb-3" *ngIf="s.budgetAlerts.length">
          <thead><tr><th>{{ 'Budget' | translate }}</th><th class="text-end">{{ 'Monthly budget' | translate }}</th><th class="text-end">{{ 'Spent' | translate }}</th><th class="text-end">{{ 'Waiting' | translate }}</th><th class="text-end">{{ 'Used' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let b of s.budgetAlerts">
              <td>{{ b.label }}</td><td class="text-end">{{ b.budget | number: '1.0-0' }}</td><td class="text-end">{{ b.spent | number: '1.0-0' }}</td><td class="text-end">{{ b.pending | number: '1.0-0' }}</td>
              <td class="text-end"><span class="badge" [ngClass]="b.state === 'OVER' ? 'badge-danger' : 'badge-warning'">{{ b.percentUsed | number: '1.0-0' }}%</span></td>
            </tr>
          </tbody>
        </table>

        <h4 class="mb-2">{{ 'Bills that need attention' | translate }}</h4>
        <table class="table-sm">
          <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Payee' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th></th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let a of s.attention">
              <td>{{ a.expenseNumber }}</td><td>{{ a.payee }}</td><td>{{ a.dueDate }}</td><td class="text-end">{{ a.amount | number: '1.2-2' }}</td><td class="text-end">{{ a.balance | number: '1.2-2' }}</td>
              <td><span class="badge" [ngClass]="a.reason === 'OVERDUE' ? 'badge-danger' : 'badge-warning'">{{ a.reason === 'OVERDUE' ? ('Overdue' | translate) : ('Due soon' | translate) }}</span></td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/accounting/expenses', a.id]">{{ 'View' | translate }}</a></td>
            </tr>
            <tr *ngIf="!s.attention.length"><td colspan="7" class="text-muted">{{ 'No bills are overdue or due soon.' | translate }}</td></tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
  styles: [`.ex-bar { height: 0.9rem; background: var(--primary, #4a7bd0); border-radius: 3px; min-width: 2px; }`],
})
export class ExpenseOverviewComponent implements OnInit {
  s: ExpenseSummary | null = null;
  error: string | null = null;

  constructor(private api: ExpenseApiService) {}

  ngOnInit(): void {
    this.api.summary().subscribe({
      next: (s) => (this.s = s),
      error: (err) => (this.error = err?.error?.message ?? T('Could not load the overview')),
    });
  }

  get changeText(): string {
    if (!this.s) return '';
    if (!this.s.lastMonth) return 'nothing last month to compare';
    const pct = ((this.s.thisMonth - this.s.lastMonth) / this.s.lastMonth) * 100;
    return (pct >= 0 ? '+' : '') + pct.toFixed(0) + '% vs last month';
  }

  barWidth(amount: number): number {
    const max = Math.max(1, ...(this.s?.lastSixMonths ?? []).map((m) => m.amount));
    return (amount / max) * 100;
  }
}
