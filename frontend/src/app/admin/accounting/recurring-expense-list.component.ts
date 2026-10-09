import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { RecurringExpenseApiService } from '../../core/api/recurring-expense-api.service';
import { RecurringExpenseTemplate } from '../../core/models';

/**
 * Admin > Accounting > Recurring expenses — Fourier: "Recurring expenses" (one of the four gaps
 * picked for this module). Each active, due template is turned into a new PENDING Expense by a
 * daily scheduled sweep (see backend RecurringExpenseTemplateService.generateDueTemplates); "Run
 * now" generates one on demand without waiting for that sweep.
 */
@Component({
  selector: 'app-recurring-expense-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Recurring expenses</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses">Back to expenses</a>
          <a class="btn btn-primary" routerLink="/admin/accounting/recurring-expenses/new">+ New template</a>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Name</th>
            <th>Category</th>
            <th>Payee</th>
            <th>Amount</th>
            <th>Frequency</th>
            <th>Next run</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of templates">
            <td>{{ t.name }}</td>
            <td>{{ t.accountCode }} — {{ t.accountName }}</td>
            <td>{{ t.payeeName }}</td>
            <td class="text-end">{{ t.amount | number: '1.2-2' }}</td>
            <td>{{ t.frequency }}</td>
            <td>{{ t.nextRunDate }}</td>
            <td>
              <span class="badge badge-success" *ngIf="t.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!t.active">Paused</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/accounting/recurring-expenses', t.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-primary" (click)="runNow(t)" [disabled]="!t.active">Run now</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="toggleActive(t)">{{ t.active ? 'Pause' : 'Resume' }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="templates.length === 0">
        No recurring expense templates yet — add one for a bill that repeats on a schedule, like rent or a subscription.
      </p>
    </app-shell>
  `,
})
export class RecurringExpenseListComponent implements OnInit {
  templates: RecurringExpenseTemplate[] = [];
  error: string | null = null;

  constructor(private recurringApi: RecurringExpenseApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.recurringApi.listAll().subscribe({
      next: (t) => (this.templates = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load recurring expense templates'),
    });
  }

  runNow(t: RecurringExpenseTemplate): void {
    this.error = null;
    this.recurringApi.runNow(t.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to run template'),
    });
  }

  toggleActive(t: RecurringExpenseTemplate): void {
    this.error = null;
    this.recurringApi.setActive(t.id, !t.active).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update template'),
    });
  }
}
