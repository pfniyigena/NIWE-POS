import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BookingApiService, BookingSettings, DayHours, Schedule } from '../../core/api/booking-api.service';
import { today } from '../../core/time-util';

interface Row { key: string; label: string; open: boolean; on: boolean; start: string; end: string; }
interface Card { s: Schedule; custom: boolean; rows: Row[]; from: string; to: string; reason: string; msg: string | null; err: string | null; }

const DAYS = [
  { key: 'MON', label: 'Monday' }, { key: 'TUE', label: 'Tuesday' }, { key: 'WED', label: 'Wednesday' }, { key: 'THU', label: 'Thursday' },
  { key: 'FRI', label: 'Friday' }, { key: 'SAT', label: 'Saturday' }, { key: 'SUN', label: 'Sunday' },
];

/** Booking > Working hours: each person's own hours and days off (otherwise they follow the opening hours) and their leave. */
@Component({
  selector: 'app-booking-staff-schedules',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-2">{{ 'Working hours and leave' | translate }}</h3>
      <p class="text-muted">{{ 'By default everyone who performs a service can be booked during the business opening hours' | translate }}
        <span *ngIf="settings">({{ settings.dayStart.slice(0, 5) }}–{{ settings.dayEnd.slice(0, 5) }})</span>{{ '. Give a person their own hours when they work shorter days or fewer days, and add leave for days they are away. Own hours must fall inside the opening hours.' | translate }}</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:1rem;" *ngFor="let c of cards" [attr.data-user]="c.s.userId">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <strong>{{ c.s.userName }}</strong>
          <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" class="bk-custom" [name]="'cu' + c.s.userId" [(ngModel)]="c.custom" (ngModelChange)="onCustom(c)" /> {{ 'Own working hours' | translate }}</label>
        </div>
        <div class="alert alert-danger mt-2" *ngIf="c.err">{{ c.err }}</div>
        <div class="alert alert-success mt-2" *ngIf="c.msg">{{ c.msg }}</div>

        <table class="table-sm mt-2" *ngIf="c.custom" style="max-width:34rem;">
          <thead><tr><th>{{ 'Day' | translate }}</th><th>{{ 'Works' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'Until' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let r of c.rows" [style.opacity]="r.open ? 1 : 0.5">
            <td>{{ r.label }}<span class="text-muted" *ngIf="!r.open" style="font-size:0.75rem;"> {{ '(business closed)' | translate }}</span></td>
            <td><input type="checkbox" [disabled]="!r.open" [name]="'on' + c.s.userId + r.key" [(ngModel)]="r.on" /></td>
            <td><input class="form-control" type="time" style="width:9.5rem;" [disabled]="!r.on" [name]="'st' + c.s.userId + r.key" [(ngModel)]="r.start" /></td>
            <td><input class="form-control" type="time" style="width:9.5rem;" [disabled]="!r.on" [name]="'en' + c.s.userId + r.key" [(ngModel)]="r.end" /></td>
          </tr></tbody>
        </table>
        <div class="mt-2" *ngIf="c.custom || c.s.custom"><button class="btn btn-sm btn-primary bk-save-hours" type="button" (click)="saveHours(c)">{{ 'Save hours' | translate }}</button></div>

        <hr />
        <strong style="font-size:0.9rem;">{{ 'Leave' | translate }}</strong>
        <table class="table-sm mt-1" *ngIf="c.s.leave.length" style="max-width:34rem;">
          <tbody><tr *ngFor="let l of c.s.leave">
            <td>{{ l.fromDate }}<span *ngIf="l.toDate !== l.fromDate"> {{ 'to {p0}' | translate: { p0: (l.toDate) } }}</span></td><td>{{ l.reason || '—' }}</td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="removeLeave(c, l.id)">{{ 'Remove' | translate }}</button></td>
          </tr></tbody>
        </table>
        <div class="text-muted" *ngIf="!c.s.leave.length" style="font-size:0.85rem;">{{ 'No leave planned.' | translate }}</div>
        <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
          <app-date-picker [name]="'lf' + c.s.userId" [(ngModel)]="c.from"></app-date-picker>
          <span>{{ 'to' | translate }}</span>
          <app-date-picker [name]="'lt' + c.s.userId" [(ngModel)]="c.to"></app-date-picker>
          <input class="form-control" style="width:14rem;" [placeholder]="'Reason (optional)' | translate" [name]="'lr' + c.s.userId" [(ngModel)]="c.reason" />
          <button class="btn btn-outline-primary bk-add-leave" type="button" [disabled]="!c.from" (click)="addLeave(c)">{{ 'Add leave' | translate }}</button>
        </div>
      </div>
      <p class="text-muted" *ngIf="loaded && !cards.length">{{ 'Nobody performs a service yet. Choose who performs each service under Services.' | translate }}</p>
    </app-shell>
  `,
})
export class BookingStaffSchedulesComponent implements OnInit {
  cards: Card[] = [];
  settings: BookingSettings | null = null;
  error: string | null = null;
  loaded = false;

  constructor(private api: BookingApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => { this.settings = s; this.load(); }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
  }

  private openDays(): Set<string> {
    return new Set((this.settings?.workDays ?? '').split(',').map((x) => x.trim().toUpperCase().slice(0, 3)).filter((x) => x));
  }

  private rowsFor(s: Schedule): Row[] {
    const open = this.openDays();
    const ds = (this.settings?.dayStart ?? '08:00').slice(0, 5);
    const de = (this.settings?.dayEnd ?? '17:00').slice(0, 5);
    return DAYS.map((d) => {
      const own = s.days.find((x) => x.day === d.key);
      return { key: d.key, label: d.label, open: open.has(d.key), on: s.custom ? !!own : open.has(d.key), start: own?.start ?? ds, end: own?.end ?? de };
    });
  }

  private card(s: Schedule, keep?: Card): Card {
    return { s, custom: s.custom, rows: this.rowsFor(s), from: keep?.from ?? '', to: keep?.to ?? '', reason: keep?.reason ?? '', msg: keep?.msg ?? null, err: null };
  }

  load(): void {
    this.api.schedules().subscribe({
      next: (l) => { this.cards = l.map((s) => this.card(s)); this.loaded = true; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the schedules'),
    });
  }

  private replace(c: Card, s: Schedule, msg: string | null): void {
    const i = this.cards.indexOf(c);
    const n = this.card(s, c);
    n.msg = msg;
    if (i >= 0) this.cards[i] = n;
  }

  onCustom(c: Card): void {
    c.err = null;
    c.msg = null;
    if (!c.custom && c.s.custom) {
      // switching back to the opening hours is saved straight away
      this.api.saveSchedule(c.s.userId, false, []).subscribe({
        next: (s) => this.replace(c, s, 'Now follows the business opening hours.'),
        error: (e) => { c.err = e?.error?.message ?? 'Could not save'; c.custom = true; },
      });
    }
  }

  saveHours(c: Card): void {
    c.err = null;
    c.msg = null;
    const days: DayHours[] = c.rows.filter((r) => r.open && r.on).map((r) => ({ day: r.key, start: r.start, end: r.end }));
    this.api.saveSchedule(c.s.userId, true, days).subscribe({
      next: (s) => this.replace(c, s, 'Working hours saved.'),
      error: (e) => (c.err = e?.error?.message ?? 'Could not save'),
    });
  }

  addLeave(c: Card): void {
    c.err = null;
    c.msg = null;
    this.api.addLeave(c.s.userId, c.from, c.to || c.from, c.reason.trim() || null).subscribe({
      next: (r) => {
        const s: Schedule = { ...c.s, leave: [...c.s.leave, r.leave].sort((a, b) => a.fromDate.localeCompare(b.fromDate)) };
        const warn = r.appointmentsInPeriod ? ` ${r.appointmentsInPeriod} appointment${r.appointmentsInPeriod === 1 ? ' is' : 's are'} already booked in this period; move or cancel ${r.appointmentsInPeriod === 1 ? 'it' : 'them'} from the calendar.` : '';
        const i = this.cards.indexOf(c);
        const n = this.card(s, { ...c, from: '', to: '', reason: '' } as Card);
        n.custom = c.custom;
        n.rows = c.rows;
        n.msg = 'Leave added.' + warn;
        if (i >= 0) this.cards[i] = n;
      },
      error: (e) => (c.err = e?.error?.message ?? 'Could not add the leave'),
    });
  }

  removeLeave(c: Card, id: number): void {
    c.err = null;
    this.api.deleteLeave(id).subscribe({
      next: () => {
        c.s = { ...c.s, leave: c.s.leave.filter((l) => l.id !== id) };
        c.msg = 'Leave removed.';
      },
      error: (e) => (c.err = e?.error?.message ?? 'Could not remove the leave'),
    });
  }
}
