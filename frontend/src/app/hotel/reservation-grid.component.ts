import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { Availability, GridBooking, GridRoom, HotelApiService, RoomGrid } from '../core/api/hotel-api.service';
import { addDays, errMsg, today } from './hotel-util';

/** Hotel > Reservation chart: rooms down the side, days across; each stay is a coloured bar. Click one to open it. */
@Component({
  selector: 'app-reservation-grid',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Reservation chart</h3>
        <div class="flex gap-2 align-center">
          <button class="btn btn-outline-secondary" (click)="shift(-14)">« 2 weeks</button>
          <input class="form-control" style="width:auto;" type="date" name="from" [(ngModel)]="from" (ngModelChange)="load()" />
          <button class="btn btn-outline-secondary" (click)="shift(14)">2 weeks »</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div style="overflow-x:auto;" *ngIf="grid">
        <table style="min-width:900px; table-layout:fixed;">
          <thead>
            <tr>
              <th style="width:130px;">Room</th>
              <th *ngFor="let d of days" style="text-align:center; font-size:0.75rem; padding:0.3rem 0;" [style.background]="d === todayStr ? 'var(--surface-alt)' : 'transparent'">{{ d.slice(5) }}</th>
            </tr>
            <tr *ngIf="avail">
              <th style="font-weight:400; font-size:0.75rem;" class="text-muted">Free rooms</th>
              <th *ngFor="let d of days; let i = index" style="text-align:center; font-weight:400; font-size:0.75rem;" class="text-muted">{{ freeOn(i) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of grid.rooms">
              <td style="padding:0.25rem 0.5rem;"><strong>{{ r.roomNumber }}</strong><div class="text-muted" style="font-size:0.7rem;">{{ r.roomTypeName }}<span *ngIf="r.outOfOrder"> · out of order</span></div></td>
              <td *ngFor="let d of days" style="padding:0; height:34px; border-left:1px solid var(--border); position:relative; cursor:pointer;"
                  [style.background]="r.outOfOrder ? 'var(--danger-bg)' : 'transparent'" (click)="cellClick(r, d)">
                <div *ngIf="barAt(r, d) as b" (click)="$event.stopPropagation(); open(b)"
                     [title]="b.guestName + ' · ' + b.bookingNumber + ' (' + b.checkInDate + ' → ' + b.checkOutDate + ')'"
                     [style.background]="b.status === 'CHECKED_IN' ? 'var(--success-border)' : 'var(--warning-border)'"
                     style="position:absolute; top:4px; bottom:4px; left:0; right:0; font-size:0.7rem; overflow:hidden; white-space:nowrap; padding:0 4px; color:var(--text);"
                     [style.border-top-left-radius.px]="d === b.checkInDate || d === from ? 6 : 0" [style.border-bottom-left-radius.px]="d === b.checkInDate || d === from ? 6 : 0">
                  <span *ngIf="d === b.checkInDate || d === from">{{ b.guestName }}</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted" style="font-size:0.8rem; margin-top:0.5rem;">Green = in-house, amber = booked. A bar runs from the arrival day up to (not including) the departure day.</p>
    </app-shell>
  `,
})
export class ReservationGridComponent implements OnInit {
  from = today();
  days: string[] = [];
  grid: RoomGrid | null = null;
  avail: Availability | null = null;
  todayStr = today();
  error: string | null = null;
  private readonly span = 21;

  constructor(private api: HotelApiService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  shift(n: number): void {
    this.from = addDays(this.from, n);
    this.load();
  }

  load(): void {
    this.error = null;
    this.days = Array.from({ length: this.span }, (_, i) => addDays(this.from, i));
    const to = addDays(this.from, this.span);
    this.api.grid(this.from, to).subscribe({ next: (g) => (this.grid = g), error: (e) => (this.error = errMsg(e, 'Failed to load the chart')) });
    this.api.availability(this.from, to).subscribe({ next: (a) => (this.avail = a), error: () => (this.avail = null) });
  }

  freeOn(i: number): number {
    return (this.avail?.types ?? []).reduce((sum, t) => sum + (t.days[i]?.available ?? 0), 0);
  }

  barAt(r: GridRoom, day: string): GridBooking | null {
    return r.bookings.find((b) => b.checkInDate <= day && day < b.checkOutDate) ?? null;
  }

  open(b: GridBooking): void {
    this.router.navigate(['/admin/hotel/bookings', b.bookingId]);
  }

  cellClick(r: GridRoom, day: string): void {
    if (this.barAt(r, day) || !r.roomId) return;
    // an empty cell: start a booking (the form picks dates itself; keeps it simple)
    this.router.navigate(['/admin/hotel/bookings/new']);
  }
}
