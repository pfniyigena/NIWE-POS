import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Booking, HotelApiService, RoomType } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { statusBadge, statusLabel } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Bookings: every reservation and stay, newest arrival first; search, filter by status, arrival dates and room type; paged; exports. */
@Component({
  selector: 'app-booking-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Bookings' | translate }}</h3>
        <div class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
          <a class="btn btn-outline-primary" routerLink="/admin/hotel/walk-in">{{ 'Walk-in' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/hotel/bookings/new">{{ '+ New booking' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <app-list-filter exportPath="/hotel/bookings/export" [exportExtra]="extra" [extraActive]="roomTypeId !== null" [placeholder]="'Search booking, guest, phone, room' | translate" [statuses]="statuses" statusAll="All statuses" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" *ngIf="types.length > 1">
        <select class="form-select type-filter" style="width:auto;" [(ngModel)]="roomTypeId" (ngModelChange)="typeChanged()" name="rt" [title]="'Room type' | translate">
          <option [ngValue]="null">{{ 'All room types' | translate }}</option>
          <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;">{{ 'Dates filter by arrival day.' | translate }}</span>
      </div>
      <table class="table-sm">
        <thead><tr><th>{{ 'Booking' | translate }}</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Room' | translate }}</th><th>{{ 'Stay' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let b of list.rows">
            <td><a [routerLink]="['/admin/hotel/bookings', b.id]">{{ b.bookingNumber }}</a></td>
            <td>{{ b.guestName }}<span class="badge badge-warning vip-badge" style="margin-left:0.3rem;" *ngIf="b.guestVip">{{ 'VIP' | translate }}</span><span class="badge badge-danger black-badge" style="margin-left:0.3rem;" *ngIf="b.guestBlacklisted">{{ 'Blacklisted' | translate }}</span><span class="badge badge-info online-badge" style="margin-left:0.3rem;" *ngIf="b.online">{{ 'Website' | translate }}</span><span class="badge badge-warning hold-badge" style="margin-left:0.3rem;" *ngIf="b.status === 'BOOKED' && b.holdExpiresAt">{{ 'Awaiting deposit' | translate }}</span><div class="text-muted" style="font-size:0.8rem;">{{ b.guestPhone }}</div><div class="text-muted guest-prefs" style="font-size:0.8rem;" *ngIf="b.guestPreferences">★ {{ b.guestPreferences }}</div></td>
            <td>{{ b.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ b.roomTypeName }}</div></td>
            <td>{{ b.checkInDate }} → {{ b.checkOutDate }}<div class="text-muted" style="font-size:0.8rem;">{{ '{p0} night{p1}' | translate: { p0: (b.nights), p1: (b.nights === 1 ? '' : 's') } }}</div></td>
            <td><span class="badge" [ngClass]="badge(b.status)">{{ label(b.status) }}</span></td>
            <td class="text-end">{{ b.chargesTotal | number: '1.2-2' }}</td>
            <td class="text-end">{{ b.balance | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || roomTypeId ? ('No bookings match these filters.' | translate) : ('No bookings yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class BookingListComponent implements OnInit {
  types: RoomType[] = [];
  roomTypeId: number | null = null;
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [
    { value: 'BOOKED', label: T('Booked') },
    { value: 'CHECKED_IN', label: T('Checked in') },
    { value: 'CHECKED_OUT', label: T('Checked out') },
    { value: 'CANCELLED', label: T('Cancelled') },
    { value: 'NO_SHOW', label: T('No-show') },
  ];
  list = new ListState<Booking>((f, p, sz) => this.api.page<Booking>('bookings', f, p, sz, { roomTypeId: this.roomTypeId }), 'Failed to load bookings');
  badge = statusBadge;
  label = statusLabel;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.roomTypes().subscribe({ next: (t) => (this.types = t), error: () => {} });
    this.list.load();
  }

  typeChanged(): void {
    this.extra = { roomTypeId: this.roomTypeId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.roomTypeId = null;
    this.extra = {};
  }
}
