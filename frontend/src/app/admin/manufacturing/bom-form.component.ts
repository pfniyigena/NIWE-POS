import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BomApiService } from '../../core/api/bom-api.service';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { Product, Routing } from '../../core/models';

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
      <h3 class="mb-3">{{ id ? 'Edit bill of materials' : 'New bill of materials' }}</h3>
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
                type="number"
                class="form-control form-control-sm"
                style="width:100px; margin-left:auto;"
                min="1"
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
        this.selectedProduct = { id: b.productId, name: b.productName } as Product;
        this.name = b.name ?? '';
        this.routingId = b.routingId;
        this.active = b.active;
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
    };
    const call = this.id ? this.bomApi.update(this.id, payload) : this.bomApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/manufacturing/boms']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save bill of materials'),
    });
  }
}
