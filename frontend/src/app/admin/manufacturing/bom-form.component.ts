import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BomApiService } from '../../core/api/bom-api.service';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { Bom, Product, Routing } from '../../core/models';

interface DraftByProduct {
  productId: number;
  productName: string;
  quantityPerUnit: number;
  costSharePercent: number;
}

interface DraftLine {
  componentId: number;
  componentName: string;
  quantityPerUnit: number;
}

/**
 * Manufacturing > Bill of Materials > New/Edit. Component-line editor mirrors
 * PurchaseOrderFormComponent's search-to-add product line pattern; the finished-good product
 * itself is picked the same way, in its own search box above the component lines.
 */
@Component({
  selector: 'app-bom-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit bill of materials' : 'New bill of materials' }}<span class="text-muted" *ngIf="costing?.version" style="font-size:1rem;"> — version {{ costing?.version }}</span></h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3" style="position:relative; max-width:480px;">
        <label class="form-label">Finished-good product</label>
        <input
          class="form-control"
          [(ngModel)]="productSearch"
          name="productSearch"
          [disabled]="!!id"
          placeholder="Search products by name, SKU or code"
          (input)="onProductSearch()"
        />
        <div class="dropdown-panel" *ngIf="productResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of productResults" (click)="selectProduct(p)">{{ p.name }}</div>
        </div>
        <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;" *ngIf="selectedProduct">
          Selected: <strong>{{ selectedProduct.name }}</strong>
        </div>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
          The product a bill of materials belongs to can't be changed once created.
        </div>
      </div>

      <div class="flex gap-2 mb-3">
        <div class="grow">
          <label class="form-label">Name (optional)</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Standard, Alt — no walnut" />
        </div>
        <div class="grow">
          <label class="form-label">Routing (optional)</label>
          <select class="form-select" name="routingId" [(ngModel)]="routingId">
            <option [ngValue]="null">— None —</option>
            <option *ngFor="let r of routings" [ngValue]="r.id">{{ r.name }}</option>
          </select>
        </div>
      </div>
      <div class="form-group mb-3" *ngIf="id">
        <label class="form-label">
          <input type="checkbox" name="active" [(ngModel)]="active" /> Active
        </label>
      </div>

      <h4 class="mb-2">Components</h4>
      <div class="flex gap-2 mb-2" style="position:relative;">
        <input
          class="form-control"
          placeholder="Search products by name, SKU or code to add a component"
          [(ngModel)]="componentSearch"
          (input)="onComponentSearch()"
        />
        <div class="dropdown-panel" *ngIf="componentResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of componentResults" (click)="addLine(p)">
            {{ p.name }} <span class="text-muted">— stock {{ p.stockQuantity }}</span>
          </div>
        </div>
      </div>

      <table class="table-sm mb-3">
        <thead>
          <tr>
            <th>Component</th>
            <th class="text-end">Quantity per unit</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let line of lines; let i = index">
            <td>{{ line.componentName }}</td>
            <td class="text-end">
              <input
                type="number" step="any"
                class="form-control form-control-sm"
                style="width:100px; margin-left:auto;"
                min="0.0001"
                [(ngModel)]="line.quantityPerUnit"
                [name]="'qty' + i"
              />
            </td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeLine(i)">Remove</button></td>
          </tr>
          <tr *ngIf="lines.length === 0">
            <td colspan="3" class="text-muted">No components yet — search for a product above to add one.</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="font-size:0.85rem;">
        "Quantity per unit" is how many of this component it takes to make exactly ONE unit of the finished good.
      </p>

      <div class="card mb-3 cost-card" style="padding:1rem;" *ngIf="costing">
        <h4 class="mb-2">Cost of one unit (today's prices)</h4>
        <table class="table-sm" style="max-width:420px;">
          <tbody>
            <tr><td>Components</td><td class="text-end cc-material">{{ costing.estimatedUnitMaterialCost | number: '1.2-2' }}</td></tr>
            <tr><td>Labour</td><td class="text-end cc-labour">{{ costing.estimatedUnitLabourCost | number: '1.2-2' }}</td></tr>
            <tr><td>Machines</td><td class="text-end cc-machine">{{ costing.estimatedUnitMachineCost | number: '1.2-2' }}</td></tr>
            <tr><td><strong>Cost per unit</strong></td><td class="text-end cc-total"><strong>{{ costing.estimatedUnitCost | number: '1.2-2' }}</strong></td></tr>
            <tr *ngIf="costing.estimatedNetUnitCost !== costing.estimatedUnitCost"><td>After by-products</td><td class="text-end cc-net">{{ costing.estimatedNetUnitCost | number: '1.2-2' }}</td></tr>
            <tr><td>Selling price</td><td class="text-end cc-price">{{ costing.productPrice | number: '1.2-2' }}</td></tr>
            <tr><td>Margin</td><td class="text-end cc-margin">{{ costing.estimatedMarginPercent != null ? (costing.estimatedMarginPercent | number: '1.0-1') + '%' : '—' }}</td></tr>
          </tbody>
        </table>
        <div class="text-muted" style="font-size:0.8rem;">Indirect costs (rent, power…) are added per order and are not included here. Save to refresh after editing.</div>
      </div>

      <h4 class="mb-2">By-products (optional)</h4>
      <div class="flex gap-2 mb-2" style="position:relative;">
        <input
          class="form-control bp-search"
          placeholder="Search products to add a by-product (bran, offcuts, whey…)"
          [(ngModel)]="byProductSearch"
          name="byProductSearch"
          (input)="onByProductSearch()"
        />
        <div class="dropdown-panel" *ngIf="byProductResults.length > 0">
          <div class="dropdown-item bp-option" *ngFor="let p of byProductResults" (click)="addByProduct(p)">{{ p.name }}</div>
        </div>
      </div>
      <table class="table-sm mb-2" *ngIf="byProducts.length > 0">
        <thead>
          <tr>
            <th>By-product</th>
            <th class="text-end">Quantity per good unit</th>
            <th class="text-end">Share of the cost (%)</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let bp of byProducts; let i = index" class="bp-row">
            <td>{{ bp.productName }}</td>
            <td class="text-end"><input type="number" step="any" class="form-control form-control-sm bp-qty" style="width:100px; margin-left:auto;" min="0.0001" [(ngModel)]="bp.quantityPerUnit" [name]="'bpq' + i" /></td>
            <td class="text-end"><input type="number" class="form-control form-control-sm bp-share" style="width:100px; margin-left:auto;" min="0" max="100" step="0.01" [(ngModel)]="bp.costSharePercent" [name]="'bps' + i" /></td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeByProduct(i)">Remove</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="font-size:0.85rem;">
        Each good unit made also puts this many of the by-product into stock. The cost share is the part of the run's cost that belongs to it; it is taken off the finished good's unit cost.
      </p>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save</button>
        <a class="btn btn-link" routerLink="/admin/manufacturing/boms">Cancel</a>
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
export class BomFormComponent implements OnInit {
  id: number | null = null;
  selectedProduct: Product | null = null;
  name = '';
  routingId: number | null = null;
  active = true;
  lines: DraftLine[] = [];
  byProducts: DraftByProduct[] = [];
  costing: Bom | null = null;
  byProductSearch = '';
  byProductResults: Product[] = [];
  private byProductDebounce: ReturnType<typeof setTimeout> | null = null;
  routings: Routing[] = [];
  error: string | null = null;

  productSearch = '';
  productResults: Product[] = [];
  componentSearch = '';
  componentResults: Product[] = [];

  private productSearchDebounce: ReturnType<typeof setTimeout> | null = null;
  private componentSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private bomApi: BomApiService,
    private routingApi: RoutingApiService,
    private catalogApi: CatalogApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.routingApi.listActive().subscribe({
      next: (r) => (this.routings = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load routings'),
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.bomApi.getById(this.id).subscribe({
      next: (b) => {
        this.costing = b;
        this.selectedProduct = { id: b.productId, name: b.productName } as Product;
        this.name = b.name ?? '';
        this.routingId = b.routingId;
        this.active = b.active;
        this.byProducts = (b.byProducts ?? []).map((x) => ({ productId: x.productId, productName: x.productName, quantityPerUnit: x.quantityPerUnit, costSharePercent: x.costSharePercent }));
        this.lines = b.lines.map((l) => ({ componentId: l.componentId, componentName: l.componentName, quantityPerUnit: l.quantityPerUnit }));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bill of materials'),
    });
  }

  onProductSearch(): void {
    if (this.productSearchDebounce) clearTimeout(this.productSearchDebounce);
    const term = this.productSearch.trim();
    if (!term) {
      this.productResults = [];
      return;
    }
    this.productSearchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.productResults = results.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  selectProduct(p: Product): void {
    this.selectedProduct = p;
    this.productSearch = '';
    this.productResults = [];
  }

  onComponentSearch(): void {
    if (this.componentSearchDebounce) clearTimeout(this.componentSearchDebounce);
    const term = this.componentSearch.trim();
    if (!term) {
      this.componentResults = [];
      return;
    }
    this.componentSearchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.componentResults = results.slice(0, 20)),
        error: () => (this.componentResults = []),
      });
    }, 250);
  }

  addLine(p: Product): void {
    if (this.selectedProduct && p.id === this.selectedProduct.id) {
      this.error = "A product cannot be a component of its own bill of materials";
      this.componentSearch = '';
      this.componentResults = [];
      return;
    }
    const existing = this.lines.find((l) => l.componentId === p.id);
    if (existing) {
      existing.quantityPerUnit += 1;
    } else {
      this.lines.push({ componentId: p.id, componentName: p.name, quantityPerUnit: 1 });
    }
    this.componentSearch = '';
    this.componentResults = [];
  }

  onByProductSearch(): void {
    if (this.byProductDebounce) clearTimeout(this.byProductDebounce);
    const term = this.byProductSearch.trim();
    if (!term) {
      this.byProductResults = [];
      return;
    }
    this.byProductDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.byProductResults = results.slice(0, 20)),
        error: () => (this.byProductResults = []),
      });
    }, 250);
  }

  addByProduct(p: Product): void {
    this.byProductSearch = '';
    this.byProductResults = [];
    if (this.selectedProduct && p.id === this.selectedProduct.id) {
      this.error = 'A product cannot be its own by-product';
      return;
    }
    if (this.lines.some((l) => l.componentId === p.id)) {
      this.error = 'A product cannot be both a component and a by-product of the same recipe';
      return;
    }
    if (!this.byProducts.some((b) => b.productId === p.id)) {
      this.byProducts.push({ productId: p.id, productName: p.name, quantityPerUnit: 1, costSharePercent: 0 });
    }
  }

  removeByProduct(index: number): void {
    this.byProducts.splice(index, 1);
  }

  removeLine(index: number): void {
    this.lines.splice(index, 1);
  }

  submit(): void {
    if (!this.selectedProduct) {
      this.error = 'Select a finished-good product';
      return;
    }
    if (this.lines.length === 0) {
      this.error = 'Add at least one component line';
      return;
    }
    this.error = null;
    const payload = {
      productId: this.selectedProduct.id,
      name: this.name || null,
      routingId: this.routingId,
      active: this.active,
      lines: this.lines.map((l) => ({ componentId: l.componentId, quantityPerUnit: l.quantityPerUnit })),
      byProducts: this.byProducts.map((b) => ({ productId: b.productId, quantityPerUnit: b.quantityPerUnit, costSharePercent: b.costSharePercent || 0 })),
    };
    const call = this.id ? this.bomApi.update(this.id, payload) : this.bomApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/manufacturing/boms']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save bill of materials'),
    });
  }
}
