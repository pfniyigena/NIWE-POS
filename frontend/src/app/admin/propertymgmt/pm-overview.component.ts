import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { MaintenanceAttentionComponent } from './maintenance-attention.component';
import { LEASE_STATE_LABEL, PmOverview, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';

/** Property Management > Overview: occupancy, the rent roll, vacancies and the leases that need attention. Every number comes from GET /api/propertymgmt/overview. */
@Component({
  selector: 'app-pm-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, MaintenanceAttentionComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Property Management overview' | translate }}</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-primary" routerLink="/admin/propertymgmt/leases/new">{{ '+ Lease' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/propertymgmt/properties/new">{{ '+ Property' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/propertymgmt/owners">{{ 'Owners' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/propertymgmt/tenants">{{ 'Tenants' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!o && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

      <ng-container *ngIf="o">
        <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(11rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Occupancy' | translate }}</div><div class="kpi">{{ o.occupancyPercent | number: '1.0-1' }}%</div><div class="text-muted kpi-sub">{{ '{p0} of {p1} units that can be let' | translate: { p0: (o.occupied), p1: (o.units - o.offMarket) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Rent roll' | translate }}</div><div class="kpi">{{ o.monthlyRentRoll | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ 'per month from leases in force' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Vacant units' | translate }}</div><div class="kpi" [style.color]="o.vacant ? '#b45309' : ''"><a routerLink="/admin/propertymgmt/units" [queryParams]="{ occupancy: 'VACANT' }">{{ o.vacant }}</a></div><div class="text-muted kpi-sub">{{ 'asking {p0} a month · {p1} let from a date' | translate: { p0: (o.vacantMarketRent | number: '1.0-0'), p1: (o.upcoming) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Leases ending soon' | translate }}</div><div class="kpi" [style.color]="o.leasesExpiring ? '#b45309' : ''"><a routerLink="/admin/propertymgmt/leases" [queryParams]="{ expiringOnly: 1 }">{{ o.leasesExpiring }}</a></div><div class="text-muted kpi-sub">{{ 'within {p0} days' | translate: { p0: (o.expiryWarningDays) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Past their end date' | translate }}</div><div class="kpi" [style.color]="o.leasesExpired ? '#b91c1c' : ''">{{ o.leasesExpired }}</div><div class="text-muted kpi-sub">{{ 'renew or end them' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Draft leases' | translate }}</div><div class="kpi"><a routerLink="/admin/propertymgmt/leases" [queryParams]="{ state: 'DRAFT' }">{{ o.draftLeases }}</a></div><div class="text-muted kpi-sub">{{ 'waiting to be signed' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Rent to invoice' | translate }}</div><div class="kpi" [style.color]="o.rentToInvoice ? '#b45309' : ''"><a routerLink="/admin/propertymgmt/rent" [queryParams]="{ status: 'TO_INVOICE' }">{{ o.rentToInvoice }}</a></div><div class="text-muted kpi-sub">{{ '{p0} ready now' | translate: { p0: (o.rentToInvoiceAmount | number: '1.0-0') } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Rent in arrears' | translate }}</div><div class="kpi" [style.color]="o.arrears ? '#b91c1c' : ''"><a routerLink="/admin/propertymgmt/arrears">{{ o.arrears | number: '1.0-0' }}</a></div><div class="text-muted kpi-sub">{{ '{p0} tenant{p1} past due' | translate: { p0: (o.tenantsInArrears), p1: (o.tenantsInArrears === 1 ? '' : 's') } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Open repairs' | translate }}</div><div class="kpi" [style.color]="o.maintenanceOverdue ? '#b91c1c' : o.maintenanceUrgent ? '#b45309' : ''"><a routerLink="/admin/propertymgmt/maintenance">{{ o.maintenanceOpen }}</a></div><div class="text-muted kpi-sub">{{ '{p0} urgent or high · {p1} overdue' | translate: { p0: (o.maintenanceUrgent), p1: (o.maintenanceOverdue) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Owed to owners' | translate }}</div><div class="kpi" [style.color]="o.statementsToPay ? '#b45309' : ''"><a routerLink="/admin/propertymgmt/statements">{{ o.statementsToPayAmount | number: '1.0-0' }}</a></div><div class="text-muted kpi-sub">{{ '{p0} statement{p1} to pay · {p2} draft{p3}' | translate: { p0: (o.statementsToPay), p1: (o.statementsToPay === 1 ? '' : 's'), p2: (o.statementsDraft), p3: (o.statementsDraft === 1 ? '' : 's') } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Deposits held' | translate }}</div><div class="kpi"><a routerLink="/admin/propertymgmt/deposits">{{ o.depositsHeld | number: '1.0-0' }}</a></div><div class="text-muted kpi-sub"><span [style.color]="o.depositsRefundDue ? '#b91c1c' : ''">{{ '{p0} to refund ({p1})' | translate: { p0: (o.depositsRefundDue), p1: (o.depositsRefundDueAmount | number: '1.0-0') } }}</span></div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Documents lapsing' | translate }}</div><div class="kpi" [style.color]="o.documentsExpired ? '#b91c1c' : o.documentsExpiring ? '#b45309' : ''"><a routerLink="/admin/reports/pm-documents">{{ o.documentsExpired + o.documentsExpiring }}</a></div><div class="text-muted kpi-sub">{{ '{p0} expired · {p1} in 30 days' | translate: { p0: (o.documentsExpired), p1: (o.documentsExpiring) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Portfolio' | translate }}</div><div class="kpi"><a routerLink="/admin/propertymgmt/properties">{{ o.properties }}</a></div><div class="text-muted kpi-sub">{{ '{p0} units · {p1} owners · {p2} tenants' | translate: { p0: (o.units), p1: (o.owners), p2: (o.tenants) } }}</div></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <div class="flex justify-between align-center mb-2"><h4>{{ 'Leases needing attention' | translate }}</h4><a routerLink="/admin/propertymgmt/leases" [queryParams]="{ expiringOnly: 1 }" style="font-size:0.85rem;">{{ 'All' | translate }}</a></div>
            <table class="table-sm" *ngIf="o.expiringLeases.length; else none"><tbody>
              <tr *ngFor="let l of o.expiringLeases">
                <td><a [routerLink]="['/admin/propertymgmt/leases', l.id]"><strong>{{ l.code }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ l.tenantName }} · {{ l.propertyName }} {{ l.unitNo }}</div></td>
                <td style="text-align:right; white-space:nowrap;">{{ l.endDate | date: 'd MMM y' }}
                  <div><span class="badge" [class.badge-danger]="l.state === 'EXPIRED'" [class.badge-warning]="l.state === 'EXPIRING'">{{ stateName(l.state) }}{{ l.daysToEnd != null ? (l.daysToEnd < 0 ? ' · ' + (-l.daysToEnd) + ' days ago' : ' · ' + l.daysToEnd + ' days') : '' }}</span></div></td>
              </tr>
            </tbody></table>
            <ng-template #none><div class="text-muted">{{ 'No lease is ending soon.' | translate }}</div></ng-template>
          </div>
          <div class="card" style="padding:1rem;">
            <div class="flex justify-between align-center mb-2"><h4>{{ 'Longest vacant' | translate }}</h4><a routerLink="/admin/propertymgmt/units" [queryParams]="{ occupancy: 'VACANT' }" style="font-size:0.85rem;">{{ 'All vacant units' | translate }}</a></div>
            <table class="table-sm" *ngIf="o.vacantUnits.length; else nov"><tbody>
              <tr *ngFor="let u of o.vacantUnits">
                <td><a [routerLink]="['/admin/propertymgmt/units', u.id]"><strong>{{ u.propertyName }} · {{ u.unitNo }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ 'vacant since {p0}' | translate: { p0: (u.vacantSince | date: 'd MMM y') } }}</div></td>
                <td style="text-align:right;">{{ u.marketRent != null ? (u.marketRent | number: '1.0-0') : '—' }}<div class="text-muted" style="font-size:0.75rem;">{{ 'asking / month' | translate }}</div></td>
              </tr>
            </tbody></table>
            <ng-template #nov><div class="text-muted">{{ 'Nothing is vacant.' | translate }}</div></ng-template>
          </div>
        </div>
        <app-maintenance-attention [rows]="o.maintenanceAttention"></app-maintenance-attention>
      </ng-container>
    </app-shell>
  `,
})
export class PmOverviewComponent implements OnInit {
  o: PmOverview | null = null;
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService) {}

  ngOnInit(): void {
    this.api.overview().subscribe({ next: (o) => (this.o = o), error: (e) => (this.error = e?.error?.message ?? 'Could not load the overview') });
  }

  stateName(s: string): string { return (LEASE_STATE_LABEL as Record<string, string>)[s] ?? s; }
}
