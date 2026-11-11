import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinalPay, HrApiService } from '../../core/api/hr-api.service';

import { T } from '../../core/i18n/t';
/** Final pay of an employee who left: unused leave paid out, notice paid or recovered, loans taken back. It reaches the payroll of the last month as adjustments. */
@Component({
  selector: 'app-employee-final-pay',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card mb-3 final-pay" style="padding:1rem;" *ngIf="f">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <strong>{{ 'Final pay' | translate }}</strong>
        <span class="badge badge-success fp-applied" *ngIf="f.applied">{{ 'On the {p0} payroll' | translate: { p0: (monthLabel()) } }}</span>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success fp-info" *ngIf="info">{{ info }}</div>
      <div class="alert alert-warning fp-blocked" *ngIf="f.blocked">{{ f.blocked }} <a *ngIf="f.runStatus && f.runStatus !== 'DRAFT'" routerLink="/admin/hr/payroll/runs">{{ 'Open payrolls' | translate }}</a></div>

      <ng-container *ngIf="f.lastDay">
        <div class="text-muted mb-2" style="font-size:0.9rem;">
          {{ 'Left on {p0}. Paid with the {p1} payroll. A day of pay is {p2} ÷ {p3} working days =' | translate: { p0: (f.lastDay), p1: (monthLabel()), p2: (f.baseSalary | number: '1.0-0'), p3: (f.workingDays) } }} <strong>{{ f.dailyRate | number: '1.0-2' }}</strong>.
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Unused leave days paid' | translate }}</label>
            <input class="form-control fp-leave" type="number" min="0" step="0.5" style="width:9rem;" name="fl" [(ngModel)]="leaveDays" [disabled]="!canEdit" />
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="f.leave.length"><span *ngFor="let l of f.leave; let last = last">{{ l.leaveTypeName }}: {{ l.remaining }}{{ last ? '' : ', ' }}</span></div>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="!f.leave.length">{{ 'No leave type is paid out, or nothing is left.' | translate }}</div>
          </div>
          <div class="form-group"><label class="form-label">{{ 'Notice days the company pays' | translate }}</label><input class="form-control fp-notice-pay" type="number" min="0" step="0.5" style="width:9rem;" name="fnp" [(ngModel)]="noticePayDays" [disabled]="!canEdit" /></div>
          <div class="form-group"><label class="form-label">{{ 'Notice days not served' | translate }}</label><input class="form-control fp-notice-recover" type="number" min="0" step="0.5" style="width:9rem;" name="fnr" [(ngModel)]="noticeRecoverDays" [disabled]="!canEdit" /></div>
        </div>
        <div class="mb-2" *ngIf="f.loans.length">
          <label style="display:flex; gap:0.4rem; align-items:center;"><input class="fp-settle" type="checkbox" name="fs" [(ngModel)]="settleLoans" [disabled]="!canEdit" /> {{ 'Take the loans back from this pay ({p0}:' | translate: { p0: (f.loansBalance | number: '1.0-0') } }} <span *ngFor="let l of f.loans; let last = last">{{ l.loanNumber }} {{ l.balance | number: '1.0-0' }}{{ last ? '' : ', ' }}</span>)</label>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'The pay is never cut below zero: whatever it cannot cover stays owed, and you can write it off on the loan.' | translate }}</div>
        </div>
        <div class="form-group" style="max-width:36rem;"><label class="form-label">{{ 'Note (optional)' | translate }}</label><input class="form-control fp-note" name="fn" [(ngModel)]="note" maxlength="300" [disabled]="!canEdit" /></div>

        <table class="table-sm fp-lines mb-2" *ngIf="f.lines.length" style="max-width:34rem;">
          <thead><tr><th>{{ 'Line' | translate }}</th><th>{{ 'Days' | translate }}</th><th style="text-align:right;">{{ 'Amount' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let l of f.lines">
            <td>{{ l.name }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="l.note">{{ l.note }}</div></td>
            <td>{{ l.days }}</td>
            <td style="text-align:right;">{{ l.kind === 'DEDUCTION' ? '−' : '' }}{{ l.amount | number: '1.0-2' }}</td>
          </tr></tbody>
        </table>
        <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="f.lines.length">{{ 'Pay added {p0}' | translate: { p0: (f.earnings | number: '1.0-2') } }}<span *ngIf="f.deductions"> {{ '· taken off {p0}' | translate: { p0: (f.deductions | number: '1.0-2') } }}</span>{{ '. The added pay is taxed and charged contributions like the rest of the month\\'s pay.' | translate }}</div>

        <div class="flex gap-2" *ngIf="canEdit">
          <button class="btn btn-primary fp-save" type="button" (click)="save()" [disabled]="busy">{{ f.applied ? ('Update final pay' | translate) : ('Put on the payroll' | translate) }}</button>
          <button class="btn btn-outline-danger fp-remove" type="button" *ngIf="f.applied" (click)="remove()" [disabled]="busy">{{ 'Remove' | translate }}</button>
        </div>
        <div class="text-muted mt-2 fp-recalc" style="font-size:0.85rem;" *ngIf="f.applied && f.runStatus === 'DRAFT'">{{ 'A draft payroll for {p0} already exists: open it and press Recalculate to include this.' | translate: { p0: (monthLabel()) } }}</div>
      </ng-container>
    </div>
  `,
})
export class EmployeeFinalPayComponent implements OnChanges {
  @Input() employeeId = 0;
  @Input() canManage = false;

  f: FinalPay | null = null;
  leaveDays = 0;
  noticePayDays = 0;
  noticeRecoverDays = 0;
  settleLoans = false;
  note = '';
  busy = false;
  error: string | null = null;
  info: string | null = null;
  private months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  constructor(private api: HrApiService) {}

  get canEdit(): boolean {
    return this.canManage && !!this.f && !this.f.blocked;
  }

  ngOnChanges(): void {
    if (this.employeeId) this.load();
  }

  monthLabel(): string {
    return this.f ? this.months[this.f.month - 1] + ' ' + this.f.year : '';
  }

  private show(f: FinalPay): void {
    this.f = f;
    this.leaveDays = f.leaveDays;
    this.noticePayDays = f.noticePayDays;
    this.noticeRecoverDays = f.noticeRecoverDays;
    this.settleLoans = f.settleLoans;
    this.note = f.note ?? '';
  }

  load(): void {
    this.api.finalPay(this.employeeId).subscribe({
      next: (f) => this.show(f),
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the final pay')),
    });
  }

  save(): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.saveFinalPay(this.employeeId, {
      leaveDays: Number(this.leaveDays) || 0, noticePayDays: Number(this.noticePayDays) || 0, noticeRecoverDays: Number(this.noticeRecoverDays) || 0,
      settleLoans: this.settleLoans, note: this.note.trim() || null,
    }).subscribe({
      next: (f) => { this.busy = false; this.show(f); this.info = T('Saved. It is on the {p0} payroll.', { p0: this.monthLabel() }); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save the final pay'); },
    });
  }

  remove(): void {
    if (!confirm(T('Take the final pay off the payroll?'))) return;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.removeFinalPay(this.employeeId).subscribe({
      next: (f) => { this.busy = false; this.show(f); this.info = T('Removed from the payroll.'); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not remove the final pay'); },
    });
  }
}
