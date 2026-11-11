import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { Count, LEAD_STATUS_LABEL, LeadStatus, PROPERTY_STATUS_LABEL, PropertyStatus, RealEstateApiService, ReOverview, SOURCE_LABEL, LeadSource, VIEWING_STATUS_LABEL } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
/** Real Estate > Overview: stock, leads, today's viewings and the leads that need a call. Every number comes from GET /api/realestate/overview. */
@Component({
  selector: 'app-re-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Real Estate overview' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-primary" routerLink="/admin/realestate/properties/new">{{ '+ Property' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/realestate/leads/new">{{ '+ Lead' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/realestate/deals/new">{{ '+ Offer' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!o && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

      <ng-container *ngIf="o">
        <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(11rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Available now' | translate }}</div><div class="kpi">{{ o.available }}</div><div class="text-muted kpi-sub">{{ '{p0} reserved · {p1} sold or let · {p2} in all' | translate: { p0: (o.reserved), p1: (o.soldOrLet), p2: (o.properties) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Value for sale' | translate }}</div><div class="kpi">{{ o.availableSaleValue | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ 'asking prices of available stock' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Rent on offer' | translate }}</div><div class="kpi">{{ o.availableMonthlyRent | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ 'per month, if everything let' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Open leads' | translate }}</div><div class="kpi">{{ o.openLeads }}</div><div class="text-muted kpi-sub">{{ '{p0} new this month' | translate: { p0: (o.newLeadsThisMonth) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Follow-ups overdue' | translate }}</div><div class="kpi" [style.color]="o.followUpsOverdue ? '#b91c1c' : ''"><a routerLink="/admin/realestate/follow-ups" [queryParams]="{ all: 1 }" [style.color]="o.followUpsOverdue ? '#b91c1c' : ''">{{ o.followUpsOverdue }}</a></div><div class="text-muted kpi-sub">{{ 'leads waiting for a call' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Deals in progress' | translate }}</div><div class="kpi"><a routerLink="/admin/realestate/deals">{{ o.openDeals }}</a></div><div class="text-muted kpi-sub">{{ 'worth {p0}' | translate: { p0: (o.openDealValue | number: '1.0-0') } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Sold this month' | translate }}</div><div class="kpi">{{ o.soldThisMonth }}</div><div class="text-muted kpi-sub">{{ o.soldValueThisMonth | number: '1.0-0' }} · <a routerLink="/admin/realestate/commissions">{{ 'commission to post {p0}' | translate: { p0: (o.commissionToPost | number: '1.0-0') } }}</a></div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Project units for sale' | translate }}</div><div class="kpi"><a routerLink="/admin/realestate/projects">{{ o.unitsAvailable }}</a></div><div class="text-muted kpi-sub">{{ '{p0} reserved' | translate: { p0: (o.unitsReserved) } }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Units sold this month' | translate }}</div><div class="kpi"><a routerLink="/admin/realestate/unit-sales">{{ o.unitsSoldThisMonth }}</a></div><div class="text-muted kpi-sub">{{ '{p0} · buyers still owe' | translate: { p0: (o.unitSalesValueThisMonth | number: '1.0-0') } }} <a routerLink="/admin/realestate/collections">{{ o.unitReceivable | number: '1.0-0' }}</a></div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Documents expiring' | translate }}</div><div class="kpi" [style.color]="o.documentsExpiring ? '#b45309' : ''">{{ o.documentsExpiring }}</div><div class="text-muted kpi-sub">{{ 'expired or due within 30 days' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Valuations expired' | translate }}</div><div class="kpi" [style.color]="o.valuationsExpired ? '#b91c1c' : ''">{{ o.valuationsExpired }}</div><div class="text-muted kpi-sub">{{ 'properties needing a fresh valuation' | translate }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">{{ 'Viewings today' | translate }}</div><div class="kpi">{{ o.viewingsToday }}</div><div class="text-muted kpi-sub">{{ '{p0} in the next 7 days' | translate: { p0: (o.viewingsNext7Days) } }}</div></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <div class="flex justify-between align-center mb-2"><h4>{{ 'Upcoming viewings' | translate }}</h4><a routerLink="/admin/realestate/viewings" style="font-size:0.85rem;">{{ 'All viewings' | translate }}</a></div>
            <table class="table-sm" *ngIf="o.upcomingViewings.length; else noviews"><tbody>
              <tr *ngFor="let v of o.upcomingViewings">
                <td style="white-space:nowrap;">{{ v.startAt | date: 'EEE d MMM, HH:mm' }}</td>
                <td><a [routerLink]="['/admin/realestate/properties', v.propertyId]">{{ v.propertyCode }}</a> · {{ v.propertyTitle }}<div class="text-muted" style="font-size:0.8rem;"><a [routerLink]="['/admin/realestate/leads', v.leadId]">{{ v.leadName }}</a> {{ '· with {p0}' | translate: { p0: (v.agentName || '—') } }}</div></td>
              </tr>
            </tbody></table>
            <ng-template #noviews><div class="text-muted">{{ 'No viewings booked for the next seven days.' | translate }}</div></ng-template>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Leads that need a call' | translate }}</h4>
            <table class="table-sm" *ngIf="o.needAttention.length; else nolead"><tbody>
              <tr *ngFor="let l of o.needAttention">
                <td><a [routerLink]="['/admin/realestate/leads', l.id]"><strong>{{ l.name }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ l.phone || 'no phone' }} · {{ l.assignedName || 'unassigned' }}</div></td>
                <td style="text-align:right; font-size:0.85rem;">
                  <span class="badge badge-danger" *ngIf="l.followUpOverdue">{{ 'Follow-up {p0}' | translate: { p0: (l.nextFollowUp | date: 'd MMM') } }}</span>
                  <span class="badge badge-warning" *ngIf="!l.followUpOverdue && l.stale">{{ 'No contact lately' | translate }}</span>
                </td>
              </tr>
            </tbody></table>
            <ng-template #nolead><div class="text-muted">{{ 'Nobody is waiting. Good.' | translate }}</div></ng-template>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Properties by status' | translate }}</h4>
            <div *ngFor="let s of o.propertiesByStatus" class="bar-row">
              <span class="bar-label">{{ propLabel(s.label) }}</span>
              <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.propertiesByStatus)" style="background:#0f766e;"></span></span>
              <span class="bar-num">{{ s.value }}</span>
            </div>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Leads by stage' | translate }}</h4>
            <div *ngFor="let s of o.leadsByStatus" class="bar-row">
              <span class="bar-label">{{ leadLabel(s.label) }}</span>
              <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.leadsByStatus)" style="background:#2563eb;"></span></span>
              <span class="bar-num">{{ s.value }}</span>
            </div>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Where leads come from' | translate }}</h4>
            <div *ngFor="let s of o.leadsBySource" class="bar-row">
              <span class="bar-label">{{ sourceLabel(s.label) }}</span>
              <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.leadsBySource)" style="background:#7c3aed;"></span></span>
              <span class="bar-num">{{ s.value }}</span>
            </div>
            <div class="text-muted" *ngIf="!o.leadsBySource.length">{{ 'No leads yet.' | translate }}</div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
  styles: [`
    .kpi-label { font-size: 0.8rem; }
    .kpi { font-size: 1.75rem; font-weight: 600; line-height: 1.2; }
    .kpi-sub { font-size: 0.75rem; }
    .bar-row { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.4rem; }
    .bar-label { width: 8rem; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { flex: 1; height: 0.9rem; background: rgba(127, 127, 127, 0.15); border-radius: 3px; overflow: hidden; }
    .bar-fill { display: block; height: 100%; min-width: 2px; border-radius: 3px; }
    .bar-num { width: 3rem; text-align: right; font-size: 0.85rem; }
  `],
})
export class ReOverviewComponent implements OnInit {
  o: ReOverview | null = null;
  error: string | null = null;
  viewingLabel = VIEWING_STATUS_LABEL;

  constructor(private api: RealEstateApiService) {}

  ngOnInit(): void {
    this.api.overview().subscribe({ next: (o) => (this.o = o), error: (e) => (this.error = e?.error?.message ?? T('Could not load the overview')) });
  }

  max(list: Count[]): number { return list.reduce((m, s) => Math.max(m, s.value), 0); }
  pct(v: number, list: Count[]): number { const m = this.max(list); return m > 0 ? Math.max(0, Math.min(100, (v / m) * 100)) : 0; }
  propLabel(k: string): string { return PROPERTY_STATUS_LABEL[k as PropertyStatus] ?? k; }
  leadLabel(k: string): string { return LEAD_STATUS_LABEL[k as LeadStatus] ?? k; }
  sourceLabel(k: string): string { return SOURCE_LABEL[k as LeadSource] ?? k; }
}
