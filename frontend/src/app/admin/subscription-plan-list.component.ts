import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionPlanApiService } from '../core/api/subscription-plan-api.service';
import { SubscriptionPlan } from '../core/models';

/**
 * Admin > Sales > Subscription plans — the fixed, admin-defined catalog customers subscribe to
 * (Fourier: "Subscriptions Module" — "subscribing to a fixed admin-defined plan", not tied to the
 * product catalog). Plans are toggled active/inactive, never deleted, mirroring
 * PaymentMethodEntity/TaxRate (see backend SubscriptionPlan's javadoc).
 */
@Component({
  selector: 'app-subscription-plan-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Subscription plans</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/subscriptions">Subscriptions</a>
          <a class="btn btn-primary" routerLink="/admin/subscription-plans/new">+ New plan</a>
        </div>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
            <th>Price</th>
            <th>Billing interval</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of plans">
            <td>{{ p.name }}</td>
            <td>{{ p.description }}</td>
            <td class="text-end">{{ p.price | number: '1.2-2' }}</td>
            <td>{{ p.billingInterval }}</td>
            <td>
              <span class="badge badge-success" *ngIf="p.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!p.active">Inactive</span>
            </td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/subscription-plans', p.id, 'edit']">Edit</a>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="plans.length === 0">
        No subscription plans yet — add one to start offering customers a recurring plan.
      </p>
    </app-shell>
  `,
})
export class SubscriptionPlanListComponent implements OnInit {
  plans: SubscriptionPlan[] = [];
  error: string | null = null;

  constructor(private planApi: SubscriptionPlanApiService) {}

  ngOnInit(): void {
    this.planApi.listAll().subscribe({
      next: (p) => (this.plans = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load subscription plans'),
    });
  }
}
