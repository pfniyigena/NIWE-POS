import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { LawExportComponent } from './law-export.component';
import { CollectionRow, Collections, LawApiService, RemindResult } from '../../core/api/law-api.service';
import { showPdf } from '../../core/open-pdf';

/** Law Firm > Fee collections: invoices clients have not paid yet, how late they are, reminders by e-mail and the client statement. */
@Component({
  selector: 'app-law-collections',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, LawExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Fee collections' | translate }}</h3>
        <app-law-export kind="collections" [filters]="{ overdueOnly: overdueOnly ? 'true' : 'false', q: q }"></app-law-export>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success coll-done" *ngIf="done">{{ done }}</div>
      <div class="alert alert-warning" *ngIf="data && data.canRemind && !data.mailReady">{{ 'E-mail is not set up yet, so reminders cannot be sent. Set it up in Settings.' | translate }}</div>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="data">
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Clients still owe' | translate }}</div><strong class="coll-total">{{ data.totalOutstanding | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} client{p1}' | translate: { p0: (data.clientsOwing), p1: (data.clientsOwing === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Of this, late' | translate }}</div><strong style="color:#b91c1c;" class="coll-late">{{ data.totalOverdue | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} invoice{p1}' | translate: { p0: (data.overdueInvoices), p1: (data.overdueInvoices === 1 ? '' : 's') } }}</div></div>
      </div>
      <div class="card mb-3" style="padding:0.8rem; overflow-x:auto;" *ngIf="data">
        <strong class="mb-1" style="display:block;">{{ 'How late' | translate }}</strong>
        <table class="stack table-sm coll-aging">
          <thead><tr><th class="text-end">{{ 'Not yet due' | translate }}</th><th class="text-end">{{ '1 to 30 days' | translate }}</th><th class="text-end">{{ '31 to 60 days' | translate }}</th><th class="text-end">{{ '61 to 90 days' | translate }}</th><th class="text-end">{{ 'Over 90 days' | translate }}</th></tr></thead>
          <tbody><tr>
            <td class="text-end" data-label="Not yet due">{{ data.aging.current | number: '1.0-2' }}</td>
            <td class="text-end" data-label="1 to 30 days">{{ data.aging.days1to30 | number: '1.0-2' }}</td>
            <td class="text-end" data-label="31 to 60 days">{{ data.aging.days31to60 | number: '1.0-2' }}</td>
            <td class="text-end" data-label="61 to 90 days">{{ data.aging.days61to90 | number: '1.0-2' }}</td>
            <td class="text-end" data-label="Over 90 days"><strong [style.color]="data.aging.over90 ? '#b91c1c' : ''">{{ data.aging.over90 | number: '1.0-2' }}</strong></td>
          </tr></tbody>
        </table>
      </div>

      <div class="card" style="padding:1rem; overflow-x:auto;">
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
          <input class="form-control coll-q" style="max-width:16rem;" [placeholder]="'Search client, matter or invoice' | translate" [(ngModel)]="q" name="q" (keyup.enter)="load()" />
          <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" class="coll-overdue" [(ngModel)]="overdueOnly" name="oo" (ngModelChange)="load()" /> {{ 'Late invoices only' | translate }}</label>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
          <span style="flex:1;"></span>
          <button class="btn btn-sm btn-primary coll-send" type="button" *ngIf="data?.canRemind" [disabled]="busy || !chosenCount() || !data?.mailReady" (click)="remind()">
            {{ 'Send reminder{p0} for {p1} chosen' | translate: { p0: (chosenCount() === 1 ? '' : 's'), p1: (chosenCount()) } }}
          </button>
        </div>
        <div class="text-muted mb-2" style="font-size:0.8rem;" *ngIf="data?.canRemind">{{ 'Tick the late invoices to remind about. Each client gets one e-mail listing the chosen invoices, with their statement of fees attached.' | translate }}</div>
        <table class="stack table-sm">
          <thead><tr>
            <th style="width:2rem;" *ngIf="data?.canRemind"><input type="checkbox" class="coll-all" [checked]="allChosen()" (change)="toggleAll($any($event.target).checked)" /></th>
            <th>{{ 'Invoice' | translate }}</th><th>{{ 'Client' | translate }}</th><th>{{ 'Matter' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Still to pay' | translate }}</th><th class="text-end">{{ 'Late (days)' | translate }}</th><th>{{ 'Reminders' | translate }}</th><th>{{ 'Statement' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let r of rows" class="coll-row">
              <td *ngIf="data?.canRemind" data-label="Remind"><input type="checkbox" class="coll-pick" [disabled]="r.daysOverdue <= 0" [checked]="chosen[r.billId]" (change)="chosen[r.billId] = $any($event.target).checked" /></td>
              <td data-label="Invoice">{{ r.invoiceNumber }}<div class="text-muted" style="font-size:0.8rem;">{{ r.invoiceDate }}</div></td>
              <td data-label="Client">{{ r.clientName }}<div style="font-size:0.8rem;" [style.color]="r.clientEmail ? '' : '#b91c1c'" [class.text-muted]="!!r.clientEmail">{{ r.clientEmail || 'no e-mail address' }}</div></td>
              <td data-label="Matter"><a [routerLink]="['/law/matters', r.matterId]">{{ r.matterNumber }}</a><div class="text-muted" style="font-size:0.8rem;">{{ r.matterTitle }}</div></td>
              <td data-label="Due">{{ r.dueDate }}<span class="text-muted" style="font-size:0.75rem;" *ngIf="r.dueAssumed" [title]="'No due date was set, so it is worked out from the usual payment term' | translate"> {{ '(worked out)' | translate }}</span></td>
              <td class="text-end" data-label="Still to pay"><strong>{{ r.outstanding | number: '1.0-2' }}</strong><div class="text-muted" style="font-size:0.75rem;" *ngIf="r.paid">{{ 'of {p0}' | translate: { p0: (r.amount | number: '1.0-2') } }}</div></td>
              <td class="text-end" data-label="Late (days)"><span *ngIf="r.daysOverdue > 0" [style.color]="r.daysOverdue > 60 ? '#b91c1c' : ''">{{ r.daysOverdue }}</span><span class="text-muted" *ngIf="r.daysOverdue <= 0">{{ 'not yet' | translate }}</span></td>
              <td data-label="Reminders"><span *ngIf="r.reminderCount">{{ '{p0} sent' | translate: { p0: (r.reminderCount) } }}<div class="text-muted" style="font-size:0.75rem;">{{ 'last {p0}' | translate: { p0: (r.lastRemindedAt | date: 'd MMM yyyy') } }}</div></span><span class="text-muted" *ngIf="!r.reminderCount">{{ 'none' | translate }}</span></td>
              <td data-label="Statement" style="white-space:nowrap;">
                <button class="btn btn-sm btn-outline-secondary coll-stmt" type="button" (click)="statement(r)" [disabled]="busy">{{ 'PDF' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary coll-stmt-mail" type="button" *ngIf="data?.canRemind && r.clientEmail" (click)="mailStatement(r)" [disabled]="busy || !data?.mailReady">{{ 'E-mail' | translate }}</button>
              </td>
            </tr>
            <tr *ngIf="data && !rows.length && !error"><td [attr.colspan]="data.canRemind ? 9 : 8" class="text-muted">{{ overdueOnly ? ('No late invoices. Untick "Late invoices only" to see everything that is unpaid.' | translate) : ('Nothing is unpaid.' | translate) }}</td></tr>
          </tbody>
        </table>
        <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Only issued invoices are counted. An invoice paid in full leaves this list. The firm can also send reminders by itself: see Law firm settings.' | translate }}</div>
      </div>
    </app-shell>
  `,
})
export class LawCollectionsComponent implements OnInit {
  data: Collections | null = null;
  rows: CollectionRow[] = [];
  chosen: Record<number, boolean> = {};
  q = '';
  overdueOnly = true;
  busy = false;
  error: string | null = null;
  done: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.collections({ overdueOnly: this.overdueOnly, q: this.q }).subscribe({
      next: (d) => { this.data = d; this.rows = d.rows; this.chosen = {}; this.error = null; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the unpaid fees'),
    });
  }

  chosenCount(): number { return this.rows.filter((r) => this.chosen[r.billId]).length; }

  allChosen(): boolean {
    const late = this.rows.filter((r) => r.daysOverdue > 0);
    return late.length > 0 && late.every((r) => this.chosen[r.billId]);
  }

  toggleAll(on: boolean): void {
    for (const r of this.rows) this.chosen[r.billId] = on && r.daysOverdue > 0;
  }

  remind(): void {
    const ids = this.rows.filter((r) => this.chosen[r.billId]).map((r) => r.billId);
    this.busy = true; this.error = null; this.done = null;
    this.api.remindClients(ids).subscribe({
      next: (r: RemindResult) => {
        this.busy = false;
        const bad = r.items.filter((x) => x.problem).map((x) => x.clientName + ': ' + x.problem);
        this.done = 'Sent ' + r.mails + ' e-mail' + (r.mails === 1 ? '' : 's') + ' for ' + r.invoices + ' invoice' + (r.invoices === 1 ? '' : 's') + (r.skipped ? '. Not sent: ' + bad.join('; ') : '') + '.';
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not send the reminders'; },
    });
  }

  statement(r: CollectionRow): void {
    this.busy = true; this.error = null;
    showPdf(this.api.clientStatementPdf(r.clientId), 'fee-statement.pdf', (err) => { this.busy = false; this.error = err; });
  }

  mailStatement(r: CollectionRow): void {
    this.busy = true; this.error = null; this.done = null;
    this.api.emailClientStatement(r.clientId).subscribe({
      next: (x) => { this.busy = false; this.done = 'The statement was sent to ' + x.sentTo + '.'; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not send the statement'; },
    });
  }
}
