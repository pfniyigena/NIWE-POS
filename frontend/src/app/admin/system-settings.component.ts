import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';

/** One entry of the currency picker — picking a code fills in its usual symbol, still editable afterward. */
interface CurrencyOption {
  code: string;
  name: string;
  symbol: string;
}

/**
 * Admin > Settings > System settings (Task: "Add System setting like currency, time zone, date
 * format, etc"). Edits the same singleton settings row as BusinessDetailsComponent — see
 * StoreSettingsApiService — but only ever saves this screen's own fields.
 *
 * Not yet wired into how amounts/dates actually render elsewhere in the app (receipts/reports
 * still format plainly, with no symbol) — that's a cross-cutting change touching every screen
 * that shows a price or a date, out of scope for just adding the settings themselves. See the
 * README "Known limitations" for this gap.
 */
@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">System settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">System settings saved.</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <div class="form-group">
          <label class="form-label">Currency</label>
          <select class="form-select" name="currencyCode" [(ngModel)]="currencyCode" (ngModelChange)="onCurrencyChange($event)">
            <option *ngFor="let c of currencies" [value]="c.code">{{ c.code }} — {{ c.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Currency symbol</label>
          <input class="form-control" name="currencySymbol" [(ngModel)]="currencySymbol" required style="max-width:120px;" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Filled in from the currency above — override it if you'd rather show a code (e.g. "UGX") instead of a symbol.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Time zone</label>
          <select class="form-select" name="timeZone" [(ngModel)]="timeZone">
            <option *ngFor="let tz of timeZones" [value]="tz">{{ tz }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Date format</label>
          <select class="form-select" name="dateFormat" [(ngModel)]="dateFormat">
            <option *ngFor="let f of dateFormats" [value]="f.value">{{ f.label }}</option>
          </select>
        </div>
        <!-- Task 1: "In the system setting configure the max discount" — a store-wide ceiling any
             single order line's discount can't exceed, enforced backend-side (see
             OrderService.validateDiscount) for both the online terminal and offline-sync flows,
             regardless of who's applying it (see Task 2's separate admin-only restriction on the
             terminal's discount input itself). -->
        <div class="form-group">
          <label class="form-label">Max discount (%)</label>
          <input class="form-control" type="number" step="0.01" min="0" max="100" name="maxDiscountPercent" [(ngModel)]="maxDiscountPercent" required style="max-width:120px;" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The highest discount percentage allowed on any single sale line, store-wide.
          </div>
        </div>
        <!-- Fourier: "Same for discount (but discount should be activate in the setting or
             configuration) and it will be per line" then "Except POS other module can have
             global discount. But again give the user to configure both discount. Either on line
             or global" — a single store-wide mode for Quotation/Sale Order/Invoice/Purchase
             Order (Bill/Credit Note/Refund inherit theirs): Off, Per-line (each line gets its own
             discount), or Global (one percentage entered once for the whole document) — never
             both at once. Off by default; the max-discount ceiling above still applies once
             either mode is on. -->
        <div class="form-group">
          <label class="form-label">Discount</label>
          <div class="form-check">
            <input class="form-check-input" type="radio" id="discountModeNone" name="discountMode" value="NONE" [(ngModel)]="discountMode" />
            <label class="form-check-label" for="discountModeNone">Off — no discount field on Quotations/Sales Orders/Invoices/Purchase Orders/Bills</label>
          </div>
          <div class="form-check">
            <input class="form-check-input" type="radio" id="discountModePerLine" name="discountMode" value="PER_LINE" [(ngModel)]="discountMode" />
            <label class="form-check-label" for="discountModePerLine">Per line — each line has its own discount</label>
          </div>
          <div class="form-check">
            <input class="form-check-input" type="radio" id="discountModeGlobal" name="discountMode" value="GLOBAL" [(ngModel)]="discountMode" />
            <label class="form-check-label" for="discountModeGlobal">Global — one discount for the whole document</label>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Only one mode applies at a time, store-wide. The POS terminal's own discount is separate and always available (subject to role permissions).
          </div>
        </div>
        <button class="btn btn-primary" type="submit" [disabled]="saving">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
      </form>
    </app-shell>
  `,
})
export class SystemSettingsComponent implements OnInit {
  loaded = false;
  saving = false;
  error: string | null = null;
  saved = false;

  currencyCode = 'USD';
  currencySymbol = '$';
  timeZone = 'UTC';
  dateFormat = 'dd/MM/yyyy';
  /** Task 1: "configure the max discount" — see StoreSettings.maxDiscountPercent's backend javadoc. Matches the entity's own default until the real value loads. */
  maxDiscountPercent = 100;
  /**
   * Fourier: "Same for discount ..." then "Except POS other module can have global discount ...
   * Either on line or global" — see StoreSettings.discountMode's backend javadoc. Matches the
   * entity's own default (NONE) until the real value loads.
   */
  discountMode: 'NONE' | 'PER_LINE' | 'GLOBAL' = 'NONE';

  // A curated, not exhaustive, list — enough to cover this app's actual userbase without
  // maintaining the full ISO 4217 table. "Other" isn't offered; an admin who needs a currency
  // not listed here can still type any symbol into the field above.
  currencies: CurrencyOption[] = [
    { code: 'USD', name: 'US Dollar', symbol: '$' },
    { code: 'EUR', name: 'Euro', symbol: '€' },
    { code: 'GBP', name: 'British Pound', symbol: '£' },
    { code: 'UGX', name: 'Ugandan Shilling', symbol: 'USh' },
    { code: 'KES', name: 'Kenyan Shilling', symbol: 'KSh' },
    { code: 'TZS', name: 'Tanzanian Shilling', symbol: 'TSh' },
    { code: 'RWF', name: 'Rwandan Franc', symbol: 'FRw' },
    { code: 'NGN', name: 'Nigerian Naira', symbol: '₦' },
    { code: 'GHS', name: 'Ghanaian Cedi', symbol: 'GH₵' },
    { code: 'ZAR', name: 'South African Rand', symbol: 'R' },
    { code: 'INR', name: 'Indian Rupee', symbol: '₹' },
  ];

  dateFormats = [
    { value: 'dd/MM/yyyy', label: 'DD/MM/YYYY (31/01/2026)' },
    { value: 'MM/dd/yyyy', label: 'MM/DD/YYYY (01/31/2026)' },
    { value: 'yyyy-MM-dd', label: 'YYYY-MM-DD (2026-01-31)' },
    { value: 'd MMM yyyy', label: 'D MMM YYYY (31 Jan 2026)' },
  ];

  /**
   * Intl.supportedValuesOf('timeZone') gives the full, current IANA tz database straight from the
   * browser (supported on all modern desktop/mobile browsers) instead of hand-maintaining a list
   * that inevitably goes stale. Falls back to a short hand-picked list on the rare browser without
   * it, so the picker is never empty.
   */
  timeZones: string[] = (() => {
    try {
      const supported = (Intl as any).supportedValuesOf?.('timeZone');
      if (Array.isArray(supported) && supported.length > 0) return supported;
    } catch {
      // fall through to the static list below
    }
    return [
      'UTC',
      'Africa/Kampala',
      'Africa/Nairobi',
      'Africa/Lagos',
      'Africa/Johannesburg',
      'Africa/Cairo',
      'Europe/London',
      'Europe/Paris',
      'America/New_York',
      'America/Los_Angeles',
      'Asia/Dubai',
      'Asia/Kolkata',
    ];
  })();

  constructor(private settingsApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.currencyCode = s.currencyCode;
        this.currencySymbol = s.currencySymbol;
        this.timeZone = s.timeZone;
        this.dateFormat = s.dateFormat;
        this.maxDiscountPercent = s.maxDiscountPercent;
        this.discountMode = s.discountMode;
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load system settings'),
    });
  }

  onCurrencyChange(code: string): void {
    const match = this.currencies.find((c) => c.code === code);
    if (match) {
      this.currencySymbol = match.symbol;
    }
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    this.settingsApi
      .updateSystemSettings({
        currencyCode: this.currencyCode,
        currencySymbol: this.currencySymbol,
        timeZone: this.timeZone,
        dateFormat: this.dateFormat,
        maxDiscountPercent: this.maxDiscountPercent,
        discountMode: this.discountMode,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.saved = true;
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Failed to save system settings';
        },
      });
  }
}
