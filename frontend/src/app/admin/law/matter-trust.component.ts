import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { showPdf } from '../../core/open-pdf';
import { LawApiService, MatterTrust, TRUST_TYPE_LABEL, TrustTx } from '../../core/api/law-api.service';

/**
 * The Client money tab of a matter: the money the firm holds for this client in its trust account, and every movement of it. Money in is
 * recorded at once; a payout or a fee transfer to an issued invoice is asked for here and approved by another person (the lead lawyer or an
 * admin). The server decides who may do what; the buttons follow the flags it sends.
 */
@Component({
  selector: 'app-law-matter-trust',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, DatePickerComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="info">{{ info }}</div>
    <div *ngIf="!t && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

    <ng-container *ngIf="t">
      <div class="card" style="padding:1rem;" *ngIf="!t.enabled">
        <strong>{{ 'The client money (trust) account is switched off.' | translate }}</strong>
        <div class="text-muted">An admin can switch it on in Law Firm &gt; Settings. Until then no client money can be recorded here.</div>
      </div>

      <ng-container *ngIf="t.enabled">
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.6rem 0.9rem; min-width:10rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Held for {p0}' | translate: { p0: (t.clientName) } }}</div><strong style="font-size:1.2rem;">{{ t.balance | number: '1.0-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'in the trust account, not the firm\\'s money' | translate }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:10rem;" *ngIf="t.pending > 0"><div class="text-muted" style="font-size:0.8rem;">{{ 'Waiting to be paid out' | translate }}</div><strong>{{ t.pending | number: '1.0-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ 'available now {p0}' | translate: { p0: (t.available | number: '1.0-2') } }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:10rem;" *ngIf="t.bankName || t.accountNumber"><div class="text-muted" style="font-size:0.8rem;">{{ 'Trust account' | translate }}</div><strong>{{ t.bankName }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ t.accountNumber }}</div></div>
        </div>

        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="t.canRecord">
          <button class="btn" [class.btn-primary]="mode === 'deposit'" [class.btn-outline-primary]="mode !== 'deposit'" *ngIf="t.canDeposit" (click)="open('deposit')">{{ 'Receive money' | translate }}</button>
          <button class="btn" [class.btn-primary]="mode === 'payout'" [class.btn-outline-primary]="mode !== 'payout'" (click)="open('payout')">{{ 'Pay out…' | translate }}</button>
          <button class="btn" [class.btn-primary]="mode === 'fee'" [class.btn-outline-primary]="mode !== 'fee'" (click)="open('fee')" [disabled]="!t.invoices.length" [title]="t.invoices.length ? '' : 'No issued invoice from this matter is waiting to be paid'">{{ 'Pay an invoice from trust…' | translate }}</button>
        </div>

        <!-- receive -->
        <form class="card mb-3" style="padding:1rem;" *ngIf="mode === 'deposit'" (ngSubmit)="deposit()">
          <h4 class="mb-2">{{ 'Receive client money' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="da" [(ngModel)]="dep.amount" /></div>
            <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Date received' | translate }}</label><app-date-picker name="dd" [(ngModel)]="dep.date"></app-date-picker></div>
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'How paid' | translate }}</label>
              <select class="form-select" name="dm" [(ngModel)]="dep.method"><option *ngFor="let m of methods" [value]="m">{{ m }}</option></select></div>
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Reference' | translate }} <span class="text-muted">{{ '(cheque / transfer no.)' | translate }}</span></label><input class="form-control" name="dr" [(ngModel)]="dep.reference" /></div>
            <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Paid in by' | translate }}</label><input class="form-control" name="dp" [(ngModel)]="dep.payerName" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">{{ 'What it is for' | translate }}</label><input class="form-control" name="du" [(ngModel)]="dep.purpose" /></div>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Only money that belongs to the client goes here. Never put the firm\\'s own money or its fees in the trust account.' | translate }}</div>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Record the deposit' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
        </form>

        <!-- payout -->
        <form class="card mb-3" style="padding:1rem;" *ngIf="mode === 'payout'" (ngSubmit)="payout()">
          <h4 class="mb-2">{{ 'Pay out client money' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Amount' | translate }} <span class="text-muted">{{ '(up to {p0})' | translate: { p0: (t.available | number: '1.0-2') } }}</span></label><input class="form-control" type="number" min="0" step="0.01" name="pa" [(ngModel)]="pay.amount" /></div>
            <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="pd" [(ngModel)]="pay.date"></app-date-picker></div>
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'How paid' | translate }}</label>
              <select class="form-select" name="pm" [(ngModel)]="pay.method"><option *ngFor="let m of methods" [value]="m">{{ m }}</option></select></div>
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Reference' | translate }}</label><input class="form-control" name="pr" [(ngModel)]="pay.reference" /></div>
            <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Paid to' | translate }}</label><input class="form-control" name="pp" [(ngModel)]="pay.payeeName" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">{{ 'What it is for' | translate }}</label><input class="form-control" name="pu" [(ngModel)]="pay.purpose" /></div>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The money only leaves the trust account after another person (the matter\\'s lead lawyer or an admin) approves it. You cannot pay out more than the client has.' | translate }}</div>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Ask for approval' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
        </form>

        <!-- fee transfer -->
        <form class="card mb-3" style="padding:1rem;" *ngIf="mode === 'fee'" (ngSubmit)="fee()">
          <h4 class="mb-2">{{ 'Pay an invoice from the client\\'s money' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:16rem;"><label class="form-label">{{ 'Invoice' | translate }}</label>
              <select class="form-select" name="fi" [(ngModel)]="ft.invoiceId" (ngModelChange)="pickInvoice()">
                <option [ngValue]="null">{{ 'Choose an invoice…' | translate }}</option>
                <option *ngFor="let i of t.invoices" [ngValue]="i.invoiceId">{{ '{p0} — owes {p1}' | translate: { p0: (i.invoiceNumber), p1: (i.outstanding | number: '1.0-2') } }}<span *ngIf="i.waiting"> {{ '({p0} waiting)' | translate: { p0: (i.waiting | number: '1.0-2') } }}</span></option>
              </select></div>
            <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="fa" [(ngModel)]="ft.amount" /></div>
          </div>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The invoice is marked paid and the client\\'s balance goes down when another person (the matter\\'s lead lawyer or an admin) approves. It cannot be more than the invoice still owes or than the client has.' | translate }}</div>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy || !ft.invoiceId">{{ 'Ask for approval' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
        </form>

        <!-- decide -->
        <div class="card mb-3" style="padding:1rem; border-left:4px solid #e67e22;" *ngIf="deciding">
          <h4 class="mb-2">{{ deciding.kind === 'approve' ? ('Approve' | translate) : deciding.kind === 'reject' ? ('Reject' | translate) : ('Take the deposit back' | translate) }} {{ deciding.tx.number }}</h4>
          <div class="mb-2">{{ '{p0} of' | translate: { p0: (typeLabel(deciding.tx)) } }} <strong>{{ deciding.tx.amount | number: '1.0-2' }}</strong><span *ngIf="deciding.tx.partyName"> — {{ deciding.tx.partyName }}</span><span *ngIf="deciding.tx.invoiceNumber"> {{ '— invoice {p0}' | translate: { p0: (deciding.tx.invoiceNumber) } }}</span>{{ ', asked by {p0}.' | translate: { p0: (deciding.tx.requestedByName) } }}</div>
          <div class="form-group mb-2"><label class="form-label">{{ deciding.kind === 'approve' ? 'Note (optional)' : deciding.kind === 'reject' ? ('Why are you rejecting it?' | translate) : ('Why is it being taken back? (for example: the cheque bounced)' | translate) }}</label>
            <input class="form-control" name="dn" [(ngModel)]="decisionNote" /></div>
          <div class="flex gap-2">
            <button class="btn btn-primary" (click)="decide()" [disabled]="busy || (deciding.kind !== 'approve' && !decisionNote.trim())">{{ deciding.kind === 'approve' ? 'Approve and pay' : deciding.kind === 'reject' ? ('Reject' | translate) : ('Take it back' | translate) }}</button>
            <button class="btn btn-outline-secondary" (click)="deciding = null">{{ 'Cancel' | translate }}</button>
          </div>
        </div>

        <!-- statement -->
        <div class="card mb-3" style="padding:0.8rem 1rem;" *ngIf="t.transactions.length">
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <strong>{{ 'Client statement' | translate }}</strong>
            <span class="text-muted">{{ 'from' | translate }}</span><app-date-picker name="sf" [(ngModel)]="stFrom"></app-date-picker>
            <span class="text-muted">{{ 'to' | translate }}</span><app-date-picker name="st" [(ngModel)]="stTo"></app-date-picker>
            <button class="btn btn-outline-secondary tr-statement" type="button" (click)="statement()" [disabled]="busy">{{ 'Print statement' | translate }}</button>
            <button class="btn btn-outline-primary tr-statement-mail" type="button" *ngIf="t.canRecord" (click)="emailStatement()" [disabled]="busy">{{ 'E-mail to the client' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Leave the dates empty for the whole history up to today.' | translate }}</div>
        </div>

        <!-- ledger -->
        <div class="card" style="padding:1rem; overflow-x:auto;">
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'No.' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Details' | translate }}</th><th class="text-end">{{ 'In' | translate }}</th><th class="text-end">{{ 'Out' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let x of t.transactions" [style.opacity]="x.status === 'REJECTED' || x.status === 'CANCELLED' ? 0.55 : 1">
                <td data-label="Date">{{ x.txDate | date: 'd MMM y' }}</td>
                <td data-label="No.">{{ x.number }}</td>
                <td data-label="What">{{ typeLabel(x) }}
                  <span class="badge" *ngIf="x.status === 'PENDING'" style="background:#e67e22; color:#fff;">{{ 'waiting for approval' | translate }}</span>
                  <span class="badge" *ngIf="x.status === 'REJECTED'" style="background:#c0392b; color:#fff;">{{ 'rejected' | translate }}</span>
                  <span class="badge" *ngIf="x.status === 'CANCELLED'" style="background:#7f8c8d; color:#fff;">{{ 'cancelled' | translate }}</span>
                  <span class="badge" *ngIf="x.reversed" style="background:#7f8c8d; color:#fff;">{{ 'taken back' | translate }}</span></td>
                <td data-label="Details">
                  <div *ngIf="x.partyName">{{ x.partyName }}<span class="text-muted" *ngIf="x.method"> · {{ x.method }}</span><span class="text-muted" *ngIf="x.reference"> · {{ x.reference }}</span></div>
                  <div *ngIf="x.invoiceNumber">{{ 'Invoice {p0}' | translate: { p0: (x.invoiceNumber) } }}</div>
                  <div class="text-muted" style="font-size:0.8rem;" *ngIf="x.purpose">{{ x.purpose }}</div>
                  <div class="text-muted" style="font-size:0.8rem;">{{ 'by {p0}' | translate: { p0: (x.requestedByName) } }}<span *ngIf="x.decidedByName && x.type !== 'DEPOSIT_REVERSAL'"> {{ '· {p0} by {p1}' | translate: { p0: (x.status === 'POSTED' ? 'approved' : x.status === 'REJECTED' ? 'rejected' : 'cancelled'), p1: (x.decidedByName) } }}</span><span *ngIf="x.decisionNote"> — {{ x.decisionNote }}</span></div>
                </td>
                <td class="text-end" data-label="In">{{ x.type === 'DEPOSIT' && x.status === 'POSTED' ? (x.amount | number: '1.0-2') : '' }}</td>
                <td class="text-end" data-label="Out">{{ x.type !== 'DEPOSIT' && x.status === 'POSTED' ? (x.amount | number: '1.0-2') : '' }}<span *ngIf="x.status === 'PENDING'" class="text-muted">({{ x.amount | number: '1.0-2' }})</span></td>
                <td class="text-end" data-label="Balance"><strong>{{ x.balanceAfter != null ? (x.balanceAfter | number: '1.0-2') : '' }}</strong></td>
                <td style="white-space:nowrap;">
                  <button class="btn btn-sm btn-outline-secondary tr-receipt" *ngIf="x.status === 'POSTED' && !x.reversed" (click)="receipt(x)">{{ x.type === 'DEPOSIT' ? ('Receipt' | translate) : ('Voucher' | translate) }}</button>
                  <button class="btn btn-sm btn-primary" *ngIf="x.canApprove" (click)="ask(x, 'approve')">{{ 'Approve' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" *ngIf="x.status === 'PENDING' && x.canCancel && !x.canApprove" (click)="cancel(x)">{{ 'Cancel' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" *ngIf="x.canApprove" (click)="ask(x, 'reject')">{{ 'Reject' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="x.canReverse" (click)="ask(x, 'reverse')">{{ 'Take back' | translate }}</button>
                </td>
              </tr>
              <tr *ngIf="!t.transactions.length"><td colspan="8" class="text-muted">{{ 'No client money has been recorded for this matter.' | translate }}</td></tr>
            </tbody>
          </table>
          <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Entries are never edited or deleted. A mistake is corrected with a "take back" entry that stays on the record. Check the bar\\'s or the firm\\'s own rules for client money with your accountant.' | translate }}</div>
        </div>
      </ng-container>
    </ng-container>
  `,
})
export class MatterTrustComponent implements OnChanges {
  @Input({ required: true }) matterId!: number;
  @Output() changed = new EventEmitter<void>();

  t: MatterTrust | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  mode: 'deposit' | 'payout' | 'fee' | null = null;
  deciding: { tx: TrustTx; kind: 'approve' | 'reject' | 'reverse' } | null = null;
  decisionNote = '';
  stFrom: string | null = null;
  stTo: string | null = null;
  methods = ['Bank transfer', 'Cheque', 'Cash', 'Mobile money', 'Other'];

  dep = this.blankDeposit();
  pay = this.blankPayout();
  ft: { invoiceId: number | null; amount: number | null } = { invoiceId: null, amount: null };

  constructor(private api: LawApiService) {}

  private blankDeposit() { return { amount: null as number | null, date: null as string | null, method: 'Bank transfer', reference: '', payerName: '', purpose: '' }; }
  private blankPayout() { return { amount: null as number | null, date: null as string | null, method: 'Bank transfer', reference: '', payeeName: '', purpose: '' }; }

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.api.matterTrust(this.matterId).subscribe({
      next: (t) => { this.t = t; this.error = null; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the client money'),
    });
  }

  receipt(x: TrustTx): void {
    this.error = null;
    showPdf(this.api.trustReceiptPdf(x.id), `${x.number}.pdf`, (e) => (this.error = e));
  }

  statement(): void {
    this.error = null;
    this.info = null;
    showPdf(this.api.trustStatementPdf(this.matterId, this.stFrom, this.stTo), 'trust-statement.pdf', (e) => (this.error = e));
  }

  emailStatement(): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.trustEmailStatement(this.matterId).subscribe({
      next: (r) => { this.busy = false; this.info = r.message; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'The statement could not be sent'; },
    });
  }

  typeLabel(x: TrustTx): string { return TRUST_TYPE_LABEL[x.type] ?? x.type; }

  open(m: 'deposit' | 'payout' | 'fee'): void {
    this.mode = this.mode === m ? null : m;
    this.deciding = null;
    this.info = null;
    this.error = null;
  }

  pickInvoice(): void {
    const i = this.t?.invoices.find((x) => x.invoiceId === this.ft.invoiceId);
    this.ft.amount = i && this.t ? Math.max(0, Math.min(i.outstanding - i.waiting, this.t.available)) : null;
  }

  private run(call: import('rxjs').Observable<MatterTrust>, done: string, after?: () => void): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (t) => { this.busy = false; this.t = t; this.info = done; this.mode = null; this.deciding = null; this.decisionNote = ''; after?.(); this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; },
    });
  }

  deposit(): void {
    this.run(this.api.trustDeposit(this.matterId, { amount: this.dep.amount, date: this.dep.date, method: this.dep.method, reference: this.dep.reference.trim() || null,
      payerName: this.dep.payerName.trim() || null, purpose: this.dep.purpose.trim() || null }), 'Deposit recorded.', () => (this.dep = this.blankDeposit()));
  }
  payout(): void {
    this.run(this.api.trustPayout(this.matterId, { amount: this.pay.amount, date: this.pay.date, method: this.pay.method, reference: this.pay.reference.trim() || null,
      payeeName: this.pay.payeeName.trim(), purpose: this.pay.purpose.trim() }), 'Sent for approval. The money stays in the trust account until another person approves.', () => (this.pay = this.blankPayout()));
  }
  fee(): void {
    if (!this.ft.invoiceId) return;
    this.run(this.api.trustFeeTransfer(this.matterId, { invoiceId: this.ft.invoiceId, amount: this.ft.amount, purpose: null }),
      'Sent for approval. The invoice is paid when another person approves.', () => (this.ft = { invoiceId: null, amount: null }));
  }
  ask(tx: TrustTx, kind: 'approve' | 'reject' | 'reverse'): void { this.deciding = { tx, kind }; this.decisionNote = ''; this.mode = null; this.info = null; this.error = null; }
  decide(): void {
    if (!this.deciding) return;
    const { tx, kind } = this.deciding;
    const note = this.decisionNote.trim();
    if (kind === 'approve') this.run(this.api.trustApprove(tx.id, note || null), `${tx.number} approved and paid.`);
    else if (kind === 'reject') this.run(this.api.trustReject(tx.id, note), `${tx.number} rejected.`);
    else this.run(this.api.trustReverse(tx.id, note), `${tx.number} taken back.`);
  }
  cancel(tx: TrustTx): void { if (confirm('Cancel this request?')) this.run(this.api.trustCancel(tx.id), `${tx.number} cancelled.`); }
}
