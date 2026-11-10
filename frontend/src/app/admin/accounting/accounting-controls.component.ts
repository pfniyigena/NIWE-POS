import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { Account } from '../../core/models';
import { AccountApiService } from '../../core/api/account-api.service';
import {
  AccountingControlsApiService, LockState, MoneyAccount, OpeningLinePayload, OpeningState, ReconDetail, ReconRow,
} from '../../core/api/accounting-controls-api.service';

interface OpeningDraftLine { accountId: number | null; debit: number | null; credit: number | null; }

/**
 * Accounting > Controls. Three things that keep the books trustworthy: the date the books are closed up to, matching a bank statement to the ledger (bank reconciliation),
 * and the opening balances of the ledger on the day the business started using the system.
 */
@Component({
  selector: 'app-accounting-controls',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'Accounting controls' | translate }}</h3>
      <div class="alert alert-danger ctl-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success ctl-done" *ngIf="done">{{ done }}</div>

      <div class="flex gap-2 mb-3 ctl-tabs" style="flex-wrap:wrap;">
        <button type="button" class="btn btn-sm ctl-tab-lock" [ngClass]="tab === 'lock' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('lock')">{{ 'Close the books' | translate }}</button>
        <button type="button" class="btn btn-sm ctl-tab-bank" [ngClass]="tab === 'bank' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('bank')">{{ 'Bank reconciliation' | translate }}</button>
        <button type="button" class="btn btn-sm ctl-tab-open" [ngClass]="tab === 'open' ? 'btn-primary' : 'btn-outline-secondary'" (click)="show('open')">{{ 'Opening balances' | translate }}</button>
      </div>

      <!-- ================= Close the books ================= -->
      <div *ngIf="tab === 'lock' && lock">
        <div class="card mb-3" style="padding:1rem;">
          <div class="ctl-lock-status" *ngIf="lock.lockDate">
            <strong>{{ 'The books are closed up to {p0}.' | translate: { p0: (lock.lockDate) } }}</strong>
            <span class="text-muted"> {{ 'Nothing dated on or before that day can be recorded: invoices, bills, expenses, journals, imports. Voiding or correcting something still works, because that is recorded today.' | translate }}</span>
          </div>
          <div class="ctl-lock-status" *ngIf="!lock.lockDate"><strong>{{ 'The books are open.' | translate }}</strong>
            <span class="text-muted"> {{ 'Anything can be recorded on any past date. Close them once a period is final (for example after the March accounts are approved).' | translate }}</span></div>

          <div *ngIf="isAdmin" style="margin-top:1rem;">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Close the books up to (and including)' | translate }}</label>
                <input class="form-control ctl-lock-date" type="date" [(ngModel)]="lockDate" name="lockDate" [max]="yesterday" /></div>
              <div class="form-group" style="margin:0; flex:1; min-width:14rem;"><label class="form-label">{{ 'Why' | translate }}</label>
                <input class="form-control ctl-lock-reason" type="text" [(ngModel)]="lockReason" name="lockReason" [placeholder]="'For example: March accounts approved' | translate" /></div>
              <button class="btn btn-primary ctl-lock-save" type="button" [disabled]="busy || !lockDate" (click)="saveLock(false)">{{ lock.lockDate ? ('Move the date' | translate) : ('Close the books' | translate) }}</button>
              <button class="btn btn-outline-danger ctl-lock-open" type="button" *ngIf="lock.lockDate" [disabled]="busy" (click)="saveLock(true)">{{ 'Reopen the books' | translate }}</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem; margin-top:0.4rem;">{{ 'The date must be before today. Every change is logged below with your reason.' | translate }}</div>
          </div>
          <div class="text-muted" *ngIf="!isAdmin" style="margin-top:0.8rem; font-size:0.85rem;">{{ 'Only an admin can close or reopen the books.' | translate }}</div>
        </div>

        <div class="card" style="padding:1rem;" *ngIf="lock.history.length">
          <strong class="mb-2" style="display:block;">{{ 'Changes' | translate }}</strong>
          <table class="ctl-lock-history">
            <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'By' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Reason' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let h of lock.history">
                <td [attr.data-label]="'When'">{{ h.at | date: 'medium' }}</td>
                <td [attr.data-label]="'By'">{{ h.changedBy }}</td>
                <td [attr.data-label]="'From'">{{ h.previousDate || 'open' }}</td>
                <td [attr.data-label]="'To'">{{ h.newDate || 'open' }}</td>
                <td [attr.data-label]="'Reason'">{{ h.reason }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ================= Bank reconciliation: list ================= -->
      <div *ngIf="tab === 'bank' && !rec">
        <div class="card mb-3" style="padding:1rem;" *ngIf="canManage">
          <strong class="mb-2" style="display:block;">{{ 'Start a reconciliation' | translate }}</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Take the statement from your bank or mobile money provider. You give its date and ending balance, then tick the ledger lines that appear on it.' | translate }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Account' | translate }}</label>
              <select class="form-select ctl-new-account" [(ngModel)]="newAccountId" name="newAccountId">
                <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                <option *ngFor="let a of moneyAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
              </select></div>
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Statement date' | translate }}</label>
              <input class="form-control ctl-new-date" type="date" [(ngModel)]="newDate" name="newDate" [max]="today" /></div>
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Ending balance on the statement' | translate }}</label>
              <input class="form-control ctl-new-balance" type="number" step="0.01" [(ngModel)]="newBalance" name="newBalance" /></div>
            <button class="btn btn-primary ctl-new-start" type="button" [disabled]="busy || !newAccountId || !newDate || newBalance === null" (click)="start()">{{ 'Start' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.78rem; margin-top:0.5rem;">{{ 'The system records all cash, bank and mobile money in one account called Cash and Bank. To reconcile a bank on its own, add it as an asset account in the chart of accounts and move money to it with a journal entry.' | translate }}</div>
        </div>

        <div class="card" style="padding:1rem;">
          <strong class="mb-2" style="display:block;">{{ 'Reconciliations' | translate }}</strong>
          <p class="text-muted" *ngIf="!recs.length">{{ 'None yet.' | translate }}</p>
          <table class="ctl-recs" *ngIf="recs.length">
            <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Account' | translate }}</th><th>{{ 'Statement date' | translate }}</th><th class="text-end">{{ 'Statement balance' | translate }}</th><th>{{ 'Lines' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let r of recs" [attr.data-rec]="r.id">
                <td [attr.data-label]="'Number'">{{ r.reconciliationNumber }}</td>
                <td [attr.data-label]="'Account'">{{ r.accountName }}</td>
                <td [attr.data-label]="'Statement date'">{{ r.statementDate }}</td>
                <td class="text-end" [attr.data-label]="'Statement balance'">{{ r.statementBalance | number: '1.2-2' }}</td>
                <td [attr.data-label]="'Lines'">{{ r.ticked }}</td>
                <td [attr.data-label]="'Status'">{{ r.status === 'DONE' ? ('Finished' | translate) : ('In progress' | translate) }}</td>
                <td class="flex gap-2">
                  <button class="btn btn-sm btn-outline-primary ctl-open-rec" type="button" (click)="openRec(r.id)">{{ r.status === 'DONE' ? ('View' | translate) : ('Continue' | translate) }}</button>
                  <button class="btn btn-sm btn-outline-danger ctl-undo-rec" type="button" *ngIf="r.canUndo && isAdmin" [disabled]="busy" (click)="undoRec(r)">{{ 'Undo' | translate }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ================= Bank reconciliation: one statement ================= -->
      <div *ngIf="tab === 'bank' && rec">
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
          <button class="btn btn-sm btn-outline-secondary ctl-back" type="button" (click)="closeRec()">&larr; All reconciliations</button>
          <strong>{{ '{p0} · {p1} · statement of {p2}' | translate: { p0: (rec.header.reconciliationNumber), p1: (rec.header.accountName), p2: (rec.header.statementDate) } }}</strong>
          <span class="text-muted">{{ rec.header.status === 'DONE' ? ('(finished)' | translate) : ('(in progress)' | translate) }}</span>
        </div>

        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Statement balance' | translate }}</div><strong class="ctl-r-statement">{{ rec.header.statementBalance | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Matched so far' | translate }}</div><strong class="ctl-r-cleared">{{ rec.cleared | number: '1.2-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} from earlier statements' | translate: { p0: (rec.clearedBefore | number: '1.2-2') } }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem;" [style.border-color]="rec.difference === 0 ? '#16a34a' : '#b91c1c'"><div class="text-muted" style="font-size:0.8rem;">{{ 'Difference' | translate }}</div>
            <strong class="ctl-r-diff" [style.color]="rec.difference === 0 ? '#16a34a' : '#b91c1c'">{{ rec.difference | number: '1.2-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;">{{ rec.difference === 0 ? ('Ready to finish' | translate) : ('Must be zero to finish' | translate) }}</div></div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="rec.header.status === 'DRAFT' && canManage">
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Statement date' | translate }}</label>
              <input class="form-control ctl-edit-date" type="date" [(ngModel)]="editDate" name="editDate" [max]="today" /></div>
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Ending balance' | translate }}</label>
              <input class="form-control ctl-edit-balance" type="number" step="0.01" [(ngModel)]="editBalance" name="editBalance" /></div>
            <button class="btn btn-sm btn-outline-secondary ctl-edit-save" type="button" [disabled]="busy" (click)="saveHeader()">{{ 'Save' | translate }}</button>
            <span style="flex:1;"></span>
            <button class="btn btn-sm btn-outline-secondary ctl-adj-toggle" type="button" (click)="showAdj = !showAdj">{{ '{p0} a bank charge or interest' | translate: { p0: (showAdj ? 'Hide' : 'Add') } }}</button>
            <button class="btn btn-sm btn-outline-danger ctl-cancel" type="button" [disabled]="busy" (click)="cancelRec()">{{ 'Cancel this reconciliation' | translate }}</button>
            <button class="btn btn-primary ctl-finish" type="button" [disabled]="busy || rec.difference !== 0" (click)="finishRec()">{{ 'Finish' | translate }}</button>
          </div>
          <div *ngIf="showAdj" class="ctl-adj" style="margin-top:0.8rem; border-top:1px solid var(--border-color, #ddd); padding-top:0.8rem;">
            <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Something on the statement that is not in the books yet. It is posted to the ledger on the date you give and ticked straight away.' | translate }}</div>
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'What happened' | translate }}</label>
                <select class="form-select ctl-adj-dir" [(ngModel)]="adjDirection" name="adjDirection">
                  <option value="OUT">{{ 'Money left the account (bank charge)' | translate }}</option>
                  <option value="IN">{{ 'Money came in (interest)' | translate }}</option>
                </select></div>
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Other side' | translate }}</label>
                <select class="form-select ctl-adj-account" [(ngModel)]="adjAccountId" name="adjAccountId">
                  <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                  <option *ngFor="let a of allAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
                </select></div>
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Amount' | translate }}</label>
                <input class="form-control ctl-adj-amount" type="number" step="0.01" min="0" [(ngModel)]="adjAmount" name="adjAmount" style="max-width:9rem;" /></div>
              <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Date' | translate }}</label>
                <input class="form-control ctl-adj-date" type="date" [(ngModel)]="adjDate" name="adjDate" [max]="rec.header.statementDate" /></div>
              <div class="form-group" style="margin:0; flex:1; min-width:10rem;"><label class="form-label">{{ 'Note' | translate }}</label>
                <input class="form-control ctl-adj-memo" type="text" [(ngModel)]="adjMemo" name="adjMemo" [placeholder]="'Monthly bank charge' | translate" /></div>
              <button class="btn btn-primary ctl-adj-post" type="button" [disabled]="busy || !adjAccountId || !adjAmount" (click)="postAdjustment()">{{ 'Post and tick' | translate }}</button>
            </div>
          </div>
        </div>

        <div class="card" style="padding:1rem;">
          <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;" *ngIf="rec.header.status === 'DRAFT'">
            <strong>{{ 'Ledger lines up to {p0} not yet matched' | translate: { p0: (rec.header.statementDate) } }}</strong>
            <span style="flex:1;"></span>
            <label class="text-muted" style="font-size:0.85rem;">{{ 'From' | translate }} <input class="form-control ctl-from" type="date" style="display:inline-block; width:auto;" [(ngModel)]="fromDate" name="fromDate" (ngModelChange)="reloadRec()" /></label>
            <ng-container *ngIf="canManage">
              <button class="btn btn-sm btn-outline-secondary ctl-tick-all" type="button" [disabled]="busy" (click)="tickAll(true)">{{ 'Tick all shown{p0}' | translate: { p0: (fromDate ? ' from that date' : '') } }}</button>
              <button class="btn btn-sm btn-outline-secondary ctl-untick-all" type="button" [disabled]="busy" (click)="tickAll(false)">{{ 'Untick all' | translate }}</button>
            </ng-container>
          </div>
          <div class="text-muted mb-2" style="font-size:0.8rem;" *ngIf="rec.header.status === 'DRAFT'">{{ 'Deposits and money in are debits; payments and money out are credits. Tick what you can see on the statement.' | translate }}</div>
          <div class="alert alert-warning" *ngIf="rec.truncated">{{ 'Only the first 2000 lines are shown. Use the From date to narrow the list.' | translate }}</div>
          <strong *ngIf="rec.header.status === 'DONE'" class="mb-2" style="display:block;">{{ 'Lines matched in this reconciliation' | translate }}</strong>
          <p class="text-muted" *ngIf="!rec.lines.length">{{ 'No ledger lines.' | translate }}</p>
          <table class="ctl-lines" *ngIf="rec.lines.length">
            <thead><tr><th style="width:2rem;"></th><th>{{ 'Date' | translate }}</th><th>{{ 'Entry' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Money in' | translate }}</th><th class="text-end">{{ 'Money out' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of rec.lines" [attr.data-line]="l.lineId">
                <td><input type="checkbox" class="ctl-tick" [checked]="l.ticked" [disabled]="busy || rec.header.status === 'DONE' || !canManage" (change)="tick(l.lineId, $any($event.target).checked)" /></td>
                <td [attr.data-label]="'Date'">{{ l.date }}</td>
                <td [attr.data-label]="'Entry'">{{ l.entryNumber }}</td>
                <td [attr.data-label]="'What'">{{ l.memo || l.description }}</td>
                <td class="text-end" [attr.data-label]="'Money in'">{{ l.debit ? (l.debit | number: '1.2-2') : '' }}</td>
                <td class="text-end" [attr.data-label]="'Money out'">{{ l.credit ? (l.credit | number: '1.2-2') : '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ================= Opening balances ================= -->
      <div *ngIf="tab === 'open' && opening">
        <div class="card mb-3" style="padding:1rem;" *ngIf="opening.recorded">
          <strong class="ctl-open-status">{{ 'Opening balances are recorded as of {p0} ({p1}).' | translate: { p0: (opening.asOf), p1: (opening.entryNumber) } }}</strong>
          <table class="mb-2 ctl-open-lines" style="margin-top:0.6rem;">
            <thead><tr><th>{{ 'Account' | translate }}</th><th class="text-end">{{ 'Debit' | translate }}</th><th class="text-end">{{ 'Credit' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of opening.lines">
                <td [attr.data-label]="'Account'">{{ l.accountCode }} {{ l.accountName }}</td>
                <td class="text-end" [attr.data-label]="'Debit'">{{ l.debit ? (l.debit | number: '1.2-2') : '' }}</td>
                <td class="text-end" [attr.data-label]="'Credit'">{{ l.credit ? (l.credit | number: '1.2-2') : '' }}</td>
              </tr>
              <tr><td><strong>{{ 'Total' | translate }}</strong></td><td class="text-end"><strong>{{ opening.totalDebit | number: '1.2-2' }}</strong></td><td class="text-end"><strong>{{ opening.totalCredit | number: '1.2-2' }}</strong></td></tr>
            </tbody>
          </table>
          <div *ngIf="isAdmin">
            <button class="btn btn-outline-danger ctl-open-reverse" type="button" [disabled]="busy" (click)="reverseOpening()">{{ 'Reverse these opening balances' | translate }}</button>
            <span class="text-muted" style="font-size:0.8rem; margin-left:0.5rem;">{{ 'Posts an equal and opposite entry on the same date so you can enter them again.' | translate }}</span>
          </div>
        </div>

        <div class="card" style="padding:1rem;" *ngIf="!opening.recorded && isAdmin">
          <strong class="mb-1" style="display:block;">{{ 'Opening balances of the ledger' | translate }}</strong>
          <p class="text-muted" style="font-size:0.85rem;">{{ 'What the business had and owed on the day it started using the system: cash and bank, equipment, loans, owner\\'s capital and so on. They are posted together as one balanced entry. What customers owe you and what you owe suppliers are not entered here: they come from the customer and supplier balances (' | translate }}<a routerLink="/admin/data">Data &gt; Bulk import</a>{{ '), so each customer and supplier has their own opening balance.' | translate }}</p>
          <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="margin:0;"><label class="form-label">{{ 'As of (the day you started)' | translate }}</label>
              <input class="form-control ctl-op-date" type="date" [(ngModel)]="opDate" name="opDate" [max]="today" /></div>
            <div class="form-group" style="margin:0; flex:1; min-width:12rem;"><label class="form-label">{{ 'Note' | translate }}</label>
              <input class="form-control ctl-op-memo" type="text" [(ngModel)]="opMemo" name="opMemo" [placeholder]="'Opening balances' | translate" /></div>
          </div>
          <table class="ctl-op-grid">
            <thead><tr><th>{{ 'Account' | translate }}</th><th class="text-end">{{ 'Debit' | translate }}</th><th class="text-end">{{ 'Credit' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let l of opLines; let i = index">
                <td [attr.data-label]="'Account'"><select class="form-select ctl-op-account" [(ngModel)]="l.accountId" [name]="'opAcc' + i">
                  <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                  <option *ngFor="let a of allAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option>
                </select></td>
                <td [attr.data-label]="'Debit'"><input class="form-control ctl-op-debit text-end" type="number" step="0.01" min="0" [(ngModel)]="l.debit" [name]="'opDeb' + i" /></td>
                <td [attr.data-label]="'Credit'"><input class="form-control ctl-op-credit text-end" type="number" step="0.01" min="0" [(ngModel)]="l.credit" [name]="'opCre' + i" /></td>
                <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="removeOpLine(i)" *ngIf="opLines.length > 1">{{ 'Remove' | translate }}</button></td>
              </tr>
              <tr><td><strong>{{ 'Total' | translate }}</strong></td><td class="text-end"><strong class="ctl-op-total-d">{{ opDebit() | number: '1.2-2' }}</strong></td><td class="text-end"><strong class="ctl-op-total-c">{{ opCredit() | number: '1.2-2' }}</strong></td><td></td></tr>
            </tbody>
          </table>
          <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:center;">
            <button class="btn btn-sm btn-outline-secondary ctl-op-add" type="button" (click)="addOpLine()">{{ 'Add a line' | translate }}</button>
            <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" class="ctl-op-equity" [(ngModel)]="opEquity" name="opEquity" /> {{ 'Put any difference in Owner\\'s Equity' | translate }}</label>
            <span class="ctl-op-diff" [style.color]="opDiff() === 0 ? '#16a34a' : '#b91c1c'">{{ opDiff() === 0 ? 'Balanced' : 'Difference ' + (opDiff() | number: '1.2-2') }}</span>
            <span style="flex:1;"></span>
            <button class="btn btn-primary ctl-op-save" type="button" [disabled]="busy || !opDate || (opDiff() !== 0 && !opEquity)" (click)="recordOpening()">{{ 'Record opening balances' | translate }}</button>
          </div>
        </div>
        <div class="card" style="padding:1rem;" *ngIf="!opening.recorded && !isAdmin"><span class="text-muted">{{ 'No opening balances are recorded. Only an admin can enter them.' | translate }}</span></div>
      </div>
    </app-shell>
  `,
})
export class AccountingControlsComponent implements OnInit {
  tab: 'lock' | 'bank' | 'open' = 'lock';
  error: string | null = null;
  done: string | null = null;
  busy = false;
  isAdmin = false;
  canManage = false;

  today = new Date().toISOString().slice(0, 10);
  yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);

  // close the books
  lock: LockState | null = null;
  lockDate = '';
  lockReason = '';

  // bank reconciliation
  moneyAccounts: MoneyAccount[] = [];
  allAccounts: Account[] = [];
  recs: ReconRow[] = [];
  rec: ReconDetail | null = null;
  newAccountId: number | null = null;
  newDate = '';
  newBalance: number | null = null;
  editDate = '';
  editBalance: number | null = null;
  fromDate = '';
  showAdj = false;
  adjDirection: 'OUT' | 'IN' = 'OUT';
  adjAccountId: number | null = null;
  adjAmount: number | null = null;
  adjDate = '';
  adjMemo = '';

  // opening balances
  opening: OpeningState | null = null;
  opDate = '';
  opMemo = '';
  opEquity = false;
  opLines: OpeningDraftLine[] = [{ accountId: null, debit: null, credit: null }, { accountId: null, debit: null, credit: null }];

  constructor(private api: AccountingControlsApiService, private accounts: AccountApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.isAdmin = this.auth.isAdmin();
    this.canManage = this.auth.canManage('ACCOUNTING');
    this.load();
  }

  private fail(err: any, fallback: string): void {
    this.busy = false;
    this.error = err?.error?.message ?? fallback;
  }

  show(tab: 'lock' | 'bank' | 'open'): void {
    this.tab = tab;
    this.error = null;
    this.done = null;
    this.load();
  }

  load(): void {
    if (this.tab === 'lock') {
      this.api.lock().subscribe({ next: (s) => (this.lock = s), error: (e) => this.fail(e, 'Could not load the closing date') });
    } else if (this.tab === 'bank') {
      this.api.moneyAccounts().subscribe({ next: (a) => (this.moneyAccounts = a), error: (e) => this.fail(e, 'Could not load the accounts') });
      this.accounts.listActive().subscribe({ next: (a) => (this.allAccounts = a) });
      this.loadRecs();
    } else {
      this.accounts.listActive().subscribe({ next: (a) => (this.allAccounts = a) });
      this.api.opening().subscribe({ next: (s) => (this.opening = s), error: (e) => this.fail(e, 'Could not load the opening balances') });
    }
  }

  // ---- close the books ----

  saveLock(reopen: boolean): void {
    this.error = null;
    this.done = null;
    this.busy = true;
    this.api.setLock(reopen ? null : this.lockDate || null, this.lockReason).subscribe({
      next: (s) => {
        this.busy = false;
        this.lock = s;
        this.lockReason = '';
        this.lockDate = '';
        this.done = s.lockDate ? `The books are now closed up to ${s.lockDate}.` : 'The books are open again.';
      },
      error: (e) => this.fail(e, 'Could not change the closing date'),
    });
  }

  // ---- bank reconciliation ----

  loadRecs(): void {
    this.api.reconciliations().subscribe({ next: (r) => (this.recs = r), error: (e) => this.fail(e, 'Could not load the reconciliations') });
  }

  start(): void {
    if (!this.newAccountId || !this.newDate || this.newBalance === null) return;
    this.error = null;
    this.done = null;
    this.busy = true;
    this.api.startReconciliation(this.newAccountId, this.newDate, this.newBalance, null).subscribe({
      next: (d) => {
        this.busy = false;
        this.setRec(d);
        this.newAccountId = null;
        this.newDate = '';
        this.newBalance = null;
      },
      error: (e) => this.fail(e, 'Could not start the reconciliation'),
    });
  }

  openRec(id: number): void {
    this.error = null;
    this.done = null;
    this.fromDate = '';
    this.api.reconciliation(id, null).subscribe({ next: (d) => this.setRec(d), error: (e) => this.fail(e, 'Could not open the reconciliation') });
  }

  private setRec(d: ReconDetail): void {
    this.rec = d;
    this.editDate = d.header.statementDate;
    this.editBalance = d.header.statementBalance;
    this.adjDate = d.header.statementDate;
  }

  closeRec(): void {
    this.rec = null;
    this.showAdj = false;
    this.loadRecs();
  }

  reloadRec(): void {
    if (!this.rec) return;
    this.api.reconciliation(this.rec.header.id, this.fromDate || null).subscribe({ next: (d) => (this.rec = d), error: (e) => this.fail(e, 'Could not reload') });
  }

  private apply(call: import('rxjs').Observable<ReconDetail>, message: string | null): void {
    this.error = null;
    this.done = null;
    this.busy = true;
    call.subscribe({
      next: (d) => {
        this.busy = false;
        this.rec = d;
        if (message) this.done = message;
      },
      error: (e) => {
        this.fail(e, 'That did not work');
        this.reloadRec();
      },
    });
  }

  tick(lineId: number, ticked: boolean): void {
    if (!this.rec) return;
    this.apply(this.api.tick(this.rec.header.id, [lineId], ticked), null);
  }

  tickAll(ticked: boolean): void {
    if (!this.rec) return;
    this.apply(this.api.tickAll(this.rec.header.id, ticked ? this.fromDate || null : null, ticked), null);
  }

  saveHeader(): void {
    if (!this.rec) return;
    this.apply(this.api.updateReconciliation(this.rec.header.id, this.editDate || null, this.editBalance, null), 'Saved.');
  }

  postAdjustment(): void {
    if (!this.rec || !this.adjAccountId || !this.adjAmount) return;
    const id = this.rec.header.id;
    this.error = null;
    this.done = null;
    this.busy = true;
    this.api.adjustment(id, this.adjAccountId, this.adjAmount, this.adjDirection, this.adjDate || null, this.adjMemo || null).subscribe({
      next: (d) => {
        this.busy = false;
        this.rec = d;
        this.adjAmount = null;
        this.adjMemo = '';
        this.done = 'Posted to the ledger and ticked.';
      },
      error: (e) => this.fail(e, 'Could not post that'),
    });
  }

  finishRec(): void {
    if (!this.rec) return;
    this.apply(this.api.finish(this.rec.header.id), 'Reconciliation finished.');
  }

  cancelRec(): void {
    if (!this.rec || !confirm('Cancel this reconciliation? The ticks are discarded. Bank charges you posted from here stay in the ledger.')) return;
    this.busy = true;
    this.api.cancel(this.rec.header.id).subscribe({
      next: () => {
        this.busy = false;
        this.closeRec();
        this.done = 'Cancelled.';
      },
      error: (e) => this.fail(e, 'Could not cancel'),
    });
  }

  undoRec(r: ReconRow): void {
    if (!confirm(`Undo ${r.reconciliationNumber}? Its lines become unmatched again.`)) return;
    this.busy = true;
    this.api.undo(r.id).subscribe({
      next: () => {
        this.busy = false;
        this.done = `${r.reconciliationNumber} undone.`;
        this.loadRecs();
      },
      error: (e) => this.fail(e, 'Could not undo'),
    });
  }

  // ---- opening balances ----

  addOpLine(): void {
    this.opLines.push({ accountId: null, debit: null, credit: null });
  }

  removeOpLine(i: number): void {
    this.opLines.splice(i, 1);
  }

  opDebit(): number {
    return Math.round(this.opLines.reduce((s, l) => s + (Number(l.debit) || 0), 0) * 100) / 100;
  }

  opCredit(): number {
    return Math.round(this.opLines.reduce((s, l) => s + (Number(l.credit) || 0), 0) * 100) / 100;
  }

  opDiff(): number {
    return Math.round((this.opDebit() - this.opCredit()) * 100) / 100;
  }

  recordOpening(): void {
    const lines: OpeningLinePayload[] = this.opLines
      .filter((l) => l.accountId && (Number(l.debit) || Number(l.credit)))
      .map((l) => ({ accountId: l.accountId as number, debit: Number(l.debit) || null, credit: Number(l.credit) || null }));
    this.error = null;
    this.done = null;
    this.busy = true;
    this.api.recordOpening(this.opDate, this.opEquity, this.opMemo || null, lines).subscribe({
      next: (s) => {
        this.busy = false;
        this.opening = s;
        this.done = 'Opening balances recorded.';
      },
      error: (e) => this.fail(e, 'Could not record the opening balances'),
    });
  }

  reverseOpening(): void {
    if (!confirm('Reverse the opening balances? An equal and opposite entry is posted so you can enter them again.')) return;
    this.busy = true;
    this.api.reverseOpening().subscribe({
      next: (s) => {
        this.busy = false;
        this.opening = s;
        this.done = 'Opening balances reversed. You can enter them again.';
      },
      error: (e) => this.fail(e, 'Could not reverse'),
    });
  }
}
