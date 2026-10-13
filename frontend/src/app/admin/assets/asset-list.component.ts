import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AssetApiService, AssetStatus, AssetSummary, DepreciationRunResult, FixedAsset } from '../../core/api/asset-api.service';

/**
 * Assets > Asset register: every fixed asset with its cost, depreciation so far and book value, plus the "Run
 * depreciation" action (the same thing the daily scheduled job does for months that have ended).
 */
@Component({
  selector: 'app-asset-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Asset register</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/assets/categories">Categories</a>
          <a class="btn btn-outline-primary" routerLink="/admin/assets/from-purchase">From a purchase</a>
          <a class="btn btn-primary" routerLink="/admin/assets/new">+ New asset</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="runMessage">{{ runMessage }}</div>

      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;" *ngIf="summary">
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Assets</div><strong>{{ summary.assetCount }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Total cost</div><strong>{{ summary.totalCost | number: '1.2-2' }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Accumulated depreciation</div><strong>{{ summary.totalAccumulatedDepreciation | number: '1.2-2' }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Book value</div><strong>{{ summary.totalBookValue | number: '1.2-2' }}</strong></div>
      </div>

      <div class="card mb-3" style="padding:0.75rem 1rem;">
        <div class="flex align-center gap-3" style="flex-wrap:wrap;">
          <strong>Run depreciation</strong>
          <label class="flex align-center gap-2">Up to
            <app-date-picker name="upTo" [(ngModel)]="upTo"></app-date-picker>
          </label>
          <button class="btn btn-outline-primary" type="button" (click)="run()" [disabled]="running">{{ running ? 'Running…' : 'Run now' }}</button>
          <span class="text-muted" style="font-size:0.85rem;">
            Charges every full month up to that date that has not been charged yet, and posts it to Accounting. This also runs by itself every day for months that have ended.
          </span>
        </div>
      </div>

      <div class="flex gap-2 mb-2 align-center">
        <label>Show</label>
        <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchId" (ngModelChange)="load()" name="branch">
          <option [ngValue]="null">All branches</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" [(ngModel)]="status" (ngModelChange)="load()" name="status">
          <option value="">All assets</option>
          <option value="ACTIVE">Active</option>
          <option value="FULLY_DEPRECIATED">Fully depreciated</option>
          <option value="DISPOSED">Disposed</option>
        </select>
      </div>

      <table class="table-sm">
        <thead>
          <tr>
            <th>Tag</th><th>Asset</th><th>Category</th><th>Purchased</th>
            <th class="text-end">Cost</th><th class="text-end">Accumulated</th><th class="text-end">Book value</th>
            <th *ngIf="branches.length > 1">Branch</th><th>Location</th><th>Status</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of assets">
            <td><a [routerLink]="['/admin/assets', a.id]">{{ a.code }}</a></td>
            <td>{{ a.name }}</td>
            <td>{{ a.categoryName }}</td>
            <td>{{ a.purchaseDate }}</td>
            <td class="text-end">{{ a.cost | number: '1.2-2' }}</td>
            <td class="text-end">{{ a.accumulatedDepreciation | number: '1.2-2' }}</td>
            <td class="text-end">{{ a.bookValue | number: '1.2-2' }}</td>
            <td *ngIf="branches.length > 1">{{ a.branchName || '—' }}</td>
            <td>{{ a.location || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="a.status === 'ACTIVE'">Active</span>
              <span class="badge badge-secondary" *ngIf="a.status === 'FULLY_DEPRECIATED'">Fully depreciated</span>
              <span class="badge badge-secondary" *ngIf="a.status === 'DISPOSED'">Disposed</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/assets', a.id]">Open</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="assets.length === 0 && !error">
        No assets yet — add the asset categories first (with their depreciation terms), then register your equipment, vehicles and furniture.
      </p>
    </app-shell>
  `,
})
export class AssetListComponent implements OnInit {
  assets: FixedAsset[] = [];
  summary: AssetSummary | null = null;
  status: AssetStatus | '' = '';
  branchId: number | null = null;
  branches: Branch[] = [];
  error: string | null = null;
  runMessage: string | null = null;
  running = false;
  upTo = AssetListComponent.endOfLastMonth();

  constructor(private api: AssetApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  load(): void {
    this.api.list(this.status, this.branchId).subscribe({
      next: (a) => (this.assets = a),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load assets'),
    });
    this.api.summary(this.branchId).subscribe({ next: (s) => (this.summary = s), error: () => {} });
  }

  run(): void {
    this.error = this.runMessage = null;
    this.running = true;
    this.api.runDepreciation(this.upTo || null).subscribe({
      next: (r: DepreciationRunResult) => {
        this.running = false;
        this.runMessage = r.entriesCreated
          ? `Depreciation posted up to ${r.upTo}: ${r.entriesCreated} charge(s) over ${r.monthsPosted} month(s), total ${r.totalAmount}.`
          : `Nothing to charge up to ${r.upTo} — depreciation is already up to date.`;
        this.load();
      },
      error: (e) => {
        this.running = false;
        this.error = e?.error?.message ?? 'Could not run depreciation';
      },
    });
  }

  private static endOfLastMonth(): string {
    const d = new Date();
    const last = new Date(d.getFullYear(), d.getMonth(), 0);
    const mm = String(last.getMonth() + 1).padStart(2, '0');
    const dd = String(last.getDate()).padStart(2, '0');
    return `${last.getFullYear()}-${mm}-${dd}`;
  }
}
