import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicBookingApiService, PublicConfirmation, PublicInfo, PublicService } from '../core/api/public-booking-api.service';
import { addDays, today } from '../core/time-util';
import { NiweFooterComponent } from '../shared/niwe-footer.component';

/**
 * The public booking page (/book): no login. Pick a service, optionally a person, a day, then one of the times that
 * are really free, and leave a name and phone. The confirmation shows the code the guest needs to look up or cancel
 * the booking, and the payment instructions when a deposit is due. Same chrome-free centred card as the reservation page.
 */
@Component({
  selector: 'app-public-booking',
  standalone: true,
  imports: [TranslatePipe, NiweFooterComponent, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem 0;">
      <div class="card" style="width:480px; max-width:96vw; padding:2rem;">
        <div class="alert alert-danger" *ngIf="loadError">{{ loadError }}</div>

        <ng-container *ngIf="info && !confirmation">
          <h3 class="mb-1" style="text-align:center;">{{ info.title || ('Book an appointment' | translate) }}</h3>
          <p class="text-muted mb-3" style="text-align:center;" *ngIf="info.intro">{{ info.intro }}</p>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

          <form (ngSubmit)="submit()">
            <div class="form-group mb-2">
              <label class="form-label">{{ 'What would you like to book?' | translate }}</label>
              <select class="form-select" name="svc" [(ngModel)]="serviceId" (ngModelChange)="onServiceChange()" required>
                <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                <option *ngFor="let s of info.services" [ngValue]="s.id">{{ '{p0} · {p1} min' | translate: { p0: (s.name), p1: (s.durationMinutes) } }}<span *ngIf="s.price"> · {{ s.price | number: '1.0-2' }}</span></option>
              </select>
              <div class="text-muted" style="font-size:0.85rem;" *ngIf="service">
                <span *ngIf="service.description">{{ service.description }} </span>
                <span *ngIf="service.amountDue > 0">{{ 'A payment of {p0} is needed to confirm.' | translate: { p0: (service.amountDue | number: '1.0-2') } }}</span>
              </div>
            </div>

            <div class="form-group mb-2" *ngIf="service && service.staff.length > 1">
              <label class="form-label">{{ 'With' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
              <select class="form-select" name="stf" [(ngModel)]="staffId" (ngModelChange)="loadSlots()">
                <option [ngValue]="null">{{ 'Anyone' | translate }}</option>
                <option *ngFor="let p of service.staff" [ngValue]="p.id">{{ p.name }}</option>
              </select>
            </div>

            <div class="form-group mb-2" *ngIf="service">
              <label class="form-label">{{ 'Day' | translate }}</label>
              <input class="form-control" type="date" name="date" [(ngModel)]="date" [min]="min" [max]="max" (ngModelChange)="loadSlots()" required />
            </div>

            <div class="form-group mb-2" *ngIf="service && date">
              <label class="form-label">{{ 'Time' | translate }}</label>
              <div class="text-muted" *ngIf="loading">{{ 'Checking availability…' | translate }}</div>
              <div class="text-muted" *ngIf="!loading && loaded && !slots.length">{{ 'Nothing free on that day — try another.' | translate }}</div>
              <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="slots.length">
                <button type="button" class="btn" *ngFor="let t of slots" [class.btn-primary]="time === t" [class.btn-outline-secondary]="time !== t" (click)="time = t">{{ t }}</button>
              </div>
            </div>

            <ng-container *ngIf="time">
              <div class="form-group mb-2"><label class="form-label">{{ 'Your name' | translate }}</label><input class="form-control" name="nm" [(ngModel)]="name" required maxlength="120" /></div>
              <div class="form-group mb-2"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="ph" [(ngModel)]="phone" required maxlength="40" /></div>
              <div class="form-group mb-2"><label class="form-label">{{ 'Email' | translate }} <span class="text-muted">{{ '(for a confirmation, optional)' | translate }}</span></label>
                <input class="form-control" type="email" name="em" [(ngModel)]="email" maxlength="160" /></div>
              <div class="form-group mb-3"><label class="form-label">{{ 'Anything we should know?' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
                <textarea class="form-control" rows="2" name="nt" [(ngModel)]="notes" maxlength="500"></textarea></div>
              <!-- Honeypot: real visitors never see or fill this; bots do. -->
              <input type="text" name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" aria-hidden="true"
                     style="position:absolute; left:-9999px; width:1px; height:1px; opacity:0;" />
              <button class="btn btn-primary" style="width:100%;" type="submit" [disabled]="saving">{{ saving ? ('Booking…' | translate) : ('Book now' | translate) }}</button>
            </ng-container>
          </form>

          <hr class="mt-3 mb-3" />
          <div>
            <a href="javascript:void(0)" (click)="showLookup = !showLookup">{{ 'Already booked? Check or cancel' | translate }}</a>
            <form *ngIf="showLookup" class="mt-2" (ngSubmit)="lookup()">
              <div class="alert alert-danger" *ngIf="lookupError">{{ lookupError }}</div>
              <div class="flex gap-2">
                <input class="form-control" [placeholder]="'Booking code' | translate" name="lc" [(ngModel)]="lookupCode" required />
                <input class="form-control" [placeholder]="'Phone you booked with' | translate" name="lp" [(ngModel)]="lookupPhone" required />
              </div>
              <button class="btn btn-outline-primary mt-2" type="submit">{{ 'Find my booking' | translate }}</button>
            </form>
          </div>
        </ng-container>

        <ng-container *ngIf="confirmation">
          <h3 class="mb-2" style="text-align:center;">{{ confirmation.status === 'CANCELLED' ? 'Booking cancelled' : confirmation.status === 'PENDING_PAYMENT' ? ('Almost there' | translate) : ('You are booked' | translate) }}</h3>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <div class="card mb-3" style="padding:1rem; text-align:center;">
            <div class="text-muted">{{ 'Your booking code' | translate }}</div>
            <div style="font-size:1.8rem; font-weight:700; letter-spacing:0.1em;">{{ confirmation.confirmationCode }}</div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Keep it — you need it with your phone number to check or cancel.' | translate }}</div>
          </div>
          <div style="font-size:1.05rem; font-weight:600;">{{ confirmation.serviceName }}</div>
          <div>{{ confirmation.startAt | date: 'EEEE d MMMM y' }}, {{ confirmation.startAt | date: 'HH:mm' }} – {{ confirmation.endAt | date: 'HH:mm' }}</div>
          <div class="text-muted" *ngIf="confirmation.staffName">{{ 'With {p0}' | translate: { p0: (confirmation.staffName) } }}</div>
          <div class="alert alert-warning mt-3" *ngIf="confirmation.status === 'PENDING_PAYMENT'">
            <strong>{{ 'Your time is held until {p0}.' | translate: { p0: (confirmation.holdExpiresAt | date: 'd MMM, HH:mm') } }}</strong>
            {{ 'Pay {p0}' | translate: { p0: (confirmation.amountDue - confirmation.amountPaid | number: '1.0-2') } }}<span *ngIf="confirmation.invoiceNumber"> {{ '(invoice {p0})' | translate: { p0: (confirmation.invoiceNumber) } }}</span> {{ 'to confirm it; otherwise it is released.' | translate }}
            <div style="white-space:pre-line; margin-top:0.5rem;" *ngIf="confirmation.paymentInstructions">{{ confirmation.paymentInstructions }}</div>
          </div>
          <div class="alert alert-success mt-3" *ngIf="moved && confirmation.status !== 'CANCELLED'" id="moved-note">{{ 'Your booking has been moved to the time shown above.' | translate }}</div>
          <div class="alert alert-success mt-3" *ngIf="confirmation.status === 'CONFIRMED'">{{ 'Confirmed.' | translate }}<span *ngIf="confirmation.amountPaid > 0"> {{ 'Paid {p0}.' | translate: { p0: (confirmation.amountPaid | number: '1.0-2') } }}</span></div>
          <div class="card mt-3" style="padding:1rem;" *ngIf="moving && confirmation.canReschedule" id="move-panel">
            <strong>{{ 'Pick a new time' | translate }}</strong>
            <input class="form-control mt-2" type="date" name="mdate" [(ngModel)]="mdate" [min]="min" [max]="max" (ngModelChange)="loadMoveSlots()" />
            <div class="text-muted mt-2" *ngIf="mloading">{{ 'Checking availability…' | translate }}</div>
            <div class="text-muted mt-2" *ngIf="!mloading && mloaded && !mslots.length">{{ 'Nothing free on that day — try another.' | translate }}</div>
            <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
              <button type="button" class="btn" *ngFor="let t of mslots" [class.btn-primary]="mtime === t" [class.btn-outline-secondary]="mtime !== t" (click)="mtime = t">{{ t }}</button>
            </div>
            <div class="mt-2"><button class="btn btn-primary" id="move-confirm" type="button" [disabled]="!mtime || saving" (click)="move()">{{ 'Move my booking' | translate }}</button></div>
          </div>
          <div class="mt-3 flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-primary" id="move-open" *ngIf="confirmation.canReschedule" (click)="openMove()" [disabled]="saving">{{ 'Move to another time' | translate }}</button>
            <button class="btn btn-outline-secondary" id="add-calendar" *ngIf="confirmation.status === 'CONFIRMED' || confirmation.status === 'PENDING_PAYMENT'" (click)="addToCalendar()">{{ 'Add to my calendar' | translate }}</button>
            <button class="btn btn-outline-danger" *ngIf="confirmation.canCancel" (click)="cancel()" [disabled]="saving">{{ 'Cancel this booking' | translate }}</button>
            <button class="btn btn-outline-secondary" (click)="reset()">{{ 'Book another' | translate }}</button>
          </div>
        </ng-container>

        <p class="text-muted mt-3" style="text-align:center; font-size:0.8rem;"><a routerLink="/">{{ 'Back to the home page' | translate }}</a></p>
      </div>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
})
export class PublicBookingComponent implements OnInit {
  info: PublicInfo | null = null;
  confirmation: PublicConfirmation | null = null;
  serviceId: number | null = null;
  staffId: number | null = null;
  date = '';
  min = today();
  max = today();
  slots: string[] = [];
  loaded = false;
  loading = false;
  time: string | null = null;
  name = '';
  phone = '';
  email = '';
  notes = '';
  website = '';
  saving = false;
  error: string | null = null;
  loadError: string | null = null;
  showLookup = false;
  lookupCode = '';
  lookupPhone = '';
  lookupError: string | null = null;
  moving = false;
  moved = false;
  mdate = '';
  mtime: string | null = null;
  mslots: string[] = [];
  mloaded = false;
  mloading = false;

  constructor(private api: PublicBookingApiService) {}

  get service(): PublicService | null {
    return this.info?.services.find((s) => s.id === this.serviceId) ?? null;
  }

  ngOnInit(): void {
    this.api.info().subscribe({
      next: (i) => { this.info = i; this.max = addDays(today(), i.maxAdvanceDays); },
      error: (e) => (this.loadError = e?.error?.message ?? 'Online booking is not available right now.'),
    });
  }

  onServiceChange(): void {
    this.staffId = null;
    this.loadSlots();
  }

  loadSlots(): void {
    this.slots = [];
    this.time = null;
    this.loaded = false;
    this.error = null;
    if (!this.serviceId || !this.date) return;
    this.loading = true;
    this.api.slots(this.serviceId, this.date, this.staffId).subscribe({
      next: (l) => { this.slots = l; this.loading = false; this.loaded = true; },
      error: (e) => { this.loading = false; this.error = e?.error?.message ?? 'Could not check availability'; },
    });
  }

  submit(): void {
    if (!this.serviceId || !this.time) return;
    this.saving = true;
    this.error = null;
    this.api.book({
      serviceId: this.serviceId, date: this.date, time: this.time, staffUserId: this.staffId, name: this.name.trim(), phone: this.phone.trim(),
      email: this.email.trim() || null, notes: this.notes.trim() || null, website: this.website,
    }).subscribe({
      next: (c) => { this.confirmation = c; this.saving = false; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not complete the booking. Please try another time.'; this.loadSlots(); },
    });
  }

  lookup(): void {
    this.lookupError = null;
    this.api.lookup(this.lookupCode.trim(), this.lookupPhone.trim()).subscribe({
      next: (c) => { this.confirmation = c; this.phone = this.lookupPhone.trim(); },
      error: (e) => (this.lookupError = e?.error?.message ?? 'We could not find a booking with that code and phone number.'),
    });
  }

  cancel(): void {
    if (!this.confirmation) return;
    const phone = this.phone || this.lookupPhone;
    if (!confirm('Cancel this booking?')) return;
    this.saving = true;
    this.error = null;
    this.api.cancel(this.confirmation.confirmationCode, phone.trim()).subscribe({
      next: (c) => { this.confirmation = c; this.saving = false; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not cancel this booking.'; },
    });
  }

  private contactPhone(): string {
    return (this.phone || this.lookupPhone).trim();
  }

  openMove(): void {
    this.moving = !this.moving;
    this.error = null;
    if (this.moving && this.confirmation) {
      this.mdate = this.confirmation.startAt.slice(0, 10);
      this.loadMoveSlots();
    }
  }

  loadMoveSlots(): void {
    this.mslots = [];
    this.mtime = null;
    this.mloaded = false;
    if (!this.confirmation || !this.mdate) return;
    this.mloading = true;
    this.api.rescheduleSlots(this.confirmation.confirmationCode, this.contactPhone(), this.mdate).subscribe({
      next: (l) => { this.mslots = l; this.mloading = false; this.mloaded = true; },
      error: (e) => { this.mloading = false; this.error = e?.error?.message ?? 'Could not check availability'; },
    });
  }

  move(): void {
    if (!this.confirmation || !this.mtime) return;
    this.saving = true;
    this.error = null;
    this.api.reschedule(this.confirmation.confirmationCode, this.contactPhone(), this.mdate, this.mtime).subscribe({
      next: (c) => { this.confirmation = c; this.saving = false; this.moving = false; this.moved = true; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not move this booking.'; this.loadMoveSlots(); },
    });
  }

  addToCalendar(): void {
    if (!this.confirmation) return;
    const code = this.confirmation.confirmationCode;
    this.api.calendar(code, this.contactPhone()).subscribe({
      next: (b) => {
        const url = URL.createObjectURL(b);
        const link = document.createElement('a');
        link.href = url;
        link.download = code + '.ics';
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 2000);
      },
      error: () => (this.error = 'Could not create the calendar file.'),
    });
  }

  reset(): void {
    this.confirmation = null;
    this.moving = false;
    this.moved = false;
    this.serviceId = null;
    this.staffId = null;
    this.date = '';
    this.slots = [];
    this.time = null;
    this.error = null;
    this.showLookup = false;
  }
}
