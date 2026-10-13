import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Booking, HotelApiService, HotelDashboard } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Front desk: today's numbers, who is arriving, who is in-house, who is due to leave. */
@Component({
  selector: 'app-front-desk',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Front desk</h3>
        <div class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
          <a class="btn btn-outline-primary" routerLink="/admin/hotel/walk-in">Walk-in</a>
          <a class="btn btn-primary" routerLink="/admin/hotel/bookings/new">+ New booking</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid" *ngIf="d">
        <div class="stat-tile"><div class="stat-label">Occupied</div><div class="stat-value">{{ d.occupied }} / {{ d.totalRooms - d.outOfOrder }}</div></div>
        <div class="stat-tile"><div class="stat-label">Occupancy</div><div class="stat-value">{{ d.occupancyPercent }}%</div></div>
        <div class="stat-tile"><div class="stat-label">Free tonight</div><div class="stat-value">{{ d.availableToday }}</div></div>
        <div class="stat-tile"><div class="stat-label">Arrivals due</div><div class="stat-value">{{ d.arrivalsToday }}</div></div>
        <div class="stat-tile"><div class="stat-label">Departures due</div><div class="stat-value">{{ d.departuresToday }}</div></div>
        <div class="stat-tile" [class.warn]="d.dirtyRooms > 0"><div class="stat-label">To clean</div><div class="stat-value">{{ d.dirtyRooms }}</div></div>
        <div class="stat-tile" [class.warn]="d.outOfOrder > 0"><div class="stat-label">Out of order</div><div class="stat-value">{{ d.outOfOrder }}</div></div>
      </div>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <button class="btn" [ngClass]="tab === 'arrivals' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'arrivals'">Arrivals ({{ arrivals.length }})</button>
        <button class="btn" [ngClass]="tab === 'inhouse' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'inhouse'">In-house ({{ inHouse.length }})</button>
        <button class="btn" [ngClass]="tab === 'departures' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'departures'">Departures ({{ departures.length }})</button>
      </div>

      <table>
        <thead><tr><th>Booking</th><th>Guest</th><th>Room</th><th>Stay</th><th class="text-end">Balance</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let b of rows()">
            <td><a [routerLink]="['/admin/hotel/bookings', b.id]">{{ b.bookingNumber }}</a></td>
            <td>{{ b.guestName }}<div class="text-muted" style="font-size:0.8rem;">{{ b.guestPhone }}</div></td>
            <td>{{ b.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ b.roomTypeName }}</div></td>
            <td>{{ b.checkInDate }} → {{ b.checkOutDate }}</td>
            <td class="text-end">{{ b.balance | number: '1.2-2' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hotel/bookings', b.id]">{{ tab === 'arrivals' ? 'Check in' : tab === 'departures' ? 'Check out' : 'Open' }}</a></td>
          </tr>
          <tr *ngIf="rows().length === 0"><td colspan="6" class="text-muted">Nobody here.</td></tr>
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

  rows(): Booking[] {
    return this.tab === 'arrivals' ? this.arrivals : this.tab === 'inhouse' ? this.inHouse : this.departures;
  }
}
