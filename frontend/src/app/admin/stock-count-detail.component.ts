import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockCountApiService } from '../core/api/stock-count-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { AuthService } from '../core/auth.service';
import { Product, StockCount, StockCountLine, WarehouseLocation } from '../core/models';

/**
 * Admin > Inventory > Stock counts > one count. While IN_PROGRESS the counted column is editable
 * (saved in bulk), products can be added to the sheet (e.g. items the system shows at zero) and
 * lines removed; SUBMITTED waits for sign-off (ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT), which posts
 * each difference as a RECOUNT stock adjustment.
 */
@Component({
  selector: 'app-stock-count-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="count as c">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3>{{ c.countNumber }}</h3>
            <div class="text-muted" style="font-size:0.85rem;">
              {{ c.warehouseName }} · {{ c.categoryName || 'All categories' }} · started {{ c.createdAt | date: 'medium' }}
              <span *ngIf="c.approvedAt"> · approved {{ c.approvedAt | date: 'medium' }}{{ c.approvedBy ? ' by ' + c.approvedBy : '' }}</span>
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="c.notes">{{ c.notes }}</div>
          </div>
          <div>
            <strong>{{ statusLabel(c) }}</strong>
          </div>
        </div>

        <div class="flex gap-4 mb-3" style="flex-wrap:wrap;">
          <div class="stat-card"><div class="stat-label">Lines</div><div class="stat-value" style="font-size:1.1rem;">{{ c.lineCount }}</div></div>
          <div class="stat-card"><div class="stat-label">Counted</div><div class="stat-value" style="font-size:1.1rem;">{{ c.countedCount }}</div></div>
          <div class="stat-card"><div class="stat-label">Differences</div><div class="stat-value" style="font-size:1.1rem;">{{ c.differenceCount }}</div></div>
          <div class="stat-card"><div class="stat-label">Variance value</div><div class="stat-value" style="font-size:1.1rem;">{{ c.varianceValue | number: '1.2-2' }}</div></div>
        </div>

        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <button class="btn btn-outline-primary" type="button" (click)="print()">Print sheet</button>
          <ng-container *ngIf="c.status === 'IN_PROGRESS'">
            <button class="btn btn-outline-primary" type="button" [disabled]="busy" (click)="save()">Save counts</button>
            <button class="btn btn-primary" type="button" [disabled]="busy" (click)="submit()">Submit for approval</button>
            <button class="btn btn-outline-danger" type="button" [disabled]="busy" (click)="cancel()">Cancel count</button>
          </ng-container>
          <ng-container *ngIf="c.status === 'SUBMITTED'">
            <button class="btn btn-primary" type="button" *ngIf="canApprove" [disabled]="busy" (click)="approve()">Approve &amp; post differences</button>
            <button class="btn btn-outline-primary" type="button" [disabled]="busy" (click)="reopen()">Reopen for counting</button>
            <button class="btn btn-outline-danger" type="button" [disabled]="busy" (click)="cancel()">Cancel count</button>
          </ng-container>
          <a class="btn btn-link" routerLink="/admin/stock-counts">Back</a>
        </div>

        <div class="form-group mb-3" style="position:relative; max-width:520px;" *ngIf="c.status === 'IN_PROGRESS'">
          <label class="form-label">Add a product to the sheet</label>
          <div class="flex gap-2">
            <input class="form-control" placeholder="Search by name, SKU or code" [(ngModel)]="productSearch" (input)="onProductSearch()" />
            <select class="form-select" [(ngModel)]="addLocationId" name="addLocationId" style="max-width:200px;">
              <option *ngFor="let l of locations" [ngValue]="l.id">{{ l.name }}</option>
            </select>
          </div>
          <div class="dropdown-panel" *ngIf="productResults.length > 0">
            <div class="dropdown-item" *ngFor="let p of productResults" (click)="addProduct(p)">{{ p.name }}</div>
          </div>
        </div>

        <table>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Product</th>
              <th>Location</th>
              <th class="text-end">System qty</th>
              <th class="text-end" style="width:130px;">Counted</th>
              <th class="text-end">Variance</th>
              <th class="text-end">Value</th>
              <th *ngIf="c.status === 'IN_PROGRESS'"></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of c.lines">
              <td>{{ l.sku }}</td>
              <td>{{ l.productName }}</td>
              <td>{{ l.locationName }}</td>
              <td class="text-end">{{ l.systemQuantity }}</td>
              <td class="text-end">
                <input *ngIf="c.status === 'IN_PROGRESS'; else fixed" class="form-control text-end" type="number" min="0"
                  [ngModel]="edits[l.id]" (ngModelChange)="edits[l.id] = $event" [name]="'q' + l.id" />
                <ng-template #fixed>{{ l.countedQuantity ?? '—' }}</ng-template>
              </td>
              <td class="text-end" [style.color]="variance(l) !== null && variance(l)! < 0 ? 'var(--danger-text)' : variance(l)! > 0 ? 'var(--success-text)' : 'inherit'">
                {{ variance(l) === null ? '' : (variance(l)! > 0 ? '+' : '') + variance(l) }}
              </td>
              <td class="text-end">{{ variance(l) !== null && l.unitCost !== null ? (variance(l)! * l.unitCost! | number: '1.2-2') : '' }}</td>
              <td *ngIf="c.status === 'IN_PROGRESS'"><button class="btn btn-sm btn-link" type="button" (click)="removeLine(l)">Remove</button></td>
            </tr>
          </tbody>
        </table>
      </ng-container>
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
export class StockCountDetailComponent implements OnInit {
  count: StockCount | null = null;
  edits: Record<number, number | null> = {};
  locations: WarehouseLocation[] = [];
  addLocationId: number | null = null;
  productSearch = '';
  productResults: Product[] = [];
  error: string | null = null;
  busy = false;
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private route: ActivatedRoute,
    private api: StockCountApiService,
    private catalogApi: CatalogApiService,
    private warehouseApi: WarehouseApiService,
    private auth: AuthService
  ) {}

  get canApprove(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN' || this.auth.hasPermission('APPROVE_STOCK_ADJUSTMENT');
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (c) => {
        this.apply(c);
        this.warehouseApi.listLocations(c.warehouseId).subscribe({
          next: (locs) => {
            this.locations = locs.filter((l) => l.active);
            if (this.locations.length > 0) this.addLocationId = this.locations[0].id;
          },
        });
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the count'),
    });
  }

  private apply(c: StockCount): void {
    this.count = c;
    this.edits = {};
    for (const l of c.lines) this.edits[l.id] = l.countedQuantity;
  }

  statusLabel(c: StockCount): string {
    return c.status === 'IN_PROGRESS' ? 'In progress' : c.status === 'SUBMITTED' ? 'Awaiting approval' : c.status === 'APPROVED' ? 'Approved' : 'Cancelled';
  }

  /** Live variance while editing; stored value once the count is locked. */
  variance(l: StockCountLine): number | null {
    const counted = this.count?.status === 'IN_PROGRESS' ? this.edits[l.id] : l.countedQuantity;
    if (counted === null || counted === undefined || (counted as unknown) === '') return null;
    return Number(counted) - l.systemQuantity;
  }

  private entries(): { lineId: number; countedQuantity: number | null }[] {
    return (this.count?.lines ?? []).map((l) => {
      const v = this.edits[l.id];
      return { lineId: l.id, countedQuantity: v === null || v === undefined || (v as unknown) === '' ? null : Number(v) };
    });
  }

  private run(call: () => ReturnType<StockCountApiService['get']>, failure: string): void {
    this.error = null;
    this.busy = true;
    call().subscribe({
      next: (c) => {
        this.busy = false;
        this.apply(c);
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? failure;
      },
    });
  }

  save(): void {
    if (!this.count) return;
    this.run(() => this.api.saveCounts(this.count!.id, this.entries()), 'Failed to save counts');
  }

  submit(): void {
    if (!this.count) return;
    const id = this.count.id;
    this.error = null;
    this.busy = true;
    this.api.saveCounts(id, this.entries()).subscribe({
      next: () => {
        this.busy = false;
        this.run(() => this.api.submit(id), 'Failed to submit the count');
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to save counts';
      },
    });
  }

  reopen(): void {
    if (this.count) this.run(() => this.api.reopen(this.count!.id), 'Failed to reopen the count');
  }

  approve(): void {
    if (!this.count) return;
    if (!confirm(`Approve ${this.count.countNumber}? Every counted difference will be posted as a stock adjustment (stock and accounting) and cannot be undone.`)) return;
    this.run(() => this.api.approve(this.count!.id), 'Failed to approve the count');
  }

  cancel(): void {
    if (!this.count) return;
    if (!confirm(`Cancel ${this.count.countNumber}? Nothing will be posted.`)) return;
    this.run(() => this.api.cancel(this.count!.id), 'Failed to cancel the count');
  }

  removeLine(l: StockCountLine): void {
    if (this.count) this.run(() => this.api.removeLine(this.count!.id, l.id), 'Failed to remove the line');
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
        next: (r) => (this.productResults = r.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  addProduct(p: Product): void {
    if (!this.count || !this.addLocationId) {
      this.error = 'Pick a location to add the product to';
      return;
    }
    const entered = this.edits; // keep unsaved entries across the reload
    this.productResults = [];
    this.productSearch = '';
    this.error = null;
    this.busy = true;
    this.api.addLine(this.count.id, p.id, this.addLocationId).subscribe({
      next: (c) => {
        this.busy = false;
        this.apply(c);
        for (const l of c.lines) if (entered[l.id] !== undefined && entered[l.id] !== null) this.edits[l.id] = entered[l.id];
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to add the product';
      },
    });
  }

  print(): void {
    if (!this.count) return;
    const c = this.count;
    const esc = (s: string | null) => (s ?? '').replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch] as string));
    const rows = c.lines
      .map((l) => `<tr><td>${esc(l.sku)}</td><td>${esc(l.productName)}</td><td>${esc(l.locationName)}</td><td class="r">${l.systemQuantity}</td><td class="r">${l.countedQuantity ?? ''}</td><td class="box"></td></tr>`)
      .join('');
    const w = window.open('', '_blank');
    if (!w) {
      this.error = 'Allow pop-ups to print the count sheet';
      return;
    }
    w.document.write(`<!doctype html><html><head><title>${esc(c.countNumber)}</title><style>
      body{font-family:Arial,sans-serif;font-size:12px;margin:16px} h2{margin:0 0 4px} table{border-collapse:collapse;width:100%;margin-top:12px}
      th,td{border:1px solid #444;padding:4px 6px;text-align:left} .r{text-align:right} .box{width:90px}
    </style></head><body><h2>Stock count ${esc(c.countNumber)}</h2>
    <div>${esc(c.warehouseName)} · ${esc(c.categoryName || 'All categories')} · ${esc(new Date(c.createdAt).toLocaleString())}</div>
    <table><thead><tr><th>SKU</th><th>Product</th><th>Location</th><th class="r">System qty</th><th class="r">Counted</th><th>Recount</th></tr></thead><tbody>${rows}</tbody></table>
    </body></html>`);
    w.document.close();
    w.focus();
    w.print();
  }
}
