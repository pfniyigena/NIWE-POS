import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { InvoiceApiService } from '../../core/api/invoice-api.service';
import { PaymentMethodApiService } from '../../core/api/payment-method-api.service';
import { InstallmentApiService, InstallmentPlanRequest, InstallmentPreview } from '../../core/api/installment-api.service';
import { Invoice, PaymentMethodOption } from '../../core/models';

/** Installments > New plan: pick an issued invoice, set the terms, preview the schedule, create. */
@Component({
  selector: 'app-installment-plan-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>New instalment plan</h3>
        <a class="btn btn-link" routerLink="/admin/installments">Back</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="doPreview()">
        <div class="form-group">
          <label class="form-label">Invoice</label>
          <select class="form-select" name="inv" [(ngModel)]="req.invoiceId" (ngModelChange)="invoiceChanged()" required>
            <option [ngValue]="null">Choose an issued invoice…</option>
            <option *ngFor="let i of invoices" [ngValue]="i.id">{{ i.invoiceNumber }} — {{ i.customerName }} — owes {{ i.amountOutstanding | number: '1.0-2' }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="!invoices.length">No issued invoice with a customer and an unpaid balance was found.</div>
        </div>

        <ng-container *ngIf="selected as inv">
          <div class="text-muted mb-2">Invoice total {{ inv.totalAmount | number: '1.0-2' }} · paid {{ inv.amountPaid | number: '1.0-2' }} · still owed <strong>{{ inv.amountOutstanding | number: '1.0-2' }}</strong></div>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Deposit now (optional)</label><input class="form-control" type="number" min="0" step="0.01" style="width:10rem;" name="dep" [(ngModel)]="req.deposit" /></div>
            <div class="form-group" *ngIf="(req.deposit ?? 0) > 0"><label class="form-label">Deposit paid by</label>
              <select class="form-select" name="dm" [(ngModel)]="req.depositMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
            <div class="form-group"><label class="form-label">Number of instalments</label><input class="form-control" type="number" min="2" max="60" style="width:8rem;" name="n" [(ngModel)]="req.numberOfInstallments" required /></div>
            <div class="form-group"><label class="form-label">Every</label>
              <select class="form-select" name="fr" [(ngModel)]="req.frequency"><option value="WEEKLY">Week</option><option value="BIWEEKLY">2 weeks</option><option value="MONTHLY">Month</option><option value="QUARTERLY">3 months</option></select></div>
            <div class="form-group"><label class="form-label">First due date</label><app-date-picker name="fd" [(ngModel)]="req.firstDueDate"></app-date-picker></div>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Interest</label>
              <select class="form-select" name="im" [(ngModel)]="req.interestMethod">
                <option value="NONE">None</option><option value="FLAT">Flat (on the whole amount)</option><option value="REDUCING">Reducing balance</option></select></div>
            <div class="form-group" *ngIf="req.interestMethod !== 'NONE'"><label class="form-label">Yearly rate %</label><input class="form-control" type="number" min="0" max="100" step="0.01" style="width:8rem;" name="ir" [(ngModel)]="req.interestRate" /></div>
            <div class="form-group"><label class="form-label">Grace days</label><input class="form-control" type="number" min="0" max="90" style="width:7rem;" name="gd" [(ngModel)]="req.graceDays" /></div>
            <div class="form-group"><label class="form-label">Late fee (fixed)</label><input class="form-control" type="number" min="0" step="0.01" style="width:8rem;" name="lf" [(ngModel)]="req.lateFeeFixed" /></div>
            <div class="form-group"><label class="form-label">Late fee (% of unpaid)</label><input class="form-control" type="number" min="0" max="100" step="0.01" style="width:8rem;" name="lp" [(ngModel)]="req.lateFeePercent" /></div>
          </div>
          <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="wu" [(ngModel)]="req.wholeUnits" /> Whole amounts (no cents) — the last instalment takes any rounding left over</label></div>
          <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="rm" [(ngModel)]="req.remindersEnabled" /> Email the customer reminders for this plan</label></div>
          <div class="form-group"><label class="form-label">Notes</label><input class="form-control" name="nt" [(ngModel)]="req.notes" /></div>
          <button class="btn btn-outline-primary" type="submit" [disabled]="busy">Preview schedule</button>
        </ng-container>
      </form>

      <div class="card" style="padding:1rem;" *ngIf="preview as p">
        <h4 class="mb-2">Schedule</h4>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
          <div><strong>Financed:</strong> {{ p.financedAmount | number: '1.0-2' }}</div>
          <div><strong>Interest:</strong> {{ p.totalInterest | number: '1.0-2' }}</div>
          <div><strong>Total to pay in instalments:</strong> {{ p.totalPayable | number: '1.0-2' }}</div>
          <div *ngIf="p.deposit > 0"><strong>Deposit:</strong> {{ p.deposit | number: '1.0-2' }}</div>
        </div>
        <table class="table-sm">
          <thead><tr><th>No.</th><th>Due date</th><th class="text-end">Principal</th><th class="text-end">Interest</th><th class="text-end">Total</th></tr></thead>
          <tbody><tr *ngFor="let l of p.lines"><td>{{ l.seq }}</td><td>{{ l.dueDate }}</td><td class="text-end">{{ l.principalDue | number: '1.0-2' }}</td><td class="text-end">{{ l.interestDue | number: '1.0-2' }}</td><td class="text-end">{{ l.totalDue | number: '1.0-2' }}</td></tr></tbody>
        </table>
        <div class="mt-3">
          <button class="btn btn-primary" type="button" (click)="create()" [disabled]="busy">{{ busy ? 'Creating…' : 'Create plan' }}</button>
          <span class="text-muted" style="margin-left:0.6rem; font-size:0.85rem;" *ngIf="p.deposit > 0">The deposit is recorded as a payment on the invoice.</span>
        </div>
      </div>
    </app-shell>
  `,
})
export class InstallmentPlanFormComponent implements OnInit {
  invoices: Invoice[] = [];
  methods: PaymentMethodOption[] = [];
  req: InstallmentPlanRequest = {
    invoiceId: null, deposit: null, depositMethod: null, numberOfInstallments: 3, frequency: 'MONTHLY', firstDueDate: null,
    interestMethod: 'NONE', interestRate: 0, graceDays: 5, lateFeeFixed: 0, lateFeePercent: 0, remindersEnabled: true, wholeUnits: null, notes: null,
  };
  preview: InstallmentPreview | null = null;
  error: string | null = null;
  busy = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private invoiceApi: InvoiceApiService,
    private paymentApi: PaymentMethodApiService,
    private api: InstallmentApiService
  ) {}

  get selected(): Invoice | undefined {
    return this.invoices.find((i) => i.id === this.req.invoiceId);
  }

  ngOnInit(): void {
    const next = new Date();
    next.setMonth(next.getMonth() + 1);
    this.req.firstDueDate = next.toISOString().slice(0, 10);
    this.paymentApi.listActive().subscribe((m) => {
      this.methods = m;
      this.req.depositMethod = m[0]?.code ?? null;
    });
    this.api.settings().subscribe({
      next: (s) => {
        this.req.interestRate = s.defaultInterestRate;
        this.req.interestMethod = s.defaultInterestRate > 0 ? 'REDUCING' : 'NONE';
        this.req.graceDays = s.defaultGraceDays;
        this.req.lateFeeFixed = s.defaultLateFeeFixed;
        this.req.lateFeePercent = s.defaultLateFeePercent;
        this.req.remindersEnabled = s.remindersEnabled;
        this.req.wholeUnits = s.defaultWholeUnits;
      },
      error: () => {},
    });
    const wanted = Number(this.route.snapshot.queryParamMap.get('invoiceId')) || null;
    this.invoiceApi.listAll().subscribe({
      next: (all) => {
        this.invoices = all.filter((i) => i.status === 'ISSUED' && i.customerId && i.amountOutstanding > 0);
        if (wanted && this.invoices.some((i) => i.id === wanted)) this.req.invoiceId = wanted;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the invoices'),
    });
  }

  invoiceChanged(): void {
    this.preview = null;
  }

  doPreview(): void {
    this.busy = true;
    this.error = null;
    this.api.preview(this.req).subscribe({
      next: (p) => {
        this.preview = p;
        this.busy = false;
      },
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not work out the schedule';
        this.preview = null;
        this.busy = false;
      },
    });
  }

  create(): void {
    this.busy = true;
    this.error = null;
    this.api.create(this.req).subscribe({
      next: (d) => this.router.navigate(['/admin/installments', d.plan.id]),
      error: (e) => {
        this.error = e?.error?.message ?? 'Could not create the plan';
        this.busy = false;
      },
    });
  }
}
