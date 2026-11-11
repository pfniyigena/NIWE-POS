import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { ReExportComponent } from './re-export.component';
import { CollectionBoard, CollectionRow, Option, ProjectRow, RealEstateApiService, SaleNoteKind } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
interface Section { key: 'overdue' | 'dueSoon' | 'unscheduled'; title: string; hint: string; color: string; total: number; rows: CollectionRow[]; }

/** Real Estate > Collections: the buyers who owe money. Late payers first, then what falls due soon, then sales with no payment schedule. Log a call or a promise, remind the buyer, or send the statement from the row. */
@Component({
  selector: 'app-re-collections',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ReExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Collections' | translate }}</h3>
        <app-re-export kind="collections" [filters]="{ projectId: projectId, agentId: agentId, days: days }"></app-re-export>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
      <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="pj" [(ngModel)]="projectId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'All projects' | translate }}</option><option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ag" [(ngModel)]="agentId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'All agents' | translate }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="dy" [(ngModel)]="days" (ngModelChange)="load()">
          <option [ngValue]="7">{{ 'Due soon: next 7 days' | translate }}</option><option [ngValue]="14">{{ 'Due soon: next 14 days' | translate }}</option><option [ngValue]="30">{{ 'Due soon: next 30 days' | translate }}</option><option [ngValue]="90">{{ 'Due soon: next 90 days' | translate }}</option>
        </select>
      </div>
      <div class="text-muted mb-3" style="font-size:0.85rem;" *ngIf="board && !board.installmentsModuleOn">
        {{ 'Buyers are not reminded automatically because the Instalments module is off. You can still remind a buyer by hand from this page.' | translate }}
      </div>
      <div class="text-muted mb-3" style="font-size:0.85rem;" *ngIf="board && !board.mailConfigured">{{ 'E-mail is not set up, so the Remind and E-mail buttons are switched off. An admin can set it up in Settings.' | translate }}</div>

      <div *ngIf="!board && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <div class="card" style="padding:1rem;" *ngIf="board && !count()">
        <strong>{{ 'Nobody owes anything right now.' | translate }}</strong>
        <div class="text-muted">{{ 'No buyer is late, no instalment falls due in the next {p0} days, and every sold unit has a payment schedule or is paid.' | translate: { p0: (board.soonDays) } }}</div>
      </div>

      <div *ngIf="board && count()" class="mb-3" style="display:grid; grid-template-columns:repeat(auto-fit, minmax(12rem, 1fr)); gap:0.6rem;">
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Late' | translate }}</div><div style="font-size:1.3rem; font-weight:600; color:#b91c1c;">{{ board.overdueTotal | number: '1.0-0' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} buyer{p1}' | translate: { p0: (board.overdue.length), p1: (board.overdue.length === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Due in {p0} days' | translate: { p0: (board.soonDays) } }}</div><div style="font-size:1.3rem; font-weight:600;">{{ board.dueSoonTotal | number: '1.0-0' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} buyer{p1}' | translate: { p0: (board.dueSoon.length), p1: (board.dueSoon.length === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'No schedule' | translate }}</div><div style="font-size:1.3rem; font-weight:600; color:#b45309;">{{ board.unscheduledTotal | number: '1.0-0' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} buyer{p1}' | translate: { p0: (board.unscheduled.length), p1: (board.unscheduled.length === 1 ? '' : 's') } }}</div></div>
      </div>

      <div *ngFor="let sec of secs; trackBy: bySection" class="mb-3">
        <h4 class="mb-1" [style.color]="sec.color">{{ sec.title }} <span class="text-muted" style="font-weight:400;">({{ sec.rows.length }})</span></h4>
        <div class="text-muted mb-1" style="font-size:0.85rem;">{{ sec.hint }}</div>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'Buyer' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Agent' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th class="text-end" *ngIf="sec.key === 'overdue'">{{ 'Late' | translate }}</th><th *ngIf="sec.key !== 'unscheduled'">{{ 'Next due' | translate }}</th><th>{{ 'Last contact' | translate }}</th><th></th></tr></thead>
          <tbody>
            <ng-container *ngFor="let r of sec.rows; trackBy: byId">
              <tr>
                <td data-label="Buyer"><a [routerLink]="['/admin/realestate/unit-sales', r.saleId]"><strong>{{ r.buyerName }}</strong></a>
                  <div><a *ngIf="r.buyerPhone" [href]="'tel:' + r.buyerPhone" style="font-size:0.85rem;">{{ r.buyerPhone }}</a><span class="text-muted" style="font-size:0.85rem;" *ngIf="!r.buyerPhone">{{ r.buyerEmail || '' }}</span></div></td>
                <td data-label="Unit">{{ r.unitNo }}<div class="text-muted" style="font-size:0.8rem;">{{ r.projectName }}</div></td>
                <td data-label="Agent">{{ r.agentName || '—' }}</td>
                <td class="text-end" data-label="Owed">{{ r.outstanding | number: '1.0-0' }}<div class="text-muted" style="font-size:0.8rem;">{{ 'of {p0}' | translate: { p0: (r.agreedPrice | number: '1.0-0') } }}</div></td>
                <td class="text-end" data-label="Late" *ngIf="sec.key === 'overdue'" style="color:#b91c1c;">{{ r.overdueAmount | number: '1.0-0' }}<div style="font-size:0.8rem;">{{ '{p0} instalment{p1}, oldest {p2} d' | translate: { p0: (r.overdueInstallments), p1: (r.overdueInstallments === 1 ? '' : 's'), p2: (r.oldestDaysOverdue) } }}</div></td>
                <td data-label="Next due" *ngIf="sec.key !== 'unscheduled'">{{ r.nextDueDate ? (r.nextDueDate | date: 'EEE d MMM') : '—' }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="r.nextDueAmount">{{ r.nextDueAmount | number: '1.0-0' }}</div></td>
                <td data-label="Last contact">
                  <span *ngIf="r.lastNoteAt">{{ r.lastNoteAt | date: 'd MMM' }} · {{ kindName(r.lastNoteKind) }}<div class="text-muted" style="font-size:0.8rem;">{{ r.lastNoteText }}</div></span>
                  <span class="text-muted" *ngIf="!r.lastNoteAt">{{ 'Nobody yet' | translate }}</span>
                  <div *ngIf="r.promiseDate" style="font-size:0.8rem;" [style.color]="r.promiseBroken ? '#b91c1c' : '#0f766e'">{{ r.promiseBroken ? 'Promise broken: was ' : 'Promised ' }}{{ r.promiseDate | date: 'd MMM' }}</div>
                </td>
                <td data-label="" style="text-align:right;">
                  <div style="display:flex; gap:0.25rem; flex-wrap:wrap; justify-content:flex-end;" *ngIf="canManage">
                    <button class="btn btn-sm btn-primary log-btn" type="button" (click)="start(r)">{{ 'Log call' | translate }}</button>
                    <button class="btn btn-sm btn-outline-secondary remind-btn" type="button" *ngIf="r.basis === 'PLAN'" (click)="remind(r)" [disabled]="busy || !board!.mailConfigured || !r.buyerEmail" [title]="!r.buyerEmail ? 'No e-mail address for this buyer' : ''">{{ 'Remind' | translate }}</button>
                    <button class="btn btn-sm btn-outline-secondary statement-btn" type="button" (click)="statement(r)" [disabled]="busy || !board!.mailConfigured || !r.buyerEmail" [title]="!r.buyerEmail ? 'No e-mail address for this buyer' : ''">{{ 'E-mail statement' | translate }}</button>
                  </div>
                </td>
              </tr>
              <tr *ngIf="open && open.saleId === r.saleId">
                <td [attr.colspan]="sec.key === 'overdue' ? 8 : 7" data-label="">
                  <div class="alert alert-danger" *ngIf="formError">{{ formError }}</div>
                  <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end; width:100%;">
                    <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'What' | translate }}</label>
                      <select class="form-select cn-kind" name="ck" [(ngModel)]="kind"><option value="CALL">{{ 'Called the buyer' | translate }}</option><option value="PROMISE">{{ 'Buyer promised to pay' | translate }}</option><option value="NOTE">{{ 'Other note' | translate }}</option></select></div>
                    <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What was said' | translate }}</label>
                      <input class="form-control cn-text" name="ct" [(ngModel)]="text" maxlength="2000" [placeholder]="'e.g. Cheque comes on Friday' | translate" /></div>
                    <div class="form-group" *ngIf="kind === 'PROMISE'" style="min-width:11rem;"><label class="form-label">{{ 'Promised date' | translate }}</label>
                      <input class="form-control cn-date" type="date" name="cd" [(ngModel)]="promise" [min]="board!.today" /></div>
                    <div class="form-group" style="display:flex; gap:0.3rem;">
                      <button class="btn btn-sm btn-primary cn-save" type="button" (click)="save(r)" [disabled]="busy">{{ 'Save' | translate }}</button>
                      <button class="btn btn-sm btn-outline-secondary" type="button" (click)="open = null">{{ 'Cancel' | translate }}</button>
                    </div>
                  </div>
                </td>
              </tr>
            </ng-container>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class ReCollectionsComponent implements OnInit {
  board: CollectionBoard | null = null;
  secs: Section[] = [];
  projects: ProjectRow[] = [];
  agents: Option[] = [];
  projectId: number | null = null;
  agentId: number | null = null;
  days = 14;
  canManage: boolean;
  error: string | null = null;
  actionError: string | null = null;
  notice: string | null = null;
  open: { saleId: number } | null = null;
  kind: SaleNoteKind = 'CALL';
  text = '';
  promise = '';
  busy = false;
  formError: string | null = null;

  constructor(private api: RealEstateApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    this.api.projects({}).subscribe({ next: (p) => (this.projects = p), error: () => {} });
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.load();
  }

  load(keepNotice = false): void {
    this.error = null;
    this.actionError = null;
    if (!keepNotice) this.notice = null;
    this.open = null;
    this.api.collections({ projectId: this.projectId, agentId: this.agentId, days: this.days }).subscribe({
      next: (b) => { this.board = b; this.secs = this.sections(); },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the collections')),
    });
  }

  count(): number { return this.board ? this.board.overdue.length + this.board.dueSoon.length + this.board.unscheduled.length : 0; }

  sections(): Section[] {
    const b = this.board;
    if (!b) return [];
    const all: Section[] = [
      { key: 'overdue', title: 'Late', hint: 'An instalment is past its due date. The longest overdue is first.', color: '#b91c1c', total: b.overdueTotal, rows: b.overdue },
      { key: 'dueSoon', title: 'Due soon', hint: `The next instalment falls due within ${b.soonDays} days. Nearest first.`, color: '#b45309', total: b.dueSoonTotal, rows: b.dueSoon },
      { key: 'unscheduled', title: 'No payment schedule', hint: 'Sold, but the buyer has no payment plan and still owes money. Set up a plan on the sale, or follow up.', color: '#b45309', total: b.unscheduledTotal, rows: b.unscheduled },
    ];
    return all.filter((s) => s.rows.length);
  }

  bySection(_: number, s: Section): string { return s.key; }
  byId(_: number, r: CollectionRow): number { return r.saleId; }

  kindName(k: SaleNoteKind | null): string {
    switch (k) {
      case 'CALL': return 'Call';
      case 'PROMISE': return 'Promise';
      case 'REMINDER': return 'Reminder sent';
      case 'STATEMENT': return 'Statement sent';
      default: return 'Note';
    }
  }

  start(r: CollectionRow): void {
    this.open = { saleId: r.saleId };
    this.kind = 'CALL';
    this.text = '';
    this.promise = '';
    this.formError = null;
  }

  save(r: CollectionRow): void {
    if (!this.text.trim()) { this.formError = T('Write what was said.'); return; }
    if (this.kind === 'PROMISE' && !this.promise) { this.formError = T('Choose the date the buyer promised to pay.'); return; }
    this.busy = true;
    this.formError = null;
    this.api.addSaleNote(r.saleId, { kind: this.kind, text: this.text.trim(), promiseDate: this.kind === 'PROMISE' ? this.promise : null }).subscribe({
      next: () => { this.busy = false; this.notice = T('Saved for {p0}.', { p0: r.buyerName }); this.load(true); },
      error: (e) => { this.busy = false; this.formError = e?.error?.message ?? T('Could not save'); },
    });
  }

  remind(r: CollectionRow): void {
    this.act(this.api.remindBuyer(r.saleId), r);
  }

  statement(r: CollectionRow): void {
    this.act(this.api.emailStatement(r.saleId), r);
  }

  private act(call: import('rxjs').Observable<{ sentTo: string; message: string }>, r: CollectionRow): void {
    this.busy = true;
    this.actionError = null;
    this.notice = null;
    call.subscribe({
      next: (m) => { this.busy = false; this.notice = m.message || T('Sent to {p0}', { p0: m.sentTo }); this.load(true); },
      error: (e) => { this.busy = false; this.actionError = r.buyerName + ': ' + (e?.error?.message ?? 'That did not work'); },
    });
  }
}
