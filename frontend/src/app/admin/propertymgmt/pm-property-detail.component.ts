import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { MaintenancePanelComponent } from './maintenance-panel.component';
import { BulkUnitsPayload, OCCUPANCY_LABEL, PM_PROPERTY_KINDS, PM_UNIT_KINDS, PmUnitKind, PropertyDto, PropertyMgmtApiService, UnitPayload, UnitRow, occupancyBadge, pmLabel } from '../../core/api/propertymgmt-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';
import { PmOwnershipPanelComponent } from './pm-ownership-panel.component';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();

interface UnitForm { unitNo: string; kind: PmUnitKind; floor: string; bedrooms: number | null; bathrooms: number | null; sizeSqm: number | null; marketRent: number | null; defaultDeposit: number | null; }

/** Property Management > a managed property: its units with who is in them, and adding units one by one or in a run. */
@Component({
  selector: 'app-pm-property-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, MaintenancePanelComponent, PmDocumentsPanelComponent, PmOwnershipPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!p && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="p">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/properties" style="font-size:0.85rem;">{{ '← Properties' | translate }}</a>
            <h3>{{ p.name }} <span class="text-muted" style="font-size:0.9rem;">{{ p.code }}</span>
              <span class="badge badge-secondary" *ngIf="!p.active" style="font-size:0.8rem;">{{ 'Inactive' | translate }}</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="p.canManage">
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/propertymgmt/properties', p.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-outline-secondary" type="button" (click)="toggleActive()" [disabled]="busy">{{ p.active ? ('Mark inactive' | translate) : ('Mark active' | translate) }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()" [disabled]="busy">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Details' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ p.ownerCount > 1 ? ('Owners' | translate) : ('Owner' | translate) }}</td><td><a *ngIf="p.ownerCount <= 1" [routerLink]="['/admin/propertymgmt/owners', p.ownerId]">{{ p.ownerName }}</a><span *ngIf="p.ownerCount > 1">{{ p.ownersLabel }}</span></td></tr>
              <tr><td class="text-muted">{{ 'Type' | translate }}</td><td>{{ kind(p.kind) }}</td></tr>
              <tr><td class="text-muted">{{ 'Area' | translate }}</td><td>{{ p.area || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Address' | translate }}</td><td>{{ p.address || '—' }}</td></tr>
              <tr *ngIf="p.titleReference"><td class="text-muted">{{ 'Title / plot ref.' | translate }}</td><td>{{ p.titleReference }}</td></tr>
              <tr><td class="text-muted">{{ 'Managed since' | translate }}</td><td>{{ p.managedFrom ? (p.managedFrom | date: 'd MMM y') : '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Management fee' | translate }}</td><td>{{ p.effectiveFeePercent != null ? (p.effectiveFeePercent | number: '1.0-2') + '%' : '—' }}<span class="text-muted" *ngIf="p.feePercent == null && p.effectiveFeePercent != null"> {{ '(the main owner\\'s / usual fee)' | translate }}</span></td></tr>
              <tr *ngIf="p.notes"><td class="text-muted">{{ 'Notes' | translate }}</td><td style="white-space:pre-wrap;">{{ p.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Occupancy' | translate }}</h4>
            <div style="font-size:1.6rem;">{{ p.occupied }} <span class="text-muted" style="font-size:0.9rem;">{{ 'of {p0} units occupied' | translate: { p0: (p.unitCount) } }}</span></div>
            <div class="text-muted">{{ '{p0} vacant · {p1} let from a date · {p2} off the market' | translate: { p0: (p.vacant), p1: (p.upcoming), p2: (p.offMarket) } }}</div>
            <div class="mt-2">{{ 'Rent roll' | translate }} <strong>{{ p.monthlyRent | number: '1.0-0' }}</strong> {{ 'a month' | translate }}<span class="text-muted" *ngIf="p.vacantMarketRent"> {{ '· vacant units would add {p0}' | translate: { p0: (p.vacantMarketRent | number: '1.0-0') } }}</span></div>
          </div>
        </div>

        <div class="card" style="padding:1rem;">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>{{ 'Units' | translate }}</h4>
            <div class="flex gap-2" *ngIf="p.canManage && p.active">
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="mode = mode === 'one' ? null : 'one'; error = null">{{ '+ Add a unit' | translate }}</button>
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="mode = mode === 'many' ? null : 'many'; error = null">{{ '+ Add many' | translate }}</button>
            </div>
          </div>

          <form *ngIf="mode === 'one'" (ngSubmit)="addOne()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
            <ng-container *ngTemplateOutlet="fields; context: { $implicit: nf, many: false }"></ng-container>
            <div class="flex gap-2"><button class="btn btn-sm btn-primary" type="submit" [disabled]="busy">{{ 'Save unit' | translate }}</button><button class="btn btn-sm btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
          </form>
          <form *ngIf="mode === 'many'" (ngSubmit)="addMany()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
            <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Makes a run of alike units, for example prefix "A-", from 1 to 20, 2 digits gives A-01 … A-20 (up to 200 at a time).' | translate }}</div>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group mb-2" style="width:8rem;"><label class="form-label">{{ 'Prefix' | translate }}</label><input class="form-control" name="bp" [(ngModel)]="bf.prefix" maxlength="30" /></div>
              <div class="form-group mb-2" style="width:7rem;"><label class="form-label">{{ 'From *' | translate }}</label><input class="form-control" type="number" min="0" name="bfr" [(ngModel)]="bf.from" /></div>
              <div class="form-group mb-2" style="width:7rem;"><label class="form-label">{{ 'To *' | translate }}</label><input class="form-control" type="number" min="0" name="bto" [(ngModel)]="bf.to" /></div>
              <div class="form-group mb-2" style="width:8rem;"><label class="form-label">{{ 'Digits' | translate }}</label><input class="form-control" type="number" min="0" max="6" name="bpd" [(ngModel)]="bf.pad" /></div>
            </div>
            <ng-container *ngTemplateOutlet="fields; context: { $implicit: bf, many: true }"></ng-container>
            <div class="flex gap-2"><button class="btn btn-sm btn-primary" type="submit" [disabled]="busy">{{ 'Make the units' | translate }}</button><button class="btn btn-sm btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
          </form>

          <ng-template #fields let-f let-many="many">
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group mb-2" style="min-width:8rem;" *ngIf="!many"><label class="form-label">{{ 'Unit no. *' | translate }}</label><input class="form-control" name="uno" [(ngModel)]="f.unitNo" maxlength="40" /></div>
              <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Type' | translate }}</label><select class="form-select" name="uk" [(ngModel)]="f.kind"><option *ngFor="let k of unitKinds" [ngValue]="k.value">{{ k.label }}</option></select></div>
              <div class="form-group mb-2" style="width:7rem;"><label class="form-label">{{ 'Floor' | translate }}</label><input class="form-control" name="uf" [(ngModel)]="f.floor" maxlength="40" /></div>
              <div class="form-group mb-2" style="width:6rem;"><label class="form-label">{{ 'Beds' | translate }}</label><input class="form-control" type="number" min="0" max="50" name="ub" [(ngModel)]="f.bedrooms" /></div>
              <div class="form-group mb-2" style="width:6rem;"><label class="form-label">{{ 'Baths' | translate }}</label><input class="form-control" type="number" min="0" max="50" name="ubt" [(ngModel)]="f.bathrooms" /></div>
              <div class="form-group mb-2" style="width:7rem;"><label class="form-label">{{ 'Size m²' | translate }}</label><input class="form-control" type="number" min="0" name="us" [(ngModel)]="f.sizeSqm" /></div>
              <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Asking rent / month' | translate }}</label><input class="form-control" type="number" min="0" name="ur" [(ngModel)]="f.marketRent" /></div>
              <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Usual deposit' | translate }}</label><input class="form-control" type="number" min="0" name="ud" [(ngModel)]="f.defaultDeposit" /></div>
            </div>
          </ng-template>

          <div style="overflow-x:auto;">
            <table class="stack table-sm" *ngIf="p.units.length; else nounits">
              <thead><tr><th>{{ 'Unit' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Beds' | translate }}</th><th class="text-end">{{ 'Asking rent' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Lease ends' | translate }}</th><th></th></tr></thead>
              <tbody>
                <tr *ngFor="let u of p.units">
                  <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', u.id]"><strong>{{ u.unitNo }}</strong></a><div class="text-muted" style="font-size:0.75rem;" *ngIf="u.floor">{{ 'floor {p0}' | translate: { p0: (u.floor) } }}</div></td>
                  <td data-label="Type">{{ unitKind(u.kind) }}</td><td data-label="Beds">{{ u.bedrooms ?? '—' }}</td>
                  <td class="text-end" data-label="Asking rent">{{ u.marketRent != null ? (u.marketRent | number: '1.0-0') : '—' }}</td>
                  <td data-label="Status"><span class="badge" [ngClass]="badge(u.occupancy)">{{ occ(u.occupancy) }}</span>
                    <div class="text-muted" style="font-size:0.75rem;" *ngIf="u.vacantSince && u.occupancy === 'VACANT'">{{ 'since {p0}' | translate: { p0: (u.vacantSince | date: 'd MMM y') } }}</div>
                    <div class="text-muted" style="font-size:0.75rem;" *ngIf="u.offMarketNote">{{ u.offMarketNote }}</div></td>
                  <td data-label="Tenant"><a *ngIf="u.tenantId" [routerLink]="['/admin/propertymgmt/tenants', u.tenantId]">{{ u.tenantName }}</a><span *ngIf="!u.tenantId" class="text-muted">—</span></td>
                  <td class="text-end" data-label="Rent">{{ u.rent != null ? (u.rent | number: '1.0-0') : '—' }}</td>
                  <td style="white-space:nowrap;" data-label="Lease ends"><span *ngIf="u.leaseEnd">{{ u.leaseEnd | date: 'd MMM y' }}</span><span *ngIf="!u.leaseEnd" class="text-muted">—</span>
                    <span class="badge badge-danger" *ngIf="u.holdingOver" style="margin-left:0.3rem;">{{ 'Past end' | translate }}</span></td>
                  <td style="white-space:nowrap; text-align:right;" data-label="">
                    <a class="btn btn-sm btn-outline-primary" *ngIf="p.canManage && u.occupancy === 'VACANT'" routerLink="/admin/propertymgmt/leases/new" [queryParams]="{ unitId: u.id }">{{ 'Let it' | translate }}</a>
                  </td>
                </tr>
              </tbody>
            </table>
            <ng-template #nounits><div class="text-muted">{{ 'No units yet. A single house is one unit; for a block use' | translate }} <strong>{{ 'Add many' | translate }}</strong>.</div></ng-template>
          </div>
        </div>
        <app-pm-ownership-panel [propertyId]="p.id" (changed)="load()"></app-pm-ownership-panel>
        <app-maintenance-panel [propertyId]="p.id" [canManage]="p.canManage"></app-maintenance-panel>
        <app-pm-documents-panel subjectType="PROPERTY" [subjectId]="p.id" [canManage]="p.canManage" defaultCategory="TITLE_DEED"></app-pm-documents-panel>
      </ng-container>
    </app-shell>
  `,
})
export class PmPropertyDetailComponent implements OnInit {
  p: PropertyDto | null = null;
  error: string | null = null;
  busy = false;
  mode: 'one' | 'many' | null = null;
  unitKinds = PM_UNIT_KINDS;
  nf = this.blank();
  bf = { ...this.blank(), prefix: '', from: 1 as number | null, to: 10 as number | null, pad: 2 as number | null };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  private blank(): UnitForm {
    return { unitNo: '', kind: 'APARTMENT', floor: '', bedrooms: null, bathrooms: null, sizeSqm: null, marketRent: null, defaultDeposit: null };
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.property(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (p) => (this.p = p), error: (e) => (this.error = e?.error?.message ?? T('Could not load the property')) });
  }

  kind(k: string): string { return pmLabel(PM_PROPERTY_KINDS, k); }
  unitKind(k: string): string { return pmLabel(PM_UNIT_KINDS, k); }
  occ(o: string): string { return (OCCUPANCY_LABEL as Record<string, string>)[o] ?? o; }
  badge(o: string): string { return occupancyBadge(o); }

  private payload(f: UnitForm): UnitPayload {
    const p: UnitPayload = { kind: f.kind, floor: txt(f.floor) };
    if (num(f.bedrooms) != null) p.bedrooms = num(f.bedrooms);
    if (num(f.bathrooms) != null) p.bathrooms = num(f.bathrooms);
    if (num(f.sizeSqm)) p.sizeSqm = num(f.sizeSqm);
    if (num(f.marketRent)) p.marketRent = num(f.marketRent);
    if (num(f.defaultDeposit)) p.defaultDeposit = num(f.defaultDeposit);
    return p;
  }

  addOne(): void {
    this.error = null;
    if (!txt(this.nf.unitNo)) { this.error = T('Give the unit a number.'); return; }
    this.busy = true;
    this.api.addUnit(this.p!.id, { ...this.payload(this.nf), unitNo: txt(this.nf.unitNo) }).subscribe({
      next: () => { this.busy = false; this.nf = this.blank(); this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not add the unit'); },
    });
  }

  addMany(): void {
    this.error = null;
    if (num(this.bf.from) == null || num(this.bf.to) == null) { this.error = T('Give the first and last number.'); return; }
    const body: BulkUnitsPayload = { ...this.payload(this.bf), prefix: txt(this.bf.prefix), from: num(this.bf.from)!, to: num(this.bf.to)!, pad: num(this.bf.pad) ?? 0 };
    this.busy = true;
    this.api.addUnitsBulk(this.p!.id, body).subscribe({
      next: () => { this.busy = false; this.mode = null; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not make the units'); },
    });
  }

  toggleActive(): void {
    this.busy = true;
    this.error = null;
    this.api.updateProperty(this.p!.id, { active: !this.p!.active }).subscribe({
      next: (p) => { this.busy = false; this.p = p; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not change the property'); },
    });
  }

  remove(): void {
    if (!confirm(T('Delete {p0} and its units?', { p0: this.p!.name }))) return;
    this.busy = true;
    this.error = null;
    this.api.deleteProperty(this.p!.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/properties']),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not delete the property'); },
    });
  }
}
