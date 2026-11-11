import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { WorkstationApiService } from '../../core/api/workstation-api.service';
import { Workstation } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Manufacturing > Workstations: search (code, name, description), active/inactive, pages and exports. */
@Component({
  selector: 'app-workstation-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Workstations' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/workstations/new">{{ '+ New workstation' | translate }}</a>
      </div>
      <app-list-filter exportPath="/manufacturing/workstations/export" [dates]="false" [statuses]="statuses" statusAll="Active and inactive" [placeholder]="'Search code, name, description' | translate" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Description' | translate }}</th>
            <th class="text-end">{{ 'Hourly rate' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let w of list.rows" class="ws-row">
            <td>{{ w.code }}</td>
            <td>{{ w.name }}</td>
            <td>{{ w.description || '—' }}</td>
            <td class="text-end">{{ w.hourlyRate | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="w.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!w.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/workstations', w.id, 'edit']">{{ 'Edit' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No workstations match these filters.' | translate) : ('No workstations yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class WorkstationListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: T('Active') }, { value: 'INACTIVE', label: T('Inactive') }];
  list = new ListState<Workstation>((f, p, sz) => this.workstationApi.page(f, p, sz), 'Failed to load workstations');

  constructor(private workstationApi: WorkstationApiService) {}

  ngOnInit(): void {
    this.list.load();
  }
}
