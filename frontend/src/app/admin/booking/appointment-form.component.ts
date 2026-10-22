import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BookableService, BookingApiService, Option, Slot } from '../../core/api/booking-api.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer } from '../../core/models';
import { today } from '../../core/time-util';

/** Booking > New appointment (staff): pick a service and a day, see only the times that really are free, then who it is for. */
@Component({
  selector: 'app-appointment-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>New appointment</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/booking">Back to calendar</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card" style="padding:1rem; max-width:44rem;" (ngSubmit)="save()">
        <div class="form-group mb-2">
          <label class="form-label">Service</label>
          <select class="form-select" name="svc" [(ngModel)]="serviceId" (ngModelChange)="onServiceChange()" required>
            <option [ngValue]="null">Choose a service…</option>
            <option *ngFor="let s of services" [ngValue]="s.id">{{ s.name }} · {{ s.durationMinutes }} min<span *ngIf="s.price"> · {{ s.price | number: '1.0-2' }}</span></option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="service?.description">{{ service?.description }}</div>
        </div>

        <div class="form-group mb-2" *ngIf="service">
          <label class="form-label">Date</label>
          <app-date-picker name="date" [(ngModel)]="date" (ngModelChange)="loadSlots()" required></app-date-picker>
        </div>

        <div class="form-group mb-2" *ngIf="service && service.staff.length">
          <label class="form-label">Prefer a person <span class="text-muted">(optional)</span></label>
          <select class="form-select" name="stf" [(ngModel)]="staffId" (ngModelChange)="loadSlots()">
            <option [ngValue]="null">Anyone free</option>
            <option *ngFor="let p of service.staff" [ngValue]="p.id">{{ p.name }}</option>
          </select>
        </div>

        <div class="form-group mb-2" *ngIf="service">
          <label class="form-label">Time</label>
          <div class="text-muted" *ngIf="loadingSlots">Checking availability…</div>
          <div class="text-muted" *ngIf="!loadingSlots && slotsLoaded && !slots.length">Nothing free on this day for this service. Try another day or person.</div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="slots.length">
            <button type="button" class="btn" *ngFor="let sl of slots" [class.btn-primary]="time === sl.time" [class.btn-outline-secondary]="time !== sl.time"
                    (click)="pick(sl)">{{ sl.time }}</button>
          </div>
        </div>

        <ng-container *ngIf="chosen">
          <div class="form-group mb-2" *ngIf="chosen.staff.length">
            <label class="form-label">Person</label>
            <select class="form-select" name="who" [(ngModel)]="staffUserId">
              <option [ngValue]="null">Assign automatically</option>
              <option *ngFor="let p of chosen.staff" [ngValue]="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div class="form-group mb-2" *ngIf="chosen.resources.length">
            <label class="form-label">Room / equipment</label>
            <select class="form-select" name="room" [(ngModel)]="resourceId">
              <option [ngValue]="null">Assign automatically</option>
              <option *ngFor="let r of chosen.resources" [ngValue]="r.id">{{ r.name }}</option>
            </select>
          </div>

          <hr />
          <div class="form-group mb-2">
            <label class="form-label">Who is it for?</label>
            <select class="form-select" name="cust" [(ngModel)]="customerId" (ngModelChange)="onCustomer()">
              <option [ngValue]="null">A walk-in / someone new (type the name below)</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}<span *ngIf="c.phone"> · {{ c.phone }}</span></option>
            </select>
          </div>
          <div class="form-group mb-2">
            <label class="form-label">Name</label>
            <input class="form-control" name="gn" [(ngModel)]="guestName" [readonly]="customerId !== null" required />
          </div>
          <div class="flex gap-2">
            <div class="form-group mb-2" style="flex:1;">
              <label class="form-label">Phone</label>
              <input class="form-control" name="gp" [(ngModel)]="guestPhone" />
            </div>
            <div class="form-group mb-2" style="flex:1;">
              <label class="form-label">Email <span class="text-muted">(for the confirmation)</span></label>
              <input class="form-control" type="email" name="ge" [(ngModel)]="guestEmail" />
            </div>
          </div>
          <div class="form-group mb-2">
            <label class="form-label">Notes</label>
            <textarea class="form-control" rows="2" name="nt" [(ngModel)]="notes"></textarea>
          </div>
          <label class="mb-3" style="display:flex; gap:0.5rem; align-items:center;" *ngIf="service && service.price > 0 && service.paymentMode !== 'NONE'">
            <input type="checkbox" name="skip" [(ngModel)]="skipPayment" />
            Confirm now without waiting for payment (no invoice is held back)
          </label>
          <p class="text-muted" *ngIf="service && service.price > 0 && service.paymentMode !== 'NONE' && !skipPayment" style="font-size:0.85rem;">
            An invoice for {{ service.price | number: '1.0-2' }} will be raised and the time is held for the guest to pay.
          </p>
          <button class="btn btn-primary" type="submit" [disabled]="saving || !time">{{ saving ? 'Booking…' : 'Book it' }}</button>
        </ng-container>
      </form>
    </app-shell>
  `,
})
export class AppointmentFormComponent implements OnInit {
  services: BookableService[] = [];
  customers: Customer[] = [];
  serviceId: number | null = null;
  date = today();
  staffId: number | null = null;
  slots: Slot[] = [];
  slotsLoaded = false;
  loadingSlots = false;
  time: string | null = null;
  chosen: Slot | null = null;
  staffUserId: number | null = null;
  resourceId: number | null = null;
  customerId: number | null = null;
  guestName = '';
  guestPhone = '';
  guestEmail = '';
  notes = '';
  skipPayment = false;
  saving = false;
  error: string | null = null;

  constructor(private api: BookingApiService, private customersApi: CustomerApiService, private route: ActivatedRoute, private router: Router) {}

  get service(): BookableService | null {
    return this.services.find((s) => s.id === this.serviceId) ?? null;
  }

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    if (q.get('date')) this.date = q.get('date')!;
    this.api.services(true).subscribe({
      next: (l) => {
        this.services = l;
        if (q.get('serviceId')) {
          this.serviceId = Number(q.get('serviceId'));
          this.onServiceChange();
        }
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the services'),
    });
    this.customersApi.list().subscribe({ next: (l) => (this.customers = l), error: () => {} });
  }

  onServiceChange(): void {
    this.staffId = null;
    this.loadSlots();
  }

  loadSlots(): void {
    this.slots = [];
    this.time = null;
    this.chosen = null;
    this.slotsLoaded = false;
    if (!this.serviceId || !this.date) return;
    this.loadingSlots = true;
    this.api.slots(this.serviceId, this.date, this.staffId).subscribe({
      next: (l) => { this.slots = l; this.loadingSlots = false; this.slotsLoaded = true; },
      error: (e) => { this.loadingSlots = false; this.error = e?.error?.message ?? 'Could not check availability'; },
    });
  }

  pick(s: Slot): void {
    this.time = s.time;
    this.chosen = s;
    this.staffUserId = this.staffId && s.staff.some((p) => p.id === this.staffId) ? this.staffId : null;
    this.resourceId = null;
  }

  onCustomer(): void {
    const c = this.customers.find((x) => x.id === this.customerId);
    if (c) {
      this.guestName = c.name;
      this.guestPhone = c.phone ?? '';
      this.guestEmail = c.email ?? '';
    }
  }

  save(): void {
    if (!this.serviceId || !this.time) return;
    this.saving = true;
    this.error = null;
    this.api.book({
      serviceId: this.serviceId, start: `${this.date}T${this.time}`, staffUserId: this.staffUserId, resourceId: this.resourceId,
      customerId: this.customerId, guestName: this.guestName.trim(), guestPhone: this.guestPhone.trim() || null,
      guestEmail: this.guestEmail.trim() || null, notes: this.notes.trim() || null, skipPayment: this.skipPayment,
    }).subscribe({
      next: (a) => this.router.navigate(['/admin/booking/appointments', a.id]),
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not book this appointment'; },
    });
  }
}
