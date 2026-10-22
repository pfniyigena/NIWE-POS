import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ProjectSummary, StaffOption, TimeApiService, TimeEntry } from '../../core/api/time-api.service';
import { addDays, formatMinutes, parseDuration, today } from '../../core/time-util';

/** Time Tracking > Approvals: time waiting for a decision, and everyone's time over a period. */
@Component({
  selector: 'app-time-approvals',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Approvals and team time</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="SUBMITTED">Waiting for approval</option>
          <option value="">All statuses</option>
          <option value="DRAFT">Not yet submitted</option><option value="APPROVED">Approved</option>
          <option value="REJECTED">Sent back</option><option value="INVOICED">Invoiced</option>
        </select>
        <select class="form-select" style="width:auto;" name="us" [(ngModel)]="userId" (ngModelChange)="load()">
          <option [ngValue]="null">Everyone</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.fullName }}</option>
        </select>
        <select class="form-select" style="width:auto; max-width:14rem;" name="pr" [(ngModel)]="projectId" (ngModelChange)="load()">
          <option [ngValue]="null">All projects</option><option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <ng-container *ngIf="status !== 'SUBMITTED'">
          <app-date-picker name="f" [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
          <span>to</span>
          <app-date-picker name="t" [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        </ng-container>
      </div>

      <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;" *ngIf="selectedIds().length">
        <strong>{{ selectedIds().length }} selected</strong>
        <button class="btn btn-success btn-sm" type="button" (click)="approve()">Approve</button>
        <input class="form-control" style="width:16rem;" placeholder="Reason for sending back" name="rr" [(ngModel)]="reason" />
        <button class="btn btn-outline-danger btn-sm" type="button" (click)="reject()">Send back</button>
      </div>

      <table class="table-sm">
        <thead><tr>
          <th style="width:2rem;"><input type="checkbox" [checked]="allChecked()" (change)="toggleAll($any($event.target).checked)" /></th>
          <th>Date</th><th>Person</th><th>Project</th><th>What</th><th class="text-end">Time</th><th>Billable</th><th class="text-end">Rate</th><th class="text-end">Amount</th><th>Status</th><th></th>
        </tr></thead>
        <tbody>
          <ng-container *ngFor="let e of entries">
            <tr>
              <td><input type="checkbox" [disabled]="e.status === 'INVOICED' || e.status === 'DRAFT'" [(ngModel)]="picked[e.id]" [name]="'p' + e.id" /></td>
              <td>{{ e.workDate }}</td>
              <td>{{ e.userName }}</td>
              <td>{{ e.projectName }}<span class="text-muted" *ngIf="e.taskName"> · {{ e.taskName }}</span></td>
              <td>{{ e.description || '—' }}<div style="color:#c0392b; font-size:0.8rem;" *ngIf="e.rejectReason">Sent back: {{ e.rejectReason }}</div></td>
              <td class="text-end">{{ fmt(e.minutes) }}</td>
              <td>{{ e.billable ? 'Yes' : 'No' }}</td>
              <td class="text-end">{{ e.hourlyRate | number: '1.0-2' }}</td>
              <td class="text-end">{{ e.billable ? (e.amount | number: '1.0-2') : '—' }}</td>
              <td><span class="badge" [class.badge-secondary]="e.status === 'DRAFT'" [class.badge-warning]="e.status === 'SUBMITTED'"
                  [class.badge-success]="e.status === 'APPROVED' || e.status === 'INVOICED'" [class.badge-danger]="e.status === 'REJECTED'">{{ e.status }}</span>
                <a *ngIf="e.invoiceId" [routerLink]="['/admin/invoices', e.invoiceId]" style="margin-left:0.3rem;">{{ e.invoiceNumber }}</a></td>
              <td><button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="e.status !== 'INVOICED'" (click)="startEdit(e)">Correct</button></td>
            </tr>
            <tr *ngIf="editing === e.id">
              <td colspan="11" style="background:rgba(0,0,0,0.03);">
                <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                  <input class="form-control" style="width:7rem;" name="eh" [(ngModel)]="editDuration" placeholder="1:30" />
                  <input class="form-control" style="width:20rem;" name="ed" [(ngModel)]="editDescription" placeholder="Description" />
                  <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="eb" [(ngModel)]="editBillable" /> Billable</label>
                  <button class="btn btn-primary btn-sm" type="button" (click)="saveEdit(e)">Save</button>
                  <button class="btn btn-outline-secondary btn-sm" type="button" (click)="editing = null">Cancel</button>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!entries.length">{{ status === 'SUBMITTED' ? 'Nothing is waiting for approval.' : 'No time found for these filters.' }}</p>
      <p class="text-muted" *ngIf="entries.length">Total: {{ totalHours() | number: '1.0-2' }} h</p>
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
