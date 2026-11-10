import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { MissingList, ProjectSummary, StaffOption, TimeApiService, TimeEntry } from '../../core/api/time-api.service';
import { addDays, formatMinutes, mondayOf, parseDuration, today } from '../../core/time-util';

/** Time Tracking > Approvals: time waiting for a decision, and everyone's time over a period. */
@Component({
  selector: 'app-time-approvals',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'Approvals and team time' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-2 mb-3" role="tablist">
        <button type="button" class="btn btn-sm" [class.btn-primary]="!missingMode" [class.btn-outline-secondary]="missingMode" (click)="setMissing(false)">{{ 'Approvals and team time' | translate }}</button>
        <button type="button" id="tab-missing" class="btn btn-sm" [class.btn-primary]="missingMode" [class.btn-outline-secondary]="!missingMode" (click)="setMissing(true)">{{ 'Not submitted' | translate }}</button>
      </div>

      <ng-container *ngIf="missingMode">
        <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary btn-sm" type="button" (click)="shiftWeek(-7)">{{ '‹ Earlier' | translate }}</button>
          <strong>{{ 'Week of {p0}' | translate: { p0: (missingWeek) } }}</strong>
          <button class="btn btn-outline-secondary btn-sm" type="button" (click)="shiftWeek(7)">{{ 'Later ›' | translate }}</button>
          <span class="spacer"></span>
          <button class="btn btn-primary btn-sm" type="button" id="remind-all" [disabled]="!missing?.rows?.length || sending" (click)="remind(null)">{{ 'Send reminder to everyone listed' | translate }}</button>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'People who normally log time but have not handed in everything for this week (Monday to Sunday).' | translate }}</div>
        <table class="table-sm" *ngIf="missing?.rows?.length">
          <thead><tr><th>{{ 'Person' | translate }}</th><th>{{ 'Where they are' | translate }}</th><th class="text-end">{{ 'Logged' | translate }}</th><th class="text-end">{{ 'Not handed in' | translate }}</th><th class="text-end">{{ 'Waiting' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let r of missing!.rows">
            <td>{{ r.userName }}<span class="text-muted" *ngIf="!r.hasEmail" style="font-size:0.75rem;"> {{ '· no e-mail address' | translate }}</span></td>
            <td><span class="badge" [class.badge-danger]="r.status === 'NOTHING'" [class.badge-warning]="r.status !== 'NOTHING'">{{ statusLabel(r.status) }}</span></td>
            <td class="text-end">{{ '{p0} h' | translate: { p0: (r.hoursLogged | number: '1.0-2') } }}</td><td class="text-end">{{ '{p0} h' | translate: { p0: (r.hoursNotSubmitted | number: '1.0-2') } }}</td><td class="text-end">{{ '{p0} h' | translate: { p0: (r.hoursSubmitted | number: '1.0-2') } }}</td>
            <td><button class="btn btn-sm btn-outline-primary" type="button" [disabled]="!r.hasEmail || sending" (click)="remind([r.userId])">{{ 'Remind' | translate }}</button></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="missing && !missing.rows.length">{{ 'Everyone who normally logs time has handed in this week.' | translate }}</p>
      </ng-container>

      <ng-container *ngIf="!missingMode">
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="SUBMITTED">{{ 'Waiting for approval' | translate }}</option>
          <option value="">{{ 'All statuses' | translate }}</option>
          <option value="DRAFT">{{ 'Not yet submitted' | translate }}</option><option value="APPROVED">{{ 'Approved' | translate }}</option>
          <option value="REJECTED">{{ 'Sent back' | translate }}</option><option value="INVOICED">{{ 'Invoiced' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="us" [(ngModel)]="userId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Everyone' | translate }}</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.fullName }}</option>
        </select>
        <select class="form-select" style="width:auto; max-width:14rem;" name="pr" [(ngModel)]="projectId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'All projects' | translate }}</option><option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <ng-container *ngIf="status !== 'SUBMITTED'">
          <app-date-picker name="f" [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
          <span>{{ 'to' | translate }}</span>
          <app-date-picker name="t" [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        </ng-container>
      </div>

      <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;" *ngIf="selectedIds().length">
        <strong>{{ '{p0} selected' | translate: { p0: (selectedIds().length) } }}</strong>
        <button class="btn btn-success btn-sm" type="button" (click)="approve()">{{ 'Approve' | translate }}</button>
        <input class="form-control" style="width:16rem;" [placeholder]="'Reason for sending back' | translate" name="rr" [(ngModel)]="reason" />
        <button class="btn btn-outline-danger btn-sm" type="button" (click)="reject()">{{ 'Send back' | translate }}</button>
      </div>

      <table class="table-sm">
        <thead><tr>
          <th style="width:2rem;"><input type="checkbox" [checked]="allChecked()" (change)="toggleAll($any($event.target).checked)" /></th>
          <th>{{ 'Date' | translate }}</th><th>{{ 'Person' | translate }}</th><th>{{ 'Project' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Time' | translate }}</th><th>{{ 'Billable' | translate }}</th><th class="text-end">{{ 'Rate' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th>
        </tr></thead>
        <tbody>
          <ng-container *ngFor="let e of entries">
            <tr>
              <td><input type="checkbox" [disabled]="e.status === 'INVOICED' || e.status === 'DRAFT'" [(ngModel)]="picked[e.id]" [name]="'p' + e.id" /></td>
              <td>{{ e.workDate }}</td>
              <td>{{ e.userName }}</td>
              <td>{{ e.projectName }}<span class="text-muted" *ngIf="e.taskName"> · {{ e.taskName }}</span></td>
              <td>{{ e.description || '—' }}<div style="color:#c0392b; font-size:0.8rem;" *ngIf="e.rejectReason">{{ 'Sent back: {p0}' | translate: { p0: (e.rejectReason) } }}</div></td>
              <td class="text-end">{{ fmt(e.minutes) }}</td>
              <td>{{ e.billable ? ('Yes' | translate) : ('No' | translate) }}</td>
              <td class="text-end">{{ e.hourlyRate | number: '1.0-2' }}</td>
              <td class="text-end">{{ e.billable ? (e.amount | number: '1.0-2') : '—' }}</td>
              <td><span class="badge" [class.badge-secondary]="e.status === 'DRAFT'" [class.badge-warning]="e.status === 'SUBMITTED'"
                  [class.badge-success]="e.status === 'APPROVED' || e.status === 'INVOICED'" [class.badge-danger]="e.status === 'REJECTED'">{{ e.status }}</span>
                <a *ngIf="e.invoiceId" [routerLink]="['/admin/invoices', e.invoiceId]" style="margin-left:0.3rem;">{{ e.invoiceNumber }}</a></td>
              <td><button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="e.status !== 'INVOICED'" (click)="startEdit(e)">{{ 'Correct' | translate }}</button></td>
            </tr>
            <tr *ngIf="editing === e.id">
              <td colspan="11" style="background:rgba(0,0,0,0.03);">
                <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                  <input class="form-control" style="width:7rem;" name="eh" [(ngModel)]="editDuration" placeholder="1:30" />
                  <input class="form-control" style="width:20rem;" name="ed" [(ngModel)]="editDescription" [placeholder]="'Description' | translate" />
                  <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="eb" [(ngModel)]="editBillable" /> {{ 'Billable' | translate }}</label>
                  <button class="btn btn-primary btn-sm" type="button" (click)="saveEdit(e)">{{ 'Save' | translate }}</button>
                  <button class="btn btn-outline-secondary btn-sm" type="button" (click)="editing = null">{{ 'Cancel' | translate }}</button>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!entries.length">{{ status === 'SUBMITTED' ? ('Nothing is waiting for approval.' | translate) : ('No time found for these filters.' | translate) }}</p>
      <p class="text-muted" *ngIf="entries.length">{{ 'Total: {p0} h' | translate: { p0: (totalHours() | number: '1.0-2') } }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class TimeApprovalsComponent implements OnInit {
  entries: TimeEntry[] = [];
  staff: StaffOption[] = [];
  projects: ProjectSummary[] = [];
  status = 'SUBMITTED';
  userId: number | null = null;
  projectId: number | null = null;
  from = addDays(today(), -30);
  to = today();
  picked: Record<number, boolean> = {};
  reason = '';
  error: string | null = null;
  notice: string | null = null;
  editing: number | null = null;
  editDuration = '';
  editDescription = '';
  editBillable = true;
  missingMode = false;
  missingWeek = addDays(mondayOf(today()), -7);
  missing: MissingList | null = null;
  sending = false;

  statusLabel(s: string): string {
    return s === 'NOTHING' ? 'Nothing logged' : s === 'SENT_BACK' ? 'Sent back' : 'Drafts not handed in';
  }

  setMissing(on: boolean): void {
    this.missingMode = on;
    this.error = null;
    this.notice = null;
    if (on) this.loadMissing();
  }

  shiftWeek(days: number): void {
    this.missingWeek = addDays(this.missingWeek, days);
    this.loadMissing();
  }

  loadMissing(): void {
    this.missing = null;
    this.api.missing(this.missingWeek).subscribe({ next: (m) => (this.missing = m), error: (e) => (this.error = e?.error?.message ?? 'Could not load the list') });
  }

  remind(userIds: number[] | null): void {
    this.error = null;
    this.notice = null;
    this.sending = true;
    this.api.remind(this.missingWeek, userIds).subscribe({
      next: (r) => {
        this.sending = false;
        const parts = [`${r.sent} reminder${r.sent === 1 ? '' : 's'} sent`];
        if (r.noEmail) parts.push(`${r.noEmail} without an e-mail address`);
        if (r.failed) parts.push(`${r.failed} failed`);
        this.notice = parts.join(', ') + '.' + (r.notes?.length ? ' ' + r.notes.join(' ') : '');
      },
      error: (e) => { this.sending = false; this.error = e?.error?.message ?? 'Could not send the reminders'; },
    });
  }

  constructor(private api: TimeApiService) {}

  ngOnInit(): void {
    this.api.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    this.api.projects(undefined, true).subscribe({ next: (p) => (this.projects = p), error: () => {} });
    this.load();
  }

  load(): void {
    this.picked = {};
    this.editing = null;
    const call = this.status === 'SUBMITTED' && !this.userId && !this.projectId
      ? this.api.approvals()
      : this.api.entries({ status: this.status, userId: this.userId, projectId: this.projectId, from: this.status === 'SUBMITTED' ? '2000-01-01' : this.from, to: this.status === 'SUBMITTED' ? today() : this.to });
    call.subscribe({ next: (e) => (this.entries = e), error: (e) => (this.error = e?.error?.message ?? 'Could not load the time') });
  }

  fmt(m: number): string {
    return formatMinutes(m);
  }

  totalHours(): number {
    return this.entries.reduce((a, e) => a + e.hours, 0);
  }

  selectedIds(): number[] {
    return Object.keys(this.picked).filter((k) => this.picked[+k]).map(Number);
  }

  private pickable(): TimeEntry[] {
    return this.entries.filter((e) => e.status === 'SUBMITTED' || e.status === 'APPROVED' || e.status === 'REJECTED');
  }

  allChecked(): boolean {
    const p = this.pickable();
    return p.length > 0 && p.every((e) => this.picked[e.id]);
  }

  toggleAll(on: boolean): void {
    this.picked = {};
    if (on) this.pickable().forEach((e) => (this.picked[e.id] = true));
  }

  approve(): void {
    this.error = null;
    this.api.approve(this.selectedIds()).subscribe({
      next: (r) => {
        this.notice = `${r.approved} approved.`;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not approve'),
    });
  }

  reject(): void {
    this.error = null;
    if (!this.reason.trim()) {
      this.error = 'Say why the time is sent back.';
      return;
    }
    this.api.reject(this.selectedIds(), this.reason).subscribe({
      next: (r) => {
        this.notice = `${r.rejected} sent back.`;
        this.reason = '';
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not send back'),
    });
  }

  startEdit(e: TimeEntry): void {
    this.editing = e.id;
    this.editDuration = formatMinutes(e.minutes);
    this.editDescription = e.description ?? '';
    this.editBillable = e.billable;
  }

  saveEdit(e: TimeEntry): void {
    const minutes = parseDuration(this.editDuration);
    if (minutes == null || minutes < 1) {
      this.error = 'Enter the time as 1:30, 1.5, 90m or 2h.';
      return;
    }
    this.api.correct(e.id, { projectId: e.projectId, taskId: e.taskId, workDate: e.workDate, minutes, description: this.editDescription, billable: this.editBillable }).subscribe({
      next: () => {
        this.notice = 'Corrected.';
        this.load();
      },
      error: (x) => (this.error = x?.error?.message ?? 'Could not correct'),
    });
  }
}
