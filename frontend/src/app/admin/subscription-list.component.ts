import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionApiService } from '../core/api/subscription-api.service';
import { SubscriptionAutomationApiService } from '../core/api/subscription-automation-api.service';
import { Subscription, SubscriptionPlan } from '../core/models';
import { SubscriptionPlanApiService } from '../core/api/subscription-plan-api.service';

/**
 * Admin > Sales > Subscriptions — one customer's own enrollment in a SubscriptionPlan. Billing is
 * automatic (daily sweep auto-generates an ISSUED invoice on each due date — see backend
 * SubscriptionAutomationService), with "Bill now" as a manual escape hatch and
 * pause/resume/cancel for lifecycle control (see backend Subscription's javadoc).
 *
 * <p>Supports the same `?customerId=&customerName=` arrival convention as InvoiceListComponent,
 * for a future "Subscriptions" link off a customer's own row.
 */
@Component({
  selector: 'app-subscription-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Subscriptions for ' + customerName : 'Subscriptions' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/subscriptions" style="font-size:0.85rem;">Show every customer's subscriptions</a>
        </div>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions/overview">Overview</a>
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions/reports">Reports</a>
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions/job-settings">Job settings</a>
          <a class="btn btn-outline-primary" routerLink="/admin/subscription-plans">Plans</a>
          <a class="btn btn-primary" routerLink="/admin/subscriptions/new">+ New subscription</a>
        </div>
      </div>
      <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="jobSummary">Daily billing job — last run: {{ jobSummary }}</div>
      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button type="button" class="btn btn-sm" *ngFor="let v of views" [ngClass]="view === v.key ? 'btn-primary' : 'btn-outline-primary'" (click)="setView(v.key)">{{ v.label }}</button>
      </div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <input class="form-control" style="width:16rem;" name="q" placeholder="Search customer or plan" [(ngModel)]="search" (ngModelChange)="reload()" />
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="reload()">
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="PAUSED">Paused</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="ENDED">Ended</option>
        </select>
        <select class="form-select" style="width:auto;" name="pl" [(ngModel)]="planId" (ngModelChange)="reload()">
          <option [ngValue]="null">Any plan</option>
          <option *ngFor="let p of plans" [ngValue]="p.id">{{ p.name }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Customer</th>
            <th>Plan</th>
            <th class="text-end">Qty</th>
            <th class="text-end">Per cycle</th>
            <th>Interval</th>
            <th>Status</th>
            <th>Next billing</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of subscriptions">
            <td>{{ s.customerName }}</td>
            <td>{{ s.planName || '—' }}</td>
            <td class="text-end">{{ s.quantity }}</td>
            <td class="text-end">{{ s.cycleAmount | number: '1.2-2' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="s.quantity > 1">{{ s.quantity }} × {{ s.price | number: '1.2-2' }}</div></td>
            <td>{{ s.billingInterval }}</td>
            <td>
              <span class="badge" [ngClass]="statusClass(s.status)">{{ s.status }}</span>
              <span class="badge badge-info" style="margin-left:0.25rem;" *ngIf="s.onTrial">TRIAL</span>
              <span class="badge badge-warning" style="margin-left:0.25rem;" *ngIf="s.cancelAtPeriodEnd">CANCELS</span>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.status === 'PAUSED' && s.resumeOn">resumes {{ s.resumeOn }}</div>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.termCycles && s.status === 'ACTIVE'">cycle {{ s.cyclesBilled }} of {{ s.termCycles }}</div>
            </td>
            <td>{{ s.status === 'CANCELLED' || s.status === 'ENDED' ? '—' : s.nextBillingDate }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/subscriptions', s.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="subscriptions.length === 0">
            <td colspan="8" class="text-muted">
              {{ customerName ? 'No subscriptions for ' + customerName + ' yet.' : (search || status || planId || view !== 'all') ? 'No subscription matches this filter.' : 'No subscriptions yet — create one with "+ New subscription".' }}
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SubscriptionListComponent implements OnInit {
  subscriptions: Subscription[] = [];
  plans: SubscriptionPlan[] = [];
  error: string | null = null;
  customerId: number | null = null;
  customerName: string | null = null;
  search = '';
  status: '' | 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'ENDED' = '';
  planId: number | null = null;
  view = 'all';
  views = [
    { key: 'all', label: 'All' },
    { key: 'trial', label: 'On trial' },
    { key: 'due-soon', label: 'Billing within 7 days' },
    { key: 'cancelling', label: 'Cancelling' },
    { key: 'term-ending', label: 'Term ending' },
    { key: 'resuming', label: 'Resuming' },
  ];

  jobSummary: string | null = null;

  constructor(
    private subscriptionApi: SubscriptionApiService,
    private automationApi: SubscriptionAutomationApiService,
    private planApi: SubscriptionPlanApiService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    const v = this.route.snapshot.queryParamMap.get('view');
    if (v) this.view = v;
    this.automationApi.get().subscribe({ next: (a) => (this.jobSummary = a.lastSummary), error: () => (this.jobSummary = null) });
    this.planApi.listAll().subscribe({ next: (p) => (this.plans = p), error: () => (this.plans = []) });
    this.reload();
  }

  setView(key: string): void {
    this.view = key;
    this.reload();
  }

  reload(): void {
    this.subscriptionApi.listAll(this.customerId ?? undefined, { status: this.status, planId: this.planId, search: this.search, view: this.view === 'all' ? '' : this.view }).subscribe({
      next: (s) => (this.subscriptions = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load subscriptions'),
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'badge-success';
      case 'PAUSED':
        return 'badge-secondary';
      case 'CANCELLED':
        return 'badge-danger';
      case 'ENDED':
        return 'badge-secondary';
      default:
        return 'badge-secondary';
    }
  }
}
