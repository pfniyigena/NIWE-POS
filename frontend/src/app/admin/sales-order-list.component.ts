import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesOrderApiService } from '../core/api/sales-order-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';
import { Branch, SalesOrder } from '../core/models';

/** Admin > Sales > Sale Orders — Task: "Add Sale order... So that we can have POS & Sales." */
@Component({
  selector: 'app-sales-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Sale orders</h3>
        <div class="flex gap-2 align-center">
          <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchFilter" title="Branch">
            <option [ngValue]="null">All branches</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <a class="btn btn-primary" routerLink="/admin/sales-orders/new">+ New sale order</a>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Order #</th>
            <th>Customer</th>
            <th>Date</th>
            <th>Status</th>
            <th *ngIf="branches.length > 1">Branch</th>
            <th class="text-end">Lines</th>
            <th class="text-end">Total</th>
            <th class="text-end">Outstanding</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let so of shown">
            <td>{{ so.orderNumber }}</td>
            <td>{{ so.customerName }}</td>
            <td>{{ so.orderDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(so.status)">{{ statusLabel(so.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(so.branchId) }}</td>
            <td class="text-end">{{ so.lines.length }}</td>
            <td class="text-end">{{ so.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ so.amountOutstanding | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/sales-orders', so.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="shown.length === 0">
            <td colspan="9" class="text-muted">No sale orders yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SalesOrderListComponent implements OnInit {
  orders: SalesOrder[] = [];
  error: string | null = null;
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  constructor(private salesOrderApi: SalesOrderApiService, private branchApi: BranchApiService) {}

  get shown(): SalesOrder[] {
    return this.branchFilter == null ? this.orders : this.orders.filter((o) => effectiveBranchId(o.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.salesOrderApi.listAll().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load sale orders'),
    });
  }

  statusLabel(status: string): string {
    return status.replace('_', ' ');
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'CONFIRMED':
        return 'badge-secondary';
      case 'PARTIALLY_FULFILLED':
        return 'badge-warning';
      case 'FULFILLED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
