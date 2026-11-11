import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Employee, HrApiService, LeaveBalance, LeaveRequest, LeaveType } from '../../core/api/hr-api.service';

import { T } from '../../core/i18n/t';
/** Attendance & Leaves > Leave: requests (with approve / reject), and the balance of each employee. */
@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Leave' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/hr/leave-calendar">{{ 'Team calendar' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/hr/leave-settings">{{ 'Leave settings' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex gap-1 mb-3">
        <button class="btn btn-sm" [class.btn-primary]="tab === 'requests'" [class.btn-outline-primary]="tab !== 'requests'" type="button" (click)="tab = 'requests'">{{ 'Requests' | translate }}</button>
        <button class="btn btn-sm" [class.btn-primary]="tab === 'balances'" [class.btn-outline-primary]="tab !== 'balances'" type="button" (click)="tab = 'balances'; loadBalances()">{{ 'Balances' | translate }}</button>
      </div>

      <ng-container *ngIf="tab === 'requests'">
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="request()">
          <strong class="mb-2">{{ 'New leave request' | translate }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Employee' | translate }}</label>
              <select class="form-select" name="re" [(ngModel)]="form.employeeId" required>
                <option [ngValue]="null" disabled>{{ 'Choose…' | translate }}</option>
                <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Leave type' | translate }}</label>
              <select class="form-select" name="rt" [(ngModel)]="form.leaveTypeId" required>
                <option [ngValue]="0" disabled>{{ 'Choose…' | translate }}</option>
                <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}{{ t.paid ? '' : ' (unpaid)' }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'From' | translate }}</label><app-date-picker name="rf" [(ngModel)]="form.startDate" (ngModelChange)="countDays()" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'To' | translate }}</label><app-date-picker name="rto" [(ngModel)]="form.endDate" (ngModelChange)="countDays()" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="rr" [(ngModel)]="form.reason" /></div>
            <button class="btn btn-primary" type="submit">{{ 'Submit' | translate }}</button>
          </div>
          <div class="flex gap-3 mt-2" style="flex-wrap:wrap;">
            <label><input type="checkbox" class="half-start" name="hs" [(ngModel)]="form.startHalfDay" (ngModelChange)="countDays()" /> {{ 'First day is a half day (afternoon only)' | translate }}</label>
            <label><input type="checkbox" class="half-end" name="he" [(ngModel)]="form.endHalfDay" (ngModelChange)="countDays()" /> {{ 'Last day is a half day (morning only)' | translate }}</label>
          </div>
          <div class="text-muted day-count" *ngIf="workingDays !== null">{{ '{p0} working day(s) (weekends and public holidays are not counted).' | translate: { p0: (workingDays) } }}</div>
        </form>

        <app-list-filter exportKind="leave-requests" exportPath="/hr/leaves/export" [placeholder]="'Search employee, leave type, reason…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" [exportExtra]="extra" (cleared)="filterEmployeeId = null; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
        <div class="flex gap-2 mb-2 align-center">
          <select class="form-select emp-filter" style="width:auto;" name="fe" [(ngModel)]="filterEmployeeId" (ngModelChange)="employeeChanged()">
            <option [ngValue]="null">{{ 'All employees' | translate }}</option>
            <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
          </select>
          <span class="text-muted" style="font-size:0.8rem;">{{ 'The dates narrow the list only when both are set (requests overlapping that period).' | translate }}</span>
        </div>
        <table class="table-sm">
          <thead><tr><th>{{ 'Employee' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Days' | translate }}</th><th>{{ 'Reason' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Decision' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let l of list.rows">
            <td>{{ l.employeeName }}</td><td>{{ l.leaveTypeName }}</td><td>{{ l.startDate }}</td><td>{{ l.endDate }}</td><td>{{ l.days }}<span class="text-muted" *ngIf="l.startHalf || l.endHalf" [title]="'Includes a half day' | translate"> ½</span></td><td>{{ l.reason || '' }}</td>
            <td><span class="badge" [class.badge-warning]="l.status === 'PENDING'" [class.badge-success]="l.status === 'APPROVED'" [class.badge-danger]="l.status === 'REJECTED'" [class.badge-secondary]="l.status === 'CANCELLED'">{{ l.status }}</span></td>
            <td>{{ l.decidedBy ? l.decidedBy : '' }}{{ l.decisionNote ? ' — ' + l.decisionNote : '' }}</td>
            <td class="flex gap-1">
              <button class="btn btn-sm btn-success" type="button" *ngIf="l.status === 'PENDING'" (click)="decide(l, 'approve')">{{ 'Approve' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="l.status === 'PENDING'" (click)="decide(l, 'reject')">{{ 'Reject' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="l.status === 'PENDING' || l.status === 'APPROVED'" (click)="decide(l, 'cancel')">{{ 'Cancel' | translate }}</button>
            </td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || filterEmployeeId ? ('Nothing matches these filters.' | translate) : ('No leave requests.' | translate) }}</p>
        <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
      </ng-container>

      <ng-container *ngIf="tab === 'balances'">
        <div class="flex gap-2 mb-2 align-center">
          <label>{{ 'Year' | translate }}</label>
          <input class="form-control" type="number" style="width:6rem;" name="by" [(ngModel)]="year" (ngModelChange)="loadBalances()" />
        </div>
        <table class="table-sm">
          <thead><tr><th>{{ 'Employee' | translate }}</th><th>{{ 'Leave type' | translate }}</th><th>{{ 'Entitled' | translate }}</th><th>{{ 'Earned so far' | translate }}</th><th>{{ 'Carried over' | translate }}</th><th>{{ 'Used' | translate }}</th><th>{{ 'Pending' | translate }}</th><th>{{ 'Remaining' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let b of balances">
            <td>{{ b.employeeName }}</td><td>{{ b.leaveTypeName }}<span class="text-muted" *ngIf="b.accrualMode === 'MONTHLY'"> {{ '(monthly)' | translate }}</span></td><td>{{ b.unlimited ? 'No limit' : b.entitled }}</td>
            <td>{{ b.unlimited ? '—' : b.accrued }}</td><td>{{ b.unlimited ? '—' : b.carriedOver }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted">{{ 'Entitled is the yearly days of the leave type. Types that are earned monthly add one twelfth of the days for each month worked, so "Earned so far" is what can be taken up to now. Carried over days come from unused days of earlier years, up to the limit set on the leave type. Remaining counts earned plus carried over days, less used and pending.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class LeavesComponent implements OnInit {
  tab: 'requests' | 'balances' = 'requests';
  branches: Branch[] = [];
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'PENDING', label: 'Pending' }, { value: 'APPROVED', label: 'Approved' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'CANCELLED', label: 'Cancelled' }];
  list = new ListState<LeaveRequest>((f, p, sz) => this.api.page<LeaveRequest>('leaves', f, p, sz, { employeeId: this.filterEmployeeId }), 'Failed to load leave requests');
  balances: LeaveBalance[] = [];
  employees: Employee[] = [];
  types: LeaveType[] = [];
  filterEmployeeId: number | null = null;
  year = new Date().getFullYear();
  workingDays: number | null = null;
  error: string | null = null;
  form: { employeeId: number | null; leaveTypeId: number; startDate: string; endDate: string; reason: string | null; startHalfDay: boolean; endHalfDay: boolean } = {
    employeeId: null, leaveTypeId: 0, startDate: new Date().toISOString().slice(0, 10), endDate: new Date().toISOString().slice(0, 10), reason: null,
    startHalfDay: false, endHalfDay: false,
  };

  constructor(private api: HrApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.api.leaveTypes().subscribe({ next: (t) => (this.types = t.filter((x) => x.active)), error: () => {} });
    this.load();
    this.countDays();
  }

  load(): void {
    this.list.load();
  }

  employeeChanged(): void {
    this.extra = { employeeId: this.filterEmployeeId };
    this.list.page = 0;
    this.list.load();
  }

  loadBalances(): void {
    this.api.leaveBalances(this.year).subscribe({ next: (b) => (this.balances = b), error: (e) => (this.error = e?.error?.message ?? T('Failed to load balances')) });
  }

  countDays(): void {
    if (!this.form.startDate || !this.form.endDate || this.form.endDate < this.form.startDate) {
      this.workingDays = null;
      return;
    }
    this.api.leaveWorkingDays(this.form.startDate, this.form.endDate, this.form.startHalfDay, this.form.endHalfDay).subscribe({ next: (n) => (this.workingDays = n), error: () => (this.workingDays = null) });
  }

  request(): void {
    if (!this.form.employeeId || !this.form.leaveTypeId) {
      this.error = T('Choose the employee and the leave type.');
      return;
    }
    this.api.requestLeave({ ...this.form, reason: this.form.reason || null }).subscribe({
      next: () => {
        this.error = null;
        this.form.reason = null;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? T('Could not submit the request')),
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
      error: (e) => (this.error = e?.error?.message ?? T('Could not update the request')),
    });
  }
}
