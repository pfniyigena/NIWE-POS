import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { RestaurantOrder } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

/**
 * Restaurant Receipts — recent checked-out orders (dine-in, takeaway, delivery) with lookup +
 * reprint, the Restaurant module's own counterpart to the POS terminal's Receipts screen (see
 * `receipts/receipt-list.component.ts`). Open to any logged-in user, same reasoning: a server
 * reprinting a bill for a guest doesn't need admin rights. Cancelled orders don't appear here —
 * nothing was ever sold on one.
 */
@Component({
  selector: 'app-restaurant-receipt-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Restaurant receipts</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
      </div>
      <app-list-filter exportPath="/restaurant/orders/closed/export" [exportExtra]="extra" [extraActive]="type !== ''" placeholder="Search ticket, table, customer, phone, server" [statuses]="statuses" statusAll="Paid receipts" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select type-filter" style="width:auto;" [(ngModel)]="type" (ngModelChange)="extraChanged()" name="type" title="Order type">
          <option value="">All order types</option>
          <option value="DINE_IN">Dine-in</option>
          <option value="TAKEAWAY">Takeaway</option>
          <option value="DELIVERY">Delivery</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;">Dates filter by the day the bill was closed.</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Closed</th>
            <th>Ticket</th>
            <th>Type</th>
            <th>Table / customer</th>
            <th class="text-end">Total</th>
            <th>Payment</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of list.rows">
            <td>{{ o.closedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ o.ticketNumber }}</td>
            <td>{{ o.orderType }}<span class="badge badge-danger cancelled-badge" style="margin-left:0.3rem;" *ngIf="o.status === 'CANCELLED'">Cancelled</span></td>
            <td>{{ o.displayLabel }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td>
              <span *ngFor="let p of o.payments; let last = last">{{ p.method }}{{ !last ? ' + ' : '' }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/restaurant/receipts', o.id]">View / Reprint</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || type ? 'No receipts match these filters.' : 'No checked-out orders yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class RestaurantReceiptListComponent implements OnInit {
  statuses = [{ value: 'CANCELLED', label: 'Cancelled orders' }, { value: 'ALL', label: 'Paid and cancelled' }];
  type = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<RestaurantOrder>((f, p, sz) => this.orderApi.closedPage(f, p, sz, { type: this.type }), 'Failed to load receipts');

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.list.load();
  }

  extraChanged(): void {
    this.extra = { type: this.type };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.type = '';
    this.extra = {};
  }
}
