import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { SupplierApiService } from '../../core/api/supplier-api.service';
import { UserApiService } from '../../core/api/user-api.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Account, AppUser, Branch, CostCenter, Customer, PayeeType, Supplier } from '../../core/models';

/**
 * Admin > Accounting > Expenses > New/Edit — Fourier's own scoping choice for this batch of work
 * ("Add new Expense & Income entry screens", later extended with "Approval workflow before
 * posting" and "Receipt/attachment upload"): category + payee (Supplier/Staff/Customer/OTHER
 * lookup) + amount + date + an optional receipt photo, logged as PENDING — it only actually posts
 * its own journal entry once approved on the detail screen (see backend ExpenseService.approve).
 * Edit is only reachable while still PENDING (see backend ExpenseService.update's own guard).
 * {@code payeeName} is always sent as a snapshot (see the backend Expense.payeeName javadoc) —
 * picking a payee from a lookup just fills it in, it never stays a live reference.
 */
@Component({
  selector: 'app-expense-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit expense' : 'New expense' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Date</label>
          <app-date-picker name="expenseDate" [(ngModel)]="expenseDate" required ></app-date-picker>
        </div>
        <div class="form-group">
          <label class="form-label">Category</label>
          <select class="form-select" name="accountId" [(ngModel)]="accountId" required>
            <option [ngValue]="null" disabled>Select an expense account…</option>
            <option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="accounts.length === 0">
            No Expense-type accounts found — add one under Chart of Accounts first.
          </div>
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
            Left unchecked, this posts as a bill owed to the payee (Accounts Payable) instead of a cash payment.
          </div>
        </div>
        <div class="form-group" *ngIf="branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-select" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
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
          <label class="form-label">Receipt (optional)</label>
          <div class="flex gap-2 align-center">
            <img *ngIf="receiptDataUrl" [src]="receiptDataUrl" alt="Receipt" style="max-width:80px; max-height:80px; border:1px solid #444; border-radius:4px; object-fit:cover;" />
            <span class="text-muted" style="font-size:0.85rem;" *ngIf="!receiptDataUrl">No receipt uploaded</span>
          </div>
          <input type="file" accept="image/*,application/pdf" (change)="onReceiptSelected($event)" style="margin-top:0.5rem;" />
          <div class="text-danger" style="font-size:0.8rem;" *ngIf="receiptError">{{ receiptError }}</div>
          <button type="button" class="btn btn-sm btn-outline-secondary" style="margin-top:0.5rem;" *ngIf="receiptDataUrl" (click)="removeReceipt()">
            Remove receipt
          </button>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/accounting/expenses">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class ExpenseFormComponent implements OnInit {
  id: number | null = null;
  expenseDate = new Date().toISOString().slice(0, 10);
  accountId: number | null = null;
  payeeType: PayeeType = 'OTHER';
  payeeId: number | null = null;
  payeeName = '';
  amount: number | null = null;
  paid = true;
  memo: string | null = null;
  costCenterId: number | null = null;
  /** Branch the expense belongs to; the picker only shows with more than one active branch. */
  branchId: number | null = null;
  branches: Branch[] = [];
  receiptDataUrl: string | null = null;
  receiptError: string | null = null;
  private readonly maxReceiptBytes = 4 * 1024 * 1024;

  accounts: Account[] = [];
  costCenters: CostCenter[] = [];
  suppliers: Supplier[] = [];
  staff: AppUser[] = [];
  customers: Customer[] = [];
  error: string | null = null;

  constructor(
    private expenseApi: ExpenseApiService,
    private accountApi: AccountApiService,
    private costCenterApi: CostCenterApiService,
    private supplierApi: SupplierApiService,
    private userApi: UserApiService,
    private customerApi: CustomerApiService,
    private branchApi: BranchApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => {
      this.branches = b;
      if (this.branchId == null) {
        this.branchId = b.find((x) => x.headquarters)?.id ?? null;
      }
    });
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a) });
    this.costCenterApi.listActive().subscribe({ next: (c) => (this.costCenters = c) });
    this.supplierApi.listActive().subscribe({ next: (s) => (this.suppliers = s) });
    this.userApi.list().subscribe({ next: (u) => (this.staff = u.filter((x) => x.active)) });
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c) });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.expenseApi.getById(this.id).subscribe({
        next: (e) => {
          if (e.status !== 'PENDING') {
            this.error = `This expense is ${e.status.toLowerCase()} — only a pending one can be edited.`;
            return;
          }
          this.expenseDate = e.expenseDate.slice(0, 10);
          this.accountId = e.accountId;
          this.payeeType = e.payeeType;
          this.payeeId = e.payeeId;
          this.payeeName = e.payeeName;
          this.amount = e.amount;
          this.paid = e.paid;
          this.memo = e.memo;
          this.costCenterId = e.costCenterId;
          this.branchId = e.branchId ?? this.branchId;
          this.receiptDataUrl = e.receiptDataUrl;
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load expense'),
      });
    }
  }

  onPayeeTypeChange(): void {
    this.payeeId = null;
    this.payeeName = '';
  }

  onPayeePicked(source: { id: number; name?: string; fullName?: string }[]): void {
    const picked = source.find((s) => s.id === this.payeeId);
    this.payeeName = picked ? (picked.name ?? picked.fullName ?? '') : '';
  }

  onReceiptSelected(event: Event): void {
    this.receiptError = null;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > this.maxReceiptBytes) {
      this.receiptError = 'Receipt file is too large (max 4MB) — choose a smaller file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => (this.receiptDataUrl = reader.result as string);
    reader.onerror = () => (this.receiptError = 'Failed to read the selected file');
    reader.readAsDataURL(file);
  }

  removeReceipt(): void {
    this.receiptDataUrl = null;
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
      expenseDate: this.expenseDate,
      accountId: this.accountId,
      payeeType: this.payeeType,
      payeeId: this.payeeType === 'OTHER' ? null : this.payeeId,
      payeeName: this.payeeName,
      amount: this.amount,
      paid: this.paid,
      memo: this.memo || null,
      costCenterId: this.costCenterId,
      branchId: this.branchId,
      receiptDataUrl: this.receiptDataUrl,
    };
    const call = this.id ? this.expenseApi.update(this.id, payload) : this.expenseApi.create(payload);
    call.subscribe({
      next: (e) => this.router.navigate(['/admin/accounting/expenses', e.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save expense'),
    });
  }
}
