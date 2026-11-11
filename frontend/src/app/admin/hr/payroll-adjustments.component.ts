import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { AdjustmentPayload, Employee, HrApiService, PayrollAdjustment } from '../../core/api/hr-api.service';

import { T } from '../../core/i18n/t';
/** Payroll > Adjustments: a bonus, back pay or an allowance added to one employee's pay for one month, or a deduction taken from it. */
@Component({
  selector: 'app-payroll-adjustments',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Payroll adjustments' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/loans">Advances &amp; loans</a>
          <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/runs">{{ 'Payroll runs' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success adj-saved" *ngIf="saved">{{ saved }}
        <a *ngIf="savedRunId" [routerLink]="['/admin/hr/payroll/runs', savedRunId]"> {{ 'Open that payroll' | translate }}</a>
      </div>

      <form class="card mb-3 adj-form" style="padding:1rem;" (ngSubmit)="save()">
        <strong class="mb-2">{{ editId ? ('Change the adjustment' | translate) : ('New adjustment' | translate) }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <label>{{ 'Employee' | translate }}
            <select class="form-select adj-employee" style="min-width:14rem;" name="emp" [(ngModel)]="form.employeeId" [disabled]="!!editId">
              <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let e of employees" [ngValue]="e.id">{{ e.fullName }}</option>
            </select>
          </label>
          <label>{{ 'What it does' | translate }}
            <select class="form-select adj-kind" name="kind" [(ngModel)]="form.kind">
              <option value="EARNING">{{ 'Adds to the pay (bonus, arrears, allowance)' | translate }}</option>
              <option value="DEDUCTION">{{ 'Takes from the pay (fine, uniform…)' | translate }}</option>
            </select>
          </label>
          <label>{{ 'Name' | translate }}
            <input class="form-control adj-name" name="name" [(ngModel)]="form.name" maxlength="120" [placeholder]="'e.g. Year-end bonus' | translate" />
          </label>
          <label>{{ 'Amount' | translate }}
            <input class="form-control adj-amount" type="number" min="0" step="0.01" style="width:9rem;" name="amount" [(ngModel)]="form.amount" />
          </label>
          <label>{{ 'Payroll month' | translate }}
            <span class="flex gap-2">
              <select class="form-select adj-month" style="width:auto;" name="month" [(ngModel)]="form.month">
                <option *ngFor="let n of months; let i = index" [ngValue]="i + 1">{{ n }}</option>
              </select>
              <input class="form-control adj-year" type="number" style="width:6rem;" name="year" [(ngModel)]="form.year" />
            </span>
          </label>
          <label *ngIf="form.kind === 'EARNING'" style="display:flex; align-items:center; gap:0.4rem;">
            <input class="adj-taxable" type="checkbox" name="taxable" [(ngModel)]="form.taxable" /> {{ 'Taxable' | translate }}
          </label>
          <label>{{ 'Note' | translate }}
            <input class="form-control adj-note" name="note" [(ngModel)]="form.note" maxlength="300" style="min-width:12rem;" />
          </label>
          <span class="flex gap-2">
            <button class="btn btn-primary adj-save" type="submit" [disabled]="busy">{{ editId ? ('Save' | translate) : ('Add' | translate) }}</button>
            <button class="btn btn-outline-secondary adj-cancel" type="button" *ngIf="editId" (click)="reset()">{{ 'Cancel' | translate }}</button>
          </span>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">{{ 'The adjustment is included the next time the payroll of that month is generated. Once that payroll is approved it cannot be changed.' | translate }}</div>
      </form>

      <app-list-filter [placeholder]="'Search name or employee…' | translate" exportPath="/hr/payroll/adjustments/export" [dates]="false" [exportExtra]="extra" (cleared)="kind = ''; fYear = null; fMonth = null; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select adj-f-kind" style="width:auto;" name="fk" [(ngModel)]="kind" (ngModelChange)="extraChanged()">
          <option value="">{{ 'Adds and takes' | translate }}</option><option value="EARNING">{{ 'Adds to the pay' | translate }}</option><option value="DEDUCTION">{{ 'Takes from the pay' | translate }}</option>
        </select>
        <select class="form-select adj-f-month" style="width:auto;" name="fm" [(ngModel)]="fMonth" (ngModelChange)="extraChanged()">
          <option [ngValue]="null">{{ 'Any month' | translate }}</option>
          <option *ngFor="let n of months; let i = index" [ngValue]="i + 1">{{ n }}</option>
        </select>
        <input class="form-control adj-f-year" type="number" style="width:6rem;" name="fy" [(ngModel)]="fYear" (ngModelChange)="extraChanged()" [placeholder]="'year' | translate" />
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'Month' | translate }}</th><th>{{ 'Employee' | translate }}</th><th>{{ 'Name' | translate }}</th><th>{{ 'Amount' | translate }}</th><th>{{ 'Note' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody><tr class="adj-row" *ngFor="let a of list.rows">
          <td>{{ months[a.month - 1] }} {{ a.year }}</td>
          <td>{{ a.employeeName }}</td>
          <td>{{ a.name }} <span class="text-muted" *ngIf="a.kind === 'EARNING' && !a.taxable" style="font-size:0.8rem;">{{ '(not taxable)' | translate }}</span></td>
          <td [style.color]="a.kind === 'DEDUCTION' ? '#b91c1c' : null">{{ a.kind === 'DEDUCTION' ? '−' : '+' }}{{ a.amount | number: '1.0-2' }}</td>
          <td>{{ a.note }}</td>
          <td><span class="badge" [class.badge-warning]="a.status === 'PENDING'" [class.badge-success]="a.status === 'APPLIED'">{{ a.status === 'PENDING' ? ('Waiting for payroll' | translate) : ('In approved payroll' | translate) }}</span>
            <a *ngIf="a.draftRunId" [routerLink]="['/admin/hr/payroll/runs', a.draftRunId]" style="font-size:0.8rem;"> {{ 'draft exists — generate again' | translate }}</a></td>
          <td><span class="flex gap-2" *ngIf="a.status === 'PENDING'">
            <button class="btn btn-sm btn-outline-secondary adj-edit" type="button" (click)="edit(a)">{{ 'Edit' | translate }}</button>
            <button class="btn btn-sm btn-outline-danger adj-del" type="button" (click)="remove(a)">{{ 'Delete' | translate }}</button>
          </span></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || kind || fMonth || fYear ? ('Nothing matches these filters.' | translate) : ('No adjustments yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PayrollAdjustmentsComponent implements OnInit {
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  employees: Employee[] = [];
  kind = '';
  fYear: number | null = null;
  fMonth: number | null = null;
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<PayrollAdjustment>((f, p, sz) => this.api.page<PayrollAdjustment>('payroll/adjustments', f, p, sz, { kind: this.kind, year: this.fYear, month: this.fMonth }), 'Failed to load adjustments');
  form: AdjustmentPayload = this.blank();
  editId: number | null = null;
  error: string | null = null;
  saved: string | null = null;
  savedRunId: number | null = null;
  busy = false;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.employees({ status: 'ACTIVE' }).subscribe({ next: (e) => (this.employees = e), error: () => {} });
    this.list.load();
  }

  private blank(): AdjustmentPayload {
    const now = new Date();
    return { employeeId: null, kind: 'EARNING', name: '', amount: null, taxable: true, year: now.getFullYear(), month: now.getMonth() + 1, note: null };
  }

  extraChanged(): void {
    this.extra = { kind: this.kind, year: this.fYear, month: this.fMonth };
    this.list.page = 0;
    this.list.load();
  }

  edit(a: PayrollAdjustment): void {
    this.editId = a.id;
    this.form = { employeeId: a.employeeId, kind: a.kind, name: a.name, amount: a.amount, taxable: a.taxable, year: a.year, month: a.month, note: a.note };
    this.saved = null;
    this.error = null;
  }

  reset(): void {
    this.editId = null;
    this.form = this.blank();
  }

  save(): void {
    this.error = null;
    this.saved = null;
    this.savedRunId = null;
    this.busy = true;
    const call = this.editId ? this.api.updateAdjustment(this.editId, this.form) : this.api.createAdjustment(this.form);
    call.subscribe({
      next: (r) => {
        this.busy = false;
        this.saved = r.draftRunId ? 'Saved. A draft payroll already exists for that month — generate it again to include this.' : 'Saved. It will be included when that month\'s payroll is generated.';
        this.savedRunId = r.draftRunId;
        this.reset();
        this.list.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not save the adjustment');
      },
    });
  }

  remove(a: PayrollAdjustment): void {
    if (!confirm(T('Remove "{p0}" for {p1}?', { p0: a.name, p1: a.employeeName }))) return;
    this.error = null;
    this.api.deleteAdjustment(a.id).subscribe({
      next: () => this.list.load(),
      error: (e) => (this.error = e?.error?.message ?? T('Could not remove the adjustment')),
    });
  }
}
