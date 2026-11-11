import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { RecurringExpenseApiService } from '../../core/api/recurring-expense-api.service';
import { RecurringExpenseTemplate } from '../../core/models';

import { T } from '../../core/i18n/t';
/**
 * Admin > Accounting > Recurring expenses — Fourier: "Recurring expenses" (one of the four gaps
 * picked for this module). Each active, due template is turned into a new PENDING Expense by a
 * daily scheduled sweep (see backend RecurringExpenseTemplateService.generateDueTemplates); "Run
 * now" generates one on demand without waiting for that sweep.
 */
@Component({
  selector: 'app-recurring-expense-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Recurring expenses' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses">{{ 'Back to expenses' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/accounting/recurring-expenses/new">{{ '+ New template' | translate }}</a>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Category' | translate }}</th>
            <th>{{ 'Payee' | translate }}</th>
            <th>{{ 'Amount' | translate }}</th>
            <th>{{ 'Frequency' | translate }}</th>
            <th>{{ 'Next run' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
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
              <span class="badge badge-success" *ngIf="t.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!t.active">{{ 'Paused' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/accounting/recurring-expenses', t.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-primary" (click)="runNow(t)" [disabled]="!t.active">{{ 'Run now' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="toggleActive(t)">{{ t.active ? ('Pause' | translate) : ('Resume' | translate) }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="templates.length === 0">
        {{ 'No recurring expense templates yet — add one for a bill that repeats on a schedule, like rent or a subscription.' | translate }}
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
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load recurring expense templates')),
    });
  }

  runNow(t: RecurringExpenseTemplate): void {
    this.error = null;
    this.recurringApi.runNow(t.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to run template')),
    });
  }

  toggleActive(t: RecurringExpenseTemplate): void {
    this.error = null;
    this.recurringApi.setActive(t.id, !t.active).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to update template')),
    });
  }
}
