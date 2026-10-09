import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { RestaurantOrder } from '../core/models';

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
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Restaurant receipts</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
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
          <tr *ngFor="let o of orders">
            <td>{{ o.closedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ o.ticketNumber }}</td>
            <td>{{ o.orderType }}</td>
            <td>{{ o.displayLabel }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td>
              <span *ngFor="let p of o.payments; let last = last">{{ p.method }}{{ !last ? ' + ' : '' }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/restaurant/receipts', o.id]">View / Reprint</a></td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="7" class="text-muted">No checked-out orders yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RestaurantReceiptListComponent implements OnInit {
  orders: RestaurantOrder[] = [];
  error: string | null = null;

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.orderApi.listClosed().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load receipts'),
    });
  }
}
