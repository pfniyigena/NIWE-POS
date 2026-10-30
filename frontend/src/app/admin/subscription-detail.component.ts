import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { DatePickerComponent } from '../shared/date-picker.component';
import { SubscriptionPlanApiService } from '../core/api/subscription-plan-api.service';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { SubscriptionApiService, SubscriptionChangeQuote, SubscriptionEvent } from '../core/api/subscription-api.service';
import { openPdfForPrint } from '../core/download-file';
import { BillingInterval, Invoice, Subscription, SubscriptionPlan, TaxRateOption } from '../core/models';

/**
 * Admin > Sales > Subscriptions > detail — `/admin/subscriptions/:id`. Shows the subscription's
 * own state plus every invoice the daily billing sweep (or a manual "Bill now") has generated for
 * it (see backend SubscriptionService.billingHistory/InvoiceService.generateForSubscription).
 *
 * <p>Pause/resume toggle between ACTIVE and PAUSED; cancel is one-way into CANCELLED (see backend
 * Subscription's javadoc on {@code SubscriptionStatus}).
 *
 * <p>Fourier: "Print also subscription" — "Print" opens an A4 PDF of this subscription's own
 * terms plus its billing history in a new tab (see {@code SubscriptionService.pdf}), same
 * convention as the Invoice/Sale Order detail screens' own "Print" buttons.
 */
@Component({
  selector: 'app-subscription-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Subscription{{ sub ? ' — ' + sub.customerName : '' }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-primary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
          <a class="btn btn-link" routerLink="/admin/subscriptions">Back to subscriptions</a>
        </div>
      </div>

      <ng-container *ngIf="sub as s">
        <div class="alert alert-info" *ngIf="s.onTrial">Free trial until {{ s.trialEndDate }} — the first bill is on {{ s.nextBillingDate }}.</div>
        <div class="alert alert-warning" *ngIf="s.status === 'PAUSED'">Paused<span *ngIf="s.pauseReason"> — {{ s.pauseReason }}</span>. {{ s.resumeOn ? 'Resumes by itself on ' + s.resumeOn + '.' : 'It stays paused until you resume it.' }}</div>
        <div class="alert alert-warning" *ngIf="s.cancelAtPeriodEnd">Cancellation scheduled: this subscription ends on {{ s.nextBillingDate }}{{ s.billInAdvance ? ' (no further bill)' : ' (after the last cycle is billed)' }}.
          <button class="btn btn-sm btn-outline-secondary" style="margin-left:0.5rem;" (click)="undoCancel()" [disabled]="busy">Withdraw cancellation</button></div>
        <div class="alert alert-secondary" *ngIf="s.status === 'ENDED'">This fixed-term subscription ended on {{ s.endedAt | date: 'mediumDate' }} after {{ s.termCycles }} cycle(s).</div>
        <div class="alert alert-info" *ngIf="s.creditBalance > 0">Credit of {{ s.creditBalance | number: '1.2-2' }} (before tax) will be taken off the next invoice(s).</div>
        <div class="alert alert-danger" *ngIf="s.dunningCount > 0">{{ s.dunningCount }} overdue reminder(s) e-mailed for the latest unpaid invoice (last on {{ s.dunningLastOn }}).</div>

        <div class="card mb-3" style="padding:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><strong>Customer:</strong> {{ s.customerName }}</div>
            <div><strong>Plan:</strong> {{ s.planName || '—' }}</div>
            <div><strong>Price:</strong> {{ s.price | number: '1.2-2' }}</div>
            <div *ngIf="s.quantity > 1"><strong>Quantity:</strong> {{ s.quantity }}</div>
            <div *ngIf="s.discountPercent > 0"><strong>Discount:</strong> {{ s.discountPercent | number: '1.0-2' }}%</div>
            <div *ngIf="s.taxCode"><strong>Tax:</strong> {{ s.taxCode }} {{ s.taxRatePercent | number: '1.0-2' }}%{{ s.taxInclusive ? ' (included)' : '' }}</div>
            <div><strong>Per cycle:</strong> {{ s.cycleAmount | number: '1.2-2' }} <span class="text-muted">(before tax)</span></div>
            <div><strong>Interval:</strong> {{ s.billingInterval }}</div>
            <div><strong>Billing timing:</strong> {{ s.billInAdvance ? 'Prepaid' : 'Paid after service' }}</div>
            <div><strong>Status:</strong> <span class="badge" [ngClass]="statusClass(s.status)">{{ s.status }}</span></div>
            <div><strong>Start date:</strong> {{ s.startDate }}</div>
            <div *ngIf="s.status === 'ACTIVE' || s.status === 'PAUSED'"><strong>Next billing:</strong> {{ s.nextBillingDate }}</div>
            <div *ngIf="s.lastBilledAt"><strong>Last billed:</strong> {{ s.lastBilledAt | date: 'medium' }}</div>
            <div *ngIf="s.setupFee > 0"><strong>Setup fee:</strong> {{ s.setupFee | number: '1.2-2' }} {{ s.setupFeeBilled ? '(billed)' : '(on the first invoice)' }}</div>
            <div *ngIf="s.termCycles"><strong>Term:</strong> cycle {{ s.cyclesBilled }} of {{ s.termCycles }}, then {{ s.autoRenew ? 'renews' : 'ends' }}<span *ngIf="s.renewals"> ({{ s.renewals }} renewal(s) so far)</span></div>
            <div *ngIf="s.cancelledAt"><strong>Cancelled:</strong> {{ s.cancelledAt | date: 'medium' }}</div>
          </div>
          <div class="text-muted mt-2" *ngIf="s.notes">{{ s.notes }}</div>
          <div class="text-muted mt-2" *ngIf="s.cancelReason && !s.cancelAtPeriodEnd">Cancel reason: {{ s.cancelReason }}</div>

          <div class="flex gap-2 mt-3" style="flex-wrap:wrap;" *ngIf="s.status === 'ACTIVE' || s.status === 'PAUSED'">
            <button class="btn btn-outline-primary" (click)="billNow()" [disabled]="s.status !== 'ACTIVE' || s.cancelAtPeriodEnd || busy">Bill now</button>
            <button class="btn btn-outline-primary" (click)="toggle('edit')">Edit terms</button>
            <button class="btn btn-outline-primary" (click)="toggle('plan')">Change plan</button>
            <button class="btn btn-outline-secondary" (click)="toggle('pause')" *ngIf="s.status === 'ACTIVE'" [disabled]="busy">Pause…</button>
            <button class="btn btn-outline-secondary" (click)="resume()" *ngIf="s.status === 'PAUSED'" [disabled]="busy">Resume</button>
            <button class="btn btn-outline-secondary" (click)="skipNext()" *ngIf="s.status === 'ACTIVE' && !s.cancelAtPeriodEnd" [disabled]="busy">Skip next billing</button>
            <button class="btn btn-outline-danger" (click)="toggle('cancel')" [disabled]="busy">Cancel subscription</button>
          </div>

          <!-- Edit terms -->
          <form class="card mt-3" style="padding:1rem;" *ngIf="panel === 'edit'" (ngSubmit)="saveEdit()">
            <h4 class="mb-2">Edit terms</h4>
            <div class="text-muted mb-2" style="font-size:0.85rem;">Changes apply from the next bill. Nothing is charged or credited now — use “Change plan” to settle the current period.</div>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group"><label class="form-label">Price</label><input class="form-control" type="number" step="0.01" min="0.01" name="ep" [(ngModel)]="ed.price" /></div>
              <div class="form-group"><label class="form-label">Quantity</label><input class="form-control" type="number" step="1" min="1" name="eq" [(ngModel)]="ed.quantity" /></div>
              <div class="form-group"><label class="form-label">Interval</label>
                <select class="form-select" name="ei" [(ngModel)]="ed.billingInterval"><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="QUARTERLY">Quarterly</option><option value="YEARLY">Yearly</option></select></div>
              <div class="form-group"><label class="form-label">Discount %</label><input class="form-control" type="number" step="0.01" min="0" max="100" name="ed" [(ngModel)]="ed.discountPercent" /></div>
              <div class="form-group"><label class="form-label">Tax rate</label>
                <select class="form-select" name="et" [(ngModel)]="ed.taxRateId"><option [ngValue]="null">— None (tax-exempt) —</option><option *ngFor="let t of taxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option></select></div>
              <div class="form-group" *ngIf="!s.setupFeeBilled"><label class="form-label">Setup fee</label><input class="form-control" type="number" step="0.01" min="0" name="es" [(ngModel)]="ed.setupFee" /></div>
              <div class="form-group"><label class="form-label">Term (cycles, blank = open)</label><input class="form-control" type="number" step="1" min="1" max="1200" name="ec" [(ngModel)]="ed.termCycles" /></div>
              <div class="form-group" *ngIf="ed.termCycles"><label class="form-label">At the end</label>
                <select class="form-select" name="ea" [(ngModel)]="ed.autoRenew"><option [ngValue]="true">Renew</option><option [ngValue]="false">End</option></select></div>
            </div>
            <div class="form-group"><label class="form-label">Notes</label><input class="form-control" name="en" [(ngModel)]="ed.notes" /></div>
            <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">Save changes</button><button class="btn btn-link" type="button" (click)="panel = ''">Never mind</button></div>
          </form>

          <!-- Change plan -->
          <form class="card mt-3" style="padding:1rem;" *ngIf="panel === 'plan'" (ngSubmit)="applyPlanChange()">
            <h4 class="mb-2">Change plan</h4>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div class="form-group"><label class="form-label">Plan</label>
                <select class="form-select" name="cp" [(ngModel)]="cp.planId" (ngModelChange)="onPlanPicked()"><option *ngFor="let p of plans" [ngValue]="p.id">{{ p.name }} — {{ p.price | number: '1.2-2' }} / {{ p.billingInterval }}</option></select></div>
              <div class="form-group"><label class="form-label">Price</label><input class="form-control" type="number" step="0.01" min="0.01" name="cpp" [(ngModel)]="cp.price" (ngModelChange)="refreshQuote()" /></div>
              <div class="form-group"><label class="form-label">Quantity</label><input class="form-control" type="number" step="1" min="1" name="cpq" [(ngModel)]="cp.quantity" (ngModelChange)="refreshQuote()" /></div>
              <div class="form-group"><label class="form-label">Interval</label>
                <select class="form-select" name="cpi" [(ngModel)]="cp.billingInterval" (ngModelChange)="refreshQuote()"><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="QUARTERLY">Quarterly</option><option value="YEARLY">Yearly</option></select></div>
              <div class="form-group"><label class="form-label">Discount %</label><input class="form-control" type="number" step="0.01" min="0" max="100" name="cpd" [(ngModel)]="cp.discountPercent" (ngModelChange)="refreshQuote()" /></div>
              <div class="form-group"><label class="form-label">Tax rate</label>
                <select class="form-select" name="cpt" [(ngModel)]="cp.taxRateId"><option [ngValue]="null">— None (tax-exempt) —</option><option *ngFor="let t of taxRates" [ngValue]="t.id">{{ t.name }} ({{ t.ratePercent | number: '1.0-2' }}%)</option></select></div>
            </div>
            <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="cpr" [(ngModel)]="cp.prorate" (ngModelChange)="refreshQuote()" /> Settle the unused part of the current period now (prepaid subscriptions that were already billed)</label>
            <div class="alert alert-info mt-2" *ngIf="quote">
              {{ quote.message }}
              <div *ngIf="quote.prorated">Current period: {{ quote.remainingDays }} of {{ quote.periodDays }} days left.</div>
              <div>Monthly value: {{ quote.monthlyBefore | number: '1.2-2' }} → {{ quote.monthlyAfter | number: '1.2-2' }}</div>
            </div>
            <div class="flex gap-2 mt-2"><button class="btn btn-primary" type="submit" [disabled]="busy">Change plan</button><button class="btn btn-link" type="button" (click)="panel = ''">Never mind</button></div>
          </form>

          <!-- Pause -->
          <form class="card mt-3" style="padding:1rem; max-width:34rem;" *ngIf="panel === 'pause'" (ngSubmit)="pause()">
            <h4 class="mb-2">Pause subscription</h4>
            <div class="form-group"><label class="form-label">Reason (optional)</label><input class="form-control" name="pr" [(ngModel)]="pauseReason" /></div>
            <div class="form-group"><label class="form-label">Resume automatically on (optional)</label><app-date-picker name="prd" [(ngModel)]="pauseResumeOn"></app-date-picker></div>
            <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">Pause</button><button class="btn btn-link" type="button" (click)="panel = ''">Never mind</button></div>
          </form>

          <!-- Cancel -->
          <form class="card mt-3" style="padding:1rem; max-width:34rem;" *ngIf="panel === 'cancel'" (ngSubmit)="cancel()">
            <h4 class="mb-2">Cancel subscription</h4>
            <div class="form-group"><label class="form-label">Reason (optional)</label><input class="form-control" name="cr" [(ngModel)]="cancelReason" /></div>
            <div class="form-group" *ngIf="s.status === 'ACTIVE'">
              <label style="display:flex; gap:0.4rem; align-items:center;"><input type="radio" name="cw" [value]="false" [(ngModel)]="cancelAtEnd" /> Cancel now</label>
              <label style="display:flex; gap:0.4rem; align-items:center;"><input type="radio" name="cw" [value]="true" [(ngModel)]="cancelAtEnd" /> At the end of the paid period ({{ s.nextBillingDate }})</label>
            </div>
            <div class="flex gap-2"><button class="btn btn-danger" type="submit" [disabled]="busy">{{ cancelAtEnd && s.status === 'ACTIVE' ? 'Schedule cancellation' : 'Confirm cancel' }}</button><button class="btn btn-link" type="button" (click)="panel = ''">Never mind</button></div>
          </form>
        </div>
      </ng-container>

      <h4 class="mt-3 mb-2">Billing history</h4>
      <table>
        <thead>
          <tr>
            <th>Invoice #</th>
            <th>Billed on</th>
            <th>Period covered</th>
            <th>Status</th>
            <th class="text-end">Total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of history">
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.invoiceDate | date: 'medium' }}</td>
            <!-- Fourier: "create an invoice from the last end date" — each row's own cycle. -->
            <td>{{ inv.periodStart ? (inv.periodStart | date: 'mediumDate') + ' – ' + (inv.periodEnd | date: 'mediumDate') : (inv.lines[0].description || '—') }}</td>
            <td><span class="badge" [ngClass]="inv.status === 'ISSUED' ? 'badge-success' : 'badge-secondary'">{{ inv.status }}</span></td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="history.length === 0">
            <td colspan="6" class="text-muted">No invoices generated for this subscription yet.</td>
          </tr>
        </tbody>
      </table>

      <h4 class="mt-3 mb-2">Activity</h4>
      <table class="table-sm">
        <thead><tr><th>When</th><th>What</th><th>Details</th><th>By</th></tr></thead>
        <tbody>
          <tr *ngFor="let e of events">
            <td style="white-space:nowrap;">{{ e.at | date: 'medium' }}</td>
            <td><span class="badge badge-secondary">{{ kindLabel(e.kind) }}</span></td>
            <td>{{ e.detail }}</td>
            <td>{{ e.by || '—' }}</td>
          </tr>
          <tr *ngIf="events.length === 0"><td colspan="4" class="text-muted">Nothing recorded yet.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SubscriptionDetailComponent implements OnInit {
  id!: number;
  sub: Subscription | null = null;
  history: Invoice[] = [];
  events: SubscriptionEvent[] = [];
  plans: SubscriptionPlan[] = [];
  taxRates: TaxRateOption[] = [];
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  printing = false;
  panel: '' | 'edit' | 'plan' | 'pause' | 'cancel' = '';
  cancelReason: string | null = null;
  cancelAtEnd = false;
  pauseReason = '';
  pauseResumeOn = '';
  ed: { price: number | null; quantity: number | null; billingInterval: BillingInterval; discountPercent: number | null; taxRateId: number | null; setupFee: number | null; termCycles: number | null; autoRenew: boolean; notes: string } =
    { price: null, quantity: 1, billingInterval: 'MONTHLY', discountPercent: 0, taxRateId: null, setupFee: 0, termCycles: null, autoRenew: true, notes: '' };
  cp: { planId: number | null; price: number | null; quantity: number | null; billingInterval: BillingInterval; discountPercent: number | null; taxRateId: number | null; prorate: boolean } =
    { planId: null, price: null, quantity: 1, billingInterval: 'MONTHLY', discountPercent: 0, taxRateId: null, prorate: true };
  quote: SubscriptionChangeQuote | null = null;

  constructor(
    private route: ActivatedRoute,
    private subscriptionApi: SubscriptionApiService,
    private planApi: SubscriptionPlanApiService,
    private taxRateApi: TaxRateApiService,
  ) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.planApi.listAll().subscribe({ next: (p) => (this.plans = p.filter((x) => x.active)), error: () => (this.plans = []) });
    this.taxRateApi.listAll().subscribe({ next: (r) => (this.taxRates = r.filter((t) => t.active)), error: () => (this.taxRates = []) });
    this.loadAll();
  }

  private loadAll(): void {
    this.subscriptionApi.getById(this.id).subscribe({
      next: (s) => (this.sub = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load subscription'),
    });
    this.subscriptionApi.billingHistory(this.id).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load billing history'),
    });
    this.subscriptionApi.events(this.id).subscribe({ next: (e) => (this.events = e), error: () => (this.events = []) });
  }

  private taxIdFor(s: Subscription): number | null {
    const t = this.taxRates.find((r) => r.code === s.taxCode);
    return t ? t.id : null;
  }

  toggle(p: 'edit' | 'plan' | 'pause' | 'cancel'): void {
    this.error = null;
    this.notice = null;
    if (this.panel === p) {
      this.panel = '';
      return;
    }
    this.panel = p;
    const s = this.sub;
    if (!s) return;
    if (p === 'edit') {
      this.ed = { price: s.price, quantity: s.quantity, billingInterval: s.billingInterval, discountPercent: s.discountPercent, taxRateId: this.taxIdFor(s), setupFee: s.setupFee,
        termCycles: s.termCycles, autoRenew: s.autoRenew, notes: s.notes ?? '' };
    }
    if (p === 'plan') {
      this.cp = { planId: s.planId, price: s.price, quantity: s.quantity, billingInterval: s.billingInterval, discountPercent: s.discountPercent, taxRateId: this.taxIdFor(s), prorate: true };
      this.refreshQuote();
    }
    if (p === 'cancel') {
      this.cancelAtEnd = false;
      this.cancelReason = null;
    }
  }

  onPlanPicked(): void {
    const p = this.plans.find((x) => x.id === this.cp.planId);
    if (p) {
      this.cp.price = p.price;
      this.cp.billingInterval = p.billingInterval;
      this.cp.discountPercent = p.discountPercent;
      this.cp.taxRateId = p.taxRateId;
    }
    this.refreshQuote();
  }

  refreshQuote(): void {
    this.quote = null;
    if (!this.cp.price || this.cp.price <= 0) return;
    this.subscriptionApi.quoteChange(this.id, this.cp as any).subscribe({ next: (q) => (this.quote = q), error: () => (this.quote = null) });
  }

  private done(message: string, s?: Subscription): void {
    this.busy = false;
    this.panel = '';
    this.notice = message;
    if (s) this.sub = s;
    this.loadAll();
  }

  private fail(err: any, fallback: string): void {
    this.busy = false;
    this.error = err?.error?.message ?? fallback;
  }

  saveEdit(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.update(this.id, { ...this.ed, termCycles: this.ed.termCycles ? this.ed.termCycles : 0, notes: this.ed.notes }).subscribe({
      next: (s) => this.done('Terms saved. They apply from the next bill.', s),
      error: (err) => this.fail(err, 'Failed to save the terms'),
    });
  }

  applyPlanChange(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.changePlan(this.id, this.cp as any).subscribe({
      next: (s) => this.done('Plan changed.', s),
      error: (err) => this.fail(err, 'Failed to change the plan'),
    });
  }

  billNow(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.billNow(this.id).subscribe({
      next: () => this.done('Billed.'),
      error: (err) => this.fail(err, 'Failed to bill subscription'),
    });
  }

  pause(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.pause(this.id, this.pauseReason, this.pauseResumeOn).subscribe({
      next: (s) => { this.pauseReason = ''; this.pauseResumeOn = ''; this.done('Paused.', s); },
      error: (err) => this.fail(err, 'Failed to pause subscription'),
    });
  }

  resume(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.resume(this.id).subscribe({
      next: (s) => this.done('Resumed.', s),
      error: (err) => this.fail(err, 'Failed to resume subscription'),
    });
  }

  skipNext(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.skipNext(this.id).subscribe({
      next: (s) => this.done('The next billing was skipped.', s),
      error: (err) => this.fail(err, 'Failed to skip the billing'),
    });
  }

  cancel(): void {
    this.error = null;
    this.busy = true;
    const atEnd = this.cancelAtEnd && this.sub?.status === 'ACTIVE';
    this.subscriptionApi.cancel(this.id, this.cancelReason || null, atEnd).subscribe({
      next: (s) => this.done(atEnd ? 'Cancellation scheduled.' : 'Subscription cancelled.', s),
      error: (err) => this.fail(err, 'Failed to cancel subscription'),
    });
  }

  undoCancel(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.undoCancel(this.id).subscribe({
      next: (s) => this.done('The scheduled cancellation was withdrawn.', s),
      error: (err) => this.fail(err, 'Failed to withdraw the cancellation'),
    });
  }

  print(): void {
    this.printing = true;
    this.subscriptionApi.pdf(this.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to open subscription PDF';
      },
    });
  }

  kindLabel(k: string): string {
    return ({ CREATED: 'Created', EDITED: 'Edited', PLAN_CHANGED: 'Plan changed', BILLED: 'Billed', SKIPPED: 'Skipped', PAUSED: 'Paused', RESUMED: 'Resumed', CANCEL_SCHEDULED: 'Cancel scheduled',
      CANCEL_UNDONE: 'Cancel withdrawn', CANCELLED: 'Cancelled', RENEWED: 'Renewed', ENDED: 'Ended', REMINDER: 'Reminder' } as Record<string, string>)[k] ?? k;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'badge-success';
      case 'PAUSED':
        return 'badge-secondary';
      case 'CANCELLED':
        return 'badge-danger';
      case 'ENDED':
        return 'badge-secondary';
      default:
        return 'badge-secondary';
    }
  }
}
