import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ReservationApiService } from '../core/api/reservation-api.service';
import { DiningTableApiService } from '../core/api/dining-table-api.service';
import { DiningTable, Reservation } from '../core/models';
import { AuthService } from '../core/auth.service';

/**
 * Admin/staff > Restaurant > Reservations (Fourier: "Reservations"). List + inline create form,
 * same "one screen" convenience as MenuAdminComponent.
 *
 * <p>Fourier's follow-up — "To use the reserved table, you need to enter reservation number. But
 * the reservation can be cancelled anytime by admin" — adds the "Seat by reservation number" card
 * above the list (the primary front-desk flow: a guest states their number, staff types it in)
 * and restricts the Cancel button to ADMIN (the backend enforces this too, so this is a UI
 * convenience, not the real guard).
 */
@Component({
  selector: 'app-reservation-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Reservations</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
          <button class="btn btn-primary" (click)="startNew()">+ New reservation</button>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 style="font-size:1rem;" class="mb-2">Seat by reservation number</h4>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="min-width:200px;">
            <label class="form-label">Reservation number</label>
            <input class="form-control" [(ngModel)]="seatCode" name="seatCode" placeholder="RES-123456" />
          </div>
          <button class="btn btn-primary" [disabled]="!seatCode.trim() || seating" (click)="seatByCode()">
            {{ seating ? 'Seating…' : 'Seat' }}
          </button>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="editing">
        <h4 style="font-size:1rem;" class="mb-2">New reservation</h4>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:160px;">
            <label class="form-label">Guest name</label>
            <input class="form-control" [(ngModel)]="guestName" name="guestName" />
          </div>
          <div class="form-group" style="flex:1; min-width:140px;">
            <label class="form-label">Phone</label>
            <input class="form-control" [(ngModel)]="guestPhone" name="guestPhone" />
          </div>
          <div class="form-group" style="width:100px;">
            <label class="form-label">Party size</label>
            <input class="form-control" type="number" min="1" [(ngModel)]="partySize" name="partySize" />
          </div>
          <div class="form-group" style="min-width:200px;">
            <label class="form-label">Date & time</label>
            <input class="form-control" type="datetime-local" [(ngModel)]="reservedForLocal" name="reservedFor" />
          </div>
          <div class="form-group" style="min-width:160px;">
            <label class="form-label">Table (optional)</label>
            <select class="form-select" [(ngModel)]="tableId" name="tableId">
              <option [ngValue]="null">— Unassigned —</option>
              <option *ngFor="let t of tables" [ngValue]="t.id">{{ t.name }}</option>
            </select>
          </div>
        </div>
        <div class="form-group mb-2">
          <label class="form-label">Notes</label>
          <input class="form-control" [(ngModel)]="notes" name="notes" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" (click)="save()">Save</button>
          <button class="btn btn-link" (click)="editing = false">Cancel</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>Reservation #</th>
            <th>Guest</th>
            <th>Party</th>
            <th>When</th>
            <th>Table</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of reservations">
            <td>{{ r.confirmationCode || '—' }}</td>
            <td>{{ r.guestName }}<span class="text-muted" *ngIf="r.guestPhone"> — {{ r.guestPhone }}</span></td>
            <td>{{ r.partySize }}</td>
            <td>{{ r.reservedFor | date: 'medium' }}</td>
            <td>{{ r.tableName || '—' }}</td>
            <td>{{ r.status }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-primary" *ngIf="r.status === 'BOOKED'" (click)="seat(r)">Seat</button>
              <button class="btn btn-sm btn-outline-danger" *ngIf="r.status === 'BOOKED' && isAdmin" (click)="cancel(r)">Cancel</button>
            </td>
          </tr>
          <tr *ngIf="reservations.length === 0">
            <td colspan="7" class="text-muted">No reservations yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ReservationListComponent implements OnInit {
  reservations: Reservation[] = [];
  tables: DiningTable[] = [];
  error: string | null = null;

  editing = false;
  guestName = '';
  guestPhone: string | null = null;
  partySize = 2;
  reservedForLocal = '';
  tableId: number | null = null;
  notes: string | null = null;

  seatCode = '';
  seating = false;

  constructor(
    private reservationApi: ReservationApiService,
    private tableApi: DiningTableApiService,
    private auth: AuthService,
    private router: Router
  ) {}

  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    this.load();
    this.tableApi.listAll().subscribe({
      next: (t) => (this.tables = t),
      error: () => {},
    });
  }

  load(): void {
    this.reservationApi.listAll().subscribe({
      next: (r) => (this.reservations = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load reservations'),
    });
  }

  startNew(): void {
    this.editing = true;
    this.guestName = '';
    this.guestPhone = null;
    this.partySize = 2;
    this.reservedForLocal = '';
    this.tableId = null;
    this.notes = null;
  }

  save(): void {
    if (!this.guestName.trim() || !this.reservedForLocal) {
      this.error = 'Guest name and date/time are required';
      return;
    }
    this.error = null;
    const payload = {
      guestName: this.guestName.trim(),
      guestPhone: this.guestPhone,
      partySize: this.partySize,
      reservedFor: new Date(this.reservedForLocal).toISOString(),
      tableId: this.tableId,
      notes: this.notes,
    };
    this.reservationApi.create(payload).subscribe({
      next: () => {
        this.editing = false;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save reservation'),
    });
  }

  seat(r: Reservation): void {
    this.error = null;
    this.reservationApi.seat(r.id, r.tableId).subscribe({
      next: (order) => this.router.navigate(['/admin/restaurant/orders', order.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to seat reservation'),
    });
  }

  cancel(r: Reservation): void {
    if (!confirm(`Cancel the reservation for ${r.guestName}?`)) return;
    this.error = null;
    this.reservationApi.cancel(r.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel reservation'),
    });
  }

  /** Fourier: "To use the reserved table, you need to enter reservation number." */
  seatByCode(): void {
    const code = this.seatCode.trim();
    if (!code) return;
    this.error = null;
    this.seating = true;
    this.reservationApi.seatByCode(code).subscribe({
      next: (order) => {
        this.seating = false;
        this.router.navigate(['/admin/restaurant/orders', order.id]);
      },
      error: (err) => {
        this.seating = false;
        this.error = err?.error?.message ?? 'Failed to seat that reservation number';
      },
    });
  }
}
