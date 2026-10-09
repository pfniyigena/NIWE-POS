import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ManufacturingOrderApiService } from '../../core/api/manufacturing-order-api.service';
import { ManufacturingOrder } from '../../core/models';

/** Manufacturing > Manufacturing orders (Fourier's own list, item 3). */
@Component({
  selector: 'app-manufacturing-order-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Manufacturing orders</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/orders/new">+ New manufacturing order</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Order #</th>
            <th>Product</th>
            <th class="text-end">Planned</th>
            <th class="text-end">Produced</th>
            <th>Status</th>
            <th>Scheduled</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders">
            <td>{{ o.orderNumber }}</td>
            <td>{{ o.productName }}</td>
            <td class="text-end">{{ o.quantityPlanned }}</td>
            <td class="text-end">{{ o.quantityProduced }}</td>
            <td><span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span></td>
            <td>{{ o.scheduledDate ? (o.scheduledDate | date: 'mediumDate') : '—' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/orders', o.id]">View</a></td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="7" class="text-muted">No manufacturing orders yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ManufacturingOrderListComponent implements OnInit {
  orders: ManufacturingOrder[] = [];
  error: string | null = null;

  constructor(private manufacturingOrderApi: ManufacturingOrderApiService) {}

  ngOnInit(): void {
    this.manufacturingOrderApi.listAll().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load manufacturing orders'),
    });
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
