import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelExportComponent } from '../hotel/hotel-export.component';
import { ShellComponent } from '../shared/shell.component';
import { MenuApiService } from '../core/api/menu-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { MenuCategory, MenuItem, OptionGroup, Product } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Admin > Restaurant > Menu (Fourier: "Menu & menu categories," reusing existing products — the
 * user's own choice when asked, over a separate menu-item concept). One screen: categories on the
 * left, the selected category's items (each an existing Product) on the right — same
 * "search existing products to add" convention as SalesOrderFormComponent's line-adding UI.
 */
@Component({
  selector: 'app-menu-admin',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, HotelExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Menu' | translate }}</h3>
        <app-hotel-export path="/restaurant/menu/items/export" name="restaurant-menu"></app-hotel-export>
      </div>
      <div class="flex gap-3" style="align-items:flex-start;">
        <div class="card" style="padding:1rem; flex:0 0 280px;">
          <div class="flex justify-between align-center mb-2">
            <h4 style="font-size:1rem; margin:0;">{{ 'Categories' | translate }}</h4>
            <button class="btn btn-sm btn-outline-primary" (click)="startNewCategory()">{{ '+ Add' | translate }}</button>
          </div>
          <div class="card mb-2" style="padding:0.5rem;" *ngIf="editingCategory">
            <input class="form-control mb-2" [placeholder]="'Category name' | translate" [(ngModel)]="categoryName" name="categoryName" />
            <div class="flex gap-2">
              <button class="btn btn-sm btn-primary" (click)="saveCategory()">{{ 'Save' | translate }}</button>
              <button class="btn btn-sm btn-link" (click)="editingCategory = false">{{ 'Cancel' | translate }}</button>
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
          <p class="text-muted" *ngIf="categories.length === 0">{{ 'No categories yet.' | translate }}</p>
        </div>

        <div class="card" style="padding:1rem; flex:1;">
          <div *ngIf="!selectedCategory" class="text-muted">{{ 'Select or add a category to manage its items.' | translate }}</div>
          <div *ngIf="selectedCategory">
            <h4 style="font-size:1rem;" class="mb-2">{{ '{p0} — items' | translate: { p0: (selectedCategory.name) } }}</h4>
            <div class="mb-2" style="position:relative;">
              <input
                class="form-control"
                [placeholder]="'Search products by name/SKU to add to this category' | translate"
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
                  <th>{{ 'Product' | translate }}</th>
                  <th>{{ 'Station' | translate }}</th>
                  <th>{{ 'Course' | translate }}</th>
                  <th>{{ 'Prep (min)' | translate }}</th>
                  <th>Choices &amp; allergens</th>
                  <th class="text-end">{{ 'Menu price' | translate }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <ng-container *ngFor="let item of items">
                <tr class="menu-row">
                  <td>
                    {{ item.product?.name }}
                    <span *ngIf="item.hasRecipe" class="text-muted" [title]="'Has a bill of materials — checkout consumes its ingredients (Admin > Manufacturing > Bill of materials)' | translate">{{ '🍳 recipe' | translate }}</span>
                  </td>
                  <td>
                    <input
                      type="text"
                      class="form-control form-control-sm"
                      style="width:130px;"
                      [placeholder]="'e.g. Grill' | translate"
                      [ngModel]="item.station"
                      (ngModelChange)="setStation(item, $event)"
                    />
                  </td>
                  <td>
                    <select class="form-select form-select-sm mi-course" style="width:90px;" [ngModel]="item.defaultCourse ?? 1" (ngModelChange)="setCourse(item, $event)" [name]="'course' + item.id">
                      <option *ngFor="let c of [1,2,3,4,5]" [ngValue]="c">{{ 'Course {p0}' | translate: { p0: (c) } }}</option>
                    </select>
                  </td>
                  <td>
                    <input type="number" class="form-control form-control-sm mi-prep" style="width:80px;" min="0" max="240" [placeholder]="'usual' | translate" [ngModel]="item.prepMinutes" (change)="setPrep(item, $any($event.target).value)" [name]="'prep' + item.id" />
                  </td>
                  <td>
                    <button class="btn btn-sm btn-outline-secondary mi-expand" (click)="toggleDetail(item)">
                      {{ '{p0} group{p1}' | translate: { p0: (item.optionGroups?.length ?? 0), p1: ((item.optionGroups?.length ?? 0) === 1 ? '' : 's') } }}<span *ngIf="item.allergens"> · {{ pretty(item.allergens) }}</span>
                    </button>
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
                  <td class="flex gap-1">
                    <button class="btn btn-sm mi-soldout" [class.btn-danger]="item.soldOut" [class.btn-outline-secondary]="!item.soldOut" (click)="toggleSoldOut(item)">{{ item.soldOut ? ('Sold out — put back' | translate) : ('Sold out?' | translate) }}</button>
                    <button class="btn btn-sm btn-outline-danger" (click)="removeItem(item)">{{ 'Remove' | translate }}</button>
                  </td>
                </tr>
                <tr class="item-detail" *ngIf="detailId === item.id">
                  <td colspan="8" style="background:var(--hover, rgba(0,0,0,0.03)); padding:0.75rem;">
                    <div class="mb-3">
                      <strong>{{ 'Allergens' | translate }}</strong>
                      <div class="flex gap-3" style="flex-wrap:wrap;">
                        <label *ngFor="let a of allergenList"><input type="checkbox" class="ma-chk" [attr.data-al]="a" [(ngModel)]="alSel[a]" [name]="'ma' + a" /> {{ pretty(a) }}</label>
                      </div>
                      <button class="btn btn-sm btn-primary mt-1 ma-save" (click)="saveAllergens(item)">{{ 'Save allergens' | translate }}</button>
                    </div>
                    <div>
                      <strong>{{ 'Choices' | translate }}</strong>
                      <span class="text-muted" style="font-size:0.85rem;"> {{ '— sizes, cooking, extras. Each option can add to the price.' | translate }}</span>
                      <div class="card mb-2 mt-1 mo-group" style="padding:0.5rem;" *ngFor="let g of editGroups; let gi = index">
                        <div class="flex gap-2 align-center mb-1" style="flex-wrap:wrap;">
                          <input class="form-control form-control-sm mo-gname" style="width:160px;" [placeholder]="'Group, e.g. Size' | translate" [(ngModel)]="g.name" [name]="'gn' + gi" />
                          <label><input type="checkbox" class="mo-req" [(ngModel)]="g.required" [name]="'gr' + gi" /> {{ 'Must choose' | translate }}</label>
                          <label>{{ 'Up to' | translate }} <input type="number" class="form-control form-control-sm mo-max" min="0" max="20" style="width:60px; display:inline-block;" [(ngModel)]="g.maxSelect" [name]="'gm' + gi" /></label>
                          <label><input type="checkbox" [(ngModel)]="g.active" [name]="'ga' + gi" /> {{ 'On' | translate }}</label>
                          <button class="btn btn-sm btn-outline-danger mo-gdel" (click)="editGroups.splice(gi, 1)">{{ 'Remove group' | translate }}</button>
                        </div>
                        <div class="flex gap-2 align-center mb-1" *ngFor="let o of g.options; let oi = index">
                          <input class="form-control form-control-sm mo-oname" style="width:160px;" [placeholder]="'Option' | translate" [(ngModel)]="o.name" [name]="'on' + gi + '_' + oi" />
                          <input class="form-control form-control-sm mo-oprice" type="number" step="0.01" style="width:100px;" [placeholder]="'+ price' | translate" [(ngModel)]="o.priceDelta" [name]="'op' + gi + '_' + oi" />
                          <label><input type="checkbox" [(ngModel)]="o.active" [name]="'oa' + gi + '_' + oi" /> {{ 'On' | translate }}</label>
                          <button class="btn btn-sm btn-outline-danger mo-odel" (click)="g.options.splice(oi, 1)">✕</button>
                        </div>
                        <div><button class="btn btn-sm btn-outline-secondary mo-add-opt" (click)="g.options.push({ name: '', priceDelta: 0, active: true })">{{ '+ Option' | translate }}</button></div>
                      </div>
                      <div class="flex gap-2">
                        <button class="btn btn-sm btn-outline-secondary mo-add-group" (click)="addGroup()">{{ '+ Group' | translate }}</button>
                        <button class="btn btn-sm btn-primary mo-save" (click)="saveOptions(item)">{{ 'Save choices' | translate }}</button>
                      </div>
                    </div>
                  </td>
                </tr>
                </ng-container>
                <tr *ngIf="items.length === 0">
                  <td colspan="6" class="text-muted">{{ 'No items yet — search for a product above to add one.' | translate }}</td>
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

  detailId: number | null = null;
  editGroups: OptionGroup[] = [];
  allergenList: string[] = [];
  alSel: Record<string, boolean> = {};

  productSearch = '';
  productResults: Product[] = [];
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private menuApi: MenuApiService,
    private catalogApi: CatalogApiService
  ) {}

  ngOnInit(): void {
    this.loadCategories();
    this.menuApi.allergens().subscribe({ next: (a) => (this.allergenList = a), error: () => (this.allergenList = []) });
  }

  loadCategories(): void {
    this.menuApi.listCategories().subscribe({
      next: (c) => (this.categories = c),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load menu categories')),
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
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load menu items')),
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
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save category')),
    });
  }

  deleteCategory(c: MenuCategory): void {
    if (!confirm(T('Delete menu category "{p0}"?', { p0: c.name }))) return;
    this.error = null;
    this.menuApi.deleteCategory(c.id).subscribe({
      next: () => {
        if (this.selectedCategory?.id === c.id) {
          this.selectedCategory = null;
          this.items = [];
        }
        this.loadCategories();
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to delete category')),
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
        error: (err) => (this.error = err?.error?.message ?? T('Failed to add item')),
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
        error: (err) => (this.error = err?.error?.message ?? T('Failed to update price')),
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
          error: (err) => (this.error = err?.error?.message ?? T('Failed to update station')),
        });
    }, 400);
  }

  pretty(csv: string | null | undefined): string {
    return (csv ?? '')
      .split(',')
      .filter((x) => x)
      .map((x) => x.charAt(0) + x.slice(1).toLowerCase())
      .join(', ');
  }

  private payloadFor(item: MenuItem, extra: Record<string, unknown> = {}) {
    return {
      categoryId: this.selectedCategory!.id,
      productId: item.product!.id,
      sortOrder: item.sortOrder,
      active: item.active,
      priceOverride: item.priceOverride,
      station: item.station,
      allergens: item.allergens ?? null,
      defaultCourse: item.defaultCourse ?? 1,
      ...extra,
    };
  }

  setPrep(item: MenuItem, raw: string): void {
    if (!this.selectedCategory || !item.product) return;
    this.error = null;
    const minutes = raw === '' || raw === null ? 0 : Number(raw);
    this.menuApi.updateItem(item.id, this.payloadFor(item, { prepMinutes: minutes })).subscribe({
      next: () => this.loadItems(),
      error: (err) => {
        this.error = err?.error?.message ?? T('Failed to update the preparation time');
        this.loadItems();
      },
    });
  }

  toggleSoldOut(item: MenuItem): void {
    this.error = null;
    const next = !item.soldOut;
    const reason = next ? (window.prompt('Why is it sold out? (optional)', '') ?? '') : '';
    this.menuApi.setSoldOut(item.id, next, reason).subscribe({
      next: () => this.loadItems(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to update the item')),
    });
  }

  setCourse(item: MenuItem, course: number): void {
    if (!this.selectedCategory || !item.product) return;
    this.error = null;
    this.menuApi.updateItem(item.id, this.payloadFor(item, { defaultCourse: course })).subscribe({
      next: () => this.loadItems(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to update the course')),
    });
  }

  toggleDetail(item: MenuItem): void {
    if (this.detailId === item.id) {
      this.detailId = null;
      return;
    }
    this.detailId = item.id;
    const have = (item.allergens ?? '').split(',');
    this.alSel = {};
    for (const a of this.allergenList) this.alSel[a] = have.includes(a);
    this.editGroups = JSON.parse(JSON.stringify(item.optionGroups ?? []));
  }

  saveAllergens(item: MenuItem): void {
    if (!this.selectedCategory || !item.product) return;
    this.error = null;
    const chosen = this.allergenList.filter((a) => this.alSel[a]).join(',');
    this.menuApi.updateItem(item.id, this.payloadFor(item, { allergens: chosen })).subscribe({
      next: () => this.loadItems(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save the allergens')),
    });
  }

  addGroup(): void {
    this.editGroups.push({ name: '', required: false, maxSelect: 1, active: true, options: [{ name: '', priceDelta: 0, active: true }] });
  }

  saveOptions(item: MenuItem): void {
    this.error = null;
    this.menuApi.saveOptions(item.id, this.editGroups).subscribe({
      next: (groups) => {
        this.editGroups = JSON.parse(JSON.stringify(groups));
        this.loadItems();
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save the choices')),
    });
  }

  removeItem(item: MenuItem): void {
    this.error = null;
    this.menuApi.removeItem(item.id).subscribe({
      next: () => this.loadItems(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to remove item')),
    });
  }
}
