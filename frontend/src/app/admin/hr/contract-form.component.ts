import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ContractComponent, ContractPayload, Employee, HrApiService } from '../../core/api/hr-api.service';

/** Contracts > New contract / Edit contract: type, dates, monthly salary and allowances or fixed deductions. */
@Component({
  selector: 'app-contract-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit contract' : 'New contract' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem;" (ngSubmit)="save()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Employee</label>
            <select class="form-select" name="emp" [(ngModel)]="f.employeeId" [disabled]="!!id" (ngModelChange)="onEmployee()" required>
              <option [ngValue]="0" disabled>Choose…</option>
              <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }} ({{ e.employeeNumber }})</option>
            </select></div>
          <div class="form-group"><label class="form-label">Type</label>
            <select class="form-select" name="type" [(ngModel)]="f.type">
              <option value="PERMANENT">Permanent</option><option value="FIXED_TERM">Fixed term</option><option value="PROBATION">Probation</option>
              <option value="CASUAL">Casual</option><option value="INTERNSHIP">Internship</option>
            </select></div>
          <div class="form-group"><label class="form-label">Job title</label><input class="form-control" name="title" [(ngModel)]="f.jobTitle" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Start date</label><app-date-picker name="start" [(ngModel)]="f.startDate" required></app-date-picker></div>
          <div class="form-group" *ngIf="f.type !== 'PERMANENT'"><label class="form-label">End date</label><app-date-picker name="end" [(ngModel)]="f.endDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">Monthly basic salary</label><input class="form-control" type="number" min="0" step="0.01" name="salary" [(ngModel)]="f.baseSalary" required /></div>
        </div>

        <strong class="mb-2 mt-2">Allowances and fixed deductions (monthly)</strong>
        <table class="table-sm" *ngIf="f.components.length">
          <thead><tr><th>Name</th><th>Kind</th><th>Amount</th><th>Taxable</th><th></th></tr></thead>
          <tbody><tr *ngFor="let c of f.components; let i = index">
            <td><input class="form-control" [name]="'cn' + i" [(ngModel)]="c.name" placeholder="e.g. Housing allowance" /></td>
            <td><select class="form-select" [name]="'ck' + i" [(ngModel)]="c.kind"><option value="EARNING">Allowance (added)</option><option value="DEDUCTION">Deduction (taken)</option></select></td>
            <td><input class="form-control" type="number" min="0" step="0.01" style="width:9rem;" [name]="'ca' + i" [(ngModel)]="c.amount" /></td>
            <td><input type="checkbox" [name]="'ct' + i" [(ngModel)]="c.taxable" [disabled]="c.kind === 'DEDUCTION'" /></td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="f.components.splice(i, 1)">Remove</button></td>
          </tr></tbody>
        </table>
        <div class="mb-2"><button class="btn btn-sm btn-outline-primary" type="button" (click)="addComponent()">+ Add line</button></div>
        <div class="text-muted" style="font-size:0.8rem;">Taxable allowances count towards income tax and contributions. A deduction (for example a loan repayment) is taken from the net pay after tax.</div>

        <div class="form-group mt-2"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="notes" [(ngModel)]="f.notes"></textarea></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">Save as draft</button>
          <a class="btn btn-link" routerLink="/admin/hr/contracts">Cancel</a>
        </div>
        <div class="text-muted" style="font-size:0.8rem;" *ngIf="!id">A new contract is saved as a draft — activate it from the contract list when it's signed.</div>
      </form>
    </app-shell>
  `,
})
export class ContractFormComponent implements OnInit {
  id: number | null = null;
  employees: Employee[] = [];
  saving = false;
  error: string | null = null;
  f: ContractPayload = {
    employeeId: 0, type: 'PERMANENT', startDate: new Date().toISOString().slice(0, 10), endDate: null, jobTitle: null, baseSalary: 0, notes: null, components: [],
  };

  constructor(private api: HrApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({
      next: (e) => {
        this.employees = e;
        const pre = Number(this.route.snapshot.queryParamMap.get('employeeId'));
        if (!this.id && pre && e.some((x) => x.id === pre)) {
          this.f.employeeId = pre;
          this.onEmployee();
        }
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load employees'),
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.api.contract(this.id).subscribe({
        next: (c) => {
          this.f = {
            employeeId: c.employeeId, type: c.type, startDate: c.startDate, endDate: c.endDate, jobTitle: c.jobTitle, baseSalary: c.baseSalary, notes: c.notes,
            components: c.components.map((x) => ({ name: x.name, kind: x.kind, amount: x.amount, taxable: x.taxable })),
          };
          if (!this.employees.some((e) => e.id === c.employeeId)) {
            this.employees = [...this.employees, { id: c.employeeId, fullName: c.employeeName, employeeNumber: c.employeeNumber } as Employee];
          }
        },
        error: (e) => (this.error = e?.error?.message ?? 'Failed to load the contract'),
      });
    }
  }

  onEmployee(): void {
    const e = this.employees.find((x) => x.id === this.f.employeeId);
    if (e && !this.f.jobTitle) this.f.jobTitle = e.jobTitle;
  }

  addComponent(): void {
    this.f.components.push({ name: '', kind: 'EARNING', amount: 0, taxable: true } as ContractComponent);
  }

  save(): void {
    if (!this.f.employeeId) {
      this.error = 'Choose the employee.';
      return;
    }
    this.error = null;
    this.saving = true;
    const payload: ContractPayload = {
      ...this.f,
      endDate: this.f.type === 'PERMANENT' ? null : this.f.endDate || null,
      components: this.f.components.filter((c) => c.name.trim()).map((c) => ({ ...c, taxable: c.kind === 'DEDUCTION' ? false : c.taxable })),
    };
    const call = this.id ? this.api.updateContract(this.id, payload) : this.api.createContract(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/hr/contracts']),
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message ?? 'Could not save the contract';
      },
    });
  }
}
