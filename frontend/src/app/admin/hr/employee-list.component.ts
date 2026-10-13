import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { Department, Employee, HrApiService } from '../../core/api/hr-api.service';

/** Employees > Employee list: everyone who works for the business, with quick filters. */
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Employees</h3>
        <a class="btn btn-primary" routerLink="/admin/hr/employees/new">+ New employee</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <input class="form-control" style="width:16rem;" placeholder="Search name, number, title…" [(ngModel)]="q" (ngModelChange)="load()" name="q" />
        <select class="form-select" style="width:auto;" [(ngModel)]="status" (ngModelChange)="load()" name="status">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
          <option value="TERMINATED">Left the company</option>
        </select>
        <select class="form-select" style="width:auto;" [(ngModel)]="departmentId" (ngModelChange)="load()" name="dept">
          <option [ngValue]="null">All departments</option>
          <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchId" (ngModelChange)="load()" name="branch">
          <option [ngValue]="null">All branches</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead><tr><th>No.</th><th>Name</th><th>Job title</th><th>Department</th><th>Reports to</th><th *ngIf="branches.length > 1">Branch</th><th>Hired</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngFor="let e of employees">
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
      <p class="text-muted" *ngIf="!loading && employees.length === 0">No employees found.</p>
    </app-shell>
  `,
})
export class EmployeeListComponent implements OnInit {
  employees: Employee[] = [];
  departments: Department[] = [];
  branches: Branch[] = [];
  q = '';
  status = 'ACTIVE';
  departmentId: number | null = null;
  branchId: number | null = null;
  loading = false;
  error: string | null = null;

  constructor(private api: HrApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.employees({ status: this.status, departmentId: this.departmentId, branchId: this.branchId, q: this.q }).subscribe({
      next: (e) => {
        this.employees = e;
        this.loading = false;
        this.error = null;
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.error?.message ?? 'Failed to load employees';
      },
    });
  }

  statusLabel(s: string): string {
    return s === 'ACTIVE' ? 'Active' : s === 'INACTIVE' ? 'Inactive' : 'Left';
  }
}
