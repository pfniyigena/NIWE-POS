import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionPlanApiService } from '../core/api/subscription-plan-api.service';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { BillingInterval, TaxRateOption } from '../core/models';

/** Admin > Sales > Subscription plans > New/Edit — see SubscriptionPlanListComponent's javadoc. */
@Component({
  selector: 'app-subscription-plan-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit subscription plan' : 'New subscription plan' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Gold membership" required />
        </div>
        <div class="form-group">
          <label class="form-label">Description (optional)</label>
          <textarea class="form-control" name="description" [(ngModel)]="description" rows="2"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">Price</label>
          <input class="form-control" type="number" step="0.01" min="0.01" name="price" [(ngModel)]="price" required />
        </div>
        <div class="form-group">
          <label class="form-label">Billing interval</label>
          <select class="form-select" name="billingInterval" [(ngModel)]="billingInterval">
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="QUARTERLY">Quarterly</option>
            <option value="YEARLY">Yearly</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Tax rate</label>
          <select class="form-select" name="taxRateId" [(ngModel)]="taxRateId">
            <option [ngValue]="null">— None (tax-exempt) —</option>
            <option *ngFor="let t of activeTaxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Discount %</label>
          <input class="form-control" type="number" step="0.01" min="0" max="100" name="discountPercent" [(ngModel)]="discountPercent" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Default discount for a new subscription on this plan — stays editable per customer.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Inactive plans stay visible on existing subscriptions but can't be picked for new ones.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/subscription-plans">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class SubscriptionPlanFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  description: string | null = null;
  price: number | null = null;
  billingInterval: BillingInterval = 'MONTHLY';
  taxRateId: number | null = null;
  discountPercent: number | null = 0;
  active = true;

  /** Active rates only — an inactive rate can't newly be assigned, though a plan keeps whichever rate it already has. */
  activeTaxRates: TaxRateOption[] = [];

  error: string | null = null;

  constructor(
    private planApi: SubscriptionPlanApiService,
    private taxRateApi: TaxRateApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.taxRateApi.listAll().subscribe((rates) => (this.activeTaxRates = rates.filter((r) => r.active)));
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.planApi.getById(this.id).subscribe({
      next: (p) => {
        this.name = p.name;
        this.description = p.description;
        this.price = p.price;
        this.billingInterval = p.billingInterval;
        this.taxRateId = p.taxRateId;
        this.discountPercent = p.discountPercent;
        this.active = p.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load plan'),
    });
  }

  submit(): void {
    this.error = null;
    if (!this.name || this.price == null) {
      this.error = 'Name and price are required';
      return;
    }
    const payload = {
      name: this.name,
      description: this.description || null,
      price: this.price,
      billingInterval: this.billingInterval,
      taxRateId: this.taxRateId,
      discountPercent: this.discountPercent ?? 0,
      active: this.active,
    };
    const call = this.id ? this.planApi.update(this.id, payload) : this.planApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/subscription-plans']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save plan'),
    });
  }
}
