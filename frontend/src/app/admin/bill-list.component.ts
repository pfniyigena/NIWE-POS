import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { BillApiService } from '../core/api/bill-api.service';
import { Branch, Bill } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

/**
 * Admin > Purchases > Bills — Fourier: "The Purchase order should flow same workflow as Sale order
 * (with Bills and Refunds equivalent with Invoices and Credit note)." Store-wide ledger; one is
 * generated automatically once a purchase order is fully received, or manually from that order's
 * own detail page from the moment it's sent to the supplier — see the backend BillService's own
 * class javadoc. Deliberately no "+ New bill" here: unlike Invoice, there is no standalone bill
 * with no purchase order behind it in this app.
 */
@Component({
  selector: 'app-bill-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">Bills</h3>
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
            <th>Bill #</th>
            <th>Purchase order</th>
            <th>Supplier</th>
            <th>Date</th>
            <th>Status</th>
            <th *ngIf="branches.length > 1">Branch</th>
            <th class="text-end">Total</th>
            <th class="text-end">Outstanding</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of shown">
            <td>{{ b.billNumber }}</td>
            <td>{{ b.purchaseOrderNumber || '—' }}</td>
            <td>{{ b.supplierName }}</td>
            <td>{{ b.billDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(b.status)">{{ b.status }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(b.branchId) }}</td>
            <td class="text-end">{{ b.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ b.status === 'ISSUED' ? (b.amountOutstanding | number: '1.2-2') : '—' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/bills', b.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="bills.length === 0">
            <td colspan="8" class="text-muted">No bills yet — one is generated once a purchase order is fully received, or manually from that order's own detail page.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BillListComponent implements OnInit {
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  get shown(): Bill[] {
    return this.branchFilter == null ? this.bills : this.bills.filter((b) => effectiveBranchId(b.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  bills: Bill[] = [];
  error: string | null = null;

  constructor(private billApi: BillApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.billApi.listAll().subscribe({
      next: (b) => (this.bills = b),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bills'),
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
