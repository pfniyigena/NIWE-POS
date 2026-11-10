import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { SalesOrderApiService } from '../core/api/sales-order-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';
import { Branch, SalesOrder } from '../core/models';

/** Admin > Sales > Sale Orders — Task: "Add Sale order... So that we can have POS & Sales." */
@Component({
  selector: 'app-sales-order-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Sale orders' | translate }}</h3>
        <div class="flex gap-2 align-center">
          <a class="btn btn-primary" routerLink="/admin/sales-orders/new">{{ '+ New sale order' | translate }}</a>
        </div>
      </div>
      <app-list-filter exportKind="sales-orders" [placeholder]="'Search order no., customer…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Order #' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th class="text-end">{{ 'Lines' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th class="text-end">{{ 'Outstanding' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let so of list.rows">
            <td>{{ so.orderNumber }}</td>
            <td>{{ so.customerName }}</td>
            <td>{{ so.orderDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(so.status)">{{ statusLabel(so.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(so.branchId) }}</td>
            <td class="text-end">{{ so.lines.length }}</td>
            <td class="text-end">{{ so.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ so.amountOutstanding | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/sales-orders', so.id]">{{ 'View' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="9" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No sale orders yet.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class SalesOrderListComponent implements OnInit {
  error: string | null = null;
  /** Branch column + choice — only shown with more than one active branch. */
  branches: Branch[] = [];

  list = new ListState<SalesOrder>((f, p, sz) => this.salesOrderApi.page(f, p, sz), 'Failed to load sale orders');
  statuses = [{ value: 'DRAFT', label: 'Draft' }, { value: 'CONFIRMED', label: 'Confirmed' }, { value: 'PARTIALLY_FULFILLED', label: 'Partially fulfilled' }, { value: 'FULFILLED', label: 'Fulfilled' }, { value: 'CANCELLED', label: 'Cancelled' }];
  constructor(private salesOrderApi: SalesOrderApiService, private branchApi: BranchApiService) {}


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
