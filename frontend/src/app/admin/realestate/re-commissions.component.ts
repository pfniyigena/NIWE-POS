import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { API_BASE } from '../../core/constants';
import { downloadBlob } from '../../core/download-file';
import { ReExportComponent } from './re-export.component';
import { PayeeStatement, PayoutBoard, PayoutFilter, PayoutState, RealEstateApiService, ShareLine } from '../../core/api/realestate-api.service';

const iso = (d: Date): string => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

/** Real Estate > Commissions: what each agent (or referrer) has earned on completed sales, what has gone to Expenses, and what has been paid out. Print or e-mail a statement for each. */
@Component({
  selector: 'app-re-commissions',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ReExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Commissions' | translate }}</h3>
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <button class="btn btn-primary post-due" type="button" *ngIf="canManage && board" (click)="postDue()" [disabled]="busy || !board.sharesToPost || !board.expensesModuleOn || !board.accountSet"
                  [title]="postHint()">{{ 'Post earned commission to Expenses{p0}' | translate: { p0: (board.sharesToPost ? ' (' + board.sharesToPost + ')' : '') } }}</button>
          <app-re-export kind="commissions" [filters]="{ from: from, to: to, payee: payee, openOnly: openOnly }"></app-re-export>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
      <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>
      <div class="alert alert-warning" *ngIf="problems.length"><div *ngFor="let p of problems">{{ p }}</div></div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select cm-period" style="width:auto;" name="pr" [(ngModel)]="period" (ngModelChange)="setPeriod()">
          <option value="all">{{ 'All sales' | translate }}</option><option value="month">{{ 'Sold this month' | translate }}</option><option value="last">{{ 'Sold last month' | translate }}</option><option value="year">{{ 'Sold this year' | translate }}</option><option value="custom">{{ 'Choose dates…' | translate }}</option>
        </select>
        <ng-container *ngIf="period === 'custom'">
          <input class="form-control cm-from" style="width:auto;" type="date" name="fr" [(ngModel)]="from" (ngModelChange)="load()" />
          <input class="form-control cm-to" style="width:auto;" type="date" name="tt" [(ngModel)]="to" (ngModelChange)="load()" />
        </ng-container>
        <select class="form-select cm-payee" style="width:auto;" name="py" [(ngModel)]="payee" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Everyone' | translate }}</option><option *ngFor="let o of options" [ngValue]="o.key">{{ o.name }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input class="cm-open" type="checkbox" name="op" [(ngModel)]="openOnly" (ngModelChange)="load()" /> {{ 'Only people still owed' | translate }}</label>
      </div>
      <div class="text-muted mb-3" style="font-size:0.85rem;" *ngIf="board && canManage && (!board.expensesModuleOn || !board.accountSet)">
        {{ !board.expensesModuleOn ? ('The Expenses module is off, so commission cannot be posted to the accounts yet.' | translate) : ('Choose the expense account for commission in the' | translate) }}
        <a *ngIf="board.expensesModuleOn" routerLink="/admin/realestate/settings">{{ 'Real Estate settings' | translate }}</a><span *ngIf="board.expensesModuleOn"> {{ 'before posting.' | translate }}</span>
      </div>

      <div *ngIf="!board && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <div class="card" style="padding:1rem;" *ngIf="board && !board.payees.length">
        <strong>{{ 'No commission to show.' | translate }}</strong>
        <div class="text-muted">{{ 'Share a deal\\'s commission between the people who earned it (on the deal\\'s page). Shares appear here, and become payable when the sale is complete.' | translate }}</div>
      </div>

      <div *ngIf="board && board.payees.length" class="mb-3" style="display:grid; grid-template-columns:repeat(auto-fit, minmax(12rem, 1fr)); gap:0.6rem;">
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Earned' | translate }}</div><div style="font-size:1.3rem; font-weight:600;">{{ board.earned | number: '1.0-0' }}</div></div>
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Paid out' | translate }}</div><div style="font-size:1.3rem; font-weight:600; color:#0f766e;">{{ board.paid | number: '1.0-0' }}</div></div>
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Still to pay' | translate }}</div><div style="font-size:1.3rem; font-weight:600; color:#b45309;">{{ board.owed | number: '1.0-0' }}</div></div>
        <div class="card" style="padding:0.7rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Not earned yet' | translate }}</div><div style="font-size:1.3rem; font-weight:600;">{{ board.planned | number: '1.0-0' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ 'sale not complete' | translate }}</div></div>
      </div>

      <div *ngFor="let p of board?.payees; trackBy: byKey" class="card mb-3 payee" style="padding:1rem;">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h4>{{ p.payeeName }} <span class="badge badge-secondary" style="font-size:0.75rem;">{{ p.payeeKind === 'STAFF' ? ('Staff' | translate) : ('External' | translate) }}</span></h4>
            <div class="text-muted" style="font-size:0.9rem;">
              {{ 'Earned {p0} · paid {p1} ·' | translate: { p0: (p.earned | number: '1.0-0'), p1: (p.paid | number: '1.0-0') } }} <strong [style.color]="p.owed ? '#b45309' : ''">{{ 'still to pay {p0}' | translate: { p0: (p.owed | number: '1.0-0') } }}</strong>
              <span *ngIf="p.toPost"> {{ '· {p0} to post' | translate: { p0: (p.toPost | number: '1.0-0') } }}</span><span *ngIf="p.awaitingApproval"> {{ '· {p0} awaiting approval' | translate: { p0: (p.awaitingApproval | number: '1.0-0') } }}</span>
              <span *ngIf="p.toPay"> {{ '· {p0} approved, to pay' | translate: { p0: (p.toPay | number: '1.0-0') } }}</span><span *ngIf="p.planned"> {{ '· {p0} not earned yet' | translate: { p0: (p.planned | number: '1.0-0') } }}</span>
              <span *ngIf="p.rejected" style="color:#b91c1c;"> {{ '· {p0} rejected' | translate: { p0: (p.rejected | number: '1.0-0') } }}</span>
            </div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-outline-secondary cm-statement" type="button" (click)="statement(p)" [disabled]="busy">{{ 'Statement' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary cm-email" type="button" *ngIf="canManage" (click)="email(p)" [disabled]="busy || !board!.mailConfigured || p.payeeKind !== 'STAFF' || !p.hasEmail"
                    [title]="emailHint(p)">{{ 'E-mail statement' | translate }}</button>
          </div>
        </div>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'Deal' | translate }}</th><th>{{ 'Property' | translate }}</th><th>{{ 'Completed' | translate }}</th><th class="text-end">{{ 'Sale price' | translate }}</th><th>{{ 'Role' | translate }}</th><th class="text-end">{{ 'Commission' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let l of p.lines; trackBy: byLine">
            <td data-label="Deal"><a [routerLink]="['/admin/realestate/deals', l.dealId]">{{ l.dealCode }}</a></td>
            <td data-label="Property">{{ l.propertyCode }} {{ l.propertyTitle }}</td>
            <td data-label="Completed">{{ l.completedDate ? (l.completedDate | date: 'd MMM y') : '—' }}</td>
            <td class="text-end" data-label="Sale price">{{ l.salePrice | number: '1.0-0' }}</td>
            <td data-label="Role">{{ l.role }}</td>
            <td class="text-end" data-label="Commission">{{ l.amount | number: '1.0-0' }}</td>
            <td data-label="Status"><span class="badge st-{{ l.state }}" [ngClass]="badge(l.state)">{{ stateName(l.state) }}</span><span class="text-muted" style="font-size:0.8rem;" *ngIf="l.paidOn"> {{ l.paidOn | date: 'd MMM y' }}</span></td>
          </tr></tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class ReCommissionsComponent implements OnInit {
  board: PayoutBoard | null = null;
  options: { key: string; name: string }[] = [];
  period = 'all';
  from: string | null = null;
  to: string | null = null;
  payee: string | null = null;
  openOnly = false;
  canManage: boolean;
  busy = false;
  error: string | null = null;
  actionError: string | null = null;
  notice: string | null = null;
  problems: string[] = [];

  constructor(private api: RealEstateApiService, private http: HttpClient, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void { this.load(); }

  private filter(): PayoutFilter { return { from: this.from, to: this.to, payee: this.payee, openOnly: this.openOnly }; }

  setPeriod(): void {
    const now = new Date();
    if (this.period === 'all') { this.from = null; this.to = null; }
    else if (this.period === 'month') { this.from = iso(new Date(now.getFullYear(), now.getMonth(), 1)); this.to = iso(now); }
    else if (this.period === 'last') { this.from = iso(new Date(now.getFullYear(), now.getMonth() - 1, 1)); this.to = iso(new Date(now.getFullYear(), now.getMonth(), 0)); }
    else if (this.period === 'year') { this.from = iso(new Date(now.getFullYear(), 0, 1)); this.to = iso(now); }
    else return;
    this.load();
  }

  load(keepMessages = false): void {
    this.error = null;
    if (!keepMessages) { this.actionError = null; this.notice = null; this.problems = []; }
    this.api.payouts(this.filter()).subscribe({
      next: (b) => { this.board = b; if (!this.payee) this.options = b.payeeOptions; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the commissions'),
    });
  }

  byKey(_: number, p: PayeeStatement): string { return p.key; }
  byLine(_: number, l: ShareLine): number { return l.commissionId; }

  stateName(s: PayoutState): string {
    switch (s) {
      case 'PLANNED': return 'Not earned yet';
      case 'TO_POST': return 'Earned, to post';
      case 'AWAITING_APPROVAL': return 'Awaiting approval';
      case 'TO_PAY': return 'Approved, to pay';
      case 'PAID': return 'Paid';
      default: return 'Rejected';
    }
  }

  badge(s: PayoutState): string {
    switch (s) {
      case 'PAID': return 'badge-success';
      case 'REJECTED': return 'badge-danger';
      case 'PLANNED': return 'badge-secondary';
      default: return 'badge-warning';
    }
  }

  postHint(): string {
    const b = this.board;
    if (!b) return '';
    if (!b.expensesModuleOn) return 'Switch on the Expenses module first';
    if (!b.accountSet) return 'Choose the expense account for commission in the Real Estate settings';
    if (!b.sharesToPost) return 'Nothing earned is waiting to be posted';
    return 'Sends every earned share on this page to Expenses for approval';
  }

  emailHint(p: PayeeStatement): string {
    if (!this.board?.mailConfigured) return 'E-mail is not set up';
    if (p.payeeKind !== 'STAFF') return 'External people have no e-mail address on file. Print the statement instead.';
    if (!p.hasEmail) return 'No e-mail address on the user record';
    return '';
  }

  postDue(): void {
    this.busy = true;
    this.actionError = null;
    this.notice = null;
    this.problems = [];
    this.api.postDueCommissions(this.filter()).subscribe({
      next: (r) => { this.busy = false; this.notice = r.message; this.problems = r.problems; this.load(true); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'Could not post'; },
    });
  }

  statement(p: PayeeStatement): void {
    this.busy = true;
    this.actionError = null;
    let params = new HttpParams().set('payee', p.key);
    if (this.from) params = params.set('from', this.from);
    if (this.to) params = params.set('to', this.to);
    this.http.get(`${API_BASE}/realestate/commissions/statement`, { params, responseType: 'blob' }).subscribe({
      next: (b) => {
        this.busy = false;
        const url = URL.createObjectURL(b);
        const w = window.open(url, '_blank');
        if (!w) downloadBlob(b, 'commission-statement.pdf');
      },
      error: () => { this.busy = false; this.actionError = 'Could not prepare the statement'; },
    });
  }

  email(p: PayeeStatement): void {
    this.busy = true;
    this.actionError = null;
    this.notice = null;
    this.api.emailCommissionStatement({ from: this.from, to: this.to, payee: p.key }).subscribe({
      next: (m) => { this.busy = false; this.notice = m.message; },
      error: (e) => { this.busy = false; this.actionError = p.payeeName + ': ' + (e?.error?.message ?? 'That did not work'); },
    });
  }
}
