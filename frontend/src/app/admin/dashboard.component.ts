import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ModuleApiService } from '../core/api/module-api.service';
import { InstallmentSummaryComponent } from './installments/installment-summary.component';
import { DashboardApiService, DashboardOverview, AttentionItem } from '../core/api/dashboard-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch, DashboardSummary, Warehouse, WarehouseLowStockRow } from '../core/models';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, InstallmentSummaryComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">Dashboard</h3>
        <!-- Branches: narrows today's sales figures only; stock, customers and sessions stay business-wide. Hidden until a second branch exists. -->
        <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="selectedBranchId" (ngModelChange)="loadSummary()" title="Branch (affects today's sales figures)">
          <option [ngValue]="null">All branches</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
      </div>
      <!-- CP-7: what needs doing, how sales compare, who owes, what to pay, what sells. Every number opens the list behind it. -->
      <ng-container *ngIf="overview as o">
        <div class="dash-section" id="dash-attention">
          <h4 class="dash-h">Needs your attention</h4>
          <div class="attn-grid" *ngIf="attentionVisible() > 0">
            <ng-container *ngFor="let a of attentionShown">
              <a class="attn-tile" *ngIf="tileOn(a.key)" [routerLink]="linkFor(a.key)" [attr.data-key]="a.key" [class.attn-hot]="a.key === 'OVERDUE_INVOICES' || a.key === 'OUT_OF_STOCK'">
                <span class="attn-count">{{ a.count }}</span>
                <span class="attn-label">{{ a.label }}</span>
                <span class="attn-amount" *ngIf="a.amount !== null">{{ a.amount | number: '1.2-2' }}</span>
              </a>
            </ng-container>
          </div>
          <div class="alert alert-success" id="dash-allclear" *ngIf="attentionVisible() === 0">All clear. Nothing is waiting for you right now.</div>
        </div>

        <div class="dash-section" id="dash-sales">
          <h4 class="dash-h">Sales compared with last week</h4>
          <div class="cmp-grid">
            <div class="cmp-card" id="cmp-today">
              <div class="stat-label">Today</div>
              <div class="cmp-value">{{ o.comparison.today | number: '1.2-2' }}</div>
              <div class="cmp-delta" [ngClass]="todayDelta.cls">{{ todayDelta.text }} <span class="text-muted">than the same day last week ({{ o.comparison.sameDayLastWeek | number: '1.2-2' }})</span></div>
            </div>
            <div class="cmp-card" id="cmp-week">
              <div class="stat-label">Last 7 days</div>
              <div class="cmp-value">{{ o.comparison.last7 | number: '1.2-2' }}</div>
              <div class="cmp-delta" [ngClass]="weekDelta.cls">{{ weekDelta.text }} <span class="text-muted">than the 7 days before ({{ o.comparison.previous7 | number: '1.2-2' }})</span></div>
            </div>
          </div>
          <div class="chart-legend text-muted">Till sales <span class="lg lg-pos"></span> and invoices issued <span class="lg lg-inv"></span> per day, last 14 days (opening balances not counted).</div>
          <div class="bar-chart" id="dash-chart">
            <div class="bar-col" *ngFor="let b of bars" [attr.title]="b.title">
              <div class="bar-stack">
                <div class="bar-inv" [style.height.%]="b.invPct"></div>
                <div class="bar-pos" [style.height.%]="b.posPct"></div>
              </div>
              <div class="bar-label">{{ b.label }}</div>
            </div>
          </div>
        </div>

        <div class="dash-two">
          <div class="dash-section" id="dash-owing">
            <div class="flex justify-between align-center mb-2">
              <h4 class="dash-h" style="margin:0;">Customers to chase</h4>
              <a class="btn btn-sm btn-outline-primary" routerLink="/admin/collections">All collections</a>
            </div>
            <div class="text-muted mb-2" *ngIf="o.owing.customersOwing > 0">{{ o.owing.totalOutstanding | number: '1.2-2' }} owed by {{ o.owing.customersOwing }} customer(s), of which {{ o.owing.totalOverdue | number: '1.2-2' }} is overdue.</div>
            <table>
              <thead><tr><th>Customer</th><th class="text-end">Owes</th><th class="text-end">Overdue</th></tr></thead>
              <tbody>
                <tr *ngFor="let r of o.owing.top" class="owing-row">
                  <td data-label="Customer"><a [routerLink]="['/admin/customers', r.customerId]">{{ r.name }}</a></td>
                  <td data-label="Owes" class="text-end">{{ r.outstanding | number: '1.2-2' }}</td>
                  <td data-label="Overdue" class="text-end" [class.text-danger]="r.overdue > 0">{{ r.overdue | number: '1.2-2' }}<span class="text-muted" *ngIf="r.oldestDaysOverdue > 0"> ({{ r.oldestDaysOverdue }}d)</span></td>
                </tr>
                <tr *ngIf="o.owing.top.length === 0"><td colspan="3" class="text-muted">Nobody owes you anything.</td></tr>
              </tbody>
            </table>
          </div>

          <div class="dash-section" id="dash-bills">
            <div class="flex justify-between align-center mb-2">
              <h4 class="dash-h" style="margin:0;">Oldest unpaid supplier bills</h4>
              <a class="btn btn-sm btn-outline-primary" routerLink="/admin/bills">All bills</a>
            </div>
            <div class="text-muted mb-2" *ngIf="o.bills.unpaidBills > 0">{{ o.bills.totalUnpaid | number: '1.2-2' }} to pay on {{ o.bills.unpaidBills }} bill(s).</div>
            <table>
              <thead><tr><th>Bill</th><th>Supplier</th><th class="text-end">To pay</th><th class="text-end">Age</th></tr></thead>
              <tbody>
                <tr *ngFor="let b of o.bills.oldest" class="bill-row">
                  <td data-label="Bill"><a [routerLink]="['/admin/bills', b.billId]">{{ b.billNumber }}</a></td>
                  <td data-label="Supplier">{{ b.supplierName }}</td>
                  <td data-label="To pay" class="text-end">{{ b.outstanding | number: '1.2-2' }}</td>
                  <td data-label="Age" class="text-end">{{ b.ageDays }}d</td>
                </tr>
                <tr *ngIf="o.bills.oldest.length === 0"><td colspan="4" class="text-muted">No unpaid bills.</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="dash-section" id="dash-sellers">
          <h4 class="dash-h">Top sellers, last {{ o.topSellersDays }} days (till sales)</h4>
          <table>
            <thead><tr><th>Product</th><th>SKU</th><th class="text-end">Sold</th><th class="text-end">Revenue</th></tr></thead>
            <tbody>
              <tr *ngFor="let t of o.topSellers" class="seller-row">
                <td data-label="Product"><a [routerLink]="['/admin/products', t.productId]">{{ t.name }}</a></td>
                <td data-label="SKU">{{ t.sku }}</td>
                <td data-label="Sold" class="text-end">{{ t.quantity }}</td>
                <td data-label="Revenue" class="text-end">{{ t.revenue | number: '1.2-2' }}</td>
              </tr>
              <tr *ngIf="o.topSellers.length === 0"><td colspan="4" class="text-muted">No till sales in this period.</td></tr>
            </tbody>
          </table>
        </div>
      </ng-container>

      <ng-container *ngIf="summary as s">
        <div class="stat-grid">
          <div class="stat-tile">
            <div class="stat-label">Today's sales</div>
            <div class="stat-value">{{ s.todaySalesCount }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Today's revenue</div>
            <div class="stat-value">{{ s.todaySalesTotal | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Open sessions</div>
            <div class="stat-value">{{ s.openSessionsCount }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Active products</div>
            <div class="stat-value">{{ s.totalActiveProducts }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Customers</div>
            <div class="stat-value">{{ s.totalCustomers }}</div>
          </div>
          <div class="stat-tile" [class.warn]="s.lowStockProducts.length > 0">
            <div class="stat-label">Low stock</div>
            <div class="stat-value">{{ s.lowStockProducts.length }}</div>
          </div>
        </div>

        <ng-container *ngIf="modules.on('INSTALLMENTS')">
          <div class="flex justify-between align-center mb-2">
            <h4 style="margin:0;">Installments</h4>
            <a class="btn btn-sm btn-outline-primary" routerLink="/admin/installments">Open</a>
          </div>
          <app-installment-summary></app-installment-summary>
        </ng-container>

        <div class="flex justify-between align-center mb-2">
          <h4 style="margin:0;">Low-stock products</h4>
          <!-- Task: "the dashboard already flags low stock, but nothing acts on it" — one click drafts a single PO covering every low-stock item. -->
          <a class="btn btn-sm btn-outline-primary" *ngIf="s.lowStockProducts.length > 0" [routerLink]="['/admin/purchase-orders/new']" [queryParams]="{ fromLowStock: 1 }">
            Create purchase order for all
          </a>
        </div>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>SKU</th>
              <th class="text-end">Stock</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of s.lowStockProducts">
              <td>{{ p.name }}</td>
              <td>{{ p.sku }}</td>
              <td class="text-end" [style.color]="'var(--warning-text)'">{{ p.stockQuantity }}</td>
              <td class="flex gap-2">
                <a [routerLink]="['/admin/products', p.id, 'edit']">Edit</a>
                <a [routerLink]="['/admin/purchase-orders/new']" [queryParams]="{ productId: p.id }">Reorder</a>
              </td>
            </tr>
            <tr *ngIf="s.lowStockProducts.length === 0">
              <td colspan="4" class="text-muted">Nothing low on stock.</td>
            </tr>
          </tbody>
        </table>

        <!-- Multi-warehouse phase 1: the table above counts the primary warehouse's sales stock only. Once there's more than one warehouse, this shows low stock inside any one of them. -->
        <ng-container *ngIf="warehouses.length > 1">
          <div class="flex justify-between align-center mb-2 mt-4" style="flex-wrap:wrap; gap:0.5rem;">
            <h4 style="margin:0;">Low stock by warehouse</h4>
            <select class="form-select" style="width:auto;" [(ngModel)]="selectedWarehouseId" (ngModelChange)="loadWarehouseLowStock()">
              <option [ngValue]="null">Choose a warehouse…</option>
              <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}</option>
            </select>
          </div>
          <div class="alert alert-danger" *ngIf="warehouseLowStockError">{{ warehouseLowStockError }}</div>
          <table *ngIf="selectedWarehouseId !== null">
            <thead>
              <tr>
                <th>Name</th>
                <th>SKU</th>
                <th class="text-end">Stock here</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of warehouseLowStock">
                <td>{{ r.productName }}</td>
                <td>{{ r.sku || '—' }}</td>
                <td class="text-end" [style.color]="'var(--warning-text)'">{{ r.quantity }}</td>
                <td><a [routerLink]="['/admin/products', r.productId]">View stock</a></td>
              </tr>
              <tr *ngIf="warehouseLowStock.length === 0">
                <td colspan="4" class="text-muted">Nothing low on stock in this warehouse.</td>
              </tr>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class AdminDashboardComponent implements OnInit {
  summary: DashboardSummary | null = null;
  overview: DashboardOverview | null = null;
  attentionShown: AttentionItem[] = [];
  bars: { label: string; posPct: number; invPct: number; title: string }[] = [];
  todayDelta = { text: '', cls: '' };
  weekDelta = { text: '', cls: '' };
  error: string | null = null;

  branches: Branch[] = [];
  selectedBranchId: number | null = null;
  warehouses: Warehouse[] = [];
  selectedWarehouseId: number | null = null;
  warehouseLowStock: WarehouseLowStockRow[] = [];
  warehouseLowStockError: string | null = null;

  constructor(
    private dashboardApi: DashboardApiService,
    private warehouseApi: WarehouseApiService,
    private catalogApi: CatalogApiService,
    private branchApi: BranchApiService,
    public modules: ModuleApiService
  ) {}

  ngOnInit(): void {
    this.loadSummary();
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    // Only needed for the "Low stock by warehouse" section, which stays hidden for a single-warehouse store.
    this.warehouseApi.listAll().subscribe({
      next: (w) => (this.warehouses = w.filter((x) => x.active)),
      error: () => (this.warehouses = []),
    });
  }

  private static readonly LINKS: Record<string, string> = {
    OVERDUE_INVOICES: '/admin/collections',
    UNPAID_BILLS: '/admin/bills',
    OUT_OF_STOCK: '/admin/products',
    LOW_STOCK: '/admin/products',
    EXPENSES_TO_APPROVE: '/admin/accounting/expenses',
    ADJUSTMENTS_TO_APPROVE: '/admin/stock-adjustments',
    ORDERS_TO_DELIVER: '/admin/sales-orders',
    PURCHASES_TO_RECEIVE: '/admin/purchase-orders',
    OPEN_SHIFTS: '/admin/shifts',
  };

  /** A tile for an optional module (till shifts, expenses) is not shown while that module is switched off, because its list cannot be opened then. */
  tileOn(key: string): boolean {
    if (key === 'OPEN_SHIFTS') return this.modules.on('POS');
    if (key === 'EXPENSES_TO_APPROVE') return this.modules.on('EXPENSES');
    return true;
  }

  attentionVisible(): number {
    let n = 0;
    for (const a of this.attentionShown) if (this.tileOn(a.key)) n++;
    return n;
  }

  linkFor(key: string): string {
    return AdminDashboardComponent.LINKS[key] ?? '/admin/dashboard';
  }

  /** "12% more", "5% less", "the same", or "new" when there was nothing to compare with. */
  private static delta(now: number, before: number): { text: string; cls: string } {
    if (before <= 0) return now > 0 ? { text: 'New activity', cls: 'delta-up' } : { text: 'No sales', cls: 'delta-flat' };
    const pct = Math.round(((now - before) / before) * 100);
    if (pct === 0) return { text: 'About the same', cls: 'delta-flat' };
    return pct > 0 ? { text: '▲ ' + pct + '% more', cls: 'delta-up' } : { text: '▼ ' + Math.abs(pct) + '% less', cls: 'delta-down' };
  }

  loadOverview(): void {
    this.dashboardApi.overview(this.selectedBranchId).subscribe({
      next: (o) => {
        this.overview = o;
        this.attentionShown = o.attention.filter((a) => a.count > 0);
        this.todayDelta = AdminDashboardComponent.delta(o.comparison.today, o.comparison.sameDayLastWeek);
        this.weekDelta = AdminDashboardComponent.delta(o.comparison.last7, o.comparison.previous7);
        const max = Math.max(1, ...o.days.map((d) => d.pos + d.invoiced));
        this.bars = o.days.map((d) => ({
          label: d.date.substring(8, 10),
          posPct: (d.pos / max) * 100,
          invPct: (d.invoiced / max) * 100,
          title: d.date + ': till ' + d.pos.toFixed(2) + ' (' + d.posCount + '), invoices ' + d.invoiced.toFixed(2) + ' (' + d.invoiceCount + ')',
        }));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the overview'),
    });
  }

  loadSummary(): void {
    this.error = null;
    this.loadOverview();
    this.dashboardApi.summary(this.selectedBranchId).subscribe({
      next: (s) => (this.summary = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load dashboard'),
    });
  }

  loadWarehouseLowStock(): void {
    this.warehouseLowStockError = null;
    this.warehouseLowStock = [];
    if (this.selectedWarehouseId === null) return;
    this.catalogApi.listLowStockInWarehouse(this.selectedWarehouseId).subscribe({
      next: (rows) => (this.warehouseLowStock = rows),
      error: (err) => (this.warehouseLowStockError = err?.error?.message ?? 'Failed to load low stock for this warehouse'),
    });
  }
}
