import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TrackingApiService, StockBatchRow, StockSerialRow } from '../core/api/tracking-api.service';
import { ShellComponent } from '../shared/shell.component';
import { DatePickerComponent } from '../shared/date-picker.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { ReportApiService, ReportFilter } from '../core/api/report-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { Product, ProductStockByLocation } from '../core/models';

/** One row of the Stock Movement History report (see the backend's ReportService.StockMovementRow) — only the columns worth repeating here, since Product/SKU/Category are already this one product. */
interface StockMovementRow {
  occurredAt: string;
  type: string;
  quantityDelta: number;
  stockBefore: number;
  stockAfter: number;
  reference: string | null;
  note: string | null;
}

/**
 * Fourier: "Add view on product where we can show current quantity on that product, Stock
 * movement, etc" — a read-only companion to `ProductFormComponent` (which is edit-only), at
 * `/admin/products/:id`. Shows the product's own current state (stock on hand, price, cost,
 * category/brand/tax, active status) plus its own recent Stock Movement History, embedded right
 * here rather than making the admin jump out to the generic Inventory report just to see "how did
 * this product's stock get here" — reuses `ReportApiService.run('stock-movement', …)` with
 * `productId` fixed to this product, same endpoint and default 30-day range
 * `ReportViewerComponent` itself defaults to. "View full report" still jumps to that generic
 * screen (with a wider date range, export, etc.) via the same `?productId=&productName=` link
 * `ProductFormComponent`'s own "Stock movement history" button already uses.
 */
@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="flex justify-between align-center mb-3" *ngIf="product as p">
        <h3 style="margin-bottom:0;">{{ p.name }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" [routerLink]="['/admin/products', p.id, 'edit']">Edit</a>
          <a class="btn btn-outline-primary" *ngIf="!p.variantParentId" [routerLink]="['/admin/products', p.id, 'variants']">Variants</a>
          <a class="btn btn-link" routerLink="/admin/products">Back to products</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="product as p">
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <img *ngIf="p.iconDataUrl" [src]="p.iconDataUrl" alt="" style="width:64px; height:64px; object-fit:cover; border-radius:6px;" />
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><strong>SKU:</strong> {{ p.sku || '—' }}</div>
            <div><strong>Barcode:</strong> {{ p.barcode || '—' }}</div>
            <div *ngIf="p.variantParentId"><strong>Variant of:</strong> <a [routerLink]="['/admin/products', p.variantParentId]">template</a> ({{ p.variantLabel }})</div>
            <div *ngIf="p.variantAttributes"><strong>Variant options:</strong> {{ p.variantAttributes }}</div>
            <div><strong>Code:</strong> {{ p.code || '—' }}</div>
            <div>
              <strong>Category:</strong>
              <span class="color-swatch" *ngIf="p.categoryColor" [style.background]="p.categoryColor"></span>
              {{ p.categoryName || '—' }}
            </div>
            <div><strong>Brand:</strong> {{ p.brandName || '—' }}</div>
            <div>
              <strong>Tax:</strong>
              <span *ngIf="p.taxRateName">{{ p.taxRateName }} ({{ p.taxRatePercent | number: '1.0-2' }}%<ng-container *ngIf="p.taxRateInclusive">, incl.</ng-container>)</span>
              <span *ngIf="!p.taxRateName">— exempt —</span>
            </div>
            <div><strong>Price:</strong> {{ p.unitPrice | number: '1.2-2' }}</div>
            <div><strong>Cost:</strong> {{ p.costPrice | number: '1.2-2' }}</div>
            <div>
              <strong>Current stock:</strong>
              <span *ngIf="p.trackStock">{{ p.stockQuantity }}</span>
              <span class="text-muted" *ngIf="!p.trackStock" title="Always sellable — stock isn't tracked for this product">Not tracked</span>
            </div>
            <div><strong>Status:</strong> <span class="badge badge-success" *ngIf="p.active">Active</span><span class="badge badge-secondary" *ngIf="!p.active">Inactive</span></div>
          </div>
        </div>
        <div class="text-muted mt-2" *ngIf="p.description">{{ p.description }}</div>
      </div>

      <!-- Multi-warehouse phase 1: where this product's stock actually sits. Read-only — to move it, use Admin > Stock transfers. -->
      <div class="card mb-3" style="padding:1rem;" *ngIf="stock as st">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap;">
          <h4 style="margin-bottom:0;">Stock by location</h4>
          <div class="text-muted" style="font-size:0.85rem;">
            Sellable: <strong>{{ st.sellableTotal }}</strong> &middot; Total on hand: <strong>{{ st.onHandTotal }}</strong>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>Warehouse</th>
              <th>Location</th>
              <th>Type</th>
              <th class="text-end">Quantity</th>
              <th>Counts as sellable?</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of st.locations">
              <td>{{ row.warehouseName }}</td>
              <td>{{ row.locationName }} <span class="badge badge-secondary" *ngIf="!row.active">Inactive</span></td>
              <td>{{ row.type || '—' }}</td>
              <td class="text-end">{{ row.quantity }}</td>
              <td>{{ row.sellable ? 'Yes' : 'No' }}</td>
            </tr>
            <tr *ngIf="st.locations.length === 0">
              <td colspan="5" class="text-muted">No locations yet.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Batch / serial tracking: what the stock is made of. Untracked stock (opening balances, returns) is not listed here. -->
      <div class="card mb-3" style="padding:1rem;" *ngIf="product?.trackingMode === 'BATCH'">
        <h4 class="mb-2">Batches</h4>
        <table>
          <thead>
            <tr>
              <th>Batch</th>
              <th>Expiry</th>
              <th class="text-end">Days left</th>
              <th>Where</th>
              <th class="text-end">Qty</th>
              <th>Source</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let b of batches">
              <td>{{ b.batchNumber || '—' }}</td>
              <td>{{ b.expiryDate || '—' }}</td>
              <td class="text-end" [style.color]="b.daysToExpiry !== null && b.daysToExpiry < 0 ? '#c0392b' : ''">{{ b.daysToExpiry === null ? '—' : b.daysToExpiry }}</td>
              <td>{{ b.inTransit ? 'In transit' : (b.warehouseName + ' / ' + b.locationName) }}</td>
              <td class="text-end">{{ b.quantity }}</td>
              <td>{{ b.source || '—' }}</td>
            </tr>
            <tr *ngIf="batches.length === 0">
              <td colspan="6" class="text-muted">No batches recorded yet — enter a batch when receiving a purchase order or adding stock by adjustment.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="product?.trackingMode === 'SERIAL'">
        <h4 class="mb-2">Serial numbers</h4>
        <table>
          <thead>
            <tr>
              <th>Serial</th>
              <th>Status</th>
              <th>Where</th>
              <th>Received</th>
              <th>Left stock</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let s of serials">
              <td>{{ s.serialNumber }}</td>
              <td>{{ s.status.replace('_', ' ') }}</td>
              <td>{{ s.locationName ? (s.warehouseName + ' / ' + s.locationName) : '—' }}</td>
              <td>{{ (s.receivedAt | date: 'yyyy-MM-dd') || '—' }}<span *ngIf="s.source"> · {{ s.source }}</span></td>
              <td>{{ s.soldAt ? ((s.soldAt | date: 'yyyy-MM-dd') + (s.soldRef ? ' · ' + s.soldRef : '')) : '—' }}</td>
            </tr>
            <tr *ngIf="serials.length === 0">
              <td colspan="5" class="text-muted">No serial numbers recorded yet.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex justify-between align-center mb-2">
        <h4 style="margin-bottom:0;">Stock movement</h4>
        <a
          class="btn btn-sm btn-outline-secondary"
          *ngIf="product"
          routerLink="/admin/reports/stock-movement"
          [queryParams]="{ productId: product.id, productName: product.name }"
        >
          View full report &rarr;
        </a>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="margin:0;">
            <label class="form-label">From</label>
            <app-date-picker [(ngModel)]="filter.from"></app-date-picker>
          </div>
          <div class="form-group" style="margin:0;">
            <label class="form-label">To</label>
            <app-date-picker [(ngModel)]="filter.to"></app-date-picker>
          </div>
          <button class="btn btn-primary" (click)="loadMovements()" [disabled]="loadingMovements">
            {{ loadingMovements ? 'Loading…' : 'Refresh' }}
          </button>
        </div>

        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Type</th>
              <th class="text-end">Qty change</th>
              <th class="text-end">Before</th>
              <th class="text-end">After</th>
              <th>Reference</th>
              <th>Note</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of movements">
              <td>{{ m.occurredAt | date: 'medium' }}</td>
              <td>{{ m.type }}</td>
              <!-- Fourier: ".text-success" was never actually defined in styles.scss (same gap
                   ".text-danger" itself had until Task "Show stock quantity on Product terminal"
                   fixed it) — rather than silently render in the default color, a positive delta
                   is left unstyled and only a negative one gets ".text-danger", which IS defined. -->
              <td class="text-end" [class.text-danger]="m.quantityDelta < 0">
                {{ m.quantityDelta > 0 ? '+' : '' }}{{ m.quantityDelta }}
              </td>
              <td class="text-end">{{ m.stockBefore }}</td>
              <td class="text-end">{{ m.stockAfter }}</td>
              <td>{{ m.reference || '—' }}</td>
              <td class="text-muted">{{ m.note || '—' }}</td>
            </tr>
            <tr *ngIf="!loadingMovements && movements.length === 0">
              <td colspan="7" class="text-muted">No stock movement for this product in this date range.</td>
            </tr>
            <tr *ngIf="loadingMovements">
              <td colspan="7" class="text-muted">Loading…</td>
            </tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class ProductDetailComponent implements OnInit {
  id!: number;
  product: Product | null = null;
  stock: ProductStockByLocation | null = null;
  batches: StockBatchRow[] = [];
  serials: StockSerialRow[] = [];
  error: string | null = null;

  movements: StockMovementRow[] = [];
  loadingMovements = false;
  /** Same default 30-day range ReportViewerComponent itself opens with — see that component's own comment. */
  filter: ReportFilter = { from: null, to: null, registerId: null, categoryId: null, productId: null };

  constructor(
    private route: ActivatedRoute,
    private catalogApi: CatalogApiService,
    private reportApi: ReportApiService,
    private warehouseApi: WarehouseApiService,
    private trackingApi: TrackingApiService
  ) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.filter.productId = this.id;

    const today = new Date();
    const past = new Date();
    past.setDate(past.getDate() - 30);
    this.filter.to = today.toISOString().slice(0, 10);
    this.filter.from = past.toISOString().slice(0, 10);

    this.catalogApi.getProduct(this.id).subscribe({
      next: (p) => {
        this.product = p;
        if (p.trackingMode === 'BATCH') {
          this.trackingApi.batches(this.id).subscribe({ next: (rows) => (this.batches = rows), error: () => (this.batches = []) });
        } else if (p.trackingMode === 'SERIAL') {
          this.trackingApi.serials(this.id).subscribe({ next: (rows) => (this.serials = rows), error: () => (this.serials = []) });
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load product'),
    });

    this.warehouseApi.stockByLocation(this.id).subscribe({
      next: (s) => (this.stock = s),
      // Not essential to the page — the rest of it still works if this panel can't load.
      error: () => (this.stock = null),
    });

    this.loadMovements();
  }

  loadMovements(): void {
    this.loadingMovements = true;
    this.reportApi.run('stock-movement', this.filter).subscribe({
      next: (rows) => {
        this.loadingMovements = false;
        this.movements = rows as StockMovementRow[];
      },
      error: (err) => {
        this.loadingMovements = false;
        this.error = err?.error?.message ?? 'Failed to load stock movement history';
      },
    });
  }
}
