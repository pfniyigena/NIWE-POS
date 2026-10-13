import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AssetApiService, AssetCategory, PurchaseLine } from '../../core/api/asset-api.service';

/**
 * Assets > From purchases: turn goods received on a purchase order into fixed assets. The receipt already put the cost into
 * Inventory, so registering moves it to Fixed Assets (nothing is counted twice). One asset is created per unit.
 */
@Component({
  selector: 'app-asset-from-purchase',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Register assets from purchases</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/assets">Back to the register</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <p class="text-muted">
        Pick a received purchase order line. The cost moves from Inventory to Fixed Assets, one asset per unit, each at the line's
        cost without tax. Buy assets under a product that has <strong>Track stock</strong> switched off, so they don't stay in the stock count.
      </p>

      <div class="card mb-3" style="padding:1rem;" *ngIf="picked">
        <strong class="mb-2">{{ picked.productName }} — {{ picked.purchaseOrderNumber }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Category</label>
            <select class="form-select" name="categoryId" [(ngModel)]="f.categoryId" (ngModelChange)="onCategory()" required>
              <option [ngValue]="null" disabled>Select…</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">How many (max {{ remaining(picked) }})</label>
            <input class="form-control" type="number" min="1" [max]="remaining(picked)" name="qty" [(ngModel)]="f.quantity" style="width:7rem;" /></div>
          <div class="form-group"><label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="f.name" /></div>
          <div class="form-group"><label class="form-label">Location</label>
            <input class="form-control" name="location" [(ngModel)]="f.location" /></div>
          <div class="form-group"><label class="form-label">Assigned to</label>
            <input class="form-control" name="assignedTo" [(ngModel)]="f.assignedTo" /></div>
          <div class="form-group"><label class="form-label"><input type="checkbox" name="prorate" [(ngModel)]="f.prorateFirstMonth" /> Prorate first month</label></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">
          Cost per asset {{ picked.unitNetCost | number: '1.2-2' }} — total {{ picked.unitNetCost * (f.quantity || 0) | number: '1.2-2' }}.
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="button" (click)="register()" [disabled]="busy">Register</button>
          <button class="btn btn-link" type="button" (click)="picked = null">Cancel</button>
        </div>
      </div>

      <table class="table-sm">
        <thead>
          <tr><th>Purchase order</th><th>Date</th><th>Supplier</th><th>Product</th><th class="text-end">Received</th><th class="text-end">Registered</th><th class="text-end">Cost each</th><th></th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let l of lines">
            <td>{{ l.purchaseOrderNumber }}</td>
            <td>{{ l.orderDate }}</td>
            <td>{{ l.supplierName || '—' }}</td>
            <td>{{ l.productName }}</td>
            <td class="text-end">{{ l.quantityReceived }}</td>
            <td class="text-end">{{ l.quantityRegistered }}</td>
            <td class="text-end">{{ l.unitNetCost | number: '1.2-2' }}</td>
            <td>
              <span class="text-muted" *ngIf="l.stockTracked" title="Track stock is on for this product">Tracks stock</span>
              <span class="text-muted" *ngIf="!l.stockTracked && remaining(l) === 0">Done</span>
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="!l.stockTracked && remaining(l) > 0" (click)="pick(l)">Register…</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="lines.length === 0 && !error">No received purchase order lines yet.</p>
    </app-shell>
  `,
})
export class AssetFromPurchaseComponent implements OnInit {
  lines: PurchaseLine[] = [];
  categories: AssetCategory[] = [];
  picked: PurchaseLine | null = null;
  error: string | null = null;
  busy = false;
  f = { categoryId: null as number | null, quantity: 1, name: '', location: '', assignedTo: '', prorateFirstMonth: false };

  constructor(private api: AssetApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.purchaseLines().subscribe({
      next: (l) => (this.lines = l),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load purchase lines'),
    });
    this.api.categories().subscribe({ next: (c) => (this.categories = c.filter((x) => x.active)), error: () => {} });
  }

  remaining(l: PurchaseLine): number {
    return Math.max(0, l.quantityReceived - l.quantityRegistered);
  }

  pick(l: PurchaseLine): void {
    this.picked = l;
    this.f = { categoryId: null, quantity: this.remaining(l), name: l.productName, location: '', assignedTo: '', prorateFirstMonth: false };
  }

  onCategory(): void {
    const c = this.categories.find((x) => x.id === this.f.categoryId);
    if (c) this.f.prorateFirstMonth = c.prorateFirstMonth;
  }

  register(): void {
    if (!this.picked || !this.f.categoryId) {
      this.error = 'Choose a category.';
      return;
    }
    this.error = null;
    this.busy = true;
    this.api
      .fromPurchase({
        purchaseOrderLineId: this.picked.lineId,
        quantity: this.f.quantity,
        categoryId: this.f.categoryId,
        name: this.f.name || null,
        purchaseDate: null,
        location: this.f.location || null,
        assignedTo: this.f.assignedTo || null,
        notes: null,
        prorateFirstMonth: this.f.prorateFirstMonth,
      })
      .subscribe({
        next: () => this.router.navigate(['/admin/assets']),
        error: (e) => {
          this.busy = false;
          this.error = e?.error?.message ?? 'Could not register the assets';
        },
      });
  }
}
