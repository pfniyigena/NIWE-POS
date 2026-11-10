import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { FollowUp, FollowUpKind, InstallmentApiService, InstallmentPlanDetail, PayoffQuote, RescheduleLine } from '../../core/api/installment-api.service';
import { openPdfForPrint } from '../../core/download-file';
import { PaymentMethodOption } from '../../core/models';

/** Installments > a plan: the schedule, what's paid, record a payment, pay off early, reverse a payment, remind or e-mail the customer, log contacts, default / write off, cancel. */
@Component({
  selector: 'app-installment-plan-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>
      <ng-container *ngIf="d as x">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <h3 style="margin:0;">{{ x.plan.planNumber }}
            <span class="badge" [class.badge-success]="x.plan.status === 'COMPLETED'" [class.badge-warning]="x.plan.status === 'ACTIVE'" [class.badge-secondary]="x.plan.status === 'CANCELLED' || x.plan.status === 'WRITTEN_OFF'" [class.badge-danger]="x.plan.status === 'DEFAULTED'">{{ statusLabel(x.plan.status) | translate }}</span></h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-primary" type="button" (click)="pdf()">{{ 'Schedule (PDF)' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" (click)="statementPdf()">{{ 'Statement (PDF)' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" (click)="agreementPdf()">{{ 'Agreement (PDF)' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" (click)="closePanels(); showEmail = !showEmail">{{ 'E-mail statement' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="x.plan.status === 'ACTIVE'" [disabled]="busy" (click)="remind()">{{ 'Send reminder' | translate }}</button>
            <button class="btn btn-primary" type="button" *ngIf="isOpen()" (click)="openPayoff()">{{ 'Pay off early' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="isOpen()" (click)="startEdit()">{{ 'Edit schedule' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="x.plan.status === 'ACTIVE'" (click)="closePanels(); showDefault = !showDefault">{{ 'Mark defaulted' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="x.plan.status === 'DEFAULTED'" [disabled]="busy" (click)="restore()">{{ 'Back to active' | translate }}</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="isOpen()" (click)="closePanels(); showWriteOff = !showWriteOff">{{ 'Write off' | translate }}</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="isOpen()" (click)="closePanels(); showCancel = !showCancel">{{ 'Cancel plan' | translate }}</button>
            <a class="btn btn-link" routerLink="/admin/installments">{{ 'Back' | translate }}</a>
          </div>
        </div>

        <div class="alert alert-danger" *ngIf="x.plan.status === 'DEFAULTED'">{{ 'In default since {p0}' | translate: { p0: (x.terms.defaultedOn) } }}<span *ngIf="x.terms.defaultReason"> — {{ x.terms.defaultReason }}</span>{{ '. No more late fees or reminders are added; payments can still be recorded.' | translate }}</div>
        <div class="alert alert-info" *ngIf="x.plan.status === 'WRITTEN_OFF'">{{ 'Written off on {p0}: {p1} was booked as a bad debt' | translate: { p0: (x.terms.writtenOffOn), p1: (x.terms.writtenOffAmount | number: '1.0-2') } }}<span *ngIf="x.terms.writeOffReason"> — {{ x.terms.writeOffReason }}</span>.</div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showEmail" (ngSubmit)="emailStatement()">
          <strong class="mb-2">{{ 'E-mail the statement to {p0}' | translate: { p0: (x.plan.customerName) } }}</strong>
          <label style="display:flex; gap:0.4rem; align-items:center;" class="mb-2"><input type="checkbox" name="ia" [(ngModel)]="includeAgreement" /> {{ 'Also attach the agreement' | translate }}</label>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Send' | translate }}</button><button class="btn btn-link" type="button" (click)="showEmail = false">{{ 'Close' | translate }}</button></div>
          <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'Needs the customer\\'s e-mail address and e-mail set up under Settings > Email. The send is added to the collections log.' | translate }}</div>
        </form>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showPayoff && quote" (ngSubmit)="payoff()">
          <strong class="mb-2">{{ 'Pay off early' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Settles every unpaid instalment today. Part of the interest not yet earned on instalments that are not yet due can be waived.' | translate }}</div>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Interest waived on instalments not yet due (%)' | translate }}</label>
              <input class="form-control" type="number" min="0" max="100" step="1" style="width:10rem;" name="rb" [(ngModel)]="rebatePct" (ngModelChange)="refreshQuote()" /></div>
            <div class="form-group"><label class="form-label">{{ 'Method' | translate }}</label><select class="form-select" name="pm" [(ngModel)]="method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
            <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="pn" [(ngModel)]="note" /></div>
          </div>
          <table class="table-sm mb-2" style="max-width:26rem;">
            <tbody>
              <tr><td>{{ 'Principal left' | translate }}</td><td class="text-end">{{ quote.principalLeft | number: '1.0-2' }}</td></tr>
              <tr><td>{{ 'Interest left' | translate }}</td><td class="text-end">{{ quote.interestLeft | number: '1.0-2' }}</td></tr>
              <tr><td>{{ 'Late fees left' | translate }}</td><td class="text-end">{{ quote.feesLeft | number: '1.0-2' }}</td></tr>
              <tr><td>{{ 'Interest waived' | translate }}</td><td class="text-end">− {{ quote.rebate | number: '1.0-2' }}</td></tr>
              <tr><td><strong>{{ 'To pay now' | translate }}</strong></td><td class="text-end"><strong>{{ quote.amountToSettle | number: '1.0-2' }}</strong></td></tr>
            </tbody>
          </table>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy || !method">{{ 'Receive {p0} and close the plan' | translate: { p0: (quote.amountToSettle | number: '1.0-2') } }}</button><button class="btn btn-link" type="button" (click)="showPayoff = false">{{ 'Close' | translate }}</button></div>
        </form>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showDefault" (ngSubmit)="markDefaulted()">
          <strong class="mb-2">{{ 'Mark this plan as defaulted?' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The customer has stopped paying. No more late fees or reminders are added. Payments are still accepted, and you can put the plan back to active at any time.' | translate }}</div>
          <div class="flex gap-2 align-center"><input class="form-control" style="max-width:26rem;" name="dr" [placeholder]="'Reason (optional)' | translate" [(ngModel)]="reason" /><button class="btn btn-danger" type="submit" [disabled]="busy">{{ 'Mark defaulted' | translate }}</button></div>
        </form>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showWriteOff" (ngSubmit)="writeOff()">
          <strong class="mb-2">{{ 'Write off the unpaid balance?' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The unpaid principal ({p0}) is booked as a bad debt and the invoice shows nothing outstanding. Unpaid interest and late fees are dropped. This can\\'t be undone from here.' | translate: { p0: (unpaidPrincipal() | number: '1.0-2') } }}</div>
          <div class="flex gap-2 align-center"><input class="form-control" style="max-width:26rem;" name="wr" [placeholder]="'Reason (required)' | translate" [(ngModel)]="reason" required /><button class="btn btn-danger" type="submit" [disabled]="busy || !reason.trim()">{{ 'Write off {p0}' | translate: { p0: (unpaidPrincipal() | number: '1.0-2') } }}</button></div>
        </form>

        <div class="alert alert-info" *ngIf="x.terms.adjustmentNote" style="white-space:pre-line;">{{ x.terms.adjustmentNote }}</div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="edit" (ngSubmit)="saveEdit()">
          <strong class="mb-1">{{ 'Edit the unpaid instalments' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Change dates and amounts, add or remove instalments. What is already paid stays as it is. The principal must add up to {p0}; interest can\\'t go below what has been paid.' | translate: { p0: (editTotal | number: '1.0-2') } }}</div>
          <table class="table-sm mb-2">
            <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Due date' | translate }}</th><th class="text-end">{{ 'Principal' | translate }}</th><th class="text-end">{{ 'Interest' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let e of edit; let k = index">
              <td>{{ editFirstSeq + k }}</td>
              <td><app-date-picker [name]="'ed' + k" [(ngModel)]="e.dueDate"></app-date-picker></td>
              <td class="text-end"><input class="form-control" type="number" min="0" step="0.01" style="width:9rem; display:inline-block;" [name]="'ep' + k" [(ngModel)]="e.principalDue" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" step="0.01" style="width:8rem; display:inline-block;" [name]="'ei' + k" [(ngModel)]="e.interestDue" /></td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="removeRow(k)" [disabled]="edit.length < 2">{{ 'Remove' | translate }}</button></td>
            </tr></tbody>
            <tfoot><tr><td colspan="2"><strong>{{ 'Total' | translate }}</strong></td>
              <td class="text-end"><strong [style.color]="editDiff() === 0 ? '' : 'var(--danger-text, #b00020)'">{{ editSum() | number: '1.0-2' }}</strong></td>
              <td class="text-end">{{ editInterestSum() | number: '1.0-2' }}</td><td></td></tr></tfoot>
          </table>
          <div class="text-danger mb-2" *ngIf="editDiff() !== 0">{{ 'The principal is {p0} {p1}.' | translate: { p0: (editDiff() > 0 ? editDiff() : -editDiff() | number: '1.0-2'), p1: (editDiff() > 0 ? 'too high' : 'too low') } }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-primary" type="submit" [disabled]="busy || editDiff() !== 0">{{ 'Save schedule' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" (click)="addRow()">{{ '+ Add instalment' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" (click)="splitEvenly()">{{ 'Split principal evenly' | translate }}</button>
            <button class="btn btn-link" type="button" (click)="edit = null">{{ 'Cancel' | translate }}</button>
          </div>
        </form>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showCancel" (ngSubmit)="cancel()">
          <strong class="mb-2">{{ 'Cancel this plan?' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Whatever is still unpaid stays on the invoice as an ordinary debt. Unpaid interest and late fees are dropped.' | translate }}</div>
          <div class="flex gap-2 align-center"><input class="form-control" style="max-width:26rem;" name="cr" [placeholder]="'Reason (optional)' | translate" [(ngModel)]="cancelReason" />
            <button class="btn btn-danger" type="submit">{{ 'Cancel the plan' | translate }}</button></div>
        </form>

        <div class="card mb-3" style="padding:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><strong>{{ 'Customer:' | translate }}</strong> {{ x.plan.customerName }}</div>
            <div><strong>{{ 'Invoice:' | translate }}</strong> <a [routerLink]="['/admin/invoices', x.plan.invoiceId]">{{ x.plan.invoiceNumber }}</a> ({{ x.terms.invoiceTotal | number: '1.0-2' }})</div>
            <div *ngIf="x.terms.deposit > 0"><strong>{{ 'Deposit:' | translate }}</strong> {{ x.terms.deposit | number: '1.0-2' }}</div>
            <div><strong>{{ 'Terms:' | translate }}</strong> {{ x.plan.numberOfInstallments }} × {{ freq(x.terms.frequency) }}<span *ngIf="x.terms.interestMethod !== 'NONE'">{{ ', {p0}% a year ({p1})' | translate: { p0: (x.terms.interestRate), p1: (x.terms.interestMethod === 'FLAT' ? 'flat' : 'reducing balance') } }}</span></div>
            <div *ngIf="x.terms.lateFeeFixed > 0 || x.terms.lateFeePercent > 0"><strong>{{ 'Late fee:' | translate }}</strong> <span *ngIf="x.terms.lateFeeFixed > 0">{{ x.terms.lateFeeFixed | number: '1.0-2' }}</span><span *ngIf="x.terms.lateFeeFixed > 0 && x.terms.lateFeePercent > 0"> + </span><span *ngIf="x.terms.lateFeePercent > 0">{{ x.terms.lateFeePercent }}%</span> {{ 'after {p0} day(s)' | translate: { p0: (x.terms.graceDays) } }}</div>
          </div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="x.terms.notes">{{ x.terms.notes }}</div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="x.terms.cancelledOn">{{ 'Cancelled on {p0}' | translate: { p0: (x.terms.cancelledOn) } }}<span *ngIf="x.terms.cancelReason"> — {{ x.terms.cancelReason }}</span></div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Total payable' | translate }}</div><div class="stat-value">{{ x.plan.totalPayable | number: '1.0-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Paid' | translate }}</div><div class="stat-value">{{ x.plan.paid | number: '1.0-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Outstanding' | translate }}</div><div class="stat-value">{{ x.plan.outstanding | number: '1.0-2' }}</div></div>
          <div class="stat-tile" [class.warn]="x.plan.overdueAmount > 0"><div class="stat-label">{{ 'Overdue' | translate }}</div><div class="stat-value">{{ x.plan.overdueAmount | number: '1.0-2' }}</div></div>
        </div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="isOpen()" (ngSubmit)="pay()">
          <strong class="mb-2">{{ 'Record a payment' | translate }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0.01" step="0.01" style="width:10rem;" name="am" [(ngModel)]="amount" required />
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="x.plan.nextDueAmount">{{ 'Next instalment: {p0}' | translate: { p0: (x.plan.nextDueAmount | number: '1.0-2') } }}</div></div>
            <div class="form-group"><label class="form-label">{{ 'Method' | translate }}</label><select class="form-select" name="me" [(ngModel)]="method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
            <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="no" [(ngModel)]="note" /></div>
            <button class="btn btn-primary" type="submit" [disabled]="busy || !amount">{{ 'Record payment' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Goes to the oldest unpaid instalment first. Interest and late fees are settled before principal; the principal part is recorded as a payment on the invoice.' | translate }}</div>
        </form>

        <h4 class="mb-2">{{ 'Schedule' | translate }}</h4>
        <table class="table-sm mb-3">
          <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Due date' | translate }}</th><th class="text-end">{{ 'Principal' | translate }}</th><th class="text-end">{{ 'Interest' | translate }}</th><th class="text-end">{{ 'Late fee' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let i of x.installments">
            <td>{{ i.seq }}</td><td>{{ i.dueDate }}</td>
            <td class="text-end">{{ i.principalDue | number: '1.0-2' }}</td><td class="text-end">{{ i.interestDue | number: '1.0-2' }}</td>
            <td class="text-end">{{ i.lateFee | number: '1.0-2' }}<span class="text-muted" *ngIf="i.lateFeeWaived"> {{ 'waived' | translate }}</span></td>
            <td class="text-end">{{ i.totalDue | number: '1.0-2' }}</td><td class="text-end">{{ i.paid | number: '1.0-2' }}</td><td class="text-end">{{ i.balance | number: '1.0-2' }}</td>
            <td><span class="badge" [class.badge-success]="i.state === 'PAID'" [class.badge-danger]="i.state === 'OVERDUE'" [class.badge-warning]="i.state === 'PARTIAL'" [class.badge-secondary]="i.state === 'PENDING'">{{ i.state }}</span>
              <span class="text-muted" style="font-size:0.75rem;" *ngIf="i.state === 'OVERDUE'"> {{ '{p0} day(s)' | translate: { p0: (i.daysOverdue) } }}</span></td>
            <td><button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="isOpen() && i.lateFee > 0" (click)="waive(i.seq)">{{ 'Waive late fee' | translate }}</button></td>
          </tr></tbody>
        </table>

        <h4 class="mb-2">{{ 'Payments' | translate }}</h4>
        <table class="table-sm">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Receipt' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th class="text-end">{{ 'Principal' | translate }}</th><th class="text-end">{{ 'Interest and fees' | translate }}</th><th>{{ 'Method' | translate }}</th><th>{{ 'Recorded by' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
          <tbody>
            <ng-container *ngFor="let p of x.payments">
              <tr [style.opacity]="p.reversed ? 0.55 : 1">
                <td>{{ p.paidAt | date: 'medium' }}</td><td>{{ p.receiptNumber || '—' }}</td><td class="text-end" [style.text-decoration]="p.reversed ? 'line-through' : ''">{{ p.amount | number: '1.0-2' }}</td>
                <td class="text-end">{{ p.principalPart | number: '1.0-2' }}</td><td class="text-end">{{ p.chargesPart | number: '1.0-2' }}</td>
                <td>{{ p.method || '—' }}</td><td>{{ p.recordedByName || '—' }}</td><td>{{ p.note || '—' }}</td>
                <td>
                  <span class="badge badge-secondary" *ngIf="p.reversed" [title]="'Reversed by ' + (p.reversedByName || '—') + ': ' + (p.reverseReason || '')">{{ 'Reversed' | translate }}</span>
                  <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="!p.reversed && x.plan.status !== 'WRITTEN_OFF'" (click)="startReverse(p.id)">{{ 'Reverse' | translate }}</button>
                </td>
              </tr>
              <tr *ngIf="reversingId === p.id"><td colspan="9">
                <form class="flex gap-2 align-center" style="flex-wrap:wrap;" (ngSubmit)="reverse(p.id)">
                  <span>{{ 'Why is {p0} ({p1}) being taken back?' | translate: { p0: (p.receiptNumber), p1: (p.amount | number: '1.0-2') } }}</span>
                  <input class="form-control" style="max-width:24rem;" name="rv{{ p.id }}" [placeholder]="'Reason (required)' | translate" [(ngModel)]="reverseReason" required />
                  <button class="btn btn-danger btn-sm" type="submit" [disabled]="busy || !reverseReason.trim()">{{ 'Reverse payment' | translate }}</button>
                  <button class="btn btn-link btn-sm" type="button" (click)="reversingId = null">{{ 'Cancel' | translate }}</button>
                </form>
                <div class="text-muted" style="font-size:0.8rem;">{{ 'The invoice payment is undone with a negative entry (nothing is deleted) and the books get the mirror posting.' | translate }}</div>
              </td></tr>
            </ng-container>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!x.payments.length">{{ 'No payments recorded through this plan yet. Payments recorded directly on the invoice also count toward the schedule.' | translate }}</p>

        <h4 class="mb-2 mt-3">{{ 'Collections log' | translate }}</h4>
        <form class="card mb-2" style="padding:1rem;" (ngSubmit)="addFollowUp()">
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Contact' | translate }}</label>
              <select class="form-select" name="fk" [(ngModel)]="fuKind"><option value="CALL">{{ 'Phone call' | translate }}</option><option value="VISIT">{{ 'Visit' | translate }}</option><option value="PROMISE">{{ 'Promise to pay' | translate }}</option><option value="NOTE">{{ 'Note' | translate }}</option></select></div>
            <div class="form-group" *ngIf="fuKind === 'PROMISE'"><label class="form-label">{{ 'Promised for' | translate }}</label><app-date-picker name="fpd" [(ngModel)]="fuDate"></app-date-picker></div>
            <div class="form-group" *ngIf="fuKind === 'PROMISE'"><label class="form-label">{{ 'Amount (optional)' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" style="width:9rem;" name="fpa" [(ngModel)]="fuAmount" /></div>
            <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'What was said' | translate }}</label><input class="form-control" name="fn" [(ngModel)]="fuNote" [placeholder]="fuKind === 'PROMISE' ? 'Optional' : 'Required'" /></div>
            <button class="btn btn-primary" type="submit" [disabled]="busy || (fuKind === 'PROMISE' ? !fuDate : !fuNote.trim())">{{ 'Add to log' | translate }}</button>
          </div>
        </form>
        <table class="table-sm" *ngIf="followUps.length">
          <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Note' | translate }}</th><th>{{ 'Promise' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let f of followUps">
            <td>{{ f.at | date: 'medium' }}</td><td>{{ kindLabel(f.kind) }}</td><td style="white-space:pre-line;">{{ f.note || '—' }}</td>
            <td><span *ngIf="f.promisedOn">{{ 'by {p0}' | translate: { p0: (f.promisedOn) } }}<span *ngIf="f.promisedAmount"> ({{ f.promisedAmount | number: '1.0-2' }})</span></span><span *ngIf="!f.promisedOn">—</span></td>
            <td>{{ f.byName || '—' }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!followUps.length">{{ 'No contact has been logged for this plan yet.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class InstallmentPlanDetailComponent implements OnInit {
  d: InstallmentPlanDetail | null = null;
  methods: PaymentMethodOption[] = [];
  amount: number | null = null;
  method = '';
  note = '';
  showCancel = false;
  edit: RescheduleLine[] | null = null;
  editFirstSeq = 1;
  editTotal = 0;
  cancelReason = '';
  showEmail = false;
  includeAgreement = true;
  showPayoff = false;
  quote: PayoffQuote | null = null;
  rebatePct: number | null = null;
  showDefault = false;
  showWriteOff = false;
  reason = '';
  reversingId: number | null = null;
  reverseReason = '';
  followUps: FollowUp[] = [];
  fuKind: FollowUpKind = 'CALL';
  fuNote = '';
  fuDate = '';
  fuAmount: number | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  private id = 0;

  constructor(private route: ActivatedRoute, private api: InstallmentApiService, private paymentApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.paymentApi.listActive().subscribe((m) => {
      this.methods = m;
      this.method = m[0]?.code ?? '';
    });
    this.api.plan(this.id).subscribe({ next: (d) => this.set(d), error: (e) => (this.error = e?.error?.message ?? 'Could not load the plan') });
    this.loadFollowUps();
  }

  private loadFollowUps(): void {
    this.api.followUps(this.id).subscribe({ next: (f) => (this.followUps = f), error: () => (this.followUps = []) });
  }

  private set(d: InstallmentPlanDetail): void {
    this.d = d;
    this.amount = d.plan.nextDueAmount ?? null;
  }

  private run(call: import('rxjs').Observable<InstallmentPlanDetail>, ok: string): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (d) => {
        this.set(d);
        this.info = ok;
        this.busy = false;
        this.closePanels();
        this.reversingId = null;
        this.reason = '';
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'That did not work';
        this.busy = false;
      },
    });
  }

  // ---- v1.18.0: status, payoff, reversal, default, write-off, log ----

  isOpen(): boolean {
    return !!this.d && (this.d.plan.status === 'ACTIVE' || this.d.plan.status === 'DEFAULTED');
  }

  statusLabel(s: string): string {
    return { WRITTEN_OFF: 'WRITTEN OFF' }[s] ?? s;
  }

  kindLabel(k: FollowUpKind): string {
    return { CALL: 'Phone call', VISIT: 'Visit', EMAIL: 'E-mail', PROMISE: 'Promise to pay', NOTE: 'Note' }[k] ?? k;
  }

  closePanels(): void {
    this.showCancel = false;
    this.showEmail = false;
    this.showPayoff = false;
    this.showDefault = false;
    this.showWriteOff = false;
  }

  unpaidPrincipal(): number {
    return this.d ? Math.round(this.d.installments.reduce((a, r) => a + r.principalLeft, 0) * 100) / 100 : 0;
  }

  openPayoff(): void {
    this.closePanels();
    this.error = null;
    this.info = null;
    this.api.payoffQuote(this.id, null).subscribe({
      next: (q) => {
        this.quote = q;
        this.rebatePct = q.rebatePercent;
        this.showPayoff = true;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not work out the payoff'),
    });
  }

  refreshQuote(): void {
    if (this.rebatePct === null || this.rebatePct < 0 || this.rebatePct > 100) return;
    this.api.payoffQuote(this.id, this.rebatePct).subscribe({ next: (q) => (this.quote = q), error: (e) => (this.error = e?.error?.message ?? 'Could not work out the payoff') });
  }

  payoff(): void {
    if (!this.quote) return;
    this.run(
      this.api.payoff(this.id, { method: this.method, note: this.note || null, rebatePercent: this.rebatePct, expectedAmount: this.quote.amountToSettle }),
      'The plan has been paid off.'
    );
    this.note = '';
  }

  startReverse(paymentId: number): void {
    this.reversingId = paymentId;
    this.reverseReason = '';
  }

  reverse(paymentId: number): void {
    this.run(this.api.reversePayment(this.id, paymentId, this.reverseReason.trim()), 'Payment reversed.');
  }

  markDefaulted(): void {
    this.run(this.api.markDefaulted(this.id, this.reason.trim() || null), 'The plan is marked as defaulted.');
  }

  restore(): void {
    this.run(this.api.restore(this.id), 'The plan is active again.');
  }

  writeOff(): void {
    if (!confirm(`Write off ${this.unpaidPrincipal()} as a bad debt?`)) return;
    this.run(this.api.writeOff(this.id, this.reason.trim()), 'The balance was written off.');
  }

  addFollowUp(): void {
    this.busy = true;
    this.error = null;
    this.api
      .addFollowUp(this.id, {
        kind: this.fuKind,
        note: this.fuNote.trim() || null,
        promisedOn: this.fuKind === 'PROMISE' ? this.fuDate || null : null,
        promisedAmount: this.fuKind === 'PROMISE' ? this.fuAmount : null,
      })
      .subscribe({
        next: (f) => {
          this.followUps = f;
          this.fuNote = '';
          this.fuAmount = null;
          this.busy = false;
        },
        error: (e) => {
          this.error = e?.error?.message ?? 'Could not save the note';
          this.busy = false;
        },
      });
  }

  statementPdf(): void {
    this.api.statementPdf(this.id).subscribe({ next: (b) => openPdfForPrint(b), error: () => (this.error = 'Could not create the statement') });
  }

  agreementPdf(): void {
    this.api.agreementPdf(this.id).subscribe({ next: (b) => openPdfForPrint(b), error: () => (this.error = 'Could not create the agreement') });
  }

  emailStatement(): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.emailStatement(this.id, this.includeAgreement).subscribe({
      next: (r) => {
        this.info = `Statement sent to ${r.sentTo}.`;
        this.busy = false;
        this.showEmail = false;
        this.loadFollowUps();
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not send the statement';
        this.busy = false;
      },
    });
  }

  // ---- editing the schedule ----

  startEdit(): void {
    if (!this.d) return;
    const rows = this.d.installments;
    let first = 0;
    while (first < rows.length && rows[first].state === 'PAID') first++;
    if (first >= rows.length) {
      this.error = 'Every instalment is already paid.';
      return;
    }
    const open = rows.slice(first);
    this.editFirstSeq = first + 1;
    this.edit = open.map((r) => ({ dueDate: r.dueDate, principalDue: r.principalDue, interestDue: r.interestDue }));
    this.editTotal = this.round2(open.reduce((a, r) => a + r.principalDue, 0));
    this.error = null;
    this.info = null;
  }

  editSum(): number {
    return this.round2((this.edit ?? []).reduce((a, r) => a + (Number(r.principalDue) || 0), 0));
  }

  editInterestSum(): number {
    return this.round2((this.edit ?? []).reduce((a, r) => a + (Number(r.interestDue) || 0), 0));
  }

  /** New total minus the required total (0 = it adds up). */
  editDiff(): number {
    return this.round2(this.editSum() - this.editTotal);
  }

  addRow(): void {
    if (!this.edit) return;
    const last = this.edit[this.edit.length - 1];
    const d = new Date(last.dueDate + 'T00:00:00Z');
    d.setUTCMonth(d.getUTCMonth() + 1);
    this.edit.push({ dueDate: d.toISOString().slice(0, 10), principalDue: 0, interestDue: 0 });
  }

  removeRow(k: number): void {
    if (this.edit && this.edit.length > 1) this.edit.splice(k, 1);
  }

  /** Shares the required principal out evenly; the last instalment takes the rounding. */
  splitEvenly(): void {
    if (!this.edit || !this.d) return;
    const n = this.edit.length;
    const scale = this.d.terms.wholeUnits ? 0 : 2;
    const f = Math.pow(10, scale);
    const each = Math.floor((this.editTotal * f) / n) / f;
    let left = this.editTotal;
    this.edit.forEach((r, k) => {
      const v = k === n - 1 ? this.round2(left) : each;
      r.principalDue = v;
      left = this.round2(left - v);
    });
  }

  saveEdit(): void {
    if (!this.edit || this.editDiff() !== 0) return;
    this.run(
      this.api.reschedule(
        this.id,
        this.edit.map((r) => ({ dueDate: r.dueDate, principalDue: Number(r.principalDue) || 0, interestDue: Number(r.interestDue) || 0 }))
      ),
      'Schedule saved.'
    );
    this.edit = null;
  }

  private round2(v: number): number {
    return Math.round(v * 100) / 100;
  }

  pay(): void {
    if (!this.amount) return;
    this.run(this.api.pay(this.id, { amount: this.amount, method: this.method, note: this.note || null }), 'Payment recorded.');
    this.note = '';
  }

  waive(seq: number): void {
    if (!confirm(`Waive the late fee on instalment ${seq}?`)) return;
    this.run(this.api.waiveFee(this.id, seq), 'Late fee waived.');
  }

  cancel(): void {
    this.run(this.api.cancel(this.id, this.cancelReason || null), 'Plan cancelled.');
  }

  remind(): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.remind(this.id).subscribe({
      next: (r) => {
        this.info = `Reminder sent to ${r.sentTo}.`;
        this.busy = false;
        this.loadFollowUps();
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not send the reminder';
        this.busy = false;
      },
    });
  }

  pdf(): void {
    this.api.schedulePdf(this.id).subscribe({ next: (b) => openPdfForPrint(b), error: () => (this.error = 'Could not create the PDF') });
  }

  freq(f: string): string {
    return { WEEKLY: 'week', BIWEEKLY: '2 weeks', MONTHLY: 'month', QUARTERLY: '3 months' }[f] ?? f;
  }
}
