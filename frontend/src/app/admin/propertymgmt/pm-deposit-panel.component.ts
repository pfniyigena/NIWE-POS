import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DEPOSIT_KIND_LABEL, DEPOSIT_METHODS, DEPOSIT_REASONS, DEPOSIT_STATE_LABEL, DepositReason, DepositTxn, DepositView, PmDepositApiService, depositBadge,
} from '../../core/api/pm-deposit-api.service';

import { T } from '../../core/i18n/t';
type Mode = 'receive' | 'keep' | 'refund' | 'move' | null;

/** The security deposit of one lease: what was agreed, what is held, every movement, and the forms to take it in, keep part of it, refund it or move it to a renewal. */
@Component({
  selector: 'app-pm-deposit-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;" *ngIf="v && v.state !== 'NONE'">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Security deposit' | translate }} <span class="badge" [ngClass]="badge(v.state)" style="margin-left:0.4rem;">{{ stateName(v.state) }}</span></h4>
        <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="v.canManage && !mode">
          <button class="btn btn-sm btn-primary" type="button" *ngIf="v.canReceive" (click)="open('receive')">{{ 'Receive deposit' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="v.canSettle" (click)="open('keep')">{{ 'Keep part' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="v.canSettle" (click)="open('refund')">{{ 'Refund' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="v.canTransfer" (click)="open('move')">{{ 'Move to a renewal' | translate }}</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="deposit-figures">
        <div><div class="text-muted kpi-label">{{ 'Agreed' | translate }}</div><strong>{{ v.agreed | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted kpi-label">{{ 'Paid in' | translate }}</div><strong>{{ v.paidIn | number: '1.0-0' }}</strong></div>
        <div *ngIf="v.deducted"><div class="text-muted kpi-label">{{ 'Kept' | translate }}</div><strong>{{ v.deducted | number: '1.0-0' }}</strong></div>
        <div *ngIf="v.refunded"><div class="text-muted kpi-label">{{ 'Refunded' | translate }}</div><strong>{{ v.refunded | number: '1.0-0' }}</strong></div>
        <div *ngIf="v.transferredOut"><div class="text-muted kpi-label">{{ 'Moved on' | translate }}</div><strong>{{ v.transferredOut | number: '1.0-0' }}</strong></div>
        <div><div class="text-muted kpi-label">{{ 'Held now' | translate }}</div><strong [style.color]="v.held && v.canSettle ? '#b91c1c' : ''">{{ v.held | number: '1.0-0' }}</strong></div>
      </div>
      <p class="text-muted mt-2" style="font-size:0.85rem;" *ngIf="v.canSettle">{{ 'The lease is over. Keep what the tenant owes, then refund the rest.' | translate }}</p>
      <p class="text-muted mt-2" style="font-size:0.85rem;" *ngIf="v.stillToPay > 0 && v.paidIn > 0 && !v.canSettle && v.leaseStatus !== 'ENDED' && v.leaseStatus !== 'TERMINATED'">{{ 'Still to collect: {p0}.' | translate: { p0: (v.stillToPay | number: '1.0-0') } }}</p>

      <form *ngIf="mode" (ngSubmit)="save()" class="mb-3 mt-2" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
        <h5 class="mb-2">{{ mode === 'receive' ? 'Receive deposit' : mode === 'keep' ? 'Keep part of the deposit' : mode === 'refund' ? ('Refund the deposit' | translate) : ('Move deposit to a renewal' | translate) }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" *ngIf="mode === 'keep'" style="min-width:11rem;"><label class="form-label">{{ 'Why *' | translate }}</label>
            <select class="form-select" name="rs" [(ngModel)]="f.reason" (ngModelChange)="reasonChanged()"><option *ngFor="let r of reasons" [ngValue]="r.value">{{ r.label }}</option></select></div>
          <div class="form-group mb-2" *ngIf="mode === 'keep' && f.reason === 'UNPAID_RENT'" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Rent invoice *' | translate }}</label>
            <select class="form-select" name="iv" [(ngModel)]="f.invoiceId" (ngModelChange)="invoiceChanged()">
              <option [ngValue]="null">{{ 'Choose the invoice' | translate }}</option>
              <option *ngFor="let i of v.openInvoices" [ngValue]="i.invoiceId">{{ '{p0} · due {p1} · owed {p2}' | translate: { p0: (i.invoiceNumber), p1: (i.dueDate | date: 'd MMM y'), p2: (i.outstanding | number: '1.0-0') } }}</option>
            </select>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="!v.openInvoices.length">{{ 'No rent invoice of this lease is unpaid.' | translate }}</div></div>
          <div class="form-group mb-2" *ngIf="mode === 'move'" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Move to *' | translate }}</label>
            <select class="form-select" name="tg" [(ngModel)]="f.toLeaseId" (ngModelChange)="targetChanged()"><option [ngValue]="null">{{ 'Choose the lease' | translate }}</option>
              <option *ngFor="let t of v.transferTargets" [ngValue]="t.leaseId">{{ '{p0} · deposit {p1} · room for {p2}' | translate: { p0: (t.leaseCode), p1: (t.agreed | number: '1.0-0'), p2: (t.room | number: '1.0-0') } }}</option></select></div>
          <div class="form-group mb-2" style="min-width:9rem;"><label class="form-label">{{ 'Amount *' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="am" [(ngModel)]="f.amount" /></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Date' | translate }}</label><input class="form-control" type="date" name="dt" [(ngModel)]="f.date" [max]="today" /></div>
          <div class="form-group mb-2" *ngIf="mode === 'receive' || mode === 'refund'" style="min-width:10rem;"><label class="form-label">{{ 'Method' | translate }}</label>
            <select class="form-select" name="md" [(ngModel)]="f.method"><option *ngFor="let m of methods" [ngValue]="m.value">{{ m.label }}</option></select></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="mode === 'keep' && f.reason === 'DAMAGE' && v.suggestedDamages > 0">
          {{ 'The move-out inspection puts the repairs at {p0}.' | translate: { p0: (v.suggestedDamages | number: '1.0-0') } }}
          <a href="#" (click)="f.amount = v.suggestedDamages; $event.preventDefault()">{{ 'Use that amount' | translate }}</a>
          <span *ngIf="v.moveOutInspectionId"> · <a [routerLink]="['/admin/propertymgmt/inspections', v.moveOutInspectionId]">{{ 'Open the inspection' | translate }}</a></span>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="nt" [(ngModel)]="f.note" maxlength="1000" /></div>
        <div class="flex gap-2">
          <button class="btn btn-sm btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Saving…' | translate) : ('Save' | translate) }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="mode = null; error = null" [disabled]="busy">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <table class="stack table-sm mt-2" *ngIf="v.txns.length">
        <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Details' | translate }}</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let t of v.txns">
            <tr [style.opacity]="t.voided ? 0.55 : 1">
              <td style="white-space:nowrap;" data-label="Date">{{ t.date | date: 'd MMM y' }}</td>
              <td data-label="What"><span [style.text-decoration]="t.voided ? 'line-through' : ''">{{ kind(t) }}</span>
                <span class="badge badge-secondary" *ngIf="t.voided" style="margin-left:0.3rem;">{{ 'Voided' | translate }}</span></td>
              <td class="text-end" style="white-space:nowrap;" data-label="Amount"><span [style.text-decoration]="t.voided ? 'line-through' : ''">{{ sign(t) }}{{ t.amount | number: '1.0-0' }}</span></td>
              <td data-label="Details" style="font-size:0.85rem;">
                <span *ngIf="t.reason">{{ reasonName(t.reason) }}</span>
                <span *ngIf="t.invoiceNumber"> {{ '· invoice {p0}' | translate: { p0: (t.invoiceNumber) } }}<span *ngIf="t.receiptNumber"> {{ '· receipt {p0}' | translate: { p0: (t.receiptNumber) } }}</span></span>
                <span *ngIf="t.otherLeaseCode"> · {{ t.kind === 'TRANSFER_IN' ? 'from' : 'to' }} <a [routerLink]="['/admin/propertymgmt/leases', t.otherLeaseId]">{{ t.otherLeaseCode }}</a></span>
                <span *ngIf="t.method" style="white-space:nowrap;"> {{ methodName(t.method) }}</span>
                <span *ngIf="t.note"> · {{ t.note }}</span>
                <span *ngIf="t.voided" class="text-muted"> {{ '· voided: {p0}' | translate: { p0: (t.voidReason) } }}</span>
                <span class="text-muted" style="white-space:nowrap;"> · {{ t.recordedByName || '—' }}</span>
              </td>
              <td style="text-align:right; white-space:nowrap;" data-label="">
                <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="v.canManage && t.canVoid && voiding !== t.id" (click)="voiding = t.id; voidReason = ''">{{ 'Void' | translate }}</button>
              </td>
            </tr>
            <tr *ngIf="voiding === t.id">
              <td colspan="5">
                <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                  <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Why is this entry wrong? *' | translate }}</label><input class="form-control" [name]="'vr' + t.id" [(ngModel)]="voidReason" maxlength="500" /></div>
                  <button class="btn btn-sm btn-danger" type="button" [disabled]="busy || !voidReason.trim()" (click)="doVoid(t)">{{ 'Void it' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="voiding = null">{{ 'Cancel' | translate }}</button>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
    </div>
  `,
  styles: [`.deposit-figures{display:flex;flex-wrap:wrap;gap:1.5rem;} .deposit-figures strong{font-size:1.1rem;} .kpi-label{font-size:0.75rem;}`],
})
export class PmDepositPanelComponent implements OnChanges {
  @Input() leaseId!: number;
  /** The lease status: when it changes (ended, terminated) the deposit is read again. */
  @Input() status = '';

  v: DepositView | null = null;
  mode: Mode = null;
  busy = false;
  error: string | null = null;
  voiding: number | null = null;
  voidReason = '';
  today = new Date().toISOString().slice(0, 10);
  reasons = DEPOSIT_REASONS;
  methods = DEPOSIT_METHODS;
  f: { amount: number | null; date: string; method: string; note: string; reason: DepositReason; invoiceId: number | null; toLeaseId: number | null } = this.blank();

  constructor(private api: PmDepositApiService) {}

  private blank() { return { amount: null as number | null, date: '', method: 'CASH', note: '', reason: 'UNPAID_RENT' as DepositReason, invoiceId: null as number | null, toLeaseId: null as number | null }; }

  ngOnChanges(): void { if (this.leaseId) this.load(); }

  load(): void {
    this.api.deposit(this.leaseId).subscribe({ next: (d) => { this.v = d; }, error: (e) => { this.error = e?.error?.message ?? T('Could not load the deposit'); } });
  }

  badge(s: string): string { return depositBadge(s); }
  stateName(s: string): string { return (DEPOSIT_STATE_LABEL as Record<string, string>)[s] ?? s; }
  kind(t: DepositTxn): string { return DEPOSIT_KIND_LABEL[t.kind] ?? t.kind; }
  sign(t: DepositTxn): string { return t.kind === 'RECEIVED' || t.kind === 'TRANSFER_IN' ? '+' : '−'; }
  reasonName(r: string): string { return this.reasons.find((x) => x.value === r)?.label ?? r; }
  methodName(m: string): string { return this.methods.find((x) => x.value === m)?.label ?? m; }

  open(m: Mode): void {
    if (!this.v) return;
    this.error = null;
    this.f = this.blank();
    if (m === 'receive') this.f.amount = this.v.stillToPay;
    if (m === 'refund') this.f.amount = this.v.held;
    if (m === 'keep') { this.f.reason = this.v.openInvoices.length ? 'UNPAID_RENT' : 'DAMAGE'; this.reasonChanged(); }
    if (m === 'move' && this.v.transferTargets.length === 1) { this.f.toLeaseId = this.v.transferTargets[0].leaseId; this.targetChanged(); }
    this.mode = m;
  }

  reasonChanged(): void {
    if (!this.v) return;
    this.f.invoiceId = null;
    if (this.f.reason === 'UNPAID_RENT') {
      if (this.v.openInvoices.length) { this.f.invoiceId = this.v.openInvoices[0].invoiceId; this.invoiceChanged(); }
    } else if (this.f.reason === 'DAMAGE' && this.v.suggestedDamages > 0) {
      this.f.amount = Math.min(this.v.suggestedDamages, this.v.held);
    }
  }

  invoiceChanged(): void {
    const i = this.v?.openInvoices.find((x) => x.invoiceId === this.f.invoiceId);
    if (i && this.v) this.f.amount = Math.min(i.outstanding, this.v.held);
  }

  targetChanged(): void {
    const t = this.v?.transferTargets.find((x) => x.leaseId === this.f.toLeaseId);
    if (t && this.v) this.f.amount = Math.min(t.room, this.v.held);
  }

  save(): void {
    if (!this.v || !this.mode) return;
    const amount = Number(this.f.amount);
    if (!(amount > 0)) { this.error = T('Enter the amount.'); return; }
    const date = this.f.date || null;
    const note = this.f.note.trim();
    let call;
    if (this.mode === 'receive') call = this.api.receive(this.leaseId, { amount, date, method: this.f.method, note });
    else if (this.mode === 'refund') call = this.api.refund(this.leaseId, { amount, date, method: this.f.method, note });
    else if (this.mode === 'keep') {
      if (this.f.reason === 'UNPAID_RENT' && !this.f.invoiceId) { this.error = T('Choose the rent invoice to settle.'); return; }
      call = this.api.deduct(this.leaseId, { amount, date, reason: this.f.reason, invoiceId: this.f.reason === 'UNPAID_RENT' ? this.f.invoiceId : null, note });
    } else {
      if (!this.f.toLeaseId) { this.error = T('Choose the lease to move the deposit to.'); return; }
      call = this.api.transfer(this.leaseId, { toLeaseId: this.f.toLeaseId, amount, date, note });
    }
    this.busy = true;
    this.error = null;
    call.subscribe({
      next: (d) => { this.v = d; this.busy = false; this.mode = null; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save'); },
    });
  }

  doVoid(t: DepositTxn): void {
    this.busy = true;
    this.error = null;
    this.api.voidEntry(t.id, this.voidReason.trim()).subscribe({
      next: (d) => { this.v = d; this.busy = false; this.voiding = null; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not void'); },
    });
  }
}
