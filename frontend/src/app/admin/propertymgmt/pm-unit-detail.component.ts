import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { MaintenancePanelComponent } from './maintenance-panel.component';
import { LEASE_STATE_LABEL, OCCUPANCY_LABEL, PM_UNIT_KINDS, PmUnitKind, PropertyMgmtApiService, UnitDto, UnitPayload, leaseBadge, occupancyBadge, pmLabel } from '../../core/api/propertymgmt-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();

/** Property Management > a unit: its details, who is in it and every lease it has had. */
@Component({
  selector: 'app-pm-unit-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, MaintenancePanelComponent, PmDocumentsPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!d && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="d">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a [routerLink]="['/admin/propertymgmt/properties', d.unit.propertyId]" style="font-size:0.85rem;">← {{ d.unit.propertyName }}</a>
            <h3>{{ 'Unit {p0}' | translate: { p0: (d.unit.unitNo) } }}
              <span class="badge" [ngClass]="badge(d.unit.occupancy)" style="font-size:0.8rem; vertical-align:middle;">{{ occ(d.unit.occupancy) }}</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="d.canManage">
            <a class="btn btn-primary" routerLink="/admin/propertymgmt/leases/new" [queryParams]="{ unitId: d.unit.id }" *ngIf="d.unit.occupancy !== 'OFF_MARKET'">{{ '+ Lease' | translate }}</a>
            <button class="btn btn-outline-secondary" type="button" (click)="editing ? editing = false : startEdit()">{{ editing ? ('Cancel' | translate) : ('Edit' | translate) }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()" [disabled]="busy">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <form *ngIf="editing" class="card mb-3" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:8rem;"><label class="form-label">{{ 'Unit no. *' | translate }}</label><input class="form-control" name="uno" [(ngModel)]="f.unitNo" maxlength="40" /></div>
            <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Type' | translate }}</label><select class="form-select" name="uk" [(ngModel)]="f.kind"><option *ngFor="let k of kinds" [ngValue]="k.value">{{ k.label }}</option></select></div>
            <div class="form-group mb-2" style="width:7rem;"><label class="form-label">{{ 'Floor' | translate }}</label><input class="form-control" name="uf" [(ngModel)]="f.floor" maxlength="40" /></div>
            <div class="form-group mb-2" style="width:6rem;"><label class="form-label">{{ 'Beds' | translate }}</label><input class="form-control" type="number" min="0" max="50" name="ub" [(ngModel)]="f.bedrooms" /></div>
            <div class="form-group mb-2" style="width:6rem;"><label class="form-label">{{ 'Baths' | translate }}</label><input class="form-control" type="number" min="0" max="50" name="ubt" [(ngModel)]="f.bathrooms" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="width:8rem;"><label class="form-label">{{ 'Size m²' | translate }}</label><input class="form-control" type="number" min="0" name="us" [(ngModel)]="f.sizeSqm" [placeholder]="'0 clears' | translate" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Asking rent / month' | translate }}</label><input class="form-control" type="number" min="0" name="ur" [(ngModel)]="f.marketRent" [placeholder]="'0 clears' | translate" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Usual deposit' | translate }}</label><input class="form-control" type="number" min="0" name="ud" [(ngModel)]="f.defaultDeposit" [placeholder]="'0 clears' | translate" /></div>
          </div>
          <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="om" [(ngModel)]="f.offMarket" /> {{ 'Off the market (not to be let for now)' | translate }}</label>
          <div class="form-group mb-2" *ngIf="f.offMarket"><label class="form-label">{{ 'Why' | translate }}</label><input class="form-control" name="on" [(ngModel)]="f.offMarketNote" maxlength="500" [placeholder]="'e.g. being repainted' | translate" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="2" name="nt" [(ngModel)]="f.notes" maxlength="1000"></textarea></div>
          <div><button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Saving…' | translate) : ('Save changes' | translate) }}</button></div>
        </form>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Details' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Property' | translate }}</td><td><a [routerLink]="['/admin/propertymgmt/properties', d.unit.propertyId]">{{ d.unit.propertyName }}</a></td></tr>
              <tr><td class="text-muted">{{ 'Owner' | translate }}</td><td><a *ngIf="d.ownerId" [routerLink]="['/admin/propertymgmt/owners', d.ownerId]">{{ d.ownerName }}</a></td></tr>
              <tr><td class="text-muted">{{ 'Type' | translate }}</td><td>{{ kind(d.unit.kind) }}</td></tr>
              <tr><td class="text-muted">{{ 'Floor' | translate }}</td><td>{{ d.unit.floor || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Bedrooms / baths' | translate }}</td><td>{{ d.unit.bedrooms ?? '—' }} / {{ d.unit.bathrooms ?? '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Size' | translate }}</td><td>{{ d.unit.sizeSqm != null ? (d.unit.sizeSqm | number: '1.0-2') + ' m²' : '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Asking rent' | translate }}</td><td>{{ d.unit.marketRent != null ? (d.unit.marketRent | number: '1.0-0') + ' a month' : '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Usual deposit' | translate }}</td><td>{{ d.unit.defaultDeposit != null ? (d.unit.defaultDeposit | number: '1.0-0') : '—' }}</td></tr>
              <tr *ngIf="d.unit.offMarketNote"><td class="text-muted">{{ 'Off the market' | translate }}</td><td>{{ d.unit.offMarketNote }}</td></tr>
              <tr *ngIf="d.unit.notes"><td class="text-muted">{{ 'Notes' | translate }}</td><td style="white-space:pre-wrap;">{{ d.unit.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Right now' | translate }}</h4>
            <ng-container *ngIf="d.unit.leaseId; else empty">
              <div><a [routerLink]="['/admin/propertymgmt/tenants', d.unit.tenantId]"><strong>{{ d.unit.tenantName }}</strong></a></div>
              <div class="text-muted">{{ 'Lease' | translate }} <a [routerLink]="['/admin/propertymgmt/leases', d.unit.leaseId]">{{ d.unit.leaseCode }}</a> {{ '· {p0} to {p1}' | translate: { p0: (d.unit.leaseStart | date: 'd MMM y'), p1: (d.unit.leaseEnd | date: 'd MMM y') } }}</div>
              <div class="mt-1">{{ d.unit.rent | number: '1.0-0' }} <span class="text-muted">{{ '({p0} a month)' | translate: { p0: (d.unit.monthlyRent | number: '1.0-0') } }}</span></div>
              <div class="mt-1" *ngIf="d.unit.holdingOver" style="color:#b91c1c;">{{ 'Past its end date: renew or end the lease.' | translate }}</div>
            </ng-container>
            <ng-template #empty>
              <div class="text-muted">{{ 'Nobody is in this unit.' | translate }}</div>
              <div class="text-muted" *ngIf="d.unit.vacantSince">{{ 'Vacant since {p0}.' | translate: { p0: (d.unit.vacantSince | date: 'd MMM y') } }}</div>
            </ng-template>
          </div>
        </div>

        <div class="card" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Leases' | translate }}</h4>
          <table class="stack table-sm" *ngIf="d.leases.length; else nol">
            <thead><tr><th>{{ 'Lease' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let l of d.leases">
              <td data-label="Lease"><a [routerLink]="['/admin/propertymgmt/leases', l.id]"><strong>{{ l.code }}</strong></a></td>
              <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', l.tenantId]">{{ l.tenantName }}</a></td>
              <td data-label="From">{{ l.startDate | date: 'd MMM y' }}</td><td data-label="To">{{ l.endDate | date: 'd MMM y' }}</td><td class="text-end" data-label="Rent">{{ l.rent | number: '1.0-0' }}</td>
              <td data-label="Status"><span class="badge" [ngClass]="lbadge(l.state)">{{ stateName(l.state) }}</span></td>
            </tr></tbody>
          </table>
          <ng-template #nol><div class="text-muted">{{ 'No leases yet.' | translate }}</div></ng-template>
        </div>
        <app-maintenance-panel [unitId]="d.unit.id" [canManage]="d.canManage"></app-maintenance-panel>
        <app-pm-documents-panel subjectType="UNIT" [subjectId]="d.unit.id" [canManage]="d.canManage" defaultCategory="PHOTO"></app-pm-documents-panel>
      </ng-container>
    </app-shell>
  `,
})
export class PmUnitDetailComponent implements OnInit {
  d: UnitDto | null = null;
  error: string | null = null;
  editing = false;
  busy = false;
  kinds = PM_UNIT_KINDS;
  f = { unitNo: '', kind: 'APARTMENT' as PmUnitKind, floor: '', bedrooms: null as number | null, bathrooms: null as number | null, sizeSqm: null as number | null, marketRent: null as number | null,
    defaultDeposit: null as number | null, offMarket: false, offMarketNote: '', notes: '' };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.unit(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (d) => (this.d = d), error: (e) => (this.error = e?.error?.message ?? T('Could not load the unit')) });
  }

  kind(k: string): string { return pmLabel(PM_UNIT_KINDS, k); }
  occ(o: string): string { return (OCCUPANCY_LABEL as Record<string, string>)[o] ?? o; }
  badge(o: string): string { return occupancyBadge(o); }
  lbadge(s: string): string { return leaseBadge(s); }
  stateName(s: string): string { return (LEASE_STATE_LABEL as Record<string, string>)[s] ?? s; }

  startEdit(): void {
    const u = this.d!.unit;
    this.f = { unitNo: u.unitNo, kind: u.kind, floor: u.floor ?? '', bedrooms: u.bedrooms, bathrooms: u.bathrooms, sizeSqm: u.sizeSqm, marketRent: u.marketRent, defaultDeposit: u.defaultDeposit,
      offMarket: u.offMarket, offMarketNote: u.offMarketNote ?? '', notes: u.notes ?? '' };
    this.editing = true;
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!txt(f.unitNo)) { this.error = T('Give the unit a number.'); return; }
    // 0 clears an optional amount on the server, so an emptied box is sent as 0.
    const p: UnitPayload = { unitNo: txt(f.unitNo), kind: f.kind, floor: txt(f.floor), sizeSqm: num(f.sizeSqm) ?? 0, marketRent: num(f.marketRent) ?? 0, defaultDeposit: num(f.defaultDeposit) ?? 0,
      offMarket: f.offMarket, offMarketNote: f.offMarket ? txt(f.offMarketNote) : '', notes: txt(f.notes) };
    if (num(f.bedrooms) != null) p.bedrooms = num(f.bedrooms);
    if (num(f.bathrooms) != null) p.bathrooms = num(f.bathrooms);
    this.busy = true;
    this.api.updateUnit(this.d!.unit.id, p).subscribe({
      next: () => { this.busy = false; this.editing = false; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save the unit'); },
    });
  }

  remove(): void {
    if (!confirm(T('Delete unit {p0}?', { p0: this.d!.unit.unitNo }))) return;
    const pid = this.d!.unit.propertyId;
    this.busy = true;
    this.error = null;
    this.api.deleteUnit(this.d!.unit.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/properties', pid]),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not delete the unit'); },
    });
  }
}
