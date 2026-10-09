import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { AppsApiService } from '../core/api/apps-api.service';
import { Brand, Category, TaxRateOption } from '../core/models';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Edit product' : 'New product' }}</h3>
        <div class="flex gap-2" *ngIf="id">
          <!-- Fourier: "Add view on product where we can show current quantity on that product,
               Stock movement, etc" — the dedicated read-only view screen itself, which embeds this
               same stock movement history (defaulted to the last 30 days) alongside the product's
               current stock/price/etc, rather than only this report link below. -->
          <a class="btn btn-outline-secondary" [routerLink]="['/admin/products', id]">View product</a>
          <!-- Fourier: "can we have Stock Movement History for a specific product (and it can be
               viewed on product...)" — jumps into the full Inventory report already filtered to
               this one product and run, for a wider date range or export than the view screen's
               own embedded table offers. -->
          <a
            class="btn btn-outline-secondary"
            routerLink="/admin/reports/stock-movement"
            [queryParams]="{ productId: id, productName: name }"
          >
            📊 Stock movement history
          </a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">SKU</label>
          <input class="form-control" name="sku" [(ngModel)]="sku" />
        </div>
        <div class="form-group">
          <label class="form-label">Barcode</label>
          <div class="flex gap-2" style="align-items:center;">
            <input class="form-control" name="barcode" [(ngModel)]="barcode" />
            <!-- Fourier: "barcode/label generation for products that don't already carry a
                 manufacturer barcode" — only offered once the product is saved (there's an id to
                 generate against) and only while the field is actually blank; clearing an existing
                 value and saving is how an admin opts back into generating a fresh one. -->
            <button
              type="button"
              class="btn btn-sm btn-outline-secondary"
              *ngIf="id && !barcode"
              [disabled]="generatingBarcode"
              (click)="generateBarcode()"
            >
              {{ generatingBarcode ? 'Generating…' : 'Generate' }}
            </button>
            <a
              *ngIf="id && barcode"
              class="btn btn-sm btn-outline-secondary"
              [routerLink]="['/admin/products/labels']"
              [queryParams]="{ ids: id }"
              target="_blank"
            >
              Print label
            </a>
          </div>
          <div class="alert alert-danger" *ngIf="barcodeError" style="margin-top:0.5rem;">{{ barcodeError }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">Code</label>
          <input class="form-control" name="code" [(ngModel)]="code" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Optional second identifier — leave blank if you don't use one. If set, it must be unique across products.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Icon</label>
          <div class="flex gap-2" style="align-items:center;">
            <img *ngIf="iconDataUrl" [src]="iconDataUrl" alt="Product icon" style="max-width:80px; max-height:80px; border:1px solid #444; border-radius:4px; object-fit:cover;" />
            <span class="text-muted" style="font-size:0.85rem;" *ngIf="!iconDataUrl">No icon uploaded</span>
          </div>
          <input class="form-control" type="file" accept="image/*" (change)="onIconSelected($event)" style="margin-top:0.5rem;" />
          <div class="alert alert-danger" *ngIf="iconError" style="margin-top:0.5rem;">{{ iconError }}</div>
          <button type="button" class="btn btn-sm btn-outline-secondary" style="margin-top:0.5rem;" *ngIf="iconDataUrl" (click)="removeIcon()">
            Remove icon
          </button>
        </div>
        <div class="form-group">
          <label class="form-label">Description</label>
          <textarea class="form-control" name="description" rows="3" [(ngModel)]="description"></textarea>
          <!-- Fourier: "On Invoice Line add product description text (by default it should pick
               the product description)" — this is that default; an invoice line still starts out
               editable, so changing it there never rewrites what's saved here. -->
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Optional — the default description text a new Invoice line for this product starts with.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Category</label>
          <select class="form-select" name="categoryId" [(ngModel)]="categoryId">
            <option [ngValue]="null">— None —</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Brand</label>
          <select class="form-select" name="brandId" [(ngModel)]="brandId">
            <option [ngValue]="null">— None —</option>
            <option *ngFor="let b of brands" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="brands.length === 0">
            No brands yet — add one under Inventory &gt; Brands first.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Tax rate</label>
          <select class="form-select" name="taxRateId" [(ngModel)]="taxRateId">
            <option [ngValue]="null">— None (tax-exempt) —</option>
            <option *ngFor="let t of activeTaxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Unit price</label>
          <input class="form-control" type="number" step="0.01" min="0" name="unitPrice" [(ngModel)]="unitPrice" required />
        </div>
        <div class="form-group">
          <label class="form-label">Cost price</label>
          <input class="form-control" type="number" step="0.01" min="0" name="costPrice" [(ngModel)]="costPrice" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            What this product costs the store — not shown to customers, used only for the admin Profit reports.
          </div>
        </div>
        <div class="form-group" *ngIf="efrisInstalled">
          <label class="form-label">EFRIS commodity code</label>
          <input class="form-control" name="unspCode" [(ngModel)]="unspCode" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The UNSPSC-style tariff code EFRIS expects on an invoice line ("unsp_code" for EFRIS Rwanda,
            "classification_code" for EFRIS Chad). Leave blank if you don't submit to EFRIS.
          </div>
        </div>
        <!-- Task: "if EFRIS Chad is set then call configured with the shared sample request" — Chad's
             invoice line needs item_nature (goods vs service), which nothing else in this app tracks.
             Only shown once EFRIS Chad is installed, same gating convention as the commodity code field
             above. -->
        <div class="form-group" *ngIf="chadInstalled">
          <label class="form-label">
            <input type="checkbox" name="service" [(ngModel)]="service" /> This is a service, not a physical good
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Feeds EFRIS Chad's item_nature on an invoice line. Leave unchecked for a physical product.
          </div>
        </div>
        <!-- Fourier: "Add track stock attribute on Product" — off makes this product always
             sellable, like a service or made-to-order item, never gated or moved by stock count
             (see the backend's Product.trackStock javadoc for exactly where this is checked). -->
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="trackStock" [(ngModel)]="trackStock" /> Track stock for this product
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Uncheck for a service or made-to-order item — it can always be sold, and selling it never changes a stock count.
          </div>
        </div>
        <div class="form-group" *ngIf="trackStock">
          <label class="form-label">Stock quantity</label>
          <input class="form-control" type="number" min="0" name="stockQuantity" [(ngModel)]="stockQuantity" required />
        </div>
        <div class="form-group" *ngIf="trackStock">
          <label class="form-label">Reorder level (optional)</label>
          <input class="form-control" type="number" min="0" name="reorderLevel" [(ngModel)]="reorderLevel" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            When sellable stock plus open purchase orders falls to this level, the product appears on Inventory &gt; Reorder.
          </div>
        </div>
        <div class="form-group" *ngIf="trackStock && reorderLevel !== null && reorderLevel !== undefined">
          <label class="form-label">Maximum stock level (optional)</label>
          <input class="form-control" type="number" min="0" name="maxStockLevel" [(ngModel)]="maxStockLevel" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">Suggested order quantity brings stock back up to this level (defaults to twice the reorder level).</div>
        </div>
        <div class="form-group" *ngIf="trackStock">
          <label class="form-label">Batch / serial tracking</label>
          <select class="form-control" name="trackingMode" [(ngModel)]="trackingMode">
            <option value="NONE">None — plain quantity</option>
            <option value="BATCH">Batches with expiry date (sold earliest-expiry-first)</option>
            <option value="SERIAL">Serial numbers (one per unit)</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Batch and serial numbers are entered when you receive a purchase order or add stock by adjustment, and follow the stock through transfers and sales.
            Stock already on hand stays untracked until it is received or adjusted in with its batch or serials.
          </div>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active (visible on terminal)
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/products">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class ProductFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  sku = '';
  barcode = '';
  code = '';
  iconDataUrl: string | null = null;
  description = '';
  iconError: string | null = null;
  categoryId: number | null = null;
  brandId: number | null = null;
  taxRateId: number | null = null;
  unitPrice: number | null = null;
  costPrice: number | null = null;
  unspCode = '';
  service = false;
  reorderLevel: number | null = null;
  trackingMode: 'NONE' | 'BATCH' | 'SERIAL' = 'NONE';
  maxStockLevel: number | null = null;
  stockQuantity = 0;
  trackStock = true;
  active = true;
  categories: Category[] = [];
  brands: Brand[] = [];
  /** Active rates only — an inactive rate can't newly be assigned, though a product keeps whichever rate it already has. */
  activeTaxRates: TaxRateOption[] = [];
  /**
   * Whether the EFRIS app is installed — gates the "EFRIS commodity code" field below so it
   * doesn't clutter every store's product form with an input only an EFRIS-installed store ever
   * needs. The value itself (`unspCode`) is still loaded/submitted unchanged either way — hiding
   * the field only affects whether it's *shown*, never whether a previously-set value survives a
   * save while EFRIS happens to be uninstalled.
   */
  efrisInstalled = false;
  /** Task: "if EFRIS Chad is set then call configured with the shared sample request" — gates the "This is a service" field below, same convention as efrisInstalled above. */
  chadInstalled = false;
  error: string | null = null;
  generatingBarcode = false;
  barcodeError: string | null = null;

  /** Keeps a huge base64 payload out of the request when the file is unreasonably large — there's no server-side resizing here. Same limit as BusinessDetailsComponent's logo. */
  private readonly maxIconBytes = 2 * 1024 * 1024;

  constructor(
    private catalogApi: CatalogApiService,
    private taxRateApi: TaxRateApiService,
    private appsApi: AppsApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.catalogApi.listCategories().subscribe((c) => (this.categories = c));
    this.catalogApi.listBrands().subscribe((b) => (this.brands = b));
    this.taxRateApi.listAll().subscribe((rates) => (this.activeTaxRates = rates.filter((r) => r.active)));
    this.appsApi.list().subscribe({
      next: (apps) => {
        this.efrisInstalled = apps.some((a) => (a.key === 'EFRIS_RW' || a.key === 'EFRIS_TD') && a.installed);
        this.chadInstalled = apps.find((a) => a.key === 'EFRIS_TD')?.installed ?? false;
      },
      error: () => {
        this.efrisInstalled = false;
        this.chadInstalled = false;
      },
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    // A dedicated single-product fetch (Task: "check the loading of 100000 products") — this used
    // to load the ENTIRE product list and find this one by id, which stopped making sense once
    // that list became paged (the product being edited might not even be on page one).
    this.catalogApi.getProduct(this.id).subscribe({
      next: (found) => {
        this.name = found.name;
        this.sku = found.sku ?? '';
        this.barcode = found.barcode ?? '';
        this.code = found.code ?? '';
        this.iconDataUrl = found.iconDataUrl;
        this.description = found.description ?? '';
        this.categoryId = found.categoryId;
        this.brandId = found.brandId;
        this.taxRateId = found.taxRateId;
        this.unitPrice = found.unitPrice;
        this.costPrice = found.costPrice;
        this.unspCode = found.unspCode ?? '';
        this.service = found.service;
        this.reorderLevel = found.reorderLevel ?? null;
        this.trackingMode = found.trackingMode ?? 'NONE';
        this.maxStockLevel = found.maxStockLevel ?? null;
        this.stockQuantity = found.stockQuantity;
        this.trackStock = found.trackStock;
        this.active = found.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load product'),
    });
  }

  onIconSelected(event: Event): void {
    this.iconError = null;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > this.maxIconBytes) {
      this.iconError = 'Icon image is too large (max 2MB) — choose a smaller file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => (this.iconDataUrl = reader.result as string);
    reader.onerror = () => (this.iconError = 'Failed to read the selected file');
    reader.readAsDataURL(file);
  }

  removeIcon(): void {
    this.iconDataUrl = null;
  }

  generateBarcode(): void {
    if (!this.id) return;
    this.barcodeError = null;
    this.generatingBarcode = true;
    this.catalogApi.generateBarcode(this.id).subscribe({
      next: (product) => {
        this.generatingBarcode = false;
        this.barcode = product.barcode ?? '';
      },
      error: (err) => {
        this.generatingBarcode = false;
        this.barcodeError = err?.error?.message ?? 'Failed to generate a barcode';
      },
    });
  }

  submit(): void {
    this.error = null;
    const payload = {
      name: this.name,
      sku: this.sku || null,
      barcode: this.barcode || null,
      code: this.code || null,
      iconDataUrl: this.iconDataUrl,
      description: this.description || null,
      unitPrice: this.unitPrice ?? 0,
      costPrice: this.costPrice ?? 0,
      unspCode: this.unspCode || null,
      service: this.service,
      trackingMode: this.trackStock ? this.trackingMode : 'NONE',
      reorderLevel: this.trackStock && this.reorderLevel !== null && (this.reorderLevel as unknown) !== '' ? Number(this.reorderLevel) : null,
      maxStockLevel: this.trackStock && this.reorderLevel !== null && this.maxStockLevel !== null && (this.maxStockLevel as unknown) !== '' ? Number(this.maxStockLevel) : null,
      stockQuantity: this.stockQuantity,
      trackStock: this.trackStock,
      active: this.active,
      categoryId: this.categoryId,
      brandId: this.brandId,
      taxRateId: this.taxRateId,
    };
    const call = this.id
      ? this.catalogApi.updateProduct(this.id, payload)
      : this.catalogApi.createProduct(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/products']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save product'),
    });
  }
}
