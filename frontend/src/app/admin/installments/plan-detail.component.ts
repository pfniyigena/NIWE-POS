import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { InstallmentApiService, InstallmentPlanDetail, RescheduleLine } from '../../core/api/installment-api.service';
import { openPdfForPrint } from '../../core/download-file';
import { PaymentMethodOption } from '../../core/models';

/** Installments > a plan: the schedule, what's paid, record a payment, remind the customer, cancel. */
@Component({
  selector: 'app-installment-plan-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>
      <ng-container *ngIf="d as x">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <h3 style="margin:0;">{{ x.plan.planNumber }}
            <span class="badge" [class.badge-success]="x.plan.status === 'COMPLETED'" [class.badge-warning]="x.plan.status === 'ACTIVE'" [class.badge-secondary]="x.plan.status === 'CANCELLED'">{{ x.plan.status }}</span></h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-outline-primary" type="button" (click)="pdf()">Schedule (PDF)</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="x.plan.status === 'ACTIVE'" [disabled]="busy" (click)="remind()">Send reminder</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="x.plan.status === 'ACTIVE'" (click)="startEdit()">Edit schedule</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="x.plan.status === 'ACTIVE'" (click)="showCancel = !showCancel">Cancel plan</button>
            <a class="btn btn-link" routerLink="/admin/installments">Back</a>
          </div>
        </div>

        <div class="alert alert-info" *ngIf="x.terms.adjustmentNote" style="white-space:pre-line;">{{ x.terms.adjustmentNote }}</div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="edit" (ngSubmit)="saveEdit()">
          <strong class="mb-1">Edit the unpaid instalments</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">Change dates and amounts, add or remove instalments. What is already paid stays as it is. The principal must add up to {{ editTotal | number: '1.0-2' }}; interest can't go below what has been paid.</div>
          <table class="table-sm mb-2">
            <thead><tr><th>No.</th><th>Due date</th><th class="text-end">Principal</th><th class="text-end">Interest</th><th></th></tr></thead>
            <tbody><tr *ngFor="let e of edit; let k = index">
              <td>{{ editFirstSeq + k }}</td>
              <td><app-date-picker [name]="'ed' + k" [(ngModel)]="e.dueDate"></app-date-picker></td>
              <td class="text-end"><input class="form-control" type="number" min="0" step="0.01" style="width:9rem; display:inline-block;" [name]="'ep' + k" [(ngModel)]="e.principalDue" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" step="0.01" style="width:8rem; display:inline-block;" [name]="'ei' + k" [(ngModel)]="e.interestDue" /></td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="removeRow(k)" [disabled]="edit.length < 2">Remove</button></td>
            </tr></tbody>
            <tfoot><tr><td colspan="2"><strong>Total</strong></td>
              <td class="text-end"><strong [style.color]="editDiff() === 0 ? '' : 'var(--danger-text, #b00020)'">{{ editSum() | number: '1.0-2' }}</strong></td>
              <td class="text-end">{{ editInterestSum() | number: '1.0-2' }}</td><td></td></tr></tfoot>
          </table>
          <div class="text-danger mb-2" *ngIf="editDiff() !== 0">The principal is {{ editDiff() > 0 ? editDiff() : -editDiff() | number: '1.0-2' }} {{ editDiff() > 0 ? 'too high' : 'too low' }}.</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button class="btn btn-primary" type="submit" [disabled]="busy || editDiff() !== 0">Save schedule</button>
            <button class="btn btn-outline-secondary" type="button" (click)="addRow()">+ Add instalment</button>
            <button class="btn btn-outline-secondary" type="button" (click)="splitEvenly()">Split principal evenly</button>
            <button class="btn btn-link" type="button" (click)="edit = null">Cancel</button>
          </div>
        </form>

        <form class="card mb-3" style="padding:1rem;" *ngIf="showCancel" (ngSubmit)="cancel()">
          <strong class="mb-2">Cancel this plan?</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">Whatever is still unpaid stays on the invoice as an ordinary debt. Unpaid interest and late fees are dropped.</div>
          <div class="flex gap-2 align-center"><input class="form-control" style="max-width:26rem;" name="cr" placeholder="Reason (optional)" [(ngModel)]="cancelReason" />
            <button class="btn btn-danger" type="submit">Cancel the plan</button></div>
        </form>

        <div class="card mb-3" style="padding:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><strong>Customer:</strong> {{ x.plan.customerName }}</div>
            <div><strong>Invoice:</strong> <a [routerLink]="['/admin/invoices', x.plan.invoiceId]">{{ x.plan.invoiceNumber }}</a> ({{ x.terms.invoiceTotal | number: '1.0-2' }})</div>
            <div *ngIf="x.terms.deposit > 0"><strong>Deposit:</strong> {{ x.terms.deposit | number: '1.0-2' }}</div>
            <div><strong>Terms:</strong> {{ x.plan.numberOfInstallments }} × {{ freq(x.terms.frequency) }}<span *ngIf="x.terms.interestMethod !== 'NONE'">, {{ x.terms.interestRate }}% a year ({{ x.terms.interestMethod === 'FLAT' ? 'flat' : 'reducing balance' }})</span></div>
            <div *ngIf="x.terms.lateFeeFixed > 0 || x.terms.lateFeePercent > 0"><strong>Late fee:</strong> <span *ngIf="x.terms.lateFeeFixed > 0">{{ x.terms.lateFeeFixed | number: '1.0-2' }}</span><span *ngIf="x.terms.lateFeeFixed > 0 && x.terms.lateFeePercent > 0"> + </span><span *ngIf="x.terms.lateFeePercent > 0">{{ x.terms.lateFeePercent }}%</span> after {{ x.terms.graceDays }} day(s)</div>
          </div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="x.terms.notes">{{ x.terms.notes }}</div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="x.terms.cancelledOn">Cancelled on {{ x.terms.cancelledOn }}<span *ngIf="x.terms.cancelReason"> — {{ x.terms.cancelReason }}</span></div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">Total payable</div><div class="stat-value">{{ x.plan.totalPayable | number: '1.0-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">Paid</div><div class="stat-value">{{ x.plan.paid | number: '1.0-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">Outstanding</div><div class="stat-value">{{ x.plan.outstanding | number: '1.0-2' }}</div></div>
          <div class="stat-tile" [class.warn]="x.plan.overdueAmount > 0"><div class="stat-label">Overdue</div><div class="stat-value">{{ x.plan.overdueAmount | number: '1.0-2' }}</div></div>
        </div>

        <form class="card mb-3" style="padding:1rem;" *ngIf="x.plan.status === 'ACTIVE'" (ngSubmit)="pay()">
          <strong class="mb-2">Record a payment</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Amount</label><input class="form-control" type="number" min="0.01" step="0.01" style="width:10rem;" name="am" [(ngModel)]="amount" required />
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="x.plan.nextDueAmount">Next instalment: {{ x.plan.nextDueAmount | number: '1.0-2' }}</div></div>
            <div class="form-group"><label class="form-label">Method</label><select class="form-select" name="me" [(ngModel)]="method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
            <div class="form-group" style="min-width:14rem;"><label class="form-label">Note</label><input class="form-control" name="no" [(ngModel)]="note" /></div>
            <button class="btn btn-primary" type="submit" [disabled]="busy || !amount">Record payment</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">Goes to the oldest unpaid instalment first. Interest and late fees are settled before principal; the principal part is recorded as a payment on the invoice.</div>
        </form>

        <h4 class="mb-2">Schedule</h4>
        <table class="table-sm mb-3">
          <thead><tr><th>No.</th><th>Due date</th><th class="text-end">Principal</th><th class="text-end">Interest</th><th class="text-end">Late fee</th><th class="text-end">Total</th><th class="text-end">Paid</th><th class="text-end">Balance</th><th>Status</th><th></th></tr></thead>
          <tbody><tr *ngFor="let i of x.installments">
            <td>{{ i.seq }}</td><td>{{ i.dueDate }}</td>
            <td class="text-end">{{ i.principalDue | number: '1.0-2' }}</td><td class="text-end">{{ i.interestDue | number: '1.0-2' }}</td>
            <td class="text-end">{{ i.lateFee | number: '1.0-2' }}<span class="text-muted" *ngIf="i.lateFeeWaived"> waived</span></td>
            <td class="text-end">{{ i.totalDue | number: '1.0-2' }}</td><td class="text-end">{{ i.paid | number: '1.0-2' }}</td><td class="text-end">{{ i.balance | number: '1.0-2' }}</td>
            <td><span class="badge" [class.badge-success]="i.state === 'PAID'" [class.badge-danger]="i.state === 'OVERDUE'" [class.badge-warning]="i.state === 'PARTIAL'" [class.badge-secondary]="i.state === 'PENDING'">{{ i.state }}</span>
              <span class="text-muted" style="font-size:0.75rem;" *ngIf="i.state === 'OVERDUE'"> {{ i.daysOverdue }} day(s)</span></td>
            <td><button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="x.plan.status === 'ACTIVE' && i.lateFee > 0" (click)="waive(i.seq)">Waive late fee</button></td>
          </tr></tbody>
        </table>

        <h4 class="mb-2">Payments</h4>
        <table class="table-sm">
          <thead><tr><th>Date</th><th>Receipt</th><th class="text-end">Amount</th><th class="text-end">Principal</th><th class="text-end">Interest and fees</th><th>Method</th><th>Recorded by</th><th>Note</th></tr></thead>
          <tbody><tr *ngFor="let p of x.payments">
            <td>{{ p.paidAt | date: 'medium' }}</td><td>{{ p.receiptNumber || '—' }}</td><td class="text-end">{{ p.amount | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.principalPart | number: '1.0-2' }}</td><td class="text-end">{{ p.chargesPart | number: '1.0-2' }}</td>
            <td>{{ p.method || '—' }}</td><td>{{ p.recordedByName || '—' }}</td><td>{{ p.note || '—' }}</td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!x.payments.length">No payments recorded through this plan yet. Payments recorded directly on the invoice also count toward the schedule.</p>
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
        this.showCancel = false;
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'That did not work';
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
