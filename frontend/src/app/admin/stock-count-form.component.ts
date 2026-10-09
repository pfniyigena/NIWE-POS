import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockCountApiService } from '../core/api/stock-count-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Category, Warehouse } from '../core/models';

/**
 * Admin > Inventory > Stock counts > Start. Picks the warehouse (and optionally one category); the
 * server snapshots every tracked product's quantity at each active location onto the count sheet.
 * Sales carry on while counting — differences post relative to the snapshot on approval.
 */
@Component({
  selector: 'app-stock-count-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Start a stock count</h3>
      <p class="text-muted mb-3" style="font-size:0.85rem;">
        The current system quantities are frozen onto a count sheet. Sales and receipts can carry on while you count; on approval only
        the difference between what you counted and the frozen figure is posted, so anything moved meanwhile is preserved.
      </p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3">
        <label class="form-label">Warehouse</label>
        <select class="form-select" [(ngModel)]="warehouseId" name="warehouseId">
          <option [ngValue]="null" disabled>Select a warehouse</option>
          <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}{{ w.branchName && warehouses.length > 1 ? ' (' + w.branchName + ')' : '' }}</option>
        </select>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Category (optional)</label>
        <select class="form-select" [(ngModel)]="categoryId" name="categoryId">
          <option [ngValue]="null">All categories</option>
          <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
        </select>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Notes (optional)</label>
        <textarea class="form-control" rows="2" [(ngModel)]="notes" name="notes"></textarea>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" [disabled]="saving" (click)="start()">Start count</button>
        <a class="btn btn-link" routerLink="/admin/stock-counts">Cancel</a>
      </div>
    </app-shell>
  `,
})
export class StockCountFormComponent implements OnInit {
  warehouses: Warehouse[] = [];
  categories: Category[] = [];
  warehouseId: number | null = null;
  categoryId: number | null = null;
  notes = '';
  error: string | null = null;
  saving = false;

  constructor(
    private api: StockCountApiService,
    private warehouseApi: WarehouseApiService,
    private catalogApi: CatalogApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.warehouseApi.listAll().subscribe({
      next: (w) => {
        this.warehouses = w.filter((x) => x.active);
        if (this.warehouses.length === 1) this.warehouseId = this.warehouses[0].id;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load warehouses'),
    });
    this.catalogApi.listCategories().subscribe({ next: (c) => (this.categories = c), error: () => (this.categories = []) });
  }

  start(): void {
    if (!this.warehouseId) {
      this.error = 'Select a warehouse';
      return;
    }
    this.error = null;
    this.saving = true;
    this.api.start({ warehouseId: this.warehouseId, categoryId: this.categoryId, notes: this.notes.trim() || null }).subscribe({
      next: (c) => this.router.navigate(['/admin/stock-counts', c.id]),
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to start the count';
      },
    });
  }
}
