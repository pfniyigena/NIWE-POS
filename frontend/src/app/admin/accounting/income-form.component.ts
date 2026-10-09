import { Component, OnInit } from '@angular/core';
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

/** Admin > Accounting > Incomes > New — ExpenseFormComponent's mirror image, against REVENUE-type accounts. See backend AccountingService.postIncome. */
@Component({
  selector: 'app-income-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">New income</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Date</label>
          <app-date-picker name="incomeDate" [(ngModel)]="incomeDate" required ></app-date-picker>
        </div>
        <div class="form-group">
          <label class="form-label">Category</label>
          <select class="form-select" name="accountId" [(ngModel)]="accountId" required>
            <option [ngValue]="null" disabled>Select a revenue account…</option>
            <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="accounts.length === 0">
            No Revenue-type accounts found besides Sales Revenue — add one under Chart of Accounts first (e.g. "Other Income").
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Received from</label>
          <select class="form-select" name="payeeType" [(ngModel)]="payeeType" (ngModelChange)="onPayeeTypeChange()">
            <option value="CLIENT">Customer</option>
            <option value="VENDOR">Vendor (supplier)</option>
            <option value="STAFF">Staff</option>
            <option value="OTHER">Other / one-off</option>
          </select>
        </div>
        <div class="form-group" *ngIf="payeeType === 'CLIENT'">
          <label class="form-label">Customer</label>
          <select class="form-select" name="payeeCustomer" [(ngModel)]="payeeId" (ngModelChange)="onPayeePicked(customers)" required>
            <option [ngValue]="null" disabled>Select a customer…</option>
            <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
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
        <div class="form-group" *ngIf="payeeType === 'OTHER'">
          <label class="form-label">Payer name</label>
          <input class="form-control" name="payeeName" [(ngModel)]="payeeName" placeholder="e.g. Landlord rebate" required />
        </div>
        <div class="form-group">
          <label class="form-label">Amount</label>
          <input class="form-control" type="number" step="0.01" min="0.01" name="amount" [(ngModel)]="amount" required />
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="received" [(ngModel)]="received" /> Received immediately (into Cash)
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="!received">
            Left unchecked, this posts as money still owed to the store (Accounts Receivable) instead of a cash receipt.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Cost center (optional)</label>
          <select class="form-select" name="costCenterId" [(ngModel)]="costCenterId">
            <option [ngValue]="null">None</option>
            <option *ngFor="let c of costCenters" [ngValue]="c.id">{{ c.code }} — {{ c.name }}</option>
          </select>
        </div>
        <div class="form-group" *ngIf="branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-select" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Memo (optional)</label>
          <input class="form-control" name="memo" [(ngModel)]="memo" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/accounting/incomes">Cancel</a>
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
      this.error = 'Category and amount are required';
      return;
    }
    if (this.payeeType !== 'OTHER' && (!this.payeeId || !this.payeeName)) {
      this.error = 'Pick a payer';
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
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save income'),
    });
  }
}
