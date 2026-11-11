import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { OwnerRow, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';
import { EmailAllResult, GenerateAllResult, PmStatementApiService, STATEMENT_STATUS_LABEL, StatementRow, statementBadge } from '../../core/api/pm-statement-api.service';

import { T } from '../../core/i18n/t';
const pad = (n: number): string => String(n).padStart(2, '0');
const ymd = (d: Date): string => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

/** Property Management > Owner statements: what is owed to each owner for a period. Make them one owner at a time or for every owner at once. */
@Component({
  selector: 'app-statement-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Owner statements' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" type="button" *ngIf="canManage" (click)="mailing = !mailing; making = false">{{ mailing ? ('Close' | translate) : ('E-mail agreed statements' | translate) }}</button>
          <button class="btn btn-primary" type="button" *ngIf="canManage" (click)="making = !making; mailing = false">{{ making ? ('Close' | translate) : ('+ Make statements' | translate) }}</button>
          <a class="btn btn-outline-secondary" routerLink="/admin/propertymgmt/reports">{{ 'Reports' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>
      <div class="alert alert-warning" *ngIf="run && run.notes.length"><div *ngFor="let n of run.notes">{{ n }}</div></div>

      <div class="alert alert-warning" *ngIf="sentRun && sentRun.notes.length"><div *ngFor="let n of sentRun.notes">{{ n }}</div></div>

      <div class="card mb-3" style="padding:1rem; max-width:50rem;" *ngIf="mailing">
        <h4 class="mb-2">{{ 'E-mail agreed statements to the owners' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.9rem;">{{ 'Sends the PDF of every agreed statement that has not been e-mailed yet to its owner\\'s e-mail address. Owners with no address are skipped and listed afterwards. Drafts are never sent.' | translate }}</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="mp" [(ngModel)]="mailPaid" /> {{ 'Also the ones that are already paid' | translate }}</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="mr" [(ngModel)]="mailAgain" /> {{ 'Send again those that were already e-mailed' | translate }}</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="ms" [(ngModel)]="mailShown" /> {{ 'Only the {p0} statement{p1} shown in the list below' | translate: { p0: (list.length), p1: (list.length === 1 ? '' : 's') } }}</label>
        <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="emailAll()" [disabled]="busy">{{ busy ? ('Sending…' | translate) : ('Send now' | translate) }}</button><button class="btn btn-outline-secondary" type="button" (click)="mailing = false">{{ 'Cancel' | translate }}</button></div>
      </div>

      <div class="card mb-3" style="padding:1rem; max-width:50rem;" *ngIf="making">
        <h4 class="mb-2">{{ 'Make statements' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.9rem;">{{ 'A statement adds up the rent paid in the period on the owner\\'s properties, takes off the management fee and the repairs the owner pays for, and shows what you owe the owner. It starts as a draft.' | translate }}</div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'From' | translate }}</label><input class="form-control" type="date" name="pf" [(ngModel)]="from" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'To' | translate }}</label><input class="form-control" type="date" name="pt" [(ngModel)]="to" [max]="todayStr" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Owner' | translate }}</label>
            <select class="form-select" name="mo" [(ngModel)]="makeOwner"><option [ngValue]="null">{{ 'Every owner' | translate }}</option><option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}</option></select></div>
        </div>
        <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="make()" [disabled]="busy">{{ makeOwner ? ('Make the statement' | translate) : ('Make for every owner' | translate) }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="lastMonth()">{{ 'Last month' | translate }}</button><button class="btn btn-sm btn-outline-secondary" type="button" (click)="thisMonth()">{{ 'This month so far' | translate }}</button></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button *ngFor="let t of tabs" type="button" class="btn btn-sm" [ngClass]="tab === t.value ? 'btn-primary' : 'btn-outline-secondary'" (click)="pick(t.value)">{{ t.label }}</button>
        <select class="form-select" style="width:auto;" name="fo" [(ngModel)]="ownerId" (ngModelChange)="load()"><option [ngValue]="null">{{ 'Any owner' | translate }}</option><option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}</option></select>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Statement' | translate }}</th><th>{{ 'Owner' | translate }}</th><th>{{ 'Period' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Rent collected' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th><th class="text-end">{{ 'Repairs' | translate }}</th><th class="text-end">{{ 'Adjust.' | translate }}</th><th class="text-end">{{ 'Net to owner' | translate }}</th><th>{{ 'Paid' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let s of pageRows()">
            <td data-label="Statement"><a [routerLink]="['/admin/propertymgmt/statements', s.id]"><strong>{{ s.code }}</strong></a></td>
            <td data-label="Owner"><a [routerLink]="['/admin/propertymgmt/owners', s.ownerId]">{{ s.ownerName }}</a></td>
            <td style="white-space:nowrap;" data-label="Period">{{ s.periodFrom | date: 'd MMM y' }} – {{ s.periodTo | date: 'd MMM y' }}</td>
            <td data-label="Status"><span class="badge" [ngClass]="badge(s.status)">{{ label(s.status) }}</span></td>
            <td class="text-end" data-label="Rent collected">{{ s.rentCollected | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Fee">{{ s.managementFee | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Repairs">{{ s.repairs | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Adjust.">{{ s.adjustments | number: '1.0-0' }}</td>
            <td class="text-end" [style.color]="s.netPayable < 0 ? '#b91c1c' : ''" data-label="Net to owner"><strong>{{ s.netPayable | number: '1.0-0' }}</strong></td>
            <td style="white-space:nowrap;" data-label="Paid">{{ s.paidDate ? (s.paidDate | date: 'd MMM y') : '' }}</td>
          </tr>
        </tbody>
        <tfoot *ngIf="list.length"><tr><td colspan="4" class="text-end"><strong>{{ '{p0} statement{p1}' | translate: { p0: (list.length), p1: (list.length === 1 ? '' : 's') } }}</strong></td>
          <td class="text-end" data-label="Rent collected"><strong>{{ sum('rentCollected') | number: '1.0-0' }}</strong></td><td class="text-end" data-label="Fee"><strong>{{ sum('managementFee') | number: '1.0-0' }}</strong></td>
          <td class="text-end" data-label="Repairs"><strong>{{ sum('repairs') | number: '1.0-0' }}</strong></td><td class="text-end" data-label="Adjust."><strong>{{ sum('adjustments') | number: '1.0-0' }}</strong></td>
          <td class="text-end" data-label="Net to owner"><strong>{{ sum('netPayable') | number: '1.0-0' }}</strong></td><td data-label="Paid"></td></tr></tfoot>
      </table>
      <app-pager [page]="page" [size]="size" [total]="list.length" (pageChange)="page = $event"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'Nothing here.' | translate }}</p>
    </app-shell>
  `,
})
export class StatementListComponent implements OnInit {
  list: StatementRow[] = [];
  page = 0;
  readonly size = PAGE_SIZE;
  owners: OwnerRow[] = [];
  tabs = [{ value: 'FINAL', label: T('To pay') }, { value: 'DRAFT', label: T('Drafts') }, { value: 'PAID', label: T('Paid') }, { value: '', label: T('All') }];
  tab = 'FINAL';
  ownerId: number | null = null;
  makeOwner: number | null = null;
  making = false;
  mailing = false;
  mailPaid = false;
  mailAgain = false;
  mailShown = false;
  sentRun: EmailAllResult | null = null;
  from = '';
  to = '';
  todayStr = ymd(new Date());
  canManage = true;
  loaded = false;
  busy = false;
  error: string | null = null;
  note: string | null = null;
  run: GenerateAllResult | null = null;

  constructor(private api: PmStatementApiService, private pm: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    if (q.has('ownerId')) { this.ownerId = Number(q.get('ownerId')); this.makeOwner = this.ownerId; this.tab = ''; this.making = q.get('make') === '1'; }
    if (q.has('tab')) this.tab = q.get('tab') ?? '';
    this.lastMonth();
    this.pm.owners({}).subscribe({ next: (o) => (this.owners = o), error: () => undefined });
    this.load();
  }

  badge(s: string): string { return statementBadge(s); }
  label(s: string): string { return (STATEMENT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  pageRows(): StatementRow[] { return this.list.slice(this.page * this.size, (this.page + 1) * this.size); }
  sum(k: 'rentCollected' | 'managementFee' | 'repairs' | 'adjustments' | 'netPayable'): number { return this.list.reduce((a, s) => a + s[k], 0); }

  lastMonth(): void {
    const n = new Date();
    this.from = ymd(new Date(n.getFullYear(), n.getMonth() - 1, 1));
    this.to = ymd(new Date(n.getFullYear(), n.getMonth(), 0));
  }

  thisMonth(): void {
    const n = new Date();
    this.from = ymd(new Date(n.getFullYear(), n.getMonth(), 1));
    this.to = ymd(n);
  }

  pick(v: string): void {
    this.tab = v;
    this.load();
  }

  load(): void {
    this.page = 0;
    this.api.list({ ownerId: this.ownerId, status: this.tab }).subscribe({
      next: (l) => { this.list = l; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the statements'); },
    });
  }

  emailAll(): void {
    this.error = null;
    this.note = null;
    this.sentRun = null;
    this.busy = true;
    this.api.emailAll({ includePaid: this.mailPaid, resend: this.mailAgain, ids: this.mailShown ? this.list.map((s) => s.id) : null }).subscribe({
      next: (r) => {
        this.busy = false;
        this.sentRun = r;
        this.mailing = false;
        this.note = r.sent ? `E-mailed ${r.sent} statement${r.sent === 1 ? '' : 's'}.` + (r.failed ? ` ${r.failed} could not be sent.` : '') : r.failed ? `Nothing was sent: ${r.failed} could not be sent.` : 'Nothing to send: every agreed statement has been e-mailed already, or has no address to send to.';
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the statements'); },
    });
  }

  make(): void {
    this.error = null;
    this.note = null;
    this.run = null;
    if (!this.from || !this.to) { this.error = T('Give the first and last day of the period.'); return; }
    this.busy = true;
    if (this.makeOwner) {
      this.api.generate(this.makeOwner, this.from, this.to).subscribe({
        next: (d) => { this.busy = false; this.router.navigate(['/admin/propertymgmt/statements', d.statement.id]); },
        error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not make the statement'); },
      });
    } else {
      this.api.generateAll(this.from, this.to).subscribe({
        next: (r) => {
          this.busy = false;
          this.run = r;
          this.note = r.created ? `Made ${r.created} statement${r.created === 1 ? '' : 's'} as drafts.` : 'No statement was made: nothing was collected or spent in that period, or the owners already have one.';
          this.tab = 'DRAFT';
          this.load();
        },
        error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not make the statements'); },
      });
    }
  }
}
