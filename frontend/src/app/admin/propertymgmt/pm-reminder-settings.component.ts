import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PlanRow, PmReminderApiService, ReminderRun, ReminderSettings, ReminderSettingsPayload } from '../../core/api/pm-reminder-api.service';

/** Settings of the rent reminders and late fees, with a look at what would go out today and a button to run it now. */
@Component({
  selector: 'app-pm-reminder-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <form class="card" style="padding:1rem; max-width:40rem; margin-top:1rem;" (ngSubmit)="save()">
      <h4 class="mb-2">{{ 'Rent reminders' | translate }}</h4>
      <div class="alert alert-warning" *ngIf="loaded && !mailReady">E-mail is not set up yet (Settings &gt; Email). Reminders cannot go out until it is.</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="re" [(ngModel)]="remindersEnabled" /> {{ 'E-mail tenants about unpaid rent every morning' | translate }}</label>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <div class="form-group mb-2"><label class="form-label">{{ 'Before it is due (days)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="30" name="bd" [(ngModel)]="beforeDays" />
          <div class="text-muted" style="font-size:0.85rem;">{{ '0: no reminder before.' | translate }}</div></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'First reminder once late (days)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="90" name="of" [(ngModel)]="firstDays" />
          <div class="text-muted" style="font-size:0.85rem;">{{ '0: none once late.' | translate }}</div></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Then every (days)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="90" name="or" [(ngModel)]="repeatDays" />
          <div class="text-muted" style="font-size:0.85rem;">{{ '0: only the first.' | translate }}</div></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'At most (reminders)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="1" max="12" name="om" [(ngModel)]="maxReminders" /></div>
      </div>
      <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'Each reminder carries the unpaid invoice as a PDF. A tenant with no e-mail address is listed below instead, and a reminder is never sent twice on the same day.' | translate }}</div>

      <h4 class="mb-2">{{ 'Late fees' | translate }}</h4>
      <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="le" [(ngModel)]="feeEnabled" /> {{ 'Charge a late fee on rent that stays unpaid' | translate }}</label>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <div class="form-group mb-2"><label class="form-label">{{ 'The fee is' | translate }}</label>
          <select class="form-select" name="ft" [(ngModel)]="feeType"><option value="PERCENT">{{ 'A percentage of the unpaid rent' | translate }}</option><option value="FIXED">{{ 'A fixed amount' | translate }}</option></select></div>
        <div class="form-group mb-2"><label class="form-label">{{ feeType === 'PERCENT' ? ('Percentage (%)' | translate) : ('Amount' | translate) }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" step="0.01" name="fv" [(ngModel)]="feeValue" /></div>
        <div class="form-group mb-2" *ngIf="feeType === 'PERCENT'"><label class="form-label">{{ 'Never more than' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" step="0.01" name="fm" [(ngModel)]="feeMax" [placeholder]="'no limit' | translate" /></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Grace period (days late)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="90" name="fg" [(ngModel)]="graceDays" /></div>
      </div>
      <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="fa" [(ngModel)]="feeAuto" [disabled]="!feeEnabled" /> {{ 'Charge it automatically every morning once the grace period is over' | translate }}</label>
      <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'The fee is invoiced to the tenant, once per rent, under the product "Late rent fee" (set VAT on that product in Products if it applies). It is the agency\\'s income and is not part of the owner\\'s statement. Without the automatic box, press' | translate }} <em>{{ 'Late fee' | translate }}</em> {{ 'on the Rent page for the rents you choose.' | translate }}</div>

      <div class="mb-2"><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save' | translate) }}</button></div>
      <div class="text-muted" style="font-size:0.85rem;" *ngIf="lastRun">{{ 'Last morning run: {p0}, {p1}.' | translate: { p0: (lastRun | date: 'd MMM y'), p1: (lastSummary) } }}</div>
    </form>

    <div class="card" style="padding:1rem; max-width:60rem; margin-top:1rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'What would go out today' | translate }}</h4>
        <div class="flex gap-2">
          <button class="btn btn-sm btn-outline-secondary" type="button" (click)="preview()" [disabled]="busy">{{ plan ? ('Refresh' | translate) : ('Show' | translate) }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="runNow()" [disabled]="busy || (!remindersEnabled && !(feeEnabled && feeAuto))">{{ 'Run it now' | translate }}</button>
        </div>
      </div>
      <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Uses the settings as saved, so save first. Running it now does what the morning run does.' | translate }}</div>
      <div class="alert alert-success" *ngIf="result">{{ '{p0} reminder{p1} sent, {p2} late fee{p3} charged' | translate: { p0: (result.reminded), p1: (result.reminded === 1 ? '' : 's'), p2: (result.fees), p3: (result.fees === 1 ? '' : 's') } }}<span *ngIf="result.skipped">{{ ', {p0} skipped' | translate: { p0: (result.skipped) } }}</span><span *ngIf="result.failed">{{ ', {p0} failed' | translate: { p0: (result.failed) } }}</span>.
        <div *ngFor="let n of result.notes" style="font-size:0.9rem;">{{ n }}</div></div>
      <table class="stack table-sm" *ngIf="plan && plan.length">
        <thead><tr><th>{{ 'Tenant' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th>{{ 'Would do' | translate }}</th></tr></thead>
        <tbody><tr *ngFor="let p of plan">
          <td data-label="Tenant">{{ p.tenantName }}<div class="text-muted" style="font-size:0.75rem;">{{ p.tenantEmail }}</div></td>
          <td data-label="Unit">{{ p.unit }}</td>
          <td data-label="Invoice">{{ p.invoiceNumber }}</td>
          <td style="white-space:nowrap;" data-label="Due">{{ p.dueDate | date: 'd MMM y' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="p.daysOverdue">{{ '{p0} days late' | translate: { p0: (p.daysOverdue) } }}</div></td>
          <td class="text-end" data-label="Owed">{{ p.outstanding | number: '1.0-0' }}</td>
          <td data-label="Would do"><div *ngIf="p.reminder">{{ p.reminder === 'BEFORE' ? ('Reminder (due soon)' | translate) : ('Reminder (overdue)' | translate) }}</div><div *ngIf="p.lateFee">{{ 'Late fee {p0}' | translate: { p0: (p.lateFee | number: '1.0-2') } }}</div>
            <div style="color:#b91c1c; font-size:0.8rem;" *ngIf="p.problem">{{ p.problem }}</div></td>
        </tr></tbody>
      </table>
      <div class="text-muted" *ngIf="plan && !plan.length">{{ 'Nothing to send or charge today.' | translate }}</div>
    </div>
  `,
})
export class PmReminderSettingsComponent implements OnInit {
  remindersEnabled = false;
  beforeDays = 3;
  firstDays = 3;
  repeatDays = 7;
  maxReminders = 3;
  feeEnabled = false;
  feeAuto = false;
  feeType: 'PERCENT' | 'FIXED' = 'PERCENT';
  feeValue: number | null = null;
  feeMax: number | null = null;
  graceDays = 5;
  lastRun: string | null = null;
  lastSummary: string | null = null;
  mailReady = false;
  hadMax = false;
  loaded = false;
  saving = false;
  saved = false;
  busy = false;
  error: string | null = null;
  plan: PlanRow[] | null = null;
  result: ReminderRun | null = null;

  constructor(private api: PmReminderApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => this.fill(s), error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the reminder settings'; } });
  }

  private fill(s: ReminderSettings): void {
    this.remindersEnabled = s.remindersEnabled;
    this.beforeDays = s.reminderBeforeDays;
    this.firstDays = s.overdueFirstDays;
    this.repeatDays = s.overdueRepeatDays;
    this.maxReminders = s.overdueMaxReminders;
    this.feeEnabled = s.lateFeeEnabled;
    this.feeAuto = s.lateFeeAuto;
    this.feeType = s.lateFeeType;
    this.feeValue = s.lateFeeValue;
    this.feeMax = s.lateFeeMax;
    this.hadMax = s.lateFeeMax != null;
    this.graceDays = s.lateFeeGraceDays;
    this.lastRun = s.lastRun;
    this.lastSummary = s.lastSummary;
    this.mailReady = s.mailReady;
    this.loaded = true;
  }

  save(): void {
    this.saving = true;
    this.saved = false;
    this.error = null;
    const p: ReminderSettingsPayload = {
      remindersEnabled: this.remindersEnabled, reminderBeforeDays: Number(this.beforeDays), overdueFirstDays: Number(this.firstDays), overdueRepeatDays: Number(this.repeatDays),
      overdueMaxReminders: Number(this.maxReminders), lateFeeEnabled: this.feeEnabled, lateFeeAuto: this.feeEnabled && this.feeAuto, lateFeeGraceDays: Number(this.graceDays), lateFeeType: this.feeType,
    };
    if (this.feeValue != null && String(this.feeValue) !== '') p.lateFeeValue = Number(this.feeValue);
    if (this.feeType === 'PERCENT' && this.feeMax != null && String(this.feeMax) !== '') p.lateFeeMax = Number(this.feeMax);
    else if (this.hadMax) p.clearLateFeeMax = true;
    this.api.saveSettings(p).subscribe({
      next: (s) => { this.saving = false; this.saved = true; this.fill(s); this.plan = null; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the settings'; },
    });
  }

  preview(): void {
    this.busy = true;
    this.error = null;
    this.api.due().subscribe({ next: (p) => { this.busy = false; this.plan = p; }, error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not work out what is due'; } });
  }

  runNow(): void {
    this.busy = true;
    this.error = null;
    this.result = null;
    this.api.run().subscribe({
      next: (r) => {
        this.busy = false;
        this.result = r;
        this.api.settings().subscribe((s) => this.fill(s));
        this.api.due().subscribe((p) => (this.plan = p));
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not run it'; },
    });
  }
}
