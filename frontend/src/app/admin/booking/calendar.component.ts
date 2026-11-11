import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Appointment, BookingApiService, BookingBlock, BookingSummary, DayView, Option } from '../../core/api/booking-api.service';
import { addDays, today } from '../../core/time-util';

import { T } from '../../core/i18n/t';
interface Col { id: number | null; name: string; }

const ROW_H = 56; // pixels per hour

/** Booking > Calendar: one day, a column per person or per room, appointments placed on a time grid. */
@Component({
  selector: 'app-booking-calendar',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Booking calendar' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" type="button" id="print-day" (click)="printSheet()" [disabled]="!view">{{ 'Print day sheet' | translate }}</button>
          <a class="btn btn-primary" [routerLink]="['/admin/booking/appointments/new']" [queryParams]="{ date: date }">{{ '+ New appointment' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile"><div class="stat-label">{{ 'Today' | translate }}</div><div class="stat-value">{{ s.today }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Next 7 days' | translate }}</div><div class="stat-value">{{ s.upcomingWeek }}</div></div>
        <div class="stat-tile" [class.warn]="s.awaitingPayment > 0"><div class="stat-label">{{ 'Waiting for payment' | translate }}</div><div class="stat-value">{{ s.awaitingPayment }}</div></div>
        <div class="stat-tile" [class.warn]="s.noShowsThisMonth > 0"><div class="stat-label">{{ 'No-shows this month' | translate }}</div><div class="stat-value">{{ s.noShowsThisMonth }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button class="btn btn-outline-secondary" (click)="go(-1)">‹</button>
        <app-date-picker name="d" [(ngModel)]="date" (ngModelChange)="load()"></app-date-picker>
        <button class="btn btn-outline-secondary" (click)="go(1)">›</button>
        <button class="btn btn-outline-secondary" (click)="date = todayIso(); load()">{{ 'Today' | translate }}</button>
        <select class="form-select" style="width:auto;" name="m" [(ngModel)]="mode">
          <option value="staff">{{ 'By person' | translate }}</option>
          <option value="resource">{{ 'By room / equipment' | translate }}</option>
        </select>
        <span class="text-muted" *ngIf="view && !view.workDay">{{ 'Not a working day.' | translate }}</span>
      </div>

      <div *ngIf="view" style="overflow-x:auto;">
        <div style="display:flex; min-width:fit-content;">
          <div style="width:3.2rem; flex:none;">
            <div style="height:2rem;"></div>
            <div *ngFor="let h of hours()" [style.height.px]="rowH" class="text-muted" style="font-size:0.7rem; text-align:right; padding-right:0.3rem;">{{ h }}:00</div>
          </div>
          <div *ngFor="let c of columns()" style="min-width:11rem; flex:1; border-left:1px solid var(--border, #d0d7de);">
            <div style="height:2rem; font-weight:600; font-size:0.85rem; padding:0.3rem 0.4rem; border-bottom:1px solid var(--border, #d0d7de); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;"
                 [title]="offNote(c) || c.name">{{ c.name }}<span *ngIf="offNote(c)" class="text-muted" style="font-weight:400; font-size:0.7rem;"> · {{ offNote(c) }}</span></div>
            <div style="position:relative;" [style.height.px]="hours().length * rowH">
              <div *ngFor="let z of offZones(c)" class="bk-off" [style.top.px]="z.top" [style.height.px]="z.height"
                   style="position:absolute; left:0; right:0; background:rgba(128,128,128,0.16); pointer-events:none;"></div>
              <div *ngFor="let h of hours(); let i = index" style="position:absolute; left:0; right:0; border-top:1px dashed var(--border, #d0d7de); opacity:0.5;" [style.top.px]="i * rowH"></div>
              <div *ngFor="let b of blocksFor(c)" [style.top.px]="top(b.startAt, view.date)" [style.height.px]="height(b.startAt, b.endAt, view.date)"
                   style="position:absolute; left:0; right:0; background:repeating-linear-gradient(45deg, rgba(128,128,128,0.18), rgba(128,128,128,0.18) 6px, transparent 6px, transparent 12px); font-size:0.7rem; padding:0.1rem 0.3rem; overflow:hidden;"
                   [title]="b.reason || 'Blocked'">🚫 {{ b.reason || 'Blocked' }}</div>
              <a *ngFor="let a of appsFor(c)" [routerLink]="['/admin/booking/appointments', a.id]"
                 [style.top.px]="top(a.startAt, view.date)" [style.height.px]="height(a.startAt, a.endAt, view.date)"
                 [style.background]="color(a)" [style.opacity]="a.status === 'COMPLETED' || a.status === 'NO_SHOW' ? 0.55 : 1"
                 [style.border]="a.status === 'PENDING_PAYMENT' ? '2px dashed #e6a700' : '1px solid rgba(0,0,0,0.25)'"
                 style="position:absolute; left:2px; right:2px; border-radius:4px; color:#fff; padding:0.15rem 0.35rem; font-size:0.75rem; overflow:hidden; text-decoration:none;">
                <strong>{{ a.startAt | date: 'HH:mm' }}</strong> {{ a.guestName }}<br>{{ a.serviceName }}
                <span *ngIf="a.status === 'PENDING_PAYMENT'"> {{ '· unpaid' | translate }}</span><span *ngIf="a.status === 'NO_SHOW'"> {{ '· no-show' | translate }}</span>
              </a>
            </div>
          </div>
        </div>
        <p class="text-muted mt-2" *ngIf="!view.appointments.length">{{ 'Nothing booked on this day.' | translate }}</p>
      </div>
    </app-shell>
  `,
})
export class BookingCalendarComponent implements OnInit {
  rowH = ROW_H;
  date = today();
  mode: 'staff' | 'resource' = 'staff';
  view: DayView | null = null;
  s: BookingSummary | null = null;
  error: string | null = null;

  constructor(private api: BookingApiService, private router: Router) {}

  ngOnInit(): void {
    this.load();
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => {} });
  }

  todayIso(): string { return today(); }

  go(days: number): void {
    this.date = addDays(this.date, days);
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.day(this.date).subscribe({ next: (v) => (this.view = v), error: (e) => (this.error = e?.error?.message ?? T('Could not load the calendar')) });
  }

  private minutes(t: string): number {
    const p = t.split(':');
    return Number(p[0]) * 60 + Number(p[1] ?? 0);
  }

  private range(): { from: number; to: number } {
    if (!this.view) return { from: 480, to: 1020 };
    let from = this.minutes(this.view.dayStart);
    let to = this.minutes(this.view.dayEnd);
    for (const a of this.view.appointments) {
      from = Math.min(from, this.minutesOf(a.startAt));
      to = Math.max(to, this.minutesOf(a.endAt));
    }
    return { from: Math.floor(from / 60) * 60, to: Math.ceil(to / 60) * 60 };
  }

  private minutesOf(iso: string): number {
    const t = iso.split('T')[1] ?? '00:00';
    return this.minutes(t);
  }

  hours(): number[] {
    const r = this.range();
    const out: number[] = [];
    for (let m = r.from; m < r.to; m += 60) out.push(m / 60);
    return out;
  }

  top(iso: string, day: string): number {
    const m = iso.slice(0, 10) < day ? 0 : this.minutesOf(iso);
    return Math.max(0, ((m - this.range().from) / 60) * this.rowH);
  }

  height(startIso: string, endIso: string, day: string): number {
    const s = startIso.slice(0, 10) < day ? this.range().from : this.minutesOf(startIso);
    const e = endIso.slice(0, 10) > day ? this.range().to : this.minutesOf(endIso);
    return Math.max(18, ((e - s) / 60) * this.rowH - 1);
  }

  columns(): Col[] {
    if (!this.view) return [];
    const base: Option[] = this.mode === 'staff' ? this.view.staff : this.view.resources;
    const cols: Col[] = base.map((o) => ({ id: o.id, name: o.name }));
    const unassigned = this.view.appointments.some((a) => (this.mode === 'staff' ? a.staffUserId : a.resourceId) == null);
    if (unassigned || !cols.length) cols.push({ id: null, name: this.mode === 'staff' ? 'No one assigned' : 'No room' });
    return cols;
  }

  appsFor(c: Col): Appointment[] {
    if (!this.view) return [];
    return this.view.appointments.filter((a) => (this.mode === 'staff' ? a.staffUserId : a.resourceId) === c.id);
  }

  blocksFor(c: Col): BookingBlock[] {
    if (!this.view) return [];
    return this.view.blocks.filter((b) => b.targetType === 'ALL' || (b.targetType === 'STAFF' && this.mode === 'staff' && b.targetId === c.id)
      || (b.targetType === 'RESOURCE' && this.mode === 'resource' && b.targetId === c.id));
  }

  private dayOf(c: Col) {
    if (!this.view || this.mode !== 'staff' || c.id == null) return null;
    return this.view.staffDays?.find((d) => d.userId === c.id) ?? null;
  }

  /** "On leave: Training", "Day off" or the person's own hours when they differ from the opening hours. */
  offNote(c: Col): string | null {
    const d = this.dayOf(c);
    if (!d || !this.view) return null;
    if (!d.working) return d.note;
    const ownHours = (d.start ?? '').slice(0, 5) !== this.view.dayStart.slice(0, 5) || (d.end ?? '').slice(0, 5) !== this.view.dayEnd.slice(0, 5);
    return ownHours ? (d.start ?? '').slice(0, 5) + '–' + (d.end ?? '').slice(0, 5) : null;
  }

  /** Greyed parts of a person's column: all day when they are off, otherwise before they start and after they finish. */
  offZones(c: Col): { top: number; height: number }[] {
    const d = this.dayOf(c);
    if (!d || !this.view) return [];
    const r = this.range();
    const px = (m: number) => ((m - r.from) / 60) * this.rowH;
    if (!d.working || !d.start || !d.end) return [{ top: 0, height: px(r.to) }];
    const out: { top: number; height: number }[] = [];
    const s = this.minutes(d.start);
    const e = this.minutes(d.end);
    if (s > r.from) out.push({ top: 0, height: px(s) });
    if (e < r.to) out.push({ top: px(e), height: px(r.to) - px(e) });
    return out;
  }

  /** A plain list of the day for the front desk: opens the print dialog. */
  printSheet(): void {
    if (!this.view) return;
    const esc = (t: string | null | undefined) => (t ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const rows = [...this.view.appointments].sort((a, b) => a.startAt.localeCompare(b.startAt)).map((a) =>
      `<tr><td>${esc(a.startAt.slice(11, 16))}–${esc(a.endAt.slice(11, 16))}</td><td>${esc(a.guestName)}<br><small>${esc(a.guestPhone)}</small></td><td>${esc(a.serviceName)}</td>` +
      `<td>${esc(a.staffName)}${a.resourceName ? '<br><small>' + esc(a.resourceName) + '</small>' : ''}</td><td>${esc(this.statusText(a.status))}</td><td>${esc(a.notes)}</td></tr>`).join('');
    const off = (this.view.staffDays ?? []).filter((d) => !d.working).map((d) => esc(this.view!.staff.find((s) => s.id === d.userId)?.name) + ' (' + esc(d.note ?? 'off') + ')').join(', ');
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>Day sheet ${esc(this.view.date)}</title>` +
      `<style>body{font-family:Arial,sans-serif;margin:24px;color:#111}h1{font-size:20px;margin:0 0 4px}table{border-collapse:collapse;width:100%;margin-top:12px}` +
      `th,td{border:1px solid #999;padding:6px 8px;text-align:left;font-size:13px;vertical-align:top}th{background:#eee}small{color:#555}</style></head><body>` +
      `<h1>Appointments for ${esc(new Date(this.view.date + 'T00:00:00').toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }))}</h1>` +
      (off ? `<div>Away: ${off}</div>` : '') +
      (rows ? `<table><thead><tr><th>Time</th><th>Guest</th><th>Service</th><th>With</th><th>Status</th><th>Notes</th></tr></thead><tbody>${rows}</tbody></table>` : '<p>Nothing booked on this day.</p>') +
      `</body></html>`;
    const w = window.open('', '_blank');
    if (!w) { this.error = T('Allow pop-ups for this site to print the day sheet.'); return; }
    w.document.write(html);
    w.document.close();
    w.focus();
    setTimeout(() => w.print(), 300);
  }

  private statusText(s: string): string {
    return s === 'PENDING_PAYMENT' ? 'Waiting for payment' : s === 'NO_SHOW' ? 'No-show' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  color(a: Appointment): string {
    if (a.status === 'PENDING_PAYMENT') return '#8a6d1d';
    if (a.status === 'NO_SHOW') return '#7a2e2e';
    return a.serviceColor || '#2f6fb3';
  }
}
