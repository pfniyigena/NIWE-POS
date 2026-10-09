import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicReservationApiService, PublicReservationConfirmation, PublicTable } from '../core/api/public-reservation-api.service';

/**
 * Fourier: "Public be able to make reservation", then "Public reservations should show available
 * table for that day. The person then book table. Once Booked it can not be used by anyone. To
 * use the reserved table, you need to enter reservation number." Mirrors LoginComponent's own
 * chrome-free centered-card layout (no <app-shell>, since an anonymous visitor has no sidenav).
 * Flow: pick a date → fetch that day's available tables (PublicReservationApiService.availableTables,
 * which hits the one other unauthenticated endpoint) → pick a table → fill in guest details → book.
 * The confirmation screen shows the reservation number prominently — that's the ONE time the guest
 * ever sees it, and staff later look the booking up by that same number (ReservationListComponent's
 * "Seat by reservation number" card) rather than by picking it out of a list.
 */
@Component({
  selector: 'app-public-reservation',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem 0;">
      <div class="card" style="width:440px; padding:2rem;">
        <ng-container *ngIf="!confirmation">
          <h3 class="mb-3" style="text-align:center;">Reserve a table</h3>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <form (ngSubmit)="submit()">
            <div class="form-group mb-2">
              <label class="form-label">Date</label>
              <input class="form-control" type="date" name="date" [(ngModel)]="date" [min]="today" (ngModelChange)="onDateChange()" required />
            </div>

            <ng-container *ngIf="date">
              <div class="form-group mb-2">
                <label class="form-label">Available tables</label>
                <div *ngIf="loadingTables" class="text-muted">Checking availability…</div>
                <div *ngIf="!loadingTables && tables.length === 0" class="text-muted">
                  No tables available on that date — try another day.
                </div>
                <div *ngIf="!loadingTables && tables.length > 0" class="flex gap-2" style="flex-wrap:wrap;">
                  <label
                    *ngFor="let t of tables"
                    class="btn"
                    [class.btn-primary]="tableId === t.id"
                    [class.btn-outline-secondary]="tableId !== t.id"
                    style="cursor:pointer;"
                  >
                    <input type="radio" name="tableId" [value]="t.id" [(ngModel)]="tableId" style="display:none;" />
                    {{ t.name }} <span *ngIf="t.section">({{ t.section }})</span> — seats {{ t.capacity }}
                  </label>
                </div>
              </div>
            </ng-container>

            <ng-container *ngIf="tableId">
              <div class="form-group mb-2">
                <label class="form-label">Time</label>
                <input class="form-control" type="time" name="time" [(ngModel)]="time" required />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="guestName" [(ngModel)]="guestName" required />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">Phone number</label>
                <input class="form-control" name="guestPhone" [(ngModel)]="guestPhone" required />
              </div>
              <div class="form-group mb-2">
                <label class="form-label">Party size</label>
                <input class="form-control" type="number" min="1" name="partySize" [(ngModel)]="partySize" required />
              </div>
              <div class="form-group mb-3">
                <label class="form-label">Notes (optional)</label>
                <textarea class="form-control" rows="2" name="notes" [(ngModel)]="notes"></textarea>
              </div>
              <button class="btn btn-primary btn-block" type="submit" [disabled]="loading">
                {{ loading ? 'Booking…' : 'Book table' }}
              </button>
            </ng-container>
          </form>
        </ng-container>
        <ng-container *ngIf="confirmation as c">
          <h3 class="mb-3" style="text-align:center;">Reservation confirmed</h3>
          <p>
            Thanks, {{ c.guestName }} — your table{{ c.tableName ? ' (' + c.tableName + ')' : '' }} for {{ c.partySize }} on
            {{ c.reservedFor | date: 'medium' }} is booked.
          </p>
          <div class="card" style="padding:1rem; text-align:center; margin:1rem 0;">
            <div class="text-muted" style="font-size:0.8rem;">Your reservation number</div>
            <div style="font-size:1.5rem; font-weight:600; letter-spacing:0.05em;">{{ c.confirmationCode }}</div>
          </div>
          <p class="text-muted" style="font-size:0.85rem;">
            Keep this number — staff will ask for it when you arrive to seat your table.
          </p>
        </ng-container>
        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">Staff login</a>
        </p>
      </div>
    </div>
  `,
})
export class PublicReservationComponent {
  today = new Date().toISOString().slice(0, 10);
  date = '';
  time = '';
  tables: PublicTable[] = [];
  tableId: number | null = null;
  loadingTables = false;

  guestName = '';
  guestPhone = '';
  partySize = 2;
  notes = '';
  error: string | null = null;
  loading = false;
  confirmation: PublicReservationConfirmation | null = null;

  constructor(private publicReservationApi: PublicReservationApiService) {}

  onDateChange(): void {
    this.tableId = null;
    this.tables = [];
    if (!this.date) return;
    this.loadingTables = true;
    this.error = null;
    this.publicReservationApi.availableTables(this.date).subscribe({
      next: (tables) => {
        this.loadingTables = false;
        this.tables = tables;
      },
      error: (err) => {
        this.loadingTables = false;
        this.error = err?.error?.message ?? 'Failed to check table availability';
      },
    });
  }

  submit(): void {
    if (!this.date || !this.tableId) {
      this.error = 'Please choose a date and a table.';
      return;
    }
    if (!this.time || !this.guestName.trim() || !this.guestPhone.trim()) {
      this.error = 'Please fill in your name, phone number, and a time.';
      return;
    }
    const reservedForIso = new Date(`${this.date}T${this.time}`).toISOString();
    if (new Date(reservedForIso).getTime() <= Date.now()) {
      this.error = 'Please choose a date/time in the future.';
      return;
    }
    this.error = null;
    this.loading = true;
    this.publicReservationApi
      .create({
        guestName: this.guestName.trim(),
        guestPhone: this.guestPhone.trim(),
        partySize: this.partySize || 1,
        reservedFor: reservedForIso,
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
          this.error = err?.error?.message ?? 'Failed to book — please try again';
        },
      });
  }
}
