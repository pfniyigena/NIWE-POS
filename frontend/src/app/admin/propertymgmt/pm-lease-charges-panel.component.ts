import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { CHARGE_SOURCE_LABEL, ChargeType, Extra, LeaseCharge, LeaseChargesView, PmChargeApiService } from '../../core/api/pm-charge-api.service';

type Mode = 'charge' | 'reading' | 'extra' | 'edit' | null;
const pad = (n: number): string => String(n).padStart(2, '0');
const todayStr = (): string => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

/** Utilities and service charges of one lease: what it carries every month or by meter, what waits for the next rent invoice, and what that invoice will come to. */
@Component({
  selector: 'app-pm-lease-charges-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;" *ngIf="v && (v.canManage || v.charges.length || v.pending.length || v.billed.length)">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Utilities and service charges' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="v.canManage && !mode">
          <button class="btn btn-sm btn-primary" type="button" (click)="openCharge()">{{ 'Add a charge' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="v.canAddReadings" (click)="openExtra()">{{ 'One-off charge' | translate }}</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'These are added to the tenant\\'s rent invoice as their own lines. The money is the agency\\'s: the owner statement counts only the rent part of what the tenant pays.' | translate }}</div>
      <p class="text-muted" *ngIf="!v.types.length && v.canManage">{{ 'There are no charge types yet.' | translate }} <a routerLink="/admin/propertymgmt/charges">{{ 'Set them up' | translate }}</a> {{ '(Service charge, Water...) and then add them here.' | translate }}</p>

      <form *ngIf="mode === 'charge'" (ngSubmit)="saveCharge()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ 'Add a charge to this lease' | translate }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Charge *' | translate }}</label>
            <select class="form-select" name="ct" [(ngModel)]="f.typeId" (ngModelChange)="typeChanged()"><option [ngValue]="null">{{ 'Choose' | translate }}</option><option *ngFor="let t of v.types" [ngValue]="t.id">{{ t.name }}</option></select></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ chosen()?.kind === 'METERED' ? 'Rate per ' + chosen()?.unitLabel : 'Amount per month' }} *</label>
            <input class="form-control" type="number" min="0" step="0.01" name="ca" [(ngModel)]="f.amount" /></div>
          <div class="form-group mb-2" *ngIf="chosen()?.kind === 'METERED'" style="min-width:10rem;"><label class="form-label">{{ 'Meter shows now *' | translate }}</label>
            <input class="form-control" type="number" min="0" step="0.001" name="co" [(ngModel)]="f.opening" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'From' | translate }}</label><input class="form-control" type="date" name="cs" [(ngModel)]="f.start" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Until (optional)' | translate }}</label><input class="form-control" type="date" name="ce" [(ngModel)]="f.end" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="cn" [(ngModel)]="f.note" maxlength="300" /></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.8rem;">{{ 'A monthly charge is added to every rent invoice whose period starts between these dates (a quarterly lease is charged three months at a time). A metered charge waits for readings.' | translate }}</div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="close()">{{ 'Cancel' | translate }}</button></div>
      </form>

      <form *ngIf="mode === 'edit' && editing" (ngSubmit)="saveEdit()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ 'Change {p0}' | translate: { p0: (editing.typeName) } }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ editing.kind === 'METERED' ? 'Rate per ' + editing.unitLabel : 'Amount per month' }}</label>
            <input class="form-control" type="number" min="0" step="0.01" name="ea" [(ngModel)]="f.amount" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'From' | translate }}</label><input class="form-control" type="date" name="es" [(ngModel)]="f.start" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Until (empty: no end)' | translate }}</label><input class="form-control" type="date" name="ee" [(ngModel)]="f.end" /></div>
        </div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="close()">{{ 'Cancel' | translate }}</button></div>
      </form>

      <form *ngIf="mode === 'reading' && editing" (ngSubmit)="saveReading()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ 'Meter reading: {p0}' | translate: { p0: (editing.typeName) } }}</h5>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The last reading was {p0} {p1} on {p2}. The use is the difference, charged at {p3} per {p4}.' | translate: { p0: (editing.lastReading), p1: (editing.unitLabel), p2: (editing.lastReadingOn | date: 'd MMM y'), p3: (editing.amount | number: '1.0-2'), p4: (editing.unitLabel) } }}</div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Meter shows *' | translate }}</label><input class="form-control" type="number" min="0" step="0.001" name="rr" [(ngModel)]="f.reading" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Read on' | translate }}</label><input class="form-control" type="date" name="rd" [(ngModel)]="f.date" [max]="today" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="rn" [(ngModel)]="f.note" maxlength="300" /></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="usePreview() !== null">{{ 'This reading comes to {p0} {p1}, about {p2}.' | translate: { p0: (usePreview() | number: '1.0-3'), p1: (editing.unitLabel), p2: ((usePreview() ?? 0) * editing.amount | number: '1.0-0') } }}</div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save the reading' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="close()">{{ 'Cancel' | translate }}</button></div>
      </form>

      <form *ngIf="mode === 'extra'" (ngSubmit)="saveExtra()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ 'One-off charge' | translate }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What for *' | translate }}</label><input class="form-control" name="xd" [(ngModel)]="f.desc" maxlength="300" [placeholder]="'e.g. Replacement key' | translate" /></div>
          <div class="form-group mb-2" style="min-width:9rem;"><label class="form-label">{{ 'Amount *' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="xa" [(ngModel)]="f.amount" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Charge type' | translate }}</label>
            <select class="form-select" name="xt" [(ngModel)]="f.typeId"><option [ngValue]="null">{{ 'Other charges' | translate }}</option><option *ngFor="let t of v.types" [ngValue]="t.id">{{ t.name }}</option></select></div>
        </div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="close()">{{ 'Cancel' | translate }}</button></div>
      </form>

      <table class="stack table-sm" *ngIf="v.charges.length">
        <thead><tr><th>{{ 'Charge' | translate }}</th><th class="text-end">{{ 'Rate' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'Until' | translate }}</th><th>{{ 'Meter' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of v.charges" [style.opacity]="c.inForce ? 1 : 0.6">
            <td data-label="Charge"><strong>{{ c.typeName }}</strong><span class="badge badge-secondary" style="margin-left:0.3rem;" *ngIf="!c.inForce">{{ 'not in force' | translate }}</span><div class="text-muted" style="font-size:0.75rem;" *ngIf="c.note">{{ c.note }}</div></td>
            <td class="text-end" data-label="Rate">{{ c.amount | number: '1.0-2' }} {{ c.kind === 'METERED' ? 'per ' + c.unitLabel : 'a month' }}</td>
            <td data-label="From" style="white-space:nowrap;">{{ c.startDate | date: 'd MMM y' }}</td>
            <td data-label="Until" style="white-space:nowrap;">{{ c.endDate ? (c.endDate | date: 'd MMM y') : '—' }}</td>
            <td data-label="Meter">{{ c.kind === 'METERED' ? (c.lastReading + ' ' + c.unitLabel + ' · ' + (c.lastReadingOn | date: 'd MMM')) : '' }}</td>
            <td data-label="" style="text-align:right; white-space:nowrap;" *ngIf="v.canManage">
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="c.kind === 'METERED' && v.canAddReadings" (click)="openReading(c)" [disabled]="!!mode">{{ 'Add reading' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="openEdit(c)" [disabled]="!!mode">{{ 'Change' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" type="button" style="margin-left:0.2rem;" *ngIf="c.canDelete" (click)="act(api.removeCharge(c.id))" [disabled]="busy || !!mode">{{ 'Remove' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="mt-3" *ngIf="v.pending.length">
        <h5 class="mb-1">{{ 'Waiting for the next rent invoice' | translate }}</h5>
        <table class="stack table-sm">
          <tbody>
            <tr *ngFor="let e of v.pending">
              <td data-label="Date" style="white-space:nowrap;">{{ e.date | date: 'd MMM y' }}</td>
              <td data-label="What">{{ e.description }}<span class="badge badge-secondary" style="margin-left:0.3rem;">{{ sourceName(e.kind) }}</span></td>
              <td class="text-end" data-label="Amount">{{ e.amount | number: '1.0-0' }}</td>
              <td data-label="" style="text-align:right;" *ngIf="v.canManage"><button class="btn btn-sm btn-outline-danger" type="button" *ngIf="e.canCancel" (click)="act(api.cancelExtra(e.id))" [disabled]="busy">{{ 'Cancel' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-3" *ngIf="v.next && (v.next.lines.length || v.charges.length)">
        <h5 class="mb-1">{{ 'The next rent invoice ({p0} to {p1})' | translate: { p0: (v.next.periodStart | date: 'd MMM'), p1: (v.next.periodEnd | date: 'd MMM y') } }}</h5>
        <table class="table-sm"><tbody>
          <tr><td>{{ 'Rent' | translate }}</td><td class="text-end">{{ v.next.rent | number: '1.0-0' }}</td></tr>
          <tr *ngFor="let l of v.next.lines"><td>{{ l.description }}</td><td class="text-end">{{ l.amount | number: '1.0-0' }}</td></tr>
          <tr><td><strong>{{ 'Total' | translate }}</strong></td><td class="text-end"><strong>{{ v.next.total | number: '1.0-0' }}</strong></td></tr>
        </tbody></table>
      </div>

      <div class="mt-3" *ngIf="v.billed.length">
        <h5 class="mb-1">{{ 'Already invoiced' | translate }}</h5>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'Rent period' | translate }}</th><th>{{ 'Charge' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let b of v.billed | slice: 0 : 12">
              <td data-label="Rent period" style="white-space:nowrap;">{{ b.periodStart | date: 'd MMM y' }}</td>
              <td data-label="Charge">{{ b.description }}</td>
              <td data-label="Invoice">{{ b.invoiceNumber }}</td>
              <td class="text-end" data-label="Amount">{{ b.amount | number: '1.0-0' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class PmLeaseChargesPanelComponent implements OnChanges {
  @Input() leaseId = 0;
  /** Changes when the lease is signed or ends, so the panel reads again. */
  @Input() status = '';
  v: LeaseChargesView | null = null;
  mode: Mode = null;
  editing: LeaseCharge | null = null;
  error: string | null = null;
  busy = false;
  today = todayStr();
  f = { typeId: null as number | null, amount: null as number | null, opening: null as number | null, start: '', end: '', note: '', reading: null as number | null, date: '', desc: '' };

  constructor(public api: PmChargeApiService) {}

  ngOnChanges(): void { this.load(); }

  load(): void {
    if (!this.leaseId) return;
    this.api.forLease(this.leaseId).subscribe({ next: (v) => (this.v = v), error: () => (this.v = null) });
  }

  sourceName(s: string): string { return (CHARGE_SOURCE_LABEL as Record<string, string>)[s] ?? s; }
  chosen(): ChargeType | null { return this.v?.types.find((t) => t.id === this.f.typeId) ?? null; }

  private reset(): void { this.f = { typeId: null, amount: null, opening: null, start: '', end: '', note: '', reading: null, date: '', desc: '' }; this.error = null; }
  close(): void { this.mode = null; this.editing = null; this.reset(); }
  openCharge(): void { this.reset(); this.mode = 'charge'; }
  openExtra(): void { this.reset(); this.mode = 'extra'; }
  openReading(c: LeaseCharge): void { this.reset(); this.editing = c; this.f.date = todayStr(); this.mode = 'reading'; }
  openEdit(c: LeaseCharge): void { this.reset(); this.editing = c; this.f.amount = c.amount; this.f.start = c.startDate; this.f.end = c.endDate ?? ''; this.mode = 'edit'; }

  typeChanged(): void { const t = this.chosen(); if (t && t.defaultAmount != null) this.f.amount = t.defaultAmount; }

  usePreview(): number | null {
    const r = num(this.f.reading);
    if (!this.editing || r === null || this.editing.lastReading === null) return null;
    const used = r - this.editing.lastReading;
    return used < 0 ? null : used;
  }

  act(call: Observable<LeaseChargesView>): void {
    this.busy = true;
    this.error = null;
    call.subscribe({
      next: (v) => { this.busy = false; this.v = v; this.close(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; },
    });
  }

  saveCharge(): void {
    const t = this.chosen();
    if (!t) { this.error = 'Choose the charge.'; return; }
    const amount = num(this.f.amount);
    if (!amount || amount <= 0) { this.error = t.kind === 'METERED' ? 'Give the rate per ' + t.unitLabel + '.' : 'Give the amount per month.'; return; }
    if (t.kind === 'METERED' && num(this.f.opening) === null) { this.error = 'Give the meter reading as it is now.'; return; }
    this.act(this.api.addCharge(this.leaseId, { chargeTypeId: t.id, amount, startDate: this.f.start || null, endDate: this.f.end || null, openingReading: t.kind === 'METERED' ? num(this.f.opening) : null, note: this.f.note.trim() }));
  }

  saveEdit(): void {
    if (!this.editing) return;
    const amount = num(this.f.amount);
    if (!amount || amount <= 0) { this.error = 'The amount must be more than zero.'; return; }
    this.act(this.api.updateCharge(this.editing.id, { amount, startDate: this.f.start || null, endDate: this.f.end || null, clearEndDate: !this.f.end }));
  }

  saveReading(): void {
    if (!this.editing) return;
    const reading = num(this.f.reading);
    if (reading === null) { this.error = 'Give the meter reading.'; return; }
    this.act(this.api.addReading(this.editing.id, { reading, date: this.f.date || null, note: this.f.note.trim() }));
  }

  saveExtra(): void {
    const amount = num(this.f.amount);
    if (!this.f.desc.trim()) { this.error = 'Say what the charge is for.'; return; }
    if (!amount || amount <= 0) { this.error = 'The amount must be more than zero.'; return; }
    this.act(this.api.addExtra(this.leaseId, { description: this.f.desc.trim(), amount, chargeTypeId: this.f.typeId }));
  }
}
