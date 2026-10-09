import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockTransferApiService } from '../core/api/stock-transfer-api.service';
import { StockTransfer } from '../core/models';
import { AuthService } from '../core/auth.service';

/**
 * Admin > Inventory > Stock Transfers — Fourier: "multi-location/multi-warehouse tracking ...
 * Stock Transfers", then later: "User can make stock transfer to any location." Same
 * maker-checker shape as StockAdjustmentListComponent: a transfer is logged PENDING here, and the
 * stock only actually moves once an admin (or anyone separately granted
 * PERM_APPROVE_STOCK_TRANSFER) confirms it. From/To each show "Warehouse / Location" so a
 * same-warehouse transfer (two locations under one warehouse) reads just as clearly as one
 * between two different warehouses.
 */
@Component({
  selector: 'app-stock-transfer-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Stock transfers</h3>
        <a class="btn btn-primary" routerLink="/admin/stock-transfers/new">+ New transfer</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Date</th>
            <th>Product</th>
            <th class="text-end">Qty</th>
            <th>From</th>
            <th>To</th>
            <th>Note</th>
            <th>Status</th>
            <th *ngIf="canApprove"></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of transfers">
            <td>{{ t.createdAt | date: 'medium' }}</td>
            <td>{{ t.productName }}</td>
            <td class="text-end">{{ t.quantity }}</td>
            <td>{{ t.fromWarehouseName }} / {{ t.fromLocationName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="t.interBranch">{{ t.fromBranchName }}</div></td>
            <td>{{ t.toWarehouseName }} / {{ t.toLocationName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="t.interBranch">{{ t.toBranchName }}</div> <span class="badge badge-warning" *ngIf="t.interBranch">Inter-branch</span></td>
            <td>{{ t.note }}</td>
            <td>
              <span
                [style.color]="
                  t.status === 'CONFIRMED' || t.status === 'RECEIVED' ? 'var(--success-text)' : t.status === 'REJECTED' ? 'var(--danger-text)' : t.status === 'IN_TRANSIT' ? 'var(--warning-text, #b45309)' : 'inherit'
                "
                >{{ t.status === 'IN_TRANSIT' ? 'IN TRANSIT' : t.status }}</span
              >
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="t.status === 'RECEIVED'">
                {{ t.receivedQuantity }} of {{ t.quantity }} received<span *ngIf="t.receivedQuantity !== null && t.receivedQuantity !== undefined && t.receivedQuantity < t.quantity"> — {{ t.quantity - t.receivedQuantity! }} short</span>
                <span *ngIf="t.receiveNote"> ({{ t.receiveNote }})</span>
              </div>
            </td>
            <td *ngIf="canApprove" class="flex gap-2">
              <ng-container *ngIf="t.status === 'PENDING'">
                <button class="btn btn-sm btn-primary" [disabled]="busyId === t.id" (click)="confirm(t)">{{ t.interBranch ? 'Dispatch' : 'Confirm' }}</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="busyId === t.id" (click)="reject(t)">Reject</button>
              </ng-container>
              <ng-container *ngIf="t.status === 'IN_TRANSIT'">
                <ng-container *ngIf="receivingId !== t.id; else receiveForm">
                  <button class="btn btn-sm btn-primary" [disabled]="busyId === t.id" (click)="startReceive(t)">Receive</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busyId === t.id" (click)="reject(t)">Return to sender</button>
                </ng-container>
                <ng-template #receiveForm>
                  <input class="form-control form-control-sm" style="width:80px;" type="number" min="0" [max]="t.quantity" [(ngModel)]="receivedQty" name="rq{{ t.id }}" title="Quantity that arrived" />
                  <input class="form-control form-control-sm" style="width:140px;" placeholder="Note (e.g. damaged)" [(ngModel)]="receiveNote" name="rn{{ t.id }}" />
                  <button class="btn btn-sm btn-primary" [disabled]="busyId === t.id" (click)="receive(t)">Save</button>
                  <button class="btn btn-sm btn-link" (click)="receivingId = null">Cancel</button>
                </ng-template>
              </ng-container>
            </td>
          </tr>
          <tr *ngIf="transfers.length === 0">
            <td [attr.colspan]="canApprove ? 8 : 7" class="text-muted">No stock transfers yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class StockTransferListComponent implements OnInit {
  transfers: StockTransfer[] = [];
  error: string | null = null;
  busyId: number | null = null;
  receivingId: number | null = null;
  receivedQty = 0;
  receiveNote = '';

  constructor(private stockTransferApi: StockTransferApiService, private auth: AuthService) {}

  /** ADMIN or anyone granted APPROVE_STOCK_TRANSFER. */
  get canApprove(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN' || this.auth.hasPermission('APPROVE_STOCK_TRANSFER');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.stockTransferApi.listAll().subscribe({
      next: (t) => (this.transfers = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load stock transfers'),
    });
  }

  confirm(t: StockTransfer): void {
    const action = t.interBranch ? 'Dispatch' : 'Confirm';
    if (!confirm(`${action} this transfer of ${t.quantity} x ${t.productName} from "${t.fromWarehouseName} / ${t.fromLocationName}" to "${t.toWarehouseName} / ${t.toLocationName}"? ${t.interBranch ? 'The stock leaves the source now and stays in transit until the other branch receives it.' : 'This will move the stock.'}`)) return;
    this.error = null;
    this.busyId = t.id;
    this.stockTransferApi.confirm(t.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to confirm transfer';
      },
    });
  }

  startReceive(t: StockTransfer): void {
    this.receivingId = t.id;
    this.receivedQty = t.quantity;
    this.receiveNote = '';
  }

  receive(t: StockTransfer): void {
    const qty = Number(this.receivedQty);
    if (!(qty >= 0) || qty > t.quantity) {
      this.error = `Enter how many arrived (0 to ${t.quantity})`;
      return;
    }
    if (qty < t.quantity && !confirm(`${t.quantity - qty} unit(s) will be written off as lost in transit. Continue?`)) return;
    this.error = null;
    this.busyId = t.id;
    this.stockTransferApi.receive(t.id, qty, this.receiveNote.trim() || null).subscribe({
      next: () => {
        this.busyId = null;
        this.receivingId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to receive transfer';
      },
    });
  }

  reject(t: StockTransfer): void {
    const msg = t.status === 'IN_TRANSIT'
      ? `Return this in-transit transfer of ${t.quantity} x ${t.productName} to "${t.fromWarehouseName} / ${t.fromLocationName}"?`
      : `Reject this logged transfer of ${t.quantity} x ${t.productName}? No stock will be moved.`;
    if (!confirm(msg)) return;
    this.error = null;
    this.busyId = t.id;
    this.stockTransferApi.reject(t.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? 'Failed to reject transfer';
      },
    });
  }
}
