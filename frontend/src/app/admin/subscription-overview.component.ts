import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionApiService, SubscriptionSummary } from '../core/api/subscription-api.service';

/** Subscriptions > Overview — recurring revenue, churn, what bills soon and what is unpaid. Amounts are before tax. */
@Component({
  selector: 'app-subscription-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Subscriptions overview' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions/reports">{{ 'Reports' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions">{{ 'All subscriptions' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="s">
        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Monthly recurring revenue' | translate }}</div><div class="stat-value">{{ s.mrr | number: '1.0-2' }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Yearly: {p0}' | translate: { p0: (s.arr | number: '1.0-0') } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Paying subscriptions' | translate }}</div><div class="stat-value">{{ s.active }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Average {p0} a month' | translate: { p0: (s.averagePerSubscription | number: '1.0-2') } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'On trial / paused' | translate }}</div><div class="stat-value">{{ s.onTrial }} / {{ s.paused }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Trials ending within 7 days: {p0}' | translate: { p0: (s.trialsEnding7) } }}</div></div>
          <div class="stat-tile" [class.warn]="s.churnPercent > 10"><div class="stat-label">{{ 'Left in the last 30 days' | translate }}</div><div class="stat-value">{{ s.cancelledLast30 }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Churn {p0}% · new {p1}' | translate: { p0: (s.churnPercent | number: '1.0-1'), p1: (s.newLast30) } }}</div></div>
        </div>
        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Billing in 7 days' | translate }}</div><div class="stat-value">{{ s.upcoming7Amount | number: '1.0-2' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} subscription(s)' | translate: { p0: (s.upcoming7Count) } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Billing in 30 days' | translate }}</div><div class="stat-value">{{ s.upcoming30Amount | number: '1.0-2' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} subscription(s)' | translate: { p0: (s.upcoming30Count) } }}</div></div>
          <div class="stat-tile" [class.warn]="s.unpaidCount > 0"><div class="stat-label">{{ 'Unpaid invoices' | translate }}</div><div class="stat-value">{{ s.unpaidAmount | number: '1.0-2' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} subscription(s)' | translate: { p0: (s.unpaidCount) } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Ending' | translate }}</div><div class="stat-value">{{ s.scheduledCancels + s.termsEnding }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} cancelling · {p1} term(s) ending' | translate: { p0: (s.scheduledCancels), p1: (s.termsEnding) } }}</div></div>
        </div>
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <a class="btn btn-sm btn-outline-primary" routerLink="/admin/subscriptions" [queryParams]="{ view: 'trial' }">{{ 'On trial' | translate }}</a>
          <a class="btn btn-sm btn-outline-primary" routerLink="/admin/subscriptions" [queryParams]="{ view: 'cancelling' }">{{ 'Cancelling' | translate }}</a>
          <a class="btn btn-sm btn-outline-primary" routerLink="/admin/subscriptions" [queryParams]="{ view: 'term-ending' }">{{ 'Term ending' | translate }}</a>
          <a class="btn btn-sm btn-outline-primary" routerLink="/admin/subscriptions" [queryParams]="{ view: 'resuming' }">{{ 'Resuming' | translate }}</a>
        </div>

        <h4 class="mb-2">{{ 'Billing next' | translate }}</h4>
        <table class="table-sm mb-3">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'Plan' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let u of s.upcoming">
              <td>{{ u.date }}</td><td>{{ u.customerName }}</td><td>{{ u.planName || '—' }}</td><td class="text-end">{{ u.amount | number: '1.2-2' }}</td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/subscriptions', u.subscriptionId]">{{ 'View' | translate }}</a></td>
            </tr>
            <tr *ngIf="!s.upcoming.length"><td colspan="5" class="text-muted">{{ 'Nothing is due to be billed in the next 30 days.' | translate }}</td></tr>
          </tbody>
        </table>

        <h4 class="mb-2">{{ 'Unpaid subscription invoices' | translate }}</h4>
        <table class="table-sm">
          <thead><tr><th>{{ 'Customer' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Outstanding' | translate }}</th><th class="text-end">{{ 'Days late' | translate }}</th><th class="text-end">{{ 'Reminders' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let u of s.unpaid">
              <td>{{ u.customerName }}</td><td>{{ u.invoiceNumber }}</td><td class="text-end">{{ u.outstanding | number: '1.2-2' }}</td>
              <td class="text-end">{{ u.daysLate }}</td><td class="text-end">{{ u.reminders }}</td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/subscriptions', u.subscriptionId]">{{ 'View' | translate }}</a></td>
            </tr>
            <tr *ngIf="!s.unpaid.length"><td colspan="6" class="text-muted">{{ 'Every subscription invoice is paid.' | translate }}</td></tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class SubscriptionOverviewComponent implements OnInit {
  s: SubscriptionSummary | null = null;
  error: string | null = null;

  constructor(private api: SubscriptionApiService) {}

  ngOnInit(): void {
    this.api.summary().subscribe({ next: (r) => (this.s = r), error: (err) => (this.error = err?.error?.message ?? 'Failed to load the overview') });
  }
}
