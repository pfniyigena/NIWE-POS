import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { Employee, HrApiService, LoanPayload, StaffLoan } from '../../core/api/hr-api.service';

import { T } from '../../core/i18n/t';
/** Payroll > Advances & loans: money given to staff and taken back from their pay, month by month. */
@Component({
  selector: 'app-payroll-loans',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Advances &amp; loans</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/adjustments">{{ 'Adjustments' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/runs">{{ 'Payroll runs' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success loan-saved" *ngIf="saved">{{ saved }}</div>

      <form class="card mb-3 loan-form" style="padding:1rem;" (ngSubmit)="give()">
        <strong class="mb-2">{{ 'Give an advance or a loan' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <label>{{ 'Employee' | translate }}
            <select class="form-select loan-employee" style="min-width:14rem;" name="emp" [(ngModel)]="form.employeeId">
              <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
            </select>
          </label>
          <label>{{ 'Kind' | translate }}
            <select class="form-select loan-type" name="type" [(ngModel)]="form.loanType">
              <option value="ADVANCE">{{ 'Salary advance (taken in one go)' | translate }}</option>
              <option value="LOAN">{{ 'Loan (repaid over months)' | translate }}</option>
            </select>
          </label>
          <label>{{ 'Amount given' | translate }}
            <input class="form-control loan-amount" type="number" min="0" step="0.01" style="width:9rem;" name="amount" [(ngModel)]="form.principal" />
          </label>
          <label>{{ 'Taken each month' | translate }}
            <input class="form-control loan-installment" type="number" min="0" step="0.01" style="width:9rem;" name="inst" [(ngModel)]="form.installment" [placeholder]="form.loanType === 'ADVANCE' ? 'all at once' : ''" />
          </label>
          <label>{{ 'Given on' | translate }}
            <input class="form-control loan-date" type="date" name="date" [(ngModel)]="form.issueDate" />
          </label>
          <label>{{ 'Note' | translate }}
            <input class="form-control loan-note" name="note" [(ngModel)]="form.note" maxlength="300" style="min-width:12rem;" />
          </label>
          <button class="btn btn-primary loan-save" type="submit" [disabled]="busy">{{ 'Give' | translate }}</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">{{ 'The cash given is recorded in Accounting. Every payroll then takes the installment from the pay until nothing is left; the last one is only what remains. An advance starts in the month it is given, a loan in the next month.' | translate }}</div>
      </form>

      <app-list-filter [placeholder]="'Search loan no. or employee…' | translate" exportPath="/hr/payroll/loans/export" [statuses]="statuses" statusAll="All statuses" [dates]="false" [exportExtra]="extra" (cleared)="type = ''; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select loan-f-type" style="width:auto;" name="ft" [(ngModel)]="type" (ngModelChange)="typeChanged()">
          <option value="">{{ 'Advances and loans' | translate }}</option><option value="ADVANCE">{{ 'Advances only' | translate }}</option><option value="LOAN">{{ 'Loans only' | translate }}</option>
        </select>
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Employee' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Given on' | translate }}</th><th>{{ 'Amount' | translate }}</th><th>{{ 'Per month' | translate }}</th><th>{{ 'Repaid' | translate }}</th><th>{{ 'Still owed' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody><tr class="loan-row" *ngFor="let l of list.rows">
          <td><a [routerLink]="['/admin/hr/payroll/loans', l.id]">{{ l.loanNumber }}</a></td>
          <td>{{ l.employeeName }}</td>
          <td>{{ l.loanType === 'ADVANCE' ? ('Advance' | translate) : ('Loan' | translate) }}</td>
          <td>{{ l.issueDate }}</td>
          <td>{{ l.principal | number: '1.0-2' }}</td>
          <td>{{ l.installment | number: '1.0-2' }}</td>
          <td>{{ l.repaid | number: '1.0-2' }}</td>
          <td><strong>{{ l.balance | number: '1.0-2' }}</strong></td>
          <td><span class="badge" [class.badge-warning]="l.status === 'ACTIVE'" [class.badge-success]="l.status === 'PAID_OFF'" [class.badge-secondary]="l.status === 'CANCELLED'">{{ l.status === 'ACTIVE' ? 'Active' : l.status === 'PAID_OFF' ? ('Paid off' | translate) : ('Cancelled' | translate) }}</span></td>
          <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hr/payroll/loans', l.id]">{{ 'Open' | translate }}</a></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || type ? ('Nothing matches these filters.' | translate) : ('No advances or loans yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PayrollLoansComponent implements OnInit {
  employees: Employee[] = [];
  type = '';
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'ACTIVE', label: 'Active' }, { value: 'PAID_OFF', label: 'Paid off' }, { value: 'CANCELLED', label: 'Cancelled' }];
  list = new ListState<StaffLoan>((f, p, sz) => this.api.page<StaffLoan>('payroll/loans', f, p, sz, { type: this.type }), 'Failed to load advances and loans');
  form: LoanPayload = this.blank();
  error: string | null = null;
  saved: string | null = null;
  busy = false;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.list.load();
  }

  private blank(): LoanPayload {
    return { employeeId: null, loanType: 'LOAN', principal: null, installment: null, issueDate: null, firstYear: null, firstMonth: null, note: null };
  }

  typeChanged(): void {
    this.extra = { type: this.type };
    this.list.page = 0;
    this.list.load();
  }

  give(): void {
    this.error = null;
    this.saved = null;
    this.busy = true;
    this.api.createLoan(this.form).subscribe({
      next: (l) => {
        this.busy = false;
        this.saved = `${l.loanNumber} recorded for ${l.employeeName}: ${l.principal} to be taken back at ${l.installment} a month.`;
        this.form = this.blank();
        this.list.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not record it');
      },
    });
  }
}
