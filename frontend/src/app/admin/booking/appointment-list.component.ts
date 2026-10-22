import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Appointment, BookingApiService, BookingSummary, Option } from '../../core/api/booking-api.service';
import { addDays, today } from '../../core/time-util';

/** Booking > Appointments: a filterable list across days (the calendar is the one-day view). */
@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Appointments</h3>
        <a class="btn btn-primary" routerLink="/admin/booking/appointments/new">+ New appointment</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile"><div class="stat-label">Today</div><div class="stat-value">{{ s.today }}</div></div>
        <div class="stat-tile"><div class="stat-label">Next 7 days</div><div class="stat-value">{{ s.upcomingWeek }}</div></div>
        <div class="stat-tile" [class.warn]="s.awaitingPayment > 0"><div class="stat-label">Waiting for payment</div><div class="stat-value">{{ s.awaitingPayment }}</div></div>
        <div class="stat-tile" [class.warn]="s.noShowsThisMonth > 0"><div class="stat-label">No-shows this month</div><div class="stat-value">{{ s.noShowsThisMonth }}</div></div>
      </div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <app-date-picker name="f" [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
        <span>to</span>
        <app-date-picker name="t" [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All statuses</option>
          <option value="PENDING_PAYMENT">Waiting for payment</option><option value="CONFIRMED">Confirmed</option><option value="COMPLETED">Completed</option>
          <option value="NO_SHOW">No-show</option><option value="CANCELLED">Cancelled</option>
        </select>
        <select class="form-select" style="width:auto;" name="sf" [(ngModel)]="staffId" (ngModelChange)="load()">
          <option [ngValue]="null">Everyone</option>
          <option *ngFor="let p of staff" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <input class="form-control" style="width:14rem;" placeholder="Search name, phone, number" name="q" [(ngModel)]="q" />
      </div>
      <table class="table-sm">
        <thead><tr><th>When</th><th>Number</th><th>Guest</th><th>Service</th><th>With</th><th>Status</th><th class="text-end">Price</th><th>Invoice</th></tr></thead>
        <tbody>
          <tr *ngFor="let a of filtered()">
            <td>{{ a.startAt | date: 'EEE d MMM, HH:mm' }}</td>
            <td><a [routerLink]="['/admin/booking/appointments', a.id]">{{ a.number }}</a></td>
            <td>{{ a.guestName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="a.guestPhone">{{ a.guestPhone }}</div></td>
            <td>{{ a.serviceName }}</td>
            <td>{{ a.staffName || '—' }}<span *ngIf="a.resourceName" class="text-muted"> · {{ a.resourceName }}</span></td>
            <td><span class="badge" [class.badge-success]="a.status === 'CONFIRMED' || a.status === 'COMPLETED'" [class.badge-warning]="a.status === 'PENDING_PAYMENT'"
                      [class.badge-danger]="a.status === 'NO_SHOW'" [class.badge-secondary]="a.status === 'CANCELLED'">{{ label(a.status) }}</span></td>
            <td class="text-end">{{ a.price | number: '1.0-2' }}</td>
            <td>{{ a.invoiceNumber || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!filtered().length">No appointments in this range.</p>
    </app-shell>
  `,
})
export class AppointmentListComponent implements OnInit {
  list: Appointment[] = [];
  s: BookingSummary | null = null;
  staff: Option[] = [];
  from = today();
  to = addDays(today(), 30);
  status = '';
  staffId: number | null = null;
  q = '';
  error: string | null = null;

  constructor(private api: BookingApiService) {}

  ngOnInit(): void {
    this.load();
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => {} });
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
  }

  load(): void {
    this.error = null;
    this.api.list({ from: this.from, to: this.to, staffId: this.staffId, status: this.status }).subscribe({
      next: (l) => (this.list = l),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the appointments'),
    });
  }

  filtered(): Appointment[] {
    const q = this.q.trim().toLowerCase();
    if (!q) return this.list;
    return this.list.filter((a) => (a.guestName + ' ' + (a.guestPhone ?? '') + ' ' + a.number + ' ' + a.confirmationCode).toLowerCase().includes(q));
  }

  label(s: string): string {
    return s === 'PENDING_PAYMENT' ? 'Waiting for payment' : s === 'NO_SHOW' ? 'No-show' : s.charAt(0) + s.slice(1).toLowerCase();
  }
}
