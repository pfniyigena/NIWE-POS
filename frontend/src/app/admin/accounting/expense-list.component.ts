import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../../core/branch-util';
import { Branch, Expense } from '../../core/models';

/**
 * Admin > Accounting > Expenses — Fourier's own scoping choice (over deriving expenses purely
 * from existing data) for recording money paid out that isn't a Purchase Order: rent, salaries,
 * utilities, a one-off supplier bill. Each row is logged as PENDING and only actually posts its
 * own journal entry once approved (Fourier: "Approval workflow before posting") — see the backend
 * ExpenseStatus javadoc for the full lifecycle. This list is read-only; every action (approve,
 * reject, edit, void, settle) lives on the row's own detail page.
 */
@Component({
  selector: 'app-expense-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Expenses</h3>
        <div class="flex gap-2">
          <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchFilter" title="Branch">
            <option [ngValue]="null">All branches</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/recurring-expenses">Recurring expenses</a>
          <a class="btn btn-primary" routerLink="/admin/accounting/expenses/new">+ New expense</a>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Date</th>
            <th>Category</th>
            <th>Payee</th>
            <th>Amount</th>
            <th>Payment</th>
            <th>Approval</th>
            <th>Cost center</th>
            <th *ngIf="branches.length > 1">Branch</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of shown" class="clickable-row" [routerLink]="['/admin/accounting/expenses', e.id]">
            <td>{{ e.expenseDate }}</td>
            <td>{{ e.accountCode }} — {{ e.accountName }}</td>
            <td>{{ e.payeeName }} <span class="text-muted">({{ e.payeeType }})</span></td>
            <td class="text-end">{{ e.amount | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="e.paid">Paid</span>
              <ng-container *ngIf="!e.paid">
                <span class="badge badge-warning" *ngIf="!e.settledAt">Unpaid</span>
                <span class="badge badge-success" *ngIf="e.settledAt">Settled</span>
              </ng-container>
            </td>
            <td>
              <span class="badge badge-secondary" *ngIf="e.status === 'PENDING'">Pending</span>
              <span class="badge badge-success" *ngIf="e.status === 'APPROVED'">Approved</span>
              <span class="badge badge-danger" *ngIf="e.status === 'REJECTED'">Rejected</span>
              <span class="badge badge-danger" *ngIf="e.status === 'VOIDED'">Voided</span>
            </td>
            <td>{{ e.costCenterName || '—' }}</td>
            <td *ngIf="branches.length > 1">{{ branchName(e.branchId) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="expenses.length === 0">No expenses recorded yet.</p>
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
  error: string | null = null;
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  constructor(private expenseApi: ExpenseApiService, private branchApi: BranchApiService) {}

  get shown(): Expense[] {
    return this.branchFilter == null ? this.expenses : this.expenses.filter((e) => effectiveBranchId(e.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.expenseApi.listAll().subscribe({
      next: (e) => (this.expenses = e),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load expenses'),
    });
  }
}
