import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Employee, HrApiService, LeaveBalance, LeaveRequest, LeaveStatus, LeaveType } from '../../core/api/hr-api.service';

/** Attendance & Leaves > Leave: requests (with approve / reject), and the balance of each employee. */
@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Leave</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/leave-settings">Leave settings</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-1 mb-3">
        <button class="btn btn-sm" [class.btn-primary]="tab === 'requests'" [class.btn-outline-primary]="tab !== 'requests'" type="button" (click)="tab = 'requests'">Requests</button>
        <button class="btn btn-sm" [class.btn-primary]="tab === 'balances'" [class.btn-outline-primary]="tab !== 'balances'" type="button" (click)="tab = 'balances'; loadBalances()">Balances</button>
      </div>

      <ng-container *ngIf="tab === 'requests'">
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="request()">
          <strong class="mb-2">New leave request</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Employee</label>
              <select class="form-select" name="re" [(ngModel)]="form.employeeId" required>
                <option [ngValue]="null" disabled>Choose…</option>
                <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">Leave type</label>
              <select class="form-select" name="rt" [(ngModel)]="form.leaveTypeId" required>
                <option [ngValue]="0" disabled>Choose…</option>
                <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}{{ t.paid ? '' : ' (unpaid)' }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">From</label><app-date-picker name="rf" [(ngModel)]="form.startDate" (ngModelChange)="countDays()" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">To</label><app-date-picker name="rto" [(ngModel)]="form.endDate" (ngModelChange)="countDays()" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">Reason</label><input class="form-control" name="rr" [(ngModel)]="form.reason" /></div>
            <button class="btn btn-primary" type="submit">Submit</button>
          </div>
          <div class="text-muted" *ngIf="workingDays !== null">{{ workingDays }} working day(s) (weekends and public holidays are not counted).</div>
        </form>

        <div class="flex gap-2 mb-2 align-center">
          <select class="form-select" style="width:auto;" name="fs" [(ngModel)]="status" (ngModelChange)="load()">
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option><option value="CANCELLED">Cancelled</option>
          </select>
          <select class="form-select" style="width:auto;" name="fe" [(ngModel)]="filterEmployeeId" (ngModelChange)="load()">
            <option [ngValue]="null">All employees</option>
            <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
          </select>
        </div>
        <table class="table-sm">
          <thead><tr><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th>Status</th><th>Decision</th><th></th></tr></thead>
          <tbody><tr *ngFor="let l of leaves">
            <td>{{ l.employeeName }}</td><td>{{ l.leaveTypeName }}</td><td>{{ l.startDate }}</td><td>{{ l.endDate }}</td><td>{{ l.days }}</td><td>{{ l.reason || '' }}</td>
            <td><span class="badge" [class.badge-warning]="l.status === 'PENDING'" [class.badge-success]="l.status === 'APPROVED'" [class.badge-danger]="l.status === 'REJECTED'" [class.badge-secondary]="l.status === 'CANCELLED'">{{ l.status }}</span></td>
            <td>{{ l.decidedBy ? l.decidedBy : '' }}{{ l.decisionNote ? ' — ' + l.decisionNote : '' }}</td>
            <td class="flex gap-1">
              <button class="btn btn-sm btn-success" type="button" *ngIf="l.status === 'PENDING'" (click)="decide(l, 'approve')">Approve</button>
              <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="l.status === 'PENDING'" (click)="decide(l, 'reject')">Reject</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="l.status === 'PENDING' || l.status === 'APPROVED'" (click)="decide(l, 'cancel')">Cancel</button>
            </td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!leaves.length">No leave requests.</p>
      </ng-container>

      <ng-container *ngIf="tab === 'balances'">
        <div class="flex gap-2 mb-2 align-center">
          <label>Year</label>
          <input class="form-control" type="number" style="width:6rem;" name="by" [(ngModel)]="year" (ngModelChange)="loadBalances()" />
        </div>
        <table class="table-sm">
          <thead><tr><th>Employee</th><th>Leave type</th><th>Entitled</th><th>Used</th><th>Pending</th><th>Remaining</th></tr></thead>
          <tbody><tr *ngFor="let b of balances">
            <td>{{ b.employeeName }}</td><td>{{ b.leaveTypeName }}</td><td>{{ b.unlimited ? 'No limit' : b.entitled }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted">Entitlements are the full yearly days of each leave type (not prorated for people hired during the year).</p>
      </ng-container>
    </app-shell>
  `,
})
export class LeavesComponent implements OnInit {
  tab: 'requests' | 'balances' = 'requests';
  leaves: LeaveRequest[] = [];
  balances: LeaveBalance[] = [];
  employees: Employee[] = [];
  types: LeaveType[] = [];
  status: LeaveStatus | '' = '';
  filterEmployeeId: number | null = null;
  year = new Date().getFullYear();
  workingDays: number | null = null;
  error: string | null = null;
  form: { employeeId: number | null; leaveTypeId: number; startDate: string; endDate: string; reason: string | null } = {
    employeeId: null, leaveTypeId: 0, startDate: new Date().toISOString().slice(0, 10), endDate: new Date().toISOString().slice(0, 10), reason: null,
  };

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.api.leaveTypes().subscribe({ next: (t) => (this.types = t.filter((x) => x.active)), error: () => {} });
    this.load();
    this.countDays();
  }

  load(): void {
    this.api.leaves({ status: this.status, employeeId: this.filterEmployeeId }).subscribe({
      next: (l) => {
        this.leaves = l;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load leave requests'),
    });
  }

  loadBalances(): void {
    this.api.leaveBalances(this.year).subscribe({ next: (b) => (this.balances = b), error: (e) => (this.error = e?.error?.message ?? 'Failed to load balances') });
  }

  countDays(): void {
    if (!this.form.startDate || !this.form.endDate || this.form.endDate < this.form.startDate) {
      this.workingDays = null;
      return;
    }
    this.api.leaveWorkingDays(this.form.startDate, this.form.endDate).subscribe({ next: (n) => (this.workingDays = n), error: () => (this.workingDays = null) });
  }

  request(): void {
    if (!this.form.employeeId || !this.form.leaveTypeId) {
      this.error = 'Choose the employee and the leave type.';
      return;
    }
    this.api.requestLeave({ ...this.form, reason: this.form.reason || null }).subscribe({
      next: () => {
        this.error = null;
        this.form.reason = null;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not submit the request'),
    });
  }

  decide(l: LeaveRequest, action: 'approve' | 'reject' | 'cancel'): void {
    let note: string | null = null;
    if (action !== 'approve') {
      note = prompt(action === 'reject' ? 'Reason for rejecting (optional):' : 'Reason for cancelling (optional):') ?? null;
    }
    this.api.decideLeave(l.id, action, note).subscribe({
      next: () => {
        this.error = null;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not update the request'),
    });
  }
}
