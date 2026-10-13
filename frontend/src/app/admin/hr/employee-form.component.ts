import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { Department, Employee, EmployeePayload, HrApiService, Position, StaffLogin } from '../../core/api/hr-api.service';

/** Employees > New employee / Edit employee. */
@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit employee' : 'New employee' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="save()" class="card" style="padding:1rem;">
        <strong class="mb-2">Personal details</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">First name</label><input class="form-control" name="first" [(ngModel)]="f.firstName" required /></div>
          <div class="form-group"><label class="form-label">Last name</label><input class="form-control" name="last" [(ngModel)]="f.lastName" required /></div>
          <div class="form-group"><label class="form-label">Date of birth</label><app-date-picker name="dob" [(ngModel)]="f.dateOfBirth"></app-date-picker></div>
          <div class="form-group"><label class="form-label">National ID</label><input class="form-control" name="nid" [(ngModel)]="f.nationalId" /></div>
          <div class="form-group"><label class="form-label">Phone</label><input class="form-control" name="phone" [(ngModel)]="f.phone" /></div>
          <div class="form-group"><label class="form-label">Email</label><input class="form-control" type="email" name="email" [(ngModel)]="f.email" /></div>
        </div>
        <div class="form-group"><label class="form-label">Address</label><input class="form-control" name="address" [(ngModel)]="f.address" /></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Emergency contact</label><input class="form-control" name="ecn" [(ngModel)]="f.emergencyContactName" /></div>
          <div class="form-group"><label class="form-label">Emergency phone</label><input class="form-control" name="ecp" [(ngModel)]="f.emergencyContactPhone" /></div>
        </div>

        <strong class="mb-2 mt-3">Job</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Employee number</label>
            <input class="form-control" name="number" [(ngModel)]="f.employeeNumber" placeholder="Automatic (EMP-0001…)" /></div>
          <div class="form-group"><label class="form-label">Hire date</label><app-date-picker name="hire" [(ngModel)]="f.hireDate" required></app-date-picker></div>
          <div class="form-group"><label class="form-label">Employment type</label>
            <select class="form-select" name="etype" [(ngModel)]="f.employmentType">
              <option value="FULL_TIME">Full time</option><option value="PART_TIME">Part time</option>
              <option value="CASUAL">Casual</option><option value="INTERN">Intern</option>
            </select></div>
          <div class="form-group"><label class="form-label">Job title</label><input class="form-control" name="title" [(ngModel)]="f.jobTitle" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Department</label>
            <select class="form-select" name="dept" [(ngModel)]="f.departmentId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">Position</label>
            <select class="form-select" name="pos" [(ngModel)]="f.positionId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let p of positions" [ngValue]="p.id">{{ p.title }}{{ p.departmentName ? ' (' + p.departmentName + ')' : '' }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">Reports to</label>
            <select class="form-select" name="mgr" [(ngModel)]="f.managerId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let m of managers" [ngValue]="m.id">{{ m.fullName }}</option>
            </select></div>
          <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">Branch</label>
            <select class="form-select" name="branch" [(ngModel)]="f.branchId">
              <option [ngValue]="null">Head office</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select></div>
        </div>
        <div class="form-group" *ngIf="logins.length">
          <label class="form-label">Staff login (lets them clock in and out themselves)</label>
          <select class="form-select" name="login" [(ngModel)]="f.userId">
            <option [ngValue]="null">None</option>
            <option *ngFor="let u of logins" [ngValue]="u.id">{{ u.username }}{{ u.fullName ? ' — ' + u.fullName : '' }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label"><input type="checkbox" name="tracked" [(ngModel)]="f.attendanceTracked" /> Track attendance for pay</label>
          <div class="text-muted" style="font-size:0.8rem;">When ticked, payroll deducts absent days and pays overtime from the attendance records. Leave it off for salaried staff who don't clock in.</div>
        </div>

        <strong class="mb-2 mt-3">Bank and statutory numbers</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Bank</label><input class="form-control" name="bank" [(ngModel)]="f.bankName" /></div>
          <div class="form-group"><label class="form-label">Account number</label><input class="form-control" name="acct" [(ngModel)]="f.bankAccount" /></div>
          <div class="form-group"><label class="form-label">Tax number</label><input class="form-control" name="tax" [(ngModel)]="f.taxNumber" /></div>
          <div class="form-group"><label class="form-label">Social security number</label><input class="form-control" name="ssn" [(ngModel)]="f.socialSecurityNumber" /></div>
        </div>
        <div class="form-group"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="notes" [(ngModel)]="f.notes"></textarea></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">Save</button>
          <a class="btn btn-link" [routerLink]="id ? ['/admin/hr/employees', id] : ['/admin/hr/employees']">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class EmployeeFormComponent implements OnInit {
  id: number | null = null;
  departments: Department[] = [];
  positions: Position[] = [];
  managers: Employee[] = [];
  branches: Branch[] = [];
  logins: StaffLogin[] = [];
  saving = false;
  error: string | null = null;
  f: EmployeePayload = {
    employeeNumber: null, firstName: '', lastName: '', dateOfBirth: null, nationalId: null, phone: null, email: null, address: null,
    emergencyContactName: null, emergencyContactPhone: null, hireDate: new Date().toISOString().slice(0, 10), employmentType: 'FULL_TIME',
    jobTitle: null, departmentId: null, positionId: null, managerId: null, branchId: null, userId: null, bankName: null, bankAccount: null,
    taxNumber: null, socialSecurityNumber: null, attendanceTracked: false, notes: null,
  };

  constructor(private api: HrApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.departments().subscribe({ next: (d) => (this.departments = d.filter((x) => x.active)), error: () => {} });
    this.api.positions().subscribe({ next: (p) => (this.positions = p.filter((x) => x.active)), error: () => {} });
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.managers = e), error: () => {} });
    this.api.staffLogins().subscribe({ next: (u) => (this.logins = u), error: () => {} });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.api.employee(this.id).subscribe({
        next: (e) => {
          this.f = {
            employeeNumber: e.employeeNumber, firstName: e.firstName, lastName: e.lastName, dateOfBirth: e.dateOfBirth, nationalId: e.nationalId,
            phone: e.phone, email: e.email, address: e.address, emergencyContactName: e.emergencyContactName,
            emergencyContactPhone: e.emergencyContactPhone, hireDate: e.hireDate, employmentType: e.employmentType, jobTitle: e.jobTitle,
            departmentId: e.departmentId, positionId: e.positionId, managerId: e.managerId, branchId: e.branchId, userId: e.userId,
            bankName: e.bankName, bankAccount: e.bankAccount, taxNumber: e.taxNumber, socialSecurityNumber: e.socialSecurityNumber,
            attendanceTracked: e.attendanceTracked, notes: e.notes,
          };
          this.managers = this.managers.filter((m) => m.id !== e.id);
        },
        error: (e) => (this.error = e?.error?.message ?? 'Failed to load the employee'),
      });
    }
  }

  save(): void {
    this.error = null;
    this.saving = true;
    const call = this.id ? this.api.updateEmployee(this.id, this.f) : this.api.createEmployee(this.f);
    call.subscribe({
      next: (e) => this.router.navigate(['/admin/hr/employees', e.id]),
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message ?? 'Could not save the employee';
      },
    });
  }
}
