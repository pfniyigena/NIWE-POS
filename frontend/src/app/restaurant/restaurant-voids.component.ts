import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { RestaurantVoidEntry } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/** Admin > Restaurant > Voids and comps: every item voided or given free, every cancelled order and every receipt copy, with who did it and why. */
@Component({
  selector: 'app-restaurant-voids',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Voids and comps' | translate }}</h3>
      </div>
      <app-list-filter exportPath="/restaurant/voids/export" [placeholder]="'Search item, reason, table or person' | translate" [statuses]="kinds" statusAll="All kinds" (filterChange)="list.apply($event)"></app-list-filter>
      <table class="void-table">
        <thead>
          <tr>
            <th>{{ 'When' | translate }}</th>
            <th>{{ 'Kind' | translate }}</th>
            <th>{{ 'Table / customer' | translate }}</th>
            <th>{{ 'Item' | translate }}</th>
            <th class="text-end">{{ 'Qty' | translate }}</th>
            <th class="text-end">{{ 'Value' | translate }}</th>
            <th>{{ 'Reason' | translate }}</th>
            <th>{{ 'By' | translate }}</th>
            <th>{{ 'Approved by' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let v of list.rows" class="void-row">
            <td>{{ v.createdAt | date: 'short' }}</td>
            <td><span class="void-kind">{{ label(v.type) }}</span></td>
            <td><a *ngIf="v.orderId" [routerLink]="['/admin/restaurant/orders', v.orderId]">{{ v.orderLabel }}</a><span *ngIf="!v.orderId">{{ v.orderLabel }}</span></td>
            <td>{{ v.description }}</td>
            <td class="text-end">{{ v.type === 'REPRINT' ? '' : v.quantity }}</td>
            <td class="text-end">{{ v.amount | number: '1.2-2' }}</td>
            <td>{{ v.reason }}</td>
            <td>{{ v.byUser }}</td>
            <td>{{ v.approvedBy }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('Nothing matches these filters.' | translate) : ('Nothing has been voided, comped or reprinted yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class RestaurantVoidsComponent implements OnInit {
  kinds = [
    { value: 'VOID', label: T('Voided items') },
    { value: 'COMP', label: T('Comps') },
    { value: 'CANCEL', label: T('Cancelled orders') },
    { value: 'REPRINT', label: T('Receipt copies') },
  ];
  list = new ListState<RestaurantVoidEntry>((f, p, sz) => this.orderApi.voidsPage(f, p, sz), 'Failed to load the log');

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.list.load();
  }

  label(type: string): string {
    return this.kinds.find((k) => k.value === type)?.label ?? type;
  }
}
