import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { DatePickerComponent } from '../shared/date-picker.component';
import { PurchaseOrderApiService } from '../core/api/purchase-order-api.service';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { SupplierPriceApiService } from '../core/api/supplier-price-api.service';
import { Product, Supplier, Warehouse } from '../core/models';

interface DraftLine {
  productId: number;
  productName: string;
  quantityOrdered: number;
  unitCost: number;
  /** Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — null/0 means no discount; only entered when discountMode is PER_LINE. */
  discountPercent: number | null;
  /** Fourier: "add tax on line ... new purchase form" — snapshotted from the product at add-line time, purely for this form's own live preview; display-only, same as every other document type (no per-line tax override exists anywhere in this app). */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxInclusive: boolean;
}

/**
 * Admin > Inventory > Purchase Orders > New/Edit — Task: "Inventory depth ... purchase orders and
 * supplier records." Only ever creates/edits a DRAFT (see PurchaseOrderStatus's backend javadoc);
 * sending, cancelling and receiving all happen on the detail screen instead.
 *
 * Pre-fills from the dashboard's low-stock list (Task: "the dashboard already flags low stock, but
 * nothing acts on it") via two optional query params: `productId` (the "Reorder" link on one
 * low-stock row) adds just that product, `fromLowStock=1` (the dashboard's "Create purchase order
 * for low-stock items" button) adds every currently low-stock product at once. Either way this is
 * still a single PO for a single supplier — a low-stock list can span products from more than one
 * usual supplier, so the admin picks one supplier for the whole order and adjusts lines/quantities
 * before saving, same as they would for any manually-built PO.
 *
 * Task: "You can add new Supplier from New purchase order form" — an inline "+ New supplier"
 * panel next to the picker, so placing an order with a supplier that isn't set up yet doesn't
 * mean losing this draft's lines to go fill out the full Admin > Suppliers form and come back.
 * Deliberately a smaller field set than that full form (name/contact/phone/email/tax ID, no
 * address/notes) — a quick add to unblock this PO, not a replacement for it; the supplier can
 * still be edited with the rest of its details later from Admin > Suppliers.
 */
@Component({
  selector: 'app-purchase-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit purchase order' : 'New purchase order' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning" *ngIf="prefilledFromLowStock">
        Pre-filled with {{ lines.length }} low-stock item{{ lines.length === 1 ? '' : 's' }} — pick a supplier and adjust quantities/costs before saving.
      </div>

      <div class="flex gap-2 mb-3">
        <div class="grow">
          <label class="form-label">Supplier</label>
          <div class="flex gap-2">
            <select class="form-select" name="supplierId" [(ngModel)]="supplierId" (ngModelChange)="onSupplierChange()" required [disabled]="showNewSupplier">
              <option [ngValue]="null" disabled>Select a supplier…</option>
              <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
            </select>
            <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewSupplier" (click)="openNewSupplier()">
              + New supplier
            </button>
          </div>
        </div>
        <div class="grow">
          <label class="form-label">Expected date (optional)</label>
          <app-date-picker name="expectedDate" [(ngModel)]="expectedDate"></app-date-picker>
        </div>
      </div>

      <!-- Multi-warehouse phase 2 — only shown once the store actually has more than one active
           warehouse. Leave on the primary warehouse to receive centrally (hub) and Stock Transfer
           onwards later; pick a branch to receive straight into it. -->
      <div class="form-group mb-3" *ngIf="warehouses.length > 1">
        <label class="form-label">Receive into warehouse</label>
        <select class="form-select" name="receivingWarehouseId" [(ngModel)]="receivingWarehouseId">
          <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}{{ w.primary ? ' (primary)' : '' }}</option>
        </select>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
          Received stock lands in this warehouse's Receiving location; move it to a Sales location with a Stock Transfer.
        </div>
      </div>

      <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewSupplier">
        <div class="alert alert-danger" *ngIf="newSupplierError">{{ newSupplierError }}</div>
        <div class="flex gap-2 mb-2">
          <div class="grow">
            <label class="form-label">Name</label>
            <input class="form-control" name="newSupplierName" [(ngModel)]="newSupplierName" placeholder="Supplier name" />
          </div>
          <div class="grow">
            <label class="form-label">Contact person</label>
            <input class="form-control" name="newSupplierContact" [(ngModel)]="newSupplierContact" />
          </div>
        </div>
        <div class="flex gap-2 mb-2">
          <div class="grow">
            <label class="form-label">Phone</label>
            <input class="form-control" name="newSupplierPhone" [(ngModel)]="newSupplierPhone" />
          </div>
          <div class="grow">
            <label class="form-label">Email</label>
            <input class="form-control" type="email" name="newSupplierEmail" [(ngModel)]="newSupplierEmail" />
          </div>
        </div>
        <div class="flex gap-2 mb-2">
          <!-- Fourier: "Add Tax ID on quick add supplier (Purchase form)" — same optional,
               purely-informational field as the full Admin > Suppliers form (Supplier.taxId); a
               supplier quick-added here still only gets name/contact/phone/email/tax ID, not the
               full form's address/notes — see this component's own class javadoc for why. -->
          <div class="grow">
            <label class="form-label">Tax ID</label>
            <input class="form-control" name="newSupplierTaxId" [(ngModel)]="newSupplierTaxId" />
          </div>
          <div class="grow"></div>
        </div>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary btn-sm" (click)="saveNewSupplier()">Save supplier</button>
          <button type="button" class="btn btn-link btn-sm" (click)="cancelNewSupplier()">Cancel</button>
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Notes</label>
        <textarea class="form-control" name="notes" rows="2" [(ngModel)]="notes"></textarea>
      </div>

      <!-- Fourier: "Except POS other module can have global discount ... Either on line or
           global" — shown only when System settings has the store's discount mode set to
           GLOBAL; one discount for the whole order instead of a per-line input below.
           Fourier: "Global discount should be a fixed amount or percentage with percentage as
           the default" — entered either way, percentage selected by default. -->
      <div class="form-group mb-3" *ngIf="discountMode === 'GLOBAL'">
        <label class="form-label">Discount (whole order)</label>
        <div class="flex gap-2" style="align-items:center;">
          <div class="btn-group" role="group" style="flex-shrink:0;">
            <button type="button" class="btn btn-sm" [class.btn-primary]="discountType === 'PERCENT'" [class.btn-outline-secondary]="discountType !== 'PERCENT'" (click)="discountType = 'PERCENT'">Percentage</button>
            <button type="button" class="btn btn-sm" [class.btn-primary]="discountType === 'FIXED_AMOUNT'" [class.btn-outline-secondary]="discountType !== 'FIXED_AMOUNT'" (click)="discountType = 'FIXED_AMOUNT'">Fixed amount</button>
          </div>
          <input class="form-control" style="max-width:160px;" type="number" min="0" max="100" step="0.01" *ngIf="discountType === 'PERCENT'" name="headerDiscountPercent" [(ngModel)]="discountPercent" placeholder="%" />
          <input class="form-control" style="max-width:160px;" type="number" min="0" step="0.01" *ngIf="discountType === 'FIXED_AMOUNT'" name="headerDiscountFixedAmount" [(ngModel)]="discountFixedAmount" placeholder="Amount" />
        </div>
      </div>

      <h4 class="mb-2">Line items</h4>
      <div class="flex gap-2 mb-2" style="position:relative;">
        <input
          class="form-control"
          placeholder="Search products by name, SKU or code to add a line"
          [(ngModel)]="productSearch"
          (input)="onProductSearch()"
        />
        <div class="dropdown-panel" *ngIf="productResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of productResults" (click)="addLine(p)">
            {{ p.name }} <span class="text-muted">— stock {{ p.stockQuantity }}, cost {{ p.costPrice | number: '1.2-2' }}</span>
          </div>
        </div>
      </div>

      <table class="table-sm mb-3">
        <thead>
          <tr>
            <th>Product</th>
            <th class="text-end">Quantity</th>
            <th class="text-end">Unit cost</th>
            <th class="text-end" *ngIf="discountMode === 'PER_LINE'">Discount %</th>
            <!-- Fourier: "add tax on line ... new purchase form" — display-only, computed from the product's own assigned tax rate (snapshotted at add-line time); no override here. -->
            <th class="text-end">Tax</th>
            <th class="text-end">Line total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let line of lines; let i = index">
            <td>{{ line.productName }}</td>
            <td class="text-end">
              <input type="number" class="form-control form-control-sm" style="width:90px; margin-left:auto;" min="1" [(ngModel)]="line.quantityOrdered" [name]="'qty' + i" />
            </td>
            <td class="text-end">
              <input type="number" class="form-control form-control-sm" style="width:100px; margin-left:auto;" min="0" step="0.01" [(ngModel)]="line.unitCost" [name]="'cost' + i" />
            </td>
            <td class="text-end" *ngIf="discountMode === 'PER_LINE'">
              <input type="number" class="form-control form-control-sm" style="width:90px; margin-left:auto;" min="0" max="100" step="0.01" [(ngModel)]="line.discountPercent" [name]="'discount' + i" />
            </td>
            <td class="text-end">{{ lineTax(line) | number: '1.2-2' }}</td>
            <td class="text-end">{{ lineTotal(line) | number: '1.2-2' }}</td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeLine(i)">Remove</button></td>
          </tr>
          <tr *ngIf="lines.length === 0">
            <td [attr.colspan]="discountMode === 'PER_LINE' ? 7 : 6" class="text-muted">No lines yet — search for a product above to add one.</td>
          </tr>
        </tbody>
      </table>

      <!-- Fourier: "add Totals on all those form while recording" — live-computed, same stat-tile convention the detail screen uses after saving. -->
      <div class="stat-grid mb-3" *ngIf="lines.length > 0">
        <div class="stat-tile">
          <div class="stat-label">Subtotal</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ subtotalAmount() | number: '1.2-2' }}</div>
        </div>
        <div class="stat-tile">
          <div class="stat-label">Tax</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ taxAmount() | number: '1.2-2' }}</div>
        </div>
        <div class="stat-tile" *ngIf="discountAmount()">
          <div class="stat-label">Discount</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ discountAmount() | number: '1.2-2' }}</div>
        </div>
        <div class="stat-tile">
          <div class="stat-label">Grand total</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ grandTotal() | number: '1.2-2' }}</div>
        </div>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save draft</button>
        <a class="btn btn-link" routerLink="/admin/purchase-orders">Cancel</a>
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
export class PurchaseOrderFormComponent implements OnInit {
  id: number | null = null;
  supplierId: number | null = null;
  expectedDate: string | null = null;
  notes = '';
  lines: DraftLine[] = [];
  suppliers: Supplier[] = [];
  /** Multi-warehouse phase 2 — active warehouses only; the picker is hidden unless there is more than one. A non-admin without access to the warehouse list simply never sees it (the call fails quietly). */
  warehouses: Warehouse[] = [];
  receivingWarehouseId: number | null = null;
  error: string | null = null;

  productSearch = '';
  productResults: Product[] = [];
  prefilledFromLowStock = false;

  /**
   * Fourier: "Same for discount ... it will be per line" then "Except POS other module can
   * have global discount ... Either on line or global" — which discount control this form
   * shows, set store-wide in System settings (see StoreSettings.discountMode's backend
   * javadoc).
   */
  discountMode: 'NONE' | 'PER_LINE' | 'GLOBAL' = 'NONE';
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single percentage for the whole order, only entered/shown when discountMode is GLOBAL. */
  discountPercent: number | null = null;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — which control is shown for the GLOBAL discount above; percentage by default. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT' = 'PERCENT';
  /** The flat currency amount, only entered/shown when discountType is FIXED_AMOUNT. */
  discountFixedAmount: number | null = null;

  // Task: "You can add new Supplier from New purchase order form" — inline quick-add panel state.
  showNewSupplier = false;
  newSupplierName = '';
  newSupplierContact = '';
  newSupplierPhone = '';
  newSupplierEmail = '';
  /** Fourier: "Add Tax ID on quick add supplier (Purchase form)". */
  newSupplierTaxId = '';
  newSupplierError: string | null = null;

  /** The chosen supplier's prices in force today, by product id — prefills unit costs. */
  private supplierPrices = new Map<number, number>();
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private purchaseOrderApi: PurchaseOrderApiService,
    private supplierApi: SupplierApiService,
    private catalogApi: CatalogApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private warehouseApi: WarehouseApiService,
    private supplierPriceApi: SupplierPriceApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.supplierApi.listActive().subscribe({
      next: (s) => (this.suppliers = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load suppliers'),
    });
    this.warehouseApi.listAll().subscribe({
      next: (all) => {
        this.warehouses = all.filter((w) => w.active);
        // New order: default to the primary warehouse. An edited order's own choice (loaded
        // below) is kept as is.
        if (this.receivingWarehouseId == null) {
          this.receivingWarehouseId = this.warehouses.find((w) => w.primary)?.id ?? null;
        }
      },
      error: () => (this.warehouses = []), // never blocks the form — just hides the picker
    });
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.discountMode = s.discountMode),
      error: () => (this.discountMode = 'NONE'), // never blocks the form — just hides the discount controls
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.purchaseOrderApi.getById(this.id).subscribe({
        next: (po) => {
          if (po.status !== 'DRAFT') {
            this.error = `${po.orderNumber} is ${po.status.replace('_', ' ').toLowerCase()} — only a draft can be edited.`;
            return;
          }
          this.supplierId = po.supplierId;
          this.onSupplierChange();
          this.expectedDate = po.expectedDate ? po.expectedDate.slice(0, 10) : null;
          this.notes = po.notes ?? '';
          this.receivingWarehouseId = po.receivingWarehouseId;
          this.discountPercent = po.discountPercent || null;
          this.discountType = po.discountType ?? 'PERCENT';
          this.discountFixedAmount = po.discountFixedAmount;
          this.lines = po.lines.map((l) => ({
            productId: l.productId as number,
            productName: l.productName,
            quantityOrdered: l.quantityOrdered,
            unitCost: l.unitCost,
            discountPercent: l.discountPercent,
            taxCode: l.taxCode,
            taxRatePercent: l.taxRatePercent,
            taxInclusive: l.taxInclusive,
          }));
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase order'),
      });
      return;
    }

    // Task: "the dashboard already flags low stock, but nothing acts on it" — pre-fill from either
    // a single-product "Reorder" link or the dashboard's bulk "Create purchase order" button.
    const productId = this.route.snapshot.queryParamMap.get('productId');
    const fromLowStock = this.route.snapshot.queryParamMap.get('fromLowStock');
    if (productId) {
      this.catalogApi.getProduct(Number(productId)).subscribe((p) => this.addLine(p, 10));
    } else if (fromLowStock) {
      this.catalogApi.listLowStock().subscribe((products) => {
        products.forEach((p) => this.addLine(p, 10));
        this.prefilledFromLowStock = products.length > 0;
      });
    }
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
        next: (results) => (this.productResults = results.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  /** defaultQty lets a low-stock prefill (see ngOnInit) suggest a top-up quantity instead of the usual "start at 1" for a manually-searched product. */
  addLine(p: Product, defaultQty = 1): void {
    const existing = this.lines.find((l) => l.productId === p.id);
    if (existing) {
      existing.quantityOrdered += defaultQty;
    } else {
      this.lines.push({
        productId: p.id,
        productName: p.name,
        quantityOrdered: defaultQty,
        unitCost: this.supplierPrices.get(p.id) ?? p.costPrice,
        discountPercent: null,
        taxCode: p.taxRateCode,
        taxRatePercent: p.taxRatePercent,
        taxInclusive: p.taxRateInclusive,
      });
    }
    this.productSearch = '';
    this.productResults = [];
  }

  /** Loads the supplier's price list; on a new order the lines already added are repriced from it where a price exists. */
  onSupplierChange(): void {
    this.supplierPrices = new Map<number, number>();
    if (!this.supplierId) return;
    this.supplierPriceApi.current(this.supplierId).subscribe({
      next: (prices) => {
        this.supplierPrices = new Map(prices.map((p) => [p.productId, p.unitCost]));
        if (!this.id) {
          for (const line of this.lines) {
            const price = this.supplierPrices.get(line.productId);
            if (price !== undefined) line.unitCost = price;
          }
        }
      },
      error: () => (this.supplierPrices = new Map<number, number>()),
    });
  }

  removeLine(index: number): void {
    this.lines.splice(index, 1);
  }

  private lineGrossBeforeDiscount(line: DraftLine): number {
    return Math.round(line.quantityOrdered * line.unitCost * 100) / 100;
  }

  /**
   * Fourier: "Global discount should be a fixed amount or percentage with percentage as the
   * default" — the GLOBAL header discount actually in effect right now, as a percentage:
   * typed directly (PERCENT, the default), or converted from the flat amount entered
   * (FIXED_AMOUNT) against this draft's raw, pre-discount subtotal — mirrors the backend's
   * one-time `TaxMath.percentFromFixedAmount` conversion at save time, computed live here
   * purely for this preview.
   */
  effectiveHeaderPercent(): number {
    if (this.discountType === 'FIXED_AMOUNT') {
      const rawSubtotal = this.lines.reduce((sum, l) => sum + this.lineGrossBeforeDiscount(l), 0);
      if (!this.discountFixedAmount || this.discountFixedAmount <= 0 || rawSubtotal <= 0) return 0;
      return (this.discountFixedAmount * 100) / rawSubtotal;
    }
    return this.discountPercent && this.discountPercent > 0 ? this.discountPercent : 0;
  }

  /**
   * Fourier: "Same for discount ... it will be per line" then "Except POS other module can
   * have global discount ... Either on line or global" — mirrors the backend's
   * TaxMath.effectiveDiscountPercent: the GLOBAL header discount wins whenever it's positive,
   * otherwise the line's own (PER_LINE mode).
   */
  private effectiveDiscountPercent(lineDiscountPercent: number | null): number {
    const header = this.effectiveHeaderPercent();
    if (header > 0) return header;
    return lineDiscountPercent ?? 0;
  }

  /** Mirrors the backend's TaxMath.applyDiscount — discount knocked off the line's gross before tax is even considered. */
  lineGross(line: DraftLine): number {
    const gross = this.lineGrossBeforeDiscount(line);
    const discountPercent = this.effectiveDiscountPercent(line.discountPercent);
    if (!discountPercent) return gross;
    return Math.round(gross * (1 - discountPercent / 100) * 100) / 100;
  }

  /** How much this line's discount actually knocked off — purely informational, already folded into lineTotal()/grandTotal() below. */
  lineDiscountAmount(line: DraftLine): number {
    return Math.round((this.lineGrossBeforeDiscount(line) - this.lineGross(line)) * 100) / 100;
  }

  private taxOnAmount(amount: number, ratePercent: number | null, inclusive: boolean): number {
    if (!ratePercent) return 0;
    const rate = ratePercent / 100;
    const raw = inclusive ? (amount * rate) / (1 + rate) : amount * rate;
    return Math.round(raw * 100) / 100;
  }

  /** Fourier: "add tax on line ... new purchase form" — the tax portion of this line, extracted (inclusive) or added (exclusive) from its (already-discounted) gross amount. */
  lineTax(line: DraftLine): number {
    return this.taxOnAmount(this.lineGross(line), line.taxRatePercent, line.taxInclusive);
  }

  private lineNet(line: DraftLine): number {
    const gross = this.lineGross(line);
    const tax = this.lineTax(line);
    return line.taxInclusive ? Math.round((gross - tax) * 100) / 100 : gross;
  }

  /** What this line actually costs in total — (already-discounted) gross when tax is inclusive, gross + tax when exclusive. */
  lineTotal(line: DraftLine): number {
    const gross = this.lineGross(line);
    return line.taxInclusive ? gross : Math.round((gross + this.lineTax(line)) * 100) / 100;
  }

  /** Fourier: "add Totals on all those form while recording" — mirrors purchase-order-print.component's own document-level reducers. */
  subtotalAmount(): number {
    return Math.round(this.lines.reduce((sum, l) => sum + this.lineNet(l), 0) * 100) / 100;
  }

  taxAmount(): number {
    return Math.round(this.lines.reduce((sum, l) => sum + this.lineTax(l), 0) * 100) / 100;
  }

  discountAmount(): number {
    return Math.round(this.lines.reduce((sum, l) => sum + this.lineDiscountAmount(l), 0) * 100) / 100;
  }

  grandTotal(): number {
    return Math.round(this.lines.reduce((sum, l) => sum + this.lineTotal(l), 0) * 100) / 100;
  }

  /** Task: "You can add new Supplier from New purchase order form" — opens the inline quick-add panel; the picker itself is disabled while it's open so there's only one obvious place to be setting the supplier. */
  openNewSupplier(): void {
    this.showNewSupplier = true;
    this.newSupplierError = null;
  }

  cancelNewSupplier(): void {
    this.showNewSupplier = false;
    this.newSupplierName = '';
    this.newSupplierContact = '';
    this.newSupplierPhone = '';
    this.newSupplierEmail = '';
    this.newSupplierTaxId = '';
    this.newSupplierError = null;
  }

  /** Creates the supplier, then — same "just did it, so select it" convenience as addLine picking a freshly-searched product — selects it on this draft right away instead of leaving the admin to find it in the dropdown themselves. */
  saveNewSupplier(): void {
    if (!this.newSupplierName.trim()) {
      this.newSupplierError = 'Name is required';
      return;
    }
    this.newSupplierError = null;
    this.supplierApi
      .create({
        name: this.newSupplierName.trim(),
        contactName: this.newSupplierContact || null,
        phone: this.newSupplierPhone || null,
        email: this.newSupplierEmail || null,
        taxId: this.newSupplierTaxId || null,
        active: true,
      })
      .subscribe({
        next: (supplier) => {
          this.suppliers = [...this.suppliers, supplier].sort((a, b) => a.name.localeCompare(b.name));
          this.supplierId = supplier.id;
          this.cancelNewSupplier();
        },
        error: (err) => (this.newSupplierError = err?.error?.message ?? 'Failed to save supplier'),
      });
  }

  submit(): void {
    if (!this.supplierId) {
      this.error = 'Select a supplier';
      return;
    }
    if (this.lines.length === 0) {
      this.error = 'Add at least one line item';
      return;
    }
    this.error = null;
    const payload = {
      supplierId: this.supplierId,
      expectedDate: this.expectedDate ? new Date(this.expectedDate).toISOString() : null,
      notes: this.notes || null,
      // Only when the picker was actually visible; otherwise omitted so the backend uses the primary warehouse.
      receivingWarehouseId: this.warehouses.length > 1 ? this.receivingWarehouseId : null,
      // Fourier: "Except POS other module can have global discount ... Either on line or
      // global" — stripped back to null whenever the mode isn't GLOBAL so an order that had one
      // from before the mode changed doesn't get resubmitted with one the backend would now
      // reject.
      // Fourier: "Global discount should be a fixed amount or percentage with percentage as
      // the default" — only whichever of the two was actually selected is ever sent, the other
      // nulled out, same "only one of the two is ever populated" convention the backend enforces.
      discountType: this.discountMode === 'GLOBAL' ? this.discountType : null,
      discountPercent: this.discountMode === 'GLOBAL' && this.discountType === 'PERCENT' ? (this.discountPercent ?? null) : null,
      discountFixedAmount: this.discountMode === 'GLOBAL' && this.discountType === 'FIXED_AMOUNT' ? (this.discountFixedAmount ?? null) : null,
      lines: this.lines.map((l) => ({
        productId: l.productId,
        quantityOrdered: l.quantityOrdered,
        unitCost: l.unitCost,
        // Fourier: "Same for discount ... it will be per line" — stripped back to null whenever the
        // mode isn't PER_LINE so a line that had a discount from before it changed doesn't get
        // resubmitted with one the backend would now reject.
        discountPercent: this.discountMode === 'PER_LINE' ? (l.discountPercent ?? null) : null,
      })),
    };
    const call = this.id ? this.purchaseOrderApi.update(this.id, payload) : this.purchaseOrderApi.create(payload);
    call.subscribe({
      next: (po) => this.router.navigate(['/admin/purchase-orders', po.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save purchase order'),
    });
  }
}
