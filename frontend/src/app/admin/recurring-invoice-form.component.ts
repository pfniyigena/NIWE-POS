import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RecurringInvoiceApiService } from '../core/api/recurring-invoice-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { Branch, Customer, InvoiceRecurrenceFrequency, Product } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';

/** Same minimal quick-add field set InvoiceFormComponent's own "+ New product" panel uses. */
const QUICK_ADD_DEFAULTS = { costPrice: 0, stockQuantity: 0, active: true, categoryId: null, taxRateId: null };

interface DraftLine {
  productId: number;
  productName: string;
  description: string;
  quantity: number;
  /** Unlike InvoiceFormComponent's own DraftLine, this is the agreed recurring price — never re-derived from the product's live catalog price once added; see the backend RecurringInvoiceTemplateLine.unitPrice's own javadoc. */
  unitPrice: number;
  discountPercent: number | null;
  /** Snapshotted from the product at add-line time, purely for this form's own live preview — the actual invoice generated each cycle re-derives tax fresh from the product at that time (see InvoiceService.applyLines), same as the product's own current price is always used for tax display here, not a frozen one. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxInclusive: boolean;
}

/**
 * Admin > Sales > Recurring invoices > New/Edit — Fourier: "Add Recurring Invoice", scoped via
 * AskUserQuestion to its own standalone module with full invoice line items (any catalog product,
 * quantity, price) rather than Subscription's single plan line. Deliberately mirrors
 * InvoiceFormComponent's own customer-picker + product-search line-editing UX (including its two
 * inline "+ New customer"/"+ New product" quick-add panels), plus the frequency/next-run-date
 * scheduling fields RecurringExpenseFormComponent already established on the Accounting side.
 *
 * <p>Each generated invoice comes out already ISSUED and posted to Accounting (Fourier, via
 * AskUserQuestion: "ISSUED immediately" — see backend RecurringInvoiceTemplateService), so unlike
 * a plain invoice draft, nothing here is reviewed before it posts — the lines/price saved on this
 * template *are* what gets charged each cycle, until the template itself is edited.
 */
@Component({
  selector: 'app-recurring-invoice-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit recurring invoice' : 'New recurring invoice' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3">
        <label class="form-label">Name</label>
        <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Monthly retainer — website support" required />
      </div>

      <div class="flex gap-2 mb-3" *ngIf="branches.length > 1">
        <div class="grow">
          <label class="form-label">Branch (generated invoices are issued from)</label>
          <select class="form-select" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>
      </div>

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

      <div class="flex gap-2 mb-3">
        <div class="grow">
          <label class="form-label">Frequency</label>
          <select class="form-select" name="frequency" [(ngModel)]="frequency">
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="QUARTERLY">Quarterly</option>
            <option value="YEARLY">Yearly</option>
          </select>
        </div>
        <div class="grow">
          <label class="form-label">Next run date</label>
          <app-date-picker name="nextRunDate" [(ngModel)]="nextRunDate" required></app-date-picker>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The daily sweep generates the next invoice on or after this date, then advances it by one {{ frequency | lowercase }} cycle.
          </div>
        </div>
        <div class="grow">
          <label class="form-label">Due in days (optional)</label>
          <input class="form-control" type="number" min="0" step="1" name="dueInDays" [(ngModel)]="dueInDays" placeholder="e.g. 30" />
        </div>
      </div>

      <div class="form-group mb-3">
        <label class="form-label">Notes (optional)</label>
        <textarea class="form-control" name="notes" rows="2" [(ngModel)]="notes" placeholder="Carried onto every generated invoice — left blank, each one notes which template generated it"></textarea>
      </div>

      <div class="form-group mb-3" *ngIf="discountMode === 'GLOBAL'">
        <label class="form-label">Discount (whole invoice)</label>
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
      </div>

      <table class="table-sm mb-3">
        <thead>
          <tr>
            <th>Product</th>
            <th class="text-end">Quantity</th>
            <th class="text-end">Unit price</th>
            <th class="text-end" *ngIf="discountMode === 'PER_LINE'">Discount %</th>
            <th class="text-end">Tax</th>
            <th class="text-end">Line total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let line of lines; let i = index">
            <tr>
              <td>{{ line.productName }}</td>
              <td class="text-end">
                <input type="number" class="form-control form-control-sm" style="width:90px; margin-left:auto;" min="1" [(ngModel)]="line.quantity" [name]="'qty' + i" />
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
            <tr>
              <td [attr.colspan]="discountMode === 'PER_LINE' ? 7 : 6" style="padding-top:0;">
                <input
                  class="form-control form-control-sm"
                  placeholder="Description (optional)"
                  [(ngModel)]="line.description"
                  [name]="'description' + i"
                />
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="lines.length === 0">
            <td [attr.colspan]="discountMode === 'PER_LINE' ? 7 : 6" class="text-muted">No lines yet — search for a product above to add one.</td>
          </tr>
        </tbody>
      </table>

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
          <div class="stat-label">Grand total (each cycle)</div>
          <div class="stat-value" style="font-size:1.1rem;">{{ grandTotal() | number: '1.2-2' }}</div>
        </div>
      </div>

      <div class="form-group mb-3" *ngIf="id">
        <label class="form-label">
          <input type="checkbox" name="active" [(ngModel)]="active" /> Active
        </label>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save</button>
        <a class="btn btn-link" routerLink="/admin/recurring-invoices">Cancel</a>
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
export class RecurringInvoiceFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  customerId: number | null = null;
  /** Branch generated invoices are issued from; the picker only shows with more than one active branch. */
  branchId: number | null = null;
  branches: Branch[] = [];
  frequency: InvoiceRecurrenceFrequency = 'MONTHLY';
  nextRunDate = new Date().toISOString().slice(0, 10);
  dueInDays: number | null = null;
  notes: string | null = null;
  active = true;
  lines: DraftLine[] = [];
  customers: Customer[] = [];
  error: string | null = null;

  productSearch = '';
  productResults: Product[] = [];

  discountMode: 'NONE' | 'PER_LINE' | 'GLOBAL' = 'NONE';
  discountPercent: number | null = null;
  discountType: 'PERCENT' | 'FIXED_AMOUNT' = 'PERCENT';
  discountFixedAmount: number | null = null;

  showNewCustomer = false;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerEmail = '';
  newCustomerTaxId = '';
  newCustomerError: string | null = null;

  showNewProduct = false;
  newProductName = '';
  newProductUnitPrice: number | null = null;
  newProductDescription = '';
  newProductError: string | null = null;

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private recurringInvoiceApi: RecurringInvoiceApiService,
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
      error: () => (this.discountMode = 'NONE'),
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.recurringInvoiceApi.getById(this.id).subscribe({
        next: (t) => {
          this.name = t.name;
          this.customerId = t.customerId;
          this.branchId = t.branchId ?? this.branchId;
          this.frequency = t.frequency;
          this.nextRunDate = t.nextRunDate.slice(0, 10);
          this.dueInDays = t.dueInDays;
          this.notes = t.notes;
          this.active = t.active;
          this.discountPercent = t.discountPercent || null;
          this.discountType = t.discountType ?? 'PERCENT';
          this.discountFixedAmount = t.discountFixedAmount;
          this.lines = t.lines.map((l) => ({
            productId: l.productId as number,
            productName: l.productName ?? '',
            description: l.description ?? '',
            quantity: l.quantity,
            unitPrice: l.unitPrice,
            discountPercent: l.discountPercent,
            taxCode: null,
            taxRatePercent: null,
            taxInclusive: false,
          }));
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load recurring invoice'),
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
      existing.quantity += 1;
    } else {
      this.lines.push({
        productId: p.id,
        productName: p.name,
        description: p.description ?? '',
        quantity: 1,
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
    return Math.round(line.quantity * line.unitPrice * 100) / 100;
  }

  effectiveHeaderPercent(): number {
    if (this.discountType === 'FIXED_AMOUNT') {
      const rawSubtotal = this.lines.reduce((sum, l) => sum + this.lineGrossBeforeDiscount(l), 0);
      if (!this.discountFixedAmount || this.discountFixedAmount <= 0 || rawSubtotal <= 0) return 0;
      return (this.discountFixedAmount * 100) / rawSubtotal;
    }
    return this.discountPercent && this.discountPercent > 0 ? this.discountPercent : 0;
  }

  private effectiveDiscountPercent(lineDiscountPercent: number | null): number {
    const header = this.effectiveHeaderPercent();
    if (header > 0) return header;
    return lineDiscountPercent ?? 0;
  }

  lineGross(line: DraftLine): number {
    const gross = this.lineGrossBeforeDiscount(line);
    const discountPercent = this.effectiveDiscountPercent(line.discountPercent);
    if (!discountPercent) return gross;
    return Math.round(gross * (1 - discountPercent / 100) * 100) / 100;
  }

  lineDiscountAmount(line: DraftLine): number {
    return Math.round((this.lineGrossBeforeDiscount(line) - this.lineGross(line)) * 100) / 100;
  }

  private taxOnAmount(amount: number, ratePercent: number | null, inclusive: boolean): number {
    if (!ratePercent) return 0;
    const rate = ratePercent / 100;
    const raw = inclusive ? (amount * rate) / (1 + rate) : amount * rate;
    return Math.round(raw * 100) / 100;
  }

  lineTax(line: DraftLine): number {
    return this.taxOnAmount(this.lineGross(line), line.taxRatePercent, line.taxInclusive);
  }

  private lineNet(line: DraftLine): number {
    const gross = this.lineGross(line);
    const tax = this.lineTax(line);
    return line.taxInclusive ? Math.round((gross - tax) * 100) / 100 : gross;
  }

  lineTotal(line: DraftLine): number {
    const gross = this.lineGross(line);
    return line.taxInclusive ? gross : Math.round((gross + this.lineTax(line)) * 100) / 100;
  }

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
    if (!this.name.trim()) {
      this.error = 'Name is required';
      return;
    }
    if (!this.customerId) {
      this.error = 'Select a customer';
      return;
    }
    if (!this.nextRunDate) {
      this.error = 'Pick the next run date';
      return;
    }
    if (this.lines.length === 0) {
      this.error = 'Add at least one line item';
      return;
    }
    this.error = null;
    const payload = {
      branchId: this.branchId,
      name: this.name.trim(),
      customerId: this.customerId,
      frequency: this.frequency,
      nextRunDate: this.nextRunDate,
      dueInDays: this.dueInDays ?? null,
      notes: this.notes || null,
      discountType: this.discountMode === 'GLOBAL' ? this.discountType : null,
      discountPercent: this.discountMode === 'GLOBAL' && this.discountType === 'PERCENT' ? (this.discountPercent ?? null) : null,
      discountFixedAmount: this.discountMode === 'GLOBAL' && this.discountType === 'FIXED_AMOUNT' ? (this.discountFixedAmount ?? null) : null,
      active: this.active,
      lines: this.lines.map((l) => ({
        productId: l.productId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        description: l.description || null,
        discountPercent: this.discountMode === 'PER_LINE' ? (l.discountPercent ?? null) : null,
      })),
    };
    const call = this.id ? this.recurringInvoiceApi.update(this.id, payload) : this.recurringInvoiceApi.create(payload);
    call.subscribe({
      next: (t) => this.router.navigate(['/admin/recurring-invoices', t.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save recurring invoice'),
    });
  }
}
