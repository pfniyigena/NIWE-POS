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
import { Department, Employee, HrApiService } from '../../core/api/hr-api.service';

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
          <a class="btn btn-primary" routerLink="/admin/hr/employees/new">+ New employee</a>
        </div>
      </div>
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

  constructor(private api: HrApiService, private branchApi: BranchApiService, private auth: AuthService) {
    this.list.filter = { ...emptyFilter(), status: 'ACTIVE' };
  }

  ngOnInit(): void {
    this.canImport = this.auth.canAccessPath('/admin/hr/employees/import');
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.list.load();
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
