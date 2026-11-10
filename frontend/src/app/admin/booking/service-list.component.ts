import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BookableService, BookingApiService } from '../../core/api/booking-api.service';

/** Booking > Services: what can be booked, how long it takes, what it costs and who can do it. */
@Component({
  selector: 'app-booking-service-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Bookable services' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/booking/services/new">{{ '+ New service' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="table-sm">
        <thead><tr><th>{{ 'Service' | translate }}</th><th>{{ 'Length' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th>{{ 'Payment to confirm' | translate }}</th><th>{{ 'Who' | translate }}</th><th>{{ 'Online' | translate }}</th><th>{{ 'Bookings' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let s of list" [style.opacity]="s.active ? 1 : 0.55">
            <td><span style="display:inline-block; width:0.7rem; height:0.7rem; border-radius:50%; margin-right:0.4rem;" [style.background]="s.color || '#2f6fb3'"></span>
              <a [routerLink]="['/admin/booking/services', s.id]">{{ s.name }}</a><span class="badge badge-secondary" *ngIf="!s.active" style="margin-left:0.4rem;">{{ 'Off' | translate }}</span></td>
            <td>{{ '{p0} min' | translate: { p0: (s.durationMinutes) } }}<span class="text-muted" *ngIf="s.bufferMinutes"> {{ '+ {p0} buffer' | translate: { p0: (s.bufferMinutes) } }}</span></td>
            <td class="text-end">{{ s.price | number: '1.0-2' }}</td>
            <td>{{ s.paymentMode === 'NONE' || !s.price ? ('None' | translate) : s.paymentMode === 'FULL' ? ('Full price' | translate) : (('Deposit' | translate) + ' ' + (s.depositPercent | number: '1.0-2') + '%') }}</td>
            <td>{{ names(s) }}<span class="text-muted" *ngIf="s.resourceKind"> {{ '· needs a {p0}' | translate: { p0: (s.resourceKind) } }}</span></td>
            <td>{{ s.publicVisible ? ('Yes' | translate) : ('No' | translate) }}</td>
            <td>{{ s.appointments }}</td>
            <td><button class="btn btn-sm btn-outline-danger" *ngIf="!s.appointments" (click)="remove(s)">{{ 'Delete' | translate }}</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">{{ 'No services yet. Add what you sell by appointment, for example a consultation, a haircut or a treatment.' | translate }}</p>
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
