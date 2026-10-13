import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { AuthService } from '../../core/auth.service';
import { Contract, Employee, HrApiService, LeaveBalance, LeaveRequest } from '../../core/api/hr-api.service';

/** Employees > one employee: details, leave balances and requests, contracts and the actions (edit, leave of absence, left the company). */
@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="e">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h3>{{ e.fullName }} <span class="badge" [class.badge-success]="e.status === 'ACTIVE'" [class.badge-warning]="e.status === 'INACTIVE'" [class.badge-secondary]="e.status === 'TERMINATED'">{{ statusLabel(e.status) }}</span></h3>
            <div class="text-muted">{{ e.employeeNumber }} · {{ e.jobTitle || 'No job title' }}{{ e.departmentName ? ' · ' + e.departmentName : '' }}</div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-primary" [routerLink]="['/admin/hr/employees', e.id, 'edit']">Edit</a>
            <button class="btn btn-outline-secondary" type="button" *ngIf="e.status === 'ACTIVE'" (click)="setStatus('INACTIVE')">Mark inactive</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="e.status !== 'ACTIVE'" (click)="setStatus('ACTIVE')">{{ e.status === 'TERMINATED' ? 'Re-hire' : 'Mark active' }}</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="e.status !== 'TERMINATED'" (click)="showLeft = !showLeft">Employee left</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()">Delete</button>
          </div>
        </div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showLeft" (ngSubmit)="left()">
          <strong class="mb-2">Employee left the company</strong>
          <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">Last day</label><app-date-picker name="ld" [(ngModel)]="leftDate"></app-date-picker></div>
            <div class="form-group" style="min-width:16rem;"><label class="form-label">Reason</label><input class="form-control" name="lr" [(ngModel)]="leftReason" /></div>
            <button class="btn btn-danger" type="submit">Confirm</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">This also ends their active contract on that date.</div>
        </form>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <div class="card" style="padding:1rem; flex:1; min-width:18rem;">
            <strong class="mb-2">Details</strong>
            <table class="table-sm">
              <tr><td class="text-muted">Hired</td><td>{{ e.hireDate }}</td></tr>
              <tr *ngIf="e.terminationDate"><td class="text-muted">Left</td><td>{{ e.terminationDate }}{{ e.terminationReason ? ' — ' + e.terminationReason : '' }}</td></tr>
              <tr><td class="text-muted">Employment</td><td>{{ typeLabel(e.employmentType) }}</td></tr>
              <tr><td class="text-muted">Position</td><td>{{ e.positionTitle || '—' }}</td></tr>
              <tr><td class="text-muted">Reports to</td><td>{{ e.managerName || '—' }}</td></tr>
              <tr *ngIf="e.branchName"><td class="text-muted">Branch</td><td>{{ e.branchName }}</td></tr>
              <tr><td class="text-muted">Date of birth</td><td>{{ e.dateOfBirth || '—' }}</td></tr>
              <tr><td class="text-muted">National ID</td><td>{{ e.nationalId || '—' }}</td></tr>
              <tr><td class="text-muted">Phone</td><td>{{ e.phone || '—' }}</td></tr>
              <tr><td class="text-muted">Email</td><td>{{ e.email || '—' }}</td></tr>
              <tr><td class="text-muted">Address</td><td>{{ e.address || '—' }}</td></tr>
              <tr><td class="text-muted">Emergency contact</td><td>{{ e.emergencyContactName || '—' }}{{ e.emergencyContactPhone ? ' · ' + e.emergencyContactPhone : '' }}</td></tr>
              <tr><td class="text-muted">Staff login</td><td>{{ e.userLabel || '—' }}</td></tr>
              <tr><td class="text-muted">Bank</td><td>{{ e.bankName || '—' }}{{ e.bankAccount ? ' · ' + e.bankAccount : '' }}</td></tr>
              <tr><td class="text-muted">Tax number</td><td>{{ e.taxNumber || '—' }}</td></tr>
              <tr><td class="text-muted">Social security no.</td><td>{{ e.socialSecurityNumber || '—' }}</td></tr>
              <tr><td class="text-muted">Attendance counts for pay</td><td>{{ e.attendanceTracked ? 'Yes' : 'No' }}</td></tr>
            </table>
            <p class="text-muted" *ngIf="e.notes" style="margin-top:0.5rem;">{{ e.notes }}</p>
          </div>

          <div style="flex:1.4; min-width:20rem;">
            <div class="card mb-3" style="padding:1rem;" *ngIf="balances.length">
              <strong class="mb-2">Leave balances {{ year }}</strong>
              <table class="table-sm">
                <thead><tr><th>Leave type</th><th>Entitled</th><th>Used</th><th>Pending</th><th>Remaining</th></tr></thead>
                <tbody><tr *ngFor="let b of balances">
                  <td>{{ b.leaveTypeName }}</td><td>{{ b.unlimited ? 'No limit' : b.entitled }}</td><td>{{ b.used }}</td><td>{{ b.pending }}</td><td>{{ b.unlimited ? '—' : b.remaining }}</td>
                </tr></tbody>
              </table>
            </div>
            <div class="card mb-3" style="padding:1rem;" *ngIf="leaves.length">
              <strong class="mb-2">Leave requests</strong>
              <table class="table-sm">
                <thead><tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Status</th></tr></thead>
                <tbody><tr *ngFor="let l of leaves">
                  <td>{{ l.leaveTypeName }}</td><td>{{ l.startDate }}</td><td>{{ l.endDate }}</td><td>{{ l.days }}</td><td>{{ l.status }}</td>
                </tr></tbody>
              </table>
            </div>
            <div class="card" style="padding:1rem;" *ngIf="canContracts">
              <div class="flex justify-between align-center mb-2">
                <strong>Contracts</strong>
                <a class="btn btn-sm btn-outline-primary" routerLink="/admin/hr/contracts/new" [queryParams]="{ employeeId: e.id }">+ New contract</a>
              </div>
              <table class="table-sm" *ngIf="contracts.length">
                <thead><tr><th>No.</th><th>Type</th><th>From</th><th>To</th><th>Salary</th><th>Status</th></tr></thead>
                <tbody><tr *ngFor="let c of contracts">
                  <td><a routerLink="/admin/hr/contracts">{{ c.contractNumber }}</a></td><td>{{ c.type }}</td><td>{{ c.startDate }}</td><td>{{ c.endDate || 'Open' }}</td>
                  <td>{{ c.baseSalary | number: '1.0-2' }}</td><td>{{ c.status }}</td>
                </tr></tbody>
              </table>
              <p class="text-muted" *ngIf="!contracts.length">No contracts yet.</p>
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
  error: string | null = null;
  showLeft = false;
  leftDate = new Date().toISOString().slice(0, 10);
  leftReason = '';
  year = new Date().getFullYear();

  constructor(private api: HrApiService, private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  ngOnInit(): void {
    this.canContracts = this.auth.canAccessPath('/admin/hr/contracts');
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

  remove(): void {
    if (!this.e || !confirm(`Delete ${this.e.fullName}? This only works when they have no contracts, leave or attendance records.`)) return;
    this.api.deleteEmployee(this.e.id).subscribe({
      next: () => this.router.navigate(['/admin/hr/employees']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the employee'),
    });
  }
}
