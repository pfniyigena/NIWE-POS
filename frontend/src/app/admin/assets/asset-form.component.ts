import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AcquisitionFunding, AssetApiService, AssetCategory, DepreciationMethod, FixedAsset } from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
/**
 * Assets > New / Edit asset. Method, useful life and salvage value start from the chosen category and can be changed
 * for this asset. Once depreciation or the purchase has been posted, cost, dates and depreciation terms are locked.
 */
@Component({
  selector: 'app-asset-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? ('Edit asset' | translate) : ('New asset' | translate) }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-info" *ngIf="locked">
        {{ 'Depreciation or the purchase posting already exists for this asset, so cost, dates and depreciation terms can\\'t be changed here. Use' | translate }} <em>{{ 'Add improvement' | translate }}</em>, <em>{{ 'Write value down' | translate }}</em> {{ 'or' | translate }} <em>{{ 'Change terms' | translate }}</em> {{ 'on the asset page, and edit the details below.' | translate }}
      </div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Asset name' | translate }}</label>
          <input class="form-control" name="name" [(ngModel)]="f.name" [placeholder]="'e.g. Delivery van' | translate" required />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Tag (optional)' | translate }}</label>
          <input class="form-control" name="code" [(ngModel)]="f.code" [placeholder]="'Left empty, one is generated (FA-0001…)' | translate" />
        </div>
        <div class="form-group" *ngIf="!id && branches.length > 1">
          <label class="form-label">{{ 'Branch' | translate }}</label>
          <select class="form-select" name="branchId" [(ngModel)]="f.branchId">
            <option [ngValue]="null">{{ 'Head office' | translate }}</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Use' | translate }} <em>{{ 'Transfer' | translate }}</em> {{ 'on the asset page to move it to another branch later.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Category' | translate }}</label>
          <select class="form-select" name="categoryId" [(ngModel)]="f.categoryId" (ngModelChange)="onCategory()" [disabled]="locked" required>
            <option [ngValue]="null" disabled>{{ 'Select a category…' | translate }}</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="categories.length === 0">
            {{ 'No categories yet —' | translate }} <a routerLink="/admin/assets/categories">{{ 'add one first' | translate }}</a>.
          </div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Purchase date' | translate }}</label>
            <app-date-picker name="purchaseDate" [(ngModel)]="f.purchaseDate" [disabled]="locked" required></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Cost' | translate }}</label>
            <input class="form-control" type="number" step="0.01" min="0.01" name="cost" [(ngModel)]="f.cost" [disabled]="locked" required /></div>
        </div>
        <div class="card mb-3" style="padding:0.75rem 1rem;">
          <strong class="mb-2">{{ 'Depreciation' | translate }}</strong>
          <div class="form-group">
            <label class="form-label">{{ 'Method' | translate }}</label>
            <select class="form-select" name="method" [(ngModel)]="f.method" [disabled]="locked">
              <option value="STRAIGHT_LINE">{{ 'Straight line (same amount each month)' | translate }}</option>
              <option value="DECLINING_BALANCE">{{ 'Declining balance (bigger early)' | translate }}</option>
            </select>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Useful life (months)' | translate }}</label>
              <input class="form-control" type="number" min="1" name="life" [(ngModel)]="f.usefulLifeMonths" [disabled]="locked" /></div>
            <div class="form-group"><label class="form-label">{{ 'Salvage value' | translate }}</label>
              <input class="form-control" type="number" step="0.01" min="0" name="salvage" [(ngModel)]="f.salvageValue" [disabled]="locked" /></div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ 'Start depreciating in' | translate }}</label>
            <app-date-picker name="startDate" [(ngModel)]="f.depreciationStartDate" [disabled]="locked"></app-date-picker>
            <div class="text-muted" style="font-size:0.8rem;">{{ f.prorateFirstMonth ? ('The first month is charged only for the days from this date.' | translate) : ('A full month is charged for the month of this date.' | translate) }}</div>
          </div>
          <div class="form-group">
            <label class="form-label"><input type="checkbox" name="prorate" [(ngModel)]="f.prorateFirstMonth" [disabled]="locked" /> {{ 'Prorate the first month by days' | translate }}</label>
          </div>
          <div class="form-group" *ngIf="!id">
            <label class="form-label">{{ 'Depreciation already charged before this system (optional)' | translate }}</label>
            <input class="form-control" type="number" step="0.01" min="0" name="opening" [(ngModel)]="f.openingAccumulatedDepreciation" />
            <div class="text-muted" style="font-size:0.8rem;">
              {{ 'For an asset you already own: set "Start depreciating in" to the first month still to be charged. This opening amount is not posted to Accounting — record it with a journal entry if you need it there.' | translate }}
            </div>
          </div>
        </div>
        <div class="form-group" *ngIf="!id">
          <label class="form-label">{{ 'Purchase posting' | translate }}</label>
          <select class="form-select" name="funding" [(ngModel)]="f.acquisitionFunding">
            <option value="NONE">{{ 'Don\\'t post (the purchase is already in the books)' | translate }}</option>
            <option value="CASH">{{ 'Post: paid in cash (Fixed Assets / Cash)' | translate }}</option>
            <option value="PAYABLE">{{ 'Post: owed to the supplier (Fixed Assets / Accounts Payable)' | translate }}</option>
          </select>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Location' | translate }}</label><input class="form-control" name="location" [(ngModel)]="f.location" /></div>
          <div class="form-group"><label class="form-label">{{ 'Held by' | translate }}</label><input class="form-control" name="assignedTo" [(ngModel)]="f.assignedTo" /></div>
          <div class="form-group"><label class="form-label">{{ 'Serial number' | translate }}</label><input class="form-control" name="serial" [(ngModel)]="f.serialNumber" /></div>
          <div class="form-group"><label class="form-label">{{ 'Supplier' | translate }}</label><input class="form-control" name="supplier" [(ngModel)]="f.supplierName" /></div>
        </div>
        <div class="card mb-3" style="padding:0.75rem 1rem;">
          <strong class="mb-2">{{ 'Warranty and insurance (optional)' | translate }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Warranty ends' | translate }}</label>
              <app-date-picker name="warrantyExpiresOn" [(ngModel)]="f.warrantyExpiresOn"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Insurance ends' | translate }}</label>
              <app-date-picker name="insuranceExpiresOn" [(ngModel)]="f.insuranceExpiresOn"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Insurance policy' | translate }}</label>
              <input class="form-control" name="insurancePolicy" [(ngModel)]="f.insurancePolicy" /></div>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'The register and the overview warn you before these dates.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Notes' | translate }}</label>
          <textarea class="form-control" rows="2" name="notes" [(ngModel)]="f.notes"></textarea>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" [routerLink]="id ? ['/admin/assets', id] : ['/admin/assets']">{{ 'Cancel' | translate }}</a>
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
    warrantyExpiresOn: null as string | null,
    insuranceExpiresOn: null as string | null,
    insurancePolicy: '' as string | null,
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
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load categories')),
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
          warrantyExpiresOn: a.warrantyExpiresOn, insuranceExpiresOn: a.insuranceExpiresOn, insurancePolicy: a.insurancePolicy,
        };
      },
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the asset')),
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
      this.error = T('Choose a category and enter the cost.');
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
      warrantyExpiresOn: this.f.warrantyExpiresOn || null,
      insuranceExpiresOn: this.f.insuranceExpiresOn || null,
      insurancePolicy: this.f.insurancePolicy || null,
    };
    this.saving = true;
    const call = this.id ? this.api.update(this.id, payload) : this.api.create(payload);
    call.subscribe({
      next: (a) => this.router.navigate(['/admin/assets', a.id]),
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message ?? T('Could not save the asset');
      },
    });
  }
}
