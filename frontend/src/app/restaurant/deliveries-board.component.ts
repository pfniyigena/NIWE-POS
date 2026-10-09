import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService, StaffOption } from '../core/api/restaurant-order-api.service';
import { RestaurantOrder } from '../core/models';

/**
 * Admin/staff > Restaurant > Deliveries board (Fourier: "Delivery & takeaway") — every still-open
 * DELIVERY order, so front-of-house can see at a glance who's still awaiting a driver and who's
 * already out. Assigning a driver here mirrors Kitchen Display's own "advance the status, no
 * enforced sequence" convention — staff can also bump the status directly if a driver leaves
 * without being formally assigned first.
 */
@Component({
  selector: 'app-deliveries-board',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Deliveries board</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/orders/new">+ New delivery</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
        </div>
      </div>

      <div class="delivery-grid">
        <div class="card delivery-card" *ngFor="let o of orders" [class.out]="o.deliveryStatus === 'OUT_FOR_DELIVERY'" [class.delivered]="o.deliveryStatus === 'DELIVERED'">
          <div class="flex justify-between align-center">
            <strong>{{ o.customerName }}</strong>
            <span class="text-muted">{{ (o.deliveryStatus ?? '').replace('_', ' ') }}</span>
          </div>
          <div class="text-muted" *ngIf="o.customerPhone">📞 {{ o.customerPhone }}</div>
          <div class="text-muted" *ngIf="o.customerAddress">📍 {{ o.customerAddress }}</div>
          <div class="mt-2">{{ o.total | number: '1.2-2' }} — {{ o.lines.length }} item{{ o.lines.length === 1 ? '' : 's' }}</div>

          <div class="mt-2">
            <label class="form-label" style="margin:0;">Driver</label>
            <select class="form-control form-control-sm" [ngModel]="o.driverId" (ngModelChange)="onAssignDriver(o, $event)">
              <option [ngValue]="null">— Unassigned —</option>
              <option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option>
            </select>
          </div>

          <div class="flex gap-2 mt-2">
            <button class="btn btn-sm btn-outline-secondary" *ngIf="o.deliveryStatus === 'AWAITING_DRIVER'" (click)="advance(o, 'OUT_FOR_DELIVERY')">Out for delivery</button>
            <button class="btn btn-sm btn-primary" *ngIf="o.deliveryStatus === 'OUT_FOR_DELIVERY'" (click)="advance(o, 'DELIVERED')">Mark delivered</button>
            <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/restaurant/orders', o.id]">Open order</a>
          </div>
        </div>
        <p class="text-muted" *ngIf="orders.length === 0">No deliveries in progress.</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .delivery-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
        gap: 12px;
      }
      .delivery-card {
        padding: 1rem;
      }
      .delivery-card.out {
        background: rgba(255, 193, 7, 0.12);
        border-color: rgba(255, 193, 7, 0.4);
      }
      .delivery-card.delivered {
        background: rgba(47, 190, 114, 0.12);
        border-color: rgba(47, 190, 114, 0.4);
      }
    `,
  ],
})
export class DeliveriesBoardComponent implements OnInit, OnDestroy {
  orders: RestaurantOrder[] = [];
  staff: StaffOption[] = [];
  error: string | null = null;
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.load();
    this.orderApi.listActiveStaff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    this.poll = setInterval(() => this.load(), 8000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  load(): void {
    this.orderApi.listDeliveries().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load deliveries'),
    });
  }

  onAssignDriver(order: RestaurantOrder, driverId: number | null): void {
    if (driverId == null) return;
    this.error = null;
    this.orderApi.assignDriver(order.id, driverId).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to assign driver'),
    });
  }

  advance(order: RestaurantOrder, status: string): void {
    this.error = null;
    this.orderApi.setDeliveryStatus(order.id, status).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update delivery status'),
    });
  }
}
