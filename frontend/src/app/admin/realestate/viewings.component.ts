import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { ViewingFormComponent } from './viewing-form.component';
import { Option, RealEstateApiService, ViewingDto, VIEWING_STATUS_LABEL, ViewingStatus } from '../../core/api/realestate-api.service';

interface Day { date: string; label: string; today: boolean; items: ViewingDto[]; }

/** Real Estate > Viewings: the diary as a list by day or as a week calendar, for a chosen period and agent. Reminders go to the lead from the row. */
@Component({
  selector: 'app-re-viewings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ViewingFormComponent, ReExportComponent],
  styles: [`
    .week { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 0.5rem; align-items: start; }
    .week-day { border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface); padding: 0.4rem; min-height: 8rem; }
    .week-day.is-today { border-color: var(--primary, #22c55e); }
    .week-day h5 { margin: 0 0 0.35rem; font-size: 0.8rem; }
    .vcard { border: 1px solid var(--border); border-left-width: 4px; border-radius: 6px; padding: 0.3rem 0.4rem; margin-bottom: 0.35rem; font-size: 0.78rem; cursor: pointer; overflow-wrap: anywhere; }
    .vcard.s-SCHEDULED { border-left-color: #d97706; } .vcard.s-DONE { border-left-color: #16a34a; } .vcard.s-CANCELLED, .vcard.s-NO_SHOW { border-left-color: #9ca3af; opacity: 0.7; }
    .vcard.open { outline: 2px solid var(--primary, #22c55e); }
    @media (max-width: 900px) { .week { grid-template-columns: 1fr; } .week-day { min-height: 0; } }
  `],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Viewings' | translate }}</h3>
        <button class="btn btn-primary" type="button" (click)="booking = !booking">{{ '+ Book viewing' | translate }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <app-viewing-form *ngIf="booking" (saved)="saved()" (closed)="booking = false"></app-viewing-form>
      <app-viewing-form *ngIf="moving" [existing]="moving" (saved)="saved()" (closed)="moving = null"></app-viewing-form>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <span style="display:inline-flex; gap:0.2rem;">
          <button class="btn btn-sm view-list" type="button" [class.btn-primary]="view === 'list'" [class.btn-outline-secondary]="view !== 'list'" (click)="setView('list')">{{ 'List' | translate }}</button>
          <button class="btn btn-sm view-week" type="button" [class.btn-primary]="view === 'week'" [class.btn-outline-secondary]="view !== 'week'" (click)="setView('week')">{{ 'Week' | translate }}</button>
        </span>
        <ng-container *ngIf="view === 'list'">
          <label class="text-muted">{{ 'From' | translate }}</label><input class="form-control" style="width:10.5rem;" type="date" name="from" [(ngModel)]="from" (change)="load()" />
          <label class="text-muted">{{ 'to' | translate }}</label><input class="form-control" style="width:10.5rem;" type="date" name="to" [(ngModel)]="to" (change)="load()" />
        </ng-container>
        <ng-container *ngIf="view === 'week'">
          <button class="btn btn-sm btn-outline-secondary week-prev" type="button" (click)="shiftWeek(-1)">{{ '‹ Previous' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary week-today" type="button" (click)="thisWeek()">{{ 'This week' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary week-next" type="button" (click)="shiftWeek(1)">{{ 'Next ›' | translate }}</button>
          <strong class="week-label">{{ weekLabel() }}</strong>
        </ng-container>
        <select class="form-select" style="width:auto;" name="ag" [(ngModel)]="agentId" (ngModelChange)="load()"><option [ngValue]="null">{{ 'Any agent' | translate }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select>
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'Any status' | translate }}</option><option value="SCHEDULED">{{ 'Scheduled' | translate }}</option><option value="DONE">{{ 'Done' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option><option value="NO_SHOW">{{ 'No-show' | translate }}</option>
        </select>
        <app-re-export kind="viewings" [filters]="{ from: from, to: to, agentId: agentId, status: status }"></app-re-export>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="outcome">
        <div class="alert alert-danger" *ngIf="outcomeError">{{ outcomeError }}</div>
        <strong>{{ 'Outcome of the viewing of {p0} on {p1}' | translate: { p0: (outcome.propertyCode), p1: (outcome.startAt | date: 'd MMM, HH:mm') } }}</strong>
        <div class="flex gap-2 mt-1" style="flex-wrap:wrap;">
          <select class="form-select" style="width:auto;" name="os" [(ngModel)]="outcomeStatus"><option value="DONE">{{ 'Happened' | translate }}</option><option value="NO_SHOW">{{ 'No-show' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option></select>
          <input class="form-control" style="flex:1; min-width:12rem;" name="of" [(ngModel)]="outcomeFeedback" maxlength="2000" [placeholder]="'Feedback (optional)' | translate" />
          <button class="btn btn-sm btn-primary" type="button" (click)="saveOutcome()" [disabled]="busy">{{ 'Save' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="outcome = null">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <!-- week calendar -->
      <div class="week mb-3" *ngIf="view === 'week'">
        <div class="week-day" *ngFor="let d of weekDays" [class.is-today]="d.today">
          <h5>{{ d.label }}<span class="text-muted" *ngIf="d.items.length"> · {{ d.items.length }}</span></h5>
          <div class="vcard" *ngFor="let v of d.items" [ngClass]="'s-' + v.status" [class.open]="picked && picked.id === v.id" (click)="pick(v)">
            <strong>{{ v.startAt | date: 'HH:mm' }}</strong> {{ v.propertyCode }}
            <div>{{ v.leadName }}</div>
            <div class="text-muted">{{ v.agentName || '—' }}</div>
            <span class="badge badge-secondary" *ngIf="v.status !== 'SCHEDULED'">{{ statusName(v.status) }}</span>
            <span class="badge badge-success" *ngIf="v.clientRemindedAt" [title]="'The client was e-mailed a reminder' | translate">{{ 'Reminded' | translate }}</span>
          </div>
          <div class="text-muted" style="font-size:0.78rem;" *ngIf="!d.items.length">—</div>
        </div>
      </div>
      <div class="card mb-3 week-detail" style="padding:0.75rem;" *ngIf="view === 'week' && picked">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a [routerLink]="['/admin/realestate/properties', picked.propertyId]"><strong>{{ picked.propertyCode }}</strong></a> {{ picked.propertyTitle }}<span class="text-muted" *ngIf="picked.propertyArea"> · {{ picked.propertyArea }}</span>
            <div>{{ picked.startAt | date: 'EEEE d MMM, HH:mm' }} – {{ picked.endAt | date: 'HH:mm' }} · <a [routerLink]="['/admin/realestate/leads', picked.leadId]">{{ picked.leadName }}</a><span class="text-muted" *ngIf="picked.leadPhone"> · {{ picked.leadPhone }}</span></div>
            <div class="text-muted">{{ 'Agent: {p0} · {p1}' | translate: { p0: (picked.agentName || '—'), p1: (statusName(picked.status)) } }}<span *ngIf="picked.feedback"> · “{{ picked.feedback }}”</span></div>
          </div>
          <div style="white-space:nowrap;">
            <ng-container *ngIf="picked.status === 'SCHEDULED' && picked.canManage">
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="moving = picked">{{ 'Move' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="startOutcome(picked)">{{ 'Outcome' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary week-remind" type="button" style="margin-left:0.2rem;" (click)="remind(picked)" [disabled]="busy" *ngIf="canRemind(picked)">{{ 'Remind' | translate }}</button>
            </ng-container>
            <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="picked = null">{{ 'Close' | translate }}</button>
          </div>
        </div>
      </div>

      <!-- list by day -->
      <ng-container *ngIf="view === 'list'">
        <div *ngFor="let d of days" class="mb-3">
          <h4 class="mb-1">{{ d.date | date: 'EEEE d MMMM' }}</h4>
          <table class="stack table-sm"><tbody>
            <tr *ngFor="let v of d.items">
              <td data-label="Time" style="white-space:nowrap; width:8rem;">{{ v.startAt | date: 'HH:mm' }} – {{ v.endAt | date: 'HH:mm' }}</td>
              <td data-label="Property"><a [routerLink]="['/admin/realestate/properties', v.propertyId]"><strong>{{ v.propertyCode }}</strong></a> {{ v.propertyTitle }}<span class="text-muted" *ngIf="v.propertyArea"> · {{ v.propertyArea }}</span></td>
              <td data-label="Client"><a [routerLink]="['/admin/realestate/leads', v.leadId]">{{ v.leadName }}</a><span class="text-muted" *ngIf="v.leadPhone"> · {{ v.leadPhone }}</span></td>
              <td data-label="Agent">{{ v.agentName || '—' }}</td>
              <td data-label="Status" style="white-space:nowrap;">
                <span class="badge" [class.badge-success]="v.status === 'DONE'" [class.badge-warning]="v.status === 'SCHEDULED'" [class.badge-secondary]="v.status === 'CANCELLED' || v.status === 'NO_SHOW'">{{ statusName(v.status) }}</span>
                <span class="badge badge-success" *ngIf="v.clientRemindedAt" style="margin-left:0.3rem;" [title]="'The client was e-mailed a reminder' | translate">{{ 'Reminded' | translate }}</span>
                <span class="text-muted" *ngIf="v.feedback" style="margin-left:0.4rem;">“{{ v.feedback }}”</span>
              </td>
              <td data-label="" style="white-space:nowrap; text-align:right;">
                <ng-container *ngIf="v.status === 'SCHEDULED' && v.canManage">
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="moving = v">{{ 'Move' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="startOutcome(v)">{{ 'Outcome' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary row-remind" type="button" style="margin-left:0.2rem;" (click)="remind(v)" [disabled]="busy" *ngIf="canRemind(v)">{{ 'Remind' | translate }}</button>
                </ng-container>
              </td>
            </tr>
          </tbody></table>
        </div>
        <p class="text-muted" *ngIf="!days.length && loaded">{{ 'No viewings in this period.' | translate }}</p>
      </ng-container>
      <p class="text-muted" *ngIf="view === 'week' && loaded && !count">{{ 'No viewings this week.' | translate }}</p>
    </app-shell>
  `,
})
export class ReViewingsComponent implements OnInit {
  list: ViewingDto[] = [];
  days: { date: string; items: ViewingDto[] }[] = [];
  weekDays: Day[] = [];
  count = 0;
  view: 'list' | 'week' = 'list';
  weekStart = new Date();
  picked: ViewingDto | null = null;
  agents: Option[] = [];
  from = '';
  to = '';
  agentId: number | null = null;
  status = '';
  loaded = false;
  booking = false;
  moving: ViewingDto | null = null;
  outcome: ViewingDto | null = null;
  outcomeStatus: ViewingStatus = 'DONE';
  outcomeFeedback = '';
  outcomeError: string | null = null;
  busy = false;
  error: string | null = null;
  notice: string | null = null;

  constructor(private api: RealEstateApiService) {}

  private iso(d: Date): string { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

  private mondayOf(d: Date): Date {
    const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    x.setDate(x.getDate() - ((x.getDay() + 6) % 7));
    return x;
  }

  ngOnInit(): void {
    const t = new Date();
    this.from = this.iso(t);
    t.setDate(t.getDate() + 14);
    this.to = this.iso(t);
    this.weekStart = this.mondayOf(new Date());
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.load();
  }

  setView(v: 'list' | 'week'): void { this.error = null; this.notice = null; this.view = v; this.picked = null; this.load(); }
  shiftWeek(n: number): void { this.error = null; this.notice = null; const d = new Date(this.weekStart); d.setDate(d.getDate() + 7 * n); this.weekStart = d; this.picked = null; this.load(); }
  thisWeek(): void { this.weekStart = this.mondayOf(new Date()); this.picked = null; this.load(); }

  weekLabel(): string {
    const e = new Date(this.weekStart); e.setDate(e.getDate() + 6);
    const f = (d: Date, y: boolean) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', ...(y ? { year: 'numeric' } : {}) });
    return `${f(this.weekStart, false)} – ${f(e, true)}`;
  }

  load(): void {
    let from = this.from, to = this.to;
    if (this.view === 'week') {
      const e = new Date(this.weekStart); e.setDate(e.getDate() + 6);
      from = this.iso(this.weekStart);
      to = this.iso(e);
    }
    this.api.viewings({ from, to, agentId: this.agentId, status: this.status }).subscribe({
      next: (l) => {
        this.list = l;
        const map = new Map<string, ViewingDto[]>();
        for (const v of l) {
          const key = this.iso(new Date(v.startAt));
          map.set(key, [...(map.get(key) ?? []), v]);
        }
        this.days = Array.from(map.entries()).map(([date, items]) => ({ date, items }));
        this.count = l.length;
        const today = this.iso(new Date());
        this.weekDays = [];
        for (let i = 0; i < 7; i++) {
          const d = new Date(this.weekStart); d.setDate(d.getDate() + i);
          const key = this.iso(d);
          this.weekDays.push({ date: key, label: d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' }), today: key === today, items: map.get(key) ?? [] });
        }
        if (this.picked) this.picked = l.find((x) => x.id === this.picked!.id) ?? null;
        this.loaded = true;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the viewings'),
    });
  }

  pick(v: ViewingDto): void { this.picked = this.picked && this.picked.id === v.id ? null : v; }
  statusName(s: string): string { return (VIEWING_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  saved(): void { this.error = null; this.booking = false; this.moving = null; this.load(); }
  startOutcome(v: ViewingDto): void { this.outcome = v; this.outcomeStatus = 'DONE'; this.outcomeFeedback = ''; this.outcomeError = null; }
  canRemind(v: ViewingDto): boolean { return v.status === 'SCHEDULED' && new Date(v.startAt).getTime() > Date.now(); }

  remind(v: ViewingDto): void {
    this.busy = true;
    this.notice = null;
    this.error = null;
    this.api.remindViewing(v.id).subscribe({
      next: (r) => { this.busy = false; if (r.status === 'SENT') this.notice = r.message; else this.error = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not send the reminder'; },
    });
  }

  saveOutcome(): void {
    if (!this.outcome) return;
    this.busy = true;
    this.api.setViewingStatus(this.outcome.id, this.outcomeStatus, this.outcomeFeedback.trim() || null).subscribe({
      next: () => { this.busy = false; this.outcome = null; this.load(); },
      error: (e) => { this.busy = false; this.outcomeError = e?.error?.message ?? 'Could not save the outcome'; },
    });
  }
}
