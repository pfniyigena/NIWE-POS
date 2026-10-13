import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { HrApiService, PayrollRun } from '../../core/api/hr-api.service';

/** Payroll > Payroll runs: one per month. Generate works out everybody's pay; the run is then reviewed, approved and paid. */
@Component({
  selector: 'app-payroll-runs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Payroll</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/rules">Rules and tax bands</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="generate()">
        <strong class="mb-2">Run payroll for a month</strong>
        <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
          <select class="form-select" style="width:auto;" name="m" [(ngModel)]="month">
            <option *ngFor="let n of months; let i = index" [ngValue]="i + 1">{{ n }}</option>
          </select>
          <input class="form-control" type="number" style="width:6rem;" name="y" [(ngModel)]="year" />
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? 'Working…' : 'Generate' }}</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">Works out the pay of everyone with a contract in that month. A draft can be generated again after you change contracts, leave or attendance; once approved it is posted to Accounting and locked.</div>
      </form>

      <table class="table-sm">
        <thead><tr><th>Month</th><th>Employees</th><th>Gross</th><th>PAYE</th><th>Contributions</th><th>Net pay</th><th>Employer cost</th><th>Status</th><th></th></tr></thead>
        <tbody><tr *ngFor="let r of runs">
          <td><a [routerLink]="['/admin/hr/payroll/runs', r.id]">{{ monthName(r) }}</a></td>
          <td>{{ r.employeeCount }}</td>
          <td>{{ r.totalGross | number: '1.0-2' }}</td><td>{{ r.totalPaye | number: '1.0-2' }}</td>
          <td>{{ r.totalEmployeeContributions | number: '1.0-2' }}</td><td>{{ r.totalNet | number: '1.0-2' }}</td>
          <td>{{ r.totalEmployerContributions | number: '1.0-2' }}</td>
          <td><span class="badge" [class.badge-warning]="r.status === 'DRAFT'" [class.badge-success]="r.status === 'PAID'" [class.badge-secondary]="r.status === 'APPROVED'">{{ r.status }}</span></td>
          <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hr/payroll/runs', r.id]">Open</a></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="!runs.length">No payroll has been run yet.</p>
    </app-shell>
  `,
})
export class PayrollRunsComponent implements OnInit {
  runs: PayrollRun[] = [];
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
    this.api.payrollRuns().subscribe({ next: (r) => (this.runs = r), error: (e) => (this.error = e?.error?.message ?? 'Failed to load payroll runs') });
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
