import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { BillingInterval, CreateSubscriptionPayload, Invoice, Subscription, SubscriptionStatus } from '../models';

export interface SubscriptionEvent {
  id: number;
  kind: 'CREATED' | 'EDITED' | 'PLAN_CHANGED' | 'BILLED' | 'SKIPPED' | 'PAUSED' | 'RESUMED' | 'CANCEL_SCHEDULED' | 'CANCEL_UNDONE' | 'CANCELLED' | 'RENEWED' | 'ENDED' | 'REMINDER';
  detail: string | null;
  by: string | null;
  at: string;
}

export interface SubscriptionUpcoming { subscriptionId: number; customerName: string; planName: string | null; date: string; amount: number; onTrial: boolean; }
export interface SubscriptionUnpaid { subscriptionId: number; customerName: string; invoiceNumber: string; outstanding: number; daysLate: number; reminders: number; }

export interface SubscriptionSummary {
  active: number; onTrial: number; paused: number; cancelled: number; ended: number;
  mrr: number; arr: number; averagePerSubscription: number;
  newLast30: number; cancelledLast30: number; churnPercent: number;
  upcoming7Count: number; upcoming7Amount: number; upcoming30Count: number; upcoming30Amount: number;
  trialsEnding7: number; scheduledCancels: number; termsEnding: number;
  unpaidCount: number; unpaidAmount: number;
  upcoming: SubscriptionUpcoming[]; unpaid: SubscriptionUnpaid[];
}

export interface SubscriptionChangeQuote {
  prorated: boolean; remainingDays: number; periodDays: number;
  oldCycleAmount: number; newCycleAmount: number; chargeNow: number; creditAdded: number;
  monthlyBefore: number; monthlyAfter: number; message: string;
}

export interface SubscriptionSettings { dunningEnabled: boolean; dunningFirstDays: number; dunningRepeatDays: number; dunningMax: number; catchUpMax: number; }

export interface SubscriptionUpdatePayload {
  price?: number | null; billingInterval?: BillingInterval | null; quantity?: number | null; discountPercent?: number | null;
  /** null = tax-exempt. */
  taxRateId?: number | null; setupFee?: number | null;
  /** 0 clears the term (open-ended). */
  termCycles?: number | null; autoRenew?: boolean | null; notes?: string | null;
}

export interface SubscriptionChangePlanPayload {
  planId?: number | null; price?: number | null; billingInterval?: BillingInterval | null; quantity?: number | null;
  discountPercent?: number | null; taxRateId?: number | null; prorate?: boolean | null;
}

export interface SubscriptionListFilter {
  customerId?: number; status?: SubscriptionStatus | ''; planId?: number | null; search?: string; view?: string;
}

/** Admin > Sales > Subscriptions — a customer's subscription to a SubscriptionPlan; billing is driven by the backend SubscriptionService (daily sweep + manual "bill now"). */
@Injectable({ providedIn: 'root' })
export class SubscriptionApiService {
  constructor(private http: HttpClient) {}

  listAll(customerId?: number, f: SubscriptionListFilter = {}): Observable<Subscription[]> {
    const q: string[] = [];
    const cid = customerId ?? f.customerId;
    if (cid != null) q.push(`customerId=${cid}`);
    if (f.status) q.push(`status=${f.status}`);
    if (f.planId != null) q.push(`planId=${f.planId}`);
    if (f.search && f.search.trim()) q.push(`search=${encodeURIComponent(f.search.trim())}`);
    if (f.view) q.push(`view=${f.view}`);
    return this.http.get<Subscription[]>(`${API_BASE}/subscriptions${q.length ? '?' + q.join('&') : ''}`);
  }

  getById(id: number): Observable<Subscription> {
    return this.http.get<Subscription>(`${API_BASE}/subscriptions/${id}`);
  }

  create(payload: CreateSubscriptionPayload): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions`, payload);
  }

  pause(id: number, reason?: string | null, resumeOn?: string | null): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/pause`, { reason: reason || null, resumeOn: resumeOn || null });
  }

  skipNext(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/skip-next`, {});
  }

  update(id: number, payload: SubscriptionUpdatePayload): Observable<Subscription> {
    return this.http.put<Subscription>(`${API_BASE}/subscriptions/${id}`, payload);
  }

  quoteChange(id: number, payload: SubscriptionChangePlanPayload): Observable<SubscriptionChangeQuote> {
    return this.http.post<SubscriptionChangeQuote>(`${API_BASE}/subscriptions/${id}/change-plan/quote`, payload);
  }

  changePlan(id: number, payload: SubscriptionChangePlanPayload): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/change-plan`, payload);
  }

  undoCancel(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/undo-cancel`, {});
  }

  events(id: number): Observable<SubscriptionEvent[]> {
    return this.http.get<SubscriptionEvent[]>(`${API_BASE}/subscriptions/${id}/events`);
  }

  summary(): Observable<SubscriptionSummary> {
    return this.http.get<SubscriptionSummary>(`${API_BASE}/subscriptions/summary`);
  }

  settings(): Observable<SubscriptionSettings> {
    return this.http.get<SubscriptionSettings>(`${API_BASE}/subscriptions/settings`);
  }

  saveSettings(payload: Partial<SubscriptionSettings>): Observable<SubscriptionSettings> {
    return this.http.put<SubscriptionSettings>(`${API_BASE}/subscriptions/settings`, payload);
  }

  resume(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/resume`, {});
  }

  cancel(id: number, reason: string | null, atPeriodEnd = false): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/cancel`, { reason, atPeriodEnd });
  }

  /** Manual escape hatch mirroring RecurringExpenseTemplate's "run now" — bills immediately instead of waiting for the daily sweep. */
  billNow(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/bill-now`, {});
  }

  /** Every invoice ever auto-generated for this subscription, newest first. */
  billingHistory(id: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${API_BASE}/subscriptions/${id}/invoices`);
  }

  /** Fourier: "Print also subscription" — open in a new tab via openPdfForPrint, same pattern as InvoiceApiService.pdf. */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/subscriptions/${id}/pdf`, { responseType: 'blob' });
  }
}
