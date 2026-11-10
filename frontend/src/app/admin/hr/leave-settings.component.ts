import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Holiday, HrApiService, HrSettings, LeaveType, LeaveTypePayload } from '../../core/api/hr-api.service';

/** Attendance & Leaves > Settings: working hours and days, leave types and public holidays. */
@Component({
  selector: 'app-leave-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Attendance & leave settings</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/leaves">{{ 'Back to leave' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ saved }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveSettings()" *ngIf="settings">
        <strong class="mb-2">{{ 'Working time' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Work starts at' | translate }}</label><input class="form-control" type="time" style="width:8rem;" name="ws" [(ngModel)]="settings.workStart" /></div>
          <div class="form-group"><label class="form-label">{{ 'Grace period (minutes)' | translate }}</label><input class="form-control" type="number" min="0" style="width:8rem;" name="gm" [(ngModel)]="settings.graceMinutes" /></div>
          <div class="form-group"><label class="form-label">{{ 'Hours in a normal day' | translate }}</label><input class="form-control" type="number" min="1" max="24" step="0.5" style="width:8rem;" name="sd" [(ngModel)]="settings.standardDailyHours" /></div>
          <div class="form-group"><label class="form-label">{{ 'Overtime pays' | translate }}</label><input class="form-control" type="number" min="1" step="0.1" style="width:8rem;" name="om" [(ngModel)]="settings.overtimeMultiplier" /></div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Working days' | translate }}</label>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <label *ngFor="let d of days"><input type="checkbox" [checked]="isDay(d.code)" (change)="toggleDay(d.code)" /> {{ d.label }}</label>
          </div>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">{{ 'Clocking in later than the start time plus the grace period counts as late. Hours beyond a normal day (or any hours on a day off) are overtime, paid at the rate above times the normal hourly pay — but only for employees whose attendance counts for pay.' | translate }}</div>
        <div><button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button></div>
      </form>

      <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
        <div style="flex:1; min-width:24rem;">
          <h4 class="mb-2">{{ 'Leave types' | translate }}</h4>
          <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveType()">
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="tn" [(ngModel)]="type.name" required /></div>
              <div class="form-group"><label class="form-label">{{ 'Days a year (0 = no limit)' | translate }}</label><input class="form-control" type="number" min="0" style="width:8rem;" name="td" [(ngModel)]="type.daysPerYear" /></div>
              <div class="form-group"><label class="form-label">{{ 'Days become available' | translate }}</label>
                <select class="form-select type-accrual" name="tacc" [(ngModel)]="type.accrualMode">
                  <option value="UPFRONT">{{ 'All at the start of the year' | translate }}</option>
                  <option value="MONTHLY">{{ 'Earned month by month' | translate }}</option>
                </select></div>
              <div class="form-group"><label class="form-label">{{ 'Carry over (max days)' | translate }}</label><input class="form-control type-carry" type="number" min="0" max="365" style="width:8rem;" name="tcarry" [(ngModel)]="type.carryOverMaxDays" /></div>
              <div class="form-group"><label class="form-label"><input type="checkbox" name="tp" [(ngModel)]="type.paid" /> {{ 'Paid' | translate }}</label></div>
              <div class="form-group"><label class="form-label" [title]="'Unused days are paid in the final pay of an employee who leaves' | translate"><input class="type-payout" type="checkbox" name="tpo" [(ngModel)]="type.payoutOnExit" /> {{ 'Paid out when leaving' | translate }}</label></div>
              <div class="form-group" *ngIf="typeId"><label class="form-label"><input type="checkbox" name="ta" [(ngModel)]="type.active" /> {{ 'Active' | translate }}</label></div>
              <div class="flex gap-2"><button class="btn btn-primary" type="submit">{{ typeId ? ('Save' | translate) : ('Add' | translate) }}</button><button class="btn btn-link" type="button" *ngIf="typeId" (click)="resetType()">{{ 'Cancel' | translate }}</button></div>
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Unpaid leave is deducted from the employee\\'s pay by payroll. Carry over is how many unused days can move into the next year (0 = none). Paid out when leaving: unused days of this type are paid in the final pay of an employee who leaves. Monthly earning gives one twelfth of the yearly days for each month worked; it also applies to people hired during the year.' | translate }}</div>
          </form>
          <table class="table-sm">
            <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'Days a year' | translate }}</th><th>{{ 'Earned' | translate }}</th><th>{{ 'Carry over' | translate }}</th><th>{{ 'Paid' | translate }}</th><th>{{ 'Paid out on leaving' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let t of types">
              <td>{{ t.name }}</td><td>{{ t.daysPerYear || 'No limit' }}</td><td>{{ t.accrualMode === 'MONTHLY' ? ('Monthly' | translate) : ('At start of year' | translate) }}</td><td>{{ t.carryOverMaxDays || 'None' }}</td><td>{{ t.paid ? ('Yes' | translate) : ('No' | translate) }}</td><td>{{ t.payoutOnExit ? ('Yes' | translate) : ('No' | translate) }}</td>
              <td><span class="badge" [class.badge-success]="t.active" [class.badge-secondary]="!t.active">{{ t.active ? ('Active' | translate) : ('Inactive' | translate) }}</span></td>
              <td class="flex gap-1">
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="editType(t)">{{ 'Edit' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" type="button" (click)="delType(t)">{{ 'Delete' | translate }}</button>
              </td>
            </tr></tbody>
          </table>
        </div>

        <div style="flex:1; min-width:22rem;">
          <h4 class="mb-2">{{ 'Public holidays' | translate }}</h4>
          <form class="card mb-3" style="padding:1rem;" (ngSubmit)="addHoliday()">
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="hd" [(ngModel)]="holiday.date" required></app-date-picker></div>
              <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="hn" [(ngModel)]="holiday.name" required /></div>
              <button class="btn btn-primary" type="submit">{{ 'Add' | translate }}</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Public holidays aren\\'t counted as leave days or working days. Add the holidays of each year.' | translate }}</div>
          </form>
          <table class="table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Holiday' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let h of holidays"><td>{{ h.date }}</td><td>{{ h.name }}</td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="delHoliday(h)">{{ 'Remove' | translate }}</button></td></tr></tbody>
          </table>
          <p class="text-muted" *ngIf="!holidays.length">{{ 'No public holidays entered yet.' | translate }}</p>
        </div>
      </div>
    </app-shell>
  `,
})
export class LeaveSettingsComponent implements OnInit {
  settings: HrSettings | null = null;
  types: LeaveType[] = [];
  holidays: Holiday[] = [];
  error: string | null = null;
  saved: string | null = null;
  typeId: number | null = null;
  type: LeaveTypePayload = { name: '', daysPerYear: 0, paid: true, active: true, accrualMode: 'UPFRONT', carryOverMaxDays: 0, payoutOnExit: false };
  holiday = { date: new Date().toISOString().slice(0, 10), name: '' };
  days = [
    { code: 'MON', label: 'Mon' }, { code: 'TUE', label: 'Tue' }, { code: 'WED', label: 'Wed' }, { code: 'THU', label: 'Thu' },
    { code: 'FRI', label: 'Fri' }, { code: 'SAT', label: 'Sat' }, { code: 'SUN', label: 'Sun' },
  ];

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.settings = { ...s, workStart: s.workStart.slice(0, 5) }), error: (e) => (this.error = e?.error?.message ?? 'Failed to load settings') });
    this.loadTypes();
    this.loadHolidays();
  }

  isDay(code: string): boolean {
    return !!this.settings && this.settings.workingDays.split(',').includes(code);
  }

  toggleDay(code: string): void {
    if (!this.settings) return;
    const set = new Set(this.settings.workingDays.split(',').filter((x) => x));
    if (set.has(code)) set.delete(code);
    else set.add(code);
    this.settings.workingDays = this.days.map((d) => d.code).filter((c) => set.has(c)).join(',');
  }

  saveSettings(): void {
    if (!this.settings) return;
    this.error = null;
    this.api.updateSettings(this.settings).subscribe({
      next: (s) => {
        this.settings = { ...s, workStart: s.workStart.slice(0, 5) };
        this.saved = 'Settings saved.';
        setTimeout(() => (this.saved = null), 3000);
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the settings'),
    });
  }

  loadTypes(): void {
    this.api.leaveTypes().subscribe({ next: (t) => (this.types = t), error: (e) => (this.error = e?.error?.message ?? 'Failed to load leave types') });
  }

  editType(t: LeaveType): void {
    this.typeId = t.id;
    this.type = { name: t.name, daysPerYear: t.daysPerYear, paid: t.paid, active: t.active, accrualMode: t.accrualMode ?? 'UPFRONT', carryOverMaxDays: t.carryOverMaxDays ?? 0, payoutOnExit: !!t.payoutOnExit };
  }

  resetType(): void {
    this.typeId = null;
    this.type = { name: '', daysPerYear: 0, paid: true, active: true, accrualMode: 'UPFRONT', carryOverMaxDays: 0, payoutOnExit: false };
  }

  saveType(): void {
    this.error = null;
    const call = this.typeId ? this.api.updateLeaveType(this.typeId, this.type) : this.api.createLeaveType(this.type);
    call.subscribe({
      next: () => {
        this.resetType();
        this.loadTypes();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the leave type'),
    });
  }

  delType(t: LeaveType): void {
    if (!confirm(`Delete leave type ${t.name}?`)) return;
    this.api.deleteLeaveType(t.id).subscribe({ next: () => this.loadTypes(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the leave type') });
  }

  loadHolidays(): void {
    this.api.holidays().subscribe({ next: (h) => (this.holidays = h), error: () => {} });
  }

  addHoliday(): void {
    this.error = null;
    this.api.createHoliday(this.holiday.date, this.holiday.name).subscribe({
      next: () => {
        this.holiday.name = '';
        this.loadHolidays();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not add the holiday'),
    });
  }

  delHoliday(h: Holiday): void {
    this.api.deleteHoliday(h.id).subscribe({ next: () => this.loadHolidays(), error: (e) => (this.error = e?.error?.message ?? 'Could not remove the holiday') });
  }
}
