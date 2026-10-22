import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HotelPublicApiService, HotelPublicAvailability, HotelPublicBooking, HotelPublicInfo, HotelPublicOffer } from '../core/api/hotel-public-api.service';
import { addDays, today } from '../core/time-util';

/**
 * The hotel's public booking page (/stay): no login. Pick dates and guests, see the rooms that are really free with the full price (tax
 * included), choose one and leave a name and phone. The confirmation shows the booking code the guest needs, with their phone number, to
 * check or cancel the booking, and the deposit to pay when one is asked for.
 */
@Component({
  selector: 'app-public-hotel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; padding:1rem 0;">
      <div class="card" style="width:640px; max-width:96vw; padding:2rem;">
        <div class="alert alert-danger" *ngIf="loadError">{{ loadError }}</div>

        <ng-container *ngIf="info && !confirmation">
          <h3 class="mb-1" style="text-align:center;">{{ info.hotelName }}</h3>
          <p class="text-muted mb-3" style="text-align:center; white-space:pre-line;" *ngIf="info.intro">{{ info.intro }}</p>
          <div class="text-muted mb-3" style="text-align:center; font-size:0.85rem;">
            Check-in from {{ info.checkInTime }} · check-out by {{ info.checkOutTime }}<span *ngIf="info.address"> · {{ info.address }}</span><span *ngIf="info.phone"> · {{ info.phone }}</span>
          </div>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

          <form (ngSubmit)="search()">
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group" style="flex:1; min-width:140px;"><label class="form-label">Arrival</label>
                <input class="form-control h-from" type="date" name="from" [(ngModel)]="from" [min]="min" [max]="max" (ngModelChange)="fixTo()" required /></div>
              <div class="form-group" style="flex:1; min-width:140px;"><label class="form-label">Departure</label>
                <input class="form-control h-to" type="date" name="to" [(ngModel)]="to" [min]="nextDay" required /></div>
              <div class="form-group" style="width:90px;"><label class="form-label">Adults</label>
                <input class="form-control h-adults" type="number" name="adults" [(ngModel)]="adults" min="1" max="10" required /></div>
              <div class="form-group" style="width:90px;"><label class="form-label">Children</label>
                <input class="form-control h-children" type="number" name="children" [(ngModel)]="children" min="0" max="10" /></div>
            </div>
            <button class="btn btn-primary h-search" type="submit" [disabled]="searching" style="width:100%;">{{ searching ? 'Checking…' : 'See available rooms' }}</button>
          </form>

          <ng-container *ngIf="result">
            <h5 class="mt-3 mb-2">{{ result.nights }} night{{ result.nights === 1 ? '' : 's' }}, {{ result.from }} to {{ result.to }}</h5>
            <div class="text-muted h-none" *ngIf="!result.offers.length">Nothing is free for those dates and that many guests. Try other dates.</div>
            <div class="card offer" *ngFor="let o of result.offers" style="padding:1rem; margin-bottom:0.8rem;" [style.border-color]="chosen?.roomTypeId === o.roomTypeId ? '#2563eb' : ''">
              <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
                <div style="flex:1; min-width:200px;">
                  <div style="font-size:1.1rem; font-weight:600;">{{ o.name }}</div>
                  <div class="text-muted" style="font-size:0.85rem;">Sleeps up to {{ o.maxOccupancy }}<span *ngIf="o.roomsLeft <= 3"> · only {{ o.roomsLeft }} left</span></div>
                  <div style="font-size:0.9rem; margin-top:0.3rem;" *ngIf="o.description">{{ o.description }}</div>
                  <div class="text-muted" style="font-size:0.8rem;" *ngIf="o.amenities">{{ o.amenities }}</div>
                </div>
                <div style="text-align:right;">
                  <div style="font-size:1.3rem; font-weight:700;" class="offer-total">{{ o.gross | number: '1.0-2' }} <span style="font-size:0.8rem;">{{ info.currency }}</span></div>
                  <div class="text-muted" style="font-size:0.8rem;">total, tax included · about {{ o.averageRate | number: '1.0-0' }} a night before tax</div>
                  <div style="font-size:0.8rem;" *ngIf="o.depositDue > 0">Deposit to keep it: {{ o.depositDue | number: '1.0-2' }}</div>
                  <button class="btn btn-outline-primary mt-1 choose-offer" type="button" (click)="choose(o)">{{ chosen?.roomTypeId === o.roomTypeId ? 'Chosen' : 'Choose' }}</button>
                </div>
              </div>
            </div>
          </ng-container>

          <form *ngIf="chosen && result" class="mt-3" (ngSubmit)="submit()">
            <h5 class="mb-2">Your details</h5>
            <div class="form-group mb-2"><label class="form-label">Your name</label><input class="form-control h-name" name="nm" [(ngModel)]="name" required maxlength="120" /></div>
            <div class="form-group mb-2"><label class="form-label">Phone</label><input class="form-control h-phone" name="ph" [(ngModel)]="phone" required maxlength="40" /></div>
            <div class="form-group mb-2"><label class="form-label">Email <span class="text-muted">(for your confirmation)</span></label>
              <input class="form-control h-email" type="email" name="em" [(ngModel)]="email" maxlength="160" /></div>
            <div class="form-group mb-3"><label class="form-label">Anything we should know? <span class="text-muted">(optional)</span></label>
              <textarea class="form-control" rows="2" name="nt" [(ngModel)]="notes" maxlength="500"></textarea></div>
            <!-- Honeypot: real visitors never see or fill this; bots do. -->
            <input type="text" name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" aria-hidden="true"
                   style="position:absolute; left:-9999px; width:1px; height:1px; opacity:0;" />
            <div class="alert alert-info" *ngIf="chosen.depositDue > 0">
              A deposit of <strong>{{ chosen.depositDue | number: '1.0-2' }}</strong> keeps the room. We hold it for {{ info.holdHours }} hours; after that it is released.
            </div>
            <button class="btn btn-primary h-book" type="submit" [disabled]="saving" style="width:100%;">{{ saving ? 'Booking…' : 'Book ' + chosen.name + ' for ' + (chosen.gross | number: '1.0-2') }}</button>
          </form>

          <hr class="mt-3 mb-3" />
          <div>
            <a href="javascript:void(0)" class="h-lookup-link" (click)="showLookup = !showLookup">Already booked? Check or cancel</a>
            <form *ngIf="showLookup" class="mt-2" (ngSubmit)="lookup()">
              <div class="alert alert-danger" *ngIf="lookupError">{{ lookupError }}</div>
              <div class="flex gap-2">
                <input class="form-control h-lcode" placeholder="Booking code" name="lc" [(ngModel)]="lookupCode" required />
                <input class="form-control h-lphone" placeholder="Phone you booked with" name="lp" [(ngModel)]="lookupPhone" required />
              </div>
              <button class="btn btn-outline-primary mt-2 h-find" type="submit">Find my booking</button>
            </form>
          </div>
        </ng-container>

        <ng-container *ngIf="confirmation as c">
          <h3 class="mb-2" style="text-align:center;">{{ c.status === 'CANCELLED' ? 'Booking cancelled' : c.status === 'BOOKED' ? 'You are booked' : 'Your booking' }}</h3>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <div class="card mb-3" style="padding:1rem; text-align:center;">
            <div class="text-muted">Your booking code</div>
            <div class="h-code" style="font-size:1.8rem; font-weight:700; letter-spacing:0.1em;">{{ c.code }}</div>
            <div class="text-muted" style="font-size:0.8rem;">Keep it — you need it with your phone number to check or cancel. Reference {{ c.bookingNumber }}.</div>
          </div>
          <div style="font-size:1.05rem; font-weight:600;">{{ c.roomTypeName }} · {{ c.nights }} night{{ c.nights === 1 ? '' : 's' }}</div>
          <div>{{ c.checkInDate | date: 'EEEE d MMMM y' }} → {{ c.checkOutDate | date: 'EEEE d MMMM y' }}</div>
          <div class="text-muted">{{ c.adults }} adult{{ c.adults === 1 ? '' : 's' }}<span *ngIf="c.children"> + {{ c.children }} child{{ c.children === 1 ? '' : 'ren' }}</span> · check-in from {{ c.checkInTime }}, check-out by {{ c.checkOutTime }}</div>
          <div class="mt-1" *ngIf="c.total > 0">Total {{ c.total | number: '1.0-2' }} {{ info?.currency }} (tax included)</div>
          <div class="alert alert-warning mt-3 h-hold" *ngIf="c.holdExpiresAt && c.status === 'BOOKED'">
            <strong>Your room is held until {{ c.holdExpiresAt | date: 'd MMM, HH:mm' }}.</strong>
            Pay the deposit of {{ c.depositDue | number: '1.0-2' }} to keep it; otherwise it is released.
            <div style="white-space:pre-line; margin-top:0.5rem;" *ngIf="c.paymentInstructions">{{ c.paymentInstructions }}</div>
          </div>
          <div class="alert alert-success mt-3" *ngIf="c.status === 'BOOKED' && !c.holdExpiresAt">Confirmed.<span *ngIf="c.depositPaid > 0"> Deposit received: {{ c.depositPaid | number: '1.0-2' }}.</span></div>
          <div class="alert alert-info mt-3 h-cancel-note" *ngIf="c.cancelNote">{{ c.cancelNote }}</div>
          <div class="mt-3 flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-danger h-cancel" *ngIf="c.canCancel" (click)="cancel()" [disabled]="saving">Cancel this booking</button>
            <button class="btn btn-outline-secondary h-again" (click)="reset()">Book another</button>
          </div>
        </ng-container>

        <p class="text-muted mt-3" style="text-align:center; font-size:0.8rem;"><a routerLink="/">Back to the home page</a></p>
      </div>
    </div>
  `,
})
export class PublicHotelComponent implements OnInit {
  info: HotelPublicInfo | null = null;
  result: HotelPublicAvailability | null = null;
  chosen: HotelPublicOffer | null = null;
  confirmation: HotelPublicBooking | null = null;
  min = today();
  max = today();
  from = addDays(today(), 1);
  to = addDays(today(), 2);
  nextDay = addDays(today(), 2);
  adults = 2;
  children = 0;
  name = '';
  phone = '';
  email = '';
  notes = '';
  website = '';
  searching = false;
  saving = false;
  error: string | null = null;
  loadError: string | null = null;
  showLookup = false;
  lookupCode = '';
  lookupPhone = '';
  lookupError: string | null = null;
  private bookedPhone = '';

  constructor(private api: HotelPublicApiService) {}

  ngOnInit(): void {
    this.api.info().subscribe({
      next: (i) => {
        this.info = i;
        this.max = addDays(today(), i.maxDaysAhead);
      },
      error: () => (this.loadError = 'Online booking is not available right now.'),
    });
  }

  fixTo(): void {
    this.nextDay = addDays(this.from, 1);
    if (!this.to || this.to <= this.from) this.to = this.nextDay;
  }

  search(): void {
    this.error = null;
    this.chosen = null;
    this.searching = true;
    this.api.availability(this.from, this.to, this.adults, this.children || 0).subscribe({
      next: (r) => {
        this.searching = false;
        this.result = r;
      },
      error: (e) => {
        this.searching = false;
        this.result = null;
        this.error = e?.error?.message || 'Could not check availability.';
      },
    });
  }

  choose(o: HotelPublicOffer): void {
    this.chosen = o;
  }

  submit(): void {
    if (!this.chosen || !this.result) return;
    this.error = null;
    this.saving = true;
    this.bookedPhone = this.phone;
    this.api.book({
      roomTypeId: this.chosen.roomTypeId, checkInDate: this.result.from, checkOutDate: this.result.to, adults: this.result.adults, children: this.result.children,
      name: this.name, phone: this.phone, email: this.email || null, notes: this.notes || null, website: this.website,
    }).subscribe({
      next: (c) => {
        this.saving = false;
        this.confirmation = c;
      },
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message || 'Could not make the booking.';
      },
    });
  }

  lookup(): void {
    this.lookupError = null;
    this.bookedPhone = this.lookupPhone;
    this.api.lookup(this.lookupCode.trim(), this.lookupPhone).subscribe({
      next: (c) => (this.confirmation = c),
      error: (e) => (this.lookupError = e?.error?.message || 'No booking found with that code and phone number.'),
    });
  }

  cancel(): void {
    if (!this.confirmation) return;
    this.error = null;
    this.saving = true;
    this.api.cancel(this.confirmation.code, this.bookedPhone).subscribe({
      next: (c) => {
        this.saving = false;
        this.confirmation = c;
      },
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message || 'Could not cancel.';
      },
    });
  }

  reset(): void {
    this.confirmation = null;
    this.result = null;
    this.chosen = null;
    this.error = null;
    this.name = this.phone = this.email = this.notes = '';
    this.showLookup = false;
  }
}
