import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { RecurringExpenseApiService } from '../../core/api/recurring-expense-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { SupplierApiService } from '../../core/api/supplier-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Account, AppUser, CostCenter, Customer, PayeeType, RecurrenceFrequency, Supplier } from '../../core/models';

/**
 * Admin > Accounting > Recurring expenses > New/Edit — a template the daily scheduled sweep turns
 * into a new PENDING Expense on or after {@link nextRunDate}, which then advances by one week or
 * month each time it fires (see backend RecurringExpenseTemplateService). Mirrors
 * ExpenseFormComponent's own category/payee fields minus date and receipt, which are per-run, not
 * per-template.
 */
@Component({
  selector: 'app-recurring-expense-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit recurring expense' : 'New recurring expense' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Shop rent" required />
        </div>
        <div class="form-group">
          <label class="form-label">Category</label>
          <select class="form-select" name="accountId" [(ngModel)]="accountId" required>
            <option [ngValue]="null" disabled>Select an expense account…</option>
            <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Paid to</label>
          <select class="form-select" name="payeeType" [(ngModel)]="payeeType" (ngModelChange)="onPayeeTypeChange()">
            <option value="VENDOR">Vendor (supplier)</option>
            <option value="STAFF">Staff</option>
            <option value="CLIENT">Customer</option>
            <option value="OTHER">Other / one-off</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'VENDOR'">
          <label class="form-label">Supplier</label>
          <select class="form-select" name="payeeSupplier" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(suppliers)" required>
            <option [ngValue]="null" disabled>Select a supplier…</option>
            <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'STAFF'">
          <label class="form-label">Staff member</label>
          <select class="form-select" name="payeeStaff" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(staff)" required>
            <option [ngValue]="null" disabled>Select a staff member…</option>
            <option *ngFor="let u of staff" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'CLIENT'">
          <label class="form-label">Customer</label>
          <select class="form-select" name="payeeCustomer" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(customers)" required>
            <option [ngValue]="null" disabled>Select a customer…</option>
            <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'OTHER'">
          <label class="form-label">Payee name</label>
          <input class="form-control" name="payeeName" [(ngModel)]="payeeName" placeholder="e.g. City Water Authority" required />
        </div>
        <div class="form-group">
          <label class="form-label">Amount</label>
          <input class="form-control" type="number" step="0.01" min="0.01" name="amount" [(ngModel)]="amount" required />
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="paid" [(ngModel)]="paid" /> Paid immediately (from Cash)
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="!paid">
            Left unchecked, each generated expense posts as a bill owed to the payee instead of a cash payment.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Cost center (optional)</label>
          <select class="form-select" name="costCenterId" [(ngModel)]="costCenterId">
            <option [ngValue]="null">None</option>
            <option *ngFor="let c of costCenters" [ngValue]="c.id">{{ c.code }} — {{ c.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Memo (optional)</label>
          <input class="form-control" name="memo" [(ngModel)]="memo" />
        </div>
        <div class="form-group">
          <label class="form-label">Frequency</label>
          <select class="form-select" name="frequency" [(ngModel)]="frequency">
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Next run date</label>
          <app-date-picker name="nextRunDate" [(ngModel)]="nextRunDate" required></app-date-picker>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The daily sweep generates the next expense on or after this date, then advances it by one {{ frequency === 'WEEKLY' ? 'week' : 'month' }}.
          </div>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/accounting/recurring-expenses">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class RecurringExpenseFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  accountId: number | null = null;
  payeeType: PayeeType = 'OTHER';
  payeeId: number | null = null;
  payeeName = '';
  amount: number | null = null;
  paid = true;
  memo: string | null = null;
  costCenterId: number | null = null;
  frequency: RecurrenceFrequency = 'MONTHLY';
  nextRunDate = new Date().toISOString().slice(0, 10);
  active = true;

  accounts: Account[] = [];
  costCenters: CostCenter[] = [];
  suppliers: Supplier[] = [];
  staff: AppUser[] = [];
  customers: Customer[] = [];
  error: string | null = null;

  constructor(
    private recurringApi: RecurringExpenseApiService,
    private accountApi: AccountApiService,
    private costCenterApi: CostCenterApiService,
    private supplierApi: SupplierApiService,
    private userApi: UserApiService,
    private customerApi: CustomerApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a) });
    this.costCenterApi.listActive().subscribe({ next: (c) => (this.costCenters = c) });
    this.supplierApi.listActive().subscribe({ next: (s) => (this.suppliers = s) });
    this.userApi.list().subscribe({ next: (u) => (this.staff = u.filter((x) => x.active)) });
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c) });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.recurringApi.getById(this.id).subscribe({
      next: (t) => {
        this.name = t.name;
        this.accountId = t.accountId;
        this.payeeType = t.payeeType;
        this.payeeId = t.payeeId;
        this.payeeName = t.payeeName;
        this.amount = t.amount;
        this.paid = t.paid;
        this.memo = t.memo;
        this.costCenterId = t.costCenterId;
        this.frequency = t.frequency;
        this.nextRunDate = t.nextRunDate.slice(0, 10);
        this.active = t.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load template'),
    });
  }

  onPayeeTypeChange(): void {
    this.payeeId = null;
    this.payeeName = '';
  }

  onPayeePicked(source: { id: number; name?: string; fullName?: string }[]): void {
    const picked = source.find((s) => s.id === this.payeeId);
    this.payeeName = picked ? (picked.name ?? picked.fullName ?? '') : '';
  }

  submit(): void {
    this.error = null;
    if (!this.accountId || this.amount == null) {
      this.error = 'Category and amount are required';
      return;
    }
    if (this.payeeType !== 'OTHER' && (!this.payeeId || !this.payeeName)) {
      this.error = 'Pick a payee';
      return;
    }
    const payload = {
      name: this.name,
      accountId: this.accountId,
      payeeType: this.payeeType,
      payeeId: this.payeeType === 'OTHER' ? null : this.payeeId,
      payeeName: this.payeeName,
      amount: this.amount,
      paid: this.paid,
      memo: this.memo || null,
      costCenterId: this.costCenterId,
      frequency: this.frequency,
      nextRunDate: this.nextRunDate,
      active: this.active,
    };
    const call = this.id ? this.recurringApi.update(this.id, payload) : this.recurringApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/accounting/recurring-expenses']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save template'),
    });
  }
}
