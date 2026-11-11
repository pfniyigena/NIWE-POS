import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { StaffOption, TimeApiService } from '../../core/api/time-api.service';
import { WorkOrderApiService, WorkOrderSummary } from '../../core/api/work-order-api.service';
import { WorkOrderTabsComponent } from './work-order-tabs.component';

import { T } from '../../core/i18n/t';
interface Day { iso: string; label: string; today: boolean; }
interface Lane { userId: number | null; name: string; }

const pad = (n: number) => String(n).padStart(2, '0');
const toIso = (d: Date) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
const fromIso = (s: string) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (s: string, n: number) => { const d = fromIso(s); d.setDate(d.getDate() + n); return toIso(d); };
const daysBetween = (a: string, b: string) => Math.round((fromIso(b).getTime() - fromIso(a).getTime()) / 86400000);

/** Work Orders > Calendar: a week per technician. Drag a job to another day or another person to re-schedule it. */
@Component({
  selector: 'app-work-order-calendar',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, WorkOrderTabsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-2">
        <h3>{{ 'Work orders' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/work-orders/new">{{ '+ New work order' | translate }}</a>
      </div>
      <app-work-order-tabs></app-work-order-tabs>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button class="btn btn-outline-secondary btn-sm" type="button" id="cal-prev" (click)="shift(-7)">{{ '‹ Previous week' | translate }}</button>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="goToday()">{{ 'This week' | translate }}</button>
        <button class="btn btn-outline-secondary btn-sm" type="button" id="cal-next" (click)="shift(7)">{{ 'Next week ›' | translate }}</button>
        <strong>{{ days[0].iso }} — {{ days[6].iso }}</strong>
        <select class="form-select" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="build()">
          <option value="">{{ 'Customer and internal' | translate }}</option>
          <option value="CUSTOMER">{{ 'Customer jobs' | translate }}</option><option value="INTERNAL">{{ 'Internal maintenance' | translate }}</option>
        </select>
        <span class="text-muted" style="font-size:0.8rem;">{{ 'Drag a job to another day or person. Jobs are shown on their scheduled day (or their due day if not scheduled).' | translate }}</span>
      </div>

      <div style="overflow-x:auto;">
        <table class="cal-table" style="border-collapse:collapse; width:100%; table-layout:fixed; min-width:60rem;">
          <thead>
            <tr>
              <th style="width:9rem; text-align:left; padding:0.4rem;">{{ 'Person' | translate }}</th>
              <th *ngFor="let d of days" style="padding:0.4rem; text-align:left;" [style.background]="d.today ? 'rgba(47,190,114,0.14)' : ''">{{ d.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of lanes" [attr.data-lane]="l.userId === null ? 'none' : l.userId">
              <th style="text-align:left; padding:0.4rem; vertical-align:top; border-top:1px solid var(--border);">{{ l.name }}</th>
              <td *ngFor="let d of days" class="cal-cell" [attr.data-day]="d.iso"
                  style="vertical-align:top; border-top:1px solid var(--border); border-left:1px solid var(--border); padding:0.25rem; height:4.5rem;"
                  [style.background]="over === key(l, d) ? 'rgba(59,130,246,0.28)' : (d.today ? 'rgba(47,190,114,0.07)' : '')"
                  (dragover)="allow($event, l, d)" (dragleave)="over = null" (drop)="drop($event, l, d)">
                <div *ngFor="let w of cell(l, d)" class="cal-job" [attr.data-id]="w.id" [attr.draggable]="movable(w)" (dragstart)="dragStart($event, w)"
                     style="background:var(--surface); border:1px solid var(--border); border-radius:4px; padding:0.2rem 0.35rem; margin-bottom:0.25rem; font-size:0.78rem;"
                     [style.borderLeft]="w.overdue ? '4px solid #c0392b' : w.status === 'IN_PROGRESS' ? '4px solid #3b82f6' : w.status === 'COMPLETED' ? '4px solid #2e9e5b' : '4px solid #9aa4b2'"
                     [style.opacity]="movable(w) ? 1 : 0.6">
                  <a [routerLink]="['/admin/work-orders', w.id]"><strong>{{ w.number }}</strong></a>
                  <span *ngIf="!w.scheduledDate" class="text-muted"> {{ '(due)' | translate }}</span>
                  <div>{{ w.title }}</div>
                  <div class="text-muted">{{ w.type === 'CUSTOMER' ? (w.customerName || '') : (w.assetName || ('Internal' | translate)) }}</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted mt-2" *ngIf="unscheduled.length">
        {{ '{p0} job(s) have no date yet:' | translate: { p0: (unscheduled.length) } }}
        <a *ngFor="let w of unscheduled; let last = last" [routerLink]="['/admin/work-orders', w.id]">{{ w.number }}{{ last ? '' : ', ' }}</a>.
      </p>
    </app-shell>
  `,
})
export class WorkOrderCalendarComponent implements OnInit {
  all: WorkOrderSummary[] = [];
  staff: StaffOption[] = [];
  days: Day[] = [];
  lanes: Lane[] = [];
  unscheduled: WorkOrderSummary[] = [];
  weekStart = '';
  type = '';
  error: string | null = null;
  notice: string | null = null;
  over: string | null = null;
  private dragging: WorkOrderSummary | null = null;
  private cells = new Map<string, WorkOrderSummary[]>();

  constructor(private api: WorkOrderApiService, private timeApi: TimeApiService) {}

  ngOnInit(): void {
    this.goToday();
    this.timeApi.staff().subscribe({ next: (s) => { this.staff = s; this.build(); }, error: () => {} });
    this.load();
  }

  goToday(): void {
    const t = new Date();
    const back = (t.getDay() + 6) % 7; // Monday first
    t.setDate(t.getDate() - back);
    this.weekStart = toIso(t);
    this.build();
  }

  shift(n: number): void {
    this.weekStart = addDays(this.weekStart, n);
    this.build();
  }

  load(): void {
    this.api.list({}).subscribe({
      next: (l) => { this.all = l.filter((w) => w.status !== 'CANCELLED'); this.build(); },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the work orders')),
    });
  }

  private dayOf(w: WorkOrderSummary): string | null { return w.scheduledDate || w.dueDate; }

  build(): void {
    const today = toIso(new Date());
    const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    this.days = names.map((n, i) => { const iso = addDays(this.weekStart, i); return { iso, label: n + ' ' + iso.substring(5), today: iso === today }; });
    const rows = this.all.filter((w) => !this.type || w.type === this.type);
    this.unscheduled = rows.filter((w) => !this.dayOf(w) && (w.status === 'OPEN' || w.status === 'IN_PROGRESS' || w.status === 'ON_HOLD'));
    this.cells = new Map();
    const from = this.days[0].iso, to = this.days[6].iso;
    const known = new Set(this.staff.map((s) => s.id));
    const extra = new Map<number, string>();
    for (const w of rows) {
      const day = this.dayOf(w);
      if (!day || day < from || day > to) continue;
      if (w.assignedToUserId != null && !known.has(w.assignedToUserId)) extra.set(w.assignedToUserId, w.assignedToName || 'User ' + w.assignedToUserId);
      const k = (w.assignedToUserId ?? 'none') + '|' + day;
      const list = this.cells.get(k) ?? [];
      list.push(w);
      this.cells.set(k, list);
    }
    this.lanes = [
      ...this.staff.map((s) => ({ userId: s.id as number | null, name: s.fullName })),
      ...Array.from(extra.entries()).map(([id, name]) => ({ userId: id as number | null, name })),
      { userId: null, name: 'Unassigned' },
    ];
  }

  key(l: Lane, d: Day): string { return (l.userId ?? 'none') + '|' + d.iso; }
  cell(l: Lane, d: Day): WorkOrderSummary[] { return this.cells.get(this.key(l, d)) ?? []; }
  movable(w: WorkOrderSummary): boolean { return w.status === 'OPEN' || w.status === 'IN_PROGRESS' || w.status === 'ON_HOLD'; }

  dragStart(ev: DragEvent, w: WorkOrderSummary): void {
    if (!this.movable(w)) { ev.preventDefault(); return; }
    this.dragging = w;
    ev.dataTransfer?.setData('text/plain', String(w.id));
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move';
  }

  allow(ev: DragEvent, l: Lane, d: Day): void {
    if (!this.dragging) return;
    ev.preventDefault();
    this.over = this.key(l, d);
  }

  drop(ev: DragEvent, l: Lane, d: Day): void {
    ev.preventDefault();
    this.over = null;
    const w = this.dragging;
    this.dragging = null;
    if (!w) return;
    this.reschedule(w, d.iso, l.userId);
  }

  /** Moves a job to a day and person; keeps the gap between its scheduled and due dates. */
  reschedule(w: WorkOrderSummary, day: string, userId: number | null): void {
    this.error = null;
    this.notice = null;
    const was = this.dayOf(w);
    const sameDay = was === day;
    const sameUser = (w.assignedToUserId ?? null) === userId;
    if (sameDay && sameUser) return;
    let due = w.dueDate;
    if (due) due = w.scheduledDate ? addDays(due, was ? daysBetween(was, day) : 0) : (due < day ? day : due);
    this.api.reschedule(w.id, day, due, userId, !sameUser).subscribe({
      next: (d) => {
        const i = this.all.findIndex((x) => x.id === w.id);
        if (i >= 0) this.all[i] = { ...this.all[i], scheduledDate: d.scheduledDate, dueDate: d.dueDate, assignedToUserId: d.assignedToUserId, assignedToName: d.assignedToName, overdue: d.overdue };
        this.notice = w.number + ' moved to ' + day + (sameUser ? '' : ' and re-assigned') + '.';
        this.build();
      },
      error: (e) => (this.error = w.number + ': ' + (e?.error?.message ?? 'could not change the schedule')),
    });
  }
}
