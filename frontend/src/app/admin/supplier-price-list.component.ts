import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { SupplierPriceApiService } from '../core/api/supplier-price-api.service';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product, Supplier, SupplierPrice } from '../core/models';

/**
 * Admin > Suppliers > Supplier prices — what each supplier charges per product (net of tax), optionally for
 * a date range. New purchase orders prefill their unit cost from the price in force today.
 */
@Component({
  selector: 'app-supplier-price-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Supplier prices</h3>
        <select class="form-select" style="max-width:260px;" [(ngModel)]="filterSupplierId" (ngModelChange)="load()" name="filterSupplier">
          <option [ngValue]="null">All suppliers</option>
          <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
        </select>
      </div>

      <div class="card mb-3" style="padding:0.75rem;">
        <strong>{{ editingId ? 'Edit price' : 'Add a price' }}</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div style="min-width:200px;">
            <label class="form-label">Supplier</label>
            <select class="form-select" [(ngModel)]="supplierId" name="supplierId" [disabled]="!!editingId">
              <option [ngValue]="null" disabled>Select</option>
              <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div style="min-width:240px; position:relative;">
            <label class="form-label">Product</label>
            <input class="form-control" placeholder="Search name, SKU or code" [(ngModel)]="productSearch" name="productSearch" (input)="onProductSearch()" [disabled]="!!editingId" />
            <div class="dropdown-panel" *ngIf="productResults.length > 0">
              <div class="dropdown-item" *ngFor="let p of productResults" (click)="selectProduct(p)">{{ p.name }}</div>
            </div>
          </div>
          <div style="width:130px;">
            <label class="form-label">Unit cost</label>
            <input class="form-control" type="number" min="0" step="0.01" [(ngModel)]="unitCost" name="unitCost" />
          </div>
          <div>
            <label class="form-label">Valid from</label>
            <input class="form-control" type="date" [(ngModel)]="validFrom" name="validFrom" />
          </div>
          <div>
            <label class="form-label">Valid to</label>
            <input class="form-control" type="date" [(ngModel)]="validTo" name="validTo" />
          </div>
          <div style="min-width:160px;">
            <label class="form-label">Note</label>
            <input class="form-control" [(ngModel)]="note" name="note" />
          </div>
          <button class="btn btn-primary" type="button" [disabled]="busy" (click)="save()">{{ editingId ? 'Save' : 'Add' }}</button>
          <button class="btn btn-link" type="button" *ngIf="editingId" (click)="resetForm()">Cancel</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>Supplier</th>
            <th>Product</th>
            <th class="text-end">Unit cost</th>
            <th>Valid from</th>
            <th>Valid to</th>
            <th>Note</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of prices">
            <td>{{ p.supplierName }}</td>
            <td>{{ p.productName }}<span class="text-muted" *ngIf="p.sku"> ({{ p.sku }})</span></td>
            <td class="text-end">{{ p.unitCost | number: '1.2-2' }}</td>
            <td>{{ p.validFrom || '—' }}</td>
            <td>{{ p.validTo || '—' }}</td>
            <td>{{ p.note }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="edit(p)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" type="button" (click)="remove(p)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="prices.length === 0">
            <td colspan="7" class="text-muted">No supplier prices yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
  styles: [
    `
      .dropdown-panel { position: absolute; top: 100%; left: 0; right: 0; z-index: 20; background: var(--surface, #fff); border: 1px solid var(--border, #ddd); border-radius: 0.375rem; max-height: 260px; overflow-y: auto; box-shadow: 0 4px 12px rgba(0,0,0,0.12); }
      .dropdown-item { padding: 0.5rem 0.75rem; cursor: pointer; }
      .dropdown-item:hover { background: var(--hover, rgba(0,0,0,0.05)); }
    `,
  ],
})
export class SupplierPriceListComponent implements OnInit {
  prices: SupplierPrice[] = [];
  suppliers: Supplier[] = [];
  filterSupplierId: number | null = null;
  error: string | null = null;
  busy = false;

  editingId: number | null = null;
  supplierId: number | null = null;
  productId: number | null = null;
  productSearch = '';
  productResults: Product[] = [];
  unitCost: number | null = null;
  validFrom = '';
  validTo = '';
  note = '';
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(private api: SupplierPriceApiService, private supplierApi: SupplierApiService, private catalogApi: CatalogApiService) {}

  ngOnInit(): void {
    this.supplierApi.listActive().subscribe({ next: (s) => (this.suppliers = s), error: () => (this.suppliers = []) });
    this.load();
  }

  load(): void {
    this.api.list(this.filterSupplierId).subscribe({
      next: (p) => (this.prices = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load supplier prices'),
    });
  }

  onProductSearch(): void {
    this.productId = null;
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    const term = this.productSearch.trim();
    if (!term) {
      this.productResults = [];
      return;
    }
    this.searchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (r) => (this.productResults = r.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  selectProduct(p: Product): void {
    this.productId = p.id;
    this.productSearch = p.name;
    this.productResults = [];
    if (this.unitCost === null) this.unitCost = p.costPrice;
  }

  edit(p: SupplierPrice): void {
    this.editingId = p.id;
    this.supplierId = p.supplierId;
    this.productId = p.productId;
    this.productSearch = p.productName;
    this.unitCost = p.unitCost;
    this.validFrom = p.validFrom ?? '';
    this.validTo = p.validTo ?? '';
    this.note = p.note ?? '';
  }

  resetForm(): void {
    this.editingId = null;
    this.productId = null;
    this.productSearch = '';
    this.unitCost = null;
    this.validFrom = '';
    this.validTo = '';
    this.note = '';
  }

  save(): void {
    if (!this.supplierId || !this.productId || this.unitCost === null || (this.unitCost as unknown) === '') {
      this.error = 'Pick a supplier and a product, and enter the unit cost';
      return;
    }
    const payload = {
      supplierId: this.supplierId,
      productId: this.productId,
      unitCost: Number(this.unitCost),
      validFrom: this.validFrom || null,
      validTo: this.validTo || null,
      note: this.note.trim() || null,
    };
    this.error = null;
    this.busy = true;
    const call = this.editingId ? this.api.update(this.editingId, payload) : this.api.create(payload);
    call.subscribe({
      next: () => {
        this.busy = false;
        this.resetForm();
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to save the price';
      },
    });
  }

  remove(p: SupplierPrice): void {
    if (!confirm(`Delete ${p.supplierName}'s price for ${p.productName}?`)) return;
    this.api.delete(p.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete the price'),
    });
  }
}
