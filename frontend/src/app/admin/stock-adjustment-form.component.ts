import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockAdjustmentApiService } from '../core/api/stock-adjustment-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { Product, ProductStockByLocation, StockAdjustmentReason, Warehouse, WarehouseLocation } from '../core/models';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

interface LocationOption {
  id: number;
  label: string;
}

/**
 * Admin > Inventory > Stock Adjustments > New — Task: "stock adjustments/write-offs with a reason
 * code." Fourier: "Build a full permissions system" — this now just logs a PENDING entry; an
 * admin (or someone granted APPROVE_STOCK_ADJUSTMENT) still has to confirm it from the list screen
 * before stock/accounting are actually touched — see StockAdjustmentService.create's own javadoc.
 */
@Component({
  selector: 'app-stock-adjustment-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">New stock adjustment</h3>
      <p class="text-muted mb-3" style="font-size:0.85rem;">
        This logs the correction for review — stock and accounting aren't touched until it's confirmed from the Stock adjustments list.
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
            {{ p.name }} <span class="text-muted">— current stock {{ p.stockQuantity }}</span>
          </div>
        </div>
        <div class="mt-2" *ngIf="selectedProduct as p">
          <strong>{{ p.name }}</strong> — current stock: {{ p.stockQuantity }}
          <button type="button" class="btn btn-sm btn-link" (click)="clearProduct()">Change</button>
        </div>
      </div>

      <!-- Multi-warehouse phase 2 — only shown once there is something to choose between: a
           brand-new warehouse comes with just Receiving + Main Stock (2 locations); more than that
           means a second warehouse or an extra location exists. Blank keeps the classic behaviour. -->
      <div class="form-group mb-3" *ngIf="locationOptions.length > 2">
        <label class="form-label">Location</label>
        <select class="form-select" name="locationId" [(ngModel)]="locationId">
          <option [ngValue]="null">Default (main warehouse sales stock)</option>
          <option *ngFor="let l of locationOptions" [ngValue]="l.id">{{ l.label }}</option>
        </select>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="locationId !== null && selectedProduct">
          Stock at this location: {{ currentStock }}.
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Quantity change</label>
        <input class="form-control" type="number" [(ngModel)]="quantityDelta" name="quantityDelta" />
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          Positive to add stock (e.g. a recount found more than recorded), negative to remove it (e.g. breakage, theft, expiry).
          <span *ngIf="selectedProduct && quantityDelta">
            New stock will be {{ currentStock + quantityDelta }}.
          </span>
        </div>
      </div>

      <div class="form-group mb-3" *ngIf="selectedProduct?.trackingMode === 'BATCH' && quantityDelta > 0">
        <label class="form-label">Batch / lot and expiry (optional)</label>
        <div class="flex gap-2">
          <input class="form-control" placeholder="Batch / lot no." [(ngModel)]="batchNumber" name="batchNumber" />
          <input class="form-control" type="date" [(ngModel)]="expiryDate" name="expiryDate" />
        </div>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          Removing batch stock takes the earliest-expiry units first.
        </div>
      </div>

      <div class="form-group mb-3" *ngIf="selectedProduct?.trackingMode === 'SERIAL' && quantityDelta">
        <label class="form-label">Serial numbers{{ quantityDelta > 0 ? ' being added' : ' being removed (optional — otherwise the oldest are used)' }}</label>
        <textarea class="form-control" rows="3" placeholder="One per line or comma-separated" [(ngModel)]="serialText" name="serialText"></textarea>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">{{ parseSerials(serialText).length }} entered, {{ quantityDelta > 0 ? quantityDelta : -quantityDelta }} units.</div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Reason</label>
        <select class="form-select" [(ngModel)]="reason" name="reason">
          <option value="DAMAGE">Damage</option>
          <option value="THEFT">Theft</option>
          <option value="EXPIRY">Expiry</option>
          <option value="RECOUNT">Recount</option>
          <option value="OTHER">Other</option>
        </select>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Note (optional)</label>
        <textarea class="form-control" rows="2" [(ngModel)]="note" name="note"></textarea>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save adjustment</button>
        <a class="btn btn-link" routerLink="/admin/stock-adjustments">Cancel</a>
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
export class StockAdjustmentFormComponent {
  productSearch = '';
  productResults: Product[] = [];
  selectedProduct: Product | null = null;
  quantityDelta = 0;
  batchNumber = '';
  expiryDate = '';
  serialText = '';
  reason: StockAdjustmentReason = 'RECOUNT';
  note = '';
  error: string | null = null;
  /** Multi-warehouse phase 2: every active location of every active warehouse; null selection = legacy default. */
  locationOptions: LocationOption[] = [];
  locationId: number | null = null;
  private stockByLocation: ProductStockByLocation | null = null;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private stockAdjustmentApi: StockAdjustmentApiService,
    private catalogApi: CatalogApiService,
    private warehouseApi: WarehouseApiService,
    private router: Router
  ) {
    this.loadLocationOptions();
  }

  /** The figure the "new stock will be" hint builds on: the chosen location's quantity, or the product's own stock when none is chosen. */
  get currentStock(): number {
    if (!this.selectedProduct) return 0;
    if (this.locationId === null) return this.selectedProduct.stockQuantity;
    return this.stockByLocation?.locations.find((r) => r.locationId === this.locationId)?.quantity ?? 0;
  }

  private loadLocationOptions(): void {
    this.warehouseApi
      .listAll()
      .pipe(catchError(() => of([] as Warehouse[])))
      .subscribe((all) => {
        const active = all.filter((w) => w.active);
        if (active.length === 0) return;
        forkJoin(
          active.map((w) =>
            this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[])))
          )
        ).subscribe((perWarehouse) => {
          const options: LocationOption[] = [];
          active.forEach((w, i) => {
            for (const l of perWarehouse[i]) {
              if (!l.active) continue;
              options.push({ id: l.id, label: `${w.name} / ${l.name}${l.type ? ' (' + l.type + ')' : ''}` });
            }
          });
          this.locationOptions = options;
        });
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
    this.stockByLocation = null;
    if (this.locationOptions.length > 2) {
      this.warehouseApi.stockByLocation(p.id).subscribe({
        next: (s) => (this.stockByLocation = s),
        error: () => (this.stockByLocation = null),
      });
    }
  }

  parseSerials(text: string): string[] {
    return (text || '')
      .split(/[\n,;]+/)
      .map((x) => x.trim())
      .filter((x) => x.length > 0);
  }

  clearProduct(): void {
    this.selectedProduct = null;
    this.stockByLocation = null;
  }

  submit(): void {
    if (!this.selectedProduct) {
      this.error = 'Select a product';
      return;
    }
    if (!this.quantityDelta) {
      this.error = 'Enter a nonzero quantity change';
      return;
    }
    this.error = null;
    const mode = this.selectedProduct.trackingMode ?? 'NONE';
    const serials = mode === 'SERIAL' ? this.parseSerials(this.serialText) : [];
    if (mode === 'SERIAL' && serials.length > 0 && serials.length !== Math.abs(this.quantityDelta)) {
      this.error = `Enter ${Math.abs(this.quantityDelta)} serial number(s) to match the quantity, or none (${serials.length} entered)`;
      return;
    }
    this.stockAdjustmentApi
      .create({
        productId: this.selectedProduct.id,
        quantityDelta: this.quantityDelta,
        reason: this.reason,
        note: this.note || null,
        locationId: this.locationId,
        batchNumber: mode === 'BATCH' && this.quantityDelta > 0 && this.batchNumber.trim() ? this.batchNumber.trim() : null,
        expiryDate: mode === 'BATCH' && this.quantityDelta > 0 && this.expiryDate ? this.expiryDate : null,
        serialNumbers: serials.length > 0 ? serials : undefined,
      })
      .subscribe({
        next: () => this.router.navigate(['/admin/stock-adjustments']),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to save adjustment'),
      });
  }
}
