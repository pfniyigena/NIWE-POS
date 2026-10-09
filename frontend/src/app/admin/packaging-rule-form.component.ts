import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PackagingApiService } from '../core/api/packaging-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { Product, TaxRateOption } from '../core/models';

/** Same minimal quick-add field set InvoiceFormComponent's/RecurringInvoiceFormComponent's own "+ New product" panel uses. */
const QUICK_ADD_DEFAULTS = { costPrice: 0, stockQuantity: 0, active: true, categoryId: null, taxRateId: null };

/**
 * Admin > Inventory > Packaging rules > New/Edit. Two product-search boxes — parent and child —
 * mirroring BomFormComponent's finished-good product picker, simplified since a packaging rule
 * only ever needs one parent + one child (no multi-line component editor like a BOM). The child
 * picker also offers a "+ New product" quick-add panel (Fourier: "add quick add product on the
 * child") — the new sellable unit (a shot, a single bottle) often doesn't exist as a catalog
 * product yet when an admin is setting up a rule, so this saves a trip to Products first; same
 * minimal name/unit price/tax rate/description panel RecurringInvoiceFormComponent's own line editor uses.
 * The parent product is assumed to already exist (it's whatever's already being bought/stocked in
 * bulk), so it keeps its plain search-only picker.
 */
@Component({
  selector: 'app-packaging-rule-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit packaging rule' : 'New packaging rule' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3" style="position:relative;">
        <label class="form-label">Parent product (what gets broken down)</label>
        <input
          class="form-control"
          [(ngModel)]="parentSearch"
          name="parentSearch"
          placeholder="Search products by name, SKU or code — e.g. Whiskey 750ml bottle"
          (input)="onParentSearch()"
        />
        <div class="dropdown-panel" *ngIf="parentResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of parentResults" (click)="selectParent(p)">{{ p.name }}</div>
        </div>
        <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;" *ngIf="selectedParent">
          Selected: <strong>{{ selectedParent.name }}</strong>
        </div>
      </div>

      <div class="form-group mb-3" style="position:relative;">
        <label class="form-label">Child product (what it becomes)</label>
        <div class="flex gap-2">
          <input
            class="form-control"
            [(ngModel)]="childSearch"
            name="childSearch"
            placeholder="Search products by name, SKU or code — e.g. Whiskey shot"
            (input)="onChildSearch()"
          />
          <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewChildProduct" (click)="openNewChildProduct()">
            + New product
          </button>
        </div>
        <div class="dropdown-panel" *ngIf="childResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of childResults" (click)="selectChild(p)">{{ p.name }}</div>
        </div>
        <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;" *ngIf="selectedChild">
          Selected: <strong>{{ selectedChild.name }}</strong>
        </div>

        <div class="card mt-2" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewChildProduct">
          <div class="alert alert-danger" *ngIf="newChildProductError">{{ newChildProductError }}</div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">Name</label>
              <input class="form-control" name="newChildProductName" [(ngModel)]="newChildProductName" placeholder="e.g. Whiskey shot" />
            </div>
            <div class="grow">
              <label class="form-label">Unit price</label>
              <input class="form-control" type="number" min="0" step="0.01" name="newChildProductUnitPrice" [(ngModel)]="newChildProductUnitPrice" />
            </div>
            <div class="grow">
              <label class="form-label">Tax rate</label>
              <select class="form-select" name="newChildProductTaxRateId" [(ngModel)]="newChildProductTaxRateId">
                <option [ngValue]="null">— None (tax-exempt) —</option>
                <option *ngFor="let t of activeTaxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option>
              </select>
            </div>
            <div class="grow">
              <label class="form-label">Description (optional)</label>
              <input class="form-control" name="newChildProductDescription" [(ngModel)]="newChildProductDescription" />
            </div>
          </div>
          <div class="flex gap-2">
            <button type="button" class="btn btn-primary btn-sm" (click)="saveNewChildProduct()">Save product &amp; select</button>
            <button type="button" class="btn btn-link btn-sm" (click)="cancelNewChildProduct()">Cancel</button>
          </div>
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Units per parent</label>
        <input type="number" class="form-control" style="max-width:160px;" min="1" name="unitsPerParent" [(ngModel)]="unitsPerParent" />
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          How many whole units of the child product one unit of the parent breaks down into — e.g. 25 for a bottle into shots, or 24 for a case into bottles.
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Notes (optional)</label>
        <textarea class="form-control" rows="2" name="notes" [(ngModel)]="notes" placeholder="e.g. Count shots carefully — spillage is normal"></textarea>
      </div>

      <div class="form-group mb-3" *ngIf="id">
        <label class="form-label">
          <input type="checkbox" name="active" [(ngModel)]="active" /> Active
        </label>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save</button>
        <a class="btn btn-link" routerLink="/admin/packaging/rules">Cancel</a>
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
export class PackagingRuleFormComponent implements OnInit {
  id: number | null = null;
  selectedParent: Product | null = null;
  selectedChild: Product | null = null;
  unitsPerParent = 1;
  notes = '';
  active = true;
  error: string | null = null;

  parentSearch = '';
  parentResults: Product[] = [];
  childSearch = '';
  childResults: Product[] = [];

  showNewChildProduct = false;
  newChildProductName = '';
  newChildProductUnitPrice: number | null = null;
  newChildProductDescription = '';
  newChildProductTaxRateId: number | null = null;
  activeTaxRates: TaxRateOption[] = [];
  newChildProductError: string | null = null;

  private parentSearchDebounce: ReturnType<typeof setTimeout> | null = null;
  private childSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private packagingApi: PackagingApiService,
    private catalogApi: CatalogApiService,
    private taxRateApi: TaxRateApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.taxRateApi.listAll().subscribe({
      next: (rates) => (this.activeTaxRates = rates.filter((r) => r.active)),
      error: () => (this.activeTaxRates = []),
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.packagingApi.getRuleById(this.id).subscribe({
      next: (r) => {
        this.selectedParent = { id: r.parentProductId, name: r.parentProductName } as Product;
        this.selectedChild = { id: r.childProductId, name: r.childProductName } as Product;
        this.unitsPerParent = r.unitsPerParent;
        this.notes = r.notes ?? '';
        this.active = r.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load packaging rule'),
    });
  }

  onParentSearch(): void {
    if (this.parentSearchDebounce) clearTimeout(this.parentSearchDebounce);
    const term = this.parentSearch.trim();
    if (!term) {
      this.parentResults = [];
      return;
    }
    this.parentSearchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.parentResults = results.slice(0, 20)),
        error: () => (this.parentResults = []),
      });
    }, 250);
  }

  selectParent(p: Product): void {
    this.selectedParent = p;
    this.parentSearch = '';
    this.parentResults = [];
  }

  onChildSearch(): void {
    if (this.childSearchDebounce) clearTimeout(this.childSearchDebounce);
    const term = this.childSearch.trim();
    if (!term) {
      this.childResults = [];
      return;
    }
    this.childSearchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.childResults = results.slice(0, 20)),
        error: () => (this.childResults = []),
      });
    }, 250);
  }

  selectChild(p: Product): void {
    this.selectedChild = p;
    this.childSearch = '';
    this.childResults = [];
  }

  openNewChildProduct(): void {
    this.showNewChildProduct = true;
    this.newChildProductError = null;
  }

  cancelNewChildProduct(): void {
    this.showNewChildProduct = false;
    this.newChildProductName = '';
    this.newChildProductUnitPrice = null;
    this.newChildProductDescription = '';
    this.newChildProductTaxRateId = null;
    this.newChildProductError = null;
  }

  saveNewChildProduct(): void {
    if (!this.newChildProductName.trim()) {
      this.newChildProductError = 'Name is required';
      return;
    }
    if (this.newChildProductUnitPrice == null || this.newChildProductUnitPrice < 0) {
      this.newChildProductError = 'Enter a valid unit price';
      return;
    }
    this.newChildProductError = null;
    this.catalogApi
      .createProduct({
        name: this.newChildProductName.trim(),
        description: this.newChildProductDescription || null,
        unitPrice: this.newChildProductUnitPrice,
        ...QUICK_ADD_DEFAULTS,
        taxRateId: this.newChildProductTaxRateId,
      })
      .subscribe({
        next: (product) => {
          this.selectChild(product);
          this.cancelNewChildProduct();
        },
        error: (err) => (this.newChildProductError = err?.error?.message ?? 'Failed to save product'),
      });
  }

  submit(): void {
    if (!this.selectedParent) {
      this.error = 'Select a parent product';
      return;
    }
    if (!this.selectedChild) {
      this.error = 'Select a child product';
      return;
    }
    if (this.selectedParent.id === this.selectedChild.id) {
      this.error = 'A product cannot be broken down into itself';
      return;
    }
    if (!this.unitsPerParent || this.unitsPerParent < 1) {
      this.error = 'Units per parent must be a positive number';
      return;
    }
    this.error = null;
    const payload = {
      parentProductId: this.selectedParent.id,
      childProductId: this.selectedChild.id,
      unitsPerParent: this.unitsPerParent,
      notes: this.notes || null,
      active: this.active,
    };
    const call = this.id ? this.packagingApi.updateRule(this.id, payload) : this.packagingApi.createRule(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/packaging/rules']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save packaging rule'),
    });
  }
}
