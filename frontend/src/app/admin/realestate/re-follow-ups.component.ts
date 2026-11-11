import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { FollowUpBoard, INTEREST_LABEL, LeadRow, NoteKind, Option, RealEstateApiService } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
interface Section { key: string; title: string; hint: string; color: string; rows: LeadRow[]; }

/** Real Estate > My follow-ups: the calls that are late, due today and coming up, and the leads that have gone quiet. Log a call or move the next call from the row. */
@Component({
  selector: 'app-re-follow-ups',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ title() }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/realestate/leads">{{ 'All leads' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="who" [(ngModel)]="who" (ngModelChange)="load()">
          <option value="me">{{ 'My leads' | translate }}</option><option value="all">{{ 'Everyone' | translate }}</option>
          <option *ngFor="let a of agents" [value]="'' + a.id">{{ a.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="days" [(ngModel)]="days" (ngModelChange)="load()">
          <option [ngValue]="7">{{ 'Coming up: next 7 days' | translate }}</option><option [ngValue]="14">{{ 'Coming up: next 14 days' | translate }}</option><option [ngValue]="30">{{ 'Coming up: next 30 days' | translate }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="board && !board.mailEnabled">{{ 'The morning e-mail is off. An admin can switch it on in the Real Estate settings.' | translate }}</span>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="board && board.mailEnabled">{{ 'Each agent gets these lists by e-mail every morning.' | translate }}</span>
      </div>

      <div *ngIf="!board && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <div class="card" style="padding:1rem;" *ngIf="board && !count()">
        <strong>{{ 'Nothing is waiting.' | translate }}</strong>
        <div class="text-muted">{{ 'No calls are late or due today, and no lead has gone quiet{p0}.' | translate: { p0: (board.all || board.agentId ? '' : ' on your list') } }}</div>
      </div>

      <div *ngFor="let sec of secs; trackBy: bySection" class="mb-3">
        <h4 class="mb-1" [style.color]="sec.color">{{ sec.title }} <span class="text-muted" style="font-weight:400;">({{ sec.rows.length }})</span></h4>
        <div class="text-muted mb-1" style="font-size:0.85rem;">{{ sec.hint }}</div>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'Lead' | translate }}</th><th>{{ 'Phone' | translate }}</th><th>{{ 'Looking to' | translate }}</th><th>{{ 'Property' | translate }}</th><th>{{ 'Agent' | translate }}</th><th>{{ 'Last contact' | translate }}</th><th>{{ 'Next call' | translate }}</th><th></th></tr></thead>
          <tbody>
            <ng-container *ngFor="let l of sec.rows; trackBy: byId">
              <tr>
                <td data-label="Lead"><a [routerLink]="['/admin/realestate/leads', l.id]"><strong>{{ l.name }}</strong></a>
                  <span class="badge badge-secondary" *ngIf="l.status !== 'NEW' && l.status !== 'CONTACTED'" style="margin-left:0.3rem;">{{ l.status | titlecase }}</span></td>
                <td data-label="Phone"><a *ngIf="l.phone" [href]="'tel:' + l.phone">{{ l.phone }}</a><span *ngIf="!l.phone">{{ l.email || '—' }}</span></td>
                <td data-label="Looking to">{{ interestName(l.interest) }}<span class="text-muted" *ngIf="l.preferredArea"> · {{ l.preferredArea }}</span></td>
                <td data-label="Property"><a *ngIf="l.propertyId" [routerLink]="['/admin/realestate/properties', l.propertyId]">{{ l.propertyCode }}</a><span *ngIf="!l.propertyId">—</span></td>
                <td data-label="Agent">{{ l.assignedName || ('Nobody' | translate) }}</td>
                <td data-label="Last contact">{{ (l.lastContactAt || l.createdAt) | date: 'd MMM' }} <span class="text-muted">({{ daysAgo(l.lastContactAt || l.createdAt) }})</span></td>
                <td data-label="Next call" [style.color]="l.followUpOverdue ? '#b91c1c' : ''">{{ l.nextFollowUp ? (l.nextFollowUp | date: 'EEE d MMM') : 'not set' }}<span *ngIf="l.followUpOverdue"> ({{ lateBy(l) }})</span></td>
                <td data-label="" style="white-space:nowrap; text-align:right;">
                  <ng-container *ngIf="canManage">
                    <button class="btn btn-sm btn-primary" type="button" (click)="start(l, 'call')">{{ 'Log call' | translate }}</button>
                    <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="start(l, 'move')">{{ 'Move' | translate }}</button>
                  </ng-container>
                </td>
              </tr>
              <tr *ngIf="open && open.id === l.id">
                <td colspan="8" data-label="">
                  <div class="alert alert-danger" *ngIf="formError">{{ formError }}</div>
                  <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end; width:100%;">
                    <div class="form-group" *ngIf="open.mode === 'call'" style="min-width:9rem;"><label class="form-label">{{ 'How' | translate }}</label>
                      <select class="form-select fu-kind" name="fk" [(ngModel)]="kind"><option value="CALL">{{ 'Phone call' | translate }}</option><option value="WHATSAPP">{{ 'WhatsApp' | translate }}</option><option value="EMAIL">{{ 'E-mail' | translate }}</option><option value="MEETING">{{ 'Meeting' | translate }}</option></select></div>
                    <div class="form-group" *ngIf="open.mode === 'call'" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What was said' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
                      <input class="form-control fu-text" name="ft" [(ngModel)]="text" maxlength="2000" [placeholder]="'e.g. Wants a second viewing on Saturday' | translate" /></div>
                    <div class="form-group" style="min-width:11rem;"><label class="form-label">{{ 'Next call' | translate }}</label>
                      <input class="form-control fu-date" type="date" name="fd" [(ngModel)]="next" [min]="todayIso()" [disabled]="none" /></div>
                    <div class="form-group" style="display:flex; gap:0.3rem; flex-wrap:wrap;">
                      <button class="btn btn-sm btn-outline-secondary fu-chip" type="button" *ngFor="let c of chips" (click)="pick(c.days)">{{ c.label }}</button>
                    </div>
                    <label style="display:flex; gap:0.4rem; align-items:center;" class="form-group"><input class="fu-none" type="checkbox" name="fn" [(ngModel)]="none" /> {{ 'No further call needed' | translate }}</label>
                    <div class="form-group" style="display:flex; gap:0.3rem;">
                      <button class="btn btn-sm btn-primary fu-save" type="button" (click)="save(l)" [disabled]="busy">{{ open.mode === 'call' ? ('Save call' | translate) : ('Move' | translate) }}</button>
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
export class ReFollowUpsComponent implements OnInit {
  board: FollowUpBoard | null = null;
  secs: Section[] = [];
  agents: Option[] = [];
  who = 'me';
  days = 7;
  canManage: boolean;
  error: string | null = null;
  open: { id: number; mode: 'call' | 'move' } | null = null;
  kind: NoteKind = 'CALL';
  text = '';
  next = '';
  none = false;
  busy = false;
  formError: string | null = null;
  chips = [{ label: T('Tomorrow'), days: 1 }, { label: T('In 3 days'), days: 3 }, { label: T('In a week'), days: 7 }, { label: T('In 2 weeks'), days: 14 }, { label: T('In a month'), days: 30 }];

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('all')) this.who = 'all';
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.load();
  }

  load(): void {
    this.error = null;
    this.open = null;
    const f: { agentId?: number | null; all?: boolean; days?: number } = { days: this.days };
    if (this.who === 'all') f.all = true;
    else if (this.who !== 'me') f.agentId = Number(this.who);
    this.api.followUps(f).subscribe({
      next: (b) => { this.board = b; this.secs = this.sections(); },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the follow-ups')),
    });
  }

  title(): string {
    if (!this.board) return 'My follow-ups';
    if (this.board.all) return 'Follow-ups: everyone';
    return this.who === 'me' ? 'My follow-ups' : 'Follow-ups: ' + (this.board.agentName ?? 'agent');
  }

  sections(): Section[] {
    const b = this.board;
    if (!b) return [];
    const all: Section[] = [
      { key: 'overdue', title: 'Late', hint: T('The call was due on an earlier day. Oldest first.'), color: '#b91c1c', rows: b.overdue },
      { key: 'today', title: 'Due today', hint: T('Call these today.'), color: '#b45309', rows: b.today },
      { key: 'upcoming', title: 'Coming up', hint: `Due in the next ${b.upcomingDays} days.`, color: '', rows: b.upcoming },
      { key: 'quiet', title: 'Gone quiet', hint: `Nobody has been in touch for ${b.staleDays} days or more and no call is booked. Call them, or mark the lead lost.`, color: '#b45309', rows: b.quiet },
    ];
    return all.filter((s) => s.rows.length);
  }

  bySection(_: number, s: Section): string { return s.key; }
  byId(_: number, l: LeadRow): number { return l.id; }

  count(): number { const b = this.board; return b ? b.overdue.length + b.today.length + b.upcoming.length + b.quiet.length : 0; }

  interestName(s: string): string { return (INTEREST_LABEL as Record<string, string>)[s] ?? s; }

  todayIso(): string { return this.iso(new Date()); }
  private iso(d: Date): string { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

  daysAgo(t: string): string {
    const n = Math.max(0, Math.floor((Date.now() - new Date(t).getTime()) / 86400000));
    return n === 0 ? 'today' : n === 1 ? '1 day ago' : n + ' days ago';
  }

  lateBy(l: LeadRow): string {
    if (!l.nextFollowUp) return '';
    const n = Math.round((new Date(this.todayIso() + 'T00:00:00').getTime() - new Date(l.nextFollowUp + 'T00:00:00').getTime()) / 86400000);
    return n + (n === 1 ? ' day late' : ' days late');
  }

  start(l: LeadRow, mode: 'call' | 'move'): void {
    this.open = { id: l.id, mode };
    this.kind = 'CALL';
    this.text = '';
    this.none = false;
    this.formError = null;
    const d = new Date();
    d.setDate(d.getDate() + 3);
    this.next = this.iso(d);
  }

  pick(days: number): void {
    const d = new Date();
    d.setDate(d.getDate() + days);
    this.next = this.iso(d);
    this.none = false;
  }

  save(l: LeadRow): void {
    if (!this.open) return;
    if (!this.none && !this.next) { this.formError = T('Choose the next call date, or tick "No further call needed".'); return; }
    this.busy = true;
    this.formError = null;
    const day = this.none ? null : this.next;
    const done = () => { this.busy = false; this.load(); };
    const fail = (e: { error?: { message?: string } }) => { this.busy = false; this.formError = e?.error?.message ?? T('Could not save'); };
    if (this.open.mode === 'move') {
      this.api.setFollowUp(l.id, day).subscribe({ next: done, error: fail });
      return;
    }
    this.api.addNote(l.id, { kind: this.kind, text: this.text.trim() || 'Followed up', nextFollowUp: day }).subscribe({
      next: () => (this.none ? this.api.setFollowUp(l.id, null).subscribe({ next: done, error: fail }) : done()),
      error: fail,
    });
  }
}
