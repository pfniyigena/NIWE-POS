import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantWastageApiService } from '../core/api/restaurant-wastage-api.service';
import { RestaurantWastage } from '../core/models';
import { AuthService } from '../core/auth.service';

/**
 * Admin > Restaurant > Wastage — Fourier: "Restaurants need a dedicated wastage module." Mirrors
 * StockAdjustmentListComponent's own layout, but open to any logged-in staff (see
 * RestaurantWastageController) and with the extra Status/Cost impact columns
 * ReportService.restaurantWastage also reports. Fourier's later "should be confirmed by admin
 * once confirmed inventory and account will be impacted" is why a PENDING row shows
 * Confirm/Reject buttons (ADMIN-only — a cashier who logs a loss can't also be the one who
 * approves its own accounting impact) and why Cost impact reads "—" until confirmed.
 */
@Component({
  selector: 'app-restaurant-wastage-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Restaurant wastage</h3>
        <a class="btn btn-primary" routerLink="/admin/restaurant/wastage/new">+ Record wastage</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Product</th>
            <th class="text-end">Qty wasted</th>
            <th>Reason</th>
            <th>Note</th>
            <th>Status</th>
            <th class="text-end">Cost impact</th>
            <th *ngIf="isAdmin"></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let w of entries">
            <td>{{ w.createdAt | date: 'medium' }}</td>
            <td>{{ w.productName }}</td>
            <td class="text-end" style="color: var(--danger-text)">-{{ w.quantity }}</td>
            <td>{{ w.reason }}</td>
            <td>{{ w.note }}</td>
            <td>
              <span
                [style.color]="
                  w.status === 'CONFIRMED' ? 'var(--success-text)' : w.status === 'REJECTED' ? 'var(--danger-text)' : 'inherit'
                "
                >{{ w.status }}</span
              >
            </td>
            <td class="text-end">{{ w.costImpact != null ? (w.costImpact | currency) : '—' }}</td>
            <td *ngIf="isAdmin" class="flex gap-2">
              <ng-container *ngIf="w.status === 'PENDING'">
                <button class="btn btn-sm btn-primary" [disabled]="busyId === w.id" (click)="confirm(w)">Confirm</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="busyId === w.id" (click)="reject(w)">Reject</button>
              </ng-container>
            </td>
          </tr>
          <tr *ngIf="entries.length === 0">
            <td [attr.colspan]="isAdmin ? 8 : 7" class="text-muted">No wastage recorded yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RestaurantWastageListComponent implements OnInit {
  entries: RestaurantWastage[] = [];
  error: string | null = null;
  busyId: number | null = null;

  constructor(private wastageApi: RestaurantWastageApiService, private auth: AuthService) {}

  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.wastageApi.listAll().subscribe({
      next: (w) => (this.entries = w),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load wastage records'),
    });
  }

  confirm(w: RestaurantWastage): void {
    if (!confirm(`Confirm ${w.quantity} ${w.productName} wasted? This will decrement stock and post to Accounting.`)) return;
    this.error = null;
    this.busyId = w.id;
    this.wastageApi.confirm(w.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to confirm wastage entry';
      },
    });
  }

  reject(w: RestaurantWastage): void {
    if (!confirm(`Reject the logged wastage of ${w.quantity} ${w.productName}? No stock/accounting impact will be made.`)) return;
    this.error = null;
    this.busyId = w.id;
    this.wastageApi.reject(w.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to reject wastage entry';
      },
    });
  }
}
