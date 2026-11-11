import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { LawExportComponent } from './law-export.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { AuthService } from '../../core/auth.service';
import { LawApiService, TRUST_TYPE_LABEL, TrustOverview } from '../../core/api/law-api.service';

import { T } from '../../core/i18n/t';
/** Law Firm > Client money: what the firm holds for each client, and the payouts and fee transfers waiting for someone to approve them. */
@Component({
  selector: 'app-law-trust-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, PagerComponent, LawExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Client money (trust account)' | translate }}</h3>
        <a *ngIf="o?.enabled && o?.canReconcile && canReconcile" class="btn btn-outline-primary" routerLink="/admin/law/trust/reconcile">{{ 'Reconcile the account' | translate }}</a>
      </div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;" *ngIf="o?.enabled">
        <app-law-export [label]="'Balances' | translate" kind="trust-balances"></app-law-export>
        <app-law-export [label]="'Ledger' | translate" kind="trust-ledger"></app-law-export>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!o && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

      <div class="card" style="padding:1rem;" *ngIf="o && !o.enabled">
        <strong>{{ 'The client money (trust) account is switched off.' | translate }}</strong>
        <div class="text-muted">An admin can switch it on in Law Firm &gt; Settings.</div>
      </div>

      <ng-container *ngIf="o?.enabled">
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.6rem 0.9rem; min-width:11rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Held for clients' | translate }}</div><strong style="font-size:1.2rem;">{{ o!.totalHeld | number: '1.0-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} client{p1}' | translate: { p0: (o!.clients), p1: (o!.clients === 1 ? '' : 's') } }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:11rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Waiting to be paid out' | translate }}</div><strong>{{ o!.totalPending | number: '1.0-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} request{p1}' | translate: { p0: (o!.approvals.length), p1: (o!.approvals.length === 1 ? '' : 's') } }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:11rem;" *ngIf="o!.bankName || o!.accountNumber"><div class="text-muted" style="font-size:0.8rem;">{{ 'Trust account' | translate }}</div><strong>{{ o!.bankName }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ o!.accountNumber }}</div></div>
        </div>

        <div class="card mb-3" style="padding:1rem; overflow-x:auto; border-left:4px solid #e67e22;" *ngIf="o!.approvals.length">
          <h4 class="mb-2">{{ 'Waiting for approval' | translate }}</h4>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'No.' | translate }}</th><th>{{ 'Matter' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'For' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Asked by' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let x of o!.approvals">
                <td data-label="Date">{{ x.txDate | date: 'd MMM y' }}</td><td data-label="No.">{{ x.number }}</td>
                <td data-label="Matter"><a [routerLink]="['/law/matters', x.matterId]" [queryParams]="{ tab: 'trust' }">{{ x.matterNumber }}</a><div class="text-muted" style="font-size:0.8rem;">{{ x.clientName }}</div></td>
                <td data-label="What">{{ label(x.type) }}</td>
                <td data-label="For">{{ x.partyName }}<span *ngIf="x.invoiceNumber"> {{ '· invoice {p0}' | translate: { p0: (x.invoiceNumber) } }}</span><div class="text-muted" style="font-size:0.8rem;">{{ x.purpose }}</div></td>
                <td class="text-end" data-label="Amount"><strong>{{ x.amount | number: '1.0-2' }}</strong></td>
                <td data-label="Asked by">{{ x.requestedByName }}</td>
                <td><a class="btn btn-sm" [class.btn-primary]="x.canApprove" [class.btn-outline-secondary]="!x.canApprove" [routerLink]="['/law/matters', x.matterId]" [queryParams]="{ tab: 'trust' }">{{ x.canApprove ? ('Review' | translate) : ('Open' | translate) }}</a></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card" style="padding:1rem; overflow-x:auto;">
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Matter' | translate }}</th><th>{{ 'Client' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Held' | translate }}</th><th class="text-end">{{ 'Waiting' | translate }}</th><th class="text-end">{{ 'Available' | translate }}</th><th>{{ 'Last activity' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let r of shown">
                <td data-label="Matter"><a [routerLink]="['/law/matters', r.matterId]" [queryParams]="{ tab: 'trust' }">{{ r.matterNumber }}</a><div class="text-muted" style="font-size:0.8rem;">{{ r.matterTitle }}</div></td>
                <td data-label="Client">{{ r.clientName }}</td><td data-label="Status">{{ r.matterStatus }}</td>
                <td class="text-end" data-label="Held"><strong>{{ r.balance | number: '1.0-2' }}</strong></td><td class="text-end" data-label="Waiting">{{ r.pending ? (r.pending | number: '1.0-2') : '' }}</td><td class="text-end" data-label="Available">{{ r.available | number: '1.0-2' }}</td>
                <td data-label="Last activity">{{ r.lastActivity | date: 'd MMM y' }}</td>
              </tr>
              <tr *ngIf="!o!.rows.length"><td colspan="7" class="text-muted">{{ 'No client money has been recorded yet. Open a matter and use its Client money tab to receive money.' | translate }}</td></tr>
            </tbody>
          </table>
          <app-pager [page]="page" [size]="size" [total]="o!.rows.length" (pageChange)="goPage($event)"></app-pager>
          <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Only matters you may see are listed. The ledger per client and the trust journal are in the Law Firm reports.' | translate }}</div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class LawTrustOverviewComponent implements OnInit {
  o: TrustOverview | null = null;
  error: string | null = null;
  canReconcile = false;
  shown: TrustOverview['rows'] = [];
  page = 0;
  readonly size = PAGE_SIZE;

  constructor(private api: LawApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.canReconcile = this.auth.canAccessPath('/admin/law/trust/reconcile');
    this.api.trustOverview().subscribe({ next: (o) => { this.o = o; this.page = 0; this.slice(); }, error: (e) => (this.error = e?.error?.message ?? T('Could not load the client money')) });
  }

  goPage(p: number): void { this.page = p; this.slice(); }
  private slice(): void { this.shown = this.o ? this.o.rows.slice(this.page * this.size, (this.page + 1) * this.size) : []; }

  label(t: string): string { return (TRUST_TYPE_LABEL as Record<string, string>)[t] ?? t; }
}
