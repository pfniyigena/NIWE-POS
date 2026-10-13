import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AssetApiService, AssetCategory, DepreciationMethod } from '../../core/api/asset-api.service';

/** Assets > Categories: the kinds of asset and the depreciation terms new assets of that kind start with. */
@Component({
  selector: 'app-asset-category-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Asset categories</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/assets">Back to the register</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <p class="text-muted">
        Each category sets the depreciation method, useful life and salvage value that a new asset of that kind starts with.
        Changing a category later does not change assets already registered.
      </p>
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
        <strong class="mb-2">{{ editingId ? 'Edit category' : 'New category' }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="form.name" placeholder="e.g. Computers" required /></div>
          <div class="form-group"><label class="form-label">Method</label>
            <select class="form-select" name="method" [(ngModel)]="form.method">
              <option value="STRAIGHT_LINE">Straight line (same amount each month)</option>
              <option value="DECLINING_BALANCE">Declining balance (bigger early)</option>
            </select></div>
          <div class="form-group"><label class="form-label">Useful life (months)</label>
            <input class="form-control" type="number" min="1" name="life" [(ngModel)]="form.usefulLifeMonths" required style="width:8rem;" /></div>
          <div class="form-group"><label class="form-label">Salvage value (% of cost)</label>
            <input class="form-control" type="number" min="0" max="99.99" step="0.01" name="salvage" [(ngModel)]="form.salvagePercent" style="width:8rem;" /></div>
          <div class="form-group"><label class="form-label"><input type="checkbox" name="prorate" [(ngModel)]="form.prorateFirstMonth" /> Prorate the first month by days</label></div>
          <div class="form-group" *ngIf="editingId"><label class="form-label"><input type="checkbox" name="active" [(ngModel)]="form.active" /> Active</label></div>
          <div class="flex gap-2">
            <button class="btn btn-primary" type="submit">Save</button>
            <button class="btn btn-link" type="button" *ngIf="editingId" (click)="reset()">Cancel</button>
          </div>
        </div>
      </form>
      <table class="table-sm">
        <thead><tr><th>Name</th><th>Method</th><th>Useful life</th><th>Salvage</th><th>First month</th><th>Assets</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of categories">
            <td>{{ c.name }}</td>
            <td>{{ c.method === 'STRAIGHT_LINE' ? 'Straight line' : 'Declining balance' }}</td>
            <td>{{ c.usefulLifeMonths }} months</td>
            <td>{{ c.salvagePercent }}%</td>
            <td>{{ c.prorateFirstMonth ? 'By days' : 'Full month' }}</td>
            <td>{{ c.assetCount }}</td>
            <td><span class="badge" [class.badge-success]="c.active" [class.badge-secondary]="!c.active">{{ c.active ? 'Active' : 'Inactive' }}</span></td>
            <td><button class="btn btn-sm btn-outline-primary" type="button" (click)="edit(c)">Edit</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="categories.length === 0">No categories yet — for example Computers (36 months), Vehicles (60 months), Furniture (84 months).</p>
    </app-shell>
  `,
})
export class AssetCategoryListComponent implements OnInit {
  categories: AssetCategory[] = [];
  error: string | null = null;
  editingId: number | null = null;
  form = AssetCategoryListComponent.blank();

  constructor(private api: AssetApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private static blank() {
    return { name: '', method: 'STRAIGHT_LINE' as DepreciationMethod, usefulLifeMonths: 60, salvagePercent: 0, prorateFirstMonth: false, active: true };
  }

  load(): void {
    this.api.categories().subscribe({
      next: (c) => (this.categories = c),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load categories'),
    });
  }

  edit(c: AssetCategory): void {
    this.editingId = c.id;
    this.form = { name: c.name, method: c.method, usefulLifeMonths: c.usefulLifeMonths, salvagePercent: c.salvagePercent, prorateFirstMonth: c.prorateFirstMonth, active: c.active };
  }

  reset(): void {
    this.editingId = null;
    this.form = AssetCategoryListComponent.blank();
  }

  save(): void {
    this.error = null;
    const call = this.editingId ? this.api.updateCategory(this.editingId, this.form) : this.api.createCategory(this.form);
    call.subscribe({
      next: () => {
        this.reset();
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the category'),
    });
  }
}
