import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { OCCUPANCY_LABEL, Occupancy, PM_UNIT_KINDS, PropertyMgmtApiService, PropertyRow, UnitRow, occupancyBadge, pmLabel } from '../../core/api/propertymgmt-api.service';

/** Property Management > Units: every unit of every property, with who is in it, filtered by status. */
@Component({
  selector: 'app-pm-unit-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'Units' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any property' | translate }}</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="oc" [(ngModel)]="occupancy" (ngModelChange)="load()">
          <option value="">{{ 'Any status' | translate }}</option><option *ngFor="let o of statuses" [value]="o.value">{{ o.label }}</option>
        </select>
        <input class="form-control" style="width:15rem;" [placeholder]="'Search unit, property, tenant' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Property' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Type' | translate }}</th><th class="text-end">{{ 'Asking rent' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Lease ends' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let u of list">
            <td data-label="Property"><a [routerLink]="['/admin/propertymgmt/properties', u.propertyId]">{{ u.propertyName }}</a></td>
            <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', u.id]"><strong>{{ u.unitNo }}</strong></a></td>
            <td data-label="Type">{{ kind(u.kind) }}</td>
            <td class="text-end" data-label="Asking rent">{{ u.marketRent != null ? (u.marketRent | number: '1.0-0') : '—' }}</td>
            <td data-label="Status"><span class="badge" [ngClass]="badge(u.occupancy)">{{ occ(u.occupancy) }}</span><div class="text-muted" style="font-size:0.75rem;" *ngIf="u.occupancy === 'VACANT' && u.vacantSince">{{ 'since {p0}' | translate: { p0: (u.vacantSince | date: 'd MMM y') } }}</div></td>
            <td data-label="Tenant"><a *ngIf="u.tenantId" [routerLink]="['/admin/propertymgmt/tenants', u.tenantId]">{{ u.tenantName }}</a><span *ngIf="!u.tenantId" class="text-muted">—</span></td>
            <td class="text-end" data-label="Rent">{{ u.rent != null ? (u.rent | number: '1.0-0') : '—' }}</td>
            <td data-label="Lease ends">{{ u.leaseEnd ? (u.leaseEnd | date: 'd MMM y') : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No units match.' | translate }}</p>
    </app-shell>
  `,
})
export class PmUnitListComponent implements OnInit {
  list: UnitRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  properties: PropertyRow[] = [];
  statuses = (Object.keys(OCCUPANCY_LABEL) as Occupancy[]).map((k) => ({ value: k, label: OCCUPANCY_LABEL[k] }));
  loaded = false;
  q = '';
  propertyId: number | null = null;
  occupancy = '';
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.occupancy = q.get('occupancy') ?? '';
    const pid = Number(q.get('propertyId'));
    if (pid) this.propertyId = pid;
    this.api.properties({}).subscribe({ next: (l) => (this.properties = l), error: () => {} });
    this.load();
  }

  kind(k: string): string { return pmLabel(PM_UNIT_KINDS, k); }
  occ(o: string): string { return (OCCUPANCY_LABEL as Record<string, string>)[o] ?? o; }
  badge(o: string): string { return occupancyBadge(o); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.unitsPage({ q: this.q.trim(), propertyId: this.propertyId, occupancy: this.occupancy, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the units'; },
    });
  }
}
