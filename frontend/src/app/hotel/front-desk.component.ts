import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Booking, HotelApiService, HotelDashboard } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { errMsg } from './hotel-util';

/** Hotel > Front desk: today's numbers, who is arriving, who is in-house, who is due to leave. */
@Component({
  selector: 'app-front-desk',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, HotelExportComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Front desk' | translate }}</h3>
        <div class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
          <a class="btn btn-outline-primary" routerLink="/admin/hotel/walk-in">{{ 'Walk-in' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/hotel/bookings/new">{{ '+ New booking' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid" *ngIf="d">
        <div class="stat-tile"><div class="stat-label">{{ 'Occupied' | translate }}</div><div class="stat-value">{{ d.occupied }} / {{ d.totalRooms - d.outOfOrder }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Occupancy' | translate }}</div><div class="stat-value">{{ d.occupancyPercent }}%</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Free tonight' | translate }}</div><div class="stat-value">{{ d.availableToday }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Arrivals due' | translate }}</div><div class="stat-value">{{ d.arrivalsToday }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Departures due' | translate }}</div><div class="stat-value">{{ d.departuresToday }}</div></div>
        <div class="stat-tile" [class.warn]="d.dirtyRooms > 0"><div class="stat-label">{{ 'To clean' | translate }}</div><div class="stat-value">{{ d.dirtyRooms }}</div></div>
        <div class="stat-tile" [class.warn]="d.outOfOrder > 0"><div class="stat-label">{{ 'Out of order' | translate }}</div><div class="stat-value">{{ d.outOfOrder }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button class="btn" [ngClass]="tab === 'arrivals' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'arrivals'">{{ 'Arrivals ({p0})' | translate: { p0: (arrivals.length) } }}</button>
        <button class="btn" [ngClass]="tab === 'inhouse' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'inhouse'">{{ 'In-house ({p0})' | translate: { p0: (inHouse.length) } }}</button>
        <button class="btn" [ngClass]="tab === 'departures' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'departures'">{{ 'Departures ({p0})' | translate: { p0: (departures.length) } }}</button>
        <app-hotel-export style="margin-left:auto;" [path]="'/hotel/front-desk/' + listName() + '/export'" [name]="listName()"></app-hotel-export>
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'Booking' | translate }}</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Room' | translate }}</th><th>{{ 'Stay' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let b of rows()">
            <td><a [routerLink]="['/admin/hotel/bookings', b.id]">{{ b.bookingNumber }}</a></td>
            <td>{{ b.guestName }}<span class="badge badge-warning vip-badge" style="margin-left:0.3rem;" *ngIf="b.guestVip">{{ 'VIP' | translate }}</span><span class="badge badge-danger black-badge" style="margin-left:0.3rem;" *ngIf="b.guestBlacklisted">{{ 'Blacklisted' | translate }}</span><div class="text-muted" style="font-size:0.8rem;">{{ b.guestPhone }}</div><div class="text-muted guest-prefs" style="font-size:0.8rem;" *ngIf="b.guestPreferences">★ {{ b.guestPreferences }}</div></td>
            <td>{{ b.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ b.roomTypeName }}</div></td>
            <td>{{ b.checkInDate }} → {{ b.checkOutDate }}</td>
            <td class="text-end">{{ b.balance | number: '1.2-2' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hotel/bookings', b.id]">{{ tab === 'arrivals' ? 'Check in' : tab === 'departures' ? ('Check out' | translate) : ('Open' | translate) }}</a></td>
          </tr>
          <tr *ngIf="rows().length === 0"><td colspan="6" class="text-muted">{{ 'Nobody here.' | translate }}</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class FrontDeskComponent implements OnInit {
  d: HotelDashboard | null = null;
  arrivals: Booking[] = [];
  inHouse: Booking[] = [];
  departures: Booking[] = [];
  tab: 'arrivals' | 'inhouse' | 'departures' = 'arrivals';
  error: string | null = null;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    const fail = (e: any) => (this.error = errMsg(e, 'Failed to load the front desk'));
    this.api.dashboard().subscribe({ next: (d) => (this.d = d), error: fail });
    this.api.arrivals().subscribe({ next: (a) => (this.arrivals = a), error: fail });
    this.api.inHouse().subscribe({ next: (a) => (this.inHouse = a), error: fail });
    this.api.departures().subscribe({ next: (a) => (this.departures = a), error: fail });
  }

  listName(): string {
    return this.tab === 'arrivals' ? 'arrivals' : this.tab === 'inhouse' ? 'in-house' : 'departures';
  }

  rows(): Booking[] {
    return this.tab === 'arrivals' ? this.arrivals : this.tab === 'inhouse' ? this.inHouse : this.departures;
  }
}
