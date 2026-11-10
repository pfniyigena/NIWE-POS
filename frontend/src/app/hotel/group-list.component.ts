import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { GroupSummary, HotelApiService } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

export function groupBadge(status: string): string {
  switch (status) {
    case 'CONFIRMED': return 'badge-success';
    case 'TENTATIVE': return 'badge-warning';
    case 'CANCELLED': return 'badge-danger';
    default: return 'badge-info';
  }
}

export function billingLabel(b: string): string {
  return b === 'MASTER_ALL' ? 'Group pays everything' : b === 'MASTER_ROOMS' ? 'Group pays rooms' : 'Each guest pays';
}

/** Hotel > Groups: weddings, conferences and tours — rooms held, booked and the account, in one list. */
@Component({
  selector: 'app-group-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Groups' | translate }}</h3>
        <a class="btn btn-primary new-group" *ngIf="auth.canManage('HOTEL')" routerLink="/admin/hotel/groups/new">{{ '+ New group' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <app-list-filter exportPath="/hotel/groups/export" [placeholder]="'Search group, contact, e-mail, phone' | translate" [statuses]="statuses" statusAll="All groups" [dates]="false" (filterChange)="list.apply($event)"></app-list-filter>
      <table class="table-sm groups-table">
        <thead><tr><th>{{ 'Group' | translate }}</th><th>{{ 'Contact' | translate }}</th><th>{{ 'Stay' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Rooms held' | translate }}</th><th class="text-end">{{ 'Booked' | translate }}</th><th class="text-end">{{ 'In house' | translate }}</th><th>{{ 'Pays' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th class="text-end">{{ 'Deposit held' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let g of list.rows" class="group-row">
            <td><a [routerLink]="['/admin/hotel/groups', g.id]"><strong>{{ g.groupCode }}</strong></a><div>{{ g.name }}</div></td>
            <td>{{ g.contactName || '—' }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="g.companyName">{{ g.companyName }}</div></td>
            <td>{{ g.arrivalDate }} → {{ g.departureDate }}</td>
            <td><span class="badge" [ngClass]="badge(g.status)">{{ g.status | titlecase }}</span></td>
            <td class="text-end">{{ g.roomsBlocked }}</td>
            <td class="text-end">{{ g.roomsBooked }}</td>
            <td class="text-end">{{ g.checkedIn }}</td>
            <td>{{ label(g.billing) }}</td>
            <td class="text-end"><strong *ngIf="g.owed > 0">{{ g.owed | number: '1.2-2' }}</strong><span *ngIf="g.owed <= 0">—</span></td>
            <td class="text-end"><span *ngIf="g.held > 0">{{ g.held | number: '1.2-2' }}</span><span *ngIf="g.held <= 0">—</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No group matches these filters.' | translate) : ('No groups yet. Use "New group" to hold rooms for a wedding, a conference or a tour.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class GroupListComponent implements OnInit {
  statuses = [{ value: 'ACTIVE', label: 'Open (tentative + confirmed)' }, { value: 'TENTATIVE', label: 'Tentative' }, { value: 'CONFIRMED', label: 'Confirmed' }, { value: 'COMPLETED', label: 'Completed' }, { value: 'CANCELLED', label: 'Cancelled' }];
  list = new ListState<GroupSummary>((f, p, sz) => this.api.page<GroupSummary>('groups', f, p, sz), 'Failed to load groups');
  badge = groupBadge;
  label = billingLabel;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.list.load();
  }
}
