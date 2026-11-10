import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ReservationApiService } from '../core/api/reservation-api.service';
import { DiningTableApiService } from '../core/api/dining-table-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { DiningTable, PaymentMethodOption, Reservation, ReservationReminder, ReservationSlot } from '../core/models';
import { AuthService } from '../core/auth.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

/**
 * Admin/staff > Restaurant > Reservations (Fourier: "Reservations"). List + a booking form with a
 * time picker that shows what is still free, deposits, no-shows and reminders.
 *
 * <p>Fourier's follow-up — "To use the reserved table, you need to enter reservation number. But
 * the reservation can be cancelled anytime by admin" — adds the "Seat by reservation number" card
 * above the list and restricts the Cancel button to managers (the backend enforces this too).
 */
@Component({
  selector: 'app-reservation-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Reservations' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/waitlist">{{ 'Waiting list' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">{{ '← Floor plan' | translate }}</a>
          <button class="btn btn-primary rv-new" (click)="startNew()">{{ '+ New reservation' | translate }}</button>
        </div>
      </div>

      <div class="card mb-3 rv-due" style="padding:0.75rem 1rem;" *ngIf="due.length > 0">
        <strong>{{ '{p0} reminder{p1} due' | translate: { p0: (due.length), p1: (due.length === 1 ? '' : 's') } }}</strong>
        <span class="text-muted"> {{ '— parties coming up soon who have not been reminded:' | translate }} </span>
        <span *ngFor="let d of due; let last = last">{{ d.guestName }} ({{ d.reservedFor | date: 'EEE HH:mm' }})<span *ngIf="!last">, </span></span>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Seat by reservation number' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="min-width:200px;">
            <label class="form-label">{{ 'Reservation number' | translate }}</label>
            <input class="form-control" [(ngModel)]="seatCode" name="seatCode" [placeholder]="'RES-123456' | translate" />
          </div>
          <button class="btn btn-primary" [disabled]="!seatCode.trim() || seating" (click)="seatByCode()">
            {{ seating ? ('Seating…' | translate) : ('Seat' | translate) }}
          </button>
        </div>
      </div>

      <div class="card mb-3 rv-form" style="padding:1rem;" *ngIf="editing">
        <h4 style="font-size:1rem;" class="mb-2">{{ editingId ? ('Change reservation' | translate) : ('New reservation' | translate) }}</h4>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:160px;">
            <label class="form-label">{{ 'Guest name' | translate }}</label>
            <input class="form-control rv-name" [(ngModel)]="guestName" name="guestName" />
          </div>
          <div class="form-group" style="flex:1; min-width:140px;">
            <label class="form-label">{{ 'Phone' | translate }}</label>
            <input class="form-control rv-phone" [(ngModel)]="guestPhone" name="guestPhone" />
          </div>
          <div class="form-group" style="flex:1; min-width:180px;">
            <label class="form-label">{{ 'E-mail (for reminders)' | translate }}</label>
            <input class="form-control rv-email" type="email" [(ngModel)]="guestEmail" name="guestEmail" />
          </div>
          <div class="form-group" style="width:100px;">
            <label class="form-label">{{ 'Party size' | translate }}</label>
            <input class="form-control rv-party" type="number" min="1" [(ngModel)]="partySize" name="partySize" (ngModelChange)="refreshAvailability()" />
          </div>
        </div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:200px;">
            <label class="form-label">Date & time</label>
            <input class="form-control rv-when" type="datetime-local" [(ngModel)]="reservedForLocal" name="reservedFor" (ngModelChange)="refreshAvailability()" />
          </div>
          <div class="form-group" style="width:120px;">
            <label class="form-label">{{ 'Minutes at table' | translate }}</label>
            <input class="form-control rv-duration" type="number" min="15" max="480" step="15" [(ngModel)]="durationMinutes" name="duration" [placeholder]="'usual' | translate" (ngModelChange)="refreshAvailability()" />
          </div>
          <div class="form-group" style="min-width:200px;">
            <label class="form-label">{{ 'Table (optional)' | translate }}</label>
            <select class="form-select rv-table" [(ngModel)]="tableId" name="tableId">
              <option [ngValue]="null">{{ '— Unassigned —' | translate }}</option>
              <option *ngFor="let t of tableChoices" [ngValue]="t.id">{{ '{p0} (seats {p1})' | translate: { p0: (t.name), p1: (t.capacity) } }}</option>
            </select>
            <div class="text-muted rv-free-hint" style="font-size:0.8rem;" *ngIf="reservedForLocal">{{ freeHint }}</div>
          </div>
        </div>
        <div class="mb-2" *ngIf="slots.length > 0">
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Free times that day (tap to use)' | translate }}</div>
          <div class="flex gap-1" style="flex-wrap:wrap;">
            <button type="button" class="btn btn-sm rv-slot" *ngFor="let s of slots"
              [class.btn-primary]="s.label === pickedLabel" [class.btn-outline-secondary]="s.label !== pickedLabel"
              [disabled]="s.tablesFree === 0" (click)="pickSlot(s)">{{ s.label }} <span style="font-size:0.7rem;">({{ s.tablesFree }})</span></button>
          </div>
        </div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="width:160px;" *ngIf="!depositPaid">
            <label class="form-label">{{ 'Deposit (blank = usual)' | translate }}</label>
            <input class="form-control rv-deposit" type="number" min="0" step="any" [(ngModel)]="depositAmount" name="depositAmount" />
          </div>
          <div class="form-group" style="min-width:180px;" *ngIf="!depositPaid && !editingId">
            <label class="form-label">{{ 'Take the deposit now with' | translate }}</label>
            <select class="form-select rv-dep-method" [(ngModel)]="depositMethod" name="depositMethod">
              <option [ngValue]="null">{{ '— Not yet —' | translate }}</option>
              <option *ngFor="let m of depositMethods" [ngValue]="m.code">{{ m.name }}</option>
            </select>
          </div>
          <div class="text-muted" *ngIf="depositPaid">{{ 'The deposit has been taken and stays as it is.' | translate }}</div>
        </div>
        <div class="form-group mb-2">
          <label class="form-label">{{ 'Notes' | translate }}</label>
          <input class="form-control rv-notes" [(ngModel)]="notes" name="notes" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary rv-save" [disabled]="saving" (click)="save()">{{ saving ? ('Saving…' | translate) : ('Save' | translate) }}</button>
          <button class="btn btn-link" (click)="editing = false">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <app-list-filter exportPath="/restaurant/reservations/export" [placeholder]="'Search guest, phone, e-mail, number, table' | translate" [statuses]="statuses" statusAll="All statuses" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-2" *ngIf="isAdmin">
        <button class="btn btn-sm btn-outline-secondary rv-mark-all" (click)="markAllNoShows()">{{ 'Mark overdue bookings as no-shows' | translate }}</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Reservation #' | translate }}</th>
            <th>{{ 'Guest' | translate }}</th>
            <th>{{ 'Party' | translate }}</th>
            <th>{{ 'When' | translate }}</th>
            <th>{{ 'Table' | translate }}</th>
            <th>{{ 'Deposit' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let r of list.rows">
            <tr class="rv-row">
              <td>{{ r.confirmationCode || '—' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.source === 'ONLINE'">{{ 'booked online' | translate }}</div></td>
              <td>
                {{ r.guestName }}<span class="text-muted" *ngIf="r.guestPhone"> — {{ r.guestPhone }}</span>
                <div class="rv-history" style="font-size:0.75rem; color:#b45309;" *ngIf="(r.guestNoShows ?? 0) > 0">{{ '⚠ {p0} earlier no-show{p1}' | translate: { p0: (r.guestNoShows), p1: (r.guestNoShows === 1 ? '' : 's') } }}</div>
                <div class="text-muted rv-visits" style="font-size:0.75rem;" *ngIf="(r.guestVisits ?? 0) > 0">{{ '{p0} earlier visit{p1}' | translate: { p0: (r.guestVisits), p1: (r.guestVisits === 1 ? '' : 's') } }}</div>
              </td>
              <td>{{ r.partySize }}</td>
              <td>{{ r.reservedFor | date: 'EEE d MMM, HH:mm' }}<div class="text-muted" style="font-size:0.75rem;">{{ '{p0} min' | translate: { p0: (r.durationMinutes) } }}<span *ngIf="r.remindedAt"> {{ '· reminded' | translate }}</span></div></td>
              <td>{{ r.tableName || '—' }}</td>
              <td class="rv-dep-cell">
                <span *ngIf="(r.depositAmount ?? 0) > 0 || r.depositStatus === 'REFUNDED' || r.depositStatus === 'FORFEITED'">{{ r.depositAmount | number: '1.0-2' }} <span class="text-muted">{{ depositLabel(r.depositStatus) }}</span></span>
                <span class="text-muted" *ngIf="!((r.depositAmount ?? 0) > 0)">—</span>
              </td>
              <td>{{ prettyStatus(r.status) }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.cancelReason">{{ r.cancelReason }}</div></td>
              <td class="flex gap-2" style="flex-wrap:wrap;">
                <ng-container *ngIf="r.status === 'BOOKED'">
                  <button class="btn btn-sm btn-primary rv-seat" (click)="seat(r)">{{ 'Seat' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary rv-edit" (click)="startEdit(r)">{{ 'Change' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary rv-dep" *ngIf="r.depositStatus === 'REQUIRED'" (click)="openPanel(r, 'deposit')">{{ 'Take deposit' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary rv-remind" (click)="openPanel(r, 'remind')">{{ 'Remind' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary rv-noshow" (click)="noShow(r)">{{ 'No-show' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger rv-cancel" *ngIf="isAdmin" (click)="openPanel(r, 'cancel')">{{ 'Cancel' | translate }}</button>
                </ng-container>
              </td>
            </tr>
            <tr class="rv-panel-row" *ngIf="panelId === r.id">
              <td colspan="8">
                <div class="card rv-panel" style="padding:0.75rem;">
                  <ng-container *ngIf="panelMode === 'deposit'">
                    <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                      <div class="form-group"><label class="form-label">{{ 'Deposit of {p0} paid with' | translate: { p0: (r.depositAmount | number: '1.0-2') } }}</label>
                        <select class="form-select rv-p-method" [(ngModel)]="panelMethod" name="panelMethod">
                          <option *ngFor="let m of depositMethods" [value]="m.code">{{ m.name }}</option>
                        </select></div>
                      <button class="btn btn-primary rv-p-go" (click)="takeDeposit(r)">{{ 'Take deposit' | translate }}</button>
                      <button class="btn btn-link" (click)="panelId = null">{{ 'Close' | translate }}</button>
                    </div>
                  </ng-container>
                  <ng-container *ngIf="panelMode === 'cancel'">
                    <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                      <div class="form-group" style="min-width:240px;"><label class="form-label">{{ 'Reason (optional)' | translate }}</label>
                        <input class="form-control rv-c-reason" [(ngModel)]="cancelReason" name="cancelReason" /></div>
                      <div class="form-group" *ngIf="r.depositStatus === 'HELD'"><label class="form-label">{{ 'The {p0} deposit' | translate: { p0: (r.depositAmount | number: '1.0-2') } }}</label>
                        <select class="form-select rv-c-dep" [(ngModel)]="cancelDeposit" name="cancelDeposit">
                          <option value="">{{ 'By the refund window' | translate }}</option>
                          <option value="REFUND">{{ 'Refund it' | translate }}</option>
                          <option value="FORFEIT">{{ 'Keep it' | translate }}</option>
                        </select></div>
                      <button class="btn btn-outline-danger rv-c-go" (click)="cancel(r)">{{ 'Cancel reservation' | translate }}</button>
                      <button class="btn btn-link" (click)="panelId = null">{{ 'Keep it' | translate }}</button>
                    </div>
                  </ng-container>
                  <ng-container *ngIf="panelMode === 'remind'">
                    <div class="rv-r-text" style="white-space:pre-wrap;">{{ reminder?.text }}</div>
                    <div class="text-muted rv-r-note" *ngIf="reminder?.note">{{ reminder?.note }}</div>
                    <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
                      <button class="btn btn-primary rv-r-send" *ngIf="r.guestEmail" (click)="sendReminder(r)">{{ 'E-mail it to {p0}' | translate: { p0: (r.guestEmail) } }}</button>
                      <button class="btn btn-outline-secondary rv-r-sent" (click)="markReminded(r)">{{ 'I sent it myself' | translate }}</button>
                      <button class="btn btn-link" (click)="panelId = null">{{ 'Close' | translate }}</button>
                    </div>
                  </ng-container>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No reservations match these filters.' | translate) : ('No reservations yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ReservationListComponent implements OnInit {
  statuses = [{ value: 'BOOKED', label: 'Booked' }, { value: 'SEATED', label: 'Seated' }, { value: 'CANCELLED', label: 'Cancelled' }, { value: 'NO_SHOW', label: 'No-show' }];
  list = new ListState<Reservation>((f, p, sz) => this.reservationApi.page(f, p, sz), 'Failed to load reservations');
  tables: DiningTable[] = [];
  tableChoices: DiningTable[] = [];
  depositMethods: PaymentMethodOption[] = [];
  due: Reservation[] = [];
  slots: ReservationSlot[] = [];
  freeHint = '';
  pickedLabel = '';
  error: string | null = null;
  notice: string | null = null;

  editing = false;
  editingId: number | null = null;
  saving = false;
  depositPaid = false;
  guestName = '';
  guestPhone: string | null = null;
  guestEmail: string | null = null;
  partySize = 2;
  reservedForLocal = '';
  durationMinutes: number | null = null;
  tableId: number | null = null;
  notes: string | null = null;
  depositAmount: number | null = null;
  depositMethod: string | null = null;

  seatCode = '';
  seating = false;

  panelId: number | null = null;
  panelMode: 'deposit' | 'cancel' | 'remind' | null = null;
  panelMethod = '';
  cancelReason = '';
  cancelDeposit: '' | 'REFUND' | 'FORFEIT' = '';
  reminder: ReservationReminder | null = null;

  constructor(
    private reservationApi: ReservationApiService,
    private tableApi: DiningTableApiService,
    private paymentApi: PaymentMethodApiService,
    private auth: AuthService,
    private router: Router
  ) {}

  get isAdmin(): boolean {
    return this.auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    this.load();
    this.tableApi.listAll().subscribe({
      next: (t) => {
        this.tables = t.filter((x) => x.active && x.status !== 'OUT_OF_SERVICE');
        this.tableChoices = this.tables;
      },
      error: () => {},
    });
    this.paymentApi.listActive().subscribe({
      next: (m) => {
        this.depositMethods = m.filter((x) => !x.credit && !x.roomCharge);
        this.panelMethod = this.depositMethods[0]?.code ?? '';
      },
      error: () => {},
    });
  }

  load(): void {
    this.list.load();
    this.reservationApi.remindersDue().subscribe({ next: (d) => (this.due = d), error: () => (this.due = []) });
  }

  prettyStatus(s: string): string {
    return s === 'NO_SHOW' ? 'No-show' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  depositLabel(s: string | undefined): string {
    switch (s) {
      case 'REQUIRED': return '(to pay)';
      case 'HELD': return '(paid)';
      case 'APPLIED': return '(on the bill)';
      case 'REFUNDED': return '(refunded)';
      case 'FORFEITED': return '(kept)';
      default: return '';
    }
  }

  startNew(): void {
    this.editing = true;
    this.editingId = null;
    this.depositPaid = false;
    this.guestName = '';
    this.guestPhone = null;
    this.guestEmail = null;
    this.partySize = 2;
    this.reservedForLocal = '';
    this.durationMinutes = null;
    this.tableId = null;
    this.notes = null;
    this.depositAmount = null;
    this.depositMethod = null;
    this.slots = [];
    this.freeHint = '';
    this.tableChoices = this.tables;
  }

  startEdit(r: Reservation): void {
    this.startNew();
    this.editingId = r.id;
    this.guestName = r.guestName;
    this.guestPhone = r.guestPhone;
    this.guestEmail = r.guestEmail ?? null;
    this.partySize = r.partySize;
    this.reservedForLocal = this.toLocalInput(r.reservedFor);
    this.durationMinutes = r.durationMinutes ?? null;
    this.tableId = r.tableId;
    this.notes = r.notes;
    this.depositPaid = r.depositStatus === 'HELD';
    this.depositAmount = r.depositStatus === 'REQUIRED' ? (r.depositAmount ?? null) : null;
    this.refreshAvailability();
  }

  private toLocalInput(iso: string): string {
    const d = new Date(iso);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  /** Looks up which tables are free at the chosen time (and the day's free times), so the staff member never has to guess. */
  refreshAvailability(): void {
    if (!this.reservedForLocal) return;
    const when = new Date(this.reservedForLocal);
    if (isNaN(when.getTime())) return;
    const party = this.partySize > 0 ? this.partySize : 1;
    this.pickedLabel = this.reservedForLocal.slice(11, 16);
    this.reservationApi.tablesAt(when.toISOString(), party).subscribe({
      next: (free) => {
        const ids = new Set(free.map((t) => t.id));
        this.tableChoices = this.tables.filter((t) => ids.has(t.id) || t.id === this.tableId);
        this.freeHint = free.length === 0 ? 'No table is free at that time for this party.' : `${free.length} table${free.length === 1 ? '' : 's'} free at that time.`;
      },
      error: () => {},
    });
    this.reservationApi.slots(this.reservedForLocal.slice(0, 10), party).subscribe({ next: (s) => (this.slots = s), error: () => (this.slots = []) });
  }

  pickSlot(s: ReservationSlot): void {
    const day = this.reservedForLocal ? this.reservedForLocal.slice(0, 10) : '';
    if (!day) return;
    this.reservedForLocal = this.toLocalInput(s.time);
    this.refreshAvailability();
  }

  save(): void {
    if (!this.guestName.trim() || !this.reservedForLocal) {
      this.error = 'Guest name and date/time are required';
      return;
    }
    this.error = null;
    this.notice = null;
    const payload = {
      guestName: this.guestName.trim(),
      guestPhone: this.guestPhone,
      guestEmail: this.guestEmail && this.guestEmail.trim() ? this.guestEmail.trim() : null,
      partySize: this.partySize,
      reservedFor: new Date(this.reservedForLocal).toISOString(),
      durationMinutes: this.durationMinutes || null,
      tableId: this.tableId,
      notes: this.notes,
      depositAmount: this.depositAmount === null || (this.depositAmount as unknown) === '' ? null : this.depositAmount,
      depositMethod: this.depositMethod,
    };
    this.saving = true;
    const call = this.editingId ? this.reservationApi.update(this.editingId, payload) : this.reservationApi.create(payload);
    call.subscribe({
      next: () => {
        this.saving = false;
        this.editing = false;
        this.load();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to save reservation';
      },
    });
  }

  seat(r: Reservation): void {
    this.error = null;
    this.reservationApi.seat(r.id, r.tableId).subscribe({
      next: (order) => this.router.navigate(['/admin/restaurant/orders', order.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to seat reservation'),
    });
  }

  openPanel(r: Reservation, mode: 'deposit' | 'cancel' | 'remind'): void {
    this.error = null;
    this.notice = null;
    this.panelId = r.id;
    this.panelMode = mode;
    this.cancelReason = '';
    this.cancelDeposit = '';
    this.reminder = null;
    if (mode === 'remind') {
      this.reservationApi.reminder(r.id, false).subscribe({ next: (x) => (this.reminder = x), error: (err) => (this.error = err?.error?.message ?? 'Could not prepare the reminder') });
    }
  }

  takeDeposit(r: Reservation): void {
    this.reservationApi.takeDeposit(r.id, this.panelMethod).subscribe({
      next: () => {
        this.panelId = null;
        this.notice = 'Deposit taken.';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not take the deposit'),
    });
  }

  cancel(r: Reservation): void {
    this.error = null;
    this.reservationApi.cancel(r.id, this.cancelReason.trim(), this.cancelDeposit).subscribe({
      next: (x) => {
        this.panelId = null;
        this.notice = x.depositStatus === 'REFUNDED' ? 'Cancelled. The deposit was refunded.' : x.depositStatus === 'FORFEITED' ? 'Cancelled. The deposit was kept.' : 'Cancelled.';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel reservation'),
    });
  }

  noShow(r: Reservation): void {
    this.error = null;
    this.notice = null;
    this.reservationApi.noShow(r.id).subscribe({
      next: (x) => {
        this.notice = x.depositStatus === 'FORFEITED' ? `${r.guestName} did not come. The deposit was kept.` : `${r.guestName} marked as a no-show.`;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not mark a no-show'),
    });
  }

  markAllNoShows(): void {
    this.error = null;
    this.reservationApi.markNoShows().subscribe({
      next: (x) => {
        this.notice = x.marked === 0 ? 'No overdue bookings.' : `${x.marked} overdue booking${x.marked === 1 ? '' : 's'} marked as no-show.`;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not mark the no-shows'),
    });
  }

  sendReminder(r: Reservation): void {
    this.reservationApi.reminder(r.id, true).subscribe({
      next: (x) => {
        this.reminder = x;
        if (x.emailed) {
          this.panelId = null;
          this.notice = x.note;
          this.load();
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not send the reminder'),
    });
  }

  markReminded(r: Reservation): void {
    this.reservationApi.markReminded(r.id).subscribe({
      next: () => {
        this.panelId = null;
        this.notice = 'Marked as reminded.';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not mark the reminder'),
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
