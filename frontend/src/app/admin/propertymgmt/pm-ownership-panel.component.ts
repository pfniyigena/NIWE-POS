import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OwnerSplit, OwnershipView, PmOwnershipApiService } from '../../core/api/pm-ownership-api.service';
import { OwnerRow, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';

import { T } from '../../core/i18n/t';
const pad = (n: number): string => String(n).padStart(2, '0');
const todayStr = (): string => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

interface Row { ownerId: number | null; percent: number | null; }

/** Property Management > a property > Owners: who owns it and the percentage of each, from a date. Each owner gets their own statement for their share. */
@Component({
  selector: 'app-pm-ownership-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card pm-ownership" style="padding:1rem; margin-top:0.75rem;" *ngIf="v">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Owners' | translate }}</h4>
        <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="v.canManage && !editing" (click)="open()">{{ 'Change owners' | translate }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning" *ngIf="v.rebuild.length">{{ 'Draft statements {p0} have lines for this property that the change affects. Open each one and press Rebuild so it uses the new shares.' | translate: { p0: (v.rebuild.join(', ')) } }}</div>

      <table class="stack table-sm" *ngIf="current() as cur">
        <thead><tr><th>{{ 'Owner' | translate }}</th><th class="text-end">{{ 'Share' | translate }}</th><th></th></tr></thead>
        <tbody><tr *ngFor="let s of cur.shares">
          <td data-label="Owner"><a [routerLink]="['/admin/propertymgmt/owners', s.ownerId]">{{ s.ownerName }}</a></td>
          <td data-label="Share" class="text-end">{{ s.percent | number: '1.0-2' }}%</td>
          <td data-label=""><span class="badge badge-secondary" *ngIf="s.lead && cur.shares.length > 1" [title]="'The owner the agency deals with. Cents left over when a payment is shared go to this owner.' | translate">{{ 'Main owner' | translate }}</span></td>
        </tr></tbody>
      </table>
      <div class="text-muted" style="font-size:0.85rem;" *ngIf="current() as cur">
        <ng-container *ngIf="cur.shares.length > 1">{{ 'Rent, the management fee and repairs the owners pay for are shared by these percentages. Each owner gets their own statement and their own fee invoice.' | translate }}</ng-container>
        <ng-container *ngIf="cur.shares.length === 1">{{ 'One owner has all of this property. Use Change owners to add a co-owner.' | translate }}</ng-container>
      </div>

      <form *ngIf="editing" (ngSubmit)="save()" class="mt-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ 'Change the owners' | translate }}</h5>
        <div class="form-group mb-2" style="max-width:14rem;"><label class="form-label">{{ 'From' | translate }}</label>
          <input class="form-control" type="date" name="ownFrom" [(ngModel)]="from" [max]="today" />
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Rent paid on or after this day is shared by the new percentages. Empty means from the beginning, which is only possible while no agreed statement covers the property. To record a change (a part sold, an heir) give the day it happened.' | translate }}</div></div>
        <div class="flex gap-2 align-center mb-2" style="flex-wrap:wrap;" *ngFor="let r of rows; let i = index">
          <select class="form-select own-owner" style="min-width:14rem; flex:1;" [name]="'ownerSel' + i" [(ngModel)]="r.ownerId">
            <option [ngValue]="null">{{ '— choose the owner —' | translate }}</option>
            <option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}{{ o.active ? '' : ' (inactive)' }}</option>
          </select>
          <input class="form-control own-pct" style="width:7rem;" type="number" min="0" max="100" step="0.01" [name]="'ownerPct' + i" [(ngModel)]="r.percent" placeholder="%" />
          <label style="display:flex; gap:0.25rem; align-items:center; white-space:nowrap;"><input class="own-lead" type="radio" name="ownLead" [value]="i" [(ngModel)]="leadIndex" /> {{ 'Main owner' | translate }}</label>
          <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="rows.length > 1" (click)="removeRow(i)">{{ 'Remove' | translate }}</button>
        </div>
        <div class="flex gap-2 align-center mb-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="addRow()">{{ '+ Add an owner' | translate }}</button>
          <span class="own-total" [style.color]="total() === 100 ? 'inherit' : '#c0392b'">{{ 'Total {p0}%' | translate: { p0: (total() | number: '1.0-2') } }} <span *ngIf="total() !== 100">{{ '(must be 100%)' | translate }}</span></span>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="splitEvenly()">{{ 'Share equally' | translate }}</button>
        </div>
        <div class="text-muted mb-2" style="font-size:0.8rem;">{{ 'The main owner is the one the agency deals with; cents left over when a payment is shared go to them. Not in the list?' | translate }} <a routerLink="/admin/propertymgmt/owners">{{ 'Add the owner first' | translate }}</a>.</div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy || total() !== 100">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="editing = false">{{ 'Cancel' | translate }}</button></div>
      </form>

      <div class="mt-3" *ngIf="v.splits.length > 1">
        <h5 class="mb-1">{{ 'History' | translate }}</h5>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'From' | translate }}</th><th>{{ 'Owners' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let sp of v.splits">
            <td data-label="From">{{ sp.validFrom ? (sp.validFrom | date: 'd MMM y') : 'The beginning' }}<span class="badge badge-secondary" *ngIf="sp.current" style="margin-left:0.3rem;">{{ 'Now' | translate }}</span></td>
            <td data-label="Owners">{{ label(sp) }}</td>
            <td data-label="" class="text-end">
              <span class="text-muted" *ngIf="sp.locked" [title]="sp.lockedReason">{{ 'Locked' | translate }}</span>
              <button class="btn btn-sm btn-outline-danger own-remove" type="button" *ngIf="v.canManage && sp.canRemove" (click)="remove(sp)" [disabled]="busy">{{ 'Remove' | translate }}</button>
            </td>
          </tr></tbody>
        </table>
        <div class="text-muted" style="font-size:0.8rem;" *ngIf="v.lockedThrough">{{ 'A split that an agreed statement already covers (up to {p0}) is locked.' | translate: { p0: (v.lockedThrough | date: 'd MMM y') } }}</div>
      </div>
    </div>
  `,
})
export class PmOwnershipPanelComponent implements OnChanges {
  @Input() propertyId!: number;
  @Output() changed = new EventEmitter<void>();
  v: OwnershipView | null = null;
  owners: OwnerRow[] = [];
  editing = false;
  busy = false;
  error: string | null = null;
  today = todayStr();
  from: string | null = null;
  rows: Row[] = [];
  leadIndex = 0;

  constructor(private api: PmOwnershipApiService, private pm: PropertyMgmtApiService) {}

  ngOnChanges(): void {
    this.load();
  }

  load(): void {
    this.api.get(this.propertyId).subscribe({ next: (v) => (this.v = v), error: (e) => (this.error = e?.error?.message ?? T('Could not load the owners')) });
  }

  current(): OwnerSplit | null {
    return this.v ? this.v.splits.find((s) => s.current) ?? null : null;
  }

  label(sp: OwnerSplit): string {
    return sp.shares.map((s) => `${s.ownerName} ${s.percent}%`).join(' · ');
  }

  open(): void {
    this.error = null;
    this.editing = true;
    // nothing agreed yet and only one split: setting the owners up or correcting them, so from the beginning; otherwise a change from today
    this.from = this.v && this.v.splits.length === 1 && !this.v.lockedThrough ? null : todayStr();
    const cur = this.current();
    this.rows = (cur?.shares ?? []).map((s) => ({ ownerId: s.ownerId, percent: s.percent }));
    if (!this.rows.length) this.rows = [{ ownerId: null, percent: 100 }];
    this.leadIndex = Math.max(0, (cur?.shares ?? []).findIndex((s) => s.lead));
    if (!this.owners.length) this.pm.owners({}).subscribe({ next: (l) => (this.owners = l), error: () => {} });
  }

  addRow(): void {
    this.rows.push({ ownerId: null, percent: null });
  }

  removeRow(i: number): void {
    this.rows.splice(i, 1);
    if (this.leadIndex >= this.rows.length) this.leadIndex = 0;
    else if (this.leadIndex > i) this.leadIndex--;
  }

  total(): number {
    return Math.round(this.rows.reduce((t, r) => t + (num(r.percent) ?? 0), 0) * 100) / 100;
  }

  splitEvenly(): void {
    const n = this.rows.length;
    if (!n) return;
    const each = Math.floor((10000 / n)) / 100;
    this.rows.forEach((r) => (r.percent = each));
    const rest = Math.round((100 - each * n) * 100) / 100;
    this.rows[this.leadIndex].percent = Math.round((each + rest) * 100) / 100;
  }

  save(): void {
    this.error = null;
    if (this.rows.some((r) => !r.ownerId)) { this.error = T('Choose the owner on every line.'); return; }
    if (this.rows.some((r) => !(num(r.percent) && (num(r.percent) as number) > 0))) { this.error = T('Give every owner a percentage above 0.'); return; }
    const ids = this.rows.map((r) => r.ownerId);
    if (new Set(ids).size !== ids.length) { this.error = T('An owner is on the list twice.'); return; }
    this.busy = true;
    this.api.set(this.propertyId, {
      validFrom: this.from || null,
      shares: this.rows.map((r) => ({ ownerId: r.ownerId as number, percent: Number(r.percent) })),
      leadOwnerId: this.rows[this.leadIndex]?.ownerId ?? null,
    }).subscribe({
      next: (v) => { this.busy = false; this.v = v; this.editing = false; this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save the owners'); },
    });
  }

  remove(sp: OwnerSplit): void {
    if (!confirm(T('Remove the owners split that starts {p0}? Rent paid from that day is then shared by the split before it.', { p0: (sp.validFrom ? T('on {p0}', { p0: sp.validFrom }) : T('at the beginning')) }))) return;
    this.error = null;
    this.busy = true;
    this.api.remove(this.propertyId, sp.key).subscribe({
      next: (v) => { this.busy = false; this.v = v; this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not remove it'); },
    });
  }
}
