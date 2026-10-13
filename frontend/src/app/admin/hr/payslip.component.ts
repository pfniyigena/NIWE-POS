import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { StoreSettingsApiService } from '../../core/api/store-settings-api.service';
import { StoreSettings } from '../../core/models';
import { HrApiService, Payslip } from '../../core/api/hr-api.service';

/** A printable payslip for one employee and month. */
@Component({
  selector: 'app-payslip',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="no-print flex justify-between align-center mb-3">
        <a class="btn btn-outline-secondary" [routerLink]="['/admin/hr/payroll/runs', runId]">Back to payroll</a>
        <div class="flex gap-2">
          <button class="btn btn-outline-primary" type="button" *ngIf="p && p.run.status !== 'DRAFT'" (click)="email()" [disabled]="emailing">{{ emailing ? 'Sending…' : 'Email to employee' }}</button>
          <button class="btn btn-primary" type="button" (click)="print()">Print</button>
        </div>
      </div>
      <div class="alert alert-danger no-print" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success no-print" *ngIf="info">{{ info }}</div>
      <div class="payslip" *ngIf="p">
        <div class="flex justify-between align-center">
          <div>
            <h3 style="margin:0;">{{ settings?.businessName || 'Payslip' }}</h3>
            <div class="text-muted" *ngIf="settings?.address">{{ settings?.address }}</div>
          </div>
          <img *ngIf="settings?.logoDataUrl" [src]="settings?.logoDataUrl" alt="" style="max-height:56px;" />
        </div>
        <hr />
        <h4 style="margin:0 0 0.5rem 0;">Payslip — {{ monthName() }}</h4>
        <table class="meta">
          <tr><td>Employee</td><td><strong>{{ p.line.employeeName }}</strong> ({{ p.line.employeeNumber }})</td></tr>
          <tr *ngIf="p.line.departmentName"><td>Department</td><td>{{ p.line.departmentName }}</td></tr>
          <tr *ngIf="p.line.contractNumber"><td>Contract</td><td>{{ p.line.contractNumber }}</td></tr>
          <tr><td>Days paid</td><td>{{ p.line.payableDays }} of {{ p.line.daysInMonth }}</td></tr>
          <tr *ngIf="p.bankName || p.bankAccount"><td>Paid to</td><td>{{ p.bankName }} {{ p.bankAccount }}</td></tr>
        </table>

        <h5>Earnings</h5>
        <table class="lines">
          <tr *ngFor="let i of earnings"><td>{{ i.name }}</td><td class="num">{{ i.amount | number: '1.2-2' }}</td></tr>
          <tr class="total"><td>Gross pay</td><td class="num">{{ p.line.grossPay | number: '1.2-2' }}</td></tr>
        </table>

        <h5>Deductions</h5>
        <table class="lines">
          <tr *ngFor="let i of deductions"><td>{{ i.name }}</td><td class="num">{{ i.amount | number: '1.2-2' }}</td></tr>
          <tr class="total"><td>Total deductions</td><td class="num">{{ p.line.grossPay - p.line.netPay | number: '1.2-2' }}</td></tr>
        </table>

        <table class="lines net">
          <tr class="total"><td>Net pay</td><td class="num">{{ p.line.netPay | number: '1.2-2' }} {{ settings?.currencySymbol || '' }}</td></tr>
        </table>

        <ng-container *ngIf="employer.length">
          <h5>Employer contributions (not deducted from your pay)</h5>
          <table class="lines">
            <tr *ngFor="let i of employer"><td>{{ i.name }}</td><td class="num">{{ i.amount | number: '1.2-2' }}</td></tr>
          </table>
        </ng-container>
        <p class="text-muted" style="font-size:0.8rem; margin-top:1rem;">Taxable pay this month: {{ p.line.taxablePay | number: '1.2-2' }}.</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .payslip { max-width: 640px; margin: 0 auto; padding: 1rem; background: #fff; color: #111; border: 1px solid #ddd; }
      .payslip table { width: 100%; border-collapse: collapse; margin-bottom: 0.75rem; }
      .payslip td { padding: 0.2rem 0.3rem; }
      .payslip .num { text-align: right; white-space: nowrap; }
      .payslip .total td { border-top: 1px solid #999; font-weight: 600; }
      .payslip .meta td:first-child { width: 8rem; color: #666; }
      .payslip h5 { margin: 0.75rem 0 0.25rem 0; }
      .payslip .net .total td { font-size: 1.1rem; border-top: 2px solid #111; }
      @media print {
        .no-print { display: none !important; }
        .payslip { border: none; }
      }
    `,
  ],
})
export class PayslipComponent implements OnInit {
  p: Payslip | null = null;
  settings: StoreSettings | null = null;
  runId = 0;
  lineId = 0;
  emailing = false;
  info: string | null = null;
  error: string | null = null;
  private months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private api: HrApiService, private route: ActivatedRoute, private storeApi: StoreSettingsApiService) {}

  get earnings() {
    return this.p?.items.filter((i) => i.kind === 'EARNING') ?? [];
  }

  get deductions() {
    return this.p?.items.filter((i) => i.kind === 'TAX' || i.kind === 'DEDUCTION') ?? [];
  }

  get employer() {
    return this.p?.items.filter((i) => i.kind === 'EMPLOYER') ?? [];
  }

  ngOnInit(): void {
    this.runId = Number(this.route.snapshot.paramMap.get('id'));
    const lineId = (this.lineId = Number(this.route.snapshot.paramMap.get('lineId')));
    this.storeApi.get().subscribe({ next: (s) => (this.settings = s), error: () => (this.settings = null) });
    this.api.payslip(this.runId, lineId).subscribe({ next: (p) => (this.p = p), error: (e) => (this.error = e?.error?.message ?? 'Failed to load the payslip') });
  }

  monthName(): string {
    return this.p ? `${this.months[this.p.run.periodMonth - 1]} ${this.p.run.periodYear}` : '';
  }

  email(): void {
    this.emailing = true;
    this.info = null;
    this.error = null;
    this.api.emailPayslips(this.runId, this.lineId).subscribe({
      next: (r) => {
        this.emailing = false;
        if (r.sent) this.info = 'Payslip emailed.';
        else this.error = r.noEmail.length ? 'This employee has no email address.' : r.failed.join('; ') || 'Not sent.';
      },
      error: (e) => {
        this.emailing = false;
        this.error = e?.error?.message ?? 'Could not send the payslip';
      },
    });
  }

  print(): void {
    window.print();
  }
}
