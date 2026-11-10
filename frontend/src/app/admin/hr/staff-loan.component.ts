import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { HrApiService, StaffLoanDetail } from '../../core/api/hr-api.service';

/** One advance or loan: what is owed, every repayment so far, and the ways to settle the rest. */
@Component({
  selector: 'app-staff-loan',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3 *ngIf="d">{{ d.loan.loanType === 'ADVANCE' ? ('Advance' | translate) : ('Loan' | translate) }} {{ d.loan.loanNumber }} — {{ d.loan.employeeName }}</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/loans">{{ 'Back' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="d">
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Given' | translate }}</div><strong>{{ d.loan.principal | number: '1.0-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Repaid' | translate }}</div><strong class="loan-repaid">{{ d.loan.repaid | number: '1.0-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Still owed' | translate }}</div><strong class="loan-balance">{{ d.loan.balance | number: '1.0-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Taken each month' | translate }}</div><strong>{{ d.loan.installment | number: '1.0-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Status' | translate }}</div><strong class="loan-status">{{ d.loan.status === 'ACTIVE' ? 'Active' : d.loan.status === 'PAID_OFF' ? ('Paid off' | translate) : ('Cancelled' | translate) }}</strong>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="d.loan.installmentsLeft">{{ 'about {p0} installment(s) to go' | translate: { p0: (d.loan.installmentsLeft) } }}</div></div>
        </div>
        <p class="text-muted">{{ 'Given on {p0}; first taken from the pay of {p1} {p2}.' | translate: { p0: (d.loan.issueDate), p1: (months[d.loan.firstMonth - 1]), p2: (d.loan.firstYear) } }} <span *ngIf="d.loan.note">{{ d.loan.note }}</span></p>

        <h4 class="mb-2">{{ 'Repayments' | translate }}</h4>
        <table class="table-sm mb-3">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Amount' | translate }}</th><th>{{ 'How' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
          <tbody><tr class="rep-row" *ngFor="let r of d.repayments">
            <td>{{ r.repaidOn }}</td><td>{{ r.amount | number: '1.0-2' }}</td>
            <td>{{ r.source === 'PAYROLL' ? 'Taken from payroll' : r.source === 'CASH' ? ('Paid back in cash' | translate) : ('Written off' | translate) }}</td>
            <td>{{ r.note }}</td>
            <td><a *ngIf="r.runId" [routerLink]="['/admin/hr/payroll/runs', r.runId]">{{ 'Open payroll' | translate }}</a></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!d.repayments.length">{{ 'Nothing has been repaid yet.' | translate }}</p>

        <ng-container *ngIf="d.loan.status === 'ACTIVE'">
          <form class="card mb-3 rep-form" style="padding:1rem;" (ngSubmit)="repay()">
            <strong class="mb-2">{{ 'The employee pays some back in cash' | translate }}</strong>
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <label>{{ 'Amount' | translate }} <input class="form-control rep-amount" type="number" min="0" step="0.01" style="width:9rem;" name="ra" [(ngModel)]="amount" /></label>
              <label>{{ 'Date' | translate }} <input class="form-control rep-date" type="date" name="rd" [(ngModel)]="date" /></label>
              <label>{{ 'Note' | translate }} <input class="form-control rep-note" name="rn" [(ngModel)]="note" maxlength="300" /></label>
              <button class="btn btn-primary rep-save" type="submit" [disabled]="busy">{{ 'Record' | translate }}</button>
            </div>
          </form>
          <div class="card mb-3" style="padding:1rem;">
            <strong class="mb-2">{{ 'Other ways to close it' | translate }}</strong>
            <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
              <label>{{ 'Reason for writing off what is left' | translate }} <input class="form-control wo-note" name="wn" [(ngModel)]="woNote" maxlength="300" style="min-width:16rem;" /></label>
              <button class="btn btn-outline-danger wo-save" type="button" [disabled]="busy" (click)="writeOff()">{{ 'Write off {p0}' | translate: { p0: (d.loan.balance | number: '1.0-2') } }}</button>
              <button class="btn btn-outline-secondary loan-cancel" type="button" [disabled]="busy" (click)="cancel()">{{ 'Cancel this loan' | translate }}</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Writing off turns what is left into a salary cost. Cancelling is only possible while nothing has been repaid (it undoes the cash given).' | translate }}</div>
          </div>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class StaffLoanComponent implements OnInit {
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  d: StaffLoanDetail | null = null;
  error: string | null = null;
  busy = false;
  amount: number | null = null;
  date: string | null = null;
  note: string | null = null;
  woNote = '';
  private id = 0;

  constructor(private api: HrApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.api.loan(this.id).subscribe({ next: (d) => (this.d = d), error: (e) => (this.error = e?.error?.message ?? 'Could not load the loan') });
  }

  private done = {
    next: (d: StaffLoanDetail) => {
      this.busy = false;
      this.d = d;
      this.amount = null;
      this.note = null;
      this.woNote = '';
    },
    error: (e: any) => {
      this.busy = false;
      this.error = e?.error?.message ?? 'Could not save';
    },
  };

  repay(): void {
    this.error = null;
    this.busy = true;
    this.api.repayLoan(this.id, this.amount ?? 0, this.date, this.note).subscribe(this.done);
  }

  writeOff(): void {
    this.error = null;
    if (!confirm('Write off what is left of this loan as a cost?')) return;
    this.busy = true;
    this.api.writeOffLoan(this.id, this.woNote).subscribe(this.done);
  }

  cancel(): void {
    this.error = null;
    if (!confirm('Cancel this loan? The cash given is put back.')) return;
    this.busy = true;
    this.api.cancelLoan(this.id).subscribe({
      next: () => {
        this.busy = false;
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not cancel';
      },
    });
  }
}
