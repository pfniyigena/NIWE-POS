import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionAutomationApiService, SubscriptionJobResult } from '../core/api/subscription-automation-api.service';
import { SubscriptionApiService } from '../core/api/subscription-api.service';

/**
 * Admin > Subscriptions > Job settings — the daily subscription job: bills due subscriptions, optionally
 * emails upcoming-billing reminders, pauses/cancels subscriptions whose invoice stays unpaid, and emails a
 * digest of what happened. Runs once a day from the chosen time (store time zone).
 */
@Component({
  selector: 'app-subscription-job-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'Subscription job settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Job settings saved.' | translate }}</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <div class="form-group">
          <label class="form-label">{{ 'Run from (store time)' | translate }}</label>
          <input class="form-control" style="max-width:140px;" type="time" name="jobTime" [(ngModel)]="jobTime" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The job runs once a day from this time. If the server was off then, it runs when it is next up the same day.
            Uses the time zone in Settings &gt; System settings.
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="billingEnabled" [(ngModel)]="billingEnabled" /> {{ 'Bill due subscriptions automatically' | translate }}
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Each active subscription whose next billing date has arrived gets its invoice issued. Turn this off to bill only by hand with "Bill now".' | translate }}
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="reminderEnabled" [(ngModel)]="reminderEnabled" /> {{ 'Email upcoming-billing reminders' | translate }}
          </label>
          <div *ngIf="reminderEnabled" style="margin-left:1.5rem;">
            <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
              <span>{{ 'Remind' | translate }}</span>
              <input class="form-control" style="width:80px;" type="number" min="1" max="60" name="reminderDays" [(ngModel)]="reminderDays" />
              <span>{{ 'day(s) before the billing date, to:' | translate }}</span>
            </div>
            <label class="form-label"><input type="checkbox" name="reminderCustomer" [(ngModel)]="reminderCustomer" /> {{ 'The customer (needs an email on their record)' | translate }}</label>
            <label class="form-label"><input type="checkbox" name="reminderStore" [(ngModel)]="reminderStore" /> The notifications email (Settings &gt; Email)</label>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">{{ 'Each upcoming bill is announced once.' | translate }}</div>
        </div>

        <div class="form-group">
          <label class="form-label">{{ 'When an invoice stays unpaid' | translate }}</label>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <select class="form-select" style="max-width:220px;" name="overdueAction" [(ngModel)]="overdueAction">
              <option value="NONE">{{ 'Do nothing' | translate }}</option>
              <option value="PAUSE">{{ 'Pause the subscription' | translate }}</option>
              <option value="CANCEL">{{ 'Cancel the subscription' | translate }}</option>
            </select>
            <ng-container *ngIf="overdueAction !== 'NONE'">
              <span>{{ 'after' | translate }}</span>
              <input class="form-control" style="width:90px;" type="number" min="1" max="365" name="overdueDays" [(ngModel)]="overdueDays" />
              <span>{{ 'day(s) past the invoice date' | translate }}</span>
            </ng-container>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Pausing can be undone with "Resume"; cancelling cannot. Subscription invoices have no separate due date, so the days count from the invoice date.' | translate }}
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="digestEnabled" [(ngModel)]="digestEnabled" /> {{ 'Email a daily results digest' | translate }}
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'To the notifications email: what was billed, anything that failed, subscriptions paused or cancelled for non-payment, and how many reminders went out. Not sent on days when nothing happened.' | translate }}
          </div>
        </div>

        <div class="flex gap-2 align-center">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save' | translate) }}</button>
          <button class="btn btn-outline-primary" type="button" [disabled]="running || saving" (click)="runNow()">{{ running ? ('Running…' | translate) : ('Run now' | translate) }}</button>
          <a class="btn btn-link" routerLink="/admin/subscriptions">{{ 'Back to subscriptions' | translate }}</a>
        </div>
        <div class="text-muted" style="font-size:0.8rem; margin-top:0.5rem;">
          {{ 'Run now uses the saved settings and counts as today\\'s run. Save first if you just changed something.' | translate }}
        </div>
        <div class="alert alert-info mt-2" *ngIf="runResult">{{ runResult.message }}</div>
        <div class="text-muted mt-2" *ngIf="lastSummary">{{ 'Last run: {p0}' | translate: { p0: (lastSummary) } }}</div>
      </form>

      <form class="card mt-3" style="padding:1rem;" (ngSubmit)="saveExtra()" *ngIf="extraLoaded">
        <h4 class="mb-2">{{ 'Overdue reminders and catching up' | translate }}</h4>
        <div class="alert alert-success" *ngIf="extraSaved">{{ 'Saved.' | translate }}</div>
        <div class="alert alert-danger" *ngIf="extraError">{{ extraError }}</div>
        <div class="form-group">
          <label class="form-label"><input type="checkbox" name="dunningEnabled" [(ngModel)]="x.dunningEnabled" /> {{ 'E-mail the customer when a subscription invoice is overdue' | translate }}</label>
          <div *ngIf="x.dunningEnabled" class="flex gap-2 align-center" style="flex-wrap:wrap; margin-left:1.5rem;">
            <span>{{ 'First reminder' | translate }}</span><input class="form-control" style="width:80px;" type="number" min="0" max="90" name="dunningFirstDays" [(ngModel)]="x.dunningFirstDays" /><span>{{ 'day(s) after the due date, then every' | translate }}</span>
            <input class="form-control" style="width:80px;" type="number" min="1" max="90" name="dunningRepeatDays" [(ngModel)]="x.dunningRepeatDays" /><span>{{ 'day(s), at most' | translate }}</span>
            <input class="form-control" style="width:80px;" type="number" min="1" max="10" name="dunningMax" [(ngModel)]="x.dunningMax" /><span>{{ 'reminder(s) per invoice' | translate }}</span>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">{{ 'Needs e-mail set up under Settings and an e-mail address on the customer. The reminders go out before the pause/cancel step above.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Missed billing cycles to bill in one run' | translate }}</label>
          <input class="form-control" style="width:90px;" type="number" min="1" max="12" name="catchUpMax" [(ngModel)]="x.catchUpMax" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">{{ '1 = one cycle per day, so a subscription that was missed for months catches up slowly. A higher number bills the missed cycles at once, each with its own period.' | translate }}</div>
        </div>
        <div><button class="btn btn-primary" type="submit" [disabled]="extraSaving">{{ extraSaving ? ('Saving…' | translate) : ('Save' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class SubscriptionJobSettingsComponent implements OnInit {
  loaded = false;
  saving = false;
  running = false;
  saved = false;
  error: string | null = null;
  runResult: SubscriptionJobResult | null = null;
  lastSummary: string | null = null;

  jobTime = '06:30';
  billingEnabled = true;
  reminderEnabled = false;
  reminderDays = 3;
  reminderCustomer = true;
  reminderStore = false;
  digestEnabled = false;
  overdueAction: 'NONE' | 'PAUSE' | 'CANCEL' = 'NONE';
  overdueDays = 30;

  x = { dunningEnabled: false, dunningFirstDays: 3, dunningRepeatDays: 7, dunningMax: 3, catchUpMax: 1 };
  extraLoaded = false;
  extraSaving = false;
  extraSaved = false;
  extraError: string | null = null;

  constructor(private api: SubscriptionAutomationApiService, private subApi: SubscriptionApiService) {}

  saveExtra(): void {
    this.extraSaving = true;
    this.extraSaved = false;
    this.extraError = null;
    this.subApi.saveSettings(this.x).subscribe({
      next: (r) => { this.extraSaving = false; this.extraSaved = true; this.x = { ...r }; },
      error: (err) => { this.extraSaving = false; this.extraError = err?.error?.message ?? 'Failed to save'; },
    });
  }

  ngOnInit(): void {
    this.subApi.settings().subscribe({ next: (r) => { this.x = { ...r }; this.extraLoaded = true; }, error: () => (this.extraLoaded = false) });
    this.api.get().subscribe({
      next: (s) => {
        this.jobTime = s.jobTime;
        this.billingEnabled = s.billingEnabled;
        this.reminderEnabled = s.reminderEnabled;
        this.reminderDays = s.reminderDays;
        this.reminderCustomer = s.reminderCustomer;
        this.reminderStore = s.reminderStore;
        this.digestEnabled = s.digestEnabled;
        this.overdueAction = s.overdueAction;
        this.overdueDays = s.overdueDays;
        this.lastSummary = s.lastSummary;
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the job settings'),
    });
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    this.api
      .save({
        billingEnabled: this.billingEnabled,
        jobTime: this.jobTime || '06:30',
        reminderEnabled: this.reminderEnabled,
        reminderDays: Number(this.reminderDays) || 3,
        reminderCustomer: this.reminderCustomer,
        reminderStore: this.reminderStore,
        digestEnabled: this.digestEnabled,
        overdueAction: this.overdueAction,
        overdueDays: Number(this.overdueDays) || 30,
      })
      .subscribe({
        next: (s) => {
          this.saving = false;
          this.saved = true;
          this.lastSummary = s.lastSummary;
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Failed to save the job settings';
        },
      });
  }

  runNow(): void {
    this.error = null;
    this.runResult = null;
    this.running = true;
    this.api.run().subscribe({
      next: (r) => {
        this.running = false;
        this.runResult = r;
        this.api.get().subscribe((s) => (this.lastSummary = s.lastSummary));
      },
      error: (err) => {
        this.running = false;
        this.error = err?.error?.message ?? 'Failed to run the subscription job';
      },
    });
  }
}
