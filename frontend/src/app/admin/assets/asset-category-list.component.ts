import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { Account } from '../../core/models';
import { AssetApiService, AssetCategory, DepreciationMethod } from '../../core/api/asset-api.service';

/** Assets > Categories: the kinds of asset and the depreciation terms new assets of that kind start with. */
@Component({
  selector: 'app-asset-category-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Asset categories' | translate }}</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/assets">{{ 'Back to the register' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <p class="text-muted">
        {{ 'Each category sets the depreciation method, useful life and salvage value that a new asset of that kind starts with. Changing a category later does not change assets already registered. You can also give a category its own accounts (for example one for Vehicles and another for Computers); left empty, the standard Fixed Assets, Accumulated Depreciation and Depreciation Expense accounts are used.' | translate }}
      </p>
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
        <strong class="mb-2">{{ editingId ? ('Edit category' | translate) : ('New category' | translate) }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label>
            <input class="form-control" name="name" [(ngModel)]="form.name" [placeholder]="'e.g. Computers' | translate" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Method' | translate }}</label>
            <select class="form-select" name="method" [(ngModel)]="form.method">
              <option value="STRAIGHT_LINE">{{ 'Straight line (same amount each month)' | translate }}</option>
              <option value="DECLINING_BALANCE">{{ 'Declining balance (bigger early)' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Useful life (months)' | translate }}</label>
            <input class="form-control" type="number" min="1" name="life" [(ngModel)]="form.usefulLifeMonths" required style="width:8rem;" /></div>
          <div class="form-group"><label class="form-label">{{ 'Salvage value (% of cost)' | translate }}</label>
            <input class="form-control" type="number" min="0" max="99.99" step="0.01" name="salvage" [(ngModel)]="form.salvagePercent" style="width:8rem;" /></div>
          <div class="form-group"><label class="form-label"><input type="checkbox" name="prorate" [(ngModel)]="form.prorateFirstMonth" /> {{ 'Prorate the first month by days' | translate }}</label></div>
          <div class="form-group"><label class="form-label">{{ 'Asset account' | translate }}</label>
            <select class="form-select" name="assetAccount" [(ngModel)]="form.assetAccountId" [disabled]="locked">
              <option [ngValue]="null">{{ 'Standard (Fixed Assets)' | translate }}</option>
              <option *ngFor="let a of assetAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Accumulated depreciation account' | translate }}</label>
            <select class="form-select" name="accumAccount" [(ngModel)]="form.accumulatedAccountId" [disabled]="locked">
              <option [ngValue]="null">{{ 'Standard (Accumulated Depreciation)' | translate }}</option>
              <option *ngFor="let a of assetAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Depreciation expense account' | translate }}</label>
            <select class="form-select" name="expenseAccount" [(ngModel)]="form.expenseAccountId" [disabled]="locked">
              <option [ngValue]="null">{{ 'Standard (Depreciation Expense)' | translate }}</option>
              <option *ngFor="let a of expenseAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
            </select></div>
          <div class="text-muted" style="flex-basis:100%; font-size:0.8rem;" *ngIf="locked">{{ 'This category already has assets, so its accounts can\\'t be changed.' | translate }}</div>
          <div class="form-group" *ngIf="editingId"><label class="form-label"><input type="checkbox" name="active" [(ngModel)]="form.active" /> {{ 'Active' | translate }}</label></div>
          <div class="flex gap-2">
            <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
            <button class="btn btn-link" type="button" *ngIf="editingId" (click)="reset()">{{ 'Cancel' | translate }}</button>
          </div>
        </div>
      </form>
      <table class="table-sm">
        <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'Method' | translate }}</th><th>{{ 'Useful life' | translate }}</th><th>{{ 'Salvage' | translate }}</th><th>{{ 'First month' | translate }}</th><th>{{ 'Accounts' | translate }}</th><th>{{ 'Assets' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of categories">
            <td>{{ c.name }}</td>
            <td>{{ c.method === 'STRAIGHT_LINE' ? ('Straight line' | translate) : ('Declining balance' | translate) }}</td>
            <td>{{ '{p0} months' | translate: { p0: (c.usefulLifeMonths) } }}</td>
            <td>{{ c.salvagePercent }}%</td>
            <td>{{ c.prorateFirstMonth ? ('By days' | translate) : ('Full month' | translate) }}</td>
            <td style="font-size:0.8rem;">
              <span *ngIf="!c.assetAccountName && !c.accumulatedAccountName && !c.expenseAccountName" class="text-muted">{{ 'Standard' | translate }}</span>
              <span *ngIf="c.assetAccountName">{{ c.assetAccountName }}</span><span *ngIf="c.accumulatedAccountName"> / {{ c.accumulatedAccountName }}</span><span *ngIf="c.expenseAccountName"> / {{ c.expenseAccountName }}</span>
            </td>
            <td>{{ c.assetCount }}</td>
            <td><span class="badge" [class.badge-success]="c.active" [class.badge-secondary]="!c.active">{{ c.active ? ('Active' | translate) : ('Inactive' | translate) }}</span></td>
            <td><button class="btn btn-sm btn-outline-primary" type="button" (click)="edit(c)">{{ 'Edit' | translate }}</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="categories.length === 0">{{ 'No categories yet — for example Computers (36 months), Vehicles (60 months), Furniture (84 months).' | translate }}</p>
    </app-shell>
  `,
})
export class AssetCategoryListComponent implements OnInit {
  categories: AssetCategory[] = [];
  assetAccounts: Account[] = [];
  expenseAccounts: Account[] = [];
  editing: AssetCategory | null = null;
  error: string | null = null;
  editingId: number | null = null;
  form = AssetCategoryListComponent.blank();

  constructor(private api: AssetApiService, private accountApi: AccountApiService) {}

  get locked(): boolean {
    return !!this.editing && this.editing.assetCount > 0;
  }

  ngOnInit(): void {
    this.accountApi.listActiveByType('ASSET').subscribe({ next: (a) => (this.assetAccounts = a), error: () => {} });
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.expenseAccounts = a), error: () => {} });
    this.load();
  }

  private static blank() {
    return { name: '', method: 'STRAIGHT_LINE' as DepreciationMethod, usefulLifeMonths: 60, salvagePercent: 0, prorateFirstMonth: false, active: true,
      assetAccountId: null as number | null, accumulatedAccountId: null as number | null, expenseAccountId: null as number | null };
  }

  load(): void {
    this.api.categories().subscribe({
      next: (c) => (this.categories = c),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load categories'),
    });
  }

  edit(c: AssetCategory): void {
    this.editingId = c.id;
    this.editing = c;
    this.form = {
      name: c.name, method: c.method, usefulLifeMonths: c.usefulLifeMonths, salvagePercent: c.salvagePercent, prorateFirstMonth: c.prorateFirstMonth, active: c.active,
      assetAccountId: c.assetAccountId, accumulatedAccountId: c.accumulatedAccountId, expenseAccountId: c.expenseAccountId,
    };
  }

  reset(): void {
    this.editingId = null;
    this.editing = null;
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
