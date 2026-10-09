import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { StoreSettings } from '../models';

export interface BusinessDetailsPayload {
  businessName: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  taxId: string | null;
  logoDataUrl: string | null;
}

export interface SystemSettingsPayload {
  currencyCode: string;
  currencySymbol: string;
  timeZone: string;
  dateFormat: string;
  /** Task 1: "configure the max discount" — see StoreSettings.maxDiscountPercent's backend javadoc. */
  maxDiscountPercent: number;
  /**
   * Fourier: "Same for discount (but discount should be activate in the setting or
   * configuration) ..." then "Except POS other module can have global discount. But again give
   * the user to configure both discount. Either on line or global" — see
   * StoreSettings.discountMode's backend javadoc.
   */
  discountMode: 'NONE' | 'PER_LINE' | 'GLOBAL';
}

/** Admin > Inventory > Settings (Task: "Inventory Setting (Example: allow negative stock)") — its own independent form/endpoint, same reasoning as SystemSettingsPayload vs. BusinessDetailsPayload. */
export interface InventorySettingsPayload {
  allowNegativeStock: boolean;
  /** Fourier: "Product Packaging & Breakdown ... If not activated ignore packaging" — see StoreSettings.productBreakdownEnabled's backend javadoc. */
  productBreakdownEnabled: boolean;
  /** Fourier: "Add allow multi-warehouse in inventory settings" — see StoreSettings.multiWarehouseEnabled's backend javadoc. */
  multiWarehouseEnabled: boolean;
  /** Multi-warehouse phase 4 — the one location Restaurant orders and wastage draw stock from; null/omitted = the primary warehouse's normal stock. */
  restaurantLocationId?: number | null;
  /** WEIGHTED_AVERAGE (default) / LAST_PURCHASE / MANUAL. */
  costingMethod?: string;
}

/**
 * Admin > Settings > Email settings (Fourier: "Configure smtp for sending email"). `smtpPassword`
 * is optional — leave it blank/undefined to keep whatever password is already saved; only a
 * non-blank value overwrites it. The backend never sends the real password back — see
 * StoreSettings.smtpPasswordSet.
 */
export interface EmailSettingsPayload {
  smtpEnabled: boolean;
  smtpHost: string | null;
  smtpPort: number;
  smtpUsername: string | null;
  smtpPassword?: string | null;
  smtpFromEmail: string | null;
  smtpFromName: string | null;
  smtpUseSsl: boolean;
  /** Fourier's own opt-in escape hatch for a real certificate-hostname mismatch — see StoreSettings.smtpSkipCertCheck's backend javadoc. Defaults to false; leave off unless you've hit exactly this problem. */
  smtpSkipCertCheck: boolean;
  /** Fourier: "Wiring a handful of business events to that same mailer" — see StoreSettings.notificationsEmail's backend javadoc. */
  notificationsEmail: string | null;
}

/** Admin > Settings > Business Details / System settings — both screens read the same singleton row via get(). */
@Injectable({ providedIn: 'root' })
export class StoreSettingsApiService {
  constructor(private http: HttpClient) {}

  get(): Observable<StoreSettings> {
    return this.http.get<StoreSettings>(`${API_BASE}/settings`);
  }

  updateBusinessDetails(payload: BusinessDetailsPayload): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(`${API_BASE}/settings/business`, payload);
  }

  updateSystemSettings(payload: SystemSettingsPayload): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(`${API_BASE}/settings/system`, payload);
  }

  updateInventorySettings(payload: InventorySettingsPayload): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(`${API_BASE}/settings/inventory`, payload);
  }

  updateEmailSettings(payload: EmailSettingsPayload): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(`${API_BASE}/settings/email`, payload);
  }

  /** Fourier: "add an Email Test button for easy testing after set up email configurations" — sends a short test email through whatever SMTP settings are currently saved. */
  sendTestEmail(email: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/settings/email/test`, { email });
  }
}
