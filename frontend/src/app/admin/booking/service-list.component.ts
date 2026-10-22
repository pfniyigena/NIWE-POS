import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BookableService, BookingApiService } from '../../core/api/booking-api.service';

/** Booking > Services: what can be booked, how long it takes, what it costs and who can do it. */
@Component({
  selector: 'app-booking-service-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Bookable services</h3>
        <a class="btn btn-primary" routerLink="/admin/booking/services/new">+ New service</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="table-sm">
        <thead><tr><th>Service</th><th>Length</th><th class="text-end">Price</th><th>Payment to confirm</th><th>Who</th><th>Online</th><th>Bookings</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let s of list" [style.opacity]="s.active ? 1 : 0.55">
            <td><span style="display:inline-block; width:0.7rem; height:0.7rem; border-radius:50%; margin-right:0.4rem;" [style.background]="s.color || '#2f6fb3'"></span>
              <a [routerLink]="['/admin/booking/services', s.id]">{{ s.name }}</a><span class="badge badge-secondary" *ngIf="!s.active" style="margin-left:0.4rem;">Off</span></td>
            <td>{{ s.durationMinutes }} min<span class="text-muted" *ngIf="s.bufferMinutes"> + {{ s.bufferMinutes }} buffer</span></td>
            <td class="text-end">{{ s.price | number: '1.0-2' }}</td>
            <td>{{ s.paymentMode === 'NONE' || !s.price ? 'None' : s.paymentMode === 'FULL' ? 'Full price' : 'Deposit ' + (s.depositPercent | number: '1.0-2') + '%' }}</td>
            <td>{{ names(s) }}<span class="text-muted" *ngIf="s.resourceKind"> · needs a {{ s.resourceKind }}</span></td>
            <td>{{ s.publicVisible ? 'Yes' : 'No' }}</td>
            <td>{{ s.appointments }}</td>
            <td><button class="btn btn-sm btn-outline-danger" *ngIf="!s.appointments" (click)="remove(s)">Delete</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">No services yet. Add what you sell by appointment, for example a consultation, a haircut or a treatment.</p>
    </app-shell>
  `,
})
export class BookingServiceListComponent implements OnInit {
  list: BookableService[] = [];
  error: string | null = null;
  constructor(private api: BookingApiService) {}
  ngOnInit(): void { this.load(); }
  load(): void {
    this.api.services().subscribe({ next: (l) => (this.list = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load the services') });
  }
  names(s: BookableService): string { return s.staff.length ? s.staff.map((p) => p.name).join(', ') : 'Anyone'; }
  remove(s: BookableService): void {
    if (!confirm(`Delete "${s.name}"?`)) return;
    this.api.deleteService(s.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete this service') });
  }
}
