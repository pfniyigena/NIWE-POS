import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { DEPOSIT_STATE_LABEL, DepositRow, DepositState, DepositTotals, PmDepositApiService, depositBadge } from '../../core/api/pm-deposit-api.service';
import { PropertyMgmtApiService, PropertyRow } from '../../core/api/propertymgmt-api.service';

/** Property Management > Deposits: the security deposit of every lease, with what is held and what has to be refunded. */
@Component({
  selector: 'app-pm-deposit-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Security deposits' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/reports/pm-deposits">{{ 'Report' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;" *ngIf="totals">
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">{{ 'Held now' | translate }}</div><div class="kpi">{{ totals.held | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ 'on {p0} lease{p1}' | translate: { p0: (totals.heldCount), p1: (totals.heldCount === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">{{ 'To refund' | translate }}</div><div class="kpi" [style.color]="totals.refundDueCount ? '#b91c1c' : ''">{{ totals.refundDue | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ '{p0} ended lease{p1}' | translate: { p0: (totals.refundDueCount), p1: (totals.refundDueCount === 1 ? '' : 's') } }}</div></div>
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">{{ 'Still to collect' | translate }}</div><div class="kpi" [style.color]="totals.toCollect ? '#b45309' : ''">{{ totals.toCollect | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ 'on leases not yet paid up' | translate }}</div></div>
      </div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="state" (ngModelChange)="load()">
          <option value="">{{ 'Any state' | translate }}</option><option *ngFor="let s of states" [value]="s.value">{{ s.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any property' | translate }}</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <input class="form-control" style="width:15rem;" [placeholder]="'Search lease, tenant, property, unit' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Lease' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th class="text-end">{{ 'Agreed' | translate }}</th><th class="text-end">{{ 'Paid in' | translate }}</th><th class="text-end">{{ 'Kept' | translate }}</th><th class="text-end">{{ 'Refunded' | translate }}</th><th class="text-end">{{ 'Held' | translate }}</th><th>{{ 'State' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list">
            <td data-label="Lease"><a [routerLink]="['/admin/propertymgmt/leases', r.leaseId]"><strong>{{ r.leaseCode }}</strong></a></td>
            <td data-label="Unit">{{ r.propertyName }} · {{ r.unitNo }}</td>
            <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', r.tenantId]">{{ r.tenantName }}</a></td>
            <td class="text-end" data-label="Agreed">{{ r.agreed | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Paid in">{{ r.paidIn | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Kept">{{ r.deducted ? (r.deducted | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Refunded">{{ r.refunded ? (r.refunded | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Held"><strong>{{ r.held | number: '1.0-0' }}</strong></td>
            <td data-label="State"><span class="badge" [ngClass]="badge(r.state)">{{ stateName(r.state) }}</span></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No deposits match.' | translate }}</p>
    </app-shell>
  `,
})
export class PmDepositListComponent implements OnInit {
  list: DepositRow[] = [];
  totals: DepositTotals | null = null;
  properties: PropertyRow[] = [];
  states = (Object.keys(DEPOSIT_STATE_LABEL) as DepositState[]).filter((s) => s !== 'NONE').map((k) => ({ value: k, label: DEPOSIT_STATE_LABEL[k] }));
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  loaded = false;
  q = '';
  state = '';
  propertyId: number | null = null;
  error: string | null = null;

  constructor(private api: PmDepositApiService, private pm: PropertyMgmtApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.state = this.route.snapshot.queryParamMap.get('state') ?? '';
    this.pm.properties({}).subscribe({ next: (l) => (this.properties = l), error: () => {} });
    this.api.totals().subscribe({ next: (t) => (this.totals = t), error: () => {} });
    this.load();
  }

  badge(s: string): string { return depositBadge(s); }
  stateName(s: string): string { return (DEPOSIT_STATE_LABEL as Record<string, string>)[s] ?? s; }
  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.deposits({ q: this.q.trim(), state: this.state, propertyId: this.propertyId, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the deposits'; },
    });
  }
}
