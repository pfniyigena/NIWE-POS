import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { Routing } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Manufacturing > Settings > Routings: search (name, description, step, workstation), active/inactive, pages and exports. */
@Component({
  selector: 'app-routing-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Production routings' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/routings/new">{{ '+ New routing' | translate }}</a>
      </div>
      <app-list-filter exportPath="/manufacturing/routings/export" [dates]="false" [statuses]="statuses" statusAll="Active and inactive" [placeholder]="'Search name, step, workstation' | translate" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Description' | translate }}</th>
            <th class="text-end">{{ 'Operations' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of list.rows" class="rg-row">
            <td>{{ r.name }}</td>
            <td>{{ r.description || '—' }}</td>
            <td class="text-end">{{ r.operations.length }}</td>
            <td>
              <span class="badge badge-success" *ngIf="r.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!r.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/routings', r.id, 'edit']">{{ 'Edit' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No routings match these filters.' | translate) : ('No routings yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
      <p class="text-muted" style="margin-top:1rem;">
        {{ 'A routing is optional — a Bill of Materials without one still works end to end; a routing just adds an operation-by-operation time/labor estimate on top.' | translate }}
      </p>
    </app-shell>
  `,
})
export class RoutingListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: T('Active') }, { value: 'INACTIVE', label: T('Inactive') }];
  list = new ListState<Routing>((f, p, sz) => this.routingApi.page(f, p, sz), 'Failed to load routings');

  constructor(private routingApi: RoutingApiService) {}

  ngOnInit(): void {
    this.list.load();
  }
}
