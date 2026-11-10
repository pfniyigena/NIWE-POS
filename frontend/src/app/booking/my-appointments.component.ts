import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { Appointment, BookingApiService } from '../core/api/booking-api.service';

/** Booking > My appointments: what is booked with me (any login that can be given appointments). */
@Component({
  selector: 'app-my-appointments',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'My appointments' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="table-sm">
        <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Service' | translate }}</th><th>{{ 'Where' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let a of list">
            <td><a [routerLink]="['/booking/appointments', a.id]">{{ a.startAt | date: 'EEE d MMM, HH:mm' }}</a></td>
            <td>{{ a.guestName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="a.guestPhone">{{ a.guestPhone }}</div></td>
            <td>{{ a.serviceName }}</td>
            <td>{{ a.resourceName || '—' }}</td>
            <td><span class="badge" [class.badge-success]="a.status === 'CONFIRMED' || a.status === 'COMPLETED'" [class.badge-warning]="a.status === 'PENDING_PAYMENT'"
                      [class.badge-secondary]="a.status === 'CANCELLED'" [class.badge-danger]="a.status === 'NO_SHOW'">{{ (a.status === 'PENDING_PAYMENT' ? 'Waiting for payment' : a.status === 'NO_SHOW' ? 'No-show' : a.status.charAt(0) + a.status.slice(1).toLowerCase()) | translate }}</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">{{ 'Nothing booked with you from today on.' | translate }}</p>
    </app-shell>
  `,
})
export class MyAppointmentsComponent implements OnInit {
  list: Appointment[] = [];
  error: string | null = null;
  constructor(private api: BookingApiService) {}
  ngOnInit(): void {
    this.api.mine().subscribe({ next: (l) => (this.list = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load your appointments') });
  }
}
