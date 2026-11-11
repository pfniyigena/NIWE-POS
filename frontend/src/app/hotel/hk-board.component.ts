import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ChecklistItem, HkBoard, HkStaff, HkTask, HotelApiService } from '../core/api/hotel-api.service';
import { downloadBlob } from '../core/download-file';
import { errMsg, today } from './hotel-util';

import { T } from '../core/i18n/t';
/** Housekeeping > Today's board: the day's cleaning tasks, who does them, how long they took, and the supervisor's inspection. */
@Component({
  selector: 'app-hk-board',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="hk-board">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <label class="form-label" style="margin:0;">{{ 'Day' | translate }}</label>
        <input class="form-control board-date" type="date" style="width:auto;" [(ngModel)]="date" name="bd" (ngModelChange)="load()" />
        <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="date !== todayStr" (click)="date = todayStr; load()">{{ 'Today' | translate }}</button>
        <button class="btn btn-sm btn-primary prepare-board" type="button" *ngIf="canManage && isToday" [disabled]="busy" (click)="prepare()">{{ 'Prepare today\\'s board' | translate }}</button>
        <button class="btn btn-sm btn-outline-primary auto-assign" type="button" *ngIf="canManage && isToday" [disabled]="busy || !hasUnassigned" (click)="autoAssign()">{{ 'Share out the work' | translate }}</button>
        <span class="flex gap-2" style="margin-left:auto;">
          <button class="btn btn-sm btn-outline-secondary hk-export-xlsx" type="button" (click)="exportBoard('xlsx')">{{ 'Excel' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary hk-export-pdf" type="button" (click)="exportBoard('pdf')">{{ 'PDF' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary hk-export-csv" type="button" (click)="exportBoard('csv')">{{ 'CSV' | translate }}</button>
        </span>
      </div>

      <div class="stat-grid mb-3" *ngIf="b">
        <div class="stat-tile"><div class="stat-label">{{ 'Rooms to clean' | translate }}</div><div class="stat-value tile-total">{{ b.summary.total }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} departure · {p1} stay-over' | translate: { p0: (b.summary.departures), p1: (b.summary.stayovers) } }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Open' | translate }}</div><div class="stat-value tile-open">{{ b.summary.open }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'In progress' | translate }}</div><div class="stat-value tile-progress">{{ b.summary.inProgress }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Done' | translate }}</div><div class="stat-value tile-done">{{ b.summary.done }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} inspected' | translate: { p0: (b.summary.inspected) } }}</div></div>
        <div class="stat-tile" [class.warn]="b.summary.failed > 0"><div class="stat-label">{{ 'Failed inspection' | translate }}</div><div class="stat-value tile-failed">{{ b.summary.failed }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Skipped' | translate }}</div><div class="stat-value tile-skipped">{{ b.summary.skipped }}</div></div>
      </div>
      <div class="flex gap-2 mb-3 workload" style="flex-wrap:wrap;" *ngIf="b && b.load.length">
        <span class="badge badge-info load-chip" *ngFor="let l of b.load">{{ '{p0}: {p1}/{p2} rooms' | translate: { p0: (l.staffName), p1: (l.done), p2: (l.tasks) } }}</span>
      </div>

      <table class="table-sm board-table" *ngIf="b && b.tasks.length">
        <thead><tr><th>{{ 'Room' | translate }}</th><th>{{ 'Task' | translate }}</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Housekeeper' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Time' | translate }}</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let t of b.tasks">
            <tr class="task-row" [class.warn-row]="t.inspectionResult === 'FAIL' && t.status !== 'DONE'">
              <td><strong>{{ t.roomNumber }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ t.roomTypeName }}<span *ngIf="t.floor"> {{ '· floor {p0}' | translate: { p0: (t.floor) } }}</span></div></td>
              <td>
                <span class="badge" [ngClass]="t.kind === 'DEPARTURE' ? 'badge-danger' : 'badge-info'">{{ t.kind === 'DEPARTURE' ? ('Departure' | translate) : ('Stay-over' | translate) }}</span>
                <span class="badge badge-warning" style="margin-left:0.25rem;" *ngIf="t.arrivalToday">{{ 'Arrival today' | translate }}</span>
                <span class="badge badge-secondary dnd-badge" style="margin-left:0.25rem;" *ngIf="t.dnd">{{ 'Do not disturb' | translate }}</span>
              </td>
              <td>
                <span *ngIf="t.guestName">{{ t.guestName }}</span><span class="text-muted" *ngIf="!t.guestName">{{ 'Vacant' | translate }}</span>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.departs && t.occupied">{{ 'leaves {p0}' | translate: { p0: (t.departs) } }}</div>
              </td>
              <td>
                <select class="form-control assign-select" style="min-width:120px;" *ngIf="canManage && isToday && t.status !== 'DONE'" [ngModel]="t.staffId" (ngModelChange)="assign(t, $event)" name="as{{ t.id }}">
                  <option [ngValue]="null">—</option><option *ngFor="let s of activeStaff" [ngValue]="s.id">{{ s.name }}</option>
                </select>
                <span *ngIf="!(canManage && isToday && t.status !== 'DONE')">{{ t.staffName || '—' }}</span>
              </td>
              <td>
                <span class="badge" [ngClass]="statusClass(t.status)">{{ statusLabel(t.status) }}</span>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.notes">{{ t.notes }}</div>
                <div style="font-size:0.8rem;" *ngIf="t.inspectionResult"><span [class]="t.inspectionResult === 'PASS' ? 'text-success' : 'text-danger'">{{ 'Inspection {p0}' | translate: { p0: (t.inspectionResult === 'PASS' ? 'passed' : 'failed') } }}</span><span *ngIf="t.attempts > 1"> {{ '· attempt {p0}' | translate: { p0: (t.attempts) } }}</span></div>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.inspectionNote">{{ t.inspectionNote }}</div>
              </td>
              <td>{{ t.minutes === null ? '' : t.minutes + ' min' }}</td>
              <td class="flex gap-1" style="flex-wrap:wrap;" *ngIf="isToday">
                <button class="btn btn-sm btn-outline-primary start-task" type="button" *ngIf="t.status === 'OPEN'" [disabled]="busy" (click)="act(t, 'start')">{{ 'Start' | translate }}</button>
                <button class="btn btn-sm btn-success finish-task" type="button" *ngIf="t.status === 'OPEN' || t.status === 'IN_PROGRESS'" [disabled]="busy" (click)="act(t, 'finish')">{{ 'Done' | translate }}</button>
                <button class="btn btn-sm btn-outline-primary inspect-task" type="button" *ngIf="t.status === 'DONE' && t.inspectionResult !== 'PASS'" (click)="openInspect(t)">{{ 'Inspect' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary skip-task" type="button" *ngIf="t.status === 'OPEN' || t.status === 'IN_PROGRESS'" [disabled]="busy" (click)="skip(t)">{{ 'Skip' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary reopen-task" type="button" *ngIf="t.status === 'SKIPPED' && !t.dnd" [disabled]="busy" (click)="act(t, 'reopen')">{{ 'Reopen' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary dnd-toggle" type="button" *ngIf="t.occupied && t.kind === 'STAYOVER'" [disabled]="busy" (click)="dnd(t)">{{ t.dnd ? ('Clear DND' | translate) : ('Do not disturb' | translate) }}</button>
              </td>
              <td *ngIf="!isToday"></td>
            </tr>
            <tr *ngIf="inspecting === t.id" class="inspect-row">
              <td colspan="7">
                <div class="inspect-panel" style="padding:0.5rem 0.25rem;">
                  <strong>{{ 'Inspect room {p0}' | translate: { p0: (t.roomNumber) } }}</strong>
                  <div *ngFor="let c of checks; let i = index" class="check-line" style="margin:0.25rem 0;">
                    <label><input type="checkbox" [(ngModel)]="c.passed" name="ck{{ i }}" /> {{ c.label }}</label>
                  </div>
                  <p class="text-muted" *ngIf="!checks.length">{{ 'No checklist set up — pass or fail the room as a whole.' | translate }}</p>
                  <input class="form-control inspect-note" style="max-width:420px; margin:0.4rem 0;" name="inote" [(ngModel)]="inspectNote" [placeholder]="'Note (what to fix)' | translate" />
                  <button class="btn btn-sm btn-success submit-inspect" type="button" [disabled]="busy" (click)="submitInspect(t)">{{ allPassed ? ('Pass the room' | translate) : ('Send back for re-cleaning' | translate) }}</button>
                  <button class="btn btn-sm btn-link" type="button" (click)="inspecting = null">{{ 'Cancel' | translate }}</button>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="b && !b.tasks.length">{{ isToday ? ('Nothing on the board yet. Use "Prepare today’s board" to list the rooms to clean.' | translate) : ('No cleaning was recorded for this day.' | translate) }}</p>
    </div>
  `,
})
export class HkBoardComponent implements OnInit {
  b: HkBoard | null = null;
  date = today();
  todayStr = today();
  staff: HkStaff[] = [];
  checklist: ChecklistItem[] = [];
  checks: { label: string; passed: boolean }[] = [];
  inspecting: number | null = null;
  inspectNote = '';
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  get isToday(): boolean {
    return this.date === this.todayStr;
  }

  get activeStaff(): HkStaff[] {
    return this.staff.filter((s) => s.active);
  }

  get hasUnassigned(): boolean {
    return !!this.b && this.b.tasks.some((t) => !t.staffId && (t.status === 'OPEN' || t.status === 'IN_PROGRESS'));
  }

  get allPassed(): boolean {
    return this.checks.every((c) => c.passed);
  }

  ngOnInit(): void {
    this.api.hkStaff().subscribe((s) => (this.staff = s));
    this.api.hkChecklist(false).subscribe((c) => (this.checklist = c.filter((x) => x.active)));
    this.load();
  }

  load(): void {
    this.api.hkBoard(this.date).subscribe({ next: (b) => (this.b = b), error: (e) => (this.error = errMsg(e, 'Failed to load the board')) });
  }

  statusClass(s: string): string {
    return s === 'DONE' ? 'badge-success' : s === 'IN_PROGRESS' ? 'badge-warning' : s === 'SKIPPED' ? 'badge-secondary' : 'badge-danger';
  }

  statusLabel(s: string): string {
    return s === 'IN_PROGRESS' ? 'Cleaning' : s === 'OPEN' ? 'To do' : s === 'DONE' ? 'Cleaned' : 'Skipped';
  }

  private apply(task: HkTask): void {
    if (!this.b) return;
    this.b = { ...this.b, tasks: this.b.tasks.map((t) => (t.id === task.id ? task : t)) };
    this.load();
  }

  private fail(e: unknown, fallback: string): void {
    this.busy = false;
    this.message = null;
    this.error = errMsg(e, fallback);
  }

  prepare(): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.hkGenerate().subscribe({ next: (b) => { this.busy = false; this.b = b; this.message = T('Board ready: {p0} room{p1} to clean.', { p0: b.summary.total, p1: b.summary.total === 1 ? '' : 's' }); }, error: (e) => this.fail(e, T('Could not prepare the board')) });
  }

  autoAssign(): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.hkAutoAssign().subscribe({ next: (b) => { this.busy = false; this.b = b; this.message = T('Work shared out between the housekeepers.'); }, error: (e) => this.fail(e, T('Could not share out the work')) });
  }

  assign(t: HkTask, staffId: number | null): void {
    this.error = this.message = null;
    this.api.hkAssign(t.id, staffId).subscribe({ next: (r) => this.apply(r), error: (e) => this.fail(e, T('Could not assign the room')) });
  }

  act(t: HkTask, action: 'start' | 'finish' | 'reopen'): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.hkAct(t.id, action).subscribe({ next: (r) => { this.busy = false; this.apply(r); }, error: (e) => this.fail(e, T('Could not update the room')) });
  }

  skip(t: HkTask): void {
    const reason = prompt(`Why skip room ${t.roomNumber}?`, '');
    if (reason === null) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.hkAct(t.id, 'skip', { reason }).subscribe({ next: (r) => { this.busy = false; this.apply(r); }, error: (e) => this.fail(e, T('Could not skip the room')) });
  }

  dnd(t: HkTask): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.hkDnd(t.roomId, !t.dnd).subscribe({ next: (b) => { this.busy = false; this.b = b; }, error: (e) => this.fail(e, T('Could not change do-not-disturb')) });
  }

  openInspect(t: HkTask): void {
    this.inspecting = t.id;
    this.inspectNote = '';
    this.checks = this.checklist.map((c) => ({ label: c.label, passed: true }));
  }

  submitInspect(t: HkTask): void {
    this.busy = true;
    this.error = this.message = null;
    const passed = this.checks.length ? null : true;
    this.api.hkInspect(t.id, this.checks, this.inspectNote || null, passed).subscribe({
      next: (r) => {
        this.busy = false;
        this.inspecting = null;
        this.message = r.inspectionResult === 'PASS' ? T('Room {p0} passed inspection.', { p0: t.roomNumber }) : T('Room {p0} sent back for re-cleaning.', { p0: t.roomNumber });
        this.apply(r);
      },
      error: (e) => this.fail(e, T('Could not record the inspection')),
    });
  }

  exportBoard(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.api.hkBoardExport(this.date, format).subscribe({ next: (blob) => downloadBlob(blob, `housekeeping-${this.date}.${format}`), error: () => (this.error = T('The export failed.')) });
  }
}
