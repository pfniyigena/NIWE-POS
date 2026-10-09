import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { DiningTableApiService } from '../core/api/dining-table-api.service';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { DiningTable } from '../core/models';

/**
 * Admin/staff > Restaurant > Tables — the floor-plan, Fourier's own "New dedicated screen" for
 * dine-in order-taking, kept entirely separate from the walk-up POS terminal. Tapping an
 * AVAILABLE table opens a new order (asking guest count first) and jumps straight into it;
 * tapping an OCCUPIED one jumps back into its already-open order.
 */
@Component({
  selector: 'app-floor-plan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Restaurant — floor plan</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/orders">📝 All orders</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/orders/new">🥡 Takeaway/Delivery</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/deliveries">🛵 Deliveries board</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/kitchen">🍳 Kitchen display</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/reservations">📅 Reservations</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/receipts">🧾 Receipts</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/wastage">🗑️ Wastage</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/reports">📊 Reports</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="openingTable">
        <h4 style="font-size:1rem;" class="mb-2">Open order at {{ openingTable.name }}</h4>
        <div class="flex gap-2 align-center">
          <label class="form-label" style="margin:0;">Guests</label>
          <input class="form-control" type="number" min="1" style="width:100px;" [(ngModel)]="guestCount" name="guestCount" />
          <button class="btn btn-primary" (click)="confirmOpen()">Open order</button>
          <button class="btn btn-link" (click)="openingTable = null">Cancel</button>
        </div>
      </div>

      <div class="table-grid">
        <div
          *ngFor="let t of tables"
          class="table-card"
          [class.available]="t.status === 'AVAILABLE'"
          [class.occupied]="t.status === 'OCCUPIED'"
          [class.reserved]="t.status === 'RESERVED'"
          [class.out-of-service]="t.status === 'OUT_OF_SERVICE'"
          (click)="onTableClick(t)"
        >
          <div class="table-name">{{ t.name }}</div>
          <div class="table-section text-muted" *ngIf="t.section">{{ t.section }}</div>
          <div class="table-status">{{ t.status.replace('_', ' ') }}</div>
          <div class="table-capacity text-muted">Seats {{ t.capacity }}</div>
        </div>
        <div *ngIf="tables.length === 0" class="text-muted">
          No tables set up yet — add some in <a routerLink="/admin/restaurant/tables">Admin &gt; Restaurant &gt; Tables</a>.
        </div>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .table-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
        gap: 12px;
      }
      .table-card {
        border-radius: 8px;
        padding: 1rem;
        cursor: pointer;
        border: 1px solid var(--border, #ddd);
        text-align: center;
      }
      .table-card.available {
        background: rgba(47, 190, 114, 0.12);
        border-color: rgba(47, 190, 114, 0.4);
      }
      .table-card.occupied {
        background: rgba(220, 53, 69, 0.1);
        border-color: rgba(220, 53, 69, 0.4);
      }
      .table-card.reserved {
        background: rgba(255, 193, 7, 0.15);
        border-color: rgba(255, 193, 7, 0.5);
      }
      .table-card.out-of-service {
        background: rgba(0, 0, 0, 0.05);
        opacity: 0.6;
        cursor: not-allowed;
      }
      .table-name {
        font-weight: 600;
        font-size: 1.1rem;
      }
      .table-status {
        font-size: 0.8rem;
        margin-top: 0.5rem;
      }
    `,
  ],
})
export class FloorPlanComponent implements OnInit {
  tables: DiningTable[] = [];
  error: string | null = null;

  openingTable: DiningTable | null = null;
  guestCount = 2;

  constructor(
    private tableApi: DiningTableApiService,
    private orderApi: RestaurantOrderApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.tableApi.listAll().subscribe({
      next: (t) => (this.tables = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load tables'),
    });
  }

  onTableClick(t: DiningTable): void {
    if (t.status === 'OUT_OF_SERVICE') return;
    if (t.openOrderId) {
      this.router.navigate(['/admin/restaurant/orders', t.openOrderId]);
      return;
    }
    this.openingTable = t;
    this.guestCount = 2;
  }

  confirmOpen(): void {
    if (!this.openingTable) return;
    this.error = null;
    this.orderApi.openOrder(this.openingTable.id, { guestCount: this.guestCount }).subscribe({
      next: (order) => {
        this.openingTable = null;
        this.router.navigate(['/admin/restaurant/orders', order.id]);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to open order'),
    });
  }
}
