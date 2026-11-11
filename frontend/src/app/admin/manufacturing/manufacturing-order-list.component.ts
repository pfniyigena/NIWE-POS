import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { FormsModule } from '@angular/forms';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { ManufacturingOrderApiService } from '../../core/api/manufacturing-order-api.service';
import { ManufacturingOrder } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Manufacturing > Manufacturing orders (Fourier's own list, item 3). */
@Component({
  selector: 'app-manufacturing-order-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Manufacturing orders' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/orders/new">{{ '+ New manufacturing order' | translate }}</a>
      </div>
      <app-list-filter exportPath="/manufacturing/orders/export" [statuses]="statuses" statusAll="All statuses" [placeholder]="'Search order no., product, recipe, note' | translate" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Order #' | translate }}</th>
            <th>{{ 'Product' | translate }}</th>
            <th class="text-end">{{ 'Planned' | translate }}</th>
            <th class="text-end">{{ 'Produced' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th>{{ 'Scheduled' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of list.rows" class="mo-row">
            <td>{{ o.orderNumber }}</td>
            <td>{{ o.productName }}</td>
            <td class="text-end">{{ o.quantityPlanned }}</td>
            <td class="text-end">{{ o.quantityProduced }}</td>
            <td><span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span></td>
            <td>{{ o.scheduledDate ? (o.scheduledDate | date: 'mediumDate') : '—' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/orders', o.id]">{{ 'View' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No manufacturing orders match these filters.' | translate) : ('No manufacturing orders yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ManufacturingOrderListComponent implements OnInit {
  statuses = [
    { value: 'DRAFT', label: T('Draft') },
    { value: 'CONFIRMED', label: T('Confirmed') },
    { value: 'PARTIALLY_PRODUCED', label: T('Partly produced') },
    { value: 'PRODUCED', label: T('Produced') },
    { value: 'CANCELLED', label: T('Cancelled') },
  ];
  list = new ListState<ManufacturingOrder>((f, p, sz) => this.manufacturingOrderApi.page(f, p, sz), 'Failed to load manufacturing orders');

  constructor(private manufacturingOrderApi: ManufacturingOrderApiService) {}

  ngOnInit(): void {
    this.list.load();
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'CONFIRMED':
        return 'badge-secondary';
      case 'PARTIALLY_PRODUCED':
        return 'badge-warning';
      case 'PRODUCED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
