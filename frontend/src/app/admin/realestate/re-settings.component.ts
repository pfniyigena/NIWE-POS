import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { DigestResult, RealEstateApiService, ReminderResult, ReSettings } from '../../core/api/realestate-api.service';
import { AccountApiService } from '../../core/api/account-api.service';
import { Account } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Real Estate > Settings (admin): the list of areas, how long a viewing lasts, when a lead counts as quiet, the commission, and the public website. */
@Component({
  selector: 'app-re-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Real Estate settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form class="card" style="padding:1rem; max-width:40rem;" (ngSubmit)="save()">
        <div class="form-group mb-2"><label class="form-label">{{ 'Areas you work in' | translate }} <span class="text-muted">{{ '(one per line)' | translate }}</span></label>
          <textarea class="form-control" rows="8" name="areas" [(ngModel)]="areas" placeholder="Kololo&#10;Nakasero&#10;Ntinda"></textarea>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'They appear as suggestions on properties and leads, and in the filters.' | translate }}</div></div>
        <div class="flex gap-2">
          <div class="form-group mb-2"><label class="form-label">{{ 'A viewing lasts (minutes)' | translate }}</label><input class="form-control" type="number" min="5" max="480" name="vm" [(ngModel)]="viewingMinutes" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'A lead is "quiet" after (days)' | translate }}</label><input class="form-control" type="number" min="1" max="365" name="sl" [(ngModel)]="staleLeadDays" /></div>
        </div>
        <h4 class="mb-1 mt-2">{{ 'Lead follow-ups' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="fe" [(ngModel)]="followUpEmail" /> {{ 'E-mail each agent every morning (about 06:30)' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Each agent gets one e-mail with the calls that are late or due today and the leads that have gone quiet. Agents need an e-mail address on their user account. They see the same lists, with buttons to log a call, under' | translate }} <strong>{{ 'My follow-ups' | translate }}</strong>.</div>
        <div class="alert alert-warning mb-2" *ngIf="!mailConfigured">E-mail is not set up yet, so nothing can be sent. Set up SMTP in Settings &gt; Email first.</div>
        <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="lastRun">{{ 'Last run: {p0} — {p1}' | translate: { p0: (lastRun | date: 'd MMM y'), p1: (lastSummary) } }}</div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm btn-outline-secondary fu-preview" type="button" (click)="digest(true)" [disabled]="digestBusy">{{ 'Preview who would get an e-mail' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary fu-send" type="button" (click)="digest(false)" [disabled]="digestBusy || !mailConfigured">{{ 'Send it now' | translate }}</button>
        </div>
        <div class="alert alert-danger" *ngIf="digestError">{{ digestError }}</div>
        <div *ngIf="digestResult" class="mb-3">
          <div class="mb-1"><strong>{{ digestResult.summary }}</strong></div>
          <table class="stack table-sm" *ngIf="digestResult.details.length">
            <thead><tr><th>{{ 'Agent' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th class="text-end">{{ 'Late' | translate }}</th><th class="text-end">{{ 'Today' | translate }}</th><th class="text-end">{{ 'Quiet' | translate }}</th><th>{{ 'Result' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let d of digestResult.details">
              <td data-label="Agent">{{ d.name }}</td><td data-label="E-mail">{{ d.email || 'none on file' }}</td>
              <td class="text-end" data-label="Late">{{ d.overdue }}</td><td class="text-end" data-label="Today">{{ d.today }}</td><td class="text-end" data-label="Quiet">{{ d.quiet }}</td>
              <td data-label="Result">{{ statusName(d.status) }}</td></tr></tbody>
          </table>
        </div>
        <h4 class="mb-1 mt-2">{{ 'Viewing reminders' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="vre" [(ngModel)]="reminderOn" /> {{ 'E-mail a reminder before each viewing' | translate }}</label>
        <div class="flex gap-2 mb-1" style="align-items:center; flex-wrap:wrap;" *ngIf="reminderOn">
          <label class="form-label" style="margin:0;">{{ 'Send it' | translate }}</label><input class="form-control" style="width:6rem;" type="number" min="1" max="72" name="vrh" [(ngModel)]="reminderHours" /><span>{{ 'hours before the viewing' | translate }}</span>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The person viewing gets the property, address, time and who will meet them, with your website phone and e-mail (above) to cancel on. The agent gets one e-mail listing the viewings coming up. A reminder is sent once; moving a viewing sends a fresh one. You can also send one by hand from the Viewings screen.' | translate }}</div>
        <div class="alert alert-warning mb-2" *ngIf="reminderOn && !mailConfigured">E-mail is not set up yet, so nothing can be sent. Set up SMTP in Settings &gt; Email first.</div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm btn-outline-secondary vr-preview" type="button" (click)="reminders(true)" [disabled]="reminderBusy">{{ 'Preview what would be sent' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary vr-send" type="button" (click)="reminders(false)" [disabled]="reminderBusy || !mailConfigured">{{ 'Send due reminders now' | translate }}</button>
        </div>
        <div class="alert alert-danger" *ngIf="reminderError">{{ reminderError }}</div>
        <div *ngIf="reminderResult" class="mb-3">
          <div class="mb-1"><strong>{{ reminderResult.summary }}</strong></div>
          <table class="stack table-sm" *ngIf="reminderResult.lines.length">
            <thead><tr><th>{{ 'Viewing' | translate }}</th><th>{{ 'When' | translate }}</th><th>{{ 'Client' | translate }}</th><th>{{ 'Client e-mail' | translate }}</th><th>{{ 'Agent' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let l of reminderResult.lines">
              <td data-label="Viewing">{{ l.propertyCode }} {{ l.propertyTitle }}</td><td data-label="When">{{ l.startAt | date: 'EEE d MMM, HH:mm' }}</td>
              <td data-label="Client">{{ l.leadName }}</td><td data-label="Client e-mail">{{ l.client ? statusName(l.client) : '—' }}</td>
              <td data-label="Agent">{{ l.agentName }}{{ l.agent ? ': ' + statusName(l.agent) : '' }}</td></tr></tbody>
          </table>
        </div>
        <h4 class="mb-1 mt-2">{{ 'Printed documents' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The offer letter, reservation letter and sale agreement are printed from the deal or the unit sale. Write your own terms below, one line per clause, or leave a box empty to use the standard wording. Have your lawyer check the wording you use.' | translate }}</div>
        <label class="form-label" for="ot">{{ 'Offer letter terms' | translate }}</label>
        <textarea id="ot" class="form-control mb-1" name="ot" rows="4" maxlength="3000" [(ngModel)]="offerTerms" placeholder="Empty = standard wording"></textarea>
        <label class="form-label" for="rt">{{ 'Reservation letter terms' | translate }}</label>
        <textarea id="rt" class="form-control mb-1" name="rt" rows="4" maxlength="3000" [(ngModel)]="reservationTerms" placeholder="Empty = standard wording"></textarea>
        <label class="form-label" for="at">{{ 'Sale agreement terms' | translate }}</label>
        <textarea id="at" class="form-control mb-2" name="at" rows="5" maxlength="3000" [(ngModel)]="agreementTerms" placeholder="Empty = standard wording"></textarea>
        <h4 class="mb-1 mt-2">{{ 'Sales and commission' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">{{ 'Usual commission on a sale (%)' | translate }}</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="100" step="0.01" name="dc" [(ngModel)]="commissionPercent" />
            <div class="text-muted" style="font-size:0.85rem;">{{ 'New deals start with this; change it on a deal when it differs. 0 means none.' | translate }}</div></div>
          <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Expense account for commission paid out' | translate }}</label>
            <select class="form-select" name="ca" [(ngModel)]="commissionAccountId"><option [ngValue]="null">{{ '— choose when posting —' | translate }}</option><option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} · {{ a.name }}</option></select>
            <div class="text-muted" style="font-size:0.85rem;">{{ 'Commission shares are posted to the accounts as expenses (they need the Expenses module).' | translate }}</div></div>
        </div>
        <h4 class="mb-1 mt-2">{{ 'Public website' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="we" [(ngModel)]="websiteEnabled" /> {{ 'Open the public listings website' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Anyone can then see the properties you tick "Show on the public website" (and that are Available) at' | translate }} <strong>{{ '/listings' | translate }}</strong> {{ 'on this site, and send an enquiry that becomes a lead. Switch it off to close the whole site at once.' | translate }}</div>
        <ng-container *ngIf="websiteEnabled">
          <div class="form-group mb-2"><label class="form-label">{{ 'Heading' | translate }}</label><input class="form-control" name="wt" [(ngModel)]="websiteTitle" maxlength="120" [placeholder]="'e.g. Homes and land for sale and rent' | translate" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Introduction' | translate }}</label><textarea class="form-control" rows="3" name="wi" [(ngModel)]="websiteIntro" maxlength="1000"></textarea></div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Phone shown to visitors' | translate }}</label><input class="form-control" name="wp" [(ngModel)]="websitePhone" maxlength="60" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'E-mail shown to visitors' | translate }}</label><input class="form-control" type="email" name="wm" [(ngModel)]="websiteEmail" /></div>
          </div>
          <div class="form-group mb-3"><label class="form-label">{{ 'Most enquiries one phone number or e-mail may send in a day' | translate }}</label><input class="form-control" style="max-width:8rem;" type="number" min="1" max="50" name="wl" [(ngModel)]="enquiriesPerDay" /></div>
        </ng-container>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save settings' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class ReSettingsComponent implements OnInit {
  areas = '';
  viewingMinutes = 30;
  staleLeadDays = 14;
  websiteEnabled = false;
  websiteTitle = '';
  websiteIntro = '';
  websitePhone = '';
  websiteEmail = '';
  followUpEmail = false;
  reminderOn = false;
  offerTerms = '';
  reservationTerms = '';
  agreementTerms = '';
  reminderHours = 24;
  reminderBusy = false;
  reminderError: string | null = null;
  reminderResult: ReminderResult | null = null;
  mailConfigured = true;
  lastRun: string | null = null;
  lastSummary: string | null = null;
  digestBusy = false;
  digestError: string | null = null;
  digestResult: DigestResult | null = null;
  enquiriesPerDay = 3;
  commissionPercent: number | null = null;
  commissionAccountId: number | null = null;
  accounts: Account[] = [];
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(private api: RealEstateApiService, private accountApi: AccountApiService) {}

  ngOnInit(): void {
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a), error: () => (this.accounts = []) });
    this.api.settings().subscribe({
      next: (s) => this.fill(s),
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the settings')),
    });
  }

  private fill(s: ReSettings): void {
    this.areas = s.areas.join('\n');
    this.viewingMinutes = s.viewingMinutes;
    this.staleLeadDays = s.staleLeadDays;
    this.websiteEnabled = s.websiteEnabled;
    this.websiteTitle = s.websiteTitle ?? '';
    this.websiteIntro = s.websiteIntro ?? '';
    this.websitePhone = s.websitePhone ?? '';
    this.websiteEmail = s.websiteEmail ?? '';
    this.enquiriesPerDay = s.enquiriesPerPhonePerDay;
    this.commissionPercent = s.defaultCommissionPercent;
    this.commissionAccountId = s.commissionAccountId;
    this.followUpEmail = s.followUpEmailEnabled;
    this.reminderOn = s.viewingReminderEnabled;
    this.offerTerms = s.offerTerms ?? '';
    this.reservationTerms = s.reservationTerms ?? '';
    this.agreementTerms = s.agreementTerms ?? '';
    this.reminderHours = s.viewingReminderHours;
    this.mailConfigured = s.mailConfigured;
    this.lastRun = s.followUpLastRun;
    this.lastSummary = s.followUpLastSummary;
  }

  statusName(s: string): string {
    return ({ SENT: 'E-mailed', WOULD_SEND: 'Would be e-mailed', NO_EMAIL: 'Skipped: no e-mail address', NOT_CONFIGURED: 'Skipped: e-mail not set up', FAILED: 'Failed' } as Record<string, string>)[s] ?? s;
  }

  reminders(preview: boolean): void {
    this.reminderBusy = true;
    this.reminderError = null;
    this.api.sendViewingReminders(preview).subscribe({
      next: (r) => { this.reminderBusy = false; this.reminderResult = r; },
      error: (e) => { this.reminderBusy = false; this.reminderError = e?.error?.message ?? 'Could not run it'; },
    });
  }

  digest(preview: boolean): void {
    this.digestBusy = true;
    this.digestError = null;
    this.api.sendFollowUpEmails(preview).subscribe({
      next: (r) => { this.digestBusy = false; this.digestResult = r; if (!preview) this.api.settings().subscribe({ next: (x) => this.fill(x), error: () => {} }); },
      error: (e) => { this.digestBusy = false; this.digestError = e?.error?.message ?? 'Could not run it'; },
    });
  }

  save(): void {
    this.saving = true;
    this.saved = false;
    this.error = null;
    // The server splits on commas, so one area per line becomes a comma list.
    const areas = this.areas.split('\n').map((a) => a.trim()).filter((a) => a).join(', ');
    this.api.saveSettings({ areas, viewingMinutes: this.viewingMinutes, staleLeadDays: this.staleLeadDays, websiteEnabled: this.websiteEnabled, websiteTitle: this.websiteTitle,
      websiteIntro: this.websiteIntro, websitePhone: this.websitePhone, websiteEmail: this.websiteEmail, enquiriesPerPhonePerDay: this.enquiriesPerDay,
      defaultCommissionPercent: this.commissionPercent ?? 0, commissionAccountId: this.commissionAccountId ?? 0, followUpEmailEnabled: this.followUpEmail, viewingReminderEnabled: this.reminderOn, viewingReminderHours: this.reminderHours, offerTerms: this.offerTerms, reservationTerms: this.reservationTerms, agreementTerms: this.agreementTerms }).subscribe({
      next: (s) => { this.saving = false; this.saved = true; this.fill(s); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? T('Could not save the settings'); },
    });
  }
}
