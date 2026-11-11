import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { PmReminderApiService } from '../../core/api/pm-reminder-api.service';
import { InvoiceRun, PropertyMgmtApiService, PropertyRow, RENT_STATUS_LABEL, RentChargeRow, RentStatus, rentBadge } from '../../core/api/propertymgmt-api.service';

import { T } from '../../core/i18n/t';
/** Property Management > Rent: the rent of all leases, by what needs doing (to invoice, overdue, open, paid, waived), with the button that invoices the rent that is ready. */
@Component({
  selector: 'app-rent-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Rent' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="button" (click)="invoiceReady()" [disabled]="busy">{{ 'Invoice the rent that is ready' | translate }}</button>
          <a class="btn btn-outline-secondary" routerLink="/admin/propertymgmt/arrears">{{ 'Arrears' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>
      <div class="alert alert-warning" *ngIf="run && run.problems.length"><div *ngFor="let p of run.problems">{{ p }}</div></div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button *ngFor="let t of tabs" type="button" class="btn btn-sm" [ngClass]="status === t.value ? 'btn-primary' : 'btn-outline-secondary'" (click)="pick(t.value)">{{ t.label }}</button>
        <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any property' | translate }}</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <input class="form-control" style="width:14rem;" [placeholder]="'Filter by tenant or unit' | translate" name="q" [(ngModel)]="q" (ngModelChange)="page = 0" />
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Tenant' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Lease' | translate }}</th><th>{{ 'Period' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of pageRows()">
            <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', c.tenantId]">{{ c.tenantName }}</a></td>
            <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', c.unitId]">{{ c.propertyName }} · {{ c.unitNo }}</a></td>
            <td data-label="Lease"><a [routerLink]="['/admin/propertymgmt/leases', c.leaseId]">{{ c.leaseCode }}</a></td>
            <td style="white-space:nowrap;" data-label="Period">{{ c.periodStart | date: 'd MMM y' }} – {{ c.periodEnd | date: 'd MMM y' }}</td>
            <td style="white-space:nowrap;" data-label="Due">{{ c.dueDate | date: 'd MMM y' }}</td>
            <td class="text-end" data-label="Rent">{{ c.amount | number: '1.0-0' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="c.extras">{{ '+ {p0} charges' | translate: { p0: (c.extras | number: '1.0-0') } }}</div></td>
            <td data-label="Status"><span class="badge" style="white-space:nowrap;" [ngClass]="badge(c.status)">{{ label(c.status) }}</span><div class="text-muted" style="font-size:0.75rem;" *ngIf="c.status === 'OVERDUE'">{{ '{p0} days late' | translate: { p0: (c.daysOverdue) } }}</div>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="c.reminders">{{ 'Reminded {p0}x, last {p1}' | translate: { p0: (c.reminders), p1: (c.lastReminderOn | date: 'd MMM') } }}</div>
<div class="text-muted" style="font-size:0.75rem;" *ngIf="c.lateFee">{{ 'Late fee {p0} ({p1})' | translate: { p0: (c.lateFee | number: '1.0-0'), p1: (c.lateFeeInvoice) } }}</div></td>
            <td data-label="Invoice"><a *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId]">{{ c.invoiceNumber }}</a></td>
            <td class="text-end" data-label="Owed">{{ c.outstanding != null ? (c.outstanding | number: '1.0-0') : '' }}</td>
            <td style="white-space:nowrap; text-align:right;" data-label="">
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="c.status === 'NOT_INVOICED'" (click)="invoiceOne(c)" [disabled]="busy">{{ 'Invoice' | translate }}</button>
              <a class="btn btn-sm btn-outline-secondary" *ngIf="c.invoiceId && c.outstanding" [routerLink]="['/admin/invoices', c.invoiceId, 'record-payment']">{{ 'Record payment' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.invoiceId && c.outstanding" style="margin-left:0.2rem;" (click)="remind(c)" [disabled]="busy">{{ 'Remind' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="c.status === 'OVERDUE' && !c.lateFee" style="margin-left:0.2rem;" (click)="chargeFee(c)" [disabled]="busy">{{ 'Late fee' | translate }}</button>
            </td>
          </tr>
        </tbody>
        <tfoot *ngIf="shown().length"><tr><td colspan="5" class="text-end"><strong>{{ '{p0} line{p1}' | translate: { p0: (shown().length), p1: (shown().length === 1 ? '' : 's') } }}</strong></td><td class="text-end" data-label="Rent"><strong>{{ total() | number: '1.0-0' }}</strong></td><td colspan="2"></td><td class="text-end" data-label="Owed"><strong>{{ owed() | number: '1.0-0' }}</strong></td><td data-label=""></td></tr></tfoot>
      </table>
      <app-pager [page]="page" [size]="size" [total]="shown().length" (pageChange)="page = $event"></app-pager>
      <p class="text-muted" *ngIf="!shown().length && loaded">{{ 'Nothing here.' | translate }}</p>
      <p class="text-muted" style="font-size:0.85rem;" *ngIf="status === ''">{{ 'Showing rent due from six months ago to a month ahead. Pick a tab for the rest.' | translate }}</p>
    </app-shell>
  `,
})
export class RentListComponent implements OnInit {
  list: RentChargeRow[] = [];
  page = 0;
  readonly size = PAGE_SIZE;
  properties: PropertyRow[] = [];
  tabs = [
    { value: 'TO_INVOICE', label: T('To invoice') }, { value: 'OVERDUE', label: T('Overdue') }, { value: 'OPEN', label: T('Invoiced, unpaid') },
    { value: 'PAID', label: T('Paid') }, { value: 'WAIVED', label: T('Waived') }, { value: '', label: T('All') },
  ];
  status = 'TO_INVOICE';
  propertyId: number | null = null;
  q = '';
  loaded = false;
  busy = false;
  error: string | null = null;
  note: string | null = null;
  run: InvoiceRun | null = null;

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private reminders: PmReminderApiService) {}

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    if (qp.has('status')) this.status = qp.get('status') ?? '';
    this.api.properties({ activeOnly: true }).subscribe({ next: (p) => (this.properties = p), error: () => undefined });
    this.load();
  }

  badge(s: string): string { return rentBadge(s); }
  label(s: RentStatus): string { return RENT_STATUS_LABEL[s] ?? s; }

  pick(v: string): void {
    this.status = v;
    this.load();
  }

  load(): void {
    this.page = 0;
    this.api.rent({ status: this.status, propertyId: this.propertyId }).subscribe({
      next: (l) => { this.list = l; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the rent'); },
    });
  }

  /** The rows on the page being looked at; the totals below the table still cover every row that matches. */
  pageRows(): RentChargeRow[] { return this.shown().slice(this.page * this.size, (this.page + 1) * this.size); }

  shown(): RentChargeRow[] {
    const n = this.q.trim().toLowerCase();
    if (!n) return this.list;
    return this.list.filter((c) => (c.tenantName ?? '').toLowerCase().includes(n) || (c.unitNo ?? '').toLowerCase().includes(n) || (c.propertyName ?? '').toLowerCase().includes(n));
  }

  total(): number { return this.shown().reduce((a, c) => a + c.amount, 0); }
  owed(): number { return this.shown().reduce((a, c) => a + (c.outstanding ?? 0), 0); }

  invoiceReady(): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.run = null;
    this.api.invoiceRent({}).subscribe({
      next: (r) => {
        this.busy = false;
        this.run = r;
        this.note = r.invoiced ? `Invoiced ${r.invoiced} rent charge${r.invoiced === 1 ? '' : 's'} (${Math.round(r.amount).toLocaleString()}).` : 'Nothing was ready to invoice.';
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not invoice the rent'); },
    });
  }

  invoiceOne(c: RentChargeRow): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.api.invoiceCharge(c.id).subscribe({ next: () => { this.busy = false; this.load(); }, error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not invoice the rent'); } });
  }

  remind(c: RentChargeRow): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    this.reminders.remind(c.id).subscribe({
      next: (r) => { this.busy = false; this.note = `Reminder sent to ${r.sentTo}.`; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the reminder'); },
    });
  }

  chargeFee(c: RentChargeRow): void {
    if (!confirm(T('Invoice a late fee to this tenant for {p0}?', { p0: c.invoiceNumber }))) return;
    this.busy = true;
    this.error = null;
    this.note = null;
    this.reminders.lateFee(c.id).subscribe({
      next: (f) => { this.busy = false; this.note = `Late fee ${Math.round(f.amount).toLocaleString()} invoiced (${f.invoiceNumber}).`; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not charge the late fee'); },
    });
  }
}
