import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { RestaurantOrder } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Restaurant Orders — every currently open order (dine-in, takeaway, delivery) in one list.
 * Fourier pointed out there was no screen for this: the floor plan only shows dine-in tables (as
 * a grid, not an order list), the Deliveries board only shows DELIVERY orders, and a takeaway
 * order had no list screen at all — once you navigated away from its own order-taking page there
 * was no way back to it short of knowing its id. Reuses the existing `GET /api/restaurant/orders/
 * open` endpoint (already used internally for the "merge into" picker) — no backend change
 * needed, just surfacing it as its own screen. Open to any logged-in staff, same tier as the rest
 * of the module's day-to-day screens.
 */
@Component({
  selector: 'app-restaurant-orders',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Restaurant orders' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">{{ '🍽️ Floor plan' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/orders/new">{{ '🥡 New takeaway/delivery' | translate }}</a>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Opened' | translate }}</th>
            <th>{{ 'Type' | translate }}</th>
            <th>{{ 'Table / customer' | translate }}</th>
            <th class="text-end">{{ 'Guests' | translate }}</th>
            <th class="text-end">{{ 'Running total' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders">
            <td>{{ o.openedAt | date: 'HH:mm' }}</td>
            <td>{{ o.orderType }}</td>
            <td>{{ o.displayLabel }}</td>
            <td class="text-end">{{ o.orderType === 'DINE_IN' ? o.guestCount : '—' }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/restaurant/orders', o.id]">{{ 'Open' | translate }}</a></td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="6" class="text-muted">{{ 'No open orders right now.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RestaurantOrdersComponent implements OnInit, OnDestroy {
  orders: RestaurantOrder[] = [];
  error: string | null = null;
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.load();
    this.poll = setInterval(() => this.load(), 8000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  load(): void {
    this.orderApi.listOpen().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load orders')),
    });
  }
}
