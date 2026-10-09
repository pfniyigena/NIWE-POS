import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';

/**
 * Admin > Settings > Email settings (Fourier: "Configure smtp for sending email"). Edits the same
 * singleton settings row as BusinessDetailsComponent/SystemSettingsComponent — see
 * StoreSettingsApiService — but only ever saves this screen's own fields.
 *
 * The SMTP password is never sent back by the backend (see StoreSettings.smtpPasswordSet) — the
 * password field here is left blank on load, and submitting it blank keeps whatever password is
 * already saved. Only typing a new password overwrites it.
 *
 * Once this is saved with "Enabled" on, the app can send: the forgot-password email (see
 * AuthController/PasswordResetService), and quotations/invoices/receipts emailed to a customer
 * (the "Email" buttons on those screens, plus Quotation's auto-email-on-send).
 */
@Component({
  selector: 'app-email-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Email settings</h3>
      <p class="text-muted">
        Configure the SMTP account this system sends email from — password reset links, and
        quotations/invoices/receipts emailed to customers.
      </p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Email settings saved.</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="smtpEnabled" [(ngModel)]="smtpEnabled" />
            Enabled
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            While off, no email is sent by this system at all — password reset requests and the
            "Email" buttons on quotations/invoices/receipts will show an error instead.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">SMTP host</label>
          <input class="form-control" name="smtpHost" [(ngModel)]="smtpHost" placeholder="smtp.gmail.com" />
        </div>
        <div class="form-group">
          <label class="form-label">SMTP port</label>
          <input class="form-control" type="number" name="smtpPort" [(ngModel)]="smtpPort" style="max-width:120px;" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            587 for STARTTLS (most providers), 465 for implicit SSL — see "Use SSL" below.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">SMTP username</label>
          <input class="form-control" name="smtpUsername" [(ngModel)]="smtpUsername" autocomplete="off" />
        </div>
        <div class="form-group">
          <label class="form-label">SMTP password</label>
          <input class="form-control" type="password" name="smtpPassword" [(ngModel)]="smtpPassword" autocomplete="new-password"
                 [placeholder]="smtpPasswordSet ? 'Saved — leave blank to keep it' : ''" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="smtpPasswordSet">
            A password is already saved. Leave this blank to keep it, or type a new one to replace it.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="smtpUseSsl" [(ngModel)]="smtpUseSsl" />
            Use SSL (port 465)
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Leave off to use STARTTLS (port 587) instead — check which one your email provider expects.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">From email</label>
          <input class="form-control" type="email" name="smtpFromEmail" [(ngModel)]="smtpFromEmail" placeholder="noreply@yourstore.com" />
        </div>
        <div class="form-group">
          <label class="form-label">From name</label>
          <input class="form-control" name="smtpFromName" [(ngModel)]="smtpFromName" placeholder="Your Store" />
        </div>

        <!-- Fourier: opt-in escape hatch for a real certificate-hostname mismatch that no
             connection-setup change could route around (see StoreSettings.smtpSkipCertCheck's
             backend javadoc) — off by default, only meant for exactly that situation. -->
        <div class="form-group" style="border:1px solid #a86; border-radius:4px; padding:0.75rem;">
          <label class="form-label">
            <input type="checkbox" name="smtpSkipCertCheck" [(ngModel)]="smtpSkipCertCheck" />
            Skip certificate check (advanced, less secure)
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Only turn this on if sending fails with a certificate/hostname error you can't fix any
            other way (e.g. your mail host's certificate doesn't match its own hostname). Mail is
            still sent encrypted, but the app stops verifying that the certificate actually
            belongs to your SMTP host — leave this off unless you've hit exactly that error.
          </div>
        </div>

        <!-- Fourier: "Wiring a handful of business events to that same mailer" — a separate
             recipient address, not tied to being "Enabled" above in the UI's own wording, but
             alerts only actually send once Enabled is on and this address is filled in (see
             StoreSettings.notificationsEmail's backend javadoc). -->
        <div class="form-group" style="border-top:1px solid var(--border-color, #ddd); padding-top:1rem; margin-top:0.5rem;">
          <label class="form-label">Business alerts email</label>
          <input class="form-control" type="email" name="notificationsEmail" [(ngModel)]="notificationsEmail" placeholder="alerts@yourstore.com" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Where to send business alert emails: a product crossing its low-stock threshold, a
            purchase requisition awaiting approval, a quotation about to expire, and a customer
            nearing their credit limit. Leave blank to turn these alerts off.
          </div>
        </div>

        <button class="btn btn-primary" type="submit" [disabled]="saving">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
      </form>

      <!-- Fourier: "add an Email Test button for easy testing after set up email configurations"
           — sends through whatever is currently saved, so save any edits above first. -->
      <div class="card" style="padding:1rem; margin-top:1.5rem;" *ngIf="loaded">
        <h4 class="mb-2" style="font-size:1rem;">Send test email</h4>
        <p class="text-muted" style="font-size:0.85rem;">
          Sends a short test message using the currently saved settings — save your changes above first if you just edited them.
        </p>
        <div class="alert alert-danger" *ngIf="testError">{{ testError }}</div>
        <div class="alert alert-success" *ngIf="testSent">Test email sent to {{ testEmailedTo }} — check that inbox to confirm it arrived.</div>
        <div class="flex gap-2">
          <input class="form-control" type="email" name="testEmail" [(ngModel)]="testEmail" placeholder="you@yourstore.com" />
          <button type="button" class="btn btn-outline-primary" (click)="sendTestEmail()" [disabled]="testing || !testEmail.trim()">
            {{ testing ? 'Sending…' : 'Send test email' }}
          </button>
        </div>
      </div>
    </app-shell>
  `,
})
export class EmailSettingsComponent implements OnInit {
  loaded = false;
  saving = false;
  error: string | null = null;
  saved = false;

  smtpEnabled = false;
  smtpHost: string | null = null;
  smtpPort = 587;
  smtpUsername: string | null = null;
  smtpPassword = '';
  smtpPasswordSet = false;
  smtpFromEmail: string | null = null;
  smtpFromName: string | null = null;
  smtpUseSsl = false;
  smtpSkipCertCheck = false;
  notificationsEmail: string | null = null;

  testEmail = '';
  testing = false;
  testSent = false;
  testError: string | null = null;
  testEmailedTo = '';

  constructor(private settingsApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.smtpEnabled = s.smtpEnabled;
        this.smtpHost = s.smtpHost;
        this.smtpPort = s.smtpPort;
        this.smtpUsername = s.smtpUsername;
        this.smtpPasswordSet = s.smtpPasswordSet;
        this.smtpFromEmail = s.smtpFromEmail;
        this.smtpFromName = s.smtpFromName;
        this.smtpUseSsl = s.smtpUseSsl;
        this.smtpSkipCertCheck = s.smtpSkipCertCheck;
        this.notificationsEmail = s.notificationsEmail;
        this.loaded = true;
        // Pre-fill the test recipient with whatever already looks like an address, so the common
        // case (confirm your own "From" address can actually receive mail) needs no typing.
        this.testEmail = s.smtpFromEmail ?? (s.smtpUsername?.includes('@') ? s.smtpUsername : '') ?? '';
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load email settings'),
    });
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    this.settingsApi
      .updateEmailSettings({
        smtpEnabled: this.smtpEnabled,
        smtpHost: this.smtpHost,
        smtpPort: this.smtpPort,
        smtpUsername: this.smtpUsername,
        // Blank means "keep the existing saved password" — see EmailSettingsRequest on the backend.
        smtpPassword: this.smtpPassword ? this.smtpPassword : undefined,
        smtpFromEmail: this.smtpFromEmail,
        smtpFromName: this.smtpFromName,
        smtpUseSsl: this.smtpUseSsl,
        smtpSkipCertCheck: this.smtpSkipCertCheck,
        notificationsEmail: this.notificationsEmail,
      })
      .subscribe({
        next: (s) => {
          this.saving = false;
          this.saved = true;
          this.smtpPasswordSet = s.smtpPasswordSet;
          this.smtpPassword = '';
          this.notificationsEmail = s.notificationsEmail;
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Failed to save email settings';
        },
      });
  }

  sendTestEmail(): void {
    const email = this.testEmail.trim();
    if (!email) return;
    this.testError = null;
    this.testSent = false;
    this.testing = true;
    this.settingsApi.sendTestEmail(email).subscribe({
      next: () => {
        this.testing = false;
        this.testSent = true;
        this.testEmailedTo = email;
      },
      error: (err) => {
        this.testing = false;
        this.testError = err?.error?.message ?? 'Failed to send test email';
      },
    });
  }
}
