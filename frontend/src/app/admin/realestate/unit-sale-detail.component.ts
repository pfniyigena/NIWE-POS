import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { RePaymentsComponent, RePrintComponent } from './re-print.component';
import { ReSaleCollectionComponent } from './re-sale-collection.component';
import { AuthService } from '../../core/auth.service';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { InstallmentPreview } from '../../core/api/installment-api.service';
import { PaymentMethodOption } from '../../core/models';
import { RealEstateApiService, UNIT_SALE_STAGE_LABEL, UnitPlanPayload, UnitSaleDto } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const today = (): string => new Date().toISOString().slice(0, 10);

/** Real Estate > Unit sales > one sale: reserve, sign the agreement (one invoice for the price), pay at once or by an instalment plan, complete. */
@Component({
  selector: 'app-unit-sale-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ReDocumentsComponent, RePrintComponent, RePaymentsComponent, ReSaleCollectionComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!s && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="s">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/realestate/unit-sales" style="font-size:0.85rem;">{{ '← Unit sales' | translate }}</a>
            <h3>{{ '{p0} · Unit {p1}' | translate: { p0: (s.code), p1: (s.unitNo) } }}
              <span class="badge" [class.badge-success]="s.stage === 'COMPLETED'" [class.badge-danger]="s.stage === 'CANCELLED'" [class.badge-warning]="s.stage === 'RESERVED' || s.stage === 'SOLD'" style="font-size:0.8rem; vertical-align:middle;">{{ stageName(s.stage) }}</span>
              <span class="badge badge-danger" *ngIf="s.overdue" style="font-size:0.8rem; vertical-align:middle; margin-left:0.3rem;">{{ 'Late' | translate }}</span></h3>
          </div>
          <div class="flex gap-2" *ngIf="canManage && (s.stage === 'RESERVED' || s.stage === 'CANCELLED')">
            <button class="btn btn-outline-secondary" type="button" *ngIf="s.stage === 'RESERVED'" (click)="editOpen = !editOpen; fillEdit()">{{ 'Edit' | translate }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <app-re-print kind="sale" [id]="s.id" [stage]="s.stage"></app-re-print>
        <div class="steps mb-3">
          <span *ngFor="let st of steps" class="step" [class.done]="stepDone(st)" [class.now]="s.stage === st">{{ stageName(st) }}</span>
          <span class="step cancelled" *ngIf="s.stage === 'CANCELLED'">{{ 'Cancelled' | translate }}</span>
        </div>
        <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
        <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>
        <div class="alert alert-warning" *ngIf="s.stage === 'CANCELLED' && s.cancelReason">{{ 'Cancelled: {p0}' | translate: { p0: (s.cancelReason) } }}</div>

        <div class="card mb-3" style="padding:1rem; max-width:46rem;" *ngIf="editOpen && s.stage === 'RESERVED'">
          <h4 class="mb-2">{{ 'Change the reservation' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div><label class="form-label">{{ 'Buyer' | translate }}</label><input class="form-control" style="width:14rem;" name="eb" [(ngModel)]="e.buyerName" maxlength="200" /></div>
            <div><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" style="width:10rem;" name="ep" [(ngModel)]="e.buyerPhone" maxlength="40" /></div>
            <div><label class="form-label">{{ 'Agreed price' | translate }}</label><input class="form-control" style="width:12rem;" type="number" min="0" name="ea" [(ngModel)]="e.agreedPrice" /></div>
            <div><label class="form-label">{{ 'Held until' | translate }}</label><input class="form-control" type="date" name="ex" [(ngModel)]="e.reservationExpiry" /></div>
          </div>
          <div class="form-group mt-2"><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control" name="en" [(ngModel)]="e.notes" maxlength="2000" /></div>
          <div class="mt-2"><button class="btn btn-primary" type="button" (click)="saveEdit()" [disabled]="busy">{{ 'Save' | translate }}</button></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'The sale' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Unit' | translate }}</td><td><a [routerLink]="['/admin/realestate/units', s.unitId]">{{ s.unitNo }}</a><span class="text-muted" *ngIf="s.block"> · {{ s.block }}</span></td></tr>
              <tr><td class="text-muted">{{ 'Project' | translate }}</td><td><a [routerLink]="['/admin/realestate/projects', s.projectId]">{{ s.projectName }}</a> <span class="text-muted">{{ s.projectCode }}</span></td></tr>
              <tr><td class="text-muted">{{ 'Buyer' | translate }}</td><td>{{ s.buyerName }}<div class="text-muted" style="font-size:0.8rem;">{{ s.buyerPhone || '' }} {{ s.buyerEmail || '' }}</div></td></tr>
              <tr *ngIf="s.leadId"><td class="text-muted">{{ 'Lead' | translate }}</td><td><a [routerLink]="['/admin/realestate/leads', s.leadId]">{{ s.leadName }}</a></td></tr>
              <tr *ngIf="s.buyerCustomerId"><td class="text-muted">{{ 'Customer' | translate }}</td><td><a [routerLink]="['/admin/customers', s.buyerCustomerId]">{{ 'Open the customer record' | translate }}</a></td></tr>
              <tr><td class="text-muted">{{ 'Agent' | translate }}</td><td>{{ s.agentName || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Reserved' | translate }}</td><td>{{ s.reservedOn ? (s.reservedOn | date: 'd MMM y') : '—' }}<span *ngIf="s.reservationExpiry && s.stage === 'RESERVED'"> {{ '· held to {p0}' | translate: { p0: (s.reservationExpiry | date: 'd MMM y') } }}</span></td></tr>
              <tr *ngIf="s.agreementDate"><td class="text-muted">{{ 'Agreement signed' | translate }}</td><td>{{ s.agreementDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="s.completedDate"><td class="text-muted">{{ 'Paid in full' | translate }}</td><td>{{ s.completedDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="s.notes"><td class="text-muted">{{ 'Notes' | translate }}</td><td style="white-space:pre-wrap;">{{ s.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'The money' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:11rem;">{{ 'List price' | translate }}</td><td class="text-end">{{ s.listPrice | number: '1.0-0' }}</td></tr>
              <tr *ngIf="s.discount !== 0"><td class="text-muted">{{ s.discount > 0 ? ('Discount' | translate) : ('Premium' | translate) }}</td><td class="text-end">{{ s.discount > 0 ? '−' : '+' }}{{ (s.discount > 0 ? s.discount : -s.discount) | number: '1.0-0' }}</td></tr>
              <tr><td class="text-muted"><strong>{{ 'Agreed price' | translate }}</strong></td><td class="text-end"><strong>{{ s.agreedPrice | number: '1.0-0' }}</strong></td></tr>
              <ng-container *ngIf="s.invoice">
                <tr><td class="text-muted">{{ 'Invoice' | translate }}</td><td class="text-end"><a [routerLink]="['/admin/invoices', s.invoice.id]">{{ s.invoice.invoiceNumber }}</a></td></tr>
                <tr><td class="text-muted">{{ 'Paid by the buyer' | translate }}</td><td class="text-end">{{ s.invoice.paid | number: '1.0-0' }}</td></tr>
                <tr><td class="text-muted">{{ 'Still to pay' | translate }}</td><td class="text-end">{{ s.invoice.outstanding | number: '1.0-0' }}</td></tr>
              </ng-container>
            </tbody></table>
          </div>
        </div>

        <!-- what to do next -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="canManage && s.canEdit">
          <h4 class="mb-2">{{ 'Next step' | translate }}</h4>
          <ng-container *ngIf="s.stage === 'RESERVED'">
            <p class="text-muted mb-2" style="font-size:0.9rem;">{{ 'When the buyer signs the sale agreement, record it. One invoice for the agreed price is made and issued to the buyer, and the unit counts as sold. Then choose how the buyer pays.' | translate }}</p>
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">{{ 'Signed on' | translate }}</label><input class="form-control" type="date" name="ad" [(ngModel)]="agreementDate" /></div>
              <button class="btn btn-primary" type="button" (click)="agree()" [disabled]="busy">{{ 'Record the agreement' | translate }}</button>
            </div>
          </ng-container>
          <ng-container *ngIf="s.stage === 'SOLD'">
            <div *ngIf="!activePlan() && (s.invoice?.outstanding ?? 0) > 0">
              <p class="text-muted mb-2" style="font-size:0.9rem;">{{ 'The buyer can pay the invoice in one go (record the payment on the invoice), or you can set up a payment plan: an optional deposit now and dated instalments, with interest and late fees if you want them.' | translate }}</p>
              <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="planOpen = !planOpen; preview = null">{{ 'Set up a payment plan…' | translate }}</button>
                <a class="btn btn-sm btn-outline-secondary" *ngIf="s.invoice" [routerLink]="['/admin/invoices', s.invoice.id, 'record-payment']">{{ 'Record a payment on the invoice' | translate }}</a>
              </div>
              <div class="card mb-2" style="padding:0.75rem;" *ngIf="planOpen">
                <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                  <div><label class="form-label">{{ 'Deposit now' | translate }}</label><input class="form-control" style="width:11rem;" type="number" min="0" name="pdp" [(ngModel)]="plan.deposit" (ngModelChange)="preview = null" /></div>
                  <div *ngIf="(plan.deposit ?? 0) > 0"><label class="form-label">{{ 'Paid by' | translate }}</label>
                    <select class="form-select" name="pdm" [(ngModel)]="plan.depositMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                  <div><label class="form-label">{{ 'Instalments' | translate }}</label><input class="form-control" style="width:7rem;" type="number" min="2" max="60" name="pn" [(ngModel)]="plan.numberOfInstallments" (ngModelChange)="preview = null" /></div>
                  <div><label class="form-label">{{ 'Every' | translate }}</label>
                    <select class="form-select" name="pfr" [(ngModel)]="plan.frequency" (ngModelChange)="preview = null"><option value="WEEKLY">{{ 'Week' | translate }}</option><option value="BIWEEKLY">{{ '2 weeks' | translate }}</option><option value="MONTHLY">{{ 'Month' | translate }}</option><option value="QUARTERLY">{{ '3 months' | translate }}</option></select></div>
                  <div><label class="form-label">{{ 'First due' | translate }}</label><input class="form-control" type="date" name="pfd" [(ngModel)]="plan.firstDueDate" (ngModelChange)="preview = null" /></div>
                </div>
                <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
                  <div><label class="form-label">{{ 'Interest' | translate }}</label>
                    <select class="form-select" name="pim" [(ngModel)]="plan.interestMethod" (ngModelChange)="preview = null"><option value="NONE">{{ 'None' | translate }}</option><option value="FLAT">{{ 'Flat' | translate }}</option><option value="REDUCING">{{ 'Reducing balance' | translate }}</option></select></div>
                  <div *ngIf="plan.interestMethod !== 'NONE'"><label class="form-label">{{ 'Rate % a year' | translate }}</label><input class="form-control" style="width:8rem;" type="number" min="0" step="0.01" name="pir" [(ngModel)]="plan.interestRate" (ngModelChange)="preview = null" /></div>
                  <div><label class="form-label">{{ 'Grace days' | translate }}</label><input class="form-control" style="width:6rem;" type="number" min="0" max="90" name="pgd" [(ngModel)]="plan.graceDays" /></div>
                  <div><label class="form-label">{{ 'Late fee (fixed)' | translate }}</label><input class="form-control" style="width:8rem;" type="number" min="0" name="plf" [(ngModel)]="plan.lateFeeFixed" /></div>
                  <div><label class="form-label">{{ 'Late fee %' | translate }}</label><input class="form-control" style="width:7rem;" type="number" min="0" step="0.01" name="plp" [(ngModel)]="plan.lateFeePercent" /></div>
                  <button class="btn btn-outline-primary" type="button" (click)="doPreview()" [disabled]="busy">{{ 'Preview' | translate }}</button>
                  <button class="btn btn-primary" type="button" (click)="makePlan()" [disabled]="busy || !preview">{{ 'Create the plan' | translate }}</button>
                </div>
                <div *ngIf="preview" class="mt-2">
                  <div style="font-size:0.9rem;">{{ 'Deposit {p0} · spread over {p1} instalments: {p2}' | translate: { p0: (preview.deposit | number: '1.0-0'), p1: (preview.lines.length), p2: (preview.financedAmount | number: '1.0-0') } }}<span *ngIf="preview.totalInterest"> {{ '+ interest {p0}' | translate: { p0: (preview.totalInterest | number: '1.0-0') } }}</span> = <strong>{{ preview.totalPayable | number: '1.0-0' }}</strong></div>
                  <div style="max-height:12rem; overflow:auto;">
                    <table class="table-sm"><thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Principal' | translate }}</th><th class="text-end">{{ 'Interest' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th></tr></thead>
                      <tbody><tr *ngFor="let l of preview.lines"><td>{{ l.seq }}</td><td>{{ l.dueDate | date: 'd MMM y' }}</td><td class="text-end">{{ l.principalDue | number: '1.0-0' }}</td><td class="text-end">{{ l.interestDue | number: '1.0-0' }}</td><td class="text-end">{{ l.totalDue | number: '1.0-0' }}</td></tr></tbody></table>
                  </div>
                </div>
              </div>
            </div>
            <div *ngIf="activePlan()" class="mb-2 text-muted" style="font-size:0.9rem;">
              {{ 'The buyer is paying by plan {p0}. Record instalments on the plan page; payments made straight on the invoice count too.' | translate: { p0: (s.plan!.plan.planNumber) } }}
              <a [routerLink]="['/admin/installments', s.plan!.plan.id]">{{ 'Open the plan' | translate }}</a>
            </div>
            <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">{{ 'Paid in full on' | translate }}</label><input class="form-control" type="date" name="cd" [(ngModel)]="completedDate" /></div>
              <button class="btn btn-success" type="button" (click)="complete(false)" [disabled]="busy">{{ 'Mark as paid in full' | translate }}</button>
              <label *ngIf="isAdmin" style="display:flex; gap:0.4rem; align-items:center;" [title]="'Only an administrator can do this' | translate"><input type="checkbox" name="fc" [(ngModel)]="forceComplete" /> {{ 'Even though it is not fully paid' | translate }}</label>
              <button class="btn btn-outline-danger" type="button" *ngIf="forceComplete && isAdmin" (click)="complete(true)" [disabled]="busy">{{ 'Complete anyway' | translate }}</button>
            </div>
          </ng-container>
          <div class="mt-3">
            <button class="btn btn-sm btn-outline-danger" type="button" (click)="cancelOpen = !cancelOpen">{{ 'Cancel this sale…' | translate }}</button>
            <div class="flex gap-2 mt-2" *ngIf="cancelOpen">
              <input class="form-control" style="max-width:26rem;" name="cr" [(ngModel)]="cancelReason" maxlength="300" [placeholder]="'Why? (e.g. buyer could not raise the money)' | translate" />
              <button class="btn btn-sm btn-danger" type="button" (click)="cancel()" [disabled]="busy">{{ 'Cancel the sale' | translate }}</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="cancelOpen">{{ 'The unit goes back on sale. An unpaid invoice is voided and its payment plan cancelled. If the buyer has already paid, cancel the invoice and refund first, in Invoices.' | translate }}</div>
          </div>
        </div>

        <!-- the payment plan -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="s.plan">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>{{ 'Payment plan {p0}' | translate: { p0: (s.plan.plan.planNumber) } }}
              <span class="badge" [class.badge-success]="s.plan.plan.status === 'COMPLETED'" [class.badge-warning]="s.plan.plan.status === 'ACTIVE'" [class.badge-secondary]="s.plan.plan.status === 'CANCELLED'" style="font-size:0.75rem;">{{ s.plan.plan.status }}</span></h4>
            <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/installments', s.plan.plan.id]">{{ 'Open in Instalments' | translate }}</a>
          </div>
          <div class="text-muted mb-2" style="font-size:0.9rem;">
            {{ 'Deposit {p0} · {p1} instalments of the {p2} financed' | translate: { p0: (s.plan.terms.deposit | number: '1.0-0'), p1: (s.plan.plan.numberOfInstallments), p2: (s.plan.plan.financedAmount | number: '1.0-0') } }}<span *ngIf="s.plan.plan.totalInterest"> {{ '· interest {p0}' | translate: { p0: (s.plan.plan.totalInterest | number: '1.0-0') } }}</span>
            {{ '· paid {p0}, outstanding {p1}' | translate: { p0: (s.plan.plan.paid | number: '1.0-0'), p1: (s.plan.plan.outstanding | number: '1.0-0') } }}<span *ngIf="s.plan.plan.overdueAmount"> · <span style="color:#b91c1c;">{{ 'overdue {p0}' | translate: { p0: (s.plan.plan.overdueAmount | number: '1.0-0') } }}</span></span>
          </div>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Principal' | translate }}</th><th class="text-end">{{ 'Interest' | translate }}</th><th class="text-end">{{ 'Late fee' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let i of s.plan.installments">
              <td data-label="No.">{{ i.seq }}</td><td data-label="Due">{{ i.dueDate | date: 'd MMM y' }}</td>
              <td class="text-end" data-label="Principal">{{ i.principalDue | number: '1.0-0' }}</td><td class="text-end" data-label="Interest">{{ i.interestDue | number: '1.0-0' }}</td><td class="text-end" data-label="Late fee">{{ i.lateFee | number: '1.0-0' }}</td>
              <td class="text-end" data-label="Total">{{ i.totalDue | number: '1.0-0' }}</td><td class="text-end" data-label="Paid">{{ i.paid | number: '1.0-0' }}</td><td class="text-end" data-label="Balance">{{ i.balance | number: '1.0-0' }}</td>
              <td data-label="Status"><span class="badge" [class.badge-success]="i.state === 'PAID'" [class.badge-danger]="i.state === 'OVERDUE'" [class.badge-warning]="i.state === 'PARTIAL'" [class.badge-secondary]="i.state === 'PENDING'">{{ i.state }}</span></td>
            </tr></tbody>
          </table>
        </div>
        <div class="card mb-3" style="padding:1rem;" *ngIf="s.invoice">
          <app-re-payments kind="unit-sales" [id]="s.id" [refresh]="s.invoice.paid"></app-re-payments>
        </div>
        <div class="card mb-3" style="padding:1rem;" *ngIf="s.stage === 'SOLD' || s.stage === 'COMPLETED'">
          <app-re-sale-collection [saleId]="s.id" [stage]="s.stage" [buyerEmail]="s.buyerEmail" [hasPlan]="activePlan()"></app-re-sale-collection>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="UNIT_SALE" [subjectId]="s.id" [canManage]="canManage" defaultCategory="SALE_AGREEMENT"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
  styles: [`
    .steps { display: flex; gap: 0.35rem; flex-wrap: wrap; }
    .step { padding: 0.25rem 0.7rem; border-radius: 1rem; font-size: 0.8rem; background: rgba(127, 127, 127, 0.15); }
    .step.done { background: #0f766e; color: #fff; }
    .step.now { background: #2563eb; color: #fff; font-weight: 600; }
    .step.cancelled { background: #b91c1c; color: #fff; }
  `],
})
export class UnitSaleDetailComponent implements OnInit {
  s: UnitSaleDto | null = null;
  error: string | null = null;
  actionError: string | null = null;
  notice: string | null = null;
  busy = false;
  canManage: boolean;
  isAdmin: boolean;
  steps = ['RESERVED', 'SOLD', 'COMPLETED'];
  agreementDate = today();
  completedDate = today();
  forceComplete = false;
  cancelOpen = false;
  cancelReason = '';
  editOpen = false;
  e = { buyerName: '', buyerPhone: '', agreedPrice: null as number | null, reservationExpiry: '', notes: '' };
  planOpen = false;
  preview: InstallmentPreview | null = null;
  methods: PaymentMethodOption[] = [];
  plan: UnitPlanPayload = {
    deposit: null, depositMethod: null, numberOfInstallments: 12, frequency: 'MONTHLY', firstDueDate: null, interestMethod: 'NONE', interestRate: 0, graceDays: 5, lateFeeFixed: 0,
    lateFeePercent: 0, remindersEnabled: true, wholeUnits: null, notes: null,
  };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router, private paymentApi: PaymentMethodApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
    this.isAdmin = auth.isAdmin();
  }

  ngOnInit(): void {
    this.paymentApi.listActive().subscribe({ next: (m) => { this.methods = m; this.plan.depositMethod = m[0]?.code ?? null; }, error: () => {} });
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    this.plan.firstDueDate = d.toISOString().slice(0, 10);
    this.load();
  }

  private id(): number { return Number(this.route.snapshot.paramMap.get('id')); }

  load(): void {
    this.api.unitSale(this.id()).subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? T('Could not load the sale')) });
  }

  stageName(st: string): string { return (UNIT_SALE_STAGE_LABEL as Record<string, string>)[st] ?? st; }
  stepDone(st: string): boolean { return this.steps.indexOf(st) < this.steps.indexOf(this.s!.stage) || this.s!.stage === 'COMPLETED'; }
  activePlan(): boolean { return !!this.s?.plan && this.s.plan.plan.status === 'ACTIVE'; }

  private run(call: import('rxjs').Observable<UnitSaleDto>, done: () => void): void {
    this.actionError = null;
    this.notice = null;
    this.busy = true;
    call.subscribe({
      next: (s) => { this.busy = false; this.s = s; done(); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'That did not work'; },
    });
  }

  fillEdit(): void {
    const s = this.s!;
    this.e = { buyerName: s.buyerName, buyerPhone: s.buyerPhone ?? '', agreedPrice: s.agreedPrice, reservationExpiry: s.reservationExpiry ?? '', notes: s.notes ?? '' };
  }

  saveEdit(): void {
    const e = this.e;
    this.run(this.api.updateUnitSale(this.s!.id, { buyerName: e.buyerName, buyerPhone: e.buyerPhone, agreedPrice: num(e.agreedPrice), reservationExpiry: e.reservationExpiry || null, notes: e.notes }),
      () => { this.editOpen = false; this.notice = T('Saved.'); });
  }

  agree(): void { this.run(this.api.unitSaleAgreement(this.s!.id, this.agreementDate || null), () => (this.notice = T('Agreement recorded and the invoice issued. Choose how the buyer pays.'))); }

  private planBody(): UnitPlanPayload | null {
    const p = this.plan;
    if (!num(p.numberOfInstallments) || (num(p.numberOfInstallments) ?? 0) < 2) { this.actionError = 'Choose at least 2 instalments.'; return null; }
    if (!p.firstDueDate) { this.actionError = 'Choose the date the first instalment falls due.'; return null; }
    if ((num(p.deposit) ?? 0) > 0 && !p.depositMethod) { this.actionError = 'Choose how the deposit is paid.'; return null; }
    this.actionError = null;
    return { ...p, deposit: num(p.deposit) ?? 0, depositMethod: (num(p.deposit) ?? 0) > 0 ? p.depositMethod : null, numberOfInstallments: num(p.numberOfInstallments), interestRate: num(p.interestRate) ?? 0,
      graceDays: num(p.graceDays) ?? 0, lateFeeFixed: num(p.lateFeeFixed) ?? 0, lateFeePercent: num(p.lateFeePercent) ?? 0 };
  }

  doPreview(): void {
    const body = this.planBody();
    if (!body) return;
    this.busy = true;
    this.api.unitSalePlanPreview(this.s!.id, body).subscribe({
      next: (r) => { this.busy = false; this.preview = r; },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'Could not work out the plan'; },
    });
  }

  makePlan(): void {
    const body = this.planBody();
    if (!body) return;
    this.run(this.api.unitSalePlan(this.s!.id, body), () => { this.planOpen = false; this.preview = null; this.notice = T('Payment plan created.'); });
  }

  complete(force: boolean): void { this.run(this.api.completeUnitSale(this.s!.id, force, this.completedDate || null), () => (this.notice = T('The sale is paid in full.'))); }

  cancel(): void {
    if (!this.cancelReason.trim()) { this.actionError = 'Say why the sale is cancelled.'; return; }
    this.run(this.api.cancelUnitSale(this.s!.id, this.cancelReason.trim()), () => { this.cancelOpen = false; this.cancelReason = ''; });
  }

  remove(): void {
    if (!confirm(T('Delete sale {p0}?', { p0: this.s!.code }))) return;
    this.api.deleteUnitSale(this.s!.id).subscribe({ next: () => this.router.navigate(['/admin/realestate/unit-sales']), error: (e) => (this.actionError = e?.error?.message ?? 'Could not delete the sale') });
  }
}
