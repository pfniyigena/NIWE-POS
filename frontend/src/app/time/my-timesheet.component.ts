import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { DatePickerComponent } from '../shared/date-picker.component';
import {
  EntryPayload, MySummary, ProjectChoice, TimeApiService, TimeEntry, Timer, Week,
} from '../core/api/time-api.service';
import { addDays, formatClock, formatMinutes, mondayOf, parseDuration, parseIso, today } from '../core/time-util';

/** Time Tracking > My time: the stopwatch, this week's entries, and submitting the week for approval. Open to every signed-in user. */
@Component({
  selector: 'app-my-timesheet',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">My time</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="stat-grid mb-3" *ngIf="summary">
        <div class="stat-tile"><div class="stat-label">Today</div><div class="stat-value">{{ summary.hoursToday | number: '1.0-2' }} h</div></div>
        <div class="stat-tile"><div class="stat-label">This week</div><div class="stat-value">{{ summary.hoursThisWeek | number: '1.0-2' }} h</div>
          <div class="text-muted" style="font-size:0.75rem;">{{ summary.billableHoursThisWeek | number: '1.0-2' }} h billable</div></div>
        <div class="stat-tile"><div class="stat-label">This month</div><div class="stat-value">{{ summary.hoursThisMonth | number: '1.0-2' }} h</div></div>
        <div class="stat-tile" [class.warn]="summary.rejected > 0">
          <div class="stat-label">To submit</div><div class="stat-value">{{ summary.toSubmit }}</div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="summary.rejected">{{ summary.rejected }} sent back</div></div>
      </div>

      <!-- Stopwatch -->
      <div class="card mb-3" style="padding:1rem;">
        <ng-container *ngIf="timer?.running; else idle">
          <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
            <div style="font-size:1.8rem; font-variant-numeric:tabular-nums;">⏱ {{ clock }}</div>
            <div>
              <strong>{{ timer!.projectName }}</strong><span *ngIf="timer!.taskName"> · {{ timer!.taskName }}</span>
              <div class="text-muted" *ngIf="timer!.description">{{ timer!.description }}</div>
            </div>
            <span class="spacer"></span>
            <input class="form-control" style="width:14rem;" placeholder="What did you do? (optional)" name="sd" [(ngModel)]="stopNote" />
            <button class="btn btn-primary" type="button" (click)="stop()">Stop and save</button>
            <button class="btn btn-outline-secondary" type="button" (click)="discard()" title="Throw this time away">Discard</button>
          </div>
        </ng-container>
        <ng-template #idle>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <strong>Timer</strong>
            <select class="form-select" style="width:auto; max-width:16rem;" name="tp" [(ngModel)]="timerForm.projectId" (ngModelChange)="timerForm.taskId = null">
              <option [ngValue]="null">Project…</option>
              <option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}<ng-container *ngIf="p.customerName"> — {{ p.customerName }}</ng-container></option>
            </select>
            <select class="form-select" style="width:auto;" name="tt" [(ngModel)]="timerForm.taskId" *ngIf="tasksOf(timerForm.projectId).length">
              <option [ngValue]="null">No task</option>
              <option *ngFor="let t of tasksOf(timerForm.projectId)" [ngValue]="t.id">{{ t.name }}</option>
            </select>
            <input class="form-control" style="width:16rem;" placeholder="What are you working on?" name="td" [(ngModel)]="timerForm.description" />
            <button class="btn btn-success" type="button" [disabled]="!timerForm.projectId" (click)="start()">▶ Start</button>
          </div>
        </ng-template>
      </div>

      <!-- Add / edit one entry -->
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
        <strong class="mb-2">{{ editingId ? 'Change this entry' : 'Add time' }}</strong>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Date</label>
            <app-date-picker name="wd" [(ngModel)]="form.workDate" [max]="todayIso"></app-date-picker></div>
          <div class="form-group"><label class="form-label">Project</label>
            <select class="form-select" name="fp" [(ngModel)]="form.projectId" (ngModelChange)="onProject()">
              <option [ngValue]="null">Choose…</option>
              <option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}<ng-container *ngIf="p.customerName"> — {{ p.customerName }}</ng-container></option>
            </select></div>
          <div class="form-group" *ngIf="tasksOf(form.projectId).length"><label class="form-label">Task</label>
            <select class="form-select" name="ft" [(ngModel)]="form.taskId">
              <option [ngValue]="null">No task</option>
              <option *ngFor="let t of tasksOf(form.projectId)" [ngValue]="t.id">{{ t.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">Time</label>
            <input class="form-control" style="width:7rem;" name="fh" [(ngModel)]="form.duration" placeholder="1:30 or 1.5" /></div>
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">What did you do?</label>
            <input class="form-control" name="fd" [(ngModel)]="form.description" /></div>
          <div class="form-group" *ngIf="currentProject()?.billable"><label style="display:flex; gap:0.3rem; align-items:center;">
            <input type="checkbox" name="fb" [(ngModel)]="form.billable" /> Billable</label></div>
          <div class="form-group">
            <button class="btn btn-primary" type="submit" [disabled]="busy">{{ editingId ? 'Save' : 'Add' }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="editingId" (click)="cancelEdit()" style="margin-left:0.4rem;">Cancel</button>
          </div>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">Type time as 1:30, 1.5, 90m or 2h. Rates are worked out from the project; you don't need to enter any.</div>
      </form>

      <!-- Week -->
      <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;" *ngIf="week">
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="shift(-7)">‹ Previous week</button>
        <strong>{{ weekLabel }}</strong>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="shift(7)">Next week ›</button>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="thisWeek()">This week</button>
        <span class="spacer"></span>
        <span>{{ week.totalHours | number: '1.0-2' }} h total · {{ week.billableHours | number: '1.0-2' }} h billable</span>
        <button class="btn btn-success" type="button" [disabled]="!submittable().length || busy" (click)="submitWeek()">
          Submit {{ submittable().length }} for approval</button>
      </div>

      <table class="table-sm" *ngIf="week">
        <thead><tr><th>Date</th><th>Project</th><th>Task</th><th>What</th><th class="text-end">Time</th><th>Billable</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let e of week.entries">
            <td>{{ dayLabel(e.workDate) }}</td>
            <td>{{ e.projectName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="e.customerName">{{ e.customerName }}</div></td>
            <td>{{ e.taskName || '—' }}</td>
            <td>{{ e.description || '—' }}<span class="badge badge-secondary" *ngIf="e.origin === 'TIMER'" style="margin-left:0.3rem;">timer</span>
              <div style="color:var(--danger, #c0392b); font-size:0.8rem;" *ngIf="e.rejectReason">Sent back: {{ e.rejectReason }}</div></td>
            <td class="text-end">{{ fmt(e.minutes) }}</td>
            <td>{{ e.billable ? 'Yes' : 'No' }}</td>
            <td><span class="badge" [class.badge-secondary]="e.status === 'DRAFT'" [class.badge-warning]="e.status === 'SUBMITTED'"
                [class.badge-success]="e.status === 'APPROVED' || e.status === 'INVOICED'" [class.badge-danger]="e.status === 'REJECTED'">{{ label(e.status) }}</span></td>
            <td style="white-space:nowrap;">
              <ng-container *ngIf="e.status === 'DRAFT' || e.status === 'REJECTED'">
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="edit(e)">Edit</button>
                <button class="btn btn-sm btn-outline-danger" type="button" (click)="remove(e)" style="margin-left:0.3rem;">Delete</button>
              </ng-container>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="week && !week.entries.length">Nothing logged this week yet. Start the timer or add time above.</p>
    </app-shell>
  `,
})
export class MyTimesheetComponent implements OnInit, OnDestroy {
  todayIso = today();
  projects: ProjectChoice[] = [];
  week: Week | null = null;
  summary: MySummary | null = null;
  timer: Timer | null = null;
  clock = '0:00:00';
  stopNote = '';
  from = mondayOf(today());
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  editingId: number | null = null;
  timerForm: { projectId: number | null; taskId: number | null; description: string } = { projectId: null, taskId: null, description: '' };
  form: { workDate: string; projectId: number | null; taskId: number | null; duration: string; description: string; billable: boolean } = this.blank();
  private tick: any;
  private startedMs = 0;

  constructor(private api: TimeApiService) {}

  ngOnInit(): void {
    this.api.projectChoices().subscribe({ next: (p) => (this.projects = p), error: (e) => this.fail(e, 'Could not load the projects') });
    this.loadTimer();
    this.reload();
    this.tick = setInterval(() => this.updateClock(), 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.tick);
  }

  private blank() {
    return { workDate: today(), projectId: null as number | null, taskId: null as number | null, duration: '', description: '', billable: true };
  }

  private fail(e: any, fallback: string): void {
    this.busy = false;
    this.notice = null;
    this.error = e?.error?.message ?? fallback;
  }

  tasksOf(projectId: number | null) {
    return this.projects.find((p) => p.id === projectId)?.tasks ?? [];
  }

  currentProject(): ProjectChoice | undefined {
    return this.projects.find((p) => p.id === this.form.projectId);
  }

  onProject(): void {
    this.form.taskId = null;
    this.form.billable = this.currentProject()?.billable ?? true;
  }

  reload(): void {
    const to = addDays(this.from, 6);
    this.api.myWeek(this.from, to).subscribe({ next: (w) => (this.week = w), error: (e) => this.fail(e, 'Could not load your time') });
    this.api.mySummary().subscribe({ next: (s) => (this.summary = s), error: () => {} });
  }

  get weekLabel(): string {
    const a = parseIso(this.from);
    const b = parseIso(addDays(this.from, 6));
    const f = (d: Date) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
    return `${f(a)} – ${f(b)} ${b.getFullYear()}`;
  }

  dayLabel(iso: string): string {
    return parseIso(iso).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
  }

  fmt(minutes: number): string {
    return formatMinutes(minutes);
  }

  label(s: string): string {
    return s === 'SUBMITTED' ? 'Waiting' : s === 'REJECTED' ? 'Sent back' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  shift(days: number): void {
    this.from = addDays(this.from, days);
    this.reload();
  }

  thisWeek(): void {
    this.from = mondayOf(today());
    this.reload();
  }

  submittable(): TimeEntry[] {
    return (this.week?.entries ?? []).filter((e) => e.status === 'DRAFT' || e.status === 'REJECTED');
  }

  // ---- entry form

  save(): void {
    this.error = null;
    this.notice = null;
    const minutes = parseDuration(this.form.duration);
    if (!this.form.projectId) { this.error = 'Choose a project.'; return; }
    if (minutes == null || minutes < 1) { this.error = 'Enter the time as 1:30, 1.5, 90m or 2h.'; return; }
    const payload: EntryPayload = {
      projectId: this.form.projectId, taskId: this.form.taskId, workDate: this.form.workDate, minutes,
      description: this.form.description, billable: this.form.billable,
    };
    this.busy = true;
    const call = this.editingId ? this.api.updateEntry(this.editingId, payload) : this.api.createEntry(payload);
    call.subscribe({
      next: () => {
        this.busy = false;
        const keepDate = this.form.workDate;
        this.editingId = null;
        this.form = { ...this.blank(), workDate: keepDate };
        // jump to the week of the date just saved, so the entry is visible
        this.from = mondayOf(keepDate);
        this.reload();
      },
      error: (e) => this.fail(e, 'Could not save'),
    });
  }

  edit(e: TimeEntry): void {
    this.editingId = e.id;
    this.form = { workDate: e.workDate, projectId: e.projectId, taskId: e.taskId, duration: formatMinutes(e.minutes), description: e.description ?? '', billable: e.billable };
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.blank();
  }

  remove(e: TimeEntry): void {
    if (!confirm('Delete this entry?')) return;
    this.api.deleteEntry(e.id).subscribe({ next: () => this.reload(), error: (x) => this.fail(x, 'Could not delete') });
  }

  submitWeek(): void {
    const ids = this.submittable().map((e) => e.id);
    this.busy = true;
    this.error = null;
    this.api.submit(ids).subscribe({
      next: (r) => {
        this.busy = false;
        this.notice = `${r.submitted} entr${r.submitted === 1 ? 'y' : 'ies'} handed in.`;
        this.reload();
      },
      error: (e) => this.fail(e, 'Could not submit'),
    });
  }

  // ---- timer

  private loadTimer(): void {
    this.api.timer().subscribe({
      next: (t) => {
        this.timer = t;
        this.startedMs = t.running && t.startedAt ? new Date(t.startedAt).getTime() : 0;
        this.updateClock();
      },
      error: () => {},
    });
  }

  private updateClock(): void {
    this.clock = this.startedMs ? formatClock(Math.max(0, Math.floor((Date.now() - this.startedMs) / 1000))) : '0:00:00';
  }

  start(): void {
    if (!this.timerForm.projectId) return;
    this.error = null;
    this.api.startTimer({ projectId: this.timerForm.projectId, taskId: this.timerForm.taskId, description: this.timerForm.description }).subscribe({
      next: (t) => {
        this.timer = t;
        this.startedMs = t.startedAt ? new Date(t.startedAt).getTime() : Date.now();
        this.timerForm = { projectId: null, taskId: null, description: '' };
        this.updateClock();
        this.reload();
      },
      error: (e) => this.fail(e, 'Could not start the timer'),
    });
  }

  stop(): void {
    this.error = null;
    this.api.stopTimer({ description: this.stopNote || null }).subscribe({
      next: (e) => {
        this.timer = null;
        this.startedMs = 0;
        this.stopNote = '';
        this.notice = `Saved ${formatMinutes(e.minutes)} on ${e.projectName}.`;
        this.updateClock();
        this.from = mondayOf(e.workDate);
        this.reload();
      },
      error: (e) => this.fail(e, 'Could not stop the timer'),
    });
  }

  discard(): void {
    if (!confirm('Throw away the time on this timer?')) return;
    this.api.discardTimer().subscribe({
      next: () => {
        this.timer = null;
        this.startedMs = 0;
        this.updateClock();
      },
      error: (e) => this.fail(e, 'Could not discard the timer'),
    });
  }
}
