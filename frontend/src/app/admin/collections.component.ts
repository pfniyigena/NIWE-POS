import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { Branch } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf } from '../core/branch-util';
import { AuthService } from '../core/auth.service';
import { downloadBlob } from '../core/download-file';
import { showPdf } from '../core/open-pdf';
import { PAGE_SIZE } from '../core/api/paged';
import {
  CollectionCustomer, CollectionItem, CollectionLogRow, CollectionQuery, CollectionSettings, CollectionSummary, CollectionsApiService, ReminderItem, RemindResult,
} from '../core/api/collections-api.service';

import { T } from '../core/i18n/t';
/**
 * Sales > Collections: who owes the business money and how late it is. "By customer" is the working view (tick the customers, send one reminder
 * e-mail each, print or e-mail a statement); "Unpaid items" lists every invoice and credit sale with its age; "Reminders sent" is the log; admins can
 * switch on the daily automatic reminders.
 */
@Component({
  selector: 'app-collections',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Collections' | translate }}</h3>
        <span class="flex gap-2 align-center col-export" *ngIf="tab !== 'log' && tab !== 'settings'">
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Export' | translate }}</span>
          <button class="btn btn-sm btn-outline-secondary col-x-xlsx" type="button" [disabled]="busy" (click)="exportAs('xlsx')">{{ 'Excel' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary col-x-pdf" type="button" [disabled]="busy" (click)="exportAs('pdf')">{{ 'PDF' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary col-x-csv" type="button" [disabled]="busy" (click)="exportAs('csv')">{{ 'CSV' | translate }}</button>
        </span>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success col-done" *ngIf="done">{{ done }}</div>
      <div class="alert alert-warning col-nomail" *ngIf="canRemind && !mailReady">E-mail is not set up yet, so reminders and statements cannot be sent. Set it up in Settings &gt; Email.</div>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="summary">
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Customers owe' | translate }}</div><strong class="col-total">{{ summary.totalOutstanding | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} customer{p1}' | translate: { p0: (summary.customersOwing), p1: (summary.customersOwing === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Of this, overdue' | translate }}</div><strong style="color:#b91c1c;" class="col-late">{{ summary.totalOverdue | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} item{p1} · {p2} customer{p3}' | translate: { p0: (summary.overdueItems), p1: (summary.overdueItems === 1 ? '' : 's'), p2: (summary.customersOverdue), p3: (summary.customersOverdue === 1 ? '' : 's') } }}</div></div>
      </div>
      <div class="card mb-3" style="padding:0.8rem; overflow-x:auto;" *ngIf="summary">
        <strong class="mb-1" style="display:block;">{{ 'How late' | translate }}</strong>
        <table class="col-aging table-sm">
          <thead><tr><th class="text-end">{{ 'Not yet due' | translate }}</th><th class="text-end">{{ '1 to 30 days' | translate }}</th><th class="text-end">{{ '31 to 60 days' | translate }}</th><th class="text-end">{{ '61 to 90 days' | translate }}</th><th class="text-end">{{ 'Over 90 days' | translate }}</th></tr></thead>
          <tbody><tr>
            <td class="text-end"><a href="javascript:void(0)" class="col-b-CURRENT" (click)="pickBucket('CURRENT')">{{ summary.aging.current | number: '1.0-2' }}</a></td>
            <td class="text-end"><a href="javascript:void(0)" class="col-b-1-30" (click)="pickBucket('1-30')">{{ summary.aging.days1to30 | number: '1.0-2' }}</a></td>
            <td class="text-end"><a href="javascript:void(0)" class="col-b-31-60" (click)="pickBucket('31-60')">{{ summary.aging.days31to60 | number: '1.0-2' }}</a></td>
            <td class="text-end"><a href="javascript:void(0)" class="col-b-61-90" (click)="pickBucket('61-90')">{{ summary.aging.days61to90 | number: '1.0-2' }}</a></td>
            <td class="text-end"><a href="javascript:void(0)" class="col-b-90" (click)="pickBucket('90+')"><strong [style.color]="summary.aging.over90 ? '#b91c1c' : ''">{{ summary.aging.over90 | number: '1.0-2' }}</strong></a></td>
          </tr></tbody>
        </table>
        <div class="text-muted" style="font-size:0.75rem;">{{ 'Click an amount to list the items behind it.' | translate }}</div>
      </div>

      <div class="flex gap-2 mb-3 col-tabs" style="flex-wrap:wrap;">
        <button type="button" class="btn btn-sm col-tab-customers" [ngClass]="tab === 'customers' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('customers')">{{ 'By customer' | translate }}</button>
        <button type="button" class="btn btn-sm col-tab-items" [ngClass]="tab === 'items' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('items')">{{ 'Unpaid items' | translate }}</button>
        <button type="button" class="btn btn-sm col-tab-log" [ngClass]="tab === 'log' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('log')">{{ 'Reminders sent' | translate }}</button>
        <button type="button" class="btn btn-sm col-tab-settings" *ngIf="isAdmin" [ngClass]="tab === 'settings' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('settings')">{{ 'Automatic reminders' | translate }}</button>
      </div>

      <div class="card" style="padding:1rem;" *ngIf="tab === 'customers' || tab === 'items'">
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
          <input class="form-control col-q" type="search" style="max-width:16rem;" [placeholder]="'Search customer, phone or number' | translate" [(ngModel)]="q" name="q" (ngModelChange)="typed()" />
          <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" class="col-overdue" [(ngModel)]="overdueOnly" name="oo" (ngModelChange)="reload()" /> {{ 'Overdue only' | translate }}</label>
          <select class="form-select col-branch" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchId" name="br" (ngModelChange)="reload()" [title]="'Branch' | translate">
            <option [ngValue]="null">{{ 'All branches' | translate }}</option>
            <option *ngFor="let br of branches" [ngValue]="br.id">{{ br.name }}</option>
          </select>
          <span class="text-muted col-bucket" *ngIf="bucket && tab === 'items'" style="font-size:0.85rem;">{{ 'Showing {p0}' | translate: { p0: (bucket === 'CURRENT' ? 'not yet due' : bucket + ' days') } }}
            <a href="javascript:void(0)" class="col-bucket-clear" (click)="pickBucket('')">{{ 'show all' | translate }}</a></span>
          <span style="flex:1;"></span>
          <button class="btn btn-sm btn-primary col-send" type="button" *ngIf="tab === 'customers' && canRemind" [disabled]="busy || !chosenIds().length || !mailReady" (click)="remind()">
            {{ 'Send reminder{p0} to {p1} chosen' | translate: { p0: (chosenIds().length === 1 ? '' : 's'), p1: (chosenIds().length) } }}
          </button>
        </div>

        <ng-container *ngIf="tab === 'customers'">
          <div class="text-muted mb-2" style="font-size:0.8rem;" *ngIf="canRemind">{{ 'Tick the customers to remind. Each gets one e-mail listing everything overdue, with their statement attached.' | translate }}</div>
          <table class="col-customers">
            <thead><tr>
              <th style="width:2rem;" *ngIf="canRemind"><input type="checkbox" class="col-all" [checked]="allChosen()" (change)="toggleAll($any($event.target).checked)" /></th>
              <th>{{ 'Customer' | translate }}</th><th class="text-end">{{ 'Items' | translate }}</th><th class="text-end">{{ 'Owes' | translate }}</th><th class="text-end">{{ 'Overdue' | translate }}</th><th class="text-end">{{ 'Oldest (days late)' | translate }}</th><th>{{ 'Reminders' | translate }}</th><th>{{ 'Statement' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let c of customers" class="col-row">
                <td *ngIf="canRemind"><input type="checkbox" class="col-pick" [disabled]="c.overdueItems <= 0" [checked]="chosen[c.customerId]" (change)="chosen[c.customerId] = $any($event.target).checked" /></td>
                <td><a class="col-cust" href="javascript:void(0)" (click)="openCustomer(c)">{{ c.name }}</a>
                  <div style="font-size:0.8rem;" [style.color]="c.email ? '' : '#b91c1c'" [class.text-muted]="!!c.email">{{ c.email || 'no e-mail address' }}<span class="text-muted" *ngIf="c.phone"> · {{ c.phone }}</span></div></td>
                <td class="text-end">{{ c.items }}</td>
                <td class="text-end"><strong>{{ c.outstanding | number: '1.0-2' }}</strong></td>
                <td class="text-end"><span [style.color]="c.overdue ? '#b91c1c' : ''">{{ c.overdue | number: '1.0-2' }}</span></td>
                <td class="text-end"><span *ngIf="c.oldestDaysOverdue > 0" [style.color]="c.oldestDaysOverdue > 60 ? '#b91c1c' : ''">{{ c.oldestDaysOverdue }}</span><span class="text-muted" *ngIf="c.oldestDaysOverdue <= 0">{{ 'not yet' | translate }}</span></td>
                <td><span *ngIf="c.remindersSent">{{ '{p0} sent' | translate: { p0: (c.remindersSent) } }}<div class="text-muted" style="font-size:0.75rem;">{{ 'last {p0}' | translate: { p0: (c.lastRemindedAt | date: 'd MMM yyyy') } }}</div></span><span class="text-muted" *ngIf="!c.remindersSent">{{ 'none' | translate }}</span></td>
                <td style="white-space:nowrap;">
                  <button class="btn btn-sm btn-outline-secondary col-stmt" type="button" (click)="statement(c)" [disabled]="busy">{{ 'PDF' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary col-stmt-mail" type="button" *ngIf="canRemind && c.email" (click)="mailStatement(c)" [disabled]="busy || !mailReady">{{ 'E-mail' | translate }}</button>
                </td>
              </tr>
              <tr *ngIf="loaded && !customers.length && !error"><td [attr.colspan]="canRemind ? 8 : 7" class="text-muted">{{ q || branchId != null ? 'Nothing matches these filters.' : overdueOnly ? ('Nobody has anything overdue. Untick "Overdue only" to see everything that is unpaid.' | translate) : ('Nothing is unpaid.' | translate) }}</td></tr>
            </tbody>
          </table>
        </ng-container>

        <ng-container *ngIf="tab === 'items'">
          <table class="col-items">
            <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'Date' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Still to pay' | translate }}</th><th class="text-end">{{ 'Late (days)' | translate }}</th><th>{{ 'Age' | translate }}</th><th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let r of items" class="col-item">
                <td><a *ngIf="r.kind === 'INVOICE'" [routerLink]="['/admin/invoices', r.documentId]">{{ r.number }}</a><span *ngIf="r.kind !== 'INVOICE'">{{ r.number }}</span>
                  <div class="text-muted" style="font-size:0.75rem;">{{ r.kind === 'INVOICE' ? ('Invoice' | translate) : ('Sale on credit' | translate) }}</div></td>
                <td>{{ r.customerName }}<div style="font-size:0.8rem;" [style.color]="r.customerEmail ? '' : '#b91c1c'" [class.text-muted]="!!r.customerEmail">{{ r.customerEmail || 'no e-mail address' }}</div></td>
                <td>{{ r.date }}</td>
                <td>{{ r.dueDate }}<span class="text-muted" style="font-size:0.75rem;" *ngIf="r.dueAssumed" [title]="'No due date was set, so it is worked out from the payment term' | translate"> {{ '(from term)' | translate }}</span></td>
                <td class="text-end"><strong>{{ r.outstanding | number: '1.0-2' }}</strong><div class="text-muted" style="font-size:0.75rem;" *ngIf="r.paid">{{ 'of {p0}' | translate: { p0: (r.total | number: '1.0-2') } }}</div></td>
                <td class="text-end"><span *ngIf="r.daysOverdue > 0" [style.color]="r.daysOverdue > 60 ? '#b91c1c' : ''">{{ r.daysOverdue }}</span><span class="text-muted" *ngIf="r.daysOverdue <= 0">{{ 'not yet' | translate }}</span></td>
                <td>{{ r.bucket === 'CURRENT' ? 'Not yet due' : r.bucket + ' days' }}</td>
                <td *ngIf="branches.length > 1">{{ branchName(r.branchId) }}</td>
              </tr>
              <tr *ngIf="loaded && !items.length && !error"><td [attr.colspan]="branches.length > 1 ? 8 : 7" class="text-muted">{{ q || bucket || branchId != null ? 'Nothing matches these filters.' : overdueOnly ? ('Nothing is overdue.' | translate) : ('Nothing is unpaid.' | translate) }}</td></tr>
            </tbody>
          </table>
        </ng-container>
        <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goto($event)"></app-pager>
        <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Counted: issued invoices and till sales made on credit that are not paid in full. An item with no due date is due after the customer\\'s payment term (or {p0} days).' | translate: { p0: (defaultTerm) } }}</div>
      </div>

      <div class="card" style="padding:1rem;" *ngIf="tab === 'log'">
        <table class="col-log">
          <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Sent to' | translate }}</th><th class="text-end">{{ 'Items' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let l of log" class="col-log-row">
              <td>{{ l.sentAt | date: 'd MMM yyyy HH:mm' }}</td><td>{{ l.customerName }}</td>
              <td>{{ l.kind === 'STATEMENT' ? 'Statement' : l.kind === 'AUTO' ? ('Reminder (automatic)' | translate) : ('Reminder' | translate) }}</td>
              <td>{{ l.email }}</td><td class="text-end">{{ l.items }}</td><td class="text-end">{{ l.amount | number: '1.0-2' }}</td><td>{{ l.sentBy }}</td>
            </tr>
            <tr *ngIf="!log.length"><td colspan="7" class="text-muted">{{ 'Nothing has been e-mailed yet.' | translate }}</td></tr>
          </tbody>
        </table>
      </div>

      <div class="card" style="padding:1rem; max-width:44rem;" *ngIf="tab === 'settings' && settings">
        <p class="text-muted">{{ 'When this is on, the system checks every morning and e-mails each customer whose payment is overdue, then repeats now and then until they pay or the limit is reached. It does nothing until you switch it on.' | translate }}</p>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" class="col-auto" [(ngModel)]="settings.autoEnabled" name="auto" /> {{ 'Send payment reminders automatically' | translate }}</label>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'First reminder after (days late)' | translate }}</label><input class="form-control col-first" type="number" min="1" max="365" [(ngModel)]="settings.firstDays" name="first" /></div>
          <div class="form-group"><label class="form-label">{{ 'Then every (days)' | translate }}</label><input class="form-control col-repeat" type="number" min="1" max="365" [(ngModel)]="settings.repeatDays" name="repeat" /></div>
          <div class="form-group"><label class="form-label">{{ 'At most (reminders)' | translate }}</label><input class="form-control col-max" type="number" min="1" max="20" [(ngModel)]="settings.maxReminders" name="max" /></div>
          <div class="form-group"><label class="form-label">{{ 'Default payment term (days)' | translate }}</label><input class="form-control col-term" type="number" min="0" max="365" [(ngModel)]="settings.defaultTermDays" name="term" /></div>
        </div>
        <div class="flex gap-2 mb-3">
          <button class="btn btn-primary col-save" type="button" [disabled]="busy" (click)="saveSettings()">{{ 'Save settings' | translate }}</button>
          <button class="btn btn-outline-secondary col-preview" type="button" [disabled]="busy" (click)="loadPreview()">{{ 'Who would be reminded now?' | translate }}</button>
          <button class="btn btn-outline-secondary col-run" type="button" [disabled]="busy || !settings.mailReady || !preview.length" (click)="runNow()">{{ 'Send them now' | translate }}</button>
        </div>
        <div *ngIf="previewed">
          <strong>{{ preview.length ? ('These customers would be reminded now:' | translate) : ('Nobody would be reminded right now.' | translate) }}</strong>
          <ul class="col-preview-list"><li *ngFor="let p of preview">{{ '{p0} — {p1} item{p2}, {p3}' | translate: { p0: (p.customerName), p1: (p.items), p2: (p.items === 1 ? '' : 's'), p3: (p.outstanding | number: '1.0-2') } }}<span class="text-muted" *ngIf="p.problem"> ({{ p.problem }})</span></li></ul>
        </div>
      </div>
    </app-shell>
  `,
})
export class CollectionsComponent implements OnInit {
  tab: 'customers' | 'items' | 'log' | 'settings' = 'customers';
  customers: CollectionCustomer[] = [];
  items: CollectionItem[] = [];
  log: CollectionLogRow[] = [];
  summary: CollectionSummary | null = null;
  settings: CollectionSettings | null = null;
  preview: ReminderItem[] = [];
  previewed = false;
  chosen: Record<number, boolean> = {};
  branches: Branch[] = [];
  q = '';
  overdueOnly = true;
  branchId: number | null = null;
  bucket = '';
  page = 0;
  size = PAGE_SIZE;
  total = 0;
  canRemind = false;
  mailReady = true;
  defaultTerm = 30;
  loaded = false;
  busy = false;
  error: string | null = null;
  done: string | null = null;
  isAdmin = false;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private api: CollectionsApiService, private branchApi: BranchApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.isAdmin = this.auth.isAdmin();
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.reload();
  }

  branchName(id: number | null | undefined): string { return branchNameOf(id, this.branches); }

  private query(): CollectionQuery {
    return { overdueOnly: this.overdueOnly, q: this.q, branchId: this.branchId, bucket: this.bucket, page: this.page, size: this.size };
  }

  show(tab: 'customers' | 'items' | 'log' | 'settings'): void {
    this.tab = tab;
    this.page = 0;
    this.done = null;
    this.error = null;
    if (tab === 'log') this.api.log().subscribe({ next: (l) => (this.log = l), error: (e) => (this.error = e?.error?.message ?? T('Could not load the log')) });
    else if (tab === 'settings') this.api.settings().subscribe({ next: (s) => (this.settings = s), error: (e) => (this.error = e?.error?.message ?? T('Could not load the settings')) });
    else this.reload();
  }

  typed(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.reload(), 300);
  }

  reload(): void {
    this.page = 0;
    this.fetch();
  }

  goto(p: number): void {
    this.page = p;
    this.fetch();
  }

  pickBucket(b: string): void {
    this.bucket = b;
    if (b) this.overdueOnly = b !== 'CURRENT' ? true : false;
    this.tab = 'items';
    this.reload();
  }

  private fetch(): void {
    if (this.tab === 'customers') {
      this.api.customers(this.query()).subscribe({
        next: (d) => { this.customers = d.rows; this.total = d.total; this.summary = d.summary; this.canRemind = d.canRemind; this.mailReady = d.mailReady; this.chosen = {}; this.loaded = true; this.error = null; },
        error: (e) => (this.error = e?.error?.message ?? T('Could not load the customers who owe')),
      });
    } else if (this.tab === 'items') {
      this.api.items(this.query()).subscribe({
        next: (d) => { this.items = d.rows; this.total = d.total; this.summary = d.summary; this.canRemind = d.canRemind; this.mailReady = d.mailReady; this.defaultTerm = d.defaultTermDays; this.loaded = true; this.error = null; },
        error: (e) => (this.error = e?.error?.message ?? T('Could not load the unpaid items')),
      });
    }
  }

  chosenIds(): number[] { return this.customers.filter((c) => this.chosen[c.customerId]).map((c) => c.customerId); }

  allChosen(): boolean {
    const late = this.customers.filter((c) => c.overdueItems > 0);
    return late.length > 0 && late.every((c) => this.chosen[c.customerId]);
  }

  toggleAll(on: boolean): void {
    for (const c of this.customers) this.chosen[c.customerId] = on && c.overdueItems > 0;
  }

  openCustomer(c: CollectionCustomer): void {
    this.q = c.name;
    this.tab = 'items';
    this.reload();
  }

  remind(): void {
    this.busy = true; this.error = null; this.done = null;
    this.api.remind(this.chosenIds()).subscribe({
      next: (r: RemindResult) => {
        this.busy = false;
        const bad = r.customers.filter((x) => x.problem).map((x) => x.customerName + ': ' + x.problem);
        this.done = 'Sent ' + r.mails + ' e-mail' + (r.mails === 1 ? '' : 's') + ' for ' + r.items + ' item' + (r.items === 1 ? '' : 's') + (r.skipped ? '. Not sent: ' + bad.join('; ') : '') + '.';
        this.fetch();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the reminders'); },
    });
  }

  statement(c: CollectionCustomer): void {
    this.busy = true; this.error = null;
    showPdf(this.api.statementPdf(c.customerId), 'statement.pdf', (err) => { this.busy = false; this.error = err; });
  }

  mailStatement(c: CollectionCustomer): void {
    this.busy = true; this.error = null; this.done = null;
    this.api.emailStatement(c.customerId).subscribe({
      next: (x) => { this.busy = false; this.done = 'The statement was sent to ' + x.email + '.'; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the statement'); },
    });
  }

  exportAs(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.busy = true; this.error = null;
    const kind = this.tab === 'items' ? 'collections' : 'collection-customers';
    this.api.export(kind, this.query(), format).subscribe({
      next: (b) => { this.busy = false; downloadBlob(b, (this.tab === 'items' ? 'overdue-payments.' : 'customers-who-owe.') + format); },
      error: () => { this.busy = false; this.error = T('The export failed.'); },
    });
  }

  saveSettings(): void {
    if (!this.settings) return;
    this.busy = true; this.error = null; this.done = null;
    const s = this.settings;
    this.api.saveSettings({ autoEnabled: s.autoEnabled, firstDays: s.firstDays, repeatDays: s.repeatDays, maxReminders: s.maxReminders, defaultTermDays: s.defaultTermDays }).subscribe({
      next: (r) => { this.busy = false; this.settings = r; this.done = 'Settings saved.'; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save the settings'); },
    });
  }

  loadPreview(): void {
    this.api.preview().subscribe({
      next: (p) => { this.preview = p; this.previewed = true; },
      error: (e) => (this.error = e?.error?.message ?? T('Could not check who would be reminded')),
    });
  }

  runNow(): void {
    this.busy = true; this.error = null; this.done = null;
    this.api.runNow().subscribe({
      next: (r) => { this.busy = false; this.done = 'Sent ' + r.mails + ' e-mail' + (r.mails === 1 ? '' : 's') + (r.skipped ? ', ' + r.skipped + ' not sent' : '') + '.'; this.loadPreview(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the reminders'); },
    });
  }
}
