import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { Branch, Invoice } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

/**
 * Admin > Sales > Invoices — Fourier: "Add Invoice ... without touching warehousing"; later:
 * "someone can create invoice (with invoice lines) without creating sales order." Store-wide
 * ledger; one is still generated from a Sales Order's own detail page once confirmed, or created
 * standalone right here via "+ New invoice".
 *
 * <p>Fourier: "on the customer ... view button where we can have button to view the list of
 * invoices, payments etc" — arriving from a customer's own row via `?customerId=&customerName=`
 * (see CustomerListComponent's "Invoices" link) narrows this to just that customer's invoices,
 * same `?productId=&productName=` convention ReportViewerComponent's own Stock Movement History
 * link already uses.
 */
@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Invoices for ' + customerName : 'Invoices' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/invoices" style="font-size:0.85rem;">Show every customer's invoices</a>
        </div>
        <a class="btn btn-primary" routerLink="/admin/invoices/new">+ New invoice</a>
      </div>
      <div class="flex gap-2 align-center mb-3" *ngIf="branches.length > 1">
        <select class="form-select" style="width:auto;" [(ngModel)]="branchFilter" title="Branch">
          <option [ngValue]="null">All branches</option>
          <option *ngFor="let br of branches" [ngValue]="br.id">{{ br.name }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Invoice #</th>
            <th>Sale order</th>
            <th>Customer</th>
            <th>Date</th>
            <th>Due date</th>
            <th>Status</th>
            <th *ngIf="branches.length > 1">Branch</th>
            <th class="text-end">Total</th>
            <th class="text-end">Outstanding</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of shown">
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.salesOrderNumber || '—' }}</td>
            <td>{{ inv.customerName }}</td>
            <td>{{ inv.invoiceDate | date: 'medium' }}</td>
            <td>{{ inv.dueDate || '—' }}</td>
            <td><span class="badge" [ngClass]="statusClass(inv.status)">{{ inv.status }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(inv.branchId) }}</td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ inv.status === 'ISSUED' ? (inv.amountOutstanding | number: '1.2-2') : '—' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="invoices.length === 0">
            <td colspan="9" class="text-muted">
              {{ customerName ? 'No invoices for ' + customerName + ' yet.' : 'No invoices yet — generate one from a sale order\\'s detail page once it\\'s confirmed, or create one directly with "+ New invoice".' }}
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class InvoiceListComponent implements OnInit {
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  get shown(): Invoice[] {
    return this.branchFilter == null ? this.invoices : this.invoices.filter((inv) => effectiveBranchId(inv.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  invoices: Invoice[] = [];
  error: string | null = null;
  /** Fourier: "on the customer ... view button" — set from `?customerId=&customerName=`, null for the store-wide list. */
  customerId: number | null = null;
  customerName: string | null = null;

  constructor(private invoiceApi: InvoiceApiService, private route: ActivatedRoute, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    this.invoiceApi.listAll(this.customerId).subscribe({
      next: (i) => (this.invoices = i),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load invoices'),
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'ISSUED':
        return 'badge-success';
      case 'VOID':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
