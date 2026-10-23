import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState, emptyFilter } from '../../core/api/list-state';
import { AuthService } from '../../core/auth.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { downloadBlob } from '../../core/download-file';
import { Department, Employee, EmployeeLoginBulk, HrApiService } from '../../core/api/hr-api.service';

/** Employees > Employee list: everyone who works for the business, with quick filters. */
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Employees</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-outline-secondary emp-import" *ngIf="canImport" routerLink="/admin/hr/employees/import">Import employees</a>
          <button class="btn btn-outline-secondary emp-logins" type="button" *ngIf="canManage" [disabled]="loginBusy" (click)="giveLogins()">{{ loginBusy ? 'Working…' : 'Give everyone a login' }}</button>
          <a class="btn btn-primary" routerLink="/admin/hr/employees/new">+ New employee</a>
        </div>
      </div>
      <div class="card mb-3 logins-result" style="padding:1rem;" *ngIf="logins">
        <strong>{{ logins.created.length }} login(s) created</strong>
        <div class="text-muted" style="font-size:0.85rem;">Give each person their username and first password now: the passwords are not shown again. They can change it under Profile after signing in, and then open <strong>My HR</strong> to see their own record, leave, attendance and payslips.</div>
        <table class="table-sm mt-2" *ngIf="logins.created.length">
          <thead><tr><th>No.</th><th>Name</th><th>Username</th><th>First password</th></tr></thead>
          <tbody><tr *ngFor="let c of logins.created"><td>{{ c.employeeNumber }}</td><td>{{ c.employeeName }}</td><td>{{ c.username }}</td><td><code>{{ c.password }}</code></td></tr></tbody>
        </table>
        <div class="text-muted mt-2" *ngIf="logins.skipped.length"><div *ngFor="let s of logins.skipped">Skipped — {{ s }}</div></div>
        <div class="flex gap-2 mt-2">
          <button class="btn btn-sm btn-outline-primary logins-csv" type="button" *ngIf="logins.created.length" (click)="downloadLogins()">Download as CSV</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="logins = null">Close</button>
        </div>
      </div>
      <div class="alert alert-info" *ngIf="loginNote">{{ loginNote }}</div>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <app-list-filter exportKind="employees" exportPath="/hr/employees/export" placeholder="Search name, number, title, phone…" [statuses]="statuses" statusAll="All statuses" [dates]="false" [branches]="branches" [initial]="initial" [exportExtra]="extra" (cleared)="departmentId = null; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" *ngIf="departments.length">
        <select class="form-select dept-filter" style="width:auto;" [(ngModel)]="departmentId" (ngModelChange)="departmentChanged()" name="dept">
          <option [ngValue]="null">All departments</option>
          <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead><tr><th>No.</th><th>Name</th><th>Job title</th><th>Department</th><th>Reports to</th><th *ngIf="branches.length > 1">Branch</th><th>Hired</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngFor="let e of list.rows">
            <td>{{ e.employeeNumber }}</td>
            <td><a [routerLink]="['/admin/hr/employees', e.id]">{{ e.fullName }}</a></td>
            <td>{{ e.jobTitle || '—' }}</td>
            <td>{{ e.departmentName || '—' }}</td>
            <td>{{ e.managerName || '—' }}</td>
            <td *ngIf="branches.length > 1">{{ e.branchName || '—' }}</td>
            <td>{{ e.hireDate }}</td>
            <td><span class="badge" [class.badge-success]="e.status === 'ACTIVE'" [class.badge-warning]="e.status === 'INACTIVE'" [class.badge-secondary]="e.status === 'TERMINATED'">{{ statusLabel(e.status) }}</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? 'Nothing matches these filters.' : 'No employees yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class EmployeeListComponent implements OnInit {
  departments: Department[] = [];
  branches: Branch[] = [];
  departmentId: number | null = null;
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }, { value: 'TERMINATED', label: 'Left the company' }];
  /** The list opens on active employees, like before. */
  initial = { status: 'ACTIVE' };
  list = new ListState<Employee>((f, p, sz) => this.api.page<Employee>('employees', f, p, sz, { departmentId: this.departmentId }), 'Failed to load employees');

  canImport = false;
  canManage = false;
  loginBusy = false;
  logins: EmployeeLoginBulk | null = null;
  loginNote: string | null = null;

  constructor(private api: HrApiService, private branchApi: BranchApiService, private auth: AuthService) {
    this.list.filter = { ...emptyFilter(), status: 'ACTIVE' };
  }

  ngOnInit(): void {
    this.canImport = this.auth.canAccessPath('/admin/hr/employees/import');
    this.canManage = this.auth.canAccessPath('/admin/hr/employees/new');
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.list.load();
  }

  giveLogins(): void {
    if (!confirm('Give a login to every employee who has none yet? Each gets a username (their employee number) and a first password.')) return;
    this.loginBusy = true;
    this.loginNote = null;
    this.api.createEmployeeLogins().subscribe({
      next: (r) => {
        this.loginBusy = false;
        this.logins = r;
        if (!r.created.length && !r.skipped.length) this.loginNote = 'Everyone already has a login.';
        this.list.load();
      },
      error: (e) => {
        this.loginBusy = false;
        this.list.error = e?.error?.message ?? 'Could not create the logins';
      },
    });
  }

  downloadLogins(): void {
    if (!this.logins) return;
    const rows = ['Employee number,Name,Username,First password', ...this.logins.created.map((c) => [c.employeeNumber, c.employeeName, c.username, c.password].map((v) => '"' + String(v).replace(/"/g, '""') + '"').join(','))];
    downloadBlob(new Blob([rows.join('\r\n')], { type: 'text/csv' }), 'employee-logins.csv');
  }

  departmentChanged(): void {
    this.extra = { departmentId: this.departmentId };
    this.list.page = 0;
    this.list.load();
  }

  statusLabel(s: string): string {
    return s === 'ACTIVE' ? 'Active' : s === 'INACTIVE' ? 'Inactive' : 'Left';
  }
}
