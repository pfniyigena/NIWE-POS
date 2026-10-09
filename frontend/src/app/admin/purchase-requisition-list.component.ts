import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseRequisitionApiService } from '../core/api/purchase-requisition-api.service';
import { PurchaseRequisition } from '../core/models';

/** Admin > Inventory > Purchase Requisitions — Fourier: "Add ... the purchase-requisition approval gate ... without touching warehousing." */
@Component({
  selector: 'app-purchase-requisition-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Purchase requisitions</h3>
        <a class="btn btn-primary" routerLink="/admin/purchase-requisitions/new">+ New requisition</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Requisition #</th>
            <th>Requested by</th>
            <th>Date</th>
            <th>Status</th>
            <th class="text-end">Lines</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of requisitions">
            <td>{{ r.requisitionNumber }}</td>
            <td>{{ r.requestedByFullName || '—' }}</td>
            <td>{{ r.requestDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(r.status)">{{ statusLabel(r.status) }}</span></td>
            <td class="text-end">{{ r.lines.length }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/purchase-requisitions', r.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="requisitions.length === 0">
            <td colspan="6" class="text-muted">No purchase requisitions yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PurchaseRequisitionListComponent implements OnInit {
  requisitions: PurchaseRequisition[] = [];
  error: string | null = null;

  constructor(private requisitionApi: PurchaseRequisitionApiService) {}

  ngOnInit(): void {
    this.requisitionApi.listAll().subscribe({
      next: (r) => (this.requisitions = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase requisitions'),
    });
  }

  statusLabel(status: string): string {
    return status.replace('_', ' ');
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'PENDING_APPROVAL':
        return 'badge-warning';
      case 'APPROVED':
        return 'badge-success';
      case 'REJECTED':
        return 'badge-danger';
      case 'CONVERTED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
