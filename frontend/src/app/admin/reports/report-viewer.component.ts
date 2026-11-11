import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReportApiService, ReportFilter } from '../../core/api/report-api.service';
import { RegisterApiService } from '../../core/api/register-api.service';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { WarehouseApiService } from '../../core/api/warehouse-api.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { AssetApiService, Verification } from '../../core/api/asset-api.service';
import { ModuleApiService, ModuleKey } from '../../core/api/module-api.service';
import { REPORT_DEFS, ReportColumn, ReportDef, reportListRouteForSection } from '../../core/report-defs';
import { Branch, Category, Product, RegisterSummary, Warehouse } from '../../core/models';

import { T } from '../../core/i18n/t';
/**
 * One generic viewer for all seven POS Reports — which report is picked by the `:key` route
 * param, looked up in REPORT_DEFS (see that file for why one component instead of seven).
 * Filters: a date range always, a register always (optional — "every register" if left blank),
 * a category filter on the two per-product reports (ReportDef.categoryFilter), and — Fourier: "can
 * we have Stock Movement History for a specific product" — a searchable product filter on Stock
 * Movement History only (ReportDef.productFilter). That last one can also arrive pre-filled via
 * `?productId=&productName=` query params (see ProductFormComponent's "View stock movement
 * history" link) so a product's own page can jump straight into its own history, already filtered
 * and run.
 */
@Component({
  selector: 'app-report-viewer',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ (def?.title ?? 'Report') | translate }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="backRoute">&larr; All reports</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="def">
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <ng-container *ngIf="def.filterMode !== 'none'">
            <div class="form-group" style="margin:0;">
              <label class="form-label">{{ 'From' | translate }}</label>
              <app-date-picker [(ngModel)]="filter.from" ></app-date-picker>
            </div>
            <div class="form-group" style="margin:0;">
              <label class="form-label">{{ 'To' | translate }}</label>
              <app-date-picker [(ngModel)]="filter.to" ></app-date-picker>
            </div>
          </ng-container>
          <div class="form-group" style="margin:0;" *ngIf="def.filterMode !== 'none' && def.filterMode !== 'dateOnly'">
            <label class="form-label">{{ 'Register' | translate }}</label>
            <select class="form-select" [(ngModel)]="filter.registerId">
              <option [ngValue]="null">{{ 'All registers' | translate }}</option>
              <option *ngFor="let r of registers" [ngValue]="r.id">{{ r.name }}</option>
            </select>
          </div>
          <div class="form-group" style="margin:0;" *ngIf="def.categoryFilter">
            <label class="form-label">{{ 'Category' | translate }}</label>
            <select class="form-select" [(ngModel)]="filter.categoryId">
              <option [ngValue]="null">{{ 'All categories' | translate }}</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-group" style="margin:0;" *ngFor="let x of def.extraFilters">
            <label class="form-label">{{ x.label | translate }}</label>
            <select class="form-select" *ngIf="x.type === 'select'" [(ngModel)]="extra[x.key]" [name]="'x-' + x.key" [attr.data-filter]="x.key">
              <option value="">{{ 'Any' | translate }}</option>
              <option *ngFor="let o of x.options" [value]="o.value">{{ o.label | translate }}</option>
            </select>
            <select class="form-select" *ngIf="x.type === 'verification'" [(ngModel)]="extra[x.key]" [name]="'x-' + x.key" [attr.data-filter]="x.key">
              <option value="">{{ 'Latest round' | translate }}</option>
              <option *ngFor="let r of rounds" [value]="'' + r.id">{{ r.name }} ({{ r.startedOn }})</option>
            </select>
            <input class="form-control" *ngIf="x.type === 'number'" type="number" min="1" style="width:8rem;" [(ngModel)]="extra[x.key]" [name]="'x-' + x.key" [attr.data-filter]="x.key" />
            <input class="form-control" *ngIf="x.type === 'text'" style="width:11rem;" [placeholder]="(x.placeholder || '') | translate" [(ngModel)]="extra[x.key]" [name]="'x-' + x.key" [attr.data-filter]="x.key" />
          </div>
          <div class="form-group" style="margin:0;" *ngIf="def.openOnlyToggle">
            <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" [(ngModel)]="includeClosed" (ngModelChange)="hasRun && run()" /> {{ def.openOnlyToggle | translate }}</label>
          </div>
          <div class="form-group" style="margin:0;" *ngIf="def.branchFilter && branches.length > 1">
            <label class="form-label">{{ 'Branch' | translate }}</label>
            <select class="form-select" [(ngModel)]="filter.branchId">
              <option [ngValue]="null">{{ 'All branches' | translate }}</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div class="form-group" style="margin:0;" *ngIf="def.warehouseFilter && warehouses.length > 1">
            <label class="form-label">{{ 'Warehouse' | translate }}</label>
            <select class="form-select" [(ngModel)]="warehouseChoice" (ngModelChange)="onWarehouseChoice()">
              <!-- Stock Movement History has just two modes: every warehouse, or one. Stock On Hand has a third (the classic primary-warehouse view). -->
              <option value="default" *ngIf="twoModeWarehouse">{{ 'All warehouses' | translate }}</option>
              <option value="default" *ngIf="!twoModeWarehouse">{{ 'Default view (primary warehouse sales stock)' | translate }}</option>
              <option value="all" *ngIf="!twoModeWarehouse">{{ 'All warehouses (sellable)' | translate }}</option>
              <option *ngFor="let w of warehouses" [value]="'w' + w.id">{{ w.name }}</option>
            </select>
          </div>
          <div class="form-group" style="margin:0; position:relative; min-width:260px;" *ngIf="def.productFilter">
            <label class="form-label">{{ 'Product' | translate }}</label>
            <div class="flex gap-2 align-center" *ngIf="filter.productId != null; else productSearchBox">
              <input class="form-control" [value]="selectedProductName" disabled />
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="clearProductFilter()">&times;</button>
            </div>
            <ng-template #productSearchBox>
              <input
                class="form-control"
                [placeholder]="'Search products by name/SKU…' | translate"
                [(ngModel)]="productSearch"
                (input)="onProductSearch()"
              />
              <div class="dropdown-panel" *ngIf="productResults.length > 0">
                <div class="dropdown-item" *ngFor="let p of productResults" (click)="selectProduct(p)">{{ p.name }}</div>
              </div>
            </ng-template>
          </div>
          <button class="btn btn-primary" (click)="run()" [disabled]="loading">{{ loading ? ('Running…' | translate) : ('Run report' | translate) }}</button>
          <span class="grow"></span>
          <button class="btn btn-outline-secondary" (click)="exportAs('xlsx')" [disabled]="exporting">{{ 'Export Excel' | translate }}</button>
          <button class="btn btn-outline-secondary" (click)="exportAs('pdf')" [disabled]="exporting">{{ 'Export PDF' | translate }}</button>
        </div>
      </div>

      <div style="overflow-x:auto;" *ngIf="def">
        <table>
          <thead>
            <tr>
              <th *ngFor="let col of def.columns">{{ col.label | translate }}</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of rows">
              <td *ngFor="let col of def.columns" [class.text-end]="isNumeric(col)">{{ cellText(row, col) }}</td>
            </tr>
            <tr *ngIf="rows.length === 0">
              <td [attr.colspan]="def.columns.length" class="text-muted">
                {{ hasRun ? 'No data for these filters.' : (def.filterMode === 'none' ? 'Click "Run report".' : 'Choose a date range and click "Run report".') }}
              </td>
            </tr>
          </tbody>
        </table>
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
export class ReportViewerComponent implements OnInit {
  def: ReportDef | null = null;
  /** Task: "all link to back report should go to the parent not to the POS reports" — which picker screen owns this report's section, derived from ReportDef.section rather than always /admin/reports. */
  backRoute = '/admin/reports';
  rows: Record<string, any>[] = [];
  registers: RegisterSummary[] = [];
  categories: Category[] = [];
  /** Multi-warehouse phase 1 — only loaded for reports with ReportDef.warehouseFilter; the picker itself only shows once there's more than one. */
  warehouses: Warehouse[] = [];
  warehouseChoice = 'default';
  /** Only loaded for reports with ReportDef.branchFilter; the picker itself only shows once there's more than one active branch. */
  branches: Branch[] = [];

  filter: ReportFilter = { from: null, to: null, registerId: null, branchId: null, categoryId: null, productId: null };
  /** Law Firm reports with ReportDef.openOnlyToggle: also list closed matters. */
  includeClosed = false;
  /** Values of ReportDef.extraFilters, by key. */
  extra: Record<string, string> = {};
  /** Asset verification rounds, for the 'verification' extra filter. */
  rounds: Verification[] = [];

  /** Fourier: "can we have Stock Movement History for a specific product" — search-to-select, same pattern as BomFormComponent's own component-line search. */
  selectedProductName = '';
  productSearch = '';
  productResults: Product[] = [];
  private productSearchDebounce: ReturnType<typeof setTimeout> | null = null;

  loading = false;
  exporting = false;
  hasRun = false;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private reportApi: ReportApiService,
    private registerApi: RegisterApiService,
    private catalogApi: CatalogApiService,
    private warehouseApi: WarehouseApiService,
    private branchApi: BranchApiService,
    private modules: ModuleApiService,
    private assetApi: AssetApiService
  ) {}

  ngOnInit(): void {
    const key = this.route.snapshot.paramMap.get('key');
    this.def = REPORT_DEFS.find((r) => r.key === key) ?? null;
    if (!this.def) {
      this.error = T('Unknown report');
      return;
    }
    this.backRoute = reportListRouteForSection(this.def.section);
    // The POS and Assets reports belong to optional modules (this viewer is shared by every module's reports).
    const hrModules: Record<string, ModuleKey> = {
      'employee-directory': 'EMPLOYEES', 'headcount-by-department': 'EMPLOYEES', 'leave-balances': 'ATTENDANCE', 'leave-requests': 'ATTENDANCE',
      'attendance-summary': 'ATTENDANCE', 'contracts-expiring': 'CONTRACTS', 'payroll-register': 'PAYROLL', 'payroll-summary': 'PAYROLL',
    };
    const moduleKey: ModuleKey | null = this.def.section.startsWith('POS') ? 'POS' : this.def.section === 'Installments' ? 'INSTALLMENTS' : this.def.section === 'Time Tracking' ? 'TIME_TRACKING' : this.def.section === 'Work Orders' ? 'WORK_ORDERS' : this.def.section === 'Booking' ? 'BOOKING' : this.def.section === 'Law Firm' ? 'LAW_FIRM' : this.def.section === 'Real Estate' ? 'REAL_ESTATE' : this.def.section === 'Property Management' ? 'PROPERTY_MANAGEMENT' : this.def.section === 'Manufacturing' ? 'MANUFACTURING' : this.def.section === 'Subscriptions' ? 'SUBSCRIPTIONS' : this.def.section === 'Assets' ? 'ASSETS' : this.def.section === 'Expenses' ? 'EXPENSES' : this.def.section === 'HR' ? hrModules[this.def.key] ?? 'EMPLOYEES' : null;
    if (moduleKey) {
      this.modules.whenLoaded().subscribe(() => {
        if (!this.modules.on(moduleKey)) {
          this.error = T('The {p0} module is not activated (Settings > Modules).', { p0: moduleKey === 'POS' ? 'POS' : moduleKey === 'MANUFACTURING' ? T('Manufacturing') : moduleKey === 'SUBSCRIPTIONS' ? T('Subscriptions') : moduleKey === 'ASSETS' ? T('Assets Management') : moduleKey === 'EXPENSES' ? T('Expenses') : moduleKey === 'INSTALLMENTS' ? T('Installments') : moduleKey === 'TIME_TRACKING' ? T('Time Tracking') : moduleKey === 'WORK_ORDERS' ? T('Work Orders') : moduleKey === 'BOOKING' ? T('Booking') : moduleKey === 'LAW_FIRM' ? T('Law Firm') : moduleKey === 'REAL_ESTATE' ? T('Real Estate') : moduleKey === 'PROPERTY_MANAGEMENT' ? T('Property Management') : T('required HR') });
          this.def = null;
        }
      });
    }

    // Default to the last 30 days so "Run report" produces something useful without the user
    // having to think about a range first.
    const today = new Date();
    const past = new Date();
    past.setDate(past.getDate() - 30);
    this.filter.to = today.toISOString().slice(0, 10);
    this.filter.from = past.toISOString().slice(0, 10);

    this.registerApi.listRegisters().subscribe((r) => (this.registers = r));
    if (this.def.categoryFilter) {
      this.catalogApi.listCategories().subscribe((c) => (this.categories = c));
    }
    if (this.def.branchFilter) {
      this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    }
    if (this.def.warehouseFilter) {
      this.warehouseApi.listAll().subscribe({ next: (w) => (this.warehouses = w.filter((x) => x.active)), error: () => (this.warehouses = []) });
    }

    // Fourier: "can we have Stock Movement History for a specific product ... viewed on product"
    // — arriving from a product's own page via ?productId=&productName= pre-fills the filter and
    // runs immediately, same as the snapshot-style reports below, so the product's own page lands
    // straight on its own history rather than an empty "click Run" state.
    let arrivedWithProductFilter = false;
    (this.def.extraFilters ?? []).forEach((x) => {
      this.extra[x.key] = this.route.snapshot.queryParamMap.get(x.key) ?? x.defaultValue ?? '';
      if (x.type === 'verification') {
        this.assetApi.verifications().subscribe({ next: (r) => (this.rounds = r), error: () => {} });
        if (this.extra[x.key]) arrivedWithProductFilter = true;
      }
    });
    if (this.def.productFilter) {
      const productIdParam = this.route.snapshot.queryParamMap.get('productId');
      const productNameParam = this.route.snapshot.queryParamMap.get('productName');
      if (productIdParam) {
        this.filter.productId = Number(productIdParam);
        this.selectedProductName = productNameParam ?? `#${productIdParam}`;
        arrivedWithProductFilter = true;
      }
    }

    // Snapshot-style reports (Outstanding/Overdue Credit) have no filters to fill in first —
    // run immediately so the screen isn't just an empty "click Run" state.
    if (this.def.filterMode === 'none' || arrivedWithProductFilter) {
      this.run();
    }
  }

  /** Maps the single Warehouse dropdown onto the two backend filters: a specific warehouse id, or "all warehouses", or neither (the original default view). */
  onWarehouseChoice(): void {
    if (this.warehouseChoice === 'all') {
      this.filter.warehouseId = null;
      this.filter.allWarehouses = true;
    } else if (this.warehouseChoice.startsWith('w')) {
      this.filter.warehouseId = Number(this.warehouseChoice.slice(1));
      this.filter.allWarehouses = false;
    } else {
      this.filter.warehouseId = null;
      this.filter.allWarehouses = false;
    }
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
    this.filter.productId = p.id;
    this.selectedProductName = p.name;
    this.productSearch = '';
    this.productResults = [];
  }

  clearProductFilter(): void {
    this.filter.productId = null;
    this.selectedProductName = '';
  }

  run(): void {
    if (!this.def) return;
    this.filter.openOnly = this.def.openOnlyToggle ? !this.includeClosed : null;
    this.filter.extra = { ...this.extra };
    this.error = null;
    this.loading = true;
    this.reportApi.run(this.def.endpoint, this.filter).subscribe({
      next: (rows) => {
        this.loading = false;
        this.hasRun = true;
        this.rows = rows;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? T('Failed to run report');
      },
    });
  }

  exportAs(format: 'xlsx' | 'pdf'): void {
    if (!this.def) return;
    this.filter.openOnly = this.def.openOnlyToggle ? !this.includeClosed : null;
    this.filter.extra = { ...this.extra };
    this.error = null;
    this.exporting = true;
    this.reportApi.download(this.def.endpoint, this.filter, format).subscribe({
      next: (blob) => {
        this.exporting = false;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${this.def!.key}.${format}`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.exporting = false;
        this.error = err?.error?.message ?? T('Export failed');
      },
    });
  }

  /** Stock Movement History, Stock Aging and Dead Stock have two warehouse modes (every warehouse, or one); Stock On Hand has a third (the primary-warehouse view). */
  get twoModeWarehouse(): boolean {
    return this.def?.key === 'stock-movement' || this.def?.key === 'stock-aging' || this.def?.key === 'dead-stock';
  }

  isNumeric(col: ReportColumn): boolean {
    return col.format === 'int' || col.format === 'money' || col.format === 'percent' || col.format === 'moneyN' || col.format === 'percentN';
  }

  cellText(row: Record<string, any>, col: ReportColumn): string {
    const value = row[col.key];
    switch (col.format) {
      case 'money':
        return value == null ? '0.00' : Number(value).toFixed(2);
      case 'percent':
        return value == null ? '0%' : `${Number(value).toFixed(2)}%`;
      case 'moneyN':
        return value == null ? '—' : Number(value).toFixed(2);
      case 'percentN':
        return value == null ? '—' : `${Number(value).toFixed(1)}%`;
      case 'int':
        return value == null ? '0' : String(value);
      case 'hash':
        return value == null ? '—' : `#${value}`;
      case 'date':
        return value == null ? '—' : new Date(value).toLocaleString();
      default:
        return value == null || value === '' ? '—' : String(value);
    }
  }
}
