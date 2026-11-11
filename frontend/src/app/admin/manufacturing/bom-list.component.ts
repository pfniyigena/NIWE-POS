import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { BomApiService } from '../../core/api/bom-api.service';
import { Bom } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Manufacturing > Bill of Materials: search (code, name, product, component), active/inactive, pages and Excel / PDF / CSV. */
@Component({
  selector: 'app-bom-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Bills of materials' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/boms/new">{{ '+ New bill of materials' | translate }}</a>
      </div>
      <app-list-filter exportPath="/manufacturing/boms/export" [dates]="false" [statuses]="statuses" statusAll="Active and inactive" [placeholder]="'Search code, name, product, component' | translate" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Product' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th class="text-end">{{ 'Version' | translate }}</th>
            <th>{{ 'Routing' | translate }}</th>
            <th class="text-end">{{ 'Components' | translate }}</th>
            <th class="text-end">{{ 'Est. unit material cost' | translate }}</th>
            <th class="text-end">{{ 'Est. unit cost' | translate }}</th>
            <th class="text-end">{{ 'Margin' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of list.rows" class="bl-row">
            <td>{{ b.code }}</td>
            <td>{{ b.productName }}</td>
            <td>{{ b.name || '—' }}</td>
            <td class="text-end">{{ 'v{p0}' | translate: { p0: (b.version) } }}</td>
            <td>{{ b.routingName || '—' }}</td>
            <td class="text-end">{{ b.lines.length }}</td>
            <td class="text-end">{{ b.estimatedUnitMaterialCost | number: '1.2-2' }}</td>
            <td class="text-end bl-cost">{{ b.estimatedUnitCost | number: '1.2-2' }}</td>
            <td class="text-end bl-margin">{{ b.estimatedMarginPercent != null ? (b.estimatedMarginPercent | number: '1.0-1') + '%' : '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="b.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!b.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/boms', b.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary bl-copy" (click)="copy(b)">{{ 'Copy' | translate }}</button>
              <button class="btn btn-sm btn-outline-success bl-activate" *ngIf="!b.active" (click)="activate(b)">{{ 'Make active' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No bills of materials match these filters.' | translate) : ('No bills of materials yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class BomListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: T('Active') }, { value: 'INACTIVE', label: T('Inactive') }];
  list = new ListState<Bom>((f, p, sz) => this.bomApi.page(f, p, sz), 'Failed to load bills of materials');

  constructor(private bomApi: BomApiService, private router: Router) {}

  copy(b: Bom): void {
    this.bomApi.copy(b.id).subscribe({
      next: (n) => this.router.navigate(['/admin/manufacturing/boms', n.id, 'edit']),
      error: (err) => (this.list.error = err?.error?.message ?? T('Failed to copy the bill of materials')),
    });
  }

  activate(b: Bom): void {
    if (!confirm(T('Make version {p0} of {p1} the active recipe? The product\'s other versions will be switched off. Orders already made keep the recipe they were made with.', { p0: b.version, p1: b.productName }))) return;
    this.bomApi.activate(b.id).subscribe({
      next: () => this.list.load(),
      error: (err) => (this.list.error = err?.error?.message ?? T('Failed to activate the bill of materials')),
    });
  }

  ngOnInit(): void {
    this.list.load();
  }
}
