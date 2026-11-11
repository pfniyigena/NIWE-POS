import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PmFeeInvoicePanelComponent } from './pm-fee-invoice-panel.component';
import { downloadBlob } from '../../core/download-file';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../../core/models';
import { PropertyMgmtApiService, PropertyRow } from '../../core/api/propertymgmt-api.service';
import { PmStatementApiService, STATEMENT_STATUS_LABEL, StatementDto, statementBadge } from '../../core/api/pm-statement-api.service';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();
const pad = (n: number): string => String(n).padStart(2, '0');
const today = (): string => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };

/** Property Management > an owner statement: the lines, the totals, and the steps from draft to paid. */
@Component({
  selector: 'app-statement-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PmFeeInvoicePanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>
      <div *ngIf="!d && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="d">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/statements" style="font-size:0.85rem;">{{ '← Owner statements' | translate }}</a>
            <h3>{{ d.statement.code }} · <a [routerLink]="['/admin/propertymgmt/owners', d.statement.ownerId]">{{ d.statement.ownerName }}</a>
              <span class="badge" [ngClass]="badge(d.statement.status)" style="font-size:0.8rem; vertical-align:middle;">{{ label(d.statement.status) }}</span></h3>
            <div class="text-muted">{{ '{p0} to {p1}' | translate: { p0: (d.statement.periodFrom | date: 'd MMM y'), p1: (d.statement.periodTo | date: 'd MMM y') } }}</div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-secondary" type="button" (click)="download('pdf')" [disabled]="busy">{{ 'PDF' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" (click)="download('xlsx')" [disabled]="busy">{{ 'Excel' | translate }}</button>
            <ng-container *ngIf="d.canManage">
              <button class="btn btn-outline-secondary" type="button" *ngIf="status !== 'DRAFT'" (click)="openEmail()" [disabled]="busy">{{ 'E-mail to owner' | translate }}</button>
              <button class="btn btn-outline-secondary" type="button" *ngIf="status === 'DRAFT'" (click)="refresh()" [disabled]="busy" [title]="'Read the payments and repairs again' | translate">{{ 'Rebuild' | translate }}</button>
              <button class="btn btn-primary" type="button" *ngIf="status === 'DRAFT'" (click)="finalize()" [disabled]="busy">{{ 'Agree the statement' | translate }}</button>
              <button class="btn btn-primary" type="button" *ngIf="status === 'FINAL'" (click)="openPay()">{{ d.statement.netPayable > 0 ? ('Pay the owner' | translate) : ('Settle' | translate) }}</button>
              <button class="btn btn-outline-secondary" type="button" *ngIf="status === 'FINAL'" (click)="act(api.reopen(d.statement.id))" [disabled]="busy">{{ 'Back to draft' | translate }}</button>
              <button class="btn btn-outline-secondary" type="button" *ngIf="status === 'PAID'" (click)="act(api.unpay(d.statement.id))" [disabled]="busy">{{ 'Undo payment' | translate }}</button>
              <button class="btn btn-outline-danger" type="button" *ngIf="status === 'DRAFT'" (click)="remove()" [disabled]="busy">{{ 'Delete' | translate }}</button>
            </ng-container>
          </div>
        </div>

        <div class="alert alert-warning" *ngFor="let w of d.warnings">{{ w }}</div>
        <div class="alert alert-info" *ngIf="status === 'DRAFT' && d.canManage">{{ 'Agreeing this statement books it to the accounts: the rent leaves sales revenue, your fee goes to Management Fee Income, VAT on the fee (if you charge it) goes to Tax Payable, and what is left is held as Owners\\' Funds Payable until you pay the owner. Record contractors\\' bills for owner repairs as expenses (Repairs and maintenance) so the books show the cost.' | translate }}</div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'email'">
          <h4 class="mb-2">{{ 'E-mail the statement to the owner' | translate }}</h4>
          <p class="text-muted mb-2">{{ 'The statement goes as a PDF, with the totals in the message.' | translate }}</p>
          <div class="form-group mb-2"><label class="form-label">{{ 'To' | translate }}</label>
            <input class="form-control" type="email" name="et" [(ngModel)]="mail.to" maxlength="255" [placeholder]="d.ownerEmail ? '' : 'The owner has no e-mail address. Type one here.'" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'A note above the statement (optional)' | translate }}</label>
            <textarea class="form-control" rows="3" name="em" [(ngModel)]="mail.message" maxlength="1000"></textarea></div>
          <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="sendEmail()" [disabled]="busy">{{ 'Send' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">{{ 'Cancel' | translate }}</button></div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'pay'">
          <h4 class="mb-2">{{ d.statement.netPayable > 0 ? ('Pay the owner' | translate) : ('Settle the statement' | translate) }}</h4>
          <div class="text-muted mb-2" style="font-size:0.9rem;" *ngIf="d.statement.netPayable > 0">{{ '{p0} to {p1}' | translate: { p0: (d.statement.netPayable | number: '1.0-0'), p1: (d.statement.ownerName) } }}<span *ngIf="d.ownerBankAccount"> · {{ d.ownerBankName }} {{ d.ownerBankAccount }}</span>{{ '. This only records the payout; make the transfer itself outside.' | translate }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Paid on' | translate }}</label><input class="form-control" type="date" name="pd" [(ngModel)]="pay.paidDate" [max]="todayStr" [min]="d.statement.periodTo" /></div>
            <div class="form-group mb-2" style="min-width:12rem;" *ngIf="d.statement.netPayable > 0"><label class="form-label">{{ 'How *' | translate }}</label>
              <select class="form-select" name="pm" [(ngModel)]="pay.method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Reference' | translate }}</label><input class="form-control" name="pr" [(ngModel)]="pay.reference" maxlength="120" [placeholder]="'transfer or cheque no.' | translate" /></div>
          </div>
          <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="doPay()" [disabled]="busy">{{ 'Record the payment' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">{{ 'Cancel' | translate }}</button></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Totals' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td>{{ 'Rent collected' | translate }}</td><td class="text-end">{{ d.statement.rentCollected | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Management fee' | translate }}</td><td class="text-end">− {{ d.statement.managementFee | number: '1.2-2' }}</td></tr>
              <tr *ngIf="d.statement.vat !== 0"><td>{{ 'VAT on the management fee' | translate }}</td><td class="text-end">− {{ d.statement.vat | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Repairs paid by the owner' | translate }}</td><td class="text-end">− {{ d.statement.repairs | number: '1.2-2' }}</td></tr>
              <tr *ngIf="d.statement.adjustments !== 0"><td>{{ 'Adjustments' | translate }}</td><td class="text-end">{{ d.statement.adjustments | number: '1.2-2' }}</td></tr>
              <tr><td><strong>{{ 'Net to the owner' | translate }}</strong></td><td class="text-end" style="font-size:1.2rem;" [style.color]="d.statement.netPayable < 0 ? '#b91c1c' : ''"><strong>{{ d.statement.netPayable | number: '1.2-2' }}</strong></td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Payout' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Owner' | translate }}</td><td><a [routerLink]="['/admin/propertymgmt/owners', d.statement.ownerId]">{{ d.statement.ownerName }}</a>{{ d.ownerPhone ? ' · ' + d.ownerPhone : '' }}</td></tr>
              <tr><td class="text-muted">{{ 'Bank' | translate }}</td><td>{{ d.ownerBankAccount ? (d.ownerBankName || '') + ' ' + d.ownerBankAccount : '—' }}</td></tr>
              <tr *ngIf="d.finalizedDate"><td class="text-muted">{{ 'Agreed' | translate }}</td><td>{{ d.finalizedDate | date: 'd MMM y' }}{{ d.finalizedByName ? ' by ' + d.finalizedByName : '' }}</td></tr>
              <tr *ngIf="status !== 'DRAFT'"><td class="text-muted">{{ 'Accounts' | translate }}</td><td>{{ d.postedToAccounts ? ('Booked to the ledger (rent, fee, repairs and the amount owed to the owner)' | translate) : ('Not booked. It will be booked when you pay it.' | translate) }}</td></tr>
              <tr *ngIf="d.emailedAt"><td class="text-muted">{{ 'E-mailed' | translate }}</td><td>{{ d.emailedTo }} · {{ d.emailedAt | date: 'd MMM y, HH:mm' }}</td></tr>
              <tr *ngIf="d.statement.paidDate"><td class="text-muted">{{ 'Paid' | translate }}</td><td>{{ d.statement.paidDate | date: 'd MMM y' }}{{ d.paidMethod ? ' · ' + d.paidMethod : '' }}{{ d.paidReference ? ' · ' + d.paidReference : '' }}</td></tr>
            </tbody></table>
            <div class="form-group mt-2" *ngIf="d.canManage && status !== 'PAID'"><label class="form-label">{{ 'Notes' | translate }}</label>
              <div class="flex gap-2"><input class="form-control" name="nt" [(ngModel)]="notes" maxlength="2000" /><button class="btn btn-outline-secondary" type="button" (click)="saveNotes()" [disabled]="busy">{{ 'Save' | translate }}</button></div></div>
            <div class="mt-2" *ngIf="status === 'PAID' && d.notes" style="white-space:pre-wrap;">{{ d.notes }}</div>
          </div>
        </div>

        <app-pm-fee-invoice-panel [statementId]="d.statement.id" [status]="d.statement.status" [fee]="d.statement.managementFee" [canManage]="d.canManage"></app-pm-fee-invoice-panel>

        <div class="card mb-3" style="padding:1rem;" *ngIf="d.properties.length > 1 || (d.properties.length === 1 && d.properties[0].feePercent != null)">
          <h4 class="mb-2">{{ 'By property' | translate }}</h4>
          <table class="table-sm stack"><thead><tr><th>{{ 'Property' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th class="text-end">{{ 'Fee %' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th><th class="text-end" *ngIf="d.statement.vat !== 0">{{ 'VAT' | translate }}</th><th class="text-end">{{ 'Repairs' | translate }}</th><th class="text-end">{{ 'Adjust.' | translate }}</th><th class="text-end">{{ 'Net' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let p of d.properties"><td data-label="Property">{{ p.propertyName }}</td><td class="text-end" data-label="Rent">{{ p.rent | number: '1.0-0' }}</td><td class="text-end" data-label="Fee %">{{ p.feePercent != null ? (p.feePercent | number: '1.0-2') : '—' }}</td>
              <td class="text-end" data-label="Fee">{{ p.fee | number: '1.0-0' }}</td><td class="text-end" *ngIf="d.statement.vat !== 0" data-label="VAT">{{ p.vat | number: '1.0-0' }}</td><td class="text-end" data-label="Repairs">{{ p.repairs | number: '1.0-0' }}</td><td class="text-end" data-label="Adjust.">{{ p.adjustments | number: '1.0-0' }}</td><td class="text-end" data-label="Net"><strong>{{ p.net | number: '1.0-0' }}</strong></td></tr></tbody></table>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Lines' | translate }}</h4>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Property · unit' | translate }}</th><th>{{ 'Reference' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let l of d.lines">
                <td style="white-space:nowrap;" data-label="Date">{{ l.date | date: 'd MMM y' }}</td>
                <td data-label="What">{{ l.description }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="l.kind === 'RENT' && l.tenantName">{{ l.tenantName }}</div></td>
                <td data-label="Property · unit">{{ l.propertyName }}<span *ngIf="l.unitNo"> · {{ l.unitNo }}</span></td>
                <td data-label="Reference">{{ l.reference }}</td>
                <td class="text-end" [style.color]="l.amount < 0 ? '#b91c1c' : ''" data-label="Amount">{{ l.amount | number: '1.2-2' }}</td>
                <td style="text-align:right;" data-label=""><button class="btn btn-sm btn-outline-danger" type="button" *ngIf="l.editable" (click)="act(api.removeLine(d.statement.id, l.id))" [disabled]="busy">{{ 'Remove' | translate }}</button></td>
              </tr>
            </tbody>
          </table>
          <form *ngIf="d.canManage && status === 'DRAFT'" class="mt-2" (ngSubmit)="addLine()">
            <h4 class="mb-1" style="font-size:0.95rem;">{{ 'Add a line' | translate }}</h4>
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What' | translate }}</label><input class="form-control" name="ld" [(ngModel)]="adj.description" maxlength="500" [placeholder]="'e.g. Advertising for A-02' | translate" /></div>
              <div class="form-group" style="width:11rem;"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" step="0.01" name="la" [(ngModel)]="adj.amount" [placeholder]="'− takes off' | translate" /></div>
              <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Property' | translate }}</label>
                <select class="form-select" name="lp" [(ngModel)]="adj.propertyId"><option [ngValue]="null">{{ 'Not tied to one' | translate }}</option><option *ngFor="let p of ownerProps" [ngValue]="p.id">{{ p.name }}</option></select></div>
              <div class="form-group" style="min-width:11rem;"><label class="form-label">{{ 'Date' | translate }}</label><input class="form-control" type="date" name="ldt" [(ngModel)]="adj.date" [min]="d.statement.periodFrom" [max]="d.statement.periodTo" /></div>
              <div class="form-group"><button class="btn btn-outline-primary" type="submit" [disabled]="busy">{{ 'Add' | translate }}</button></div>
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'A positive amount adds to what the owner gets; a negative amount takes from it.' | translate }}</div>
          </form>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">{{ 'Made by {p0} on {p1}. Rent is counted on the day the payment was recorded, not the day it was due.' | translate: { p0: (d.createdByName || '—'), p1: (d.createdAt | date: 'd MMM y') } }}</div>
      </ng-container>
    </app-shell>
  `,
})
export class StatementDetailComponent implements OnInit {
  d: StatementDto | null = null;
  methods: PaymentMethodOption[] = [];
  ownerProps: PropertyRow[] = [];
  error: string | null = null;
  note: string | null = null;
  busy = false;
  panel: 'pay' | 'email' | null = null;
  mail = { to: '', message: '' };
  notes = '';
  todayStr = today();
  pay = { paidDate: today(), method: '', reference: '' };
  adj = { description: '', amount: null as number | null, propertyId: null as number | null, date: '' };

  constructor(public api: PmStatementApiService, private pm: PropertyMgmtApiService, private methodApi: PaymentMethodApiService, private route: ActivatedRoute, private router: Router) {}

  get status(): string { return this.d?.statement.status ?? ''; }

  ngOnInit(): void {
    this.api.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (d) => { this.set(d); this.pm.properties({ ownerId: d.statement.ownerId }).subscribe({ next: (p) => (this.ownerProps = p), error: () => undefined }); },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the statement')),
    });
    this.methodApi.listActive().subscribe({ next: (m) => { this.methods = m; if (m.length && !this.pay.method) this.pay.method = m[0].code; }, error: () => undefined });
  }

  private set(d: StatementDto): void {
    this.d = d;
    this.notes = d.notes ?? '';
  }

  badge(s: string): string { return statementBadge(s); }
  label(s: string): string { return (STATEMENT_STATUS_LABEL as Record<string, string>)[s] ?? s; }

  act(call: Observable<StatementDto>, done?: string): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    call.subscribe({
      next: (d) => { this.busy = false; this.set(d); this.panel = null; if (done) this.note = done; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }

  refresh(): void { if (this.d) this.act(this.api.refresh(this.d.statement.id), 'Rebuilt from the payments and repairs as they are now.'); }
  finalize(): void { if (this.d) this.act(this.api.finalize(this.d.statement.id), 'Agreed. The lines are now locked.'); }
  saveNotes(): void { if (this.d) this.act(this.api.notes(this.d.statement.id, txt(this.notes)), 'Notes saved.'); }

  openEmail(): void {
    this.mail = { to: this.d?.emailedTo ?? this.d?.ownerEmail ?? '', message: '' };
    this.panel = 'email';
  }

  sendEmail(): void {
    if (!this.d) return;
    const to = txt(this.mail.to);
    if (!to && !this.d.ownerEmail) { this.error = T('Type an e-mail address for the owner.'); return; }
    this.act(this.api.email(this.d.statement.id, { to, message: txt(this.mail.message) }), 'E-mailed to the owner.');
  }

  openPay(): void {
    this.pay = { paidDate: today() > (this.d?.statement.periodTo ?? '') ? today() : (this.d?.statement.periodTo ?? today()), method: this.methods[0]?.code ?? '', reference: '' };
    this.panel = 'pay';
  }

  doPay(): void {
    if (!this.d) return;
    if (this.d.statement.netPayable > 0 && !this.pay.method) { this.error = T('Choose how the owner was paid.'); return; }
    this.act(this.api.pay(this.d.statement.id, { paidDate: this.pay.paidDate || null, method: this.d.statement.netPayable > 0 ? this.pay.method : null, reference: txt(this.pay.reference) }), 'Recorded as paid.');
  }

  addLine(): void {
    if (!this.d) return;
    const amount = num(this.adj.amount);
    if (!txt(this.adj.description)) { this.error = T('Say what the line is for.'); return; }
    if (!amount) { this.error = T('Enter the amount (negative takes from the owner).'); return; }
    this.act(this.api.addLine(this.d.statement.id, { description: txt(this.adj.description), amount, propertyId: this.adj.propertyId, date: this.adj.date || null }));
    this.adj = { description: '', amount: null, propertyId: null, date: '' };
  }

  download(format: 'pdf' | 'xlsx'): void {
    if (!this.d) return;
    this.busy = true;
    this.api.file(this.d.statement.id, format).subscribe({
      next: (blob) => { this.busy = false; downloadBlob(blob, `${this.d!.statement.code}.${format}`); },
      error: () => { this.busy = false; this.error = T('Could not make the file'); },
    });
  }

  remove(): void {
    if (!this.d || !confirm(T('Delete {p0}? The period becomes free to make again.', { p0: this.d.statement.code }))) return;
    this.busy = true;
    this.api.remove(this.d.statement.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/statements']),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not delete the statement'); },
    });
  }
}
