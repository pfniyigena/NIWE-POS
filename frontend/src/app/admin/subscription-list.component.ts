import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionApiService } from '../core/api/subscription-api.service';
import { Subscription } from '../core/models';

/**
 * Admin > Sales > Subscriptions — one customer's own enrollment in a SubscriptionPlan. Billing is
 * automatic (daily sweep auto-generates an ISSUED invoice on each due date — see backend
 * SubscriptionService.generateDueInvoices), with "Bill now" as a manual escape hatch and
 * pause/resume/cancel for lifecycle control (see backend Subscription's javadoc).
 *
 * <p>Supports the same `?customerId=&customerName=` arrival convention as InvoiceListComponent,
 * for a future "Subscriptions" link off a customer's own row.
 */
@Component({
  selector: 'app-subscription-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Subscriptions for ' + customerName : 'Subscriptions' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/subscriptions" style="font-size:0.85rem;">Show every customer's subscriptions</a>
        </div>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/subscription-plans">Plans</a>
          <a class="btn btn-primary" routerLink="/admin/subscriptions/new">+ New subscription</a>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Customer</th>
            <th>Plan</th>
            <th class="text-end">Price</th>
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
            <td class="text-end">{{ s.price | number: '1.2-2' }}</td>
            <td>{{ s.billingInterval }}</td>
            <td><span class="badge" [ngClass]="statusClass(s.status)">{{ s.status }}</span></td>
            <td>{{ s.status === 'CANCELLED' ? '—' : s.nextBillingDate }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/subscriptions', s.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="subscriptions.length === 0">
            <td colspan="7" class="text-muted">
              {{ customerName ? 'No subscriptions for ' + customerName + ' yet.' : 'No subscriptions yet — create one with "+ New subscription".' }}
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SubscriptionListComponent implements OnInit {
  subscriptions: Subscription[] = [];
  error: string | null = null;
  customerId: number | null = null;
  customerName: string | null = null;

  constructor(private subscriptionApi: SubscriptionApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    this.subscriptionApi.listAll(this.customerId ?? undefined).subscribe({
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
      default:
        return 'badge-secondary';
    }
  }
}
