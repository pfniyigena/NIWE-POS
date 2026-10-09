import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RecurringInvoiceApiService } from '../core/api/recurring-invoice-api.service';
import { Branch, Invoice, RecurringInvoiceTemplate } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf } from '../core/branch-util';

/**
 * Admin > Sales > Recurring invoices > detail — `/admin/recurring-invoices/:id`. Shows the
 * template's own configuration plus every invoice the daily sweep (or a manual "Run now") has
 * generated for it (see backend RecurringInvoiceTemplateService.generatedInvoices/
 * InvoiceService.generateForRecurringInvoiceTemplate). "Pause"/"Resume" toggle {@link
 * RecurringInvoiceTemplate#active} without losing the template's own configuration or history —
 * same "toggle, don't delete" convention as RecurringExpenseTemplate/Subscription.
 */
@Component({
  selector: 'app-recurring-invoice-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Recurring invoice{{ template ? ' — ' + template.name : '' }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" *ngIf="template" [routerLink]="['/admin/recurring-invoices', template.id, 'edit']">Edit</a>
          <a class="btn btn-link" routerLink="/admin/recurring-invoices">Back to recurring invoices</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="template as t">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>Customer:</strong> {{ t.customerName }}</div>
          <div *ngIf="branches.length > 1"><strong>Branch:</strong> {{ branchName(t.branchId) }}</div>
          <div><strong>Frequency:</strong> {{ t.frequency }}</div>
          <div><strong>Est. amount / cycle:</strong> {{ t.estimatedAmount | number: '1.2-2' }}</div>
          <div><strong>Status:</strong> <span class="badge" [ngClass]="t.active ? 'badge-success' : 'badge-secondary'">{{ t.active ? 'Active' : 'Paused' }}</span></div>
          <div><strong>Next run:</strong> {{ t.nextRunDate }}</div>
          <div *ngIf="t.dueInDays"><strong>Due in:</strong> {{ t.dueInDays }} days</div>
          <div *ngIf="t.lastRunAt"><strong>Last run:</strong> {{ t.lastRunAt | date: 'medium' }}</div>
        </div>
        <div class="text-muted mt-2" *ngIf="t.notes">{{ t.notes }}</div>

        <table class="table-sm mt-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Quantity</th>
              <th class="text-end">Unit price</th>
              <th class="text-end" *ngIf="hasLineDiscount(t)">Discount %</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let line of t.lines">
              <td>{{ line.productName }}<span class="text-muted" *ngIf="line.description"> — {{ line.description }}</span></td>
              <td class="text-end">{{ line.quantity }}</td>
              <td class="text-end">{{ line.unitPrice | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="hasLineDiscount(t)">{{ line.discountPercent || 0 }}</td>
            </tr>
          </tbody>
        </table>

        <div class="flex gap-2 mt-3">
          <button class="btn btn-outline-primary" (click)="runNow()" [disabled]="!t.active || busy">{{ busy ? 'Running…' : 'Run now' }}</button>
          <button class="btn btn-outline-secondary" (click)="pause()" *ngIf="t.active" [disabled]="busy">Pause</button>
          <button class="btn btn-outline-secondary" (click)="resume()" *ngIf="!t.active" [disabled]="busy">Resume</button>
        </div>
      </div>

      <h4 class="mt-3 mb-2">Generated invoices</h4>
      <table>
        <thead>
          <tr>
            <th>Invoice #</th>
            <th>Generated on</th>
            <th>Status</th>
            <th class="text-end">Total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of history">
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.invoiceDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="inv.status === 'ISSUED' ? 'badge-success' : 'badge-secondary'">{{ inv.status }}</span></td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="history.length === 0">
            <td colspan="5" class="text-muted">No invoices generated for this template yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RecurringInvoiceDetailComponent implements OnInit {
  /** Branch shown on the detail only with more than one active branch. */
  branches: Branch[] = [];

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  id!: number;
  template: RecurringInvoiceTemplate | null = null;
  history: Invoice[] = [];
  error: string | null = null;
  busy = false;

  constructor(private route: ActivatedRoute, private recurringInvoiceApi: RecurringInvoiceApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadAll();
  }

  private loadAll(): void {
    this.recurringInvoiceApi.getById(this.id).subscribe({
      next: (t) => (this.template = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load recurring invoice'),
    });
    this.recurringInvoiceApi.generatedInvoices(this.id).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load generated invoices'),
    });
  }

  hasLineDiscount(t: RecurringInvoiceTemplate): boolean {
    return t.lines.some((l) => (l.discountPercent ?? 0) > 0);
  }

  runNow(): void {
    this.error = null;
    this.busy = true;
    this.recurringInvoiceApi.runNow(this.id).subscribe({
      next: () => {
        this.busy = false;
        this.loadAll();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to run recurring invoice';
      },
    });
  }

  pause(): void {
    this.error = null;
    this.busy = true;
    this.recurringInvoiceApi.setActive(this.id, false).subscribe({
      next: (t) => {
        this.busy = false;
        this.template = t;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to pause recurring invoice';
      },
    });
  }

  resume(): void {
    this.error = null;
    this.busy = true;
    this.recurringInvoiceApi.setActive(this.id, true).subscribe({
      next: (t) => {
        this.busy = false;
        this.template = t;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to resume recurring invoice';
      },
    });
  }
}
