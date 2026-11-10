import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionApiService } from '../core/api/subscription-api.service';
import { SubscriptionPlanApiService } from '../core/api/subscription-plan-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { BillingInterval, Customer, SubscriptionPlan, TaxRateOption } from '../core/models';

/**
 * Admin > Sales > Subscriptions > New — enrolls a customer in an active SubscriptionPlan.
 *
 * <p>Fourier: "What if the One Subscription can be assign to the customer with different price
 * and different Billing interval" — picking a plan pre-fills price/billing interval from it, but
 * both stay editable so this one customer can get a negotiated price or a different cadence than
 * the plan's own listed defaults; see the backend SubscriptionService.create/
 * CreateSubscriptionRequest.price's own javadoc. Whatever is saved here is this subscription's own
 * committed terms going forward — editing the plan later never reaches back into it.
 *
 * <p>Fourier: "On new subscription add quick customer creation and subscription plan" — two
 * inline "+ New …" panels, one per picker, same convention (and same minimal field set) as every
 * other admin form's own quick-add panels (see e.g. SalesOrderFormComponent's "+ New customer").
 * Enrolling a brand-new customer, or trying out a plan that doesn't exist yet, no longer means
 * abandoning this form to go create either one properly first and coming back.
 */
@Component({
  selector: 'app-subscription-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'New subscription' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Customer' | translate }}</label>
          <div class="flex gap-2">
            <select class="form-select" name="customerId" [(ngModel)]="customerId" required [disabled]="showNewCustomer">
              <option [ngValue]="null" disabled>{{ 'Select a customer…' | translate }}</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select>
            <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewCustomer" (click)="openNewCustomer()">
              {{ '+ New customer' | translate }}
            </button>
          </div>
        </div>

        <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewCustomer">
          <div class="alert alert-danger" *ngIf="newCustomerError">{{ newCustomerError }}</div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">{{ 'Name' | translate }}</label>
              <input class="form-control" name="newCustomerName" [(ngModel)]="newCustomerName" [placeholder]="'Customer name' | translate" />
            </div>
            <div class="grow">
              <label class="form-label">{{ 'Phone' | translate }}</label>
              <input class="form-control" name="newCustomerPhone" [(ngModel)]="newCustomerPhone" />
            </div>
          </div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">{{ 'Email' | translate }}</label>
              <input class="form-control" type="email" name="newCustomerEmail" [(ngModel)]="newCustomerEmail" />
            </div>
            <div class="grow">
              <label class="form-label">{{ 'Tax ID' | translate }}</label>
              <input class="form-control" name="newCustomerTaxId" [(ngModel)]="newCustomerTaxId" />
            </div>
          </div>
          <div class="flex gap-2">
            <button type="button" class="btn btn-primary btn-sm" (click)="saveNewCustomer()">{{ 'Save customer' | translate }}</button>
            <button type="button" class="btn btn-link btn-sm" (click)="cancelNewCustomer()">{{ 'Cancel' | translate }}</button>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">{{ 'Plan' | translate }}</label>
          <div class="flex gap-2">
            <select class="form-select" name="planId" [(ngModel)]="planId" (ngModelChange)="onPlanChange()" required [disabled]="showNewPlan">
              <option [ngValue]="null" disabled>{{ 'Select a plan…' | translate }}</option>
              <option *ngFor="let p of plans" [ngValue]="p.id">{{ p.name }} — {{ p.price | number: '1.2-2' }} / {{ p.billingInterval }}</option>
            </select>
            <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewPlan" (click)="openNewPlan()">
              {{ '+ New plan' | translate }}
            </button>
          </div>
        </div>

        <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewPlan">
          <div class="alert alert-danger" *ngIf="newPlanError">{{ newPlanError }}</div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">{{ 'Name' | translate }}</label>
              <input class="form-control" name="newPlanName" [(ngModel)]="newPlanName" [placeholder]="'e.g. Gold membership' | translate" />
            </div>
            <div class="grow">
              <label class="form-label">{{ 'Price' | translate }}</label>
              <input class="form-control" type="number" step="0.01" min="0.01" name="newPlanPrice" [(ngModel)]="newPlanPrice" />
            </div>
            <div class="grow">
              <label class="form-label">{{ 'Billing interval' | translate }}</label>
              <select class="form-select" name="newPlanBillingInterval" [(ngModel)]="newPlanBillingInterval">
                <option value="WEEKLY">{{ 'Weekly' | translate }}</option>
                <option value="MONTHLY">{{ 'Monthly' | translate }}</option>
                <option value="QUARTERLY">{{ 'Quarterly' | translate }}</option>
                <option value="YEARLY">{{ 'Yearly' | translate }}</option>
              </select>
            </div>
          </div>
          <div class="form-group mb-2">
            <label class="form-label">{{ 'Description (optional)' | translate }}</label>
            <input class="form-control" name="newPlanDescription" [(ngModel)]="newPlanDescription" />
          </div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">{{ 'Tax rate' | translate }}</label>
              <select class="form-select" name="newPlanTaxRateId" [(ngModel)]="newPlanTaxRateId">
                <option [ngValue]="null">{{ '— None (tax-exempt) —' | translate }}</option>
                <option *ngFor="let t of activeTaxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option>
              </select>
            </div>
            <div class="grow">
              <label class="form-label">{{ 'Discount %' | translate }}</label>
              <input class="form-control" type="number" step="0.01" min="0" max="100" name="newPlanDiscountPercent" [(ngModel)]="newPlanDiscountPercent" />
            </div>
          </div>
          <div class="flex gap-2">
            <button type="button" class="btn btn-primary btn-sm" (click)="saveNewPlan()">Save plan &amp; use it</button>
            <button type="button" class="btn btn-link btn-sm" (click)="cancelNewPlan()">{{ 'Cancel' | translate }}</button>
          </div>
        </div>
        <div class="form-group" *ngIf="selectedPlan">
          <label class="form-label">{{ 'Price for this customer' | translate }}</label>
          <input class="form-control" type="number" step="0.01" min="0.01" name="price" [(ngModel)]="price" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Pre-filled from \\'{p0}\\' ({p1}) — change it to give this customer a different price.' | translate: { p0: (selectedPlan.name), p1: (selectedPlan.price | number: '1.2-2') } }}
          </div>
        </div>
        <div class="form-group" *ngIf="selectedPlan">
          <label class="form-label">{{ 'Billing interval for this customer' | translate }}</label>
          <select class="form-select" name="billingInterval" [(ngModel)]="billingInterval">
            <option value="WEEKLY">{{ 'Weekly' | translate }}</option>
            <option value="MONTHLY">{{ 'Monthly' | translate }}</option>
            <option value="QUARTERLY">{{ 'Quarterly' | translate }}</option>
            <option value="YEARLY">{{ 'Yearly' | translate }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Pre-filled from the plan\\'s own {p0} cadence — change it to bill this customer on a different cycle.' | translate: { p0: (selectedPlan.billingInterval | lowercase) } }}
          </div>
        </div>
        <div class="form-group" *ngIf="selectedPlan">
          <label class="form-label">{{ 'Tax rate for this customer' | translate }}</label>
          <select class="form-select" name="taxRateId" [(ngModel)]="taxRateId">
            <option [ngValue]="null">{{ '— None (tax-exempt) —' | translate }}</option>
            <option *ngFor="let t of activeTaxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Pre-filled from the plan — change it to tax (or exempt) this customer differently.' | translate }}
          </div>
        </div>
        <div class="form-group" *ngIf="selectedPlan">
          <label class="form-label">{{ 'Discount % for this customer' | translate }}</label>
          <input class="form-control" type="number" step="0.01" min="0" max="100" name="discountPercent" [(ngModel)]="discountPercent" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Pre-filled from the plan\\'s own default discount — change it to give this customer a different discount.' | translate }}
          </div>
        </div>
        <div class="flex gap-2" *ngIf="selectedPlan">
          <div class="form-group grow">
            <label class="form-label">{{ 'Quantity (seats / units)' | translate }}</label>
            <input class="form-control" type="number" step="1" min="1" name="quantity" [(ngModel)]="quantity" />
          </div>
          <div class="form-group grow">
            <label class="form-label">{{ 'Setup fee (one time)' | translate }}</label>
            <input class="form-control" type="number" step="0.01" min="0" name="setupFee" [(ngModel)]="setupFee" />
          </div>
          <div class="form-group grow">
            <label class="form-label">{{ 'Free trial (days)' | translate }}</label>
            <input class="form-control" type="number" step="1" min="0" max="365" name="trialDays" [(ngModel)]="trialDays" />
          </div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.8rem;" *ngIf="selectedPlan">
          {{ 'Each invoice bills quantity × price. The setup fee is added to the first invoice only. Billing starts when the free trial ends.' | translate }}
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Start date' | translate }}</label>
          <app-date-picker name="startDate" [(ngModel)]="startDate" required></app-date-picker>
        </div>
        <!-- Fourier: "may be some subscription will be paid after service other will be prepaid" — chosen fresh per subscription, no plan default. -->
        <div class="form-group">
          <label class="form-label">{{ 'Billing timing' | translate }}</label>
          <select class="form-select" name="billInAdvance" [(ngModel)]="billInAdvance">
            <option [ngValue]="true">{{ 'Prepaid — bill on the start date, before that cycle\\'s service' | translate }}</option>
            <option [ngValue]="false">{{ 'Paid after service — bill one {p0} cycle after the start date' | translate: { p0: (billingInterval | lowercase) } }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ '{p0} Each later invoice follows this subscription\\'s own billing interval above either way.' | translate: { p0: (billInAdvance
              ? 'First invoice is generated on the start date itself (or the next daily sweep after it).'
              : 'First invoice is generated one billing cycle after the start date, once that cycle of service is done.') } }}
          </div>
        </div>
        <div class="flex gap-2">
          <div class="form-group grow">
            <label class="form-label">{{ 'Term (billing cycles)' | translate }}</label>
            <input class="form-control" type="number" step="1" min="1" max="1200" name="termCycles" [(ngModel)]="termCycles" [placeholder]="'Blank = until cancelled' | translate" />
          </div>
          <div class="form-group grow" *ngIf="termCycles">
            <label class="form-label">{{ 'At the end of the term' | translate }}</label>
            <select class="form-select" name="autoRenew" [(ngModel)]="autoRenew">
              <option [ngValue]="true">{{ 'Renew for another term' | translate }}</option>
              <option [ngValue]="false">{{ 'End the subscription' | translate }}</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Notes (optional)' | translate }}</label>
          <textarea class="form-control" name="notes" [(ngModel)]="notes" rows="2"></textarea>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Create subscription' | translate }}</button>
          <a class="btn btn-link" routerLink="/admin/subscriptions">{{ 'Cancel' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class SubscriptionFormComponent implements OnInit {
  customerId: number | null = null;
  planId: number | null = null;
  price: number | null = null;
  billingInterval: BillingInterval = 'MONTHLY';
  /** Fourier: "subscription plan should also have tax and discount on it" — pre-filled from the chosen plan, left editable, same treatment as price/billingInterval above. */
  taxRateId: number | null = null;
  discountPercent: number | null = 0;
  startDate = new Date().toISOString().slice(0, 10);
  /** Fourier: "may be some subscription will be paid after service other will be prepaid" — true (prepaid) is the default, same behavior every subscription had before this field existed. */
  billInAdvance = true;
  notes: string | null = null;
  quantity = 1;
  setupFee = 0;
  trialDays = 0;
  termCycles: number | null = null;
  autoRenew = true;

  customers: Customer[] = [];
  plans: SubscriptionPlan[] = [];
  selectedPlan: SubscriptionPlan | null = null;
  /** Active rates only — an inactive rate can't newly be assigned, though an existing subscription keeps whichever rate it already has. */
  activeTaxRates: TaxRateOption[] = [];
  error: string | null = null;

  // Fourier: "add quick customer creation" — inline quick-add panel state, same shape as SalesOrderFormComponent's own.
  showNewCustomer = false;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerEmail = '';
  newCustomerTaxId = '';
  newCustomerError: string | null = null;

  // Fourier: "add ... subscription plan" — a second inline quick-add panel, for plans this time.
  showNewPlan = false;
  newPlanName = '';
  newPlanDescription: string | null = null;
  newPlanPrice: number | null = null;
  newPlanBillingInterval: BillingInterval = 'MONTHLY';
  newPlanTaxRateId: number | null = null;
  newPlanDiscountPercent: number | null = 0;
  newPlanError: string | null = null;

  constructor(
    private subscriptionApi: SubscriptionApiService,
    private planApi: SubscriptionPlanApiService,
    private customerApi: CustomerApiService,
    private taxRateApi: TaxRateApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c) });
    this.planApi.listActive().subscribe({ next: (p) => (this.plans = p) });
    this.taxRateApi.listAll().subscribe((rates) => (this.activeTaxRates = rates.filter((r) => r.active)));
  }

  /** Fourier: "different price and different Billing interval" / "subscription plan should also have tax and discount on it" — pre-fills all four from the chosen plan; the admin can still edit any of them below before saving. */
  onPlanChange(): void {
    this.selectedPlan = this.plans.find((p) => p.id === this.planId) ?? null;
    if (this.selectedPlan) {
      this.price = this.selectedPlan.price;
      this.billingInterval = this.selectedPlan.billingInterval;
      this.taxRateId = this.selectedPlan.taxRateId;
      this.discountPercent = this.selectedPlan.discountPercent;
    }
  }

  /** Fourier: "add quick customer creation" — opens the inline quick-add panel; the picker itself is disabled while it's open, same convention as SalesOrderFormComponent.openNewCustomer. */
  openNewCustomer(): void {
    this.showNewCustomer = true;
    this.newCustomerError = null;
  }

  cancelNewCustomer(): void {
    this.showNewCustomer = false;
    this.newCustomerName = '';
    this.newCustomerPhone = '';
    this.newCustomerEmail = '';
    this.newCustomerTaxId = '';
    this.newCustomerError = null;
  }

  /** Creates the customer, then selects it on this subscription right away. */
  saveNewCustomer(): void {
    if (!this.newCustomerName.trim()) {
      this.newCustomerError = 'Name is required';
      return;
    }
    this.newCustomerError = null;
    this.customerApi
      .create({
        name: this.newCustomerName.trim(),
        phone: this.newCustomerPhone || null,
        email: this.newCustomerEmail || null,
        taxId: this.newCustomerTaxId || null,
      })
      .subscribe({
        next: (customer) => {
          this.customers = [...this.customers, customer].sort((a, b) => a.name.localeCompare(b.name));
          this.customerId = customer.id;
          this.cancelNewCustomer();
        },
        error: (err) => (this.newCustomerError = err?.error?.message ?? 'Failed to save customer'),
      });
  }

  /** Fourier: "add ... subscription plan" — opens the quick-add plan panel. */
  openNewPlan(): void {
    this.showNewPlan = true;
    this.newPlanError = null;
  }

  cancelNewPlan(): void {
    this.showNewPlan = false;
    this.newPlanName = '';
    this.newPlanDescription = null;
    this.newPlanPrice = null;
    this.newPlanBillingInterval = 'MONTHLY';
    this.newPlanTaxRateId = null;
    this.newPlanDiscountPercent = 0;
    this.newPlanError = null;
  }

  /** Creates the plan (active by default), then selects it on this subscription right away — same "just did it, so use it" convenience as saveNewCustomer above. onPlanChange() pre-fills price/billing interval/tax/discount from it exactly as picking an existing plan would. */
  saveNewPlan(): void {
    if (!this.newPlanName.trim()) {
      this.newPlanError = 'Name is required';
      return;
    }
    if (this.newPlanPrice == null || this.newPlanPrice <= 0) {
      this.newPlanError = 'Enter a valid price';
      return;
    }
    this.newPlanError = null;
    this.planApi
      .create({
        name: this.newPlanName.trim(),
        description: this.newPlanDescription || null,
        price: this.newPlanPrice,
        billingInterval: this.newPlanBillingInterval,
        taxRateId: this.newPlanTaxRateId,
        discountPercent: this.newPlanDiscountPercent ?? 0,
        active: true,
      })
      .subscribe({
        next: (plan) => {
          this.plans = [...this.plans, plan].sort((a, b) => a.name.localeCompare(b.name));
          this.planId = plan.id;
          this.onPlanChange();
          this.cancelNewPlan();
        },
        error: (err) => (this.newPlanError = err?.error?.message ?? 'Failed to save plan'),
      });
  }

  submit(): void {
    this.error = null;
    if (!this.customerId || !this.planId) {
      this.error = 'Pick a customer and a plan';
      return;
    }
    if (this.price == null || this.price <= 0) {
      this.error = 'Enter a valid price';
      return;
    }
    this.subscriptionApi
      .create({
        customerId: this.customerId,
        planId: this.planId,
        price: this.price,
        billingInterval: this.billingInterval,
        taxRateId: this.taxRateId,
        discountPercent: this.discountPercent ?? 0,
        startDate: this.startDate || null,
        billInAdvance: this.billInAdvance,
        notes: this.notes || null,
        quantity: this.quantity || 1,
        setupFee: this.setupFee || 0,
        trialDays: this.trialDays || 0,
        termCycles: this.termCycles || null,
        autoRenew: this.autoRenew,
      })
      .subscribe({
        next: (s) => this.router.navigate(['/admin/subscriptions', s.id]),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to create subscription'),
      });
  }
}
