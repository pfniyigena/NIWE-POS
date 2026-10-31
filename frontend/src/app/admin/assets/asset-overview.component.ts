import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AssetApiService, AssetOverview, AttentionItem } from '../../core/api/asset-api.service';

/** Assets > Overview: what the assets are worth, what depreciation will charge next, and what needs attention. */
@Component({
  selector: 'app-asset-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Assets overview</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <select class="form-select" *ngIf="branches.length > 1" [(ngModel)]="branchId" (ngModelChange)="load()" name="branch" style="width:auto;">
            <option [ngValue]="null">All branches</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <a class="btn btn-outline-primary" routerLink="/admin/assets/settings">Settings</a>
          <a class="btn btn-outline-primary" routerLink="/admin/assets">Asset register</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="o">
        <div class="alert alert-warning" *ngIf="o.monthsBehind > 0">
          Depreciation is {{ o.monthsBehind }} month(s) behind (charged up to {{ o.depreciatedThrough || 'nothing yet' }}).
          <a routerLink="/admin/assets">Run it from the register</a>.
        </div>

        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Assets in use</div><strong>{{ o.assetCount }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Total cost</div><strong>{{ o.totalCost | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Accumulated depreciation</div><strong>{{ o.totalAccumulated | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Book value</div><strong>{{ o.totalBookValue | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Next month's depreciation</div><strong>{{ o.nextMonthCharge | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Fully depreciated</div><strong>{{ o.fullyDepreciatedCount }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Disposed</div><strong>{{ o.disposedCount }}</strong></div>
          <a class="card" style="padding:0.75rem 1rem; text-decoration:none; color:inherit;" routerLink="/admin/assets" [queryParams]="{ view: 'no-custodian' }">
            <div class="text-muted">Nobody holding</div><strong>{{ o.withoutCustodian }}</strong></a>
          <a class="card" style="padding:0.75rem 1rem; text-decoration:none; color:inherit;" routerLink="/admin/assets/verifications">
            <div class="text-muted">Open verification rounds</div><strong>{{ o.openVerifications }}</strong></a>
        </div>

        <div class="flex gap-4" style="flex-wrap:wrap; align-items:flex-start;">
          <div style="flex:1; min-width:22rem;">
            <h4 class="mb-2">By category</h4>
            <table class="table-sm mb-3">
              <thead><tr><th>Category</th><th class="text-end">Assets</th><th class="text-end">Cost</th><th class="text-end">Book value</th></tr></thead>
              <tbody>
                <tr *ngFor="let r of o.byCategory"><td>{{ r.name }}</td><td class="text-end">{{ r.count }}</td><td class="text-end">{{ r.cost | number: '1.2-2' }}</td><td class="text-end">{{ r.bookValue | number: '1.2-2' }}</td></tr>
                <tr *ngIf="!o.byCategory.length"><td colspan="4" class="text-muted">No assets yet.</td></tr>
              </tbody>
            </table>
          </div>
          <div style="flex:1; min-width:22rem;" *ngIf="o.byBranch.length > 1">
            <h4 class="mb-2">By branch</h4>
            <table class="table-sm mb-3">
              <thead><tr><th>Branch</th><th class="text-end">Assets</th><th class="text-end">Cost</th><th class="text-end">Book value</th></tr></thead>
              <tbody>
                <tr *ngFor="let r of o.byBranch"><td>{{ r.name }}</td><td class="text-end">{{ r.count }}</td><td class="text-end">{{ r.cost | number: '1.2-2' }}</td><td class="text-end">{{ r.bookValue | number: '1.2-2' }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="flex gap-4" style="flex-wrap:wrap; align-items:flex-start;">
          <div style="flex:1; min-width:20rem;">
            <h4 class="mb-2">Warranty ending in {{ o.alertDays }} days</h4>
            <ng-container *ngTemplateOutlet="list; context: { $implicit: o.warrantyExpiring }"></ng-container>
          </div>
          <div style="flex:1; min-width:20rem;">
            <h4 class="mb-2">Insurance ending in {{ o.alertDays }} days</h4>
            <ng-container *ngTemplateOutlet="list; context: { $implicit: o.insuranceExpiring }"></ng-container>
          </div>
          <div style="flex:1; min-width:20rem;">
            <h4 class="mb-2">Fully depreciated within {{ o.soonMonths }} months</h4>
            <ng-container *ngTemplateOutlet="list; context: { $implicit: o.fullyDepreciatingSoon }"></ng-container>
          </div>
        </div>
      </ng-container>

      <ng-template #list let-items>
        <table class="table-sm mb-3">
          <tbody>
            <tr *ngFor="let i of items">
              <td><a [routerLink]="['/admin/assets', i.id]">{{ i.code }}</a></td>
              <td>{{ i.name }}</td>
              <td>{{ i.detail }}<span *ngIf="i.date"> — {{ i.date }}</span></td>
            </tr>
            <tr *ngIf="!items.length"><td class="text-muted">Nothing to watch.</td></tr>
          </tbody>
        </table>
      </ng-template>
    </app-shell>
  `,
})
export class AssetOverviewComponent implements OnInit {
  o: AssetOverview | null = null;
  branches: Branch[] = [];
  branchId: number | null = null;
  error: string | null = null;

  constructor(private api: AssetApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  load(): void {
    this.api.overview(this.branchId).subscribe({
      next: (o) => (this.o = o),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the overview'),
    });
  }
}
export type { AttentionItem };
