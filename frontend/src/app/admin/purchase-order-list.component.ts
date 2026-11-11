import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { PurchaseOrderApiService } from '../core/api/purchase-order-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';
import { Branch, PurchaseOrder } from '../core/models';

import { T } from '../core/i18n/t';
/** Admin > Inventory > Purchase Orders — Task: "Inventory depth ... purchase orders ... a stock-receiving flow." */
@Component({
  selector: 'app-purchase-order-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Purchase orders' | translate }}</h3>
        <div class="flex gap-2 align-center">
          <a class="btn btn-primary" routerLink="/admin/purchase-orders/new">{{ '+ New purchase order' | translate }}</a>
        </div>
      </div>
      <app-list-filter exportKind="purchase-orders" [placeholder]="'Search order no., supplier, bill…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Order #' | translate }}</th>
            <th>{{ 'Supplier' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th class="text-end">{{ 'Lines' | translate }}</th>
            <th class="text-end">{{ 'Outstanding' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let po of list.rows">
            <td>{{ po.orderNumber }}</td>
            <td>{{ po.supplierName }}</td>
            <td>{{ po.orderDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(po.status)">{{ statusLabel(po.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(po.branchId) }}</td>
            <td class="text-end">{{ po.lines.length }}</td>
            <td class="text-end">{{ po.amountOutstanding | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/purchase-orders', po.id]">{{ 'View' | translate }}</a>
              <!-- Task 5: "Add Record payment on purchase order view or list (if not paid)". -->
              <a class="btn btn-sm btn-primary" *ngIf="po.amountOutstanding > 0" [routerLink]="['/admin/suppliers', po.supplierId, 'record-payment']">{{ 'Record payment' | translate }}</a>
              <!-- Task 3: "Print out purchase order" — same "direct Print link per row" convention as Returns. -->
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/purchase-orders', po.id, 'print']" target="_blank">{{ 'Print' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="7" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No purchase orders yet.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PurchaseOrderListComponent implements OnInit {
  error: string | null = null;
  /** Branch column + choice — only shown with more than one active branch. */
  branches: Branch[] = [];

  list = new ListState<PurchaseOrder>((f, p, sz) => this.purchaseOrderApi.page(f, p, sz), 'Failed to load purchase orders');
  statuses = [{ value: 'DRAFT', label: T('Draft') }, { value: 'SENT', label: T('Sent') }, { value: 'PARTIALLY_RECEIVED', label: T('Partially received') }, { value: 'RECEIVED', label: T('Received') }, { value: 'CANCELLED', label: T('Cancelled') }];
  constructor(private purchaseOrderApi: PurchaseOrderApiService, private branchApi: BranchApiService) {}


  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.list.load();
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
