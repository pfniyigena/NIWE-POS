import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { downloadBlob } from '../../core/download-file';
import { HrApiService, PayrollRunDetail } from '../../core/api/hr-api.service';

/** Payroll > one payroll run: every employee's pay for the month, with approve (posts to Accounting) and pay. */
@Component({
  selector: 'app-payroll-run',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="d">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h3>Payroll {{ monthName() }} <span class="badge" [class.badge-warning]="d.run.status === 'DRAFT'" [class.badge-success]="d.run.status === 'PAID'" [class.badge-secondary]="d.run.status === 'APPROVED'">{{ d.run.status }}</span></h3>
            <div class="text-muted">
              <ng-container *ngIf="d.run.approvedByName">Approved by {{ d.run.approvedByName }}. </ng-container>
              <ng-container *ngIf="d.run.paidDate">Paid on {{ d.run.paidDate }}.</ng-container>
            </div>
          </div>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <a class="btn btn-outline-secondary" routerLink="/admin/hr/payroll/runs">Back</a>
            <button class="btn btn-outline-primary" type="button" *ngIf="d.run.status === 'DRAFT'" (click)="regenerate()">Recalculate</button>
            <button class="btn btn-success" type="button" *ngIf="d.run.status === 'DRAFT'" (click)="approve()">Approve and post</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="d.run.status === 'DRAFT'" (click)="remove()">Delete</button>
            <ng-container *ngIf="d.run.status === 'APPROVED'">
              <app-date-picker name="pd" [(ngModel)]="paidDate"></app-date-picker>
              <button class="btn btn-success" type="button" (click)="pay()">Mark salaries as paid</button>
            </ng-container>
          </div>
        </div>

        <div class="alert alert-warning" *ngFor="let w of d.warnings">{{ w }}</div>
        <div class="alert alert-success" *ngIf="info">{{ info }}</div>

        <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="d.run.status !== 'DRAFT'">
          <div class="flex align-center gap-2" style="flex-wrap:wrap;">
            <strong>Pay the salaries</strong>
            <button class="btn btn-outline-primary" type="button" (click)="bankFile('csv')">Bank transfer file (CSV)</button>
            <button class="btn btn-outline-primary" type="button" (click)="bankFile('xlsx')">Bank transfer file (Excel)</button>
            <button class="btn btn-outline-primary" type="button" (click)="emailAll()" [disabled]="emailing">{{ emailing ? 'Sending…' : 'Email all payslips' }}</button>
            <span class="text-warning" *ngIf="missingBank.length">{{ missingBank.length }} employee(s) have no bank account: {{ missingBank.join(', ') }}.</span>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">The bank file follows the usual payroll sheet: No, SURNAME, NAME, NET SALARY, ACCOUNT NO, BANK NAME (Excel is best; it keeps account numbers as text). Payslips go as an email with a PDF to each employee's email address (set up SMTP under Settings > Email).</div>
        </div>

        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Gross pay</div><strong>{{ d.run.totalGross | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Income tax (PAYE)</div><strong>{{ d.run.totalPaye | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Employee contributions</div><strong>{{ d.run.totalEmployeeContributions | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Other deductions</div><strong>{{ d.run.totalOtherDeductions | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Net pay</div><strong>{{ d.run.totalNet | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Employer contributions</div><strong>{{ d.run.totalEmployerContributions | number: '1.2-2' }}</strong></div>
        </div>

        <table class="table-sm">
          <thead><tr><th>No.</th><th>Employee</th><th>Department</th><th>Days</th><th>Gross</th><th>PAYE</th><th>Contributions</th><th>Other</th><th>Net pay</th><th>Employer</th><th></th></tr></thead>
          <tbody><tr *ngFor="let l of d.lines">
            <td>{{ l.employeeNumber }}</td><td>{{ l.employeeName }}</td><td>{{ l.departmentName || '—' }}</td>
            <td>{{ l.payableDays }}/{{ l.daysInMonth }}</td>
            <td>{{ l.grossPay | number: '1.0-2' }}</td><td>{{ l.payeTax | number: '1.0-2' }}</td><td>{{ l.employeeContributions | number: '1.0-2' }}</td>
            <td>{{ l.otherDeductions | number: '1.0-2' }}</td><td><strong>{{ l.netPay | number: '1.0-2' }}</strong></td><td>{{ l.employerContributions | number: '1.0-2' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/hr/payroll/runs', d.run.id, 'payslip', l.id]">Payslip</a>
              <span class="badge badge-warning" *ngIf="d.run.status !== 'DRAFT' && !l.bankAccount" style="margin-left:0.3rem;">no bank account</span></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!d.lines.length">Nobody is in this payroll.</p>
      </ng-container>
    </app-shell>
  `,
})
export class PayrollRunComponent implements OnInit {
  d: PayrollRunDetail | null = null;
  error: string | null = null;
  info: string | null = null;
  emailing = false;
  paidDate = new Date().toISOString().slice(0, 10);
  private months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private api: HrApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const warnings = (history.state?.warnings as string[] | undefined) ?? [];
    this.api.payrollRun(id).subscribe({
      next: (d) => (this.d = { ...d, warnings: d.warnings.length ? d.warnings : warnings }),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the payroll'),
    });
  }

  get missingBank(): string[] {
    return (this.d?.lines ?? []).filter((l) => l.netPay > 0 && !l.bankAccount).map((l) => l.employeeName);
  }

  bankFile(format: 'csv' | 'xlsx'): void {
    if (!this.d) return;
    const period = `${this.d.run.periodYear}-${String(this.d.run.periodMonth).padStart(2, '0')}`;
    this.api.bankFile(this.d.run.id, format).subscribe({
      next: (blob) => downloadBlob(blob, `bank-transfers-${period}.${format}`),
      error: () => (this.error = 'Could not create the bank file'),
    });
  }

  emailAll(): void {
    if (!this.d || !confirm('Email every employee their payslip?')) return;
    this.emailing = true;
    this.info = null;
    this.error = null;
    this.api.emailPayslips(this.d.run.id).subscribe({
      next: (r) => {
        this.emailing = false;
        this.info = `${r.sent} payslip(s) sent.` + (r.noEmail.length ? ` No email address: ${r.noEmail.join(', ')}.` : '') + (r.failed.length ? ` Failed: ${r.failed.join('; ')}.` : '');
      },
      error: (e) => {
        this.emailing = false;
        this.error = e?.error?.message ?? 'Could not send the payslips';
      },
    });
  }

  monthName(): string {
    return this.d ? `${this.months[this.d.run.periodMonth - 1]} ${this.d.run.periodYear}` : '';
  }

  regenerate(): void {
    if (!this.d) return;
    this.api.generatePayroll(this.d.run.periodYear, this.d.run.periodMonth).subscribe({
      next: (d) => {
        this.d = d;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not recalculate'),
    });
  }

  approve(): void {
    if (!this.d || !confirm('Approve this payroll? It is posted to Accounting and can no longer be changed.')) return;
    this.api.approvePayroll(this.d.run.id).subscribe({
      next: (r) => {
        if (this.d) this.d = { ...this.d, run: r, warnings: [] };
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not approve the payroll'),
    });
  }

  pay(): void {
    if (!this.d || !confirm('Record that the net salaries have been paid? This posts the payment to Accounting.')) return;
    this.api.payPayroll(this.d.run.id, this.paidDate).subscribe({
      next: (r) => {
        if (this.d) this.d = { ...this.d, run: r };
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not record the payment'),
    });
  }

  remove(): void {
    if (!this.d || !confirm('Delete this draft payroll?')) return;
    this.api.deletePayroll(this.d.run.id).subscribe({
      next: () => this.router.navigate(['/admin/hr/payroll/runs']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the payroll'),
    });
  }
}
