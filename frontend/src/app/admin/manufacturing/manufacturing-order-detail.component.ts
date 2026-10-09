import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ManufacturingOrderApiService } from '../../core/api/manufacturing-order-api.service';
import { ManufacturingOrder } from '../../core/models';

/**
 * Manufacturing > Manufacturing orders > detail — where a DRAFT is confirmed or cancelled, and
 * where a CONFIRMED/PARTIALLY_PRODUCED order is actually produced against. Mirrors
 * PurchaseOrderDetailComponent's "process against this order" + history-table layout. Each
 * "Record production" click can cover any quantity up to what's still remaining, so a partial run
 * today and the rest later — same partial-run shape as a purchase order's receipt or a sales
 * order's fulfillment.
 */
@Component({
  selector: 'app-manufacturing-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="mo as o">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ o.orderNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-primary" *ngIf="o.status === 'DRAFT'" (click)="confirm()">Confirm</button>
            <button class="btn btn-outline-danger" *ngIf="o.status === 'DRAFT' || o.status === 'CONFIRMED'" (click)="cancel()">Cancel order</button>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Product</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.productName }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Bill of materials</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.bomCode || '—' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.locationName">
            <div class="stat-label">Location</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.warehouseName ? o.warehouseName + ' / ' : '' }}{{ o.locationName }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Routing</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.routingName || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Planned</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.quantityPlanned }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Produced</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.quantityProduced }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Scheduled</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.scheduledDate ? (o.scheduledDate | date: 'mediumDate') : '—' }}</div>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Total material cost</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalMaterialCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total overhead cost</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalOverheadCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total cost</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Unit cost</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.unitCost != null ? (o.unitCost | number: '1.2-2') : '—' }}</div>
          </div>
        </div>

        <p class="text-muted mb-3" *ngIf="o.note">{{ o.note }}</p>

        <ng-container *ngIf="o.overheads.length > 0">
          <h4 class="mb-2">Indirect costs applied</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Name</th>
                <th>Method</th>
                <th class="text-end">Rate</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let oh of o.overheads">
                <td>{{ oh.name }}</td>
                <td>{{ methodLabel(oh.method) }}</td>
                <td class="text-end">{{ oh.rate | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <ng-container *ngIf="o.status === 'CONFIRMED' || o.status === 'PARTIALLY_PRODUCED'">
          <h4 class="mb-2">Record production</h4>
          <div class="alert alert-danger" *ngIf="produceError">{{ produceError }}</div>
          <div class="flex gap-2 align-center mb-3">
            <div>
              <label class="form-label">Quantity to produce now (remaining: {{ remaining() }})</label>
              <input type="number" class="form-control" style="width:140px;" min="1" [max]="remaining()" [(ngModel)]="produceQuantity" name="produceQuantity" />
            </div>
            <button class="btn btn-primary" style="margin-top:1.5rem;" (click)="recordProduction()">Record production</button>
          </div>
        </ng-container>

        <ng-container *ngIf="o.runs.length > 0">
          <h4 class="mb-2">Production run history</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Date</th>
                <th class="text-end">Quantity produced</th>
                <th class="text-end">Material cost</th>
                <th class="text-end">Overhead cost</th>
                <th class="text-end">Total cost</th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let run of o.runs">
                <tr>
                  <td>{{ run.producedAt | date: 'medium' }}</td>
                  <td class="text-end">{{ run.quantityProduced }}</td>
                  <td class="text-end">{{ run.materialCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.overheadCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.totalCost | number: '1.2-2' }}</td>
                </tr>
                <tr>
                  <td colspan="5" style="padding-left:2rem;">
                    <span class="text-muted" style="font-size:0.85rem;">Consumed: </span>
                    <span *ngFor="let c of run.consumptions; let last = last" style="font-size:0.85rem;">
                      {{ c.componentName }} × {{ c.quantityConsumed }}{{ !last ? ', ' : '' }}
                    </span>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class ManufacturingOrderDetailComponent implements OnInit {
  mo: ManufacturingOrder | null = null;
  error: string | null = null;
  produceError: string | null = null;
  produceQuantity = 1;

  constructor(
    private manufacturingOrderApi: ManufacturingOrderApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.manufacturingOrderApi.getById(id).subscribe({
      next: (mo) => {
        this.mo = mo;
        this.produceQuantity = Math.max(1, this.remaining());
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load manufacturing order'),
    });
  }

  remaining(): number {
    return this.mo ? this.mo.quantityPlanned - this.mo.quantityProduced : 0;
  }

  confirm(): void {
    if (!this.mo) return;
    this.error = null;
    this.manufacturingOrderApi.confirm(this.mo.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to confirm manufacturing order'),
    });
  }

  cancel(): void {
    if (!this.mo) return;
    if (!confirm(`Cancel manufacturing order ${this.mo.orderNumber}?`)) return;
    this.error = null;
    this.manufacturingOrderApi.cancel(this.mo.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel manufacturing order'),
    });
  }

  recordProduction(): void {
    if (!this.mo) return;
    if (!this.produceQuantity || this.produceQuantity <= 0) {
      this.produceError = 'Enter a quantity greater than zero';
      return;
    }
    this.produceError = null;
    this.manufacturingOrderApi.produce(this.mo.id, { quantity: this.produceQuantity }).subscribe({
      next: () => this.load(),
      error: (err) => (this.produceError = err?.error?.message ?? 'Failed to record production'),
    });
  }

  methodLabel(method: string): string {
    switch (method) {
      case 'FLAT':
        return 'Flat';
      case 'PER_UNIT':
        return 'Per unit';
      case 'PERCENT_OF_MATERIAL_COST':
        return '% of material';
      default:
        return method;
    }
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
