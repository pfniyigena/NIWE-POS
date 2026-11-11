import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PmAccountApiService, TenantAccount } from '../../core/api/pm-account-api.service';
import { downloadBlob } from '../../core/download-file';

import { T } from '../../core/i18n/t';
/** A tenant's account statement: what was charged and paid over a period, with the balance, to download as a PDF or e-mail to the tenant. */
@Component({
  selector: 'app-tenant-account',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;">
      <h4 class="mb-2">{{ 'Account statement' | translate }}</h4>
      <div class="flex gap-2 align-center mb-2" style="flex-wrap:wrap;">
        <label class="text-muted">{{ 'From' | translate }}</label><input class="form-control" style="width:10.5rem;" type="date" name="af" [(ngModel)]="from" />
        <label class="text-muted">{{ 'To' | translate }}</label><input class="form-control" style="width:10.5rem;" type="date" name="at" [(ngModel)]="to" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()" [disabled]="busy">{{ 'Show' | translate }}</button>
        <button class="btn btn-outline-secondary" type="button" (click)="pdf()" [disabled]="busy || !a">{{ 'PDF' | translate }}</button>
        <button class="btn btn-outline-secondary" type="button" *ngIf="canManage" (click)="sending = !sending" [disabled]="busy || !a">{{ 'E-mail' | translate }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>

      <form *ngIf="sending" class="mb-2" style="border:1px solid var(--border); border-radius:0.5rem; padding:0.75rem;" (ngSubmit)="send()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'To' | translate }}</label>
            <input class="form-control" type="email" name="sto" [(ngModel)]="address" [placeholder]="email ? 'Leave empty to use ' + email : 'Type the tenant e-mail address'" /></div>
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Message (optional)' | translate }}</label>
            <input class="form-control" name="smsg" [(ngModel)]="message" maxlength="1000" /></div>
        </div>
        <button class="btn btn-primary btn-sm" type="submit" [disabled]="busy">{{ busy ? ('Sending…' | translate) : ('Send statement' | translate) }}</button>
      </form>

      <ng-container *ngIf="a">
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
          <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Brought forward' | translate }}</div><strong>{{ a.opening | number: '1.0-0' }}</strong></div>
          <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Charged' | translate }}</div><strong>{{ a.charged | number: '1.0-0' }}</strong></div>
          <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Paid' | translate }}</div><strong>{{ a.paid | number: '1.0-0' }}</strong></div>
          <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Balance owed' | translate }}</div><strong [style.color]="a.closing > 0 ? '#b91c1c' : ''">{{ a.closing | number: '1.0-0' }}</strong></div>
          <div *ngIf="a.overdue"><div class="text-muted" style="font-size:0.8rem;">{{ 'Overdue today' | translate }}</div><strong style="color:#b91c1c;">{{ a.overdue | number: '1.0-0' }}</strong></div>
        </div>
        <table class="stack table-sm" *ngIf="a.lines.length; else none">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Reference' | translate }}</th><th class="text-end">{{ 'Charged' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let l of a.lines">
            <td style="white-space:nowrap;" data-label="Date">{{ l.date | date: 'd MMM y' }}</td>
            <td data-label="What">{{ l.description }}</td>
            <td data-label="Reference">{{ l.reference }}</td>
            <td class="text-end" data-label="Charged">{{ l.charge != null ? (l.charge | number: '1.0-0') : '' }}</td>
            <td class="text-end" data-label="Paid">{{ l.payment != null ? (l.payment | number: '1.0-0') : '' }}</td>
            <td class="text-end" data-label="Balance"><strong>{{ l.balance | number: '1.0-0' }}</strong></td>
          </tr></tbody>
        </table>
        <ng-template #none><div class="text-muted">{{ 'Nothing was charged or paid in this period.' | translate }}</div></ng-template>
      </ng-container>
    </div>
  `,
})
export class TenantAccountComponent implements OnChanges {
  @Input({ required: true }) tenantId!: number;
  @Input() email: string | null = null;
  @Input() canManage = false;
  a: TenantAccount | null = null;
  from = '';
  to = '';
  busy = false;
  error: string | null = null;
  note: string | null = null;
  sending = false;
  address = '';
  message = '';

  constructor(private api: PmAccountApiService) {}

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.busy = true;
    this.error = null;
    this.api.account(this.tenantId, this.from || undefined, this.to || undefined).subscribe({
      next: (r) => { this.busy = false; this.a = r; if (!this.from) this.from = r.from; if (!this.to) this.to = r.to; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not load the statement'); },
    });
  }

  pdf(): void {
    if (!this.a) return;
    this.busy = true;
    this.error = null;
    this.api.accountPdf(this.tenantId, this.from || undefined, this.to || undefined).subscribe({
      next: (b) => { this.busy = false; downloadBlob(b, `tenant-statement-${this.a!.tenantCode.toLowerCase()}.pdf`); },
      error: () => { this.busy = false; this.error = T('Could not make the statement'); },
    });
  }

  send(): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    const body: { from?: string; to?: string; email?: string; message?: string } = { from: this.from || undefined, to: this.to || undefined };
    if (this.address.trim()) body.email = this.address.trim();
    if (this.message.trim()) body.message = this.message.trim();
    this.api.emailAccount(this.tenantId, body).subscribe({
      next: (r) => { this.busy = false; this.sending = false; this.note = `Statement sent to ${r.sentTo}.`; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not send the statement'); },
    });
  }
}
