import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskSettingsApiService } from '../core/api/helpdesk-api.service';
import { HelpDeskSettings } from '../core/models';

/**
 * Admin > Help Desk > Settings — the public page switch, the notification e-mail list (one address per
 * line), the deadline (SLA) hours per priority with warnings and escalation, e-mailed replies, public
 * replies and ratings, and automatic closing of resolved tickets.
 */
@Component({
  selector: 'app-helpdesk-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Help Desk settings' | translate }}</h3>
        <a class="btn btn-link" routerLink="/admin/helpdesk/tickets">{{ 'Back to tickets' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <h4 class="mb-1">{{ 'Public website' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" class="hd-public" name="publicEnabled" [(ngModel)]="f.publicEnabled" /> {{ 'Allow public website' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Anyone can then send a support request and check the progress of a ticket at' | translate }} <strong>{{ '/helpdesk' | translate }}</strong> {{ 'on this site, without signing in. Switch it off to close the page and take its tile off the home page; staff keep working in the Help Desk as usual.' | translate }}</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" class="hd-public-reply-on" name="publicReplyEnabled" [(ngModel)]="f.publicReplyEnabled" /> {{ 'Customers can reply and attach files on the ticket page' | translate }}</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-3"><input type="checkbox" class="hd-satisfaction-on" name="satisfactionEnabled" [(ngModel)]="f.satisfactionEnabled" /> {{ 'Ask customers to rate the help once a ticket is resolved' | translate }}</label>

        <div class="form-group">
          <label class="form-label">{{ 'Notification emails' | translate }}</label>
          <textarea class="form-control" name="emailsText" [(ngModel)]="emailsText" rows="4" placeholder="one address per line"></textarea>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Everyone on this list gets an email whenever a new ticket is created, when a customer replies to a ticket nobody is assigned to, and when a deadline is missed. The assigned staff member and the requester also get emails (assignment, resolution); they don\\'t need to be on this list.' | translate }}
          </div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-3"><input type="checkbox" class="hd-reply-email" name="replyEmailEnabled" [(ngModel)]="f.replyEmailEnabled" /> {{ 'E-mail each reply to the customer (when the ticket has an e-mail address)' | translate }}</label>

        <h4 class="mb-1">{{ 'Deadlines (SLA)' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" class="hd-sla-on" name="slaEnabled" [(ngModel)]="f.slaEnabled" /> {{ 'Give every new ticket a first-reply and a resolution deadline' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Deadlines are counted in calendar hours from the moment the ticket is created. Time spent waiting for the customer does not count. Only tickets created after you switch this on get deadlines.' | translate }}</div>
        <ng-container *ngIf="f.slaEnabled">
          <table class="table-sm mb-2 hd-sla-table">
            <thead><tr><th>{{ 'Priority' | translate }}</th><th>{{ 'First reply within (hours)' | translate }}</th><th>{{ 'Resolved within (hours)' | translate }}</th></tr></thead>
            <tbody>
              <tr><td>{{ 'Urgent' | translate }}</td><td><input class="form-control" type="number" min="1" max="2160" name="slaResponseUrgent" [(ngModel)]="f.slaResponseUrgent" /></td><td><input class="form-control" type="number" min="1" max="2160" name="slaResolutionUrgent" [(ngModel)]="f.slaResolutionUrgent" /></td></tr>
              <tr><td>{{ 'High' | translate }}</td><td><input class="form-control" type="number" min="1" max="2160" name="slaResponseHigh" [(ngModel)]="f.slaResponseHigh" /></td><td><input class="form-control" type="number" min="1" max="2160" name="slaResolutionHigh" [(ngModel)]="f.slaResolutionHigh" /></td></tr>
              <tr><td>{{ 'Medium' | translate }}</td><td><input class="form-control" type="number" min="1" max="2160" name="slaResponseMedium" [(ngModel)]="f.slaResponseMedium" /></td><td><input class="form-control" type="number" min="1" max="2160" name="slaResolutionMedium" [(ngModel)]="f.slaResolutionMedium" /></td></tr>
              <tr><td>{{ 'Low' | translate }}</td><td><input class="form-control" type="number" min="1" max="2160" name="slaResponseLow" [(ngModel)]="f.slaResponseLow" /></td><td><input class="form-control" type="number" min="1" max="2160" name="slaResolutionLow" [(ngModel)]="f.slaResolutionLow" /></td></tr>
            </tbody>
          </table>
          <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="slaInternal" [(ngModel)]="f.slaInternal" /> {{ 'Internal tickets get deadlines too' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" class="hd-escalation-on" name="escalationEnabled" [(ngModel)]="f.escalationEnabled" /> {{ 'E-mail a warning before a deadline and an alert when it is missed' | translate }}</label>
          <div class="form-group" style="max-width:260px;" *ngIf="f.escalationEnabled">
            <label class="form-label">{{ 'Warn this many hours before the resolution deadline (0 = no warning)' | translate }}</label>
            <input class="form-control" type="number" min="0" max="168" name="warnHours" [(ngModel)]="f.warnHours" />
          </div>
        </ng-container>

        <h4 class="mb-1 mt-3">{{ 'Closing resolved tickets' | translate }}</h4>
        <div class="form-group" style="max-width:300px;">
          <label class="form-label">{{ 'Close a resolved ticket automatically after (days, 0 = never)' | translate }}</label>
          <input class="form-control hd-autoclose" type="number" min="0" max="365" name="autoCloseDays" [(ngModel)]="f.autoCloseDays" />
        </div>

        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ 'Save' | translate }}</button>
        </div>
      </form>
    </app-shell>
  `,
})
export class HelpDeskSettingsComponent implements OnInit {
  f: Omit<HelpDeskSettings, 'id' | 'notificationEmails'> = {
    publicEnabled: true, slaEnabled: false, slaInternal: false,
    slaResponseLow: 72, slaResponseMedium: 24, slaResponseHigh: 8, slaResponseUrgent: 2,
    slaResolutionLow: 240, slaResolutionMedium: 120, slaResolutionHigh: 48, slaResolutionUrgent: 8,
    escalationEnabled: false, warnHours: 4, replyEmailEnabled: true, publicReplyEnabled: true, satisfactionEnabled: true, autoCloseDays: 0,
  };
  emailsText = '';
  loaded = false;
  error: string | null = null;
  saved = false;
  saving = false;

  constructor(private settingsApi: HelpDeskSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.apply(s);
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load settings'),
    });
  }

  private apply(s: HelpDeskSettings): void {
    const { id, notificationEmails, ...rest } = s;
    this.f = { ...this.f, ...rest, publicEnabled: s.publicEnabled !== false };
    this.emailsText = (notificationEmails || []).join('\n');
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    const notificationEmails = this.emailsText
      .split('\n')
      .map((e) => e.trim())
      .filter((e) => e.length > 0);
    this.settingsApi.update({ ...this.f, notificationEmails }).subscribe({
      next: (s) => {
        this.saving = false;
        this.saved = true;
        this.apply(s);
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to save settings';
      },
    });
  }
}
