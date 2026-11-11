import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PmReminderApiService } from '../../core/api/pm-reminder-api.service';
import { PropertyMgmtApiService, RENT_STATUS_LABEL, RentChargeRow, RentSchedule, RentStatus, rentBadge } from '../../core/api/propertymgmt-api.service';

import { T } from '../../core/i18n/t';
/** The rent of one lease, period by period: what is invoiced, paid or owed, with the steps to invoice, waive or restore a period. */
@Component({
  selector: 'app-rent-schedule',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
      <h4>{{ 'Rent' | translate }}</h4>
      <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="s?.canManage && readyCount()" (click)="invoiceReady()" [disabled]="busy">{{ 'Invoice the rent that is ready ({p0})' | translate: { p0: (readyCount()) } }}</button>
    </div>
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="note">{{ note }}</div>
    <ng-container *ngIf="s">
      <div class="alert alert-warning" *ngIf="s.afterLeaving">{{ '{p0} rent invoice{p1} made for a period after the tenant left. Void {p2} in Invoices if it should not be charged.' | translate: { p0: (s.afterLeaving), p1: (s.afterLeaving === 1 ? ' was' : 's were'), p2: (s.afterLeaving === 1 ? 'it' : 'them') } }}</div>
      <div class="flex gap-3 mb-2" style="flex-wrap:wrap;" *ngIf="s.charges.length">
        <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Invoiced' | translate }}</div><strong>{{ s.billed | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Paid' | translate }}</div><strong>{{ s.paid | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Owed' | translate }}</div><strong>{{ s.outstanding | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Overdue' | translate }}</div><strong [style.color]="s.overdue ? '#b91c1c' : ''">{{ s.overdue | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Not yet invoiced' | translate }}</div><strong>{{ s.notYetInvoiced | number: '1.0-0' }}</strong></div>
      </div>
      <table class="stack table-sm" *ngIf="s.charges.length">
        <thead><tr><th>{{ 'Period' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let c of s.charges">
            <tr>
              <td style="white-space:nowrap;" data-label="Period">{{ c.periodStart | date: 'd MMM y' }} – {{ c.periodEnd | date: 'd MMM y' }}<span class="text-muted" style="font-size:0.75rem;" *ngIf="c.prorated"> {{ '· part period' | translate }}</span></td>
              <td style="white-space:nowrap;" data-label="Due">{{ c.dueDate | date: 'd MMM y' }}</td>
              <td class="text-end" data-label="Rent">{{ c.amount | number: '1.0-0' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="c.extras">{{ '+ {p0} charges' | translate: { p0: (c.extras | number: '1.0-0') } }}</div></td>
              <td data-label="Status"><span class="badge" style="white-space:nowrap;" [ngClass]="badge(c.status)">{{ label(c.status) }}</span>
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="c.status === 'OVERDUE'">{{ '{p0} day{p1} late' | translate: { p0: (c.daysOverdue), p1: (c.daysOverdue === 1 ? '' : 's') } }}</div>
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="c.status === 'WAIVED'">{{ c.waivedReason }}</div>
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="c.reminders">{{ 'Reminded {p0}x, last {p1}' | translate: { p0: (c.reminders), p1: (c.lastReminderOn | date: 'd MMM') } }}</div>
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="c.lateFee">{{ 'Late fee {p0} ({p1})' | translate: { p0: (c.lateFee | number: '1.0-0'), p1: (c.lateFeeInvoice) } }}</div></td>
              <td data-label="Invoice"><a *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId]">{{ c.invoiceNumber }}</a></td>
              <td class="text-end" data-label="Paid">{{ c.paid != null ? (c.paid | number: '1.0-0') : '' }}</td>
              <td class="text-end" data-label="Owed">{{ c.outstanding != null ? (c.outstanding | number: '1.0-0') : '' }}</td>
              <td style="white-space:nowrap; text-align:right;" data-label="">
                <ng-container *ngIf="s.canManage">
                  <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="c.status === 'NOT_INVOICED'" (click)="invoiceOne(c)" [disabled]="busy">{{ 'Invoice' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.status === 'NOT_INVOICED'" style="margin-left:0.2rem;" (click)="startWaive(c)">{{ 'Waive' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.status === 'WAIVED'" (click)="restore(c)" [disabled]="busy">{{ 'Restore' | translate }}</button>
                </ng-container>
                <a class="btn btn-sm btn-outline-secondary" *ngIf="c.invoiceId && c.outstanding" style="margin-left:0.2rem;" [routerLink]="['/admin/invoices', c.invoiceId, 'record-payment']">{{ 'Record payment' | translate }}</a>
                <ng-container *ngIf="s.canManage">
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.invoiceId && c.outstanding" style="margin-left:0.2rem;" (click)="remind(c)" [disabled]="busy">{{ 'Remind' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.status === 'OVERDUE' && !c.lateFee" style="margin-left:0.2rem;" (click)="chargeFee(c)" [disabled]="busy">{{ 'Late fee' | translate }}</button>
                </ng-container>
              </td>
            </tr>
            <tr *ngIf="waiving?.id === c.id">
              <td colspan="8">
                <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                  <input class="form-control" style="max-width:26rem;" name="wr" [(ngModel)]="reason" [placeholder]="'Why is this rent waived?' | translate" maxlength="500" />
                  <button class="btn btn-sm btn-primary" type="button" (click)="waive()" [disabled]="busy">{{ 'Waive' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="waiving = null">{{ 'Cancel' | translate }}</button>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <div class="text-muted" *ngIf="!s.charges.length">{{ 'Rent is laid out when the lease is signed.' | translate }}</div>
    </ng-container>
  `,
})
export class RentScheduleComponent implements OnChanges {
  @Input({ required: true }) leaseId!: number;
  @Input() refresh = 0;
  @Output() changed = new EventEmitter<void>();

  s: RentSchedule | null = null;
  error: string | null = null;
  note: string | null = null;
  busy = false;
  waiving: RentChargeRow | null = null;
  reason = '';

  constructor(private api: PropertyMgmtApiService, private reminders: PmReminderApiService) {}

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.api.leaseRent(this.leaseId).subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? T('Could not load the rent')) });
  }

  badge(s: string): string { return rentBadge(s); }
  label(s: RentStatus): string { return RENT_STATUS_LABEL[s] ?? s; }
  readyCount(): number { return this.s ? this.s.charges.filter((c) => c.readyToInvoice).length : 0; }

  private done(msg: string | null): void {
    this.busy = false;
    this.note = msg;
    this.waiving = null;
    this.load();
    this.changed.emit();
  }

  private fail(e: any, what: string): void {
    this.busy = false;
    this.error = e?.error?.message ?? what;
  }

  invoiceReady(): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.api.invoiceRent({ leaseId: this.leaseId }).subscribe({
      next: (r) => {
        if (r.failed) this.error = r.problems.join(' ');
        this.done(r.invoiced ? `Invoiced ${r.invoiced} period${r.invoiced === 1 ? '' : 's'}.` : null);
      },
      error: (e) => this.fail(e, T('Could not invoice the rent')),
    });
  }

  invoiceOne(c: RentChargeRow): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.api.invoiceCharge(c.id).subscribe({ next: () => this.done('Invoiced.'), error: (e) => this.fail(e, T('Could not invoice the rent')) });
  }

  startWaive(c: RentChargeRow): void {
    this.waiving = this.waiving?.id === c.id ? null : c;
    this.reason = '';
    this.error = null;
  }

  waive(): void {
    if (!this.waiving) return;
    if (!this.reason.trim()) { this.error = T('Say why the rent is waived.'); return; }
    this.busy = true;
    this.error = null;
    this.note = null;
    this.api.waiveCharge(this.waiving.id, this.reason.trim()).subscribe({ next: () => this.done('Waived.'), error: (e) => this.fail(e, T('Could not waive the rent')) });
  }

  restore(c: RentChargeRow): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.api.restoreCharge(c.id).subscribe({ next: () => this.done('Restored.'), error: (e) => this.fail(e, T('Could not restore the rent')) });
  }

  remind(c: RentChargeRow): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.reminders.remind(c.id).subscribe({ next: (r) => this.done(`Reminder sent to ${r.sentTo}.`), error: (e) => this.fail(e, T('Could not send the reminder')) });
  }

  chargeFee(c: RentChargeRow): void {
    if (!confirm(T('Invoice a late fee to this tenant for {p0}?', { p0: c.invoiceNumber }))) return;
    this.busy = true;
    this.error = null;
    this.note = null;
    this.reminders.lateFee(c.id).subscribe({ next: (f) => this.done(`Late fee ${Math.round(f.amount).toLocaleString()} invoiced (${f.invoiceNumber}).`), error: (e) => this.fail(e, T('Could not charge the late fee')) });
  }
}
