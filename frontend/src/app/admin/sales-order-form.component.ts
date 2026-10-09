import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesOrderApiService } from '../core/api/sales-order-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch, Product, Customer } from '../core/models';

/** How much of a brand-new product's optional fields this quick-add panel bothers asking for — same convention as InvoiceFormComponent's own QUICK_ADD_DEFAULTS. */
const QUICK_ADD_DEFAULTS = { costPrice: 0, stockQuantity: 0, active: true, categoryId: null, taxRateId: null };

interface DraftLine {
  productId: number;
  productName: string;
  quantityOrdered: number;
  unitPrice: number;
  /** Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — null/0 means no discount; only entered when discountMode is PER_LINE. */
  discountPercent: number | null;
  /** Fourier: "add tax on line ... same new sale order form" — snapshotted from the product at add-line time, purely for this form's own live preview; display-only, same as every other document type (no per-line tax override exists anywhere in this app). */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxInclusive: boolean;
}

/**
 * Admin > Sales > Sale Orders > New/Edit — Task: "Add Sale order... So that we can have POS &
 * Sales." Only ever creates/edits a DRAFT (see SalesOrderStatus's backend javadoc); confirming,
 * cancelling and fulfilling all happen on the detail screen instead — same split as
 * PurchaseOrderFormComponent/PurchaseOrderDetailComponent on the purchasing side.
 *
 * Task: "can add new Customer from New sale order form" — an inline "+ New customer" panel next
 * to the picker, same pattern and same reasoning as PurchaseOrderFormComponent's own "+ New
 * supplier" panel: placing an order for a customer who isn't set up yet shouldn't mean losing this
 * draft's lines to go fill out the full Admin > Customers form and come back. Deliberately a
 * smaller field set (name/phone/email) than that full form — no credit fields here, since those
 * are a separate, more consequential decision an admin should make deliberately on the real form,
 * not as a side effect of placing one order; the customer can still be edited with the rest of its
 * details (credit, tax ID, default terms) later from Admin > Customers.
 *
 * <p>Bug (now fixed at the backend): "not confirming sale order => Customer 'Walk-in Customer' is
 * not enabled for credit purchases." An earlier pass here worked around this by hiding Walk-in
 * Customer from the picker below, since confirming used to run the same credit-enabled/credit-
 * limit check a POS credit sale does. Per Task: "The rules of enabling credit on customer should
 * only be required in POS Module not in Sale Module," {@code SalesOrderService.confirm} no longer
 * runs that check at all (see its own javadoc) — any customer, walk-in included, can be confirmed
 * — so this picker went back to listing every customer with nothing filtered out.
 *
 * <p>Fourier: "Quick-add product on Quotation and Sale order add forms" — a second inline
 * quick-add panel, "+ New product", next to the product search box; name and unit price only,
 * same minimal field set and QUICK_ADD_DEFAULTS as InvoiceFormComponent's own, which this one is
 * copied from.
 */
@Component({
  selector: 'app-sales-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit sale order' : 'New sale order' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="flex gap-2 mb-3">
        <div class="grow">
          <label class="form-label">Customer</label>
          <div class="flex gap-2">
            <select class="form-select" name="customerId" [(ngModel)]="customerId" required [disabled]="showNewCustomer">
              <option [ngValue]="null" disabled>Select a customer…</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select>
            <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewCustomer" (click)="openNewCustomer()">
              + New customer
            </button>
          </div>
        </div>
        <div class="grow">
          <label class="form-label">Expected date (optional)</label>
          <app-date-picker name="expectedDate" [(ngModel)]="expectedDate" ></app-date-picker>
        </div>
        <div class="grow" *ngIf="branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-control" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>
      </div>

      <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewCustomer">
        <div class="alert alert-danger" *ngIf="newCustomerError">{{ newCustomerError }}</div>
        <div class="flex gap-2 mb-2">
          <div class="grow">
            <label class="form-label">Name</label>
            <input class="form-control" name="newCustomerName" [(ngModel)]="newCustomerName" placeholder="Customer name" />
          </div>
          <div class="grow">
            <label class="form-label">Phone</label>
            <input class="form-control" name="newCustomerPhone" [(ngModel)]="newCustomerPhone" />
          </div>
          <div class="grow">
            <label class="form-label">Email</label>
            <input class="form-control" type="email" name="newCustomerEmail" [(ngModel)]="newCustomerEmail" />
          </div>
          <div class="grow">
            <label class="form-label">Tax ID</label>
            <input class="form-control" name="newCustomerTaxId" [(ngModel)]="newCustomerTaxId" />
          </div>
        </div>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary btn-sm" (click)="saveNewCustomer()">Save customer</button>
          <button type="button" class="btn btn-link btn-sm" (click)="cancelNewCustomer()">Cancel</button>
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
            {{ p.name }} <span class="text-muted">— stock {{ p.stockQuantity }}, price {{ p.unitPrice | number: '1.2-2' }}</span>
          </div>
        </div>
        <!-- Fourier: "Quick-add product on Quotation and Sale order add forms" — a line item for a product that was never catalogued doesn't mean abandoning this draft. -->
        <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewProduct" (click)="openNewProduct()">
          + New product
        </button>
      </div>

      <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewProduct">
        <div class="alert alert-danger" *ngIf="newProductError">{{ newProductError }}</div>
        <div class="flex gap-2 mb-2">
          <div class="grow">
            <label class="form-label">Name</label>
            <input class="form-control" name="newProductName" [(ngModel)]="newProductName" placeholder="Product name" />
          </div>
          <div class="grow">
            <label class="form-label">Unit price</label>
            <input class="form-control" type="number" min="0" step="0.01" name="newProductUnitPrice" [(ngModel)]="newProductUnitPrice" />
          </div>
          <div class="grow">
            <label class="form-label">Description (optional)</label>
            <input class="form-control" name="newProductDescription" [(ngModel)]="newProductDescription" />
          </div>
        </div>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary btn-sm" (click)="saveNewProduct()">Save product &amp; add line</button>
          <button type="button" class="btn btn-link btn-sm" (click)="cancelNewProduct()">Cancel</button>
        </div>
        <div class="text-muted mt-2" style="font-size:0.8rem;">
          Starts with no stock tracked and no category/tax rate — fill those in later from Admin &gt; Products if needed.
        </div>
      </div>

      <table class="table-sm mb-3">
        <thead>
          <tr>
            <th>Product</th>
            <th class="text-end">Quantity</th>
            <th class="text-end">Unit price</th>
            <th class="text-end" *ngIf="discountMode === 'PER_LINE'">Discount %</th>
            <!-- Fourier: "add tax on line ... same new sale order form" — display-only, computed from the product's own assigned tax rate (snapshotted at add-line time); no override here. -->
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
              <input type="number" class="form-control form-control-sm" style="width:100px; margin-left:auto;" min="0" step="0.01" [(ngModel)]="line.unitPrice" [name]="'price' + i" />
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
        <a class="btn btn-link" routerLink="/admin/sales-orders">Cancel</a>
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
export class SalesOrderFormComponent implements OnInit {
  id: number | null = null;
  customerId: number | null = null;
  expectedDate: string | null = null;
  notes = '';
  /** Branch the order is sold from; the picker only shows with more than one active branch. */
  branchId: number | null = null;
  branches: Branch[] = [];
  lines: DraftLine[] = [];
  customers: Customer[] = [];
  error: string | null = null;

  productSearch = '';
  productResults: Product[] = [];

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

  // Task: "can add new Customer from New sale order form" — inline quick-add panel state, same shape as PurchaseOrderFormComponent's supplier one.
  showNewCustomer = false;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerEmail = '';
  /** Fourier: "Add Tax Id for all Customer quick-add" — same field as the full Customer form's own Tax ID. */
  newCustomerTaxId = '';
  newCustomerError: string | null = null;

  // Fourier: "Quick-add product on Quotation and Sale order add forms" — a second inline quick-add panel, for products this time, same shape as InvoiceFormComponent's own.
  showNewProduct = false;
  newProductName = '';
  newProductUnitPrice: number | null = null;
  newProductDescription = '';
  newProductError: string | null = null;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private salesOrderApi: SalesOrderApiService,
    private customerApi: CustomerApiService,
    private catalogApi: CatalogApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private branchApi: BranchApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => {
      this.branches = b;
      if (this.branchId == null) {
        this.branchId = b.find((x) => x.headquarters)?.id ?? null;
      }
    });
    this.customerApi.list().subscribe({
      next: (c) => (this.customers = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load customers'),
    });
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.discountMode = s.discountMode),
      error: () => (this.discountMode = 'NONE'), // never blocks the form — just hides the discount controls
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.salesOrderApi.getById(this.id).subscribe({
        next: (so) => {
          if (so.status !== 'DRAFT') {
            this.error = `${so.orderNumber} is ${so.status.replace('_', ' ').toLowerCase()} — only a draft can be edited.`;
            return;
          }
          this.customerId = so.customerId;
          this.expectedDate = so.expectedDate ? so.expectedDate.slice(0, 10) : null;
          this.notes = so.notes ?? '';
          this.branchId = so.branchId ?? this.branchId;
          this.discountPercent = so.discountPercent || null;
          this.discountType = so.discountType ?? 'PERCENT';
          this.discountFixedAmount = so.discountFixedAmount;
          this.lines = so.lines.map((l) => ({
            productId: l.productId as number,
            productName: l.productName,
            quantityOrdered: l.quantityOrdered,
            unitPrice: l.unitPrice,
            discountPercent: l.discountPercent,
            taxCode: l.taxCode,
            taxRatePercent: l.taxRatePercent,
            taxInclusive: l.taxInclusive,
          }));
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load sale order'),
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

  addLine(p: Product): void {
    const existing = this.lines.find((l) => l.productId === p.id);
    if (existing) {
      existing.quantityOrdered += 1;
    } else {
      this.lines.push({
        productId: p.id,
        productName: p.name,
        quantityOrdered: 1,
        unitPrice: p.unitPrice,
        discountPercent: null,
        taxCode: p.taxRateCode,
        taxRatePercent: p.taxRatePercent,
        taxInclusive: p.taxRateInclusive,
      });
    }
    this.productSearch = '';
    this.productResults = [];
  }

  removeLine(index: number): void {
    this.lines.splice(index, 1);
  }

  private lineGrossBeforeDiscount(line: DraftLine): number {
    return Math.round(line.quantityOrdered * line.unitPrice * 100) / 100;
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

  /** Fourier: "add tax on line ... same new sale order form" — the tax portion of this line, extracted (inclusive) or added (exclusive) from its (already-discounted) gross amount. */
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

  /** Opens the inline quick-add panel; the picker itself is disabled while it's open so there's only one obvious place to be setting the customer — same convention as PurchaseOrderFormComponent.openNewSupplier. */
  openNewCustomer(): void {
    this.showNewCustomer = true;
    this.newCustomerError = null;
  }

  cancelNewCustomer(): void {
    this.showNewCustomer = false;
    this.newCustomerName = '';
    this.newCustomerPhone = '';
    this.newCustomerEmail = '';
    this.newCustomerTaxId = '';
    this.newCustomerError = null;
  }

  /** Creates the customer, then selects it on this draft right away — same "just did it, so select it" convenience as addLine picking a freshly-searched product. */
  saveNewCustomer(): void {
    if (!this.newCustomerName.trim()) {
      this.newCustomerError = 'Name is required';
      return;
    }
    this.newCustomerError = null;
    this.customerApi
      .create({
        name: this.newCustomerName.trim(),
        phone: this.newCustomerPhone || null,
        email: this.newCustomerEmail || null,
        taxId: this.newCustomerTaxId || null,
      })
      .subscribe({
        next: (customer) => {
          this.customers = [...this.customers, customer].sort((a, b) => a.name.localeCompare(b.name));
          this.customerId = customer.id;
          this.cancelNewCustomer();
        },
        error: (err) => (this.newCustomerError = err?.error?.message ?? 'Failed to save customer'),
      });
  }

  /** Fourier: "Quick-add product on Quotation and Sale order add forms" — opens the quick-add product panel. */
  openNewProduct(): void {
    this.showNewProduct = true;
    this.newProductError = null;
  }

  cancelNewProduct(): void {
    this.showNewProduct = false;
    this.newProductName = '';
    this.newProductUnitPrice = null;
    this.newProductDescription = '';
    this.newProductError = null;
  }

  /** Creates the product with everything but name/price/description left at its usual default (see QUICK_ADD_DEFAULTS), then adds it as a line on this draft right away. */
  saveNewProduct(): void {
    if (!this.newProductName.trim()) {
      this.newProductError = 'Name is required';
      return;
    }
    if (this.newProductUnitPrice == null || this.newProductUnitPrice < 0) {
      this.newProductError = 'Enter a valid unit price';
      return;
    }
    this.newProductError = null;
    this.catalogApi
      .createProduct({
        name: this.newProductName.trim(),
        description: this.newProductDescription || null,
        unitPrice: this.newProductUnitPrice,
        ...QUICK_ADD_DEFAULTS,
      })
      .subscribe({
        next: (product) => {
          this.addLine(product);
          this.cancelNewProduct();
        },
        error: (err) => (this.newProductError = err?.error?.message ?? 'Failed to save product'),
      });
  }

  submit(): void {
    if (!this.customerId) {
      this.error = 'Select a customer';
      return;
    }
    if (this.lines.length === 0) {
      this.error = 'Add at least one line item';
      return;
    }
    this.error = null;
    const payload = {
      customerId: this.customerId,
      expectedDate: this.expectedDate ? new Date(this.expectedDate).toISOString() : null,
      notes: this.notes || null,
      branchId: this.branchId,
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
        unitPrice: l.unitPrice,
        // Fourier: "Same for discount ... it will be per line" — stripped back to null whenever the
        // mode isn't PER_LINE so a line that had a discount from before it changed doesn't get
        // resubmitted with one the backend would now reject.
        discountPercent: this.discountMode === 'PER_LINE' ? (l.discountPercent ?? null) : null,
      })),
    };
    const call = this.id ? this.salesOrderApi.update(this.id, payload) : this.salesOrderApi.create(payload);
    call.subscribe({
      next: (so) => this.router.navigate(['/admin/sales-orders', so.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save sale order'),
    });
  }
}
