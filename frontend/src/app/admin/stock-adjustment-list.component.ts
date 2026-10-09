import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockAdjustmentApiService } from '../core/api/stock-adjustment-api.service';
import { StockAdjustment } from '../core/models';
import { AuthService } from '../core/auth.service';

/**
 * Admin > Inventory > Stock Adjustments — Task: "Inventory depth ... stock adjustments/write-offs
 * with a reason code." Admin-only, same as before (see StockAdjustmentController's own javadoc).
 * Fourier: "Build a full permissions system" — this still has a Status column and Confirm/Reject
 * buttons on a PENDING row, a maker-checker split kept for when an admin wants someone else to
 * sign off a correction: canApprove below reads true for the admin viewing this screen, or for
 * anyone separately granted PERM_APPROVE_STOCK_ADJUSTMENT (not reachable via this admin-only
 * screen today, but still honored if that permission is used some other way later).
 */
@Component({
  selector: 'app-stock-adjustment-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Stock adjustments</h3>
        <a class="btn btn-primary" routerLink="/admin/stock-adjustments/new">+ New adjustment</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Product</th>
            <th>Location</th>
            <th class="text-end">Change</th>
            <th class="text-end">Stock after</th>
            <th>Reason</th>
            <th>Note</th>
            <th>Status</th>
            <th *ngIf="canApprove"></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of adjustments">
            <td>{{ a.createdAt | date: 'medium' }}</td>
            <td>{{ a.productName }}</td>
            <td>{{ a.locationName ? (a.warehouseName ? a.warehouseName + ' / ' : '') + a.locationName : '—' }}</td>
            <td class="text-end" [style.color]="a.quantityDelta < 0 ? 'var(--danger-text)' : 'var(--success-text)'">
              {{ a.quantityDelta > 0 ? '+' : '' }}{{ a.quantityDelta }}
            </td>
            <td class="text-end">{{ a.stockAfter }}</td>
            <td>{{ a.reason }}</td>
            <td>{{ a.note }}</td>
            <td>
              <span
                [style.color]="
                  a.status === 'CONFIRMED' ? 'var(--success-text)' : a.status === 'REJECTED' ? 'var(--danger-text)' : 'inherit'
                "
                >{{ a.status }}</span
              >
            </td>
            <td *ngIf="canApprove" class="flex gap-2">
              <ng-container *ngIf="a.status === 'PENDING'">
                <button class="btn btn-sm btn-primary" [disabled]="busyId === a.id" (click)="confirm(a)">Confirm</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="busyId === a.id" (click)="reject(a)">Reject</button>
              </ng-container>
            </td>
          </tr>
          <tr *ngIf="adjustments.length === 0">
            <td [attr.colspan]="canApprove ? 9 : 8" class="text-muted">No stock adjustments yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class StockAdjustmentListComponent implements OnInit {
  adjustments: StockAdjustment[] = [];
  error: string | null = null;
  busyId: number | null = null;

  constructor(private stockAdjustmentApi: StockAdjustmentApiService, private auth: AuthService) {}

  /** Fourier: "Build a full permissions system" — ADMIN or anyone granted APPROVE_STOCK_ADJUSTMENT. */
  get canApprove(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN' || this.auth.hasPermission('APPROVE_STOCK_ADJUSTMENT');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.stockAdjustmentApi.listAll().subscribe({
      next: (a) => (this.adjustments = a),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load stock adjustments'),
    });
  }

  confirm(a: StockAdjustment): void {
    if (!confirm(`Confirm this adjustment to ${a.productName}? This will apply the stock change and post to Accounting.`)) return;
    this.error = null;
    this.busyId = a.id;
    this.stockAdjustmentApi.confirm(a.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to confirm adjustment';
      },
    });
  }

  reject(a: StockAdjustment): void {
    if (!confirm(`Reject this logged adjustment to ${a.productName}? No stock/accounting impact will be made.`)) return;
    this.error = null;
    this.busyId = a.id;
    this.stockAdjustmentApi.reject(a.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to reject adjustment';
      },
    });
  }
}
