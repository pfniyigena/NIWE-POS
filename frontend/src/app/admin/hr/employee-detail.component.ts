import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { PaperButtonComponent } from '../../shared/paper-button.component';
import { AuthService } from '../../core/auth.service';
import { EmployeeDocumentsComponent } from './employee-documents.component';
import { EmployeeFinalPayComponent } from './employee-final-pay.component';
import { EmployeeChecklistsComponent } from './employee-checklists.component';
import { Contract, Employee, EmployeeLoginCreated, HrApiService, LeaveBalance, LeaveRequest } from '../../core/api/hr-api.service';

/** Employees > one employee: details, leave balances and requests, contracts and the actions (edit, leave of absence, left the company). */
@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, PaperButtonComponent, EmployeeDocumentsComponent, EmployeeChecklistsComponent, EmployeeFinalPayComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="e">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h3>{{ e.fullName }} <span class="badge" [class.badge-success]="e.status === 'ACTIVE'" [class.badge-warning]="e.status === 'INACTIVE'" [class.badge-secondary]="e.status === 'TERMINATED'">{{ statusLabel(e.status) }}</span></h3>
            <div class="text-muted">{{ e.employeeNumber }} · {{ e.jobTitle || ('No job title' | translate) }}{{ e.departmentName ? ' · ' + e.departmentName : '' }}</div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <app-paper-button class="service-card" *ngIf="e.status !== 'TERMINATED'" [label]="'Print Service Card' | translate" [path]="'/hr/employees/' + e.id + '/service-card'" [file]="'service-card-' + e.employeeNumber + '.pdf'"></app-paper-button>
            <a class="btn btn-outline-primary" [routerLink]="['/admin/hr/employees', e.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-outline-secondary" type="button" *ngIf="e.status === 'ACTIVE'" (click)="setStatus('INACTIVE')">{{ 'Mark inactive' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="e.status !== 'ACTIVE'" (click)="setStatus('ACTIVE')">{{ e.status === 'TERMINATED' ? ('Re-hire' | translate) : ('Mark active' | translate) }}</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="e.status !== 'TERMINATED'" (click)="showLeft = !showLeft">{{ 'Employee left' | translate }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <div class="card mb-3 login-result" style="padding:1rem;" *ngIf="login">
          <strong>{{ 'Login for {p0}' | translate: { p0: (login.employeeName) } }}</strong>
          <div>{{ 'Username' | translate }} <code>{{ login.username }}</code> {{ '· password' | translate }} <code>{{ login.password }}</code></div>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Give these to the employee now; the password is not shown again. After signing in they open' | translate }} <strong>{{ 'My HR' | translate }}</strong> {{ 'to see their own record, leave, attendance and payslips.' | translate }}</div>
          <div><button class="btn btn-sm btn-outline-secondary mt-2" type="button" (click)="login = null">{{ 'Close' | translate }}</button></div>
        </div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showLeft" (ngSubmit)="left()">
          <strong class="mb-2">{{ 'Employee left the company' | translate }}</strong>
          <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Last day' | translate }}</label><app-date-picker name="ld" [(ngModel)]="leftDate"></app-date-picker></div>
            <div class="form-group" style="min-width:16rem;"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="lr" [(ngModel)]="leftReason" /></div>
            <button class="btn btn-danger" type="submit">{{ 'Confirm' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'This also ends their active contract on that date.' | translate }}</div>
        </form>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <div class="card" style="padding:1rem; flex:1; min-width:18rem;">
            <strong class="mb-2">{{ 'Details' | translate }}</strong>
            <table class="table-sm">
              <tr><td class="text-muted">{{ 'Hired' | translate }}</td><td>{{ e.hireDate }}</td></tr>
              <tr *ngIf="e.terminationDate"><td class="text-muted">{{ 'Left' | translate }}</td><td>{{ e.terminationDate }}{{ e.terminationReason ? ' — ' + e.terminationReason : '' }}</td></tr>
              <tr><td class="text-muted">{{ 'Employment' | translate }}</td><td>{{ typeLabel(e.employmentType) }}</td></tr>
              <tr><td class="text-muted">{{ 'Position' | translate }}</td><td>{{ e.positionTitle || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Reports to' | translate }}</td><td>{{ e.managerName || '—' }}</td></tr>
              <tr *ngIf="e.branchName"><td class="text-muted">{{ 'Branch' | translate }}</td><td>{{ e.branchName }}</td></tr>
              <tr><td class="text-muted">{{ 'Date of birth' | translate }}</td><td>{{ e.dateOfBirth || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'National ID' | translate }}</td><td>{{ e.nationalId || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Phone' | translate }}</td><td>{{ e.phone || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Email' | translate }}</td><td>{{ e.email || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Address' | translate }}</td><td>{{ e.address || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Emergency contact' | translate }}</td><td>{{ e.emergencyContactName || '—' }}{{ e.emergencyContactPhone ? ' · ' + e.emergencyContactPhone : '' }}</td></tr>
              <tr><td class="text-muted">{{ 'Staff login' | translate }}</td><td>{{ e.userLabel || '—' }}
                <button class="btn btn-sm btn-outline-primary give-login" type="button" *ngIf="canManage && !e.userId && e.status !== 'TERMINATED'" (click)="giveLogin()">{{ 'Give login' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary reset-login" type="button" *ngIf="canManage && e.userId" (click)="resetLogin()">{{ 'New password' | translate }}</button>
              </td></tr>
              <tr><td class="text-muted">{{ 'Bank' | translate }}</td><td>{{ e.bankName || '—' }}{{ e.bankAccount ? ' · ' + e.bankAccount : '' }}</td></tr>
              <tr><td class="text-muted">{{ 'Tax number' | translate }}</td><td>{{ e.taxNumber || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Social security no.' | translate }}</td><td>{{ e.socialSecurityNumber || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Attendance counts for pay' | translate }}</td><td>{{ e.attendanceTracked ? ('Yes' | translate) : ('No' | translate) }}</td></tr>
            </table>
            <p class="text-muted" *ngIf="e.notes" style="margin-top:0.5rem;">{{ e.notes }}</p>
          </div>

          <div style="flex:1.4; min-width:20rem;">
            <div class="card mb-3" style="padding:1rem;" *ngIf="balances.length">
              <strong class="mb-2">{{ 'Leave balances {p0}' | translate: { p0: (year) } }}</strong>
              <table class="table-sm">
                <thead><tr><th>{{ 'Leave type' | translate }}</th><th>{{ 'Entitled' | translate }}</th><th>{{ 'Used' | translate }}</th><th>{{ 'Pending' | translate }}</th><th>{{ 'Remaining' | translate }}</th></tr></thead>
                <tbody><tr *ngFor="let b of balances">
                  <td>{{ b.leaveTypeName }}</td><td>{{ b.unlimited ? ('No limit' | translate) : b.entitled }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
                </tr></tbody>
              </table>
            </div>
            <div class="card mb-3" style="padding:1rem;" *ngIf="leaves.length">
              <strong class="mb-2">{{ 'Leave requests' | translate }}</strong>
              <table class="table-sm">
                <thead><tr><th>{{ 'Type' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Days' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
                <tbody><tr *ngFor="let l of leaves">
                  <td>{{ l.leaveTypeName }}</td><td>{{ l.startDate }}</td><td>{{ l.endDate }}</td><td>{{ l.days }}</td><td>{{ l.status }}</td>
                </tr></tbody>
              </table>
            </div>
            <div class="card mb-3" style="padding:1rem;" *ngIf="canContracts">
              <div class="flex justify-between align-center mb-2">
                <strong>{{ 'Contracts' | translate }}</strong>
                <a class="btn btn-sm btn-outline-primary" routerLink="/admin/hr/contracts/new" [queryParams]="{ employeeId: e.id }">{{ '+ New contract' | translate }}</a>
              </div>
              <table class="table-sm" *ngIf="contracts.length">
                <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Salary' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
                <tbody><tr *ngFor="let c of contracts">
                  <td><a routerLink="/admin/hr/contracts">{{ c.contractNumber }}</a></td><td>{{ c.type }}</td><td>{{ c.startDate }}</td><td>{{ c.endDate || ('Open' | translate) }}</td>
                  <td>{{ c.baseSalary | number: '1.0-2' }}</td><td>{{ c.status }}</td>
                </tr></tbody>
              </table>
              <p class="text-muted" *ngIf="!contracts.length">{{ 'No contracts yet.' | translate }}</p>
            </div>
            <app-employee-final-pay *ngIf="canContracts && e.status === 'TERMINATED'" [employeeId]="e.id" [canManage]="canPayrollManage"></app-employee-final-pay>
            <app-employee-documents [employeeId]="e.id" [canManage]="canManage"></app-employee-documents>
            <app-employee-checklists [employeeId]="e.id" [canManage]="canManage"></app-employee-checklists>
            <div class="card cert-card" style="padding:1rem;" *ngIf="canContracts">
              <strong class="mb-2">{{ 'Annual earnings and tax certificate' | translate }}</strong>
              <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                <input class="form-control cert-year" type="number" style="width:6rem;" name="cy" [(ngModel)]="certYear" />
                <app-paper-button class="cert-pdf" [label]="'Certificate (PDF)' | translate" [path]="'/hr/payroll/certificate?employeeId=' + e.id + '&year=' + certYear" [file]="'certificate-' + certYear + '.pdf'"></app-paper-button>
              </div>
              <div class="text-muted" style="font-size:0.8rem;">{{ 'Month by month gross pay, income tax and contributions of the approved and paid payrolls of that year.' | translate }}</div>
            </div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class EmployeeDetailComponent implements OnInit {
  e: Employee | null = null;
  balances: LeaveBalance[] = [];
  leaves: LeaveRequest[] = [];
  contracts: Contract[] = [];
  canContracts = false;
  canManage = false;
  login: EmployeeLoginCreated | null = null;
  canPayrollManage = false;
  error: string | null = null;
  showLeft = false;
  leftDate = new Date().toISOString().slice(0, 10);
  leftReason = '';
  year = new Date().getFullYear();
  certYear = new Date().getFullYear();

  constructor(private api: HrApiService, private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  ngOnInit(): void {
    this.canContracts = this.auth.canAccessPath('/admin/hr/contracts');
    this.canManage = this.auth.canAccessPath('/admin/hr/employees/new');
    this.canPayrollManage = this.auth.canAccessPath('/admin/hr/payroll/new');
    this.load();
  }

  private load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.employee(id).subscribe({
      next: (e) => {
        this.e = e;
        this.api.leaveBalances(this.year, id).subscribe({ next: (b) => (this.balances = b.filter((x) => !x.unlimited || x.used > 0 || x.pending > 0)), error: () => {} });
        this.api.leaves({ employeeId: id }).subscribe({ next: (l) => (this.leaves = l.slice(0, 10)), error: () => {} });
        if (this.canContracts) {
          this.api.contracts({ employeeId: id }).subscribe({ next: (c) => (this.contracts = c), error: () => {} });
        }
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the employee'),
    });
  }

  statusLabel(s: string): string {
    return s === 'ACTIVE' ? 'Active' : s === 'INACTIVE' ? 'Inactive' : 'Left the company';
  }

  typeLabel(t: string): string {
    return t.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  }

  setStatus(status: 'ACTIVE' | 'INACTIVE'): void {
    if (!this.e) return;
    this.api.setEmployeeStatus(this.e.id, status).subscribe({
      next: (e) => {
        this.e = e;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not change the status'),
    });
  }

  left(): void {
    if (!this.e) return;
    this.api.terminateEmployee(this.e.id, this.leftDate, this.leftReason || null).subscribe({
      next: () => {
        this.showLeft = false;
        this.error = null;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not record that the employee left'),
    });
  }

  giveLogin(): void {
    if (!this.e || !confirm('Give ' + this.e.fullName + ' a login? A username and a first password are made for them.')) return;
    this.api.createEmployeeLogin(this.e.id).subscribe({
      next: (r) => {
        this.login = r;
        this.error = null;
        this.load();
      },
      error: (x) => (this.error = x?.error?.message ?? 'Could not create the login'),
    });
  }

  resetLogin(): void {
    if (!this.e || !confirm('Make a new password for ' + this.e.fullName + '? The old one stops working.')) return;
    this.api.resetEmployeeLoginPassword(this.e.id).subscribe({
      next: (r) => {
        this.login = r;
        this.error = null;
      },
      error: (x) => (this.error = x?.error?.message ?? 'Could not reset the password'),
    });
  }

  remove(): void {
    if (!this.e || !confirm(`Delete ${this.e.fullName}? This only works when they have no contracts, leave or attendance records.`)) return;
    this.api.deleteEmployee(this.e.id).subscribe({
      next: () => this.router.navigate(['/admin/hr/employees']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the employee'),
    });
  }
}
