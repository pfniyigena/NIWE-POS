import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { ModuleApiService } from '../../core/api/module-api.service';
import { Account } from '../../core/models';
import {
  BILLING_LABEL, BillPreview, BillRequestPayload, BillResult, FeeTermsPayload, LawApiService, MatterBilling, Option,
} from '../../core/api/law-api.service';

/**
 * The Billing tab of a matter: fee terms and rates, hours (approval), costs paid for the client, fixed-fee stages, and turning it all into a
 * draft invoice. The server decides who may do what; the buttons follow the flags it sends (canTerms, canBill, canWork, canApprove).
 */
@Component({
  selector: 'app-law-matter-billing',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, DatePickerComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="info">{{ info }} <a *ngIf="lastInvoiceId" [routerLink]="['/admin/invoices', lastInvoiceId]">{{ 'Open the invoice' | translate }}</a></div>
    <div *ngIf="!b && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

    <ng-container *ngIf="b">
      <!-- figures -->
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Hours logged' | translate }}</div><strong>{{ '{p0} h' | translate: { p0: (b.summary.hoursLogged | number: '1.0-2') } }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} h billable' | translate: { p0: (b.summary.billableHours | number: '1.0-2') } }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Approved time to bill' | translate }}</div><strong>{{ b.summary.unbilledTime | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="b.summary.timeWaitingApproval">{{ '{p0} entr{p1} waiting for approval' | translate: { p0: (b.summary.timeWaitingApproval), p1: (b.summary.timeWaitingApproval === 1 ? 'y' : 'ies') } }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Costs to bill' | translate }}</div><strong>{{ b.summary.unbilledCosts | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;" *ngIf="b.summary.stagesPlanned > 0 || b.terms.fixedFee"><div class="text-muted" style="font-size:0.8rem;">{{ 'Fixed-fee stages to bill' | translate }}</div><strong>{{ b.summary.stagesPlanned | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Billed (before tax)' | translate }}</div><strong>{{ b.summary.billed | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ 'paid {p0} · owed {p1}' | translate: { p0: (b.summary.paid | number: '1.0-2'), p1: (b.summary.outstanding | number: '1.0-2') } }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem; min-width:9rem;" *ngIf="b.summary.retainerInvoiced > 0"><div class="text-muted" style="font-size:0.8rem;">{{ 'Retainer left' | translate }}</div><strong>{{ b.summary.retainerBalance | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">{{ 'of {p0} invoiced' | translate: { p0: (b.summary.retainerInvoiced | number: '1.0-2') } }}</div></div>
      </div>

      <!-- fee terms -->
      <div class="card mb-3" style="padding:1rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <h5 style="margin:0;">{{ 'Fee terms' | translate }}</h5>
          <button class="btn btn-sm btn-outline-secondary" *ngIf="b.canTerms && !editTerms" (click)="startTerms()">{{ 'Change' | translate }}</button>
        </div>
        <div *ngIf="!editTerms" class="mt-2">
          <div>{{ label(b.terms.billingType) }}<span class="text-muted" *ngIf="b.terms.billingNote"> — {{ b.terms.billingNote }}</span></div>
          <div class="text-muted" style="font-size:0.9rem;">
            <span *ngIf="b.terms.hourlyRate">{{ 'Hourly rate {p0}' | translate: { p0: (b.terms.hourlyRate | number: '1.0-2') } }}</span><span *ngIf="!b.terms.hourlyRate && usesTime(b.terms.billingType)">{{ 'Hourly rate: the person\\'s rate or the Time Tracking default' | translate }}</span>
            <span *ngIf="b.terms.fixedFee"> {{ '· Fixed fee {p0}' | translate: { p0: (b.terms.fixedFee | number: '1.0-2') } }}</span>
            <span *ngIf="b.terms.retainerAmount"> {{ '· Retainer {p0}' | translate: { p0: (b.terms.retainerAmount | number: '1.0-2') } }}</span>
            <span *ngIf="b.terms.contingencyPercent"> {{ '· Contingency {p0}%' | translate: { p0: (b.terms.contingencyPercent | number: '1.0-2') } }}</span>
          </div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="!usesTime(b.terms.billingType)">{{ 'Hours on this matter are tracked but not billed.' | translate }}</div>
        </div>
        <form *ngIf="editTerms" (ngSubmit)="saveTerms()" class="mt-2">
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'How it is billed' | translate }}</label>
              <select class="form-select" name="tb" [(ngModel)]="tf.billingType"><option *ngFor="let t of types" [value]="t.key">{{ t.label }}</option></select></div>
            <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Fee agreement in words' | translate }}</label><input class="form-control" name="tn" [(ngModel)]="termsNote" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Hourly rate' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="th" [(ngModel)]="tf.hourlyRate" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Fixed fee' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="tf" [(ngModel)]="tf.fixedFee" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Retainer' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="tr" [(ngModel)]="tf.retainerAmount" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Contingency %' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="any" name="tc" [(ngModel)]="tf.contingencyPercent" /></div>
          </div>
          <div class="text-muted mb-2" style="font-size:0.8rem;">{{ 'Hours are billed for hourly and retainer matters. On the other kinds they are tracked but not billed.' | translate }}</div>
          <div class="flex gap-2"><button class="btn btn-primary btn-sm" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary btn-sm" type="button" (click)="editTerms = false">{{ 'Cancel' | translate }}</button></div>
        </form>

        <div class="mt-3" *ngIf="b.canTerms && usesTime(b.terms.billingType)">
          <strong style="font-size:0.9rem;">{{ 'Rates per person' | translate }}</strong> <span class="text-muted" style="font-size:0.8rem;">{{ '(override the matter rate)' | translate }}</span>
          <div *ngFor="let r of b.rates" class="flex gap-2 align-center mt-1">
            <span style="min-width:12rem;">{{ r.userName }}</span><span>{{ r.hourlyRate | number: '1.0-2' }}</span>
            <button class="btn btn-sm btn-outline-danger" (click)="removeRate(r.userId)" [disabled]="busy">{{ 'Remove' | translate }}</button>
          </div>
          <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
            <select class="form-select" style="max-width:14rem;" [(ngModel)]="rate.userId" name="ru"><option [ngValue]="null">{{ 'Choose a person…' | translate }}</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.name }}</option></select>
            <input class="form-control" style="max-width:9rem;" type="number" min="0" step="any" [placeholder]="'Rate' | translate" [(ngModel)]="rate.hourlyRate" name="rr" />
            <button class="btn btn-sm btn-outline-primary" (click)="saveRate()" [disabled]="busy || !rate.userId || rate.hourlyRate == null">{{ 'Set rate' | translate }}</button>
          </div>
        </div>
      </div>

      <!-- hours -->
      <div class="card mb-3" style="padding:1rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <h5 style="margin:0;">{{ 'Hours' | translate }}</h5>
          <a class="btn btn-sm btn-outline-primary" routerLink="/time">{{ 'Log time (My time)' | translate }}</a>
        </div>
        <p class="text-muted mt-2" *ngIf="!b.time.length">{{ 'Nobody has logged time on this matter yet. Lawyers log hours in' | translate }} <em>{{ 'My time' | translate }}</em> {{ 'and pick this matter.' | translate }}</p>
        <ng-container *ngIf="b.time.length">
          <div class="flex gap-2 mt-2 align-center" *ngIf="b.canApprove && waiting().length" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-success" (click)="approve()" [disabled]="busy || !selected.size">{{ 'Approve selected' | translate }}</button>
            <button class="btn btn-sm btn-outline-danger" (click)="reject()" [disabled]="busy || !selected.size || !rejectReason.trim()">{{ 'Send back' | translate }}</button>
            <input class="form-control" style="max-width:18rem;" [placeholder]="'Reason for sending back' | translate" [(ngModel)]="rejectReason" name="rj" />
            <button class="btn btn-sm btn-outline-secondary" (click)="selectWaiting()">{{ 'Select all waiting' | translate }}</button>
          </div>
          <table class="stack table-sm mt-2">
            <thead><tr><th *ngIf="b.canApprove"></th><th>{{ 'Date' | translate }}</th><th>{{ 'Who' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Hours' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let e of b.time">
                <td *ngIf="b.canApprove"><input type="checkbox" *ngIf="e.status === 'SUBMITTED'" [checked]="selected.has(e.id)" (change)="toggle(e.id)" /></td>
                <td data-label="Date">{{ e.workDate }}</td><td data-label="Who">{{ e.userName }}</td><td data-label="What">{{ e.description || '—' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="e.rejectReason">{{ 'Sent back: {p0}' | translate: { p0: (e.rejectReason) } }}</div></td>
                <td class="text-end" data-label="Hours">{{ e.hours | number: '1.0-2' }}</td><td class="text-end" data-label="Amount">{{ e.billable ? (e.amount | number: '1.0-2') : '—' }}</td>
                <td data-label="Status"><span class="badge" [class.badge-success]="e.status === 'APPROVED' || e.status === 'INVOICED'" [class.badge-warning]="e.status === 'SUBMITTED'" [class.badge-secondary]="e.status === 'DRAFT'" [class.badge-danger]="e.status === 'REJECTED'">{{ e.status.charAt(0) + e.status.slice(1).toLowerCase() }}</span>
                  <span class="text-muted" style="font-size:0.75rem;" *ngIf="e.invoiceNumber"> {{ e.invoiceNumber }}</span><span class="text-muted" style="font-size:0.75rem;" *ngIf="!e.billable"> {{ 'not billable' | translate }}</span></td>
              </tr>
            </tbody>
          </table>
        </ng-container>
      </div>

      <!-- costs -->
      <div class="card mb-3" style="padding:1rem;">
        <h5 style="margin:0;">{{ 'Costs paid for the client' | translate }}</h5>
        <p class="text-muted mt-1" style="font-size:0.85rem;">{{ 'Court and filing fees, travel, courier and similar. Charged costs go on the next invoice.' | translate }}</p>
        <table class="stack table-sm" *ngIf="b.costs.length">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Paid to' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th><th class="text-end">{{ 'Charged' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of b.costs">
              <td data-label="Date">{{ c.expenseDate }}</td><td data-label="What">{{ c.description }}</td><td data-label="Paid to">{{ c.supplierName || '—' }}</td>
              <td class="text-end" data-label="Cost">{{ c.amount | number: '1.0-2' }}</td><td class="text-end" data-label="Charged">{{ c.chargeAmount | number: '1.0-2' }}</td>
              <td data-label="Status"><span class="badge" [class.badge-success]="c.billed" [class.badge-secondary]="!c.billed">{{ c.billed ? 'Billed' : c.chargeAmount > 0 ? ('To bill' | translate) : ('Absorbed' | translate) }}</span><span class="text-muted" style="font-size:0.75rem;" *ngIf="c.invoiceNumber"> {{ c.invoiceNumber }}</span></td>
              <td><button class="btn btn-sm btn-outline-danger" *ngIf="c.canEdit" (click)="deleteCost(c.id)" [disabled]="busy">{{ 'Delete' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <form *ngIf="b.canWork" (ngSubmit)="addCost()" class="mt-2">
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="cd" [(ngModel)]="cost.expenseDate"></app-date-picker></div>
            <div class="form-group" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'What' | translate }}</label><input class="form-control" name="cw" [(ngModel)]="cost.description" required /></div>
            <div class="form-group" style="flex:1; min-width:9rem;"><label class="form-label">{{ 'Paid to' | translate }}</label><input class="form-control" name="cs" [(ngModel)]="cost.supplierName" /></div>
            <div class="form-group" style="width:8rem;"><label class="form-label">{{ 'Cost' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="ca" [(ngModel)]="cost.amount" required (ngModelChange)="costChargeDefault()" /></div>
            <div class="form-group" style="width:8rem;"><label class="form-label">{{ 'Charge client' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="cc" [(ngModel)]="cost.chargeAmount" (ngModelChange)="chargeTouched = true" /></div>
            <div class="form-group" *ngIf="expenseAccounts.length" style="min-width:12rem;"><label class="form-label">{{ 'Also record in Expenses' | translate }}</label>
              <select class="form-select" name="ce" [(ngModel)]="cost.expenseAccountId"><option [ngValue]="null">{{ 'No' | translate }}</option><option *ngFor="let a of expenseAccounts" [ngValue]="a.id">{{ a.code }} {{ a.name }}</option></select></div>
            <button class="btn btn-primary" type="submit" [disabled]="busy || !cost.description.trim() || !cost.amount">{{ 'Add cost' | translate }}</button>
          </div>
        </form>
      </div>

      <!-- stages -->
      <div class="card mb-3" style="padding:1rem;" *ngIf="b.terms.billingType === 'FIXED' || b.terms.fixedFee || b.stages.length">
        <h5 style="margin:0;">{{ 'Fixed-fee stages' | translate }}</h5>
        <p class="text-muted mt-1" style="font-size:0.85rem;">{{ 'The fixed fee of {p0} is billed stage by stage. Stages cannot add up to more than the fee.' | translate: { p0: (b.terms.fixedFee ? (b.terms.fixedFee | number: '1.0-2') : '(not set)') } }}</p>
        <table class="stack table-sm" *ngIf="b.stages.length">
          <thead><tr><th>{{ 'Stage' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let s of b.stages">
              <td data-label="Stage">{{ s.description }}</td><td data-label="Due">{{ s.dueDate || '—' }}</td><td class="text-end" data-label="Amount">{{ s.amount | number: '1.0-2' }}</td>
              <td data-label="Status"><span class="badge" [class.badge-success]="s.status === 'BILLED'" [class.badge-secondary]="s.status !== 'BILLED'">{{ s.status === 'BILLED' ? ('Billed' | translate) : ('To bill' | translate) }}</span><span class="text-muted" style="font-size:0.75rem;" *ngIf="s.invoiceNumber"> {{ s.invoiceNumber }}</span></td>
              <td><button class="btn btn-sm btn-outline-danger" *ngIf="b.canTerms && s.status === 'PLANNED'" (click)="deleteStage(s.id)" [disabled]="busy">{{ 'Delete' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <form *ngIf="b.canTerms" (ngSubmit)="addStage()" class="mt-2">
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Stage' | translate }}</label><input class="form-control" name="sd" [(ngModel)]="stage.description" [placeholder]="'e.g. On filing the claim' | translate" /></div>
            <div class="form-group" style="width:9rem;"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0" step="any" name="sa" [(ngModel)]="stage.amount" /></div>
            <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Due' | translate }}</label><app-date-picker name="sdu" [(ngModel)]="stage.dueDate"></app-date-picker></div>
            <button class="btn btn-primary" type="submit" [disabled]="busy || !stage.description.trim() || !stage.amount">{{ 'Add stage' | translate }}</button>
          </div>
        </form>
      </div>

      <!-- billing -->
      <div class="card mb-3" style="padding:1rem;" *ngIf="b.canBill">
        <h5 style="margin:0;">{{ 'Bill this matter' | translate }}</h5>
        <p class="text-muted mt-1" style="font-size:0.85rem;">{{ 'Builds one draft invoice for {p0}. Nothing is final until you issue the invoice.' | translate: { p0: (b.clientName) } }}</p>
        <div class="flex gap-3 align-center" style="flex-wrap:wrap;" *ngIf="usesTime(b.terms.billingType)">
          <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="bi" [(ngModel)]="bill.includeTime" (ngModelChange)="preview = null" /> {{ 'Approved time ({p0})' | translate: { p0: (b.summary.unbilledTime | number: '1.0-2') } }}</label>
          <label style="display:flex; gap:0.4rem; align-items:center;">{{ 'up to' | translate }} <app-date-picker name="bt" [(ngModel)]="bill.timeTo" (ngModelChange)="preview = null"></app-date-picker></label>
        </div>
        <div class="mt-2" *ngIf="plannedStages().length">
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Fixed-fee stages' | translate }}</div>
          <label *ngFor="let s of plannedStages()" style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" [checked]="stageSel.has(s.id)" (change)="toggleStage(s.id)" /> {{ s.description }} — {{ s.amount | number: '1.0-2' }}</label>
        </div>
        <div class="mt-2" *ngIf="billableCosts().length">
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Costs to charge' | translate }}</div>
          <label *ngFor="let c of billableCosts()" style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" [checked]="costSel.has(c.id)" (change)="toggleCost(c.id)" /> {{ c.description }} — {{ c.chargeAmount | number: '1.0-2' }}</label>
        </div>
        <div class="mt-2 flex gap-2 align-center" *ngIf="b.terms.contingencyPercent" style="flex-wrap:wrap;">
          <span>{{ 'Contingency fee ({p0}% of the amount recovered):' | translate: { p0: (b.terms.contingencyPercent | number: '1.0-2') } }}</span>
          <input class="form-control" style="max-width:11rem;" type="number" min="0" step="any" [placeholder]="'Amount recovered' | translate" name="br" [(ngModel)]="bill.recoveredAmount" (ngModelChange)="preview = null" />
        </div>
        <label class="mt-2" style="display:flex; gap:0.4rem; align-items:center;" *ngIf="b.summary.retainerBalance > 0"><input type="checkbox" name="ba" [(ngModel)]="bill.applyRetainer" (ngModelChange)="preview = null" /> {{ 'Use up the retainer ({p0} left) against the fees' | translate: { p0: (b.summary.retainerBalance | number: '1.0-2') } }}</label>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Due date' | translate }}</label><app-date-picker name="bd" [(ngModel)]="bill.dueDate"></app-date-picker></div>
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Note on the invoice' | translate }}</label><input class="form-control" name="bn" [(ngModel)]="bill.notes" /></div>
        </div>
        <div class="flex gap-2 mt-2"><button class="btn btn-outline-primary" (click)="doPreview()" [disabled]="busy">{{ 'Preview' | translate }}</button></div>
        <div class="mt-3" *ngIf="preview">
          <table class="table-sm">
            <tbody>
              <tr *ngFor="let l of preview.lines"><td>{{ l.description }}</td><td class="text-end">{{ l.amount | number: '1.0-2' }}</td></tr>
              <tr *ngIf="preview.retainerApplied > 0"><td>{{ 'Less retainer applied' | translate }}</td><td class="text-end">− {{ preview.retainerApplied | number: '1.0-2' }}</td></tr>
              <tr><td><strong>{{ 'Total (before tax)' | translate }}</strong></td><td class="text-end"><strong>{{ preview.total | number: '1.0-2' }}</strong></td></tr>
            </tbody>
          </table>
          <div class="alert alert-warning mt-2" *ngIf="preview.warning">{{ preview.warning }}</div>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="preview.total === 0 && preview.retainerApplied > 0">{{ 'The retainer covers all of it, so no invoice is made; the work is marked as billed against the retainer.' | translate }}</div>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-primary" (click)="doBill()" [disabled]="busy || !preview.lines.length || !!preview.warning">{{ preview.total === 0 ? ('Bill against the retainer' | translate) : ('Create draft invoice' | translate) }}</button>
            <button class="btn btn-outline-secondary" (click)="preview = null">{{ 'Close' | translate }}</button>
          </div>
        </div>

        <hr />
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;" *ngIf="b.terms.billingType !== 'PRO_BONO'">
          <span><strong>{{ 'Ask for a retainer' | translate }}</strong> <span class="text-muted" style="font-size:0.85rem;">{{ '(an advance invoice, taken off later fees)' | translate }}</span></span>
          <input class="form-control" style="max-width:10rem;" type="number" min="0" step="any" [placeholder]="'Amount' | translate" name="ra" [(ngModel)]="retainerAmount" />
          <button class="btn btn-outline-primary btn-sm" (click)="askRetainer()" [disabled]="busy || !retainerAmount">{{ 'Create retainer invoice' | translate }}</button>
        </div>
      </div>

      <!-- bills -->
      <div class="card" style="padding:1rem;" *ngIf="b.bills.length">
        <h5 style="margin:0;">{{ 'Bills' | translate }}</h5>
        <table class="stack table-sm mt-2">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Fees' | translate }}</th><th class="text-end">{{ 'Costs' | translate }}</th><th class="text-end">{{ 'Retainer used' | translate }}</th><th class="text-end">{{ 'Billed' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let x of b.bills" [style.opacity]="x.status === 'RELEASED' ? 0.5 : 1">
              <td data-label="Date">{{ x.createdAt | date: 'yyyy-MM-dd' }}</td>
              <td data-label="What">{{ x.kind === 'RETAINER' ? 'Retainer' : x.summary || 'Bill' }}<span class="text-muted" style="font-size:0.75rem;" *ngIf="x.recoveredAmount"> {{ '(recovered {p0})' | translate: { p0: (x.recoveredAmount | number: '1.0-2') } }}</span>
                <span class="badge badge-secondary" *ngIf="x.status === 'RELEASED'" style="margin-left:0.3rem;">{{ 'Taken back' | translate }}</span></td>
              <td data-label="Invoice"><a *ngIf="x.invoiceId && x.invoiceStatus !== 'DELETED'" [routerLink]="['/admin/invoices', x.invoiceId]">{{ x.invoiceNumber }}</a><span *ngIf="!x.invoiceId" class="text-muted">{{ 'none (retainer)' | translate }}</span>
                <span class="badge" style="margin-left:0.3rem;" *ngIf="x.invoiceStatus" [class.badge-warning]="x.invoiceStatus === 'DRAFT'" [class.badge-success]="x.invoiceStatus === 'ISSUED'" [class.badge-danger]="x.invoiceStatus === 'VOID' || x.invoiceStatus === 'DELETED'">{{ x.invoiceStatus.charAt(0) + x.invoiceStatus.slice(1).toLowerCase() }}</span></td>
              <td class="text-end" data-label="Fees">{{ x.feesAmount | number: '1.0-2' }}</td><td class="text-end" data-label="Costs">{{ x.expensesAmount | number: '1.0-2' }}</td>
              <td class="text-end" data-label="Retainer used">{{ x.retainerApplied ? (x.retainerApplied | number: '1.0-2') : '—' }}</td><td class="text-end" data-label="Billed">{{ x.amount | number: '1.0-2' }}</td>
              <td class="text-end" data-label="Paid">{{ x.amountPaid != null ? (x.amountPaid | number: '1.0-2') : '—' }}</td>
              <td><button class="btn btn-sm btn-outline-secondary" *ngIf="b.canBill && x.status === 'ACTIVE'" (click)="release(x.id, x.stale)" [disabled]="busy">{{ x.stale ? ('Free the work' | translate) : ('Take back' | translate) }}</button></td>
            </tr>
          </tbody>
        </table>
        <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Taking a bill back deletes its draft invoice and frees the hours, costs and stages to be billed again. An issued invoice must be voided first.' | translate }}</div>
      </div>
    </ng-container>
  `,
})
export class MatterBillingComponent implements OnChanges {
  @Input() matterId!: number;
  @Output() changed = new EventEmitter<void>();

  b: MatterBilling | null = null;
  staff: Option[] = [];
  expenseAccounts: Account[] = [];
  error: string | null = null;
  info: string | null = null;
  lastInvoiceId: number | null = null;
  busy = false;

  editTerms = false;
  tf: FeeTermsPayload = { billingType: 'HOURLY', billingNote: null, hourlyRate: null, fixedFee: null, retainerAmount: null, contingencyPercent: null };
  termsNote = '';
  rate: { userId: number | null; hourlyRate: number | null } = { userId: null, hourlyRate: null };
  selected = new Set<number>();
  rejectReason = '';
  cost: { expenseDate: string | null; description: string; supplierName: string; amount: number | null; chargeAmount: number | null; expenseAccountId: number | null } =
    { expenseDate: null, description: '', supplierName: '', amount: null, chargeAmount: null, expenseAccountId: null };
  chargeTouched = false;
  stage: { description: string; amount: number | null; dueDate: string | null } = { description: '', amount: null, dueDate: null };
  bill: { includeTime: boolean; timeTo: string | null; recoveredAmount: number | null; applyRetainer: boolean; dueDate: string | null; notes: string } =
    { includeTime: true, timeTo: null, recoveredAmount: null, applyRetainer: true, dueDate: null, notes: '' };
  stageSel = new Set<number>();
  costSel = new Set<number>();
  preview: BillPreview | null = null;
  retainerAmount: number | null = null;
  types = Object.entries(BILLING_LABEL).map(([key, label]) => ({ key, label }));

  constructor(private api: LawApiService, private accountApi: AccountApiService, private modules: ModuleApiService) {}

  ngOnChanges(): void {
    if (this.matterId && !this.b) {
      this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
      if (this.modules.on('EXPENSES')) this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.expenseAccounts = a), error: () => {} });
      this.load();
    }
  }

  label(t: string): string { return (BILLING_LABEL as Record<string, string>)[t] ?? t; }
  usesTime(t: string): boolean { return t === 'HOURLY' || t === 'RETAINER'; }
  waiting() { return (this.b?.time ?? []).filter((e) => e.status === 'SUBMITTED'); }
  plannedStages() { return (this.b?.stages ?? []).filter((s) => s.status === 'PLANNED'); }
  billableCosts() { return (this.b?.costs ?? []).filter((c) => !c.billed && c.chargeAmount > 0); }

  load(): void {
    this.api.billing(this.matterId).subscribe({
      next: (x) => this.show(x),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the billing'),
    });
  }

  private show(x: MatterBilling): void {
    this.b = x;
    this.preview = null;
    this.selected = new Set([...this.selected].filter((id) => x.time.some((e) => e.id === id && e.status === 'SUBMITTED')));
    this.stageSel = new Set([...this.stageSel].filter((id) => x.stages.some((s) => s.id === id && s.status === 'PLANNED')));
    this.costSel = new Set([...this.costSel].filter((id) => x.costs.some((c) => c.id === id && !c.billed)));
    if (this.retainerAmount == null && x.terms.retainerAmount) this.retainerAmount = x.terms.retainerAmount;
    this.bill.applyRetainer = x.summary.retainerBalance > 0;
    if (!this.usesTime(x.terms.billingType)) this.bill.includeTime = false;
  }

  private run(call: { subscribe: (o: { next: (x: MatterBilling) => void; error: (e: any) => void }) => unknown }, message?: string, after?: () => void): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    this.lastInvoiceId = null;
    call.subscribe({
      next: (x) => { this.busy = false; this.show(x); if (message) this.info = message; if (after) after(); this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Something went wrong'; },
    });
  }

  // ---- terms and rates
  startTerms(): void {
    if (!this.b) return;
    const t = this.b.terms;
    this.tf = { billingType: t.billingType, billingNote: t.billingNote, hourlyRate: t.hourlyRate, fixedFee: t.fixedFee, retainerAmount: t.retainerAmount, contingencyPercent: t.contingencyPercent };
    this.termsNote = t.billingNote ?? '';
    this.editTerms = true;
  }
  saveTerms(): void {
    this.run(this.api.saveTerms(this.matterId, { ...this.tf, billingNote: this.termsNote.trim() || null }), 'Fee terms saved.', () => (this.editTerms = false));
  }
  saveRate(): void {
    if (!this.rate.userId || this.rate.hourlyRate == null) return;
    this.run(this.api.setRate(this.matterId, this.rate.userId, this.rate.hourlyRate), 'Rate saved.', () => (this.rate = { userId: null, hourlyRate: null }));
  }
  removeRate(userId: number): void { this.run(this.api.removeRate(this.matterId, userId), 'Rate removed.'); }

  // ---- hours
  toggle(id: number): void { if (!this.selected.delete(id)) this.selected.add(id); }
  selectWaiting(): void { this.selected = new Set(this.waiting().map((e) => e.id)); }
  approve(): void { this.run(this.api.approveTime(this.matterId, [...this.selected]), 'Time approved.', () => (this.selected = new Set())); }
  reject(): void { this.run(this.api.rejectTime(this.matterId, [...this.selected], this.rejectReason.trim()), 'Time sent back.', () => { this.selected = new Set(); this.rejectReason = ''; }); }

  // ---- costs
  costChargeDefault(): void { if (!this.chargeTouched) this.cost.chargeAmount = this.cost.amount; }
  addCost(): void {
    this.run(this.api.addCost(this.matterId, { ...this.cost, description: this.cost.description.trim(), supplierName: this.cost.supplierName.trim() || null }), 'Cost added.',
      () => { this.cost = { expenseDate: null, description: '', supplierName: '', amount: null, chargeAmount: null, expenseAccountId: null }; this.chargeTouched = false; });
  }
  deleteCost(id: number): void { if (confirm('Delete this cost?')) this.run(this.api.deleteCost(this.matterId, id), 'Cost deleted.'); }

  // ---- stages
  addStage(): void {
    this.run(this.api.addStage(this.matterId, { description: this.stage.description.trim(), amount: this.stage.amount, dueDate: this.stage.dueDate }), 'Stage added.',
      () => (this.stage = { description: '', amount: null, dueDate: null }));
  }
  deleteStage(id: number): void { if (confirm('Delete this stage?')) this.run(this.api.deleteStage(this.matterId, id), 'Stage deleted.'); }
  toggleStage(id: number): void { if (!this.stageSel.delete(id)) this.stageSel.add(id); this.preview = null; }
  toggleCost(id: number): void { if (!this.costSel.delete(id)) this.costSel.add(id); this.preview = null; }

  // ---- billing
  private billBody(): BillRequestPayload {
    return {
      includeTime: this.bill.includeTime, timeTo: this.bill.timeTo, stageIds: [...this.stageSel], costIds: [...this.costSel],
      recoveredAmount: this.bill.recoveredAmount, applyRetainer: this.bill.applyRetainer, dueDate: this.bill.dueDate, notes: this.bill.notes.trim() || null,
    };
  }
  doPreview(): void {
    this.busy = true;
    this.error = null;
    this.api.previewBill(this.matterId, this.billBody()).subscribe({
      next: (p) => { this.busy = false; this.preview = p; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not prepare the invoice'; },
    });
  }
  doBill(): void {
    this.busy = true;
    this.error = null;
    this.api.bill(this.matterId, this.billBody()).subscribe({
      next: (r: BillResult) => {
        this.busy = false;
        this.stageSel = new Set();
        this.costSel = new Set();
        this.bill.recoveredAmount = null;
        this.load();
        this.info = r.invoiceNumber ? `Draft invoice ${r.invoiceNumber} created.` : 'Billed against the retainer.';
        this.lastInvoiceId = r.invoiceId;
        this.changed.emit();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not create the invoice'; },
    });
  }
  askRetainer(): void {
    if (!this.retainerAmount) return;
    this.busy = true;
    this.error = null;
    this.api.requestRetainer(this.matterId, { amount: this.retainerAmount, dueDate: this.bill.dueDate, notes: null }).subscribe({
      next: (r: BillResult) => { this.busy = false; this.load(); this.info = `Retainer invoice ${r.invoiceNumber} created.`; this.lastInvoiceId = r.invoiceId; this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not create the retainer invoice'; },
    });
  }
  release(billId: number, stale: boolean): void {
    const q = stale ? 'Free the hours, costs and stages of this bill so they can be billed again?' : 'Take this bill back? Its draft invoice is deleted and the work can be billed again.';
    if (confirm(q)) this.run(this.api.releaseBill(this.matterId, billId), 'Bill taken back.');
  }
}
