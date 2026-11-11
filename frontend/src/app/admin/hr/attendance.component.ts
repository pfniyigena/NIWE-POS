import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Attendance, AttendancePayload, AttendanceSummary, Department, Employee, HrApiService, TodayStatus } from '../../core/api/hr-api.service';

import { T } from '../../core/i18n/t';
/** Attendance & Leaves > Attendance: clock in / out, the daily records, manual entries and a summary per employee. */
@Component({
  selector: 'app-attendance',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, DatePickerComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'Attendance' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>

      <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="today">
        <div class="flex align-center gap-3" style="flex-wrap:wrap;">
          <strong>{{ 'My day' | translate }}</strong>
          <ng-container *ngIf="today.linked; else notLinked">
            <span>{{ today.employeeName }}:
              <ng-container *ngIf="!today.record?.clockIn">{{ 'not clocked in yet' | translate }}</ng-container>
              <ng-container *ngIf="today.record?.clockIn">{{ 'in at {p0}' | translate: { p0: (today.record?.clockIn) } }}<ng-container *ngIf="today.record?.clockOut"> {{ '· out at {p0} ({p1} h)' | translate: { p0: (today.record?.clockOut), p1: (today.record?.hoursWorked) } }}</ng-container></ng-container>
            </span>
            <button class="btn btn-success" type="button" *ngIf="!today.record?.clockIn" (click)="clock('in')">{{ 'Clock in' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="today.record?.clockIn && !today.record?.clockOut" (click)="clock('out')">{{ 'Clock out' | translate }}</button>
          </ng-container>
          <ng-template #notLinked><span class="text-muted">{{ 'Your login isn\\'t linked to an employee record, so you can\\'t clock in here. Link it on your employee page.' | translate }}</span></ng-template>
        </div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <label>{{ 'From' | translate }}</label><app-date-picker name="from" [(ngModel)]="from" (ngModelChange)="rangeChanged()"></app-date-picker>
        <label>{{ 'To' | translate }}</label><app-date-picker name="to" [(ngModel)]="to" (ngModelChange)="rangeChanged()"></app-date-picker>
        <select class="form-select" style="width:auto;" name="emp" [(ngModel)]="employeeId" (ngModelChange)="rangeChanged()">
          <option [ngValue]="null">{{ 'All employees' | translate }}</option>
          <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="dept" [(ngModel)]="departmentId" (ngModelChange)="rangeChanged()">
          <option [ngValue]="null">{{ 'All departments' | translate }}</option>
          <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
        </select>
        <div class="flex gap-1">
          <button class="btn btn-sm" [class.btn-primary]="view === 'records'" [class.btn-outline-primary]="view !== 'records'" type="button" (click)="view = 'records'">{{ 'Daily records' | translate }}</button>
          <button class="btn btn-sm" [class.btn-primary]="view === 'summary'" [class.btn-outline-primary]="view !== 'summary'" type="button" (click)="view = 'summary'">{{ 'Summary' | translate }}</button>
        </div>
      </div>

      <ng-container *ngIf="view === 'records'">
        <app-list-filter exportKind="attendance" exportPath="/hr/attendance/export" [placeholder]="'Search employee, note…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="false" [branches]="branches" [exportExtra]="extra" (filterChange)="list.apply($event)"></app-list-filter>
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveEntry()">
          <strong class="mb-2">{{ 'Add or correct a day' | translate }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Employee' | translate }}</label>
              <select class="form-select" name="fe" [(ngModel)]="entry.employeeId" required>
                <option [ngValue]="0" disabled>{{ 'Choose…' | translate }}</option>
                <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="fd" [(ngModel)]="entry.workDate" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'In' | translate }}</label><input class="form-control" type="time" style="width:8rem;" name="fi" [(ngModel)]="entry.clockIn" /></div>
            <div class="form-group"><label class="form-label">{{ 'Out' | translate }}</label><input class="form-control" type="time" style="width:8rem;" name="fo" [(ngModel)]="entry.clockOut" /></div>
            <div class="form-group"><label class="form-label">{{ 'Status' | translate }}</label>
              <select class="form-select" name="fs" [(ngModel)]="entry.status">
                <option [ngValue]="null">{{ 'Automatic' | translate }}</option>
                <option value="PRESENT">{{ 'Present' | translate }}</option><option value="LATE">{{ 'Late' | translate }}</option><option value="ABSENT">{{ 'Absent' | translate }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="fn" [(ngModel)]="entry.note" /></div>
            <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Hours and overtime are worked out from the in and out times and the working hours in Leave settings. Saving a day that already has a record replaces it.' | translate }}</div>
        </form>
        <table class="table-sm">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Employee' | translate }}</th><th>{{ 'In' | translate }}</th><th>{{ 'Out' | translate }}</th><th>{{ 'Hours' | translate }}</th><th>{{ 'Overtime' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let a of list.rows">
            <td>{{ a.workDate }}</td><td>{{ a.employeeName }}</td><td>{{ a.clockIn || '—' }}</td><td>{{ a.clockOut || '—' }}</td>
            <td>{{ a.hoursWorked }}</td><td>{{ a.overtimeHours }}</td>
            <td><span class="badge" [class.badge-success]="a.status === 'PRESENT'" [class.badge-warning]="a.status === 'LATE'" [class.badge-danger]="a.status === 'ABSENT'">{{ a.status }}</span></td>
            <td>{{ a.note || '' }}</td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="remove(a)">{{ 'Delete' | translate }}</button></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('Nothing matches these filters.' | translate) : ('No attendance records in this period.' | translate) }}</p>
        <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
      </ng-container>

      <ng-container *ngIf="view === 'summary'">
        <table class="table-sm">
          <thead><tr><th>{{ 'Employee' | translate }}</th><th>{{ 'Department' | translate }}</th><th>{{ 'Expected days' | translate }}</th><th>{{ 'Present' | translate }}</th><th>{{ 'Late' | translate }}</th><th>{{ 'Absent' | translate }}</th><th>{{ 'On leave' | translate }}</th><th>{{ 'Hours' | translate }}</th><th>{{ 'Overtime' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let s of summary">
            <td>{{ s.employeeName }}</td><td>{{ s.departmentName || '—' }}</td><td>{{ s.expectedDays }}</td><td>{{ s.presentDays }}</td><td>{{ s.lateDays }}</td>
            <td>{{ s.absentDays }}</td><td>{{ s.leaveDays }}</td><td>{{ s.hoursWorked }}</td><td>{{ s.overtimeHours }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!summary.length">{{ 'Nothing to summarise for this period.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class AttendanceComponent implements OnInit {
  today: TodayStatus | null = null;
  branches: Branch[] = [];
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'PRESENT', label: 'Present' }, { value: 'LATE', label: 'Late' }, { value: 'ABSENT', label: 'Absent' }];
  list = new ListState<Attendance>((f, p, sz) => this.api.page<Attendance>('attendance', f, p, sz, this.rangeParams()), 'Failed to load attendance');
  summary: AttendanceSummary[] = [];
  employees: Employee[] = [];
  departments: Department[] = [];
  view: 'records' | 'summary' = 'records';
  from = AttendanceComponent.monthStart();
  to = new Date().toISOString().slice(0, 10);
  employeeId: number | null = null;
  departmentId: number | null = null;
  error: string | null = null;
  entry: { employeeId: number; workDate: string; clockIn: string | null; clockOut: string | null; status: 'PRESENT' | 'LATE' | 'ABSENT' | null; note: string | null } = AttendanceComponent.blank();

  constructor(private api: HrApiService, private branchApi: BranchApiService) {}

  private static monthStart(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }

  private static blank() {
    return { employeeId: 0, workDate: new Date().toISOString().slice(0, 10), clockIn: null, clockOut: null, status: null, note: null };
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.extra = this.rangeParams();
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.api.today().subscribe({ next: (t) => (this.today = t), error: () => {} });
    this.load();
  }

  private rangeParams(): Record<string, string | number | null> {
    return { from: this.from, to: this.to, employeeId: this.employeeId, departmentId: this.departmentId };
  }

  /** The dates, the employee or the department changed: the export follows, and both the records and the summary are asked again. */
  rangeChanged(): void {
    this.extra = this.rangeParams();
    this.list.page = 0;
    this.load();
  }

  load(): void {
    if (!this.from || !this.to) return;
    this.list.load();
    this.api.attendanceSummary(this.from, this.to, this.employeeId, this.departmentId).subscribe({ next: (s) => (this.summary = s), error: () => {} });
  }

  clock(dir: 'in' | 'out'): void {
    (dir === 'in' ? this.api.clockIn() : this.api.clockOut()).subscribe({
      next: () => {
        this.error = null;
        this.api.today().subscribe((t) => (this.today = t));
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? T('Could not record that')),
    });
  }

  saveEntry(): void {
    if (!this.entry.employeeId) {
      this.error = T('Choose the employee.');
      return;
    }
    const payload: AttendancePayload = { ...this.entry, clockIn: this.entry.clockIn || null, clockOut: this.entry.clockOut || null };
    this.api.saveAttendance(payload).subscribe({
      next: () => {
        this.error = null;
        this.entry = AttendanceComponent.blank();
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? T('Could not save the record')),
    });
  }

  remove(a: Attendance): void {
    if (!confirm(T('Delete the record of {p0} on {p1}?', { p0: a.employeeName, p1: a.workDate }))) return;
    this.api.deleteAttendance(a.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? T('Could not delete the record')) });
  }
}
