import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { DatePickerComponent } from '../shared/date-picker.component';
import { PaperButtonComponent } from '../shared/paper-button.component';
import { downloadBlob } from '../core/download-file';
import {
  Attendance, EmployeeDocument, HrApiService, docCategoryLabel, LeaveBalance, LeaveRequest, LeaveType, MyContactPayload, MyOverview, MyPayslip, MyPayslipRow,
} from '../core/api/hr-api.service';

import { T } from '../core/i18n/t';
type Tab = 'overview' | 'payslips' | 'leave' | 'attendance' | 'documents' | 'contact';

/** My HR: employee self-service — own payslips, leave, attendance and contact details. Open to every signed-in user. */
@Component({
  selector: 'app-my-hr',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, DatePickerComponent, PaperButtonComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'My HR' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>

      <div class="card mb-3 not-linked" style="padding:1rem;" *ngIf="overview && !overview.linked">
        <strong>{{ 'Your login isn\\'t linked to an employee record.' | translate }}</strong>
        <div class="text-muted">{{ 'Ask HR to link it on your employee page; then your payslips, leave and attendance show up here.' | translate }}</div>
      </div>

      <ng-container *ngIf="overview && overview.linked && overview.profile">
        <div class="flex gap-1 mb-3" style="flex-wrap:wrap;">
          <button *ngFor="let t of tabs" class="btn btn-sm" [ngClass]="'tab-' + t.code" [class.btn-primary]="tab === t.code" [class.btn-outline-primary]="tab !== t.code" type="button" (click)="open(t.code)">{{ t.label }}</button>
        </div>

        <!-- overview -->
        <ng-container *ngIf="tab === 'overview'">
          <div class="card mb-3" style="padding:1rem;">
            <strong class="my-name">{{ overview.profile.fullName }}</strong>
            <div class="text-muted">{{ overview.profile.employeeNumber }} · {{ overview.profile.jobTitle || 'No job title' }}<span *ngIf="overview.profile.departmentName"> · {{ overview.profile.departmentName }}</span> {{ '· since {p0}' | translate: { p0: (overview.profile.hireDate) } }}</div>
            <div class="text-muted" *ngIf="overview.profile.managerName">{{ 'Reports to {p0}' | translate: { p0: (overview.profile.managerName) } }}</div>
          </div>
          <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
            <div class="card" style="padding:1rem; min-width:14rem;">
              <strong>{{ 'Today' | translate }}</strong>
              <div class="text-muted today-state" *ngIf="!overview.today.record">{{ 'You haven\\'t clocked in.' | translate }}</div>
              <div class="text-muted today-state" *ngIf="overview.today.record && !overview.today.record.clockOut">{{ 'Clocked in at {p0}.' | translate: { p0: (overview.today.record.clockIn) } }}</div>
              <div class="text-muted today-state" *ngIf="overview.today.record && overview.today.record.clockOut">{{ '{p0} to {p1} ({p2} h).' | translate: { p0: (overview.today.record.clockIn), p1: (overview.today.record.clockOut), p2: (overview.today.record.hoursWorked) } }}</div>
              <div class="flex gap-2 mt-2" *ngIf="overview.profile.status !== 'TERMINATED'">
                <button class="btn btn-sm btn-success clock-in" type="button" *ngIf="!overview.today.record" (click)="clock('in')">{{ 'Clock in' | translate }}</button>
                <button class="btn btn-sm btn-outline-primary clock-out" type="button" *ngIf="overview.today.record && !overview.today.record.clockOut" (click)="clock('out')">{{ 'Clock out' | translate }}</button>
              </div>
            </div>
            <div class="card" style="padding:1rem; min-width:14rem;">
              <strong>{{ 'Leave' | translate }}</strong>
              <div class="text-muted">{{ '{p0} request(s) waiting for a decision.' | translate: { p0: (overview.pendingRequests) } }}</div>
              <button class="btn btn-sm btn-outline-primary mt-2" type="button" (click)="open('leave')">{{ 'Request leave' | translate }}</button>
            </div>
            <div class="card" style="padding:1rem; min-width:14rem;">
              <strong>{{ 'Payslips' | translate }}</strong>
              <div class="text-muted">{{ '{p0} available.' | translate: { p0: (overview.payslipCount) } }}</div>
              <button class="btn btn-sm btn-outline-primary mt-2" type="button" (click)="open('payslips')">{{ 'See payslips' | translate }}</button>
            </div>
          </div>
          <h4 class="mb-2">{{ 'Leave balance {p0}' | translate: { p0: (overview.year) } }}</h4>
          <table class="table-sm bal-table">
            <thead><tr><th>{{ 'Leave type' | translate }}</th><th>{{ 'Available' | translate }}</th><th>{{ 'Used' | translate }}</th><th>{{ 'Waiting' | translate }}</th><th>{{ 'Remaining' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let b of overview.balances">
              <td>{{ b.leaveTypeName }}</td><td>{{ b.unlimited ? 'No limit' : b.accrued + b.carriedOver }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
            </tr></tbody>
          </table>
        </ng-container>

        <!-- payslips -->
        <ng-container *ngIf="tab === 'payslips'">
          <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;">
            <label>{{ 'Year' | translate }}</label>
            <input class="form-control pay-year" type="number" style="width:6rem;" name="py" [(ngModel)]="payYear" (ngModelChange)="loadPayslips()" />
            <app-paper-button class="my-cert" label="Earnings & tax certificate (PDF)" [path]="'/hr/me/certificate?year=' + payYear" [file]="'certificate-' + payYear + '.pdf'"></app-paper-button>
          </div>
          <table class="table-sm pay-table">
            <thead><tr><th>{{ 'Month' | translate }}</th><th>{{ 'Gross pay' | translate }}</th><th>{{ 'Income tax' | translate }}</th><th>{{ 'Contributions' | translate }}</th><th>{{ 'Other deductions' | translate }}</th><th>{{ 'Net pay' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody>
              <ng-container *ngFor="let p of payslips">
                <tr class="pay-row">
                  <td>{{ monthName(p.month) }} {{ p.year }}</td><td>{{ p.grossPay | number: '1.0-2' }}</td><td>{{ p.payeTax | number: '1.0-2' }}</td><td>{{ p.employeeContributions | number: '1.0-2' }}</td><td>{{ p.otherDeductions | number: '1.0-2' }}</td><td><strong>{{ p.netPay | number: '1.0-2' }}</strong></td>
                  <td><span class="badge" [class.badge-success]="p.status === 'PAID'" [class.badge-warning]="p.status === 'APPROVED'">{{ p.status === 'PAID' ? 'Paid' + (p.paidDate ? ' ' + p.paidDate : '') : 'Approved' }}</span></td>
                  <td class="flex gap-1">
                    <button class="btn btn-sm btn-outline-secondary pay-detail" type="button" (click)="toggleDetail(p)">{{ detail && detail.runId === p.runId ? ('Hide' | translate) : ('Details' | translate) }}</button>
                    <app-paper-button class="pay-pdf" [label]="'PDF' | translate" [path]="'/hr/me/payslips/' + p.runId + '/pdf'" [file]="'payslip-' + p.year + '-' + p.month + '.pdf'"></app-paper-button>
                  </td>
                </tr>
                <tr class="pay-items" *ngIf="detail && detail.runId === p.runId">
                  <td colspan="8">
                    <table class="table-sm no-stack" style="max-width:32rem;">
                      <tbody>
                        <tr><td>{{ 'Days paid' | translate }}</td><td style="text-align:right;">{{ '{p0} of {p1}' | translate: { p0: (detail.line.payableDays), p1: (detail.line.daysInMonth) } }}</td></tr>
                        <tr *ngFor="let i of detail.items"><td>{{ i.name }}<span class="text-muted" *ngIf="i.kind === 'EMPLOYER'"> {{ '(paid by your employer on top)' | translate }}</span></td><td style="text-align:right;">{{ i.kind === 'DEDUCTION' || i.kind === 'TAX' ? '−' : '' }}{{ i.amount | number: '1.0-2' }}</td></tr>
                        <tr><td><strong>{{ 'Net pay' | translate }}</strong></td><td style="text-align:right;"><strong>{{ detail.line.netPay | number: '1.0-2' }}</strong></td></tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="loaded && payslips.length === 0">{{ 'No payslips for {p0}. A payslip appears here once the payroll of the month is approved.' | translate: { p0: (payYear) } }}</p>
        </ng-container>

        <!-- leave -->
        <ng-container *ngIf="tab === 'leave'">
          <form class="card mb-3" style="padding:1rem;" (ngSubmit)="requestLeave()" *ngIf="overview.profile.status !== 'TERMINATED'">
            <strong class="mb-2">{{ 'Ask for leave' | translate }}</strong>
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group"><label class="form-label">{{ 'Leave type' | translate }}</label>
                <select class="form-select lv-type" name="lt" [(ngModel)]="form.leaveTypeId" required>
                  <option [ngValue]="0" disabled>{{ 'Choose…' | translate }}</option>
                  <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}{{ t.paid ? '' : ' (unpaid)' }}</option>
                </select></div>
              <div class="form-group"><label class="form-label">{{ 'From' | translate }}</label><app-date-picker name="lf" [(ngModel)]="form.startDate" (ngModelChange)="countDays()" required></app-date-picker></div>
              <div class="form-group"><label class="form-label">{{ 'To' | translate }}</label><app-date-picker name="lto" [(ngModel)]="form.endDate" (ngModelChange)="countDays()" required></app-date-picker></div>
              <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control lv-reason" name="lr" [(ngModel)]="form.reason" /></div>
              <button class="btn btn-primary lv-submit" type="submit">{{ 'Send request' | translate }}</button>
            </div>
            <div class="flex gap-3 mt-2" style="flex-wrap:wrap;">
              <label><input type="checkbox" class="half-start" name="hs" [(ngModel)]="form.startHalfDay" (ngModelChange)="countDays()" /> {{ 'First day is a half day' | translate }}</label>
              <label><input type="checkbox" class="half-end" name="he" [(ngModel)]="form.endHalfDay" (ngModelChange)="countDays()" /> {{ 'Last day is a half day' | translate }}</label>
            </div>
            <div class="text-muted day-count" *ngIf="workingDays !== null">{{ '{p0} working day(s) (weekends and public holidays are not counted). Your request goes to HR for a decision.' | translate: { p0: (workingDays) } }}</div>
          </form>
          <h4 class="mb-2">{{ 'My requests' | translate }}</h4>
          <table class="table-sm lv-table">
            <thead><tr><th>{{ 'Type' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Days' | translate }}</th><th>{{ 'Reason' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Decision' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let l of leaves" class="lv-row">
              <td>{{ l.leaveTypeName }}</td><td>{{ l.startDate }}</td><td>{{ l.endDate }}</td><td>{{ l.days }}<span class="text-muted" *ngIf="l.startHalf || l.endHalf"> ½</span></td><td>{{ l.reason || '' }}</td>
              <td><span class="badge" [class.badge-warning]="l.status === 'PENDING'" [class.badge-success]="l.status === 'APPROVED'" [class.badge-danger]="l.status === 'REJECTED'" [class.badge-secondary]="l.status === 'CANCELLED'">{{ l.status }}</span></td>
              <td>{{ l.decidedBy ? l.decidedBy : '' }}{{ l.decisionNote ? ' — ' + l.decisionNote : '' }}</td>
              <td><button class="btn btn-sm btn-outline-secondary lv-cancel" type="button" *ngIf="l.status === 'PENDING'" (click)="cancelLeave(l)">{{ 'Withdraw' | translate }}</button></td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="loaded && leaves.length === 0">{{ 'You have no leave requests.' | translate }}</p>
          <h4 class="mb-2 mt-3">{{ 'Balance {p0}' | translate: { p0: (year) } }}</h4>
          <table class="table-sm bal-table">
            <thead><tr><th>{{ 'Leave type' | translate }}</th><th>{{ 'Earned so far' | translate }}</th><th>{{ 'Carried over' | translate }}</th><th>{{ 'Used' | translate }}</th><th>{{ 'Waiting' | translate }}</th><th>{{ 'Remaining' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let b of balances">
              <td>{{ b.leaveTypeName }}</td><td>{{ b.unlimited ? 'No limit' : b.accrued }}</td><td>{{ b.unlimited ? '—' : b.carriedOver }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
            </tr></tbody>
          </table>
        </ng-container>

        <!-- attendance -->
        <ng-container *ngIf="tab === 'attendance'">
          <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;">
            <select class="form-select att-month" style="width:auto;" name="am" [(ngModel)]="attMonth" (ngModelChange)="loadAttendance()">
              <option *ngFor="let m of months; let i = index" [ngValue]="i + 1">{{ m }}</option>
            </select>
            <input class="form-control att-year" type="number" style="width:6rem;" name="ay" [(ngModel)]="attYear" (ngModelChange)="loadAttendance()" />
            <span class="text-muted att-total" *ngIf="attendance.length">{{ '{p0} day(s), {p1} hours worked, {p2} overtime.' | translate: { p0: (attendance.length), p1: (attHours), p2: (attOvertime) } }}</span>
          </div>
          <table class="table-sm att-table">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'In' | translate }}</th><th>{{ 'Out' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Hours' | translate }}</th><th>{{ 'Overtime' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let a of attendance" class="att-row">
              <td>{{ a.workDate }}</td><td>{{ a.clockIn || '' }}</td><td>{{ a.clockOut || '' }}</td><td>{{ a.status }}</td><td>{{ a.hoursWorked }}</td><td>{{ a.overtimeHours }}</td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="loaded && attendance.length === 0">{{ 'No attendance recorded in this month.' | translate }}</p>
        </ng-container>

        <!-- documents the company shared with me -->
        <ng-container *ngIf="tab === 'documents'">
          <table class="stack table-sm my-docs" *ngIf="documents.length">
            <thead><tr><th>{{ 'Document' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Expires' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let d of documents">
              <td data-label="Document">{{ d.name }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="d.description">{{ d.description }}</div></td>
              <td data-label="Kind">{{ docKind(d.category) }}</td>
              <td data-label="Expires">{{ d.expiryDate || '—' }}</td>
              <td data-label="">
                <button class="btn btn-sm btn-outline-secondary my-doc-open" type="button" (click)="openDoc(d)">{{ 'Open' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary my-doc-download" type="button" (click)="downloadDoc(d)">{{ 'Download' | translate }}</button>
              </td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="loaded && documents.length === 0">{{ 'HR has not shared any documents with you yet.' | translate }}</p>
        </ng-container>

        <!-- contact -->
        <ng-container *ngIf="tab === 'contact'">
          <form class="card" style="padding:1rem; max-width:34rem;" (ngSubmit)="saveContact()">
            <strong class="mb-2">{{ 'My contact details' | translate }}</strong>
            <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control ct-phone" name="cp" [(ngModel)]="contact.phone" /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control ct-email" name="ce" [(ngModel)]="contact.email" /></div>
            <div class="form-group"><label class="form-label">{{ 'Address' | translate }}</label><input class="form-control ct-address" name="ca" [(ngModel)]="contact.address" /></div>
            <div class="form-group"><label class="form-label">{{ 'Emergency contact' | translate }}</label><input class="form-control ct-em-name" name="cen" [(ngModel)]="contact.emergencyContactName" /></div>
            <div class="form-group"><label class="form-label">{{ 'Emergency contact phone' | translate }}</label><input class="form-control ct-em-phone" name="cep" [(ngModel)]="contact.emergencyContactPhone" /></div>
            <div><button class="btn btn-primary ct-save" type="submit">{{ 'Save' | translate }}</button></div>
            <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Your bank account, tax number and job details can only be changed by HR.' | translate }}</div>
          </form>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class MyHrComponent implements OnInit {
  tab: Tab = 'overview';
  tabs: { code: Tab; label: string }[] = [
    { code: 'overview', label: 'Overview' }, { code: 'payslips', label: 'Payslips' }, { code: 'leave', label: 'Leave' },
    { code: 'attendance', label: 'Attendance' }, { code: 'documents', label: 'Documents' }, { code: 'contact', label: 'My details' },
  ];
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  overview: MyOverview | null = null;
  error: string | null = null;
  info: string | null = null;
  loaded = false;
  year = new Date().getFullYear();

  payYear = new Date().getFullYear();
  payslips: MyPayslipRow[] = [];
  documents: EmployeeDocument[] = [];
  detail: MyPayslip | null = null;

  types: LeaveType[] = [];
  leaves: LeaveRequest[] = [];
  balances: LeaveBalance[] = [];
  workingDays: number | null = null;
  form: { leaveTypeId: number; startDate: string; endDate: string; reason: string | null; startHalfDay: boolean; endHalfDay: boolean } = {
    leaveTypeId: 0, startDate: new Date().toISOString().slice(0, 10), endDate: new Date().toISOString().slice(0, 10), reason: null, startHalfDay: false, endHalfDay: false,
  };

  attMonth = new Date().getMonth() + 1;
  attYear = new Date().getFullYear();
  attendance: Attendance[] = [];
  attHours = 0;
  attOvertime = 0;

  contact: MyContactPayload = { phone: null, email: null, address: null, emergencyContactName: null, emergencyContactPhone: null };

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.loadOverview();
  }

  monthName(m: number): string {
    return this.months[m - 1];
  }

  private fail(e: any, fallback: string): void {
    this.info = null;
    this.error = e?.error?.message ?? fallback;
  }

  loadOverview(): void {
    this.api.myOverview().subscribe({
      next: (o) => {
        this.overview = o;
        if (o.profile) {
          this.contact = {
            phone: o.profile.phone, email: o.profile.email, address: o.profile.address,
            emergencyContactName: o.profile.emergencyContactName, emergencyContactPhone: o.profile.emergencyContactPhone,
          };
        }
      },
      error: (e) => this.fail(e, T('Failed to load your HR page')),
    });
  }

  open(t: Tab): void {
    this.tab = t;
    this.error = null;
    this.info = null;
    this.loaded = false;
    if (t === 'payslips') this.loadPayslips();
    if (t === 'leave') this.loadLeave();
    if (t === 'attendance') this.loadAttendance();
    if (t === 'documents') this.loadDocuments();
  }

  docKind(c: string): string { return docCategoryLabel(c); }

  loadDocuments(): void {
    this.api.myDocuments().subscribe({
      next: (d) => { this.documents = d; this.loaded = true; },
      error: (e) => { this.loaded = true; this.fail(e, T('Failed to load your documents')); },
    });
  }

  openDoc(d: EmployeeDocument): void {
    this.api.myDocumentFile(d.id).subscribe({ next: (b) => window.open(URL.createObjectURL(b), '_blank'), error: (e) => this.fail(e, T('Could not open the file')) });
  }

  downloadDoc(d: EmployeeDocument): void {
    this.api.myDocumentFile(d.id).subscribe({ next: (b) => downloadBlob(b, d.fileName), error: (e) => this.fail(e, T('Could not download the file')) });
  }

  clock(dir: 'in' | 'out'): void {
    (dir === 'in' ? this.api.myClockIn() : this.api.myClockOut()).subscribe({
      next: () => {
        this.error = null;
        this.loadOverview();
      },
      error: (e) => this.fail(e, T('Could not record the time')),
    });
  }

  // payslips
  loadPayslips(): void {
    this.detail = null;
    this.api.myPayslips(this.payYear).subscribe({
      next: (p) => {
        this.payslips = p;
        this.loaded = true;
        this.error = null;
      },
      error: (e) => this.fail(e, T('Failed to load your payslips')),
    });
  }

  toggleDetail(p: MyPayslipRow): void {
    if (this.detail && this.detail.runId === p.runId) {
      this.detail = null;
      return;
    }
    this.api.myPayslip(p.runId).subscribe({ next: (d) => (this.detail = d), error: (e) => this.fail(e, T('Could not open the payslip')) });
  }

  // leave
  loadLeave(): void {
    this.api.myLeaveTypes().subscribe({ next: (t) => (this.types = t), error: () => {} });
    this.api.myLeaves().subscribe({
      next: (l) => {
        this.leaves = l;
        this.loaded = true;
      },
      error: (e) => this.fail(e, T('Failed to load your leave')),
    });
    this.api.myLeaveBalances(this.year).subscribe({ next: (b) => (this.balances = b), error: () => {} });
    this.countDays();
  }

  countDays(): void {
    if (!this.form.startDate || !this.form.endDate || this.form.endDate < this.form.startDate) {
      this.workingDays = null;
      return;
    }
    this.api.myWorkingDays(this.form.startDate, this.form.endDate, this.form.startHalfDay, this.form.endHalfDay).subscribe({
      next: (n) => (this.workingDays = n),
      error: () => (this.workingDays = null),
    });
  }

  requestLeave(): void {
    if (!this.form.leaveTypeId) {
      this.error = T('Choose the leave type.');
      return;
    }
    this.api.myRequestLeave({ employeeId: null, ...this.form, reason: this.form.reason || null }).subscribe({
      next: () => {
        this.error = null;
        this.info = T('Your request was sent to HR.');
        this.form.reason = null;
        this.loadLeave();
        this.loadOverview();
      },
      error: (e) => this.fail(e, T('Could not send the request')),
    });
  }

  cancelLeave(l: LeaveRequest): void {
    this.api.myCancelLeave(l.id, null).subscribe({
      next: () => {
        this.error = null;
        this.info = T('Your request was withdrawn.');
        this.loadLeave();
        this.loadOverview();
      },
      error: (e) => this.fail(e, T('Could not withdraw the request')),
    });
  }

  // attendance
  loadAttendance(): void {
    if (!this.attYear || this.attYear < 2000 || this.attYear > 2200) return;
    const m = String(this.attMonth).padStart(2, '0');
    const last = new Date(this.attYear, this.attMonth, 0).getDate();
    this.api.myAttendance(`${this.attYear}-${m}-01`, `${this.attYear}-${m}-${String(last).padStart(2, '0')}`).subscribe({
      next: (a) => {
        this.attendance = a;
        this.attHours = Math.round(a.reduce((n, x) => n + (x.hoursWorked || 0), 0) * 100) / 100;
        this.attOvertime = Math.round(a.reduce((n, x) => n + (x.overtimeHours || 0), 0) * 100) / 100;
        this.loaded = true;
        this.error = null;
      },
      error: (e) => this.fail(e, T('Failed to load your attendance')),
    });
  }

  // contact
  saveContact(): void {
    this.api.myUpdateContact(this.contact).subscribe({
      next: () => {
        this.error = null;
        this.info = T('Your details were saved.');
        this.loadOverview();
      },
      error: (e) => this.fail(e, T('Could not save your details')),
    });
  }
}
