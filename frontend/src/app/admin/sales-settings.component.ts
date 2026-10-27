import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesSettingsApiService } from '../core/api/sales-settings-api.service';
import { ModuleApiService } from '../core/api/module-api.service';

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
        <h4 class="mb-1">Features</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">
          Switch on only what you use. When one is off its menu entry and till parts are hidden and the server refuses to use it; what was already saved (promotions, cards, points) is kept and works again when you switch it back on.
        </div>
        <label class="flex gap-2 align-center mb-1"><input type="checkbox" class="sf-promotions" name="promotionsEnabled" [(ngModel)]="promotionsEnabled" /> Promotions (discounts, buy-X-get-Y, coupons)</label>
        <label class="flex gap-2 align-center mb-1"><input type="checkbox" class="sf-giftcards" name="giftCardsEnabled" [(ngModel)]="giftCardsEnabled" /> Gift cards</label>
        <label class="flex gap-2 align-center mb-3"><input type="checkbox" class="sf-loyalty" name="loyaltyEnabled" [(ngModel)]="loyaltyEnabled" /> Loyalty points (the earning and spending rules are set on the Loyalty page)</label>
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
  promotionsEnabled = false;
  giftCardsEnabled = false;
  loyaltyEnabled = false;
  error: string | null = null;
  saved = false;
  saving = false;

  constructor(private settingsApi: SalesSettingsApiService, private modules: ModuleApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.emailsText = (s.invoiceReceiverEmails || []).join('\n');
        this.promotionsEnabled = s.promotionsEnabled;
        this.giftCardsEnabled = s.giftCardsEnabled;
        this.loyaltyEnabled = s.loyaltyEnabled;
      },
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
    this.settingsApi.update({ invoiceReceiverEmails, promotionsEnabled: this.promotionsEnabled, giftCardsEnabled: this.giftCardsEnabled, loyaltyEnabled: this.loyaltyEnabled }).subscribe({
      next: (s) => {
        this.saving = false;
        this.saved = true;
        this.emailsText = (s.invoiceReceiverEmails || []).join('\n');
        this.modules.refreshFeatures().subscribe();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Failed to save settings';
      },
    });
  }
}
