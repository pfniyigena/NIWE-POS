import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskSettingsApiService } from '../core/api/helpdesk-api.service';

/**
 * Admin > Help Desk > Settings — Fourier: "Once the ticket is created it should send email to
 * the helpdesk configured emails(this should be set to the helpdesk setting and the email can be
 * a list." One address per line in the textarea (simplest input for an admin-edited list with no
 * other per-address fields), split/joined to the string[] the API expects — see the backend
 * HelpDeskSettingsService's own javadoc for how that list is stored.
 */
@Component({
  selector: 'app-helpdesk-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Help Desk settings</h3>
        <a class="btn btn-link" routerLink="/admin/helpdesk/tickets">Back to tickets</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Notification emails</label>
          <textarea class="form-control" name="emailsText" [(ngModel)]="emailsText" rows="5" placeholder="one address per line"></textarea>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Everyone on this list gets an email whenever a new ticket (customer or internal) is created.
            When a ticket is assigned, the assigned staff member and the ticket's requester each get an email too —
            and again once the ticket is resolved. Those two don't need to be on this list; it's only for new-ticket alerts.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">Save</button>
        </div>
      </form>
    </app-shell>
  `,
})
export class HelpDeskSettingsComponent implements OnInit {
  emailsText = '';
  error: string | null = null;
  saved = false;
  saving = false;

  constructor(private settingsApi: HelpDeskSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => (this.emailsText = (s.notificationEmails || []).join('\n')),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load settings'),
    });
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    const notificationEmails = this.emailsText
      .split('\n')
      .map((e) => e.trim())
      .filter((e) => e.length > 0);
    this.settingsApi.update({ notificationEmails }).subscribe({
      next: (s) => {
        this.saving = false;
        this.saved = true;
        this.emailsText = (s.notificationEmails || []).join('\n');
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to save settings';
      },
    });
  }
}
