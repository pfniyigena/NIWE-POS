import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { CHARGE_KIND_LABEL, ChargeKind, ChargeType, PmChargeApiService } from '../../core/api/pm-charge-api.service';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

/** Property Management > Charges: the kinds of charge that can be put on a lease and billed on its rent invoice (Service charge, Garbage, Water, Electricity...). */
@Component({
  selector: 'app-pm-charge-types',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Utilities and service charges' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/reports/pm-charges">{{ 'Report' | translate }}</a>
          <button class="btn btn-primary" type="button" *ngIf="!form" (click)="openNew()">{{ 'Add a charge type' | translate }}</button>
        </div>
      </div>
      <div class="text-muted mb-3" style="font-size:0.9rem;">{{ 'Set up the kinds of charge here, then put them on each lease (on the lease page). They are added to the tenant\\'s rent invoice as their own lines. A fixed charge is the same every month; a metered one (water, electricity) is worked out from meter readings.' | translate }}</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form *ngIf="form" (ngSubmit)="save()" class="card mb-3" style="padding:1rem; max-width:48rem;">
        <h4 class="mb-2">{{ form.id ? 'Change ' + form.original : 'New charge type' }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Name *' | translate }}</label><input class="form-control" name="n" [(ngModel)]="form.name" maxlength="100" [placeholder]="'e.g. Service charge' | translate" /></div>
          <div class="form-group mb-2" style="min-width:13rem;"><label class="form-label">{{ 'Kind *' | translate }}</label>
            <select class="form-select" name="k" [(ngModel)]="form.kind" [disabled]="form.kindLocked"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select></div>
          <div class="form-group mb-2" *ngIf="form.kind === 'METERED'" style="min-width:8rem;"><label class="form-label">{{ 'Unit *' | translate }}</label><input class="form-control" name="u" [(ngModel)]="form.unit" maxlength="20" [placeholder]="'m3, kWh' | translate" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ form.kind === 'METERED' ? ('Usual rate per unit' | translate) : ('Usual amount per month' | translate) }}</label><input class="form-control" type="number" min="0" step="0.01" name="a" [(ngModel)]="form.amount" /></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.8rem;" *ngIf="form.kindLocked">{{ 'This type is on leases already, so its kind cannot change.' | translate }}</div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="form = null">{{ 'Cancel' | translate }}</button></div>
      </form>
      <table class="stack table-sm" *ngIf="list.length">
        <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'Kind' | translate }}</th><th class="text-end">{{ 'Usual amount' | translate }}</th><th class="text-end">{{ 'On leases' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let t of list" [style.opacity]="t.active ? 1 : 0.6">
            <td data-label="Name"><strong>{{ t.name }}</strong></td>
            <td data-label="Kind">{{ t.kind === 'METERED' ? 'Metered, per ' + t.unitLabel : 'Fixed every month' }}</td>
            <td class="text-end" data-label="Usual amount">{{ t.defaultAmount != null ? (t.defaultAmount | number: '1.0-2') : '—' }}</td>
            <td class="text-end" data-label="On leases">{{ t.leaseCharges }}</td>
            <td data-label="Status"><span class="badge" [ngClass]="t.active ? 'badge-success' : 'badge-secondary'">{{ t.active ? ('In use' | translate) : ('Switched off' | translate) }}</span></td>
            <td data-label="" style="text-align:right; white-space:nowrap;">
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="openEdit(t)">{{ 'Change' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="toggle(t)" [disabled]="busy">{{ t.active ? ('Switch off' | translate) : ('Switch on' | translate) }}</button>
              <button class="btn btn-sm btn-outline-danger" type="button" style="margin-left:0.2rem;" *ngIf="!t.inUse" (click)="remove(t)" [disabled]="busy">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No charge types yet. Add the ones you bill, for example Service charge, Garbage, Water and Electricity.' | translate }}</p>
    </app-shell>
  `,
})
export class PmChargeTypesComponent implements OnInit {
  list: ChargeType[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;
  kinds = (Object.keys(CHARGE_KIND_LABEL) as ChargeKind[]).map((k) => ({ value: k, label: CHARGE_KIND_LABEL[k] }));
  form: { id: number | null; original: string; name: string; kind: ChargeKind; unit: string; amount: number | null; kindLocked: boolean } | null = null;

  constructor(private api: PmChargeApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.types().subscribe({ next: (l) => { this.list = l; this.loaded = true; }, error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the charge types'); } });
  }

  openNew(): void { this.error = null; this.form = { id: null, original: '', name: '', kind: 'FIXED', unit: '', amount: null, kindLocked: false }; }
  openEdit(t: ChargeType): void { this.error = null; this.form = { id: t.id, original: t.name, name: t.name, kind: t.kind, unit: t.unitLabel ?? '', amount: t.defaultAmount, kindLocked: t.inUse }; }

  save(): void {
    const f = this.form;
    if (!f) return;
    if (!f.name.trim()) { this.error = T('Give the charge a name.'); return; }
    if (f.kind === 'METERED' && !f.unit.trim()) { this.error = T('Say what the meter counts (for example m3 or kWh).'); return; }
    const amount = num(f.amount);
    this.busy = true;
    this.error = null;
    const body = { name: f.name.trim(), kind: f.kind, unitLabel: f.kind === 'METERED' ? f.unit.trim() : undefined, defaultAmount: amount };
    const call = f.id === null ? this.api.createType(body) : this.api.updateType(f.id, { ...body, clearDefaultAmount: amount === null });
    call.subscribe({ next: () => { this.busy = false; this.form = null; this.load(); }, error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save'); } });
  }

  toggle(t: ChargeType): void {
    this.busy = true;
    this.error = null;
    this.api.updateType(t.id, { active: !t.active }).subscribe({ next: () => { this.busy = false; this.load(); }, error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); } });
  }

  remove(t: ChargeType): void {
    if (!confirm(T('Delete the charge type {p0}?', { p0: t.name }))) return;
    this.busy = true;
    this.error = null;
    this.api.deleteType(t.id).subscribe({ next: () => { this.busy = false; this.load(); }, error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); } });
  }
}
