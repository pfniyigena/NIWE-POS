import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { DEAL_STAGE_LABEL, DealRow, DealStage, Option, RealEstateApiService } from '../../core/api/realestate-api.service';

/** Real Estate > Deals: offers, reservations and sales, with what the buyer has paid so far. */
@Component({
  selector: 'app-deal-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ReExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Deals' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/realestate/deals/new">{{ '+ Record an offer' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="stage" (ngModelChange)="load()">
          <option value="">{{ 'All stages' | translate }}</option><option *ngFor="let s of stages" [value]="s.value">{{ s.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ag" [(ngModel)]="agentId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any agent' | translate }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="oo" [(ngModel)]="openOnly" (ngModelChange)="load()" /> {{ 'Open only' | translate }}</label>
        <input class="form-control" style="width:15rem;" [placeholder]="'Search deal, buyer, property' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
        <app-re-export kind="deals" [filters]="{ q: q.trim(), stage: stage, agentId: agentId, openOnly: openOnly }"></app-re-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Deal' | translate }}</th><th>{{ 'Property' | translate }}</th><th>{{ 'Buyer' | translate }}</th><th>{{ 'Stage' | translate }}</th><th class="text-end">{{ 'Asking' | translate }}</th><th class="text-end">{{ 'Offer / agreed' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Commission' | translate }}</th><th>{{ 'Agent' | translate }}</th><th>{{ 'Next date' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let d of list">
            <td data-label="Deal"><a [routerLink]="['/admin/realestate/deals', d.id]"><strong>{{ d.code }}</strong></a></td>
            <td data-label="Property"><a [routerLink]="['/admin/realestate/properties', d.propertyId]">{{ d.propertyCode }}</a><div class="text-muted" style="font-size:0.8rem;">{{ d.propertyTitle }}</div></td>
            <td data-label="Buyer">{{ d.buyerName }}</td>
            <td data-label="Stage"><span class="badge" [class.badge-success]="d.stage === 'COMPLETED'" [class.badge-danger]="d.stage === 'CANCELLED'" [class.badge-warning]="d.stage === 'RESERVED' || d.stage === 'AGREEMENT'" [class.badge-secondary]="d.stage === 'OFFER'">{{ stageName(d.stage) }}</span>
              <span class="badge badge-danger" *ngIf="d.overdue" style="margin-left:0.3rem;">{{ 'Late' | translate }}</span></td>
            <td class="text-end" data-label="Asking">{{ d.listPrice != null ? (d.listPrice | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Offer / agreed">{{ (d.agreedPrice ?? d.offerAmount) | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Paid">{{ d.agreedPrice != null ? (d.paid | number: '1.0-0') : '—' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="d.outstanding != null && d.stage !== 'COMPLETED' && d.stage !== 'CANCELLED'">{{ '{p0} to come' | translate: { p0: (d.outstanding | number: '1.0-0') } }}</div></td>
            <td class="text-end" data-label="Commission">{{ d.commissionTotal ? (d.commissionTotal | number: '1.0-0') : '—' }}</td>
            <td data-label="Agent">{{ d.agentName || '—' }}</td>
            <td style="white-space:nowrap;" data-label="Next date">{{ nextDate(d) }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No deals match.' | translate }}</p>
    </app-shell>
  `,
})
export class DealListComponent implements OnInit {
  list: DealRow[] = [];
  loaded = false;
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  agents: Option[] = [];
  stages = (Object.keys(DEAL_STAGE_LABEL) as DealStage[]).map((k) => ({ value: k, label: DEAL_STAGE_LABEL[k] }));
  stage = '';
  agentId: number | null = null;
  openOnly = true;
  q = '';
  error: string | null = null;

  constructor(private api: RealEstateApiService) {}

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.load();
  }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.dealsPage({ q: this.q.trim(), stage: this.stage, agentId: this.agentId, openOnly: this.openOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the deals'; },
    });
  }

  stageName(s: string): string { return (DEAL_STAGE_LABEL as Record<string, string>)[s] ?? s; }

  nextDate(d: DealRow): string {
    const f = (x: string) => new Date(x + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
    if (d.stage === 'OFFER') return d.offerValidUntil ? 'offer valid to ' + f(d.offerValidUntil) : '—';
    if (d.stage === 'RESERVED') return d.reservationExpiry ? 'reserved to ' + f(d.reservationExpiry) : d.expectedCompletion ? 'completion ' + f(d.expectedCompletion) : '—';
    if (d.stage === 'AGREEMENT') return d.expectedCompletion ? 'completion ' + f(d.expectedCompletion) : '—';
    if (d.stage === 'COMPLETED') return d.completedDate ? 'sold ' + f(d.completedDate) : '—';
    return '—';
  }
}
