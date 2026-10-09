import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseOrderApiService } from '../core/api/purchase-order-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';
import { Branch, PurchaseOrder } from '../core/models';

/** Admin > Inventory > Purchase Orders — Task: "Inventory depth ... purchase orders ... a stock-receiving flow." */
@Component({
  selector: 'app-purchase-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Purchase orders</h3>
        <div class="flex gap-2 align-center">
          <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchFilter" title="Branch">
            <option [ngValue]="null">All branches</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <a class="btn btn-primary" routerLink="/admin/purchase-orders/new">+ New purchase order</a>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Order #</th>
            <th>Supplier</th>
            <th>Date</th>
            <th>Status</th>
            <th *ngIf="branches.length > 1">Branch</th>
            <th class="text-end">Lines</th>
            <th class="text-end">Outstanding</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let po of shown">
            <td>{{ po.orderNumber }}</td>
            <td>{{ po.supplierName }}</td>
            <td>{{ po.orderDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(po.status)">{{ statusLabel(po.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(po.branchId) }}</td>
            <td class="text-end">{{ po.lines.length }}</td>
            <td class="text-end">{{ po.amountOutstanding | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/purchase-orders', po.id]">View</a>
              <!-- Task 5: "Add Record payment on purchase order view or list (if not paid)". -->
              <a class="btn btn-sm btn-primary" *ngIf="po.amountOutstanding > 0" [routerLink]="['/admin/suppliers', po.supplierId, 'record-payment']">Record payment</a>
              <!-- Task 3: "Print out purchase order" — same "direct Print link per row" convention as Returns. -->
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/purchase-orders', po.id, 'print']" target="_blank">Print</a>
            </td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="7" class="text-muted">No purchase orders yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PurchaseOrderListComponent implements OnInit {
  orders: PurchaseOrder[] = [];
  error: string | null = null;
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  constructor(private purchaseOrderApi: PurchaseOrderApiService, private branchApi: BranchApiService) {}

  get shown(): PurchaseOrder[] {
    return this.branchFilter == null ? this.orders : this.orders.filter((o) => effectiveBranchId(o.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.purchaseOrderApi.listAll().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase orders'),
    });
  }

  statusLabel(status: string): string {
    return status.replace('_', ' ');
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'SENT':
        return 'badge-secondary';
      case 'PARTIALLY_RECEIVED':
        return 'badge-warning';
      case 'RECEIVED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
