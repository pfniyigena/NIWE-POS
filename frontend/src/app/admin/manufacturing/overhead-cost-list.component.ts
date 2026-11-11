import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { OverheadCostApiService } from '../../core/api/overhead-cost-api.service';
import { OverheadCost } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Manufacturing > Indirect costs: search (code, name), active/inactive, allocation method, pages and exports. */
@Component({
  selector: 'app-overhead-cost-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Indirect costs' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/overhead-costs/new">{{ '+ New indirect cost' | translate }}</a>
      </div>
      <app-list-filter exportPath="/manufacturing/overhead-costs/export" [exportExtra]="extra" [extraActive]="method !== ''" [dates]="false" [statuses]="statuses" statusAll="Active and inactive" [placeholder]="'Search code, name' | translate" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select oc-method" style="width:auto;" [(ngModel)]="method" (ngModelChange)="extraChanged()" name="method" [title]="'Allocation method' | translate">
          <option value="">{{ 'Any method' | translate }}</option>
          <option value="FLAT">{{ 'Flat (per order)' | translate }}</option>
          <option value="PER_UNIT">{{ 'Per unit produced' | translate }}</option>
          <option value="PERCENT_OF_MATERIAL_COST">{{ '% of material cost' | translate }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Allocation method' | translate }}</th>
            <th class="text-end">{{ 'Rate' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of list.rows" class="oc-row">
            <td>{{ o.code }}</td>
            <td>{{ o.name }}</td>
            <td>{{ methodLabel(o.allocationMethod) }}</td>
            <td class="text-end">{{ o.rate | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="o.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!o.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/overhead-costs', o.id, 'edit']">{{ 'Edit' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || method ? ('No indirect costs match these filters.' | translate) : ('No indirect costs yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
      <p class="text-muted" style="margin-top:1rem;">
        {{ '"Flat" spreads a fixed total across a Manufacturing Order\\'s runs proportional to quantity produced. "Per unit" charges the rate for every unit actually produced. "Percent of material cost" charges a percentage of each run\\'s own material cost.' | translate }}
      </p>
    </app-shell>
  `,
})
export class OverheadCostListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: T('Active') }, { value: 'INACTIVE', label: T('Inactive') }];
  method = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<OverheadCost>((f, p, sz) => this.overheadCostApi.page(f, p, sz, { method: this.method }), 'Failed to load indirect costs');

  constructor(private overheadCostApi: OverheadCostApiService) {}

  ngOnInit(): void {
    this.list.load();
  }

  extraChanged(): void {
    this.extra = { method: this.method };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.method = '';
    this.extra = {};
  }

  methodLabel(method: string): string {
    switch (method) {
      case 'FLAT':
        return 'Flat (per order)';
      case 'PER_UNIT':
        return 'Per unit produced';
      case 'PERCENT_OF_MATERIAL_COST':
        return '% of material cost';
      default:
        return method;
    }
  }
}
