import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesSettingsApiService } from '../core/api/sales-settings-api.service';

/**
 * Admin > Sales > Settings — Fourier: "Every time invoice is ISSUED send try to send email to
 * customer and to configured emails. (Add invoices receiver emails in sales settings)." One
 * address per line in the textarea, same simplest-input convention as HelpDeskSettingsComponent's
 * own notification-emails editor — split/joined to the string[] the API expects.
 */
@Component({
  selector: 'app-sales-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Sales settings</h3>
        <a class="btn btn-link" routerLink="/admin/invoices">Back to invoices</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Invoice receiver emails</label>
          <textarea class="form-control" name="emailsText" [(ngModel)]="emailsText" rows="5" placeholder="one address per line"></textarea>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Every time an invoice is issued, the system tries to email a PDF copy to the customer's
            own address and to everyone on this list. Email only goes out if SMTP is configured
            under Settings &gt; Email, and a failed send never blocks or undoes the invoice itself.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">Save</button>
        </div>
      </form>
    </app-shell>
  `,
})
export class SalesSettingsComponent implements OnInit {
  emailsText = '';
  error: string | null = null;
  saved = false;
  saving = false;

  constructor(private settingsApi: SalesSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => (this.emailsText = (s.invoiceReceiverEmails || []).join('\n')),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load settings'),
    });
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    const invoiceReceiverEmails = this.emailsText
      .split('\n')
      .map((e) => e.trim())
      .filter((e) => e.length > 0);
    this.settingsApi.update({ invoiceReceiverEmails }).subscribe({
      next: (s) => {
        this.saving = false;
        this.saved = true;
        this.emailsText = (s.invoiceReceiverEmails || []).join('\n');
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to save settings';
      },
    });
  }
}
