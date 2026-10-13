import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { Department, DepartmentPayload, Employee, HrApiService, Position, PositionPayload } from '../../core/api/hr-api.service';

/** Organizational Structure > Departments and positions. */
@Component({
  selector: 'app-org-setup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Departments and positions</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/org/chart">Organization chart</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
        <div style="flex:1; min-width:24rem;">
          <h4 class="mb-2">Departments</h4>
          <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveDept()">
            <strong class="mb-2">{{ deptId ? 'Edit department' : 'New department' }}</strong>
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group"><label class="form-label">Name</label><input class="form-control" name="dn" [(ngModel)]="dept.name" required /></div>
              <div class="form-group"><label class="form-label">Code</label><input class="form-control" style="width:6rem;" name="dc" [(ngModel)]="dept.code" /></div>
              <div class="form-group"><label class="form-label">Part of</label>
                <select class="form-select" name="dp" [(ngModel)]="dept.parentId">
                  <option [ngValue]="null">— (top level)</option>
                  <option *ngFor="let d of departments" [ngValue]="d.id" [disabled]="d.id === deptId">{{ d.name }}</option>
                </select></div>
              <div class="form-group"><label class="form-label">Manager</label>
                <select class="form-select" name="dm" [(ngModel)]="dept.managerId">
                  <option [ngValue]="null">—</option>
                  <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
                </select></div>
              <div class="form-group" *ngIf="deptId"><label class="form-label"><input type="checkbox" name="da" [(ngModel)]="dept.active" /> Active</label></div>
              <div class="flex gap-2">
                <button class="btn btn-primary" type="submit">Save</button>
                <button class="btn btn-link" type="button" *ngIf="deptId" (click)="resetDept()">Cancel</button>
              </div>
            </div>
          </form>
          <table class="table-sm">
            <thead><tr><th>Name</th><th>Part of</th><th>Manager</th><th>People</th><th>Status</th><th></th></tr></thead>
            <tbody><tr *ngFor="let d of departments">
              <td>{{ d.name }}<span class="text-muted" *ngIf="d.code"> ({{ d.code }})</span></td>
              <td>{{ d.parentName || '—' }}</td><td>{{ d.managerName || '—' }}</td><td>{{ d.employeeCount }}</td>
              <td><span class="badge" [class.badge-success]="d.active" [class.badge-secondary]="!d.active">{{ d.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="flex gap-1">
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="editDept(d)">Edit</button>
                <button class="btn btn-sm btn-outline-danger" type="button" (click)="delDept(d)">Delete</button>
              </td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="!departments.length">No departments yet — for example Sales, Finance, Operations.</p>
        </div>

        <div style="flex:1; min-width:24rem;">
          <h4 class="mb-2">Positions</h4>
          <form class="card mb-3" style="padding:1rem;" (ngSubmit)="savePos()">
            <strong class="mb-2">{{ posId ? 'Edit position' : 'New position' }}</strong>
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group"><label class="form-label">Title</label><input class="form-control" name="pt" [(ngModel)]="pos.title" required /></div>
              <div class="form-group"><label class="form-label">Department</label>
                <select class="form-select" name="pd" [(ngModel)]="pos.departmentId">
                  <option [ngValue]="null">—</option>
                  <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
                </select></div>
              <div class="form-group"><label class="form-label">Planned headcount</label><input class="form-control" type="number" min="0" style="width:7rem;" name="ph" [(ngModel)]="pos.plannedHeadcount" /></div>
              <div class="form-group" *ngIf="posId"><label class="form-label"><input type="checkbox" name="pa" [(ngModel)]="pos.active" /> Active</label></div>
              <div class="flex gap-2">
                <button class="btn btn-primary" type="submit">Save</button>
                <button class="btn btn-link" type="button" *ngIf="posId" (click)="resetPos()">Cancel</button>
              </div>
            </div>
            <div class="form-group"><label class="form-label">Description</label><input class="form-control" name="pdesc" [(ngModel)]="pos.description" /></div>
          </form>
          <table class="table-sm">
            <thead><tr><th>Title</th><th>Department</th><th>Filled / planned</th><th>Status</th><th></th></tr></thead>
            <tbody><tr *ngFor="let p of positions">
              <td>{{ p.title }}</td><td>{{ p.departmentName || '—' }}</td>
              <td>{{ p.filled }}{{ p.plannedHeadcount ? ' / ' + p.plannedHeadcount : '' }}</td>
              <td><span class="badge" [class.badge-success]="p.active" [class.badge-secondary]="!p.active">{{ p.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="flex gap-1">
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="editPos(p)">Edit</button>
                <button class="btn btn-sm btn-outline-danger" type="button" (click)="delPos(p)">Delete</button>
              </td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="!positions.length">No positions yet — for example Cashier, Accountant, Store manager.</p>
        </div>
      </div>
    </app-shell>
  `,
})
export class OrgSetupComponent implements OnInit {
  departments: Department[] = [];
  positions: Position[] = [];
  employees: Employee[] = [];
  error: string | null = null;
  deptId: number | null = null;
  posId: number | null = null;
  dept: DepartmentPayload = OrgSetupComponent.blankDept();
  pos: PositionPayload = OrgSetupComponent.blankPos();

  constructor(private api: HrApiService) {}

  private static blankDept(): DepartmentPayload {
    return { name: '', code: null, parentId: null, managerId: null, active: true };
  }

  private static blankPos(): PositionPayload {
    return { title: '', departmentId: null, description: null, plannedHeadcount: 0, active: true };
  }

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: (e) => (this.error = e?.error?.message ?? 'Failed to load departments') });
    this.api.positions().subscribe({ next: (p) => (this.positions = p), error: (e) => (this.error = e?.error?.message ?? 'Failed to load positions') });
  }

  editDept(d: Department): void {
    this.deptId = d.id;
    this.dept = { name: d.name, code: d.code, parentId: d.parentId, managerId: d.managerId, active: d.active };
  }

  resetDept(): void {
    this.deptId = null;
    this.dept = OrgSetupComponent.blankDept();
  }

  saveDept(): void {
    this.error = null;
    const call = this.deptId ? this.api.updateDepartment(this.deptId, this.dept) : this.api.createDepartment(this.dept);
    call.subscribe({
      next: () => {
        this.resetDept();
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the department'),
    });
  }

  delDept(d: Department): void {
    if (!confirm(`Delete department ${d.name}?`)) return;
    this.api.deleteDepartment(d.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the department') });
  }

  editPos(p: Position): void {
    this.posId = p.id;
    this.pos = { title: p.title, departmentId: p.departmentId, description: p.description, plannedHeadcount: p.plannedHeadcount, active: p.active };
  }

  resetPos(): void {
    this.posId = null;
    this.pos = OrgSetupComponent.blankPos();
  }

  savePos(): void {
    this.error = null;
    const call = this.posId ? this.api.updatePosition(this.posId, this.pos) : this.api.createPosition(this.pos);
    call.subscribe({
      next: () => {
        this.resetPos();
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the position'),
    });
  }

  delPos(p: Position): void {
    if (!confirm(`Delete position ${p.title}?`)) return;
    this.api.deletePosition(p.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the position') });
  }
}
