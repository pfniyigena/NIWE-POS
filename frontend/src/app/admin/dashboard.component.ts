import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { DashboardApiService } from '../core/api/dashboard-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch, DashboardSummary, Warehouse, WarehouseLowStockRow } from '../core/models';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
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
    private branchApi: BranchApiService
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

  loadSummary(): void {
    this.error = null;
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
