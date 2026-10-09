import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CreateSubscriptionPayload, Invoice, Subscription } from '../models';

/** Admin > Sales > Subscriptions — a customer's subscription to a SubscriptionPlan; billing is driven by the backend SubscriptionService (daily sweep + manual "bill now"). */
@Injectable({ providedIn: 'root' })
export class SubscriptionApiService {
  constructor(private http: HttpClient) {}

  listAll(customerId?: number): Observable<Subscription[]> {
    const url = customerId != null
      ? `${API_BASE}/subscriptions?customerId=${customerId}`
      : `${API_BASE}/subscriptions`;
    return this.http.get<Subscription[]>(url);
  }

  getById(id: number): Observable<Subscription> {
    return this.http.get<Subscription>(`${API_BASE}/subscriptions/${id}`);
  }

  create(payload: CreateSubscriptionPayload): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions`, payload);
  }

  pause(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/pause`, {});
  }

  resume(id: number): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/resume`, {});
  }

  cancel(id: number, reason: string | null): Observable<Subscription> {
    return this.http.post<Subscription>(`${API_BASE}/subscriptions/${id}/cancel`, { reason });
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
