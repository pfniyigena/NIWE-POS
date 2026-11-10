import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OrderApiService } from '../core/api/order-api.service';
import { Order } from '../core/models';

/**
 * One cashier session's full order history — every status (open tabs, completed sales, voided
 * carts), not just what's currently open at the terminal. Reached from the "Sessions" screen
 * (Change: "he need to see orders on his session"). Ownership is enforced server-side (see
 * OrderService.listAllForCashierSession): a cashier only ever gets here for their own session
 * (nothing links to anyone else's from the "Sessions" screen), and an admin can open any of them.
 */
@Component({
  selector: 'app-cashier-session-orders',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Orders — session #{p0}' | translate: { p0: (cashierSessionId) } }}</h3>
        <a routerLink="/sessions">&larr; Back to sessions</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Order' | translate }}</th>
            <th>{{ 'Register' | translate }}</th>
            <th>{{ 'Cashier' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th>{{ 'Invoice' | translate }}</th>
            <th>{{ 'Created' | translate }}</th>
            <th>{{ 'Completed' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders">
            <td>#{{ o.id }}</td>
            <td>{{ o.registerName }}</td>
            <td>{{ o.cashierName }}</td>
            <td>
              <span
                class="badge"
                [class.badge-success]="o.status === 'COMPLETED'"
                [class.badge-secondary]="o.status !== 'COMPLETED'"
                >{{ o.status }}</span
              >
            </td>
            <td>{{ o.invoiceNumber ?? '—' }}</td>
            <td>{{ o.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ o.completedAt ? (o.completedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td>
              <a *ngIf="o.status === 'COMPLETED'" [routerLink]="['/receipt', o.id]">{{ 'Receipt' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="9" class="text-muted">{{ 'No orders on this session yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CashierSessionOrdersComponent implements OnInit {
  cashierSessionId!: number;
  orders: Order[] = [];
  error: string | null = null;

  constructor(private route: ActivatedRoute, private orderApi: OrderApiService) {}

  ngOnInit(): void {
    this.cashierSessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.orderApi.listAllForCashierSession(this.cashierSessionId).subscribe({
      next: (o) => (this.orders = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load orders'),
    });
  }
}
