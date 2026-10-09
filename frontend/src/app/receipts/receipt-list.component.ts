import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OrderApiService } from '../core/api/order-api.service';
import { Order } from '../core/models';

/**
 * Recent completed sales — lookup + reprint. Open to any logged-in user (a cashier reprinting a
 * receipt for a customer doesn't need admin rights), unlike the /admin/* management screens.
 */
@Component({
  selector: 'app-receipt-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <h3 class="mb-3">Receipts</h3>
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Invoice</th>
            <th>Customer</th>
            <th class="text-end">Total</th>
            <th>Payment</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders">
            <td>
              {{ o.completedAt | date: 'yyyy-MM-dd HH:mm' }}
              <!-- Task: "local queuing of sales that sync once connectivity returns" — this sale was rung offline and replayed once back online. -->
              <span *ngIf="o.offlineQueuedAt" class="text-muted" style="font-size:0.8rem;" [title]="'Rung offline at ' + (o.offlineQueuedAt | date: 'short')">📴</span>
              <span *ngIf="o.syncNote" class="text-danger" style="font-size:0.8rem;" [title]="o.syncNote">⚠</span>
            </td>
            <td>{{ o.invoiceNumber }}</td>
            <td>{{ o.customerName }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td>
              <span *ngFor="let p of o.payments; let last = last">{{ p.method }}{{ !last ? ' + ' : '' }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/receipt', o.id]">View / Reprint</a></td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="6" class="text-muted">No completed sales yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ReceiptListComponent implements OnInit {
  orders: Order[] = [];
  error: string | null = null;

  constructor(private orderApi: OrderApiService) {}

  ngOnInit(): void {
    this.orderApi.listCompleted().subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load receipts'),
    });
  }
}
