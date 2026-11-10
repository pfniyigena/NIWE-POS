import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
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
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? ('Edit employee' | translate) : ('New employee' | translate) }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="save()" class="card" style="padding:1rem;">
        <strong class="mb-2">{{ 'Personal details' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'First name' | translate }}</label><input class="form-control" name="first" [(ngModel)]="f.firstName" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Last name' | translate }}</label><input class="form-control" name="last" [(ngModel)]="f.lastName" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Date of birth' | translate }}</label><app-date-picker name="dob" [(ngModel)]="f.dateOfBirth"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'National ID' | translate }}</label><input class="form-control" name="nid" [(ngModel)]="f.nationalId" /></div>
          <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="phone" [(ngModel)]="f.phone" /></div>
          <div class="form-group"><label class="form-label">{{ 'Email' | translate }}</label><input class="form-control" type="email" name="email" [(ngModel)]="f.email" /></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Address' | translate }}</label><input class="form-control" name="address" [(ngModel)]="f.address" /></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Emergency contact' | translate }}</label><input class="form-control" name="ecn" [(ngModel)]="f.emergencyContactName" /></div>
          <div class="form-group"><label class="form-label">{{ 'Emergency phone' | translate }}</label><input class="form-control" name="ecp" [(ngModel)]="f.emergencyContactPhone" /></div>
        </div>

        <strong class="mb-2 mt-3">{{ 'Job' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Employee number' | translate }}</label>
            <input class="form-control" name="number" [(ngModel)]="f.employeeNumber" [placeholder]="'Automatic (EMP-0001…)' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Hire date' | translate }}</label><app-date-picker name="hire" [(ngModel)]="f.hireDate" required></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Employment type' | translate }}</label>
            <select class="form-select" name="etype" [(ngModel)]="f.employmentType">
              <option value="FULL_TIME">{{ 'Full time' | translate }}</option><option value="PART_TIME">{{ 'Part time' | translate }}</option>
              <option value="CASUAL">{{ 'Casual' | translate }}</option><option value="INTERN">{{ 'Intern' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Job title' | translate }}</label><input class="form-control" name="title" [(ngModel)]="f.jobTitle" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Department' | translate }}</label>
            <select class="form-select" name="dept" [(ngModel)]="f.departmentId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Position' | translate }}</label>
            <select class="form-select" name="pos" [(ngModel)]="f.positionId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let p of positions" [ngValue]="p.id">{{ p.title }}{{ p.departmentName ? ' (' + p.departmentName + ')' : '' }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Reports to' | translate }}</label>
            <select class="form-select" name="mgr" [(ngModel)]="f.managerId">
              <option [ngValue]="null">—</option>
              <option *ngFor="let m of managers" [ngValue]="m.id">{{ m.fullName }}</option>
            </select></div>
          <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">{{ 'Branch' | translate }}</label>
            <select class="form-select" name="branch" [(ngModel)]="f.branchId">
              <option [ngValue]="null">{{ 'Head office' | translate }}</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select></div>
        </div>
        <div class="form-group" *ngIf="logins.length">
          <label class="form-label">{{ 'Staff login (lets them clock in and out themselves)' | translate }}</label>
          <select class="form-select" name="login" [(ngModel)]="f.userId">
            <option [ngValue]="null">{{ 'None' | translate }}</option>
            <option *ngFor="let u of logins" [ngValue]="u.id">{{ u.username }}{{ u.fullName ? ' — ' + u.fullName : '' }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label"><input type="checkbox" name="tracked" [(ngModel)]="f.attendanceTracked" /> {{ 'Track attendance for pay' | translate }}</label>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'When ticked, payroll deducts absent days and pays overtime from the attendance records. Leave it off for salaried staff who don\\'t clock in.' | translate }}</div>
        </div>

        <strong class="mb-2 mt-3">{{ 'Bank and statutory numbers' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Bank' | translate }}</label><input class="form-control" name="bank" [(ngModel)]="f.bankName" /></div>
          <div class="form-group"><label class="form-label">{{ 'Account number' | translate }}</label><input class="form-control" name="acct" [(ngModel)]="f.bankAccount" /></div>
          <div class="form-group"><label class="form-label">{{ 'Tax number' | translate }}</label><input class="form-control" name="tax" [(ngModel)]="f.taxNumber" /></div>
          <div class="form-group"><label class="form-label">{{ 'Social security number' | translate }}</label><input class="form-control" name="ssn" [(ngModel)]="f.socialSecurityNumber" /></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="2" name="notes" [(ngModel)]="f.notes"></textarea></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" [routerLink]="id ? ['/admin/hr/employees', id] : ['/admin/hr/employees']">{{ 'Cancel' | translate }}</a>
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
