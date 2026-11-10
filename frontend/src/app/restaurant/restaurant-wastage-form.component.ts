import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantWastageApiService } from '../core/api/restaurant-wastage-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product, RestaurantWastageReason } from '../core/models';

import { q4 } from '../core/qty';
/**
 * Admin > Restaurant > Wastage > New — Fourier: "Restaurants need a dedicated wastage module."
 * Mirrors StockAdjustmentFormComponent's own product-search-to-select pattern, but quantity here
 * is always the positive amount lost (unlike a stock adjustment's signed delta) and the reason list
 * matches RestaurantWastageReason instead of StockAdjustmentReason.
 */
@Component({
  selector: 'app-restaurant-wastage-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'Record wastage' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3" style="position:relative;">
        <label class="form-label">{{ 'Product' | translate }}</label>
        <input
          class="form-control"
          [placeholder]="'Search by name, SKU or code' | translate"
          [(ngModel)]="productSearch"
          (input)="onProductSearch()"
          [disabled]="!!selectedProduct"
        />
        <div class="dropdown-panel" *ngIf="productResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of productResults" (click)="selectProduct(p)">
            {{ p.name }} <span class="text-muted">{{ '— current stock {p0}' | translate: { p0: (p.stockQuantity) } }}</span>
          </div>
        </div>
        <div class="mt-2" *ngIf="selectedProduct as p">
          <strong>{{ p.name }}</strong> {{ '— current stock: {p0}' | translate: { p0: (p.stockQuantity) } }}
          <button type="button" class="btn btn-sm btn-link" (click)="clearProduct()">{{ 'Change' | translate }}</button>
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">{{ 'Quantity wasted' | translate }}</label>
        <input class="form-control" type="number" step="any" min="0.0001" [(ngModel)]="quantity" name="quantity" />
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          {{ 'The amount lost — always a positive number.' | translate }}
          <span *ngIf="selectedProduct && quantity">
            {{ 'New stock will be {p0}.' | translate: { p0: (stockAfter()) } }}
          </span>
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">{{ 'Reason' | translate }}</label>
        <select class="form-select" [(ngModel)]="reason" name="reason">
          <option value="SPOILAGE">{{ 'Spoilage' | translate }}</option>
          <option value="BURNT_OR_OVERCOOKED">{{ 'Burnt / overcooked' | translate }}</option>
          <option value="DROPPED_OR_SPILLED">{{ 'Dropped / spilled' | translate }}</option>
          <option value="EXPIRED">{{ 'Expired' | translate }}</option>
          <option value="PREP_TRIM">{{ 'Prep trim' | translate }}</option>
          <option value="CUSTOMER_COMPLAINT">{{ 'Customer complaint' | translate }}</option>
          <option value="OTHER">{{ 'Other' | translate }}</option>
        </select>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">{{ 'Note (optional)' | translate }}</label>
        <textarea class="form-control" rows="2" [(ngModel)]="note" name="note"></textarea>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">{{ 'Save wastage' | translate }}</button>
        <a class="btn btn-link" routerLink="/admin/restaurant/wastage">{{ 'Cancel' | translate }}</a>
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
export class RestaurantWastageFormComponent {
  productSearch = '';
  productResults: Product[] = [];
  selectedProduct: Product | null = null;

  stockAfter(): number {
    return q4((this.selectedProduct?.stockQuantity ?? 0) - Number(this.quantity || 0));
  }
  quantity = 1;
  reason: RestaurantWastageReason = 'SPOILAGE';
  note = '';
  error: string | null = null;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private wastageApi: RestaurantWastageApiService,
    private catalogApi: CatalogApiService,
    private router: Router
  ) {}

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
    if (!this.quantity || this.quantity <= 0) {
      this.error = 'Enter a quantity greater than zero';
      return;
    }
    this.error = null;
    this.wastageApi
      .create({ productId: this.selectedProduct.id, quantity: this.quantity, reason: this.reason, note: this.note || null })
      .subscribe({
        next: () => this.router.navigate(['/admin/restaurant/wastage']),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to save wastage record'),
      });
  }
}
