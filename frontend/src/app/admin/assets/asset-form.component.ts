import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AcquisitionFunding, AssetApiService, AssetCategory, DepreciationMethod, FixedAsset } from '../../core/api/asset-api.service';

/**
 * Assets > New / Edit asset. Method, useful life and salvage value start from the chosen category and can be changed
 * for this asset. Once depreciation or the purchase has been posted, cost, dates and depreciation terms are locked.
 */
@Component({
  selector: 'app-asset-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit asset' : 'New asset' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-info" *ngIf="locked">
        Depreciation or the purchase posting already exists for this asset, so cost, dates and depreciation terms can't be changed. You can still edit the details below.
      </div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Asset name</label>
          <input class="form-control" name="name" [(ngModel)]="f.name" placeholder="e.g. Delivery van" required />
        </div>
        <div class="form-group">
          <label class="form-label">Tag (optional)</label>
          <input class="form-control" name="code" [(ngModel)]="f.code" placeholder="Left empty, one is generated (FA-0001…)" />
        </div>
        <div class="form-group" *ngIf="!id && branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-select" name="branchId" [(ngModel)]="f.branchId">
            <option [ngValue]="null">Head office</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;">Use <em>Transfer</em> on the asset page to move it to another branch later.</div>
        </div>
        <div class="form-group">
          <label class="form-label">Category</label>
          <select class="form-select" name="categoryId" [(ngModel)]="f.categoryId" (ngModelChange)="onCategory()" [disabled]="locked" required>
            <option [ngValue]="null" disabled>Select a category…</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="categories.length === 0">
            No categories yet — <a routerLink="/admin/assets/categories">add one first</a>.
          </div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Purchase date</label>
            <app-date-picker name="purchaseDate" [(ngModel)]="f.purchaseDate" [disabled]="locked" required></app-date-picker></div>
          <div class="form-group"><label class="form-label">Cost</label>
            <input class="form-control" type="number" step="0.01" min="0.01" name="cost" [(ngModel)]="f.cost" [disabled]="locked" required /></div>
        </div>
        <div class="card mb-3" style="padding:0.75rem 1rem;">
          <strong class="mb-2">Depreciation</strong>
          <div class="form-group">
            <label class="form-label">Method</label>
            <select class="form-select" name="method" [(ngModel)]="f.method" [disabled]="locked">
              <option value="STRAIGHT_LINE">Straight line (same amount each month)</option>
              <option value="DECLINING_BALANCE">Declining balance (bigger early)</option>
            </select>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">Useful life (months)</label>
              <input class="form-control" type="number" min="1" name="life" [(ngModel)]="f.usefulLifeMonths" [disabled]="locked" /></div>
            <div class="form-group"><label class="form-label">Salvage value</label>
              <input class="form-control" type="number" step="0.01" min="0" name="salvage" [(ngModel)]="f.salvageValue" [disabled]="locked" /></div>
          </div>
          <div class="form-group">
            <label class="form-label">Start depreciating in</label>
            <app-date-picker name="startDate" [(ngModel)]="f.depreciationStartDate" [disabled]="locked"></app-date-picker>
            <div class="text-muted" style="font-size:0.8rem;">{{ f.prorateFirstMonth ? 'The first month is charged only for the days from this date.' : 'A full month is charged for the month of this date.' }}</div>
          </div>
          <div class="form-group">
            <label class="form-label"><input type="checkbox" name="prorate" [(ngModel)]="f.prorateFirstMonth" [disabled]="locked" /> Prorate the first month by days</label>
          </div>
          <div class="form-group" *ngIf="!id">
            <label class="form-label">Depreciation already charged before this system (optional)</label>
            <input class="form-control" type="number" step="0.01" min="0" name="opening" [(ngModel)]="f.openingAccumulatedDepreciation" />
            <div class="text-muted" style="font-size:0.8rem;">
              For an asset you already own: set "Start depreciating in" to the first month still to be charged. This opening amount is not posted to Accounting — record it with a journal entry if you need it there.
            </div>
          </div>
        </div>
        <div class="form-group" *ngIf="!id">
          <label class="form-label">Purchase posting</label>
          <select class="form-select" name="funding" [(ngModel)]="f.acquisitionFunding">
            <option value="NONE">Don't post (the purchase is already in the books)</option>
            <option value="CASH">Post: paid in cash (Fixed Assets / Cash)</option>
            <option value="PAYABLE">Post: owed to the supplier (Fixed Assets / Accounts Payable)</option>
          </select>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Location</label><input class="form-control" name="location" [(ngModel)]="f.location" /></div>
          <div class="form-group"><label class="form-label">Assigned to</label><input class="form-control" name="assignedTo" [(ngModel)]="f.assignedTo" /></div>
          <div class="form-group"><label class="form-label">Serial number</label><input class="form-control" name="serial" [(ngModel)]="f.serialNumber" /></div>
          <div class="form-group"><label class="form-label">Supplier</label><input class="form-control" name="supplier" [(ngModel)]="f.supplierName" /></div>
        </div>
        <div class="form-group">
          <label class="form-label">Notes</label>
          <textarea class="form-control" rows="2" name="notes" [(ngModel)]="f.notes"></textarea>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">Save</button>
          <a class="btn btn-link" [routerLink]="id ? ['/admin/assets', id] : ['/admin/assets']">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class AssetFormComponent implements OnInit {
  id: number | null = null;
  categories: AssetCategory[] = [];
  branches: Branch[] = [];
  locked = false;
  saving = false;
  error: string | null = null;
  f = {
    code: '' as string | null,
    name: '',
    categoryId: null as number | null,
    purchaseDate: new Date().toISOString().slice(0, 10),
    cost: null as number | null,
    salvageValue: null as number | null,
    usefulLifeMonths: null as number | null,
    method: 'STRAIGHT_LINE' as DepreciationMethod,
    depreciationStartDate: null as string | null,
    location: '' as string | null,
    assignedTo: '' as string | null,
    serialNumber: '' as string | null,
    supplierName: '' as string | null,
    notes: '' as string | null,
    acquisitionFunding: 'NONE' as AcquisitionFunding,
    openingAccumulatedDepreciation: null as number | null,
    prorateFirstMonth: false,
    branchId: null as number | null,
  };

  constructor(private api: AssetApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const idParam = this.route.snapshot.paramMap.get('id');
    this.api.categories().subscribe({
      next: (c) => {
        this.categories = c.filter((x) => x.active);
        if (idParam) this.loadAsset(Number(idParam), c);
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load categories'),
    });
  }

  private loadAsset(id: number, all: AssetCategory[]): void {
    this.id = id;
    this.api.get(id).subscribe({
      next: (a: FixedAsset) => {
        this.locked = a.hasDepreciation || a.acquisitionPosted;
        // keep the asset's own category selectable even if it was made inactive since
        const own = all.find((c) => c.id === a.categoryId);
        if (own && !this.categories.some((c) => c.id === own.id)) this.categories = [...this.categories, own];
        this.f = {
          ...this.f,
          code: a.code, name: a.name, categoryId: a.categoryId, purchaseDate: a.purchaseDate, cost: a.cost,
          salvageValue: a.salvageValue, usefulLifeMonths: a.usefulLifeMonths, method: a.method,
          depreciationStartDate: a.depreciationStartDate, location: a.location, assignedTo: a.assignedTo,
          serialNumber: a.serialNumber, supplierName: a.supplierName, notes: a.notes, prorateFirstMonth: a.prorateFirstMonth,
        };
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the asset'),
    });
  }

  /** New asset: pre-fill the depreciation terms from the category. */
  onCategory(): void {
    if (this.id) return;
    const c = this.categories.find((x) => x.id === this.f.categoryId);
    if (!c) return;
    this.f.method = c.method;
    this.f.usefulLifeMonths = c.usefulLifeMonths;
    this.f.prorateFirstMonth = c.prorateFirstMonth;
    if (this.f.cost) this.f.salvageValue = Math.round(this.f.cost * c.salvagePercent) / 100;
  }

  submit(): void {
    this.error = null;
    if (!this.f.categoryId || !this.f.cost) {
      this.error = 'Choose a category and enter the cost.';
      return;
    }
    const payload = {
      code: this.f.code || null,
      name: this.f.name,
      categoryId: this.f.categoryId,
      purchaseDate: this.f.purchaseDate,
      cost: this.f.cost,
      salvageValue: this.f.salvageValue,
      usefulLifeMonths: this.f.usefulLifeMonths,
      method: this.f.method,
      depreciationStartDate: this.f.depreciationStartDate || null,
      location: this.f.location || null,
      assignedTo: this.f.assignedTo || null,
      serialNumber: this.f.serialNumber || null,
      supplierName: this.f.supplierName || null,
      notes: this.f.notes || null,
      acquisitionFunding: this.f.acquisitionFunding,
      openingAccumulatedDepreciation: this.f.openingAccumulatedDepreciation,
      prorateFirstMonth: this.f.prorateFirstMonth,
      branchId: this.f.branchId,
    };
    this.saving = true;
    const call = this.id ? this.api.update(this.id, payload) : this.api.create(payload);
    call.subscribe({
      next: (a) => this.router.navigate(['/admin/assets', a.id]),
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message ?? 'Could not save the asset';
      },
    });
  }
}
