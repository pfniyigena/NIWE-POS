import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { PmExportComponent } from './pm-export.component';
import { LEASE_STATE_LABEL, LeaseRow, LeaseState, PropertyMgmtApiService, PropertyRow, RENT_FREQUENCIES, leaseBadge, pmLabel } from '../../core/api/propertymgmt-api.service';

/** Property Management > Leases: every lease with its state, filtered by state, property or tenant. */
@Component({
  selector: 'app-lease-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, PmExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Leases' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/propertymgmt/leases/new">{{ '+ Lease' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="state" (ngModelChange)="load()">
          <option value="">{{ 'Any state' | translate }}</option><option *ngFor="let s of states" [value]="s.value">{{ s.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any property' | translate }}</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ex" [(ngModel)]="expiringOnly" (ngModelChange)="load()" /> {{ 'Ending soon or past the end date' | translate }}</label>
        <input class="form-control" style="width:15rem;" [placeholder]="'Search lease, tenant, property, unit' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
        <app-pm-export kind="leases" [filters]="{ q: q.trim(), state: state, propertyId: propertyId, expiringOnly: expiringOnly }"></app-pm-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Lease' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Pays' | translate }}</th><th>{{ 'State' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let l of list">
            <td data-label="Lease"><a [routerLink]="['/admin/propertymgmt/leases', l.id]"><strong>{{ l.code }}</strong></a></td>
            <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', l.unitId]">{{ l.propertyName }} · {{ l.unitNo }}</a></td>
            <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', l.tenantId]">{{ l.tenantName }}</a></td>
            <td style="white-space:nowrap;" data-label="From">{{ l.startDate | date: 'd MMM y' }}</td><td style="white-space:nowrap;" data-label="To">{{ l.endDate | date: 'd MMM y' }}</td>
            <td class="text-end" data-label="Rent">{{ l.rent | number: '1.0-0' }}</td><td data-label="Pays">{{ freq(l.frequency) }}</td>
            <td data-label="State"><span class="badge" [ngClass]="badge(l.state)">{{ stateName(l.state) }}</span>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="l.daysToEnd != null && (l.state === 'EXPIRING' || l.state === 'EXPIRED')">{{ l.daysToEnd < 0 ? (-l.daysToEnd) + ' days ago' : l.daysToEnd + ' days left' }}</div></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No leases match.' | translate }}</p>
    </app-shell>
  `,
})
export class LeaseListComponent implements OnInit {
  list: LeaseRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  properties: PropertyRow[] = [];
  states = (Object.keys(LEASE_STATE_LABEL) as LeaseState[]).map((k) => ({ value: k, label: LEASE_STATE_LABEL[k] }));
  loaded = false;
  q = '';
  state = '';
  propertyId: number | null = null;
  expiringOnly = false;
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.state = q.get('state') ?? '';
    this.expiringOnly = q.get('expiringOnly') === '1' || q.get('expiringOnly') === 'true';
    const pid = Number(q.get('propertyId'));
    if (pid) this.propertyId = pid;
    this.api.properties({}).subscribe({ next: (l) => (this.properties = l), error: () => {} });
    this.load();
  }

  freq(f: string): string { return pmLabel(RENT_FREQUENCIES, f); }
  badge(s: string): string { return leaseBadge(s); }
  stateName(s: string): string { return (LEASE_STATE_LABEL as Record<string, string>)[s] ?? s; }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.leasesPage({ q: this.q.trim(), state: this.state, propertyId: this.propertyId, expiringOnly: this.expiringOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the leases'; },
    });
  }
}
