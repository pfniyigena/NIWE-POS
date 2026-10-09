import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ShellComponent } from '../shared/shell.component';
import { StockTransferApiService } from '../core/api/stock-transfer-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { Product, Warehouse } from '../core/models';

/** One flattened, pickable row — a location, labeled with its own warehouse so two same-named locations under different warehouses stay distinguishable. */
interface LocationOption {
  id: number;
  label: string;
}

/**
 * Admin > Inventory > Stock Transfers > New — Fourier: "multi-location/multi-warehouse tracking
 * ... Stock Transfers", then later: "User can make stock transfer to any location." Same "logs
 * for review" shape as StockAdjustmentFormComponent: this just creates a PENDING entry — an admin
 * (or someone granted APPROVE_STOCK_TRANSFER) still has to confirm it from the list screen before
 * any stock actually moves. Picks a *location* on each side (not a warehouse) — the two can even
 * be two locations under the same warehouse, which the original warehouse-to-warehouse shape
 * couldn't express.
 */
@Component({
  selector: 'app-stock-transfer-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">New stock transfer</h3>
      <p class="text-muted mb-3" style="font-size:0.85rem;">
        This logs the transfer for review — stock isn't moved until it's confirmed from the Stock transfers list.
      </p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3" style="position:relative;">
        <label class="form-label">Product</label>
        <input
          class="form-control"
          placeholder="Search by name, SKU or code"
          [(ngModel)]="productSearch"
          (input)="onProductSearch()"
          [disabled]="!!selectedProduct"
        />
        <div class="dropdown-panel" *ngIf="productResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of productResults" (click)="selectProduct(p)">
            {{ p.name }}
          </div>
        </div>
        <div class="mt-2" *ngIf="selectedProduct as p">
          <strong>{{ p.name }}</strong>
          <button type="button" class="btn btn-sm btn-link" (click)="clearProduct()">Change</button>
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">From location</label>
        <select class="form-select" [(ngModel)]="fromLocationId" name="fromLocationId">
          <option [ngValue]="null" disabled>Select a location</option>
          <option *ngFor="let loc of locationOptions" [ngValue]="loc.id">{{ loc.label }}</option>
        </select>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">To location</label>
        <select class="form-select" [(ngModel)]="toLocationId" name="toLocationId">
          <option [ngValue]="null" disabled>Select a location</option>
          <option *ngFor="let loc of locationOptions" [ngValue]="loc.id" [disabled]="loc.id === fromLocationId">{{ loc.label }}</option>
        </select>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          Can be another location under the same warehouse, or one under a different warehouse.
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Quantity</label>
        <input class="form-control" type="number" min="1" [(ngModel)]="quantity" name="quantity" />
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Note (optional)</label>
        <textarea class="form-control" rows="2" [(ngModel)]="note" name="note"></textarea>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save transfer</button>
        <a class="btn btn-link" routerLink="/admin/stock-transfers">Cancel</a>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .dropdown-panel {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 20;
        background: var(--surface, #fff);
        border: 1px solid var(--border, #ddd);
        border-radius: 0.375rem;
        max-height: 260px;
        overflow-y: auto;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
      }
      .dropdown-item {
        padding: 0.5rem 0.75rem;
        cursor: pointer;
      }
      .dropdown-item:hover {
        background: var(--hover, rgba(0, 0, 0, 0.05));
      }
    `,
  ],
})
export class StockTransferFormComponent implements OnInit {
  productSearch = '';
  productResults: Product[] = [];
  selectedProduct: Product | null = null;
  locationOptions: LocationOption[] = [];
  fromLocationId: number | null = null;
  toLocationId: number | null = null;
  quantity = 1;
  note = '';
  error: string | null = null;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private stockTransferApi: StockTransferApiService,
    private catalogApi: CatalogApiService,
    private warehouseApi: WarehouseApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.warehouseApi.listAll().subscribe({
      next: (warehouses) => this.loadLocationOptions(warehouses.filter((w) => w.active)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load warehouses'),
    });
  }

  /**
   * Flattens every active warehouse's own active locations into one pickable list, each labeled
   * "Warehouse / Location (Type)" so same-named locations under different warehouses stay
   * distinguishable, and so the type — RECEIVING, SALES, STORAGE, or DAMAGED — is visible right in
   * the picker. Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — this is now the only
   * way to move received stock out of RECEIVING onto a sellable SALES location, or stock out of
   * DAMAGED back into circulation, so every type deliberately stays pickable on both sides.
   */
  private loadLocationOptions(warehouses: Warehouse[]): void {
    if (warehouses.length === 0) {
      this.locationOptions = [];
      return;
    }
    forkJoin(warehouses.map((w) => this.warehouseApi.listLocations(w.id))).subscribe({
      next: (perWarehouseLocations) => {
        this.locationOptions = perWarehouseLocations.flatMap((locations, i) =>
          locations.filter((loc) => loc.active).map((loc) => ({ id: loc.id, label: `${warehouses[i].name} / ${loc.name} (${loc.type ?? '—'})` }))
        );
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load locations'),
    });
  }

  onProductSearch(): void {
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    const term = this.productSearch.trim();
    if (!term) {
      this.productResults = [];
      return;
    }
    this.searchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.productResults = results.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  selectProduct(p: Product): void {
    this.selectedProduct = p;
    this.productResults = [];
    this.productSearch = '';
  }

  clearProduct(): void {
    this.selectedProduct = null;
  }

  submit(): void {
    if (!this.selectedProduct) {
      this.error = 'Select a product';
      return;
    }
    if (!this.fromLocationId || !this.toLocationId) {
      this.error = 'Select both a source and destination location';
      return;
    }
    if (this.fromLocationId === this.toLocationId) {
      this.error = 'Source and destination location must be different';
      return;
    }
    if (!this.quantity || this.quantity <= 0) {
      this.error = 'Enter a quantity greater than zero';
      return;
    }
    this.error = null;
    this.stockTransferApi
      .create({
        productId: this.selectedProduct.id,
        fromLocationId: this.fromLocationId,
        toLocationId: this.toLocationId,
        quantity: this.quantity,
        note: this.note || null,
      })
      .subscribe({
        next: () => this.router.navigate(['/admin/stock-transfers']),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to save transfer'),
      });
  }
}
