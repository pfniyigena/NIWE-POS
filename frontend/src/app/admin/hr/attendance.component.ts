import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Attendance, AttendancePayload, AttendanceSummary, Department, Employee, HrApiService, TodayStatus } from '../../core/api/hr-api.service';

/** Attendance & Leaves > Attendance: clock in / out, the daily records, manual entries and a summary per employee. */
@Component({
  selector: 'app-attendance',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Attendance</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="today">
        <div class="flex align-center gap-3" style="flex-wrap:wrap;">
          <strong>My day</strong>
          <ng-container *ngIf="today.linked; else notLinked">
            <span>{{ today.employeeName }}:
              <ng-container *ngIf="!today.record?.clockIn">not clocked in yet</ng-container>
              <ng-container *ngIf="today.record?.clockIn">in at {{ today.record?.clockIn }}<ng-container *ngIf="today.record?.clockOut"> · out at {{ today.record?.clockOut }} ({{ today.record?.hoursWorked }} h)</ng-container></ng-container>
            </span>
            <button class="btn btn-success" type="button" *ngIf="!today.record?.clockIn" (click)="clock('in')">Clock in</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="today.record?.clockIn && !today.record?.clockOut" (click)="clock('out')">Clock out</button>
          </ng-container>
          <ng-template #notLinked><span class="text-muted">Your login isn't linked to an employee record, so you can't clock in here. Link it on your employee page.</span></ng-template>
        </div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <label>From</label><app-date-picker name="from" [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
        <label>To</label><app-date-picker name="to" [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        <select class="form-select" style="width:auto;" name="emp" [(ngModel)]="employeeId" (ngModelChange)="load()">
          <option [ngValue]="null">All employees</option>
          <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="dept" [(ngModel)]="departmentId" (ngModelChange)="load()">
          <option [ngValue]="null">All departments</option>
          <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
        </select>
        <div class="flex gap-1">
          <button class="btn btn-sm" [class.btn-primary]="view === 'records'" [class.btn-outline-primary]="view !== 'records'" type="button" (click)="view = 'records'">Daily records</button>
          <button class="btn btn-sm" [class.btn-primary]="view === 'summary'" [class.btn-outline-primary]="view !== 'summary'" type="button" (click)="view = 'summary'">Summary</button>
        </div>
      </div>

      <ng-container *ngIf="view === 'records'">
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveEntry()">
          <strong class="mb-2">Add or correct a day</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Employee</label>
              <select class="form-select" name="fe" [(ngModel)]="entry.employeeId" required>
                <option [ngValue]="0" disabled>Choose…</option>
                <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">Date</label><app-date-picker name="fd" [(ngModel)]="entry.workDate" required></app-date-picker></div>
            <div class="form-group"><label class="form-label">In</label><input class="form-control" type="time" style="width:8rem;" name="fi" [(ngModel)]="entry.clockIn" /></div>
            <div class="form-group"><label class="form-label">Out</label><input class="form-control" type="time" style="width:8rem;" name="fo" [(ngModel)]="entry.clockOut" /></div>
            <div class="form-group"><label class="form-label">Status</label>
              <select class="form-select" name="fs" [(ngModel)]="entry.status">
                <option [ngValue]="null">Automatic</option>
                <option value="PRESENT">Present</option><option value="LATE">Late</option><option value="ABSENT">Absent</option>
              </select></div>
            <div class="form-group"><label class="form-label">Note</label><input class="form-control" name="fn" [(ngModel)]="entry.note" /></div>
            <button class="btn btn-primary" type="submit">Save</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">Hours and overtime are worked out from the in and out times and the working hours in Leave settings. Saving a day that already has a record replaces it.</div>
        </form>
        <table class="table-sm">
          <thead><tr><th>Date</th><th>Employee</th><th>In</th><th>Out</th><th>Hours</th><th>Overtime</th><th>Status</th><th>Note</th><th></th></tr></thead>
          <tbody><tr *ngFor="let a of records">
            <td>{{ a.workDate }}</td><td>{{ a.employeeName }}</td><td>{{ a.clockIn || '—' }}</td><td>{{ a.clockOut || '—' }}</td>
            <td>{{ a.hoursWorked }}</td><td>{{ a.overtimeHours }}</td>
            <td><span class="badge" [class.badge-success]="a.status === 'PRESENT'" [class.badge-warning]="a.status === 'LATE'" [class.badge-danger]="a.status === 'ABSENT'">{{ a.status }}</span></td>
            <td>{{ a.note || '' }}</td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="remove(a)">Delete</button></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!records.length">No attendance records in this period.</p>
      </ng-container>

      <ng-container *ngIf="view === 'summary'">
        <table class="table-sm">
          <thead><tr><th>Employee</th><th>Department</th><th>Expected days</th><th>Present</th><th>Late</th><th>Absent</th><th>On leave</th><th>Hours</th><th>Overtime</th></tr></thead>
          <tbody><tr *ngFor="let s of summary">
            <td>{{ s.employeeName }}</td><td>{{ s.departmentName || '—' }}</td><td>{{ s.expectedDays }}</td><td>{{ s.presentDays }}</td><td>{{ s.lateDays }}</td>
            <td>{{ s.absentDays }}</td><td>{{ s.leaveDays }}</td><td>{{ s.hoursWorked }}</td><td>{{ s.overtimeHours }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!summary.length">Nothing to summarise for this period.</p>
      </ng-container>
    </app-shell>
  `,
})
export class AttendanceComponent implements OnInit {
  today: TodayStatus | null = null;
  records: Attendance[] = [];
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

  constructor(private api: HrApiService) {}

  private static monthStart(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }

  private static blank() {
    return { employeeId: 0, workDate: new Date().toISOString().slice(0, 10), clockIn: null, clockOut: null, status: null, note: null };
  }

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.api.today().subscribe({ next: (t) => (this.today = t), error: () => {} });
    this.load();
  }

  load(): void {
    if (!this.from || !this.to) return;
    this.api.attendance(this.from, this.to, this.employeeId, this.departmentId).subscribe({
      next: (r) => {
        this.records = r;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load attendance'),
    });
    this.api.attendanceSummary(this.from, this.to, this.employeeId, this.departmentId).subscribe({ next: (s) => (this.summary = s), error: () => {} });
  }

  clock(dir: 'in' | 'out'): void {
    (dir === 'in' ? this.api.clockIn() : this.api.clockOut()).subscribe({
      next: () => {
        this.error = null;
        this.api.today().subscribe((t) => (this.today = t));
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not record that'),
    });
  }

  saveEntry(): void {
    if (!this.entry.employeeId) {
      this.error = 'Choose the employee.';
      return;
    }
    const payload: AttendancePayload = { ...this.entry, clockIn: this.entry.clockIn || null, clockOut: this.entry.clockOut || null };
    this.api.saveAttendance(payload).subscribe({
      next: () => {
        this.error = null;
        this.entry = AttendanceComponent.blank();
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the record'),
    });
  }

  remove(a: Attendance): void {
    if (!confirm(`Delete the record of ${a.employeeName} on ${a.workDate}?`)) return;
    this.api.deleteAttendance(a.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the record') });
  }
}
