import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { AuthService } from '../../core/auth.service';
import { downloadBlob, openPdfForPrint } from '../../core/download-file';
import { Expense, ExpenseEvent, ExpenseFile, ExpensePayment, ExpensePaymentAccount } from '../../core/models';

/**
 * Admin > Accounting > Expenses > detail — where a pending expense is approved, rejected, edited or deleted, where an approved one is
 * voided or its bill is paid (in full or in parts), and where its files, payments and history are kept.
 */
@Component({
  selector: 'app-expense-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <ng-container *ngIf="expense as e">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ e.payeeName }}</h3>
            <span class="text-muted">{{ e.expenseNumber }}</span>
            <span class="badge" [ngClass]="statusClass(e.status)" style="margin-left:0.5rem;">{{ e.status }}</span>
            <span class="badge badge-danger" *ngIf="e.dueState === 'OVERDUE'" style="margin-left:0.25rem;">{{ 'Overdue' | translate }}</span>
            <span class="badge badge-warning" *ngIf="e.dueState === 'DUE_SOON'" style="margin-left:0.25rem;">{{ 'Due soon' | translate }}</span>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-primary" *ngIf="e.status === 'PENDING'" [routerLink]="['/admin/accounting/expenses', e.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-primary" *ngIf="e.status === 'PENDING' && canApprove" (click)="approve(false)" [disabled]="busy">{{ 'Approve' | translate }}</button>
            <button class="btn btn-outline-primary" *ngIf="e.status === 'PENDING' && canApprove && overBudgetBlocked && auth.isAdmin()" (click)="approve(true)" [disabled]="busy">{{ 'Approve anyway (over budget)' | translate }}</button>
            <button class="btn btn-outline-danger" *ngIf="e.status === 'PENDING' && canApprove" (click)="reject()" [disabled]="busy">{{ 'Reject' | translate }}</button>
            <button class="btn btn-outline-danger" *ngIf="e.status === 'APPROVED' && !e.settledAt && !(e.amountPaid > 0) && canApprove" (click)="voidIt()" [disabled]="busy">{{ 'Void' | translate }}</button>
            <button class="btn btn-outline-danger" *ngIf="e.status === 'PENDING' || e.status === 'REJECTED'" (click)="remove()" [disabled]="busy">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <div class="alert alert-warning mb-3" *ngIf="e.budgetWarning">{{ e.budgetWarning }}</div>

        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Date' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.expenseDate }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Category' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.accountCode }} — {{ e.accountName }}</div></div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ e.amount | number: '1.2-2' }}</div>
            <div class="text-muted" style="font-size:0.75rem;" *ngIf="e.taxAmount">{{ 'VAT {p0} · cost {p1}' | translate: { p0: (e.taxAmount | number: '1.2-2'), p1: (e.netAmount | number: '1.2-2') } }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Payment' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ paymentText(e) }}</div>
            <div class="text-muted" style="font-size:0.75rem;" *ngIf="e.status === 'APPROVED' && !e.paid && !e.settledAt">{{ 'paid {p0} · owed {p1}' | translate: { p0: (e.amountPaid | number: '1.2-2'), p1: (e.balance | number: '1.2-2') } }}</div>
          </div>
          <div class="stat-tile" *ngIf="e.dueDate && !e.paid"><div class="stat-label">{{ 'Due date' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.dueDate }}</div></div>
          <div class="stat-tile" *ngIf="e.reference"><div class="stat-label">{{ 'Reference' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.reference }}</div></div>
          <div class="stat-tile" *ngIf="e.costCenterName"><div class="stat-label">{{ 'Cost center' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.costCenterName }}</div></div>
          <div class="stat-tile" *ngIf="e.createdByName"><div class="stat-label">{{ 'Entered by' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.createdByName }}</div></div>
          <div class="stat-tile" *ngIf="e.approvedByName"><div class="stat-label">{{ 'Approved by' | translate }}</div><div class="stat-value" style="font-size:1.1rem;">{{ e.approvedByName }}</div></div>
        </div>

        <p class="text-muted mb-3" *ngIf="e.memo">{{ e.memo }}</p>
        <p class="text-muted mb-3" *ngIf="e.recurringTemplateId">{{ 'Auto-generated by a recurring expense template.' | translate }}</p>
        <div class="alert alert-danger mb-3" *ngIf="e.status === 'REJECTED' && e.rejectionReason">{{ 'Rejected{p0}: {p1}' | translate: { p0: (e.rejectedByName ? ' by ' + e.rejectedByName : ''), p1: (e.rejectionReason) } }}</div>
        <div class="alert alert-danger mb-3" *ngIf="e.status === 'VOIDED' && e.voidReason">{{ 'Voided{p0}: {p1}' | translate: { p0: (e.voidedByName ? ' by ' + e.voidedByName : ''), p1: (e.voidReason) } }}</div>

        <!-- Paying the bill -->
        <div class="card mb-3" *ngIf="e.status === 'APPROVED' && !e.paid && e.balance > 0 && canManage" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Pay this bill' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <label class="text-muted" style="font-size:0.8rem;">{{ 'Amount' | translate }} <input class="form-control" style="width:9rem;" type="number" step="0.01" min="0.01" name="payAmount" [(ngModel)]="payAmount" /></label>
            <label class="text-muted" style="font-size:0.8rem;">{{ 'Date' | translate }} <input class="form-control" type="date" name="payDate" [(ngModel)]="payDate" /></label>
            <label class="text-muted" style="font-size:0.8rem;">{{ 'Paid from' | translate }}
              <select class="form-select" name="payFrom" [(ngModel)]="payFrom">
                <option [ngValue]="null">{{ 'Cash' | translate }}</option>
                <ng-container *ngFor="let a of paymentAccounts"><option *ngIf="!a.cash" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option></ng-container>
              </select>
            </label>
            <label class="text-muted" style="font-size:0.8rem;">{{ 'Note' | translate }} <input class="form-control" name="payNote" [(ngModel)]="payNote" /></label>
            <button class="btn btn-primary" (click)="pay()" [disabled]="busy">{{ 'Record payment' | translate }}</button>
          </div>
          <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'Owed: {p0}. Paying less than that leaves the rest open.' | translate: { p0: (e.balance | number: '1.2-2') } }}</div>
        </div>

        <div class="mb-3" *ngIf="payments.length">
          <h4 class="mb-2">{{ 'Payments' | translate }}</h4>
          <table class="table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Paid from' | translate }}</th><th>{{ 'Note' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let p of payments"><td>{{ p.paidOn }}</td><td class="text-end">{{ p.amount | number: '1.2-2' }}</td><td>{{ p.paidFromAccountName }}</td><td>{{ p.note || '—' }}</td><td>{{ p.createdByName || '—' }}</td></tr>
            </tbody>
          </table>
        </div>

        <!-- Files -->
        <div class="mb-3">
          <h4 class="mb-2">{{ 'Files' | translate }}</h4>
          <div class="mb-2" *ngIf="e.receiptDataUrl">
            <a [href]="e.receiptDataUrl" target="_blank"><img [src]="e.receiptDataUrl" [alt]="'Receipt' | translate" style="max-width:160px; border:1px solid var(--border, #444); border-radius:4px;" /></a>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Receipt kept from before' | translate }}</div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div *ngFor="let f of files" style="width:8rem;">
              <img *ngIf="f.image && thumbs[f.id]" [src]="thumbs[f.id]" [alt]="f.fileName" style="width:100%; height:5.5rem; object-fit:cover; border-radius:4px;" />
              <div *ngIf="!f.image || !thumbs[f.id]" style="height:5.5rem; display:flex; align-items:center; justify-content:center; font-size:0.75rem; text-align:center; word-break:break-all;">{{ f.fileName }}</div>
              <div style="font-size:0.75rem; word-break:break-all;"><a href="javascript:void(0)" (click)="openFile(f)">{{ f.fileName }}</a></div>
              <div class="text-muted" style="font-size:0.7rem;">{{ '{p0} KB · {p1}' | translate: { p0: (f.sizeBytes / 1024 | number: '1.0-0'), p1: (f.uploadedBy || '') } }}</div>
              <button type="button" class="btn btn-link btn-sm" style="padding:0;" *ngIf="canManage" (click)="removeFile(f)">{{ 'Remove' | translate }}</button>
            </div>
          </div>
          <p class="text-muted" *ngIf="files.length === 0 && !e.receiptDataUrl">{{ 'No files yet.' | translate }}</p>
          <div *ngIf="canManage && e.status !== 'REJECTED' && e.status !== 'VOIDED'" class="mt-2">
            <input type="file" multiple accept="image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain" (change)="addFiles($event)" />
            <span class="text-muted" style="font-size:0.8rem;"> {{ 'Photos, PDF or text, up to 10 MB each.' | translate }}</span>
          </div>
        </div>

        <!-- History -->
        <div class="mb-3">
          <h4 class="mb-2">{{ 'History' | translate }}</h4>
          <table class="table-sm">
            <tbody>
              <tr *ngFor="let ev of events"><td style="white-space:nowrap;">{{ ev.at | date: 'yyyy-MM-dd HH:mm' }}</td><td>{{ ev.kind }}</td><td>{{ ev.detail }}</td><td>{{ ev.by }}</td></tr>
            </tbody>
          </table>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class ExpenseDetailComponent implements OnInit {
  expense: Expense | null = null;
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  payments: ExpensePayment[] = [];
  files: ExpenseFile[] = [];
  events: ExpenseEvent[] = [];
  thumbs: Record<number, string> = {};
  paymentAccounts: ExpensePaymentAccount[] = [];
  payAmount: number | null = null;
  payDate = new Date().toISOString().slice(0, 10);
  payFrom: number | null = null;
  payNote = '';
  /** True after an approval was refused because the expense is over its budget. */
  overBudgetBlocked = false;

  constructor(private expenseApi: ExpenseApiService, private route: ActivatedRoute, private router: Router, public auth: AuthService) {}

  get canApprove(): boolean {
    return this.auth.isAdmin() || this.auth.hasPermission('APPROVE_EXPENSE');
  }

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  ngOnInit(): void {
    this.expenseApi.paymentAccounts().subscribe({ next: (a) => (this.paymentAccounts = a), error: () => {} });
    this.load();
  }

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  load(): void {
    this.expenseApi.getById(this.id).subscribe({
      next: (e) => {
        this.expense = e;
        this.payAmount = e.balance > 0 ? e.balance : null;
        this.payFrom = e.paidFromAccountId;
        this.expenseApi.payments(e.id).subscribe({ next: (p) => (this.payments = p), error: () => {} });
        this.expenseApi.events(e.id).subscribe({ next: (v) => (this.events = v), error: () => {} });
        this.loadFiles();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load expense'),
    });
  }

  private loadFiles(): void {
    this.expenseApi.files(this.id).subscribe({
      next: (l) => {
        this.files = l;
        l.filter((f) => f.image && !this.thumbs[f.id]).forEach((f) =>
          this.expenseApi.fileBlob(this.id, f.id).subscribe({ next: (b) => (this.thumbs = { ...this.thumbs, [f.id]: URL.createObjectURL(b) }), error: () => {} })
        );
      },
      error: () => {},
    });
  }

  private fail(err: any, fallback: string): void {
    this.busy = false;
    this.error = err?.error?.message ?? fallback;
  }

  paymentText(e: Expense): string {
    const from = e.paidFromAccountName ?? 'Cash';
    if (e.paid) return 'Paid from ' + from;
    if (e.status === 'APPROVED') return e.settledAt ? 'Bill — paid off' : e.amountPaid > 0 ? 'Bill — part paid' : 'Bill — unpaid';
    return 'Bill — to pay';
  }

  approve(override: boolean): void {
    if (!this.expense) return;
    this.error = null;
    this.notice = null;
    this.busy = true;
    this.expenseApi.approve(this.expense.id, override).subscribe({
      next: () => {
        this.busy = false;
        this.overBudgetBlocked = false;
        this.load();
      },
      error: (err) => {
        this.overBudgetBlocked = (err?.error?.message ?? '').startsWith('Over budget');
        this.fail(err, 'Failed to approve expense');
      },
    });
  }

  reject(): void {
    if (!this.expense) return;
    const reason = prompt('Reason for rejecting (optional):') ?? '';
    this.error = null;
    this.busy = true;
    this.expenseApi.reject(this.expense.id, reason || null).subscribe({
      next: () => {
        this.busy = false;
        this.load();
      },
      error: (err) => this.fail(err, 'Failed to reject expense'),
    });
  }

  voidIt(): void {
    if (!this.expense) return;
    const reason = prompt('Reason for voiding this expense (optional):') ?? '';
    if (!confirm('Void this expense? This reverses its posted entry — it cannot be undone.')) return;
    this.error = null;
    this.busy = true;
    this.expenseApi.voidExpense(this.expense.id, reason || null).subscribe({
      next: () => {
        this.busy = false;
        this.load();
      },
      error: (err) => this.fail(err, 'Failed to void expense'),
    });
  }

  remove(): void {
    if (!this.expense) return;
    if (!confirm('Delete this ' + this.expense.status.toLowerCase() + ' expense? Nothing was posted for it. This cannot be undone.')) return;
    this.busy = true;
    this.expenseApi.delete(this.expense.id).subscribe({
      next: () => this.router.navigate(['/admin/accounting/expenses']),
      error: (err) => this.fail(err, 'Failed to delete expense'),
    });
  }

  pay(): void {
    if (!this.expense || !this.payAmount) return;
    this.error = null;
    this.notice = null;
    this.busy = true;
    this.expenseApi.pay(this.expense.id, { amount: this.payAmount, paidOn: this.payDate || null, paidFromAccountId: this.payFrom, note: this.payNote || null }).subscribe({
      next: (e) => {
        this.busy = false;
        this.notice = e.settledAt ? 'The bill is now paid off.' : 'Payment recorded. ' + e.balance.toFixed(2) + ' is still owed.';
        this.payNote = '';
        this.load();
      },
      error: (err) => this.fail(err, 'Failed to record the payment'),
    });
  }

  addFiles(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const list = Array.from(input.files ?? []);
    input.value = '';
    if (!this.expense || !list.length) return;
    this.error = null;
    let pending = list.length;
    list.forEach((f) =>
      this.expenseApi.addFile(this.expense!.id, f).subscribe({
        next: () => {
          if (--pending === 0) this.load();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Could not add ' + f.name;
          if (--pending === 0) this.load();
        },
      })
    );
  }

  openFile(f: ExpenseFile): void {
    this.expenseApi.fileBlob(this.id, f.id).subscribe({
      next: (b) => (f.contentType === 'text/plain' ? downloadBlob(b, f.fileName) : openPdfForPrint(b)),
      error: (err) => this.fail(err, 'Could not open the file'),
    });
  }

  removeFile(f: ExpenseFile): void {
    if (!confirm('Remove ' + f.fileName + '?')) return;
    this.expenseApi.deleteFile(this.id, f.id).subscribe({
      next: () => this.load(),
      error: (err) => this.fail(err, 'Could not remove the file'),
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'PENDING':
        return 'badge-secondary';
      case 'APPROVED':
        return 'badge-success';
      case 'REJECTED':
      case 'VOIDED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
