import { Subscription } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { downloadBlob } from '../../core/download-file';
import { HrApiService, Remittance, RemittanceKindTotal, RemittancePayload, RemittanceRow } from '../../core/api/hr-api.service';

/** Payroll > Tax & social security: what each approved payroll owes the tax authority and the social-security fund, what has been paid, and the monthly returns. */
@Component({
  selector: 'app-payroll-remittances',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Tax &amp; social security</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/runs">{{ 'Payroll runs' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error || history.error">{{ error || history.error }}</div>
      <div class="alert alert-success rem-saved" *ngIf="saved">{{ saved }}</div>

      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
        <div class="card rem-total" *ngFor="let t of totals" style="padding:0.75rem 1rem; min-width:14rem;">
          <div class="text-muted">{{ '{p0} — still owed' | translate: { p0: (t.kindLabel) } }}</div>
          <strong class="rem-total-amount" style="font-size:1.2rem;">{{ t.outstanding | number: '1.0-2' }}</strong>
          <div style="font-size:0.8rem; color:#b91c1c;" *ngIf="t.overdue > 0">{{ '{p0} is past its due date' | translate: { p0: (t.overdue | number: '1.0-2') } }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.overdue <= 0 && t.outstanding > 0">{{ 'nothing overdue yet' | translate }}</div>
        </div>
      </div>
      <p class="text-muted">{{ 'Income tax and contributions of a month are due by the {p0}th of the next month. The amounts come from the approved payrolls; payments are recorded in Accounting.' | translate: { p0: (dueDay) } }}</p>

      <form class="card mb-3 rem-form" style="padding:1rem;" *ngIf="form" (ngSubmit)="save()">
        <strong class="mb-2">{{ 'Record a payment — {p0}, {p1} {p2}' | translate: { p0: (kindLabel(form.kind)), p1: (months[form.month - 1]), p2: (form.year) } }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <label>{{ 'Amount' | translate }} <input class="form-control rem-amount" type="number" min="0" step="0.01" style="width:10rem;" name="amount" [(ngModel)]="form.amount" /></label>
          <label>{{ 'Paid on' | translate }} <input class="form-control rem-date" type="date" name="date" [(ngModel)]="form.paidOn" /></label>
          <label>{{ 'Paid to' | translate }} <input class="form-control rem-paidto" name="to" [(ngModel)]="form.paidTo" maxlength="160" /></label>
          <label>{{ 'Receipt / reference' | translate }} <input class="form-control rem-ref" name="ref" [(ngModel)]="form.reference" maxlength="120" /></label>
          <label>{{ 'Note' | translate }} <input class="form-control rem-note" name="note" [(ngModel)]="form.note" maxlength="300" /></label>
          <span class="flex gap-2">
            <button class="btn btn-primary rem-save" type="submit" [disabled]="busy">{{ 'Record payment' | translate }}</button>
            <button class="btn btn-outline-secondary rem-cancel" type="button" (click)="form = null">{{ 'Cancel' | translate }}</button>
          </span>
        </div>
      </form>

      <div class="flex gap-2 mb-2 align-center">
        <label class="text-muted">{{ 'Year' | translate }} <input class="form-control rem-year" type="number" style="width:6rem;display:inline-block;" name="year" [(ngModel)]="year" (ngModelChange)="load()" /></label>
      </div>
      <table class="table-sm mb-3">
        <thead><tr><th>{{ 'Payroll month' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Owed' | translate }}</th><th>{{ 'Paid' | translate }}</th><th>{{ 'Still owed' | translate }}</th><th>{{ 'Due' | translate }}</th><th>{{ 'Return' | translate }}</th><th></th></tr></thead>
        <tbody><tr class="rem-row" *ngFor="let r of rows">
          <td><a [routerLink]="['/admin/hr/payroll/runs', r.runId]">{{ months[r.month - 1] }} {{ r.year }}</a></td>
          <td>{{ r.kindLabel }}</td>
          <td>{{ r.owed | number: '1.0-2' }}</td>
          <td>{{ r.paid | number: '1.0-2' }}</td>
          <td><strong>{{ r.outstanding | number: '1.0-2' }}</strong></td>
          <td>{{ r.dueDate }}
            <span class="badge badge-success" *ngIf="r.outstanding <= 0">{{ 'Settled' | translate }}</span>
            <span class="badge badge-danger" *ngIf="r.overdue">{{ 'Overdue' | translate }}</span></td>
          <td><span class="flex gap-2" *ngIf="r.kind !== 'OTHER'">
            <button class="btn btn-sm btn-outline-secondary rem-ret-xlsx" type="button" (click)="ret(r, 'xlsx')">{{ 'Excel' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary rem-ret-pdf" type="button" (click)="ret(r, 'pdf')">{{ 'PDF' | translate }}</button>
          </span></td>
          <td><button class="btn btn-sm btn-primary rem-pay" type="button" *ngIf="r.outstanding > 0" (click)="startPay(r)">{{ 'Record payment' | translate }}</button></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && rows.length === 0">{{ 'Nothing is owed for {p0} yet — taxes and contributions appear here once a payroll is approved.' | translate: { p0: (year) } }}</p>

      <h4 class="mb-2">{{ 'Payments made' | translate }}</h4>
      <app-list-filter [placeholder]="'Search receipt, paid to…' | translate" exportPath="/hr/payroll/remittances/export" [statuses]="statuses" statusAll="All" [dates]="false" (filterChange)="history.apply($event)"></app-list-filter>
      <table class="table-sm">
        <thead><tr><th>{{ 'Paid on' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Payroll month' | translate }}</th><th>{{ 'Amount' | translate }}</th><th>{{ 'Paid to' | translate }}</th><th>{{ 'Reference' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
        <tbody><tr class="pay-row" *ngFor="let p of history.rows" [style.opacity]="p.status === 'VOIDED' ? 0.55 : 1">
          <td>{{ p.paidOn }}</td><td>{{ p.kindLabel }}</td><td>{{ months[p.month - 1] }} {{ p.year }}</td>
          <td><span [style.text-decoration]="p.status === 'VOIDED' ? 'line-through' : null">{{ p.amount | number: '1.0-2' }}</span></td>
          <td>{{ p.paidTo }}</td><td>{{ p.reference }}</td>
          <td>{{ p.note }}<span *ngIf="p.status === 'VOIDED'"> {{ '— cancelled: {p0}' | translate: { p0: (p.voidNote) } }}</span></td>
          <td><button class="btn btn-sm btn-outline-danger pay-void" type="button" *ngIf="p.status === 'ACTIVE'" (click)="voidIt(p)">{{ 'Cancel' | translate }}</button></td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="history.loaded && history.rows.length === 0">{{ 'No payments recorded yet.' | translate }}</p>
      <app-pager [page]="history.page" [size]="history.size" [total]="history.total" (pageChange)="history.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PayrollRemittancesComponent implements OnInit, OnDestroy {
  private sub?: Subscription;
  months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  dueDay = 15;
  year: number | null = new Date().getFullYear();
  totals: RemittanceKindTotal[] = [];
  rows: RemittanceRow[] = [];
  loaded = false;
  country = '';
  form: RemittancePayload | null = null;
  statuses = [{ value: 'ACTIVE', label: 'Active' }, { value: 'VOIDED', label: 'Cancelled' }];
  history = new ListState<Remittance>((f, p, sz) => this.api.page<Remittance>('payroll/remittances', f, p, sz), 'Failed to load the payments');
  error: string | null = null;
  saved: string | null = null;
  busy = false;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.payrollSettings().subscribe({ next: (s) => (this.country = s.payrollCountry), error: () => {} });
    this.load();
    this.history.load();
  }

  kindLabel(k: string): string {
    return k === 'PAYE' ? 'Income tax (PAYE)' : k === 'SOCIAL_SECURITY' ? 'Social security' : 'Other payroll payables';
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  load(): void {
    // a newer request replaces one still running (the year box fires on every key)
    this.sub?.unsubscribe();
    this.sub = this.api.remittanceSummary(this.year).subscribe({
      next: (s) => {
        this.totals = s.totals;
        this.rows = s.rows;
        this.loaded = true;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load what is owed'),
    });
  }

  private authority(kind: string): string {
    const chad = this.country === 'CHAD';
    return kind === 'PAYE' ? (chad ? 'DGI' : 'RRA') : kind === 'SOCIAL_SECURITY' ? (chad ? 'CNPS' : 'RSSB') : '';
  }

  startPay(r: RemittanceRow): void {
    this.saved = null;
    this.error = null;
    this.form = { kind: r.kind, year: r.year, month: r.month, amount: r.outstanding, paidOn: new Date().toISOString().slice(0, 10), reference: null, paidTo: this.authority(r.kind), note: null };
  }

  save(): void {
    if (!this.form) return;
    this.error = null;
    this.busy = true;
    this.api.payRemittance(this.form).subscribe({
      next: (r) => {
        this.busy = false;
        this.saved = `Recorded ${r.amount} paid for ${r.kindLabel}, ${this.months[r.month - 1]} ${r.year}.`;
        this.form = null;
        this.load();
        this.history.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not record the payment';
      },
    });
  }

  voidIt(p: Remittance): void {
    const note = prompt('Why is this payment cancelled?');
    if (!note) return;
    this.error = null;
    this.api.voidRemittance(p.id, note).subscribe({
      next: () => {
        this.saved = 'The payment was cancelled and the amount is owed again.';
        this.load();
        this.history.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not cancel the payment'),
    });
  }

  ret(r: RemittanceRow, format: 'xlsx' | 'pdf'): void {
    const kind = r.kind === 'PAYE' ? 'paye' : 'social-security';
    this.api.payrollReturn(r.runId, kind, format).subscribe({
      next: (blob) => downloadBlob(blob, `${kind}-return-${r.year}-${String(r.month).padStart(2, '0')}.${format}`),
      error: () => (this.error = 'Could not create the return'),
    });
  }
}
