import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { downloadBlob } from '../../core/download-file';
import { HrApiService, PayrollRun } from '../../core/api/hr-api.service';

/** Payroll > Payroll runs: one per month. Generate works out everybody's pay; the run is then reviewed, approved and paid. */
@Component({
  selector: 'app-payroll-runs',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Payroll' | translate }}</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-outline-primary pr-adjustments" routerLink="/admin/hr/payroll/adjustments">{{ 'Adjustments' | translate }}</a>
          <a class="btn btn-outline-primary pr-loans" routerLink="/admin/hr/payroll/loans">Advances &amp; loans</a>
          <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/rules">{{ 'Rules and tax bands' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="generate()">
        <strong class="mb-2">{{ 'Run payroll for a month' | translate }}</strong>
        <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
          <select class="form-select" style="width:auto;" name="m" [(ngModel)]="month">
            <option *ngFor="let n of months; let i = index" [ngValue]="i + 1">{{ n }}</option>
          </select>
          <input class="form-control" type="number" style="width:6rem;" name="y" [(ngModel)]="year" />
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Working…' | translate) : ('Generate' | translate) }}</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">{{ 'Works out the pay of everyone with a contract in that month. A draft can be generated again after you change contracts, leave or attendance; once approved it is posted to Accounting and locked.' | translate }}</div>
      </form>

      <div class="card mb-3 summary-card" style="padding:0.75rem 1rem;">
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <strong>{{ 'Yearly summary' | translate }}</strong>
          <input class="form-control summary-year" type="number" style="width:6rem;" name="sy" [(ngModel)]="summaryYear" />
          <button class="btn btn-outline-primary summary-xlsx" type="button" (click)="summary('xlsx')">{{ 'Excel' | translate }}</button>
          <button class="btn btn-outline-primary summary-pdf" type="button" (click)="summary('pdf')">{{ 'PDF' | translate }}</button>
          <button class="btn btn-outline-primary summary-csv" type="button" (click)="summary('csv')">{{ 'CSV' | translate }}</button>
          <span class="text-muted" style="font-size:0.8rem;">{{ 'One row per employee: gross, tax, contributions and net of the approved and paid payrolls of the year.' | translate }}</span>
        </div>
      </div>

      <app-list-filter [showSearch]="false" exportKind="payroll-runs" exportPath="/hr/payroll/runs/export" [statuses]="statuses" statusAll="All statuses" [dates]="false" [exportExtra]="extra" (cleared)="filterYear = null; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <label class="text-muted" style="font-size:0.85rem;">{{ 'Year' | translate }} <input class="form-control year-filter" type="number" style="width:6rem;display:inline-block;" name="fy" [(ngModel)]="filterYear" (ngModelChange)="yearChanged()" [placeholder]="'all' | translate" /></label>
      </div>
      <table class="table-sm">
        <thead><tr><th>{{ 'Month' | translate }}</th><th>{{ 'Employees' | translate }}</th><th>{{ 'Gross' | translate }}</th><th>{{ 'PAYE' | translate }}</th><th>{{ 'Contributions' | translate }}</th><th>{{ 'Net pay' | translate }}</th><th>{{ 'Employer cost' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody><tr *ngFor="let r of list.rows">
          <td><a [routerLink]="['/admin/hr/payroll/runs', r.id]">{{ monthName(r) }}</a></td>
          <td>{{ r.employeeCount }}</td>
          <td>{{ r.totalGross | number: '1.0-2' }}</td><td>{{ r.totalPaye | number: '1.0-2' }}</td>
          <td>{{ r.totalEmployeeContributions | number: '1.0-2' }}</td><td>{{ r.totalNet | number: '1.0-2' }}</td>
          <td>{{ r.totalEmployerContributions | number: '1.0-2' }}</td>
          <td><span class="badge" [class.badge-warning]="r.status === 'DRAFT'" [class.badge-success]="r.status === 'PAID'" [class.badge-secondary]="r.status === 'APPROVED'">{{ r.status }}</span></td>
          <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hr/payroll/runs', r.id]">{{ 'Open' | translate }}</a></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || filterYear ? ('Nothing matches these filters.' | translate) : ('No payroll has been run yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PayrollRunsComponent implements OnInit {
  filterYear: number | null = null;
  summaryYear = new Date().getFullYear();
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'DRAFT', label: 'Draft' }, { value: 'APPROVED', label: 'Approved' }, { value: 'PAID', label: 'Paid' }];
  list = new ListState<PayrollRun>((f, p, sz) => this.api.page<PayrollRun>('payroll/runs', f, p, sz, { year: this.filterYear }), 'Failed to load payroll runs');
  error: string | null = null;
  busy = false;
  month = new Date().getMonth() + 1;
  year = new Date().getFullYear();
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private api: HrApiService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  summary(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.error = null;
    this.api.payrollAnnualSummary(this.summaryYear, format).subscribe({
      next: (blob) => downloadBlob(blob, `payroll-summary-${this.summaryYear}.${format}`),
      error: () => (this.error = 'Could not create the summary'),
    });
  }

  yearChanged(): void {
    this.extra = { year: this.filterYear };
    this.list.page = 0;
    this.list.load();
  }

  monthName(r: PayrollRun): string {
    return `${this.months[r.periodMonth - 1]} ${r.periodYear}`;
  }

  generate(): void {
    this.error = null;
    this.busy = true;
    this.api.generatePayroll(this.year, this.month).subscribe({
      next: (d) => {
        this.busy = false;
        this.router.navigate(['/admin/hr/payroll/runs', d.run.id], { state: { warnings: d.warnings } });
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not generate the payroll';
      },
    });
  }
}
