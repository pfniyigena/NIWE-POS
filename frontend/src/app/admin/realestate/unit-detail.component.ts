import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { AuthService } from '../../core/auth.service';
import {
  LeadRow, Option, RealEstateApiService, UNIT_KINDS, UNIT_SALE_STAGE_LABEL, UNIT_STATUS_LABEL, UnitDto, UnitKind, UnitPayload, UnitSalePayload, unitKindLabel,
} from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string | null => { const t = (v ?? '').trim(); return t ? t : null; };

/** Real Estate > Projects > one unit: its details and price history, its sales, and reserving it for a buyer. */
@Component({
  selector: 'app-unit-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ReDocumentsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!u && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="u">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a [routerLink]="['/admin/realestate/projects', u.projectId]" style="font-size:0.85rem;">← {{ u.projectName }}</a>
            <h3>{{ 'Unit {p0}' | translate: { p0: (u.unitNo) } }}
              <span class="badge" [class.badge-success]="u.status === 'AVAILABLE'" [class.badge-warning]="u.status === 'RESERVED'" [class.badge-danger]="u.status === 'SOLD'" [class.badge-secondary]="u.status === 'WITHHELD'" style="font-size:0.8rem; vertical-align:middle;">{{ statusName(u.status) }}</span></h3>
          </div>
          <div class="flex gap-2" *ngIf="canManage" style="flex-wrap:wrap;">
            <button class="btn btn-outline-secondary" type="button" (click)="editing = !editing; fill()">{{ 'Edit' | translate }}</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="!u.sales.length" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>
        <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
        <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>

        <div class="card mb-3" style="padding:1rem; max-width:52rem;" *ngIf="editing">
          <h4 class="mb-2">{{ 'Change this unit' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div><label class="form-label">{{ 'Unit no.' | translate }}</label><input class="form-control" style="width:8rem;" name="e1" [(ngModel)]="f.unitNo" maxlength="40" /></div>
            <div><label class="form-label">{{ 'Block / phase' | translate }}</label><input class="form-control" style="width:10rem;" name="e2" [(ngModel)]="f.block" maxlength="80" /></div>
            <div><label class="form-label">{{ 'Type' | translate }}</label><select class="form-select" name="e3" [(ngModel)]="f.kind"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select></div>
            <div><label class="form-label">{{ 'Floor' | translate }}</label><input class="form-control" style="width:5rem;" type="number" name="e4" [(ngModel)]="f.floor" /></div>
            <div><label class="form-label">{{ 'Beds' | translate }}</label><input class="form-control" style="width:5rem;" type="number" min="0" name="e5" [(ngModel)]="f.bedrooms" /></div>
            <div><label class="form-label">{{ 'Baths' | translate }}</label><input class="form-control" style="width:5rem;" type="number" min="0" name="e6" [(ngModel)]="f.bathrooms" /></div>
            <div><label class="form-label">{{ 'Size (m²)' | translate }}</label><input class="form-control" style="width:7rem;" type="number" min="0" step="0.01" name="e7" [(ngModel)]="f.sizeSqm" /></div>
          </div>
          <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div><label class="form-label">{{ 'List price' | translate }}</label><input class="form-control" style="width:12rem;" type="number" min="0" name="e8" [(ngModel)]="f.listPrice" [disabled]="u.priceLocked" /></div>
            <div *ngIf="!u.priceLocked"><label class="form-label">{{ 'Why the price changes' | translate }}</label><input class="form-control" style="width:16rem;" name="e9" [(ngModel)]="f.reason" maxlength="200" /></div>
            <div *ngIf="!u.priceLocked"><label class="form-label">{{ 'Status' | translate }}</label>
              <select class="form-select" name="e10" [(ngModel)]="f.status"><option value="AVAILABLE">{{ 'Available' | translate }}</option><option value="WITHHELD">{{ 'Withheld (not for sale)' | translate }}</option></select></div>
          </div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="u.priceLocked">{{ 'The price of a reserved or sold unit is set by its sale.' | translate }}</div>
          <div class="form-group mt-2"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="2" name="e11" [(ngModel)]="f.notes" maxlength="1000"></textarea></div>
          <div class="mt-2"><button class="btn btn-primary" type="button" (click)="save()" [disabled]="busy">{{ 'Save changes' | translate }}</button></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'The unit' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Project' | translate }}</td><td><a [routerLink]="['/admin/realestate/projects', u.projectId]">{{ u.projectName }}</a> <span class="text-muted">{{ u.projectCode }}</span></td></tr>
              <tr><td class="text-muted">{{ 'Block / phase' | translate }}</td><td>{{ u.block || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Type' | translate }}</td><td>{{ kindName(u.kind) }}</td></tr>
              <tr><td class="text-muted">{{ 'Floor' | translate }}</td><td>{{ u.floor ?? '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Bedrooms / baths' | translate }}</td><td>{{ u.bedrooms ?? '—' }} / {{ u.bathrooms ?? '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Size' | translate }}</td><td>{{ u.sizeSqm != null ? (u.sizeSqm | number: '1.0-2') + ' m²' : '—' }}</td></tr>
              <tr><td class="text-muted"><strong>{{ 'List price' | translate }}</strong></td><td><strong>{{ u.listPrice | number: '1.0-0' }}</strong><span class="text-muted" *ngIf="u.pricePerSqm != null"> {{ '· {p0} per m²' | translate: { p0: (u.pricePerSqm | number: '1.0-0') } }}</span></td></tr>
              <tr *ngIf="u.notes"><td class="text-muted">{{ 'Notes' | translate }}</td><td style="white-space:pre-wrap;">{{ u.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;" *ngIf="canManage && u.status === 'AVAILABLE'">
            <h4 class="mb-2">{{ 'Reserve this unit' | translate }}</h4>
            <div class="form-group mb-2">
              <label class="form-label">{{ 'From a lead' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
              <select class="form-select" name="rl" [(ngModel)]="r.leadId" (ngModelChange)="leadPicked()">
                <option [ngValue]="null">{{ '— a new buyer —' | translate }}</option>
                <option *ngFor="let l of leads" [ngValue]="l.id">{{ l.name }}{{ l.phone ? ' · ' + l.phone : '' }}</option>
              </select>
            </div>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group mb-2" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Buyer\\'s name *' | translate }}</label><input class="form-control" name="rn" [(ngModel)]="r.buyerName" maxlength="200" /></div>
              <div class="form-group mb-2" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="rp" [(ngModel)]="r.buyerPhone" maxlength="40" /></div>
              <div class="form-group mb-2" style="flex:1; min-width:11rem;"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="re" [(ngModel)]="r.buyerEmail" /></div>
            </div>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group mb-2"><label class="form-label">{{ 'Agreed price' | translate }}</label><input class="form-control" style="width:12rem;" type="number" min="0" name="ra" [(ngModel)]="r.agreedPrice" /></div>
              <div class="form-group mb-2"><label class="form-label">{{ 'Held until' | translate }}</label><input class="form-control" type="date" name="rx" [(ngModel)]="r.reservationExpiry" /></div>
              <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Agent' | translate }}</label>
                <select class="form-select" name="rg" [(ngModel)]="r.agentUserId"><option [ngValue]="null">{{ 'Me' | translate }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select></div>
            </div>
            <div class="form-group mb-2"><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control" name="rt" [(ngModel)]="r.notes" maxlength="2000" /></div>
            <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="r.agreedPrice != null && r.agreedPrice < u.listPrice">{{ 'Discount of {p0} off the list price.' | translate: { p0: (u.listPrice - r.agreedPrice | number: '1.0-0') } }}</div>
            <button class="btn btn-primary" type="button" (click)="reserve()" [disabled]="busy">{{ 'Reserve for this buyer' | translate }}</button>
            <div class="text-muted mt-1" style="font-size:0.85rem;">{{ 'The project must be' | translate }} <em>{{ 'Selling' | translate }}</em>{{ '. The agreement, invoice and payment plan come next, on the sale.' | translate }}</div>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Sales of this unit' | translate }}</h4>
          <table class="stack table-sm" *ngIf="u.sales.length">
            <thead><tr><th>{{ 'Sale' | translate }}</th><th>{{ 'Buyer' | translate }}</th><th>{{ 'Stage' | translate }}</th><th class="text-end">{{ 'Agreed price' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Still owed' | translate }}</th><th>{{ 'Reserved' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let s of u.sales">
              <td data-label="Sale"><a [routerLink]="['/admin/realestate/unit-sales', s.id]"><strong>{{ s.code }}</strong></a></td>
              <td data-label="Buyer">{{ s.buyerName }}</td>
              <td data-label="Stage"><span class="badge" [class.badge-success]="s.stage === 'COMPLETED'" [class.badge-danger]="s.stage === 'CANCELLED'" [class.badge-warning]="s.stage === 'RESERVED' || s.stage === 'SOLD'">{{ stageName(s.stage) }}</span></td>
              <td class="text-end" data-label="Agreed price">{{ s.agreedPrice | number: '1.0-0' }}</td><td class="text-end" data-label="Paid">{{ s.paid | number: '1.0-0' }}</td><td class="text-end" data-label="Still owed">{{ s.outstanding | number: '1.0-0' }}</td>
              <td data-label="Reserved">{{ s.reservedOn ? (s.reservedOn | date: 'd MMM y') : '—' }}</td>
            </tr></tbody>
          </table>
          <p class="text-muted" *ngIf="!u.sales.length">{{ 'Not sold or reserved yet.' | translate }}</p>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Price history' | translate }}</h4>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'When' | translate }}</th><th class="text-end">{{ 'From' | translate }}</th><th class="text-end">{{ 'To' | translate }}</th><th>{{ 'Why' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let c of u.priceHistory">
              <td data-label="When">{{ c.at | date: 'd MMM y, HH:mm' }}</td><td class="text-end" data-label="From">{{ c.oldPrice != null ? (c.oldPrice | number: '1.0-0') : '—' }}</td><td class="text-end" data-label="To">{{ c.newPrice | number: '1.0-0' }}</td>
              <td data-label="Why">{{ c.reason || '—' }}</td><td data-label="By">{{ c.changedByName || '—' }}</td>
            </tr></tbody>
          </table>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="UNIT" [subjectId]="u.id" [canManage]="canManage" defaultCategory="FLOOR_PLAN"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class UnitDetailComponent implements OnInit {
  u: UnitDto | null = null;
  error: string | null = null;
  actionError: string | null = null;
  notice: string | null = null;
  busy = false;
  editing = false;
  canManage: boolean;
  kinds = UNIT_KINDS;
  leads: LeadRow[] = [];
  agents: Option[] = [];
  f = { unitNo: '', block: '', kind: 'PLOT' as UnitKind, floor: null as number | null, bedrooms: null as number | null, bathrooms: null as number | null, sizeSqm: null as number | null, listPrice: null as number | null, reason: '', status: 'AVAILABLE' as 'AVAILABLE' | 'WITHHELD', notes: '' };
  r = { leadId: null as number | null, buyerName: '', buyerPhone: '', buyerEmail: '', agreedPrice: null as number | null, reservationExpiry: '', agentUserId: null as number | null, notes: '' };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    this.load();
    if (this.canManage) {
      this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
      this.api.leads({ openOnly: true }).subscribe({ next: (l) => (this.leads = l.filter((x) => x.interest !== 'RENT')), error: () => {} });
    }
  }

  private id(): number { return Number(this.route.snapshot.paramMap.get('id')); }

  load(): void {
    this.api.unit(this.id()).subscribe({
      next: (u) => { this.u = u; this.r.agreedPrice = this.r.agreedPrice ?? u.listPrice; },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the unit')),
    });
  }

  statusName(s: string): string { return (UNIT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  stageName(s: string): string { return (UNIT_SALE_STAGE_LABEL as Record<string, string>)[s] ?? s; }
  kindName(k: string): string { return unitKindLabel(k); }

  fill(): void {
    const u = this.u;
    if (!u) return;
    this.f = { unitNo: u.unitNo, block: u.block ?? '', kind: u.kind, floor: u.floor, bedrooms: u.bedrooms, bathrooms: u.bathrooms, sizeSqm: u.sizeSqm, listPrice: u.listPrice, reason: '',
      status: u.status === 'WITHHELD' ? 'WITHHELD' : 'AVAILABLE', notes: u.notes ?? '' };
  }

  save(): void {
    const u = this.u!;
    const f = this.f;
    if (!f.unitNo.trim()) { this.actionError = 'Give the unit a number.'; return; }
    this.actionError = null;
    this.notice = null;
    const body: UnitPayload = { unitNo: f.unitNo.trim(), block: f.block, kind: f.kind, floor: num(f.floor), bedrooms: num(f.bedrooms), bathrooms: num(f.bathrooms), sizeSqm: num(f.sizeSqm), notes: f.notes };
    if (!u.priceLocked) {
      body.listPrice = num(f.listPrice);
      body.reason = f.reason;
      body.status = f.status;
    }
    this.busy = true;
    this.api.updateUnit(u.id, body).subscribe({
      next: (x) => { this.busy = false; this.u = x; this.editing = false; this.notice = T('Saved.'); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'Could not save the unit'; },
    });
  }

  remove(): void {
    if (!confirm(T('Delete unit {p0}?', { p0: this.u!.unitNo }))) return;
    const pid = this.u!.projectId;
    this.api.deleteUnit(this.u!.id).subscribe({ next: () => this.router.navigate(['/admin/realestate/projects', pid]), error: (e) => (this.actionError = e?.error?.message ?? 'Could not delete the unit') });
  }

  leadPicked(): void {
    const l = this.leads.find((x) => x.id === this.r.leadId);
    if (!l) return;
    this.r.buyerName = l.name;
    this.r.buyerPhone = l.phone ?? '';
    this.r.buyerEmail = l.email ?? '';
  }

  reserve(): void {
    const r = this.r;
    if (!txt(r.buyerName) && !r.leadId) { this.actionError = 'Give the buyer\'s name, or pick a lead.'; return; }
    this.actionError = null;
    const p: UnitSalePayload = {
      unitId: this.u!.id, leadId: r.leadId, buyerName: txt(r.buyerName), buyerPhone: txt(r.buyerPhone), buyerEmail: txt(r.buyerEmail), agentUserId: r.agentUserId,
      agreedPrice: num(r.agreedPrice), reservationExpiry: r.reservationExpiry || null, notes: txt(r.notes),
    };
    this.busy = true;
    this.api.reserveUnit(p).subscribe({
      next: (s) => { this.busy = false; this.router.navigate(['/admin/realestate/unit-sales', s.id]); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'Could not reserve the unit'; },
    });
  }
}
