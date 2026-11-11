import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MailedBuyer, RealEstateApiService, SaleNote, SaleNoteKind } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
/** On a sold unit: tell the buyer what is owed (reminder, statement by e-mail) and keep a log of calls and promises. */
@Component({
  selector: 'app-re-sale-collection',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <h4 class="mb-2">{{ 'Collecting from the buyer' | translate }}</h4>
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>
    <div class="flex gap-2 mb-2" style="flex-wrap:wrap;" *ngIf="canManage && stage === 'SOLD'">
      <button class="btn btn-sm btn-outline-secondary sc-remind" type="button" *ngIf="hasPlan" (click)="send('remind')" [disabled]="busy">{{ 'Remind the buyer' | translate }}</button>
      <button class="btn btn-sm btn-outline-secondary sc-statement" type="button" (click)="send('statement')" [disabled]="busy">{{ 'E-mail the statement' | translate }}</button>
      <span class="text-muted" style="font-size:0.8rem; align-self:center;">{{ 'Sent to {p0}' | translate: { p0: (buyerEmail || 'the buyer (no e-mail address on file)') } }}</span>
    </div>
    <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="canManage && stage === 'SOLD'">
      <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Log' | translate }}</label>
        <select class="form-select sc-kind" name="sk" [(ngModel)]="kind"><option value="CALL">{{ 'Called the buyer' | translate }}</option><option value="PROMISE">{{ 'Buyer promised to pay' | translate }}</option><option value="NOTE">{{ 'Other note' | translate }}</option></select></div>
      <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What was said' | translate }}</label>
        <input class="form-control sc-text" name="st" [(ngModel)]="text" maxlength="2000" /></div>
      <div class="form-group" *ngIf="kind === 'PROMISE'" style="min-width:11rem;"><label class="form-label">{{ 'Promised date' | translate }}</label>
        <input class="form-control sc-date" type="date" name="sd" [(ngModel)]="promise" [min]="todayIso()" /></div>
      <div class="form-group"><button class="btn btn-sm btn-primary sc-save" type="button" (click)="save()" [disabled]="busy">{{ 'Save' | translate }}</button></div>
    </div>
    <p class="text-muted" *ngIf="loaded && !notes.length">{{ 'Nothing has been logged yet.' | translate }}</p>
    <table class="stack table-sm" *ngIf="notes.length">
      <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Note' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
      <tbody><tr *ngFor="let n of notes; trackBy: track">
        <td data-label="When">{{ n.createdAt | date: 'd MMM y, HH:mm' }}</td>
        <td data-label="What">{{ kindName(n.kind) }}<span *ngIf="n.promiseDate"> {{ '· by {p0}' | translate: { p0: (n.promiseDate | date: 'd MMM') } }}</span></td>
        <td data-label="Note">{{ n.text }}</td>
        <td data-label="By">{{ n.createdByName || ('The system' | translate) }}</td>
      </tr></tbody>
    </table>
  `,
})
export class ReSaleCollectionComponent implements OnInit, OnChanges {
  @Input() saleId: number | null = null;
  @Input() stage: string | null = null;
  @Input() buyerEmail: string | null = null;
  @Input() hasPlan = false;

  notes: SaleNote[] = [];
  loaded = false;
  canManage: boolean;
  busy = false;
  error: string | null = null;
  notice: string | null = null;
  kind: SaleNoteKind = 'CALL';
  text = '';
  promise = '';

  constructor(private api: RealEstateApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void { this.load(); }
  ngOnChanges(): void { if (this.loaded) this.load(); }

  track(_: number, n: SaleNote): number { return n.id; }
  todayIso(): string { return new Date().toISOString().slice(0, 10); }

  kindName(k: SaleNoteKind): string {
    switch (k) {
      case 'CALL': return 'Call';
      case 'PROMISE': return 'Promise to pay';
      case 'REMINDER': return 'Reminder e-mailed';
      case 'STATEMENT': return 'Statement e-mailed';
      default: return 'Note';
    }
  }

  load(): void {
    if (this.saleId == null) return;
    this.api.saleNotes(this.saleId).subscribe({ next: (n) => { this.notes = n; this.loaded = true; }, error: () => { this.notes = []; this.loaded = true; } });
  }

  save(): void {
    if (this.saleId == null) return;
    this.error = null;
    this.notice = null;
    if (!this.text.trim()) { this.error = T('Write what was said.'); return; }
    if (this.kind === 'PROMISE' && !this.promise) { this.error = T('Choose the date the buyer promised to pay.'); return; }
    this.busy = true;
    this.api.addSaleNote(this.saleId, { kind: this.kind, text: this.text.trim(), promiseDate: this.kind === 'PROMISE' ? this.promise : null }).subscribe({
      next: () => { this.busy = false; this.text = ''; this.promise = ''; this.notice = T('Saved.'); this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save'); },
    });
  }

  send(what: 'remind' | 'statement'): void {
    if (this.saleId == null) return;
    this.error = null;
    this.notice = null;
    this.busy = true;
    const call: Observable<MailedBuyer> = what === 'remind' ? this.api.remindBuyer(this.saleId) : this.api.emailStatement(this.saleId);
    call.subscribe({
      next: (m) => { this.busy = false; this.notice = m.message || T('Sent to {p0}', { p0: m.sentTo }); this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
