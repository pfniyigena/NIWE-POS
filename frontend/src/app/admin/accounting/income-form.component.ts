import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { IncomeApiService } from '../../core/api/income-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { SupplierApiService } from '../../core/api/supplier-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Account, AppUser, Branch, CostCenter, Customer, PayeeType, Supplier } from '../../core/models';
import { BranchApiService } from '../../core/api/branch-api.service';

import { T } from '../../core/i18n/t';
/** Admin > Accounting > Incomes > New — ExpenseFormComponent's mirror image, against REVENUE-type accounts. See backend AccountingService.postIncome. */
@Component({
  selector: 'app-income-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'New income' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Date' | translate }}</label>
          <app-date-picker name="incomeDate" [(ngModel)]="incomeDate" required ></app-date-picker>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Category' | translate }}</label>
          <select class="form-select" name="accountId" [(ngModel)]="accountId" required>
            <option [ngValue]="null" disabled>{{ 'Select a revenue account…' | translate }}</option>
            <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="accounts.length === 0">
            {{ 'No Revenue-type accounts found besides Sales Revenue — add one under Chart of Accounts first (e.g. "Other Income").' | translate }}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Received from' | translate }}</label>
          <select class="form-select" name="payeeType" [(ngModel)]="payeeType" (ngModelChange)="onPayeeTypeChange()">
            <option value="CLIENT">{{ 'Customer' | translate }}</option>
            <option value="VENDOR">{{ 'Vendor (supplier)' | translate }}</option>
            <option value="STAFF">{{ 'Staff' | translate }}</option>
            <option value="OTHER">{{ 'Other / one-off' | translate }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'CLIENT'">
          <label class="form-label">{{ 'Customer' | translate }}</label>
          <select class="form-select" name="payeeCustomer" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(customers)" required>
            <option [ngValue]="null" disabled>{{ 'Select a customer…' | translate }}</option>
            <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'VENDOR'">
          <label class="form-label">{{ 'Supplier' | translate }}</label>
          <select class="form-select" name="payeeSupplier" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(suppliers)" required>
            <option [ngValue]="null" disabled>{{ 'Select a supplier…' | translate }}</option>
            <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'STAFF'">
          <label class="form-label">{{ 'Staff member' | translate }}</label>
          <select class="form-select" name="payeeStaff" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(staff)" required>
            <option [ngValue]="null" disabled>{{ 'Select a staff member…' | translate }}</option>
            <option *ngFor="let u of staff" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'OTHER'">
          <label class="form-label">{{ 'Payer name' | translate }}</label>
          <input class="form-control" name="payeeName" [(ngModel)]="payeeName" [placeholder]="'e.g. Landlord rebate' | translate" required />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Amount' | translate }}</label>
          <input class="form-control" type="number" step="0.01" min="0.01" name="amount" [(ngModel)]="amount" required />
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="received" [(ngModel)]="received" /> {{ 'Received immediately (into Cash)' | translate }}
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="!received">
            {{ 'Left unchecked, this posts as money still owed to the store (Accounts Receivable) instead of a cash receipt.' | translate }}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Cost center (optional)' | translate }}</label>
          <select class="form-select" name="costCenterId" [(ngModel)]="costCenterId">
            <option [ngValue]="null">{{ 'None' | translate }}</option>
            <option *ngFor="let c of costCenters" [ngValue]="c.id">{{ c.code }} — {{ c.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="branches.length > 1">
          <label class="form-label">{{ 'Branch' | translate }}</label>
          <select class="form-select" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Memo (optional)' | translate }}</label>
          <input class="form-control" name="memo" [(ngModel)]="memo" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" routerLink="/admin/accounting/incomes">{{ 'Cancel' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class IncomeFormComponent implements OnInit {
  incomeDate = new Date().toISOString().slice(0, 10);
  accountId: number | null = null;
  payeeType: PayeeType = 'CLIENT';
  payeeId: number | null = null;
  payeeName = '';
  amount: number | null = null;
  received = true;
  memo: string | null = null;
  costCenterId: number | null = null;
  /** Branch this document is issued from; the picker only shows with more than one active branch. */
  branchId: number | null = null;
  branches: Branch[] = [];

  accounts: Account[] = [];
  costCenters: CostCenter[] = [];
  suppliers: Supplier[] = [];
  staff: AppUser[] = [];
  customers: Customer[] = [];
  error: string | null = null;

  constructor(
    private incomeApi: IncomeApiService,
    private accountApi: AccountApiService,
    private costCenterApi: CostCenterApiService,
    private supplierApi: SupplierApiService,
    private userApi: UserApiService,
    private customerApi: CustomerApiService,
    private branchApi: BranchApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => {
      this.branches = b;
      if (this.branchId == null) {
        this.branchId = b.find((x) => x.headquarters)?.id ?? null;
      }
    });
    this.accountApi.listActiveByType('REVENUE').subscribe({ next: (a) => (this.accounts = a) });
    this.costCenterApi.listActive().subscribe({ next: (c) => (this.costCenters = c) });
    this.supplierApi.listActive().subscribe({ next: (s) => (this.suppliers = s) });
    this.userApi.list().subscribe({ next: (u) => (this.staff = u.filter((x) => x.active)) });
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c) });
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
      this.error = T('Category and amount are required');
      return;
    }
    if (this.payeeType !== 'OTHER' && (!this.payeeId || !this.payeeName)) {
      this.error = T('Pick a payer');
      return;
    }
    const payload = {
      incomeDate: this.incomeDate,
      accountId: this.accountId,
      payeeType: this.payeeType,
      payeeId: this.payeeType === 'OTHER' ? null : this.payeeId,
      payeeName: this.payeeName,
      amount: this.amount,
      received: this.received,
      memo: this.memo || null,
      costCenterId: this.costCenterId,
      branchId: this.branchId,
    };
    this.incomeApi.create(payload).subscribe({
      next: () => this.router.navigate(['/admin/accounting/incomes']),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save income')),
    });
  }
}
