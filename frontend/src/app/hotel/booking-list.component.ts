import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Booking, HotelApiService } from '../core/api/hotel-api.service';
import { errMsg, statusBadge, statusLabel } from './hotel-util';

/** Hotel > Bookings: every reservation and stay, newest arrival first; search by booking number, guest, phone or room. */
@Component({
  selector: 'app-booking-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Bookings</h3>
        <div class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
          <a class="btn btn-outline-primary" routerLink="/admin/hotel/walk-in">Walk-in</a>
          <a class="btn btn-primary" routerLink="/admin/hotel/bookings/new">+ New booking</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <input class="form-control" style="max-width:300px;" placeholder="Search booking, guest, phone, room" name="q" [(ngModel)]="q" (ngModelChange)="search()" />
        <select class="form-control" style="max-width:180px;" name="status" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All statuses</option>
          <option value="BOOKED">Booked</option>
          <option value="CHECKED_IN">Checked in</option>
          <option value="CHECKED_OUT">Checked out</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="NO_SHOW">No-show</option>
        </select>
      </div>
      <table>
        <thead><tr><th>Booking</th><th>Guest</th><th>Room</th><th>Stay</th><th>Status</th><th class="text-end">Total</th><th class="text-end">Balance</th></tr></thead>
        <tbody>
          <tr *ngFor="let b of bookings">
            <td><a [routerLink]="['/admin/hotel/bookings', b.id]">{{ b.bookingNumber }}</a></td>
            <td>{{ b.guestName }}<div class="text-muted" style="font-size:0.8rem;">{{ b.guestPhone }}</div></td>
            <td>{{ b.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ b.roomTypeName }}</div></td>
            <td>{{ b.checkInDate }} → {{ b.checkOutDate }}<div class="text-muted" style="font-size:0.8rem;">{{ b.nights }} night{{ b.nights === 1 ? '' : 's' }}</div></td>
            <td><span class="badge" [ngClass]="badge(b.status)">{{ label(b.status) }}</span></td>
            <td class="text-end">{{ b.chargesTotal | number: '1.2-2' }}</td>
            <td class="text-end">{{ b.balance | number: '1.2-2' }}</td>
          </tr>
          <tr *ngIf="bookings.length === 0"><td colspan="7" class="text-muted">No bookings found.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BookingListComponent implements OnInit {
  bookings: Booking[] = [];
  q = '';
  status = '';
  error: string | null = null;
  badge = statusBadge;
  label = statusLabel;
  private timer: any;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.bookings(this.status || undefined, this.q.trim() || undefined).subscribe({
      next: (b) => (this.bookings = b),
      error: (e) => (this.error = errMsg(e, 'Failed to load bookings')),
    });
  }

  search(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.load(), 250);
  }
}
