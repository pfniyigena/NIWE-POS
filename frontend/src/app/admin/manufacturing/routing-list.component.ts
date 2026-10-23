import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { Routing } from '../../core/models';

/** Manufacturing > Settings > Routings: search (name, description, step, workstation), active/inactive, pages and exports. */
@Component({
  selector: 'app-routing-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Production routings</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/routings/new">+ New routing</a>
      </div>
      <app-list-filter exportPath="/manufacturing/routings/export" [dates]="false" [statuses]="statuses" statusAll="Active and inactive" placeholder="Search name, step, workstation" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
            <th class="text-end">Operations</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of list.rows" class="rg-row">
            <td>{{ r.name }}</td>
            <td>{{ r.description || '—' }}</td>
            <td class="text-end">{{ r.operations.length }}</td>
            <td>
              <span class="badge badge-success" *ngIf="r.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!r.active">Inactive</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/routings', r.id, 'edit']">Edit</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? 'No routings match these filters.' : 'No routings yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
      <p class="text-muted" style="margin-top:1rem;">
        A routing is optional — a Bill of Materials without one still works end to end; a routing just adds an operation-by-operation time/labor estimate on top.
      </p>
    </app-shell>
  `,
})
export class RoutingListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];
  list = new ListState<Routing>((f, p, sz) => this.routingApi.page(f, p, sz), 'Failed to load routings');

  constructor(private routingApi: RoutingApiService) {}

  ngOnInit(): void {
    this.list.load();
  }
}
