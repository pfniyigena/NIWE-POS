import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicReservationApiService, PublicReservationConfirmation, PublicReservationInfo, PublicSlot, PublicTable } from '../core/api/public-reservation-api.service';
import { NiweFooterComponent } from '../shared/niwe-footer.component';

import { T } from '../core/i18n/t';
/**
 * Fourier: "Public be able to make reservation", then "Public reservations should show available
 * table for that day. The person then book table. Once Booked it can not be used by anyone. To
 * use the reserved table, you need to enter reservation number." Chrome-free centered card (an
 * anonymous visitor has no sidenav).
 *
 * <p>Flow: party size and date → the times still open that day (inside the restaurant's hours) →
 * the tables free at that time → name, phone, optional e-mail → book. The confirmation shows the
 * reservation number prominently, and the deposit the guest is asked to bring when there is one.
 */
@Component({
  selector: 'app-public-reservation',
  standalone: true,
  imports: [TranslatePipe, NiweFooterComponent, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem 0;">
      <div class="card" style="width:480px; max-width:96vw; padding:2rem;">
        <ng-container *ngIf="!confirmation">
          <h3 class="mb-1" style="text-align:center;">{{ 'Reserve a table' | translate }}</h3>
          <p class="text-muted pr-hours" style="text-align:center; font-size:0.85rem;" *ngIf="info">{{ '{p0} · open {p1}–{p2}' | translate: { p0: (info.restaurantName), p1: (info.openTime), p2: (info.closeTime) } }}</p>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <form (ngSubmit)="submit()">
            <div class="flex gap-2 mb-2">
              <div class="form-group" style="flex:1;">
                <label class="form-label">{{ 'Date' | translate }}</label>
                <input class="form-control pr-date" type="date" name="date" [(ngModel)]="date" [min]="today" [max]="maxDate" (ngModelChange)="onChange()" required />
              </div>
              <div class="form-group" style="width:110px;">
                <label class="form-label">{{ 'Guests' | translate }}</label>
                <input class="form-control pr-party" type="number" min="1" max="50" name="partySize" [(ngModel)]="partySize" (ngModelChange)="onChange()" required />
              </div>
            </div>

            <ng-container *ngIf="date">
              <div class="form-group mb-2">
                <label class="form-label">{{ 'Time' | translate }}</label>
                <div *ngIf="loadingSlots" class="text-muted">{{ 'Checking the day…' | translate }}</div>
                <div *ngIf="!loadingSlots && openSlots.length === 0" class="text-muted pr-noslots">{{ 'No tables are free that day for {p0} — try another day or fewer guests.' | translate: { p0: (partySize) } }}</div>
                <div *ngIf="!loadingSlots && openSlots.length > 0" class="flex gap-1" style="flex-wrap:wrap;">
                  <button type="button" class="btn btn-sm pr-slot" *ngFor="let s of openSlots"
                    [class.btn-primary]="slot?.time === s.time" [class.btn-outline-secondary]="slot?.time !== s.time"
                    (click)="pickSlot(s)">{{ s.label }}</button>
                </div>
              </div>
            </ng-container>

            <ng-container *ngIf="slot">
              <div class="form-group mb-2">
                <label class="form-label">{{ 'Table' | translate }}</label>
                <div *ngIf="loadingTables" class="text-muted">{{ 'Checking tables…' | translate }}</div>
                <div *ngIf="!loadingTables && tables.length === 0" class="text-muted">{{ 'No table is free at that time — pick another time.' | translate }}</div>
                <div *ngIf="!loadingTables && tables.length > 0" class="flex gap-2" style="flex-wrap:wrap;">
                  <label *ngFor="let t of tables" class="btn pr-table" [class.btn-primary]="tableId === t.id" [class.btn-outline-secondary]="tableId !== t.id" style="cursor:pointer;">
                    <input type="radio" name="tableId" [value]="t.id" [(ngModel)]="tableId" style="display:none;" />
                    {{ t.name }} <span *ngIf="t.section">({{ t.section }})</span> {{ '— seats {p0}' | translate: { p0: (t.capacity) } }}
                  </label>
                </div>
              </div>
            </ng-container>

            <ng-container *ngIf="tableId">
              <div class="form-group mb-2">
                <label class="form-label">{{ 'Name' | translate }}</label>
                <input class="form-control pr-name" name="guestName" [(ngModel)]="guestName" required />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">{{ 'Phone number' | translate }}</label>
                <input class="form-control pr-phone" name="guestPhone" [(ngModel)]="guestPhone" required />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">{{ 'E-mail (optional — for a reminder)' | translate }}</label>
                <input class="form-control pr-email" type="email" name="guestEmail" [(ngModel)]="guestEmail" />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">{{ 'Notes (optional)' | translate }}</label>
                <textarea class="form-control" rows="2" name="notes" [(ngModel)]="notes"></textarea>
              </div>
              <p class="text-muted pr-deposit-note" style="font-size:0.85rem;" *ngIf="deposit() > 0">
                {{ 'A deposit of' | translate }} <strong>{{ deposit() | number: '1.0-2' }}</strong> {{ 'is asked for this booking. You pay it at the restaurant and it comes off your bill.' | translate }}
              </p>
              <button class="btn btn-primary btn-block pr-book" type="submit" [disabled]="loading">
                {{ loading ? ('Booking…' | translate) : ('Book table' | translate) }}
              </button>
            </ng-container>
          </form>
        </ng-container>
        <ng-container *ngIf="confirmation as c">
          <h3 class="mb-3" style="text-align:center;">{{ 'Reservation confirmed' | translate }}</h3>
          <p>
            {{ 'Thanks, {p0} — your table{p1} for {p2} on {p3} is booked' | translate: { p0: (c.guestName), p1: (c.tableName ? ' (' + c.tableName + ')' : ''), p2: (c.partySize), p3: (c.reservedFor | date: 'EEEE d MMMM, HH:mm') } }}<span *ngIf="c.durationMinutes"> {{ '(held for {p0} minutes)' | translate: { p0: (c.durationMinutes) } }}</span>.
          </p>
          <div class="card" style="padding:1rem; text-align:center; margin:1rem 0;">
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Your reservation number' | translate }}</div>
            <div class="pr-code" style="font-size:1.5rem; font-weight:600; letter-spacing:0.05em;">{{ c.confirmationCode }}</div>
          </div>
          <p class="pr-deposit" *ngIf="(c.depositAmount ?? 0) > 0">{{ 'Please bring a deposit of' | translate }} <strong>{{ c.depositAmount | number: '1.0-2' }}</strong>{{ '. It comes off your bill.' | translate }}</p>
          <p class="text-muted" style="font-size:0.85rem;">
            {{ 'Keep this number — staff will ask for it when you arrive to seat your table.' | translate }}
          </p>
        </ng-container>
        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">{{ 'Staff login' | translate }}</a>
        </p>
      </div>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
})
export class PublicReservationComponent implements OnInit {
  info: PublicReservationInfo | null = null;
  today = new Date().toISOString().slice(0, 10);
  maxDate = '';
  date = '';
  partySize = 2;
  slots: PublicSlot[] = [];
  openSlots: PublicSlot[] = [];
  slot: PublicSlot | null = null;
  loadingSlots = false;
  tables: PublicTable[] = [];
  tableId: number | null = null;
  loadingTables = false;

  guestName = '';
  guestPhone = '';
  guestEmail = '';
  notes = '';
  error: string | null = null;
  loading = false;
  confirmation: PublicReservationConfirmation | null = null;

  constructor(private publicReservationApi: PublicReservationApiService) {}

  ngOnInit(): void {
    this.publicReservationApi.info().subscribe({
      next: (i) => {
        this.info = i;
        this.today = i.today;
        const max = new Date(i.today + 'T00:00:00');
        max.setDate(max.getDate() + i.bookingDaysAhead);
        this.maxDate = max.toISOString().slice(0, 10);
      },
      error: () => {},
    });
  }

  deposit(): number {
    return (this.info?.depositPerGuest ?? 0) * (this.partySize || 1);
  }

  /** The date or the party changed: the times (and everything after them) have to be looked up again. */
  onChange(): void {
    this.slot = null;
    this.tableId = null;
    this.tables = [];
    this.slots = [];
    this.openSlots = [];
    if (!this.date || !this.partySize || this.partySize < 1) return;
    this.loadingSlots = true;
    this.error = null;
    this.publicReservationApi.slots(this.date, this.partySize).subscribe({
      next: (s) => {
        this.loadingSlots = false;
        this.slots = s;
        this.openSlots = s.filter((x) => x.tablesFree > 0);
      },
      error: (err) => {
        this.loadingSlots = false;
        this.error = err?.error?.message ?? T('Failed to check availability');
      },
    });
  }

  pickSlot(s: PublicSlot): void {
    this.slot = s;
    this.tableId = null;
    this.tables = [];
    this.loadingTables = true;
    this.error = null;
    this.publicReservationApi.availableTables(this.date, s.time, this.partySize).subscribe({
      next: (t) => {
        this.loadingTables = false;
        this.tables = t;
      },
      error: (err) => {
        this.loadingTables = false;
        this.error = err?.error?.message ?? T('Failed to check table availability');
      },
    });
  }

  submit(): void {
    if (!this.slot || !this.tableId) {
      this.error = T('Please choose a time and a table.');
      return;
    }
    if (!this.guestName.trim() || !this.guestPhone.trim()) {
      this.error = T('Please fill in your name and phone number.');
      return;
    }
    this.error = null;
    this.loading = true;
    this.publicReservationApi
      .create({
        guestName: this.guestName.trim(),
        guestPhone: this.guestPhone.trim(),
        guestEmail: this.guestEmail.trim() || null,
        partySize: this.partySize || 1,
        reservedFor: this.slot.time,
        tableId: this.tableId,
        notes: this.notes || null,
      })
      .subscribe({
        next: (c) => {
          this.loading = false;
          this.confirmation = c;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? T('Failed to book — please try again');
        },
      });
  }
}
