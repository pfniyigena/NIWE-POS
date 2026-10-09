import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product } from '../core/models';

/**
 * Admin > Products (Task: "check the loading of 100000 products"). Used to load every product
 * in one request and render every row in one unbounded *ngFor — fine at a few dozen products,
 * unworkable at real scale: a 100,000-row table freezes the tab, and the backend paid to
 * serialize the entire catalog on every visit even to look at page one. Now a real server-side
 * pager (CatalogApiService.listAllProducts(q, page, size) → PageResponse) — only `pageSize` rows
 * ever cross the wire or hit the DOM at once, however big the catalog gets.
 */
@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Products</h3>
        <a class="btn btn-primary" routerLink="/admin/products/new">+ New product</a>
      </div>

      <div class="flex gap-2 mb-3" style="align-items:center;">
        <input
          class="form-control"
          style="max-width:280px;"
          placeholder="Search by name, SKU, barcode or code"
          [(ngModel)]="searchTerm"
          (input)="onSearchChange()"
        />
        <select class="form-select" style="max-width:140px;" [(ngModel)]="pageSize" (ngModelChange)="onPageSizeChange()">
          <option [ngValue]="25">25 / page</option>
          <option [ngValue]="50">50 / page</option>
          <option [ngValue]="100">100 / page</option>
          <option [ngValue]="200">200 / page</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="!loading">
          {{ totalElements | number }} product{{ totalElements === 1 ? '' : 's' }}
        </span>
        <!-- Fourier: "barcode/label generation for products that don't already carry a
             manufacturer barcode" — print labels for any set of products picked on this page at
             once, rather than one at a time from each product's own edit screen. -->
        <a
          class="btn btn-outline-secondary"
          style="margin-left:auto;"
          *ngIf="selectedIds.size > 0"
          [routerLink]="['/admin/products/labels']"
          [queryParams]="{ ids: selectedIdsParam() }"
          target="_blank"
        >
          Print labels ({{ selectedIds.size }})
        </a>
      </div>

      <table>
        <thead>
          <tr>
            <th><input type="checkbox" [checked]="allOnPageSelected()" (change)="toggleSelectAll($event)" /></th>
            <th></th>
            <th>Name</th>
            <th>SKU</th>
            <th>Barcode</th>
            <th>Code</th>
            <th>Category</th>
            <th>Brand</th>
            <th>Tax</th>
            <th class="text-end">Price</th>
            <th class="text-end">Cost</th>
            <th class="text-end">Stock</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of products">
            <td><input type="checkbox" [checked]="selectedIds.has(p.id)" (change)="toggleSelected(p.id)" /></td>
            <td>
              <img *ngIf="p.iconDataUrl" [src]="p.iconDataUrl" alt="" style="width:32px; height:32px; object-fit:cover; border-radius:4px;" />
            </td>
            <td>{{ p.name }}</td>
            <td>{{ p.sku }}</td>
            <td>
              <span *ngIf="p.barcode">
                {{ p.barcode }}
                <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/products/labels']" [queryParams]="{ ids: p.id }" target="_blank">
                  Label
                </a>
              </span>
              <button
                type="button"
                class="btn btn-sm btn-outline-secondary"
                *ngIf="!p.barcode"
                [disabled]="generatingFor === p.id"
                (click)="generateBarcode(p)"
              >
                {{ generatingFor === p.id ? 'Generating…' : 'Generate' }}
              </button>
            </td>
            <td>{{ p.code }}</td>
            <td>
              <span class="color-swatch" *ngIf="p.categoryColor" [style.background]="p.categoryColor"></span>
              {{ p.categoryName }}
            </td>
            <td>{{ p.brandName || '—' }}</td>
            <td>
              <span *ngIf="p.taxRateName">
                {{ p.taxRateName }} ({{ p.taxRatePercent | number: '1.0-2' }}%<ng-container *ngIf="p.taxRateInclusive">, incl.</ng-container>)
              </span>
              <span class="text-muted" *ngIf="!p.taxRateName">— exempt —</span>
            </td>
            <td class="text-end">{{ p.unitPrice | number: '1.2-2' }}</td>
            <td class="text-end">{{ p.costPrice | number: '1.2-2' }}</td>
            <td class="text-end">
              <span *ngIf="p.trackStock">{{ p.stockQuantity }}</span>
              <span class="text-muted" *ngIf="!p.trackStock" title="Always sellable — stock isn't tracked for this product">Not tracked</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="p.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!p.active">Inactive</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/products', p.id]">View</a>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/products', p.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-danger" *ngIf="p.active" (click)="deactivate(p)">Deactivate</button>
            </td>
          </tr>
          <tr *ngIf="!loading && products.length === 0">
            <td colspan="14" class="text-muted">No products {{ searchTerm ? 'match your search' : 'yet' }}.</td>
          </tr>
          <tr *ngIf="loading">
            <td colspan="14" class="text-muted">Loading…</td>
          </tr>
        </tbody>
      </table>

      <div class="flex gap-2 mt-3" style="align-items:center;" *ngIf="totalPages > 1">
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page === 0" (click)="goToPage(page - 1)">&laquo; Prev</button>
        <span class="text-muted" style="font-size:0.85rem;">Page {{ page + 1 }} of {{ totalPages }}</span>
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page >= totalPages - 1" (click)="goToPage(page + 1)">Next &raquo;</button>
      </div>
    </app-shell>
  `,
})
export class ProductListComponent implements OnInit {
  products: Product[] = [];
  error: string | null = null;
  loading = false;

  searchTerm = '';
  page = 0;
  pageSize = 50;
  totalElements = 0;
  totalPages = 0;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  /** Fourier: "barcode/label generation ..." — selection is page-local (ids only), not persisted across page changes; printing reads it fresh each time via the query param on navigation. */
  selectedIds = new Set<number>();
  generatingFor: number | null = null;

  constructor(private catalogApi: CatalogApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.catalogApi.listAllProducts(this.searchTerm || null, this.page, this.pageSize).subscribe({
      next: (result) => {
        this.products = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load products';
        this.loading = false;
      },
    });
  }

  /** Debounced so every keystroke doesn't fire its own request against a possibly-huge catalog. */
  onSearchChange(): void {
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.page = 0;
      this.load();
    }, 300);
  }

  onPageSizeChange(): void {
    this.page = 0;
    this.load();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.page = page;
    this.load();
  }

  deactivate(p: Product): void {
    if (!confirm(`Deactivate "${p.name}"? It will no longer appear on the terminal.`)) return;
    this.error = null;
    this.catalogApi.deactivateProduct(p.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to deactivate product'),
    });
  }

  toggleSelected(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  allOnPageSelected(): boolean {
    return this.products.length > 0 && this.products.every((p) => this.selectedIds.has(p.id));
  }

  toggleSelectAll(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    for (const p of this.products) {
      if (checked) this.selectedIds.add(p.id);
      else this.selectedIds.delete(p.id);
    }
  }

  /** Comma-separated, for the "ids" query param both the per-row Label link and this one read on ProductLabelsComponent. */
  selectedIdsParam(): string {
    return Array.from(this.selectedIds).join(',');
  }

  generateBarcode(p: Product): void {
    this.error = null;
    this.generatingFor = p.id;
    this.catalogApi.generateBarcode(p.id).subscribe({
      next: (updated) => {
        this.generatingFor = null;
        p.barcode = updated.barcode;
      },
      error: (err) => {
        this.generatingFor = null;
        this.error = err?.error?.message ?? 'Failed to generate a barcode';
      },
    });
  }
}
