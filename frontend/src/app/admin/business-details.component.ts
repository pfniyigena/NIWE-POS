import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';

/**
 * Admin > Settings > Business Details (Task: "Under Settings menu add Business Details menu" /
 * "Add Business Details like name, address, contact, TAX ID, logo, etc"). Edits the same
 * singleton settings row as SystemSettingsComponent — see StoreSettingsApiService — but only ever
 * saves this screen's own fields (businessName/address/phone/email/taxId/logo), never the
 * currency/timeZone/dateFormat fields the other screen owns.
 */
@Component({
  selector: 'app-business-details',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Business details</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Business details saved.</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <div class="form-group">
          <label class="form-label">Business name</label>
          <input class="form-control" name="businessName" [(ngModel)]="businessName" required />
        </div>
        <div class="form-group">
          <label class="form-label">Address</label>
          <textarea class="form-control" name="address" [(ngModel)]="address" rows="2"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">Phone</label>
          <input class="form-control" name="phone" [(ngModel)]="phone" />
        </div>
        <div class="form-group">
          <label class="form-label">Email</label>
          <input class="form-control" type="email" name="email" [(ngModel)]="email" />
        </div>
        <div class="form-group">
          <label class="form-label">Tax ID</label>
          <input class="form-control" name="taxId" [(ngModel)]="taxId" />
        </div>
        <div class="form-group">
          <label class="form-label">Logo</label>
          <div class="flex gap-2" style="align-items:center;">
            <img *ngIf="logoDataUrl" [src]="logoDataUrl" alt="Business logo" style="max-width:120px; max-height:80px; border:1px solid #444; border-radius:4px;" />
            <span class="text-muted" style="font-size:0.85rem;" *ngIf="!logoDataUrl">No logo uploaded</span>
          </div>
          <input class="form-control" type="file" accept="image/*" (change)="onLogoSelected($event)" style="margin-top:0.5rem;" />
          <div class="alert alert-danger" *ngIf="logoError" style="margin-top:0.5rem;">{{ logoError }}</div>
          <button type="button" class="btn btn-sm btn-outline-secondary" style="margin-top:0.5rem;" *ngIf="logoDataUrl" (click)="removeLogo()">
            Remove logo
          </button>
        </div>
        <button class="btn btn-primary" type="submit" [disabled]="saving || !businessName.trim()">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
      </form>
    </app-shell>
  `,
})
export class BusinessDetailsComponent implements OnInit {
  loaded = false;
  saving = false;
  error: string | null = null;
  saved = false;

  businessName = '';
  address = '';
  phone = '';
  email = '';
  taxId = '';
  logoDataUrl: string | null = null;
  logoError: string | null = null;

  /** Keeps a huge base64 payload out of the request when the file is unreasonably large — there's no server-side resizing here. */
  private readonly maxLogoBytes = 2 * 1024 * 1024;

  constructor(private settingsApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.businessName = s.businessName ?? '';
        this.address = s.address ?? '';
        this.phone = s.phone ?? '';
        this.email = s.email ?? '';
        this.taxId = s.taxId ?? '';
        this.logoDataUrl = s.logoDataUrl;
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load business details'),
    });
  }

  onLogoSelected(event: Event): void {
    this.logoError = null;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > this.maxLogoBytes) {
      this.logoError = 'Logo image is too large (max 2MB) — choose a smaller file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => (this.logoDataUrl = reader.result as string);
    reader.onerror = () => (this.logoError = 'Failed to read the selected file');
    reader.readAsDataURL(file);
  }

  removeLogo(): void {
    this.logoDataUrl = null;
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    this.settingsApi
      .updateBusinessDetails({
        businessName: this.businessName.trim(),
        address: this.address.trim() || null,
        phone: this.phone.trim() || null,
        email: this.email.trim() || null,
        taxId: this.taxId.trim() || null,
        logoDataUrl: this.logoDataUrl,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.saved = true;
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Failed to save business details';
        },
      });
  }
}
