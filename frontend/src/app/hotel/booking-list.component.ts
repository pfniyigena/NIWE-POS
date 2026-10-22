import { Component, OnInit } from '@angular/core';
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

/** Hotel > Bookings: every reservation and stay, newest arrival first; search, filter by status, arrival dates and room type; paged; exports. */
@Component({
  selector: 'app-booking-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Bookings</h3>
        <div class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
          <a class="btn btn-outline-primary" routerLink="/admin/hotel/walk-in">Walk-in</a>
          <a class="btn btn-primary" routerLink="/admin/hotel/bookings/new">+ New booking</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <app-list-filter exportPath="/hotel/bookings/export" [exportExtra]="extra" [extraActive]="roomTypeId !== null" placeholder="Search booking, guest, phone, room" [statuses]="statuses" statusAll="All statuses" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" *ngIf="types.length > 1">
        <select class="form-select type-filter" style="width:auto;" [(ngModel)]="roomTypeId" (ngModelChange)="typeChanged()" name="rt" title="Room type">
          <option [ngValue]="null">All room types</option>
          <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;">Dates filter by arrival day.</span>
      </div>
      <table class="table-sm">
        <thead><tr><th>Booking</th><th>Guest</th><th>Room</th><th>Stay</th><th>Status</th><th class="text-end">Total</th><th class="text-end">Balance</th></tr></thead>
        <tbody>
          <tr *ngFor="let b of list.rows">
            <td><a [routerLink]="['/admin/hotel/bookings', b.id]">{{ b.bookingNumber }}</a></td>
            <td>{{ b.guestName }}<span class="badge badge-warning vip-badge" style="margin-left:0.3rem;" *ngIf="b.guestVip">VIP</span><span class="badge badge-danger black-badge" style="margin-left:0.3rem;" *ngIf="b.guestBlacklisted">Blacklisted</span><span class="badge badge-info online-badge" style="margin-left:0.3rem;" *ngIf="b.online">Website</span><span class="badge badge-warning hold-badge" style="margin-left:0.3rem;" *ngIf="b.status === 'BOOKED' && b.holdExpiresAt">Awaiting deposit</span><div class="text-muted" style="font-size:0.8rem;">{{ b.guestPhone }}</div><div class="text-muted guest-prefs" style="font-size:0.8rem;" *ngIf="b.guestPreferences">★ {{ b.guestPreferences }}</div></td>
            <td>{{ b.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ b.roomTypeName }}</div></td>
            <td>{{ b.checkInDate }} → {{ b.checkOutDate }}<div class="text-muted" style="font-size:0.8rem;">{{ b.nights }} night{{ b.nights === 1 ? '' : 's' }}</div></td>
            <td><span class="badge" [ngClass]="badge(b.status)">{{ label(b.status) }}</span></td>
            <td class="text-end">{{ b.chargesTotal | number: '1.2-2' }}</td>
            <td class="text-end">{{ b.balance | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || roomTypeId ? 'No bookings match these filters.' : 'No bookings yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class BookingListComponent implements OnInit {
  types: RoomType[] = [];
  roomTypeId: number | null = null;
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [
    { value: 'BOOKED', label: 'Booked' },
    { value: 'CHECKED_IN', label: 'Checked in' },
    { value: 'CHECKED_OUT', label: 'Checked out' },
    { value: 'CANCELLED', label: 'Cancelled' },
    { value: 'NO_SHOW', label: 'No-show' },
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
