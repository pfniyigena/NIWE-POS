import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { MenuApiService } from '../core/api/menu-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { MenuCategory, MenuItem, Product } from '../core/models';

/**
 * Admin > Restaurant > Menu (Fourier: "Menu & menu categories," reusing existing products — the
 * user's own choice when asked, over a separate menu-item concept). One screen: categories on the
 * left, the selected category's items (each an existing Product) on the right — same
 * "search existing products to add" convention as SalesOrderFormComponent's line-adding UI.
 */
@Component({
  selector: 'app-menu-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <h3 class="mb-3">Menu</h3>
      <div class="flex gap-3" style="align-items:flex-start;">
        <div class="card" style="padding:1rem; flex:0 0 280px;">
          <div class="flex justify-between align-center mb-2">
            <h4 style="font-size:1rem; margin:0;">Categories</h4>
            <button class="btn btn-sm btn-outline-primary" (click)="startNewCategory()">+ Add</button>
          </div>
          <div class="card mb-2" style="padding:0.5rem;" *ngIf="editingCategory">
            <input class="form-control mb-2" placeholder="Category name" [(ngModel)]="categoryName" name="categoryName" />
            <div class="flex gap-2">
              <button class="btn btn-sm btn-primary" (click)="saveCategory()">Save</button>
              <button class="btn btn-sm btn-link" (click)="editingCategory = false">Cancel</button>
            </div>
          </div>
          <div
            *ngFor="let c of categories"
            class="flex justify-between align-center"
            style="padding:0.4rem 0.25rem; cursor:pointer; border-radius:4px;"
            [style.background]="selectedCategory?.id === c.id ? 'var(--hover, rgba(0,0,0,0.06))' : 'transparent'"
            (click)="selectCategory(c)"
          >
            <span [class.text-muted]="!c.active">{{ c.name }}</span>
            <button class="btn btn-sm btn-outline-danger" (click)="deleteCategory(c); $event.stopPropagation()">✕</button>
          </div>
          <p class="text-muted" *ngIf="categories.length === 0">No categories yet.</p>
        </div>

        <div class="card" style="padding:1rem; flex:1;">
          <div *ngIf="!selectedCategory" class="text-muted">Select or add a category to manage its items.</div>
          <div *ngIf="selectedCategory">
            <h4 style="font-size:1rem;" class="mb-2">{{ selectedCategory.name }} — items</h4>
            <div class="mb-2" style="position:relative;">
              <input
                class="form-control"
                placeholder="Search products by name/SKU to add to this category"
                [(ngModel)]="productSearch"
                (input)="onProductSearch()"
              />
              <div class="dropdown-panel" *ngIf="productResults.length > 0">
                <div class="dropdown-item" *ngFor="let p of productResults" (click)="addItem(p)">
                  {{ p.name }} <span class="text-muted">— {{ p.unitPrice | number: '1.2-2' }}</span>
                </div>
              </div>
            </div>
            <table class="table-sm">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Station</th>
                  <th class="text-end">Menu price</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let item of items">
                  <td>
                    {{ item.product?.name }}
                    <span *ngIf="item.hasRecipe" class="text-muted" title="Has a bill of materials — checkout consumes its ingredients (Admin > Manufacturing > Bill of materials)">🍳 recipe</span>
                  </td>
                  <td>
                    <input
                      type="text"
                      class="form-control form-control-sm"
                      style="width:130px;"
                      placeholder="e.g. Grill"
                      [ngModel]="item.station"
                      (ngModelChange)="setStation(item, $event)"
                    />
                  </td>
                  <td class="text-end">
                    <input
                      type="number"
                      class="form-control form-control-sm"
                      style="width:110px; margin-left:auto;"
                      min="0"
                      step="0.01"
                      [ngModel]="item.priceOverride ?? item.product?.unitPrice"
                      (ngModelChange)="setPriceOverride(item, $event)"
                    />
                  </td>
                  <td><button class="btn btn-sm btn-outline-danger" (click)="removeItem(item)">Remove</button></td>
                </tr>
                <tr *ngIf="items.length === 0">
                  <td colspan="4" class="text-muted">No items yet — search for a product above to add one.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
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
export class MenuAdminComponent implements OnInit {
  categories: MenuCategory[] = [];
  selectedCategory: MenuCategory | null = null;
  items: MenuItem[] = [];
  error: string | null = null;

  editingCategory = false;
  categoryName = '';

  productSearch = '';
  productResults: Product[] = [];
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private menuApi: MenuApiService,
    private catalogApi: CatalogApiService
  ) {}

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.menuApi.listCategories().subscribe({
      next: (c) => (this.categories = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load menu categories'),
    });
  }

  selectCategory(c: MenuCategory): void {
    this.selectedCategory = c;
    this.loadItems();
  }

  loadItems(): void {
    if (!this.selectedCategory) return;
    this.menuApi.listItems(this.selectedCategory.id).subscribe({
      next: (i) => (this.items = i),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load menu items'),
    });
  }

  startNewCategory(): void {
    this.editingCategory = true;
    this.categoryName = '';
  }

  saveCategory(): void {
    if (!this.categoryName.trim()) return;
    this.error = null;
    this.menuApi.createCategory({ name: this.categoryName.trim(), sortOrder: this.categories.length, active: true }).subscribe({
      next: () => {
        this.editingCategory = false;
        this.loadCategories();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save category'),
    });
  }

  deleteCategory(c: MenuCategory): void {
    if (!confirm(`Delete menu category "${c.name}"?`)) return;
    this.error = null;
    this.menuApi.deleteCategory(c.id).subscribe({
      next: () => {
        if (this.selectedCategory?.id === c.id) {
          this.selectedCategory = null;
          this.items = [];
        }
        this.loadCategories();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete category'),
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
        next: (results) => (this.productResults = results),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  addItem(p: Product): void {
    if (!this.selectedCategory) return;
    this.error = null;
    this.menuApi
      .addItem({ categoryId: this.selectedCategory.id, productId: p.id, sortOrder: this.items.length, active: true })
      .subscribe({
        next: () => {
          this.productSearch = '';
          this.productResults = [];
          this.loadItems();
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to add item'),
      });
  }

  setPriceOverride(item: MenuItem, value: number): void {
    if (!this.selectedCategory || !item.product) return;
    const override = value === item.product.unitPrice ? null : value;
    this.menuApi
      .updateItem(item.id, {
        categoryId: this.selectedCategory.id,
        productId: item.product.id,
        sortOrder: item.sortOrder,
        active: item.active,
        priceOverride: override,
        station: item.station,
      })
      .subscribe({
        next: () => this.loadItems(),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to update price'),
      });
  }

  private stationDebounce: ReturnType<typeof setTimeout> | null = null;

  /** Fourier: "Kitchen stations" — debounced since this fires on every keystroke in the free-text station input. */
  setStation(item: MenuItem, value: string): void {
    if (!this.selectedCategory || !item.product) return;
    item.station = value;
    if (this.stationDebounce) clearTimeout(this.stationDebounce);
    this.stationDebounce = setTimeout(() => {
      this.menuApi
        .updateItem(item.id, {
          categoryId: this.selectedCategory!.id,
          productId: item.product!.id,
          sortOrder: item.sortOrder,
          active: item.active,
          priceOverride: item.priceOverride,
          station: value.trim() || null,
        })
        .subscribe({
          next: () => this.loadItems(),
          error: (err) => (this.error = err?.error?.message ?? 'Failed to update station'),
        });
    }, 400);
  }

  removeItem(item: MenuItem): void {
    this.error = null;
    this.menuApi.removeItem(item.id).subscribe({
      next: () => this.loadItems(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to remove item'),
    });
  }
}
