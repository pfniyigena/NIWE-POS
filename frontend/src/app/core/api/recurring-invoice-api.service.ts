import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Invoice, RecurringInvoiceTemplate, RecurringInvoiceTemplatePayload } from '../models';

/** Admin > Sales > Recurring invoices — a saved template with real invoice line items that auto-generates a brand-new, already-ISSUED invoice on a schedule (daily sweep + manual "run now"), driven by the backend RecurringInvoiceTemplateService. */
@Injectable({ providedIn: 'root' })
export class RecurringInvoiceApiService {
  constructor(private http: HttpClient) {}

  listAll(customerId?: number): Observable<RecurringInvoiceTemplate[]> {
    const url = customerId != null
      ? `${API_BASE}/recurring-invoices?customerId=${customerId}`
      : `${API_BASE}/recurring-invoices`;
    return this.http.get<RecurringInvoiceTemplate[]>(url);
  }

  getById(id: number): Observable<RecurringInvoiceTemplate> {
    return this.http.get<RecurringInvoiceTemplate>(`${API_BASE}/recurring-invoices/${id}`);
  }

  create(payload: RecurringInvoiceTemplatePayload): Observable<RecurringInvoiceTemplate> {
    return this.http.post<RecurringInvoiceTemplate>(`${API_BASE}/recurring-invoices`, payload);
  }

  update(id: number, payload: RecurringInvoiceTemplatePayload): Observable<RecurringInvoiceTemplate> {
    return this.http.put<RecurringInvoiceTemplate>(`${API_BASE}/recurring-invoices/${id}`, payload);
  }

  setActive(id: number, active: boolean): Observable<RecurringInvoiceTemplate> {
    return this.http.post<RecurringInvoiceTemplate>(`${API_BASE}/recurring-invoices/${id}/active`, { active });
  }

  /** Manual escape hatch mirroring RecurringExpenseTemplate/Subscription's own "run now"/"bill now" — generates immediately instead of waiting for the daily sweep. */
  runNow(id: number): Observable<RecurringInvoiceTemplate> {
    return this.http.post<RecurringInvoiceTemplate>(`${API_BASE}/recurring-invoices/${id}/run-now`, {});
  }

  /** Every invoice ever auto-generated for this template, newest first. */
  generatedInvoices(id: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${API_BASE}/recurring-invoices/${id}/invoices`);
  }
}
