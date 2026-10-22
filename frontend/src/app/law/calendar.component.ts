import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { API_BASE } from '../core/constants';
import { downloadBlob } from '../core/download-file';
import { CalendarEntry, DEADLINE_TYPE_LABEL, DeadlineType, LawApiService } from '../core/api/law-api.service';

interface Cell { date: string; day: number; inMonth: boolean; today: boolean; entries: CalendarEntry[]; shown: CalendarEntry[]; more: number; }

const pad = (n: number): string => (n < 10 ? '0' : '') + n;
const ymd = (d: Date): string => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
const parse = (s: string): Date => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (d: Date, n: number): Date => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
/** Monday of the week the date falls in. */
const monday = (d: Date): Date => addDays(d, -((d.getDay() + 6) % 7));
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** Law Firm > Calendar: deadlines and tasks by month or week, with a download for Outlook, Google Calendar or a phone. Open to any login; the server shows only what the person may see. */
@Component({
  selector: 'app-law-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  styles: [`
    .cal-grid { display:grid; grid-template-columns:repeat(7, minmax(0, 1fr)); gap:1px; background:#d6d9de; border:1px solid #d6d9de; }
    .cal-head { background:#f3f4f6; padding:0.3rem; text-align:center; font-size:0.8rem; font-weight:600; }
    .cal-cell { background:#fff; min-height:5.5rem; padding:0.2rem 0.25rem; cursor:pointer; overflow:hidden; }
    .cal-cell.out { background:#f9fafb; color:#9ca3af; }
    .cal-cell.sel { outline:2px solid #2563eb; outline-offset:-2px; }
    .cal-cell.today .cal-day { background:#2563eb; color:#fff; border-radius:50%; padding:0 0.35rem; }
    .cal-day { font-size:0.8rem; font-weight:600; }
    .cal-chip { display:block; font-size:0.72rem; line-height:1.25; padding:0 0.25rem; margin-top:2px; border-radius:3px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .cal-count { display:none; font-size:0.7rem; margin-top:2px; }
    .k-limit { background:#fee2e2; color:#991b1b; } .k-court { background:#dbeafe; color:#1e40af; } .k-filing { background:#ffedd5; color:#9a3412; }
    .k-meeting { background:#ede9fe; color:#5b21b6; } .k-task { background:#e5e7eb; color:#374151; } .k-other { background:#f3f4f6; color:#374151; }
    .done { text-decoration:line-through; opacity:0.6; }
    .week-day { border:1px solid #d6d9de; border-radius:6px; padding:0.5rem; margin-bottom:0.5rem; background:#fff; }
    .week-day.today { border-color:#2563eb; }
    .entry-row { display:flex; gap:0.5rem; align-items:baseline; padding:0.25rem 0; border-top:1px solid #eef0f3; flex-wrap:wrap; }
    .entry-row:first-of-type { border-top:0; }
    @media (max-width: 640px) {
      .cal-cell { min-height:3rem; }
      .cal-chip { display:none; }
      .cal-count { display:block; }
    }
  `],
  template: `
    <app-shell [wide]="true">
      <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:0.5rem;" class="mb-2">
        <h3 class="mb-0">Calendar</h3>
        <div style="display:flex; gap:0.4rem; align-items:center; flex-wrap:wrap;">
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="download()" [disabled]="busyIcs">Download for Outlook / phone (.ics)</button>
          <span style="color:#b91c1c; font-size:0.8rem;" *ngIf="icsError">{{ icsError }}</span>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:0.75rem;">
        <div style="display:flex; gap:0.75rem; align-items:center; flex-wrap:wrap;">
          <div style="display:flex; gap:0.3rem; align-items:center;">
            <button class="btn btn-sm btn-outline-secondary cal-prev" type="button" (click)="step(-1)" aria-label="Earlier">‹</button>
            <button class="btn btn-sm btn-outline-secondary cal-today" type="button" (click)="goToday()">Today</button>
            <button class="btn btn-sm btn-outline-secondary cal-next" type="button" (click)="step(1)" aria-label="Later">›</button>
          </div>
          <strong class="cal-title" style="min-width:10rem;">{{ title }}</strong>
          <div style="display:flex; gap:0.3rem;">
            <button class="btn btn-sm cal-view-month" [class.btn-primary]="view === 'month'" [class.btn-outline-secondary]="view !== 'month'" type="button" (click)="setView('month')">Month</button>
            <button class="btn btn-sm cal-view-week" [class.btn-primary]="view === 'week'" [class.btn-outline-secondary]="view !== 'week'" type="button" (click)="setView('week')">Week</button>
          </div>
          <select class="form-select cal-who" style="width:auto;" *ngIf="canSeeAll" [ngModel]="mine ? 'mine' : 'all'" (ngModelChange)="setWho($event)" name="who">
            <option value="mine">Mine</option><option value="all">Everyone</option>
          </select>
          <label style="display:flex; gap:0.3rem; align-items:center; margin:0;"><input type="checkbox" class="cal-tasks" [ngModel]="tasks" (ngModelChange)="tasks = $event; load()" name="tasks" /> Tasks</label>
          <label style="display:flex; gap:0.3rem; align-items:center; margin:0;"><input type="checkbox" class="cal-done" [ngModel]="includeDone" (ngModelChange)="includeDone = $event; load()" name="done" /> Finished</label>
        </div>
      </div>

      <ng-container *ngIf="view === 'month'">
        <div class="cal-grid" *ngIf="rows.length">
          <div class="cal-head" *ngFor="let h of heads">{{ h }}</div>
          <ng-container *ngFor="let r of rows">
            <div *ngFor="let c of r" class="cal-cell" [class.out]="!c.inMonth" [class.today]="c.today" [class.sel]="selected && selected.date === c.date" (click)="select(c)" [attr.data-date]="c.date">
              <span class="cal-day">{{ c.day }}</span>
              <span *ngFor="let e of c.shown" class="cal-chip" [ngClass]="cls(e)" [class.done]="e.done" [title]="e.title">{{ label(e) }}</span>
              <span class="text-muted cal-chip" *ngIf="c.more > 0">+{{ c.more }} more</span>
              <span class="cal-count" *ngIf="c.entries.length" [style.color]="c.entries.length ? '#991b1b' : ''">● {{ c.entries.length }}</span>
            </div>
          </ng-container>
        </div>
      </ng-container>

      <ng-container *ngIf="view === 'week'">
        <div *ngFor="let c of weekDays" class="week-day" [class.today]="c.today" [attr.data-date]="c.date">
          <strong>{{ dayName(c.date) }}</strong>
          <ng-container *ngTemplateOutlet="entryList; context: { $implicit: c.entries }"></ng-container>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="!c.entries.length">Nothing.</div>
        </div>
      </ng-container>

      <div class="card mt-3 cal-day-panel" style="padding:0.75rem;" *ngIf="view === 'month' && selected">
        <strong>{{ dayName(selected.date) }}</strong>
        <ng-container *ngTemplateOutlet="entryList; context: { $implicit: selected.entries }"></ng-container>
        <div class="text-muted" style="font-size:0.85rem;" *ngIf="!selected.entries.length">Nothing on this day.</div>
      </div>

      <div class="mt-2 text-muted" style="font-size:0.8rem;">
        <span class="cal-chip k-limit" style="display:inline-block;">Limitation date</span>
        <span class="cal-chip k-court" style="display:inline-block;">Court date / hearing</span>
        <span class="cal-chip k-filing" style="display:inline-block;">Filing</span>
        <span class="cal-chip k-meeting" style="display:inline-block;">Meeting</span>
        <span class="cal-chip k-task" style="display:inline-block;">Task</span>
      </div>

      <ng-template #entryList let-list>
        <div class="entry-row cal-entry" *ngFor="let e of list">
          <span class="cal-chip" [ngClass]="cls(e)" style="display:inline-block; margin:0;">{{ e.kind === 'TASK' ? 'Task' : typeLabel(e.type) }}</span>
          <span [class.done]="e.done"><strong>{{ e.time ? e.time.slice(0, 5) + ' ' : '' }}{{ e.title }}</strong></span>
          <a [routerLink]="['/law/matters', e.matterId]">{{ e.matterNumber }}</a>
          <span class="text-muted">{{ e.matterTitle }}</span>
          <span class="text-muted" *ngIf="e.assignedToName">· {{ e.assignedToName }}</span>
          <span class="badge badge-danger" *ngIf="e.overdue">Overdue</span>
        </div>
      </ng-template>
    </app-shell>
  `,
})
export class LawCalendarComponent implements OnInit {
  heads = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  view: 'month' | 'week' = 'month';
  /** First day of the month (month view) or the Monday (week view) being shown. */
  anchor: Date = new Date();
  mine = true;
  tasks = true;
  includeDone = false;
  canSeeAll = false;
  title = '';
  rows: Cell[][] = [];
  weekDays: Cell[] = [];
  selected: Cell | null = null;
  error: string | null = null;
  busyIcs = false;
  icsError: string | null = null;

  constructor(private api: LawApiService, private http: HttpClient, private auth: AuthService) {}

  ngOnInit(): void {
    this.canSeeAll = this.auth.canAccessPath('/admin/law/work');
    const now = new Date();
    this.anchor = new Date(now.getFullYear(), now.getMonth(), 1);
    this.load(ymd(now));
  }

  typeLabel(t: string | null): string { return t ? (DEADLINE_TYPE_LABEL[t as DeadlineType] ?? t) : ''; }
  label(e: CalendarEntry): string { return (e.time ? e.time.slice(0, 5) + ' ' : '') + e.title; }
  cls(e: CalendarEntry): string {
    if (e.kind === 'TASK') return 'k-task';
    switch (e.type) {
      case 'LIMITATION': return 'k-limit';
      case 'COURT_DATE': case 'HEARING': return 'k-court';
      case 'FILING': return 'k-filing';
      case 'MEETING': return 'k-meeting';
      default: return 'k-other';
    }
  }
  dayName(date: string): string {
    const d = parse(date);
    return ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][d.getDay()] + ' ' + d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
  }

  private range(): { from: Date; to: Date } {
    if (this.view === 'week') return { from: this.anchor, to: addDays(this.anchor, 6) };
    const first = monday(this.anchor);
    const last = new Date(this.anchor.getFullYear(), this.anchor.getMonth() + 1, 0);
    const end = addDays(last, 6 - ((last.getDay() + 6) % 7));
    return { from: first, to: end };
  }

  setView(v: 'month' | 'week'): void {
    if (v === this.view) return;
    const keep = this.selected ? parse(this.selected.date) : new Date();
    this.view = v;
    this.anchor = v === 'week' ? monday(keep) : new Date(keep.getFullYear(), keep.getMonth(), 1);
    this.load();
  }

  setWho(v: string): void { this.mine = v !== 'all'; this.load(); }

  step(n: number): void {
    this.anchor = this.view === 'week' ? addDays(this.anchor, 7 * n) : new Date(this.anchor.getFullYear(), this.anchor.getMonth() + n, 1);
    this.selected = null;
    this.load();
  }

  goToday(): void {
    const now = new Date();
    this.anchor = this.view === 'week' ? monday(now) : new Date(now.getFullYear(), now.getMonth(), 1);
    this.load(ymd(now));
  }

  select(c: Cell): void { this.selected = c; }

  load(select?: string): void {
    const { from, to } = this.range();
    this.error = null;
    this.api.calendar({ mine: this.mine, from: ymd(from), to: ymd(to), includeDone: this.includeDone, tasks: this.tasks }).subscribe({
      next: (list) => this.build(from, to, list, select),
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not load the calendar';
        if (e?.status === 403 && !this.mine) this.mine = true;
      },
    });
  }

  private build(from: Date, to: Date, list: CalendarEntry[], select?: string): void {
    const by = new Map<string, CalendarEntry[]>();
    for (const e of list) { const a = by.get(e.date) ?? []; a.push(e); by.set(e.date, a); }
    const todayStr = ymd(new Date());
    const cells: Cell[] = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      const s = ymd(d);
      const entries = by.get(s) ?? [];
      cells.push({ date: s, day: d.getDate(), inMonth: this.view === 'week' || d.getMonth() === this.anchor.getMonth(), today: s === todayStr, entries, shown: entries.slice(0, 3), more: Math.max(0, entries.length - 3) });
    }
    if (this.view === 'week') {
      this.weekDays = cells;
      this.rows = [];
      const a = this.anchor, b = addDays(this.anchor, 6);
      this.title = a.getDate() + ' ' + MONTHS[a.getMonth()].slice(0, 3) + ' – ' + b.getDate() + ' ' + MONTHS[b.getMonth()].slice(0, 3) + ' ' + b.getFullYear();
    } else {
      this.weekDays = [];
      const rows: Cell[][] = [];
      for (let i = 0; i < cells.length; i += 7) rows.push(cells.slice(i, i + 7));
      this.rows = rows;
      this.title = MONTHS[this.anchor.getMonth()] + ' ' + this.anchor.getFullYear();
      const want = select ?? this.selected?.date;
      this.selected = cells.find((c) => c.date === want) ?? (select ? null : this.selected && cells.find((c) => c.date === this.selected!.date)) ?? null;
    }
  }

  download(): void {
    this.busyIcs = true;
    this.icsError = null;
    const params = new HttpParams().set('mine', this.mine).set('tasks', this.tasks);
    this.http.get(`${API_BASE}/law/calendar/ics`, { params, responseType: 'blob' }).subscribe({
      next: (b) => { this.busyIcs = false; downloadBlob(b, 'law-firm-calendar.ics'); },
      error: () => { this.busyIcs = false; this.icsError = 'Could not prepare the file'; },
    });
  }
}
