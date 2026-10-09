import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CancelPaidInvoiceRequest, CustomerPayment, Invoice, InvoicePayload, RecordInvoicePaymentRequest } from '../models';

/** Fourier: "Add Invoice ... without touching warehousing"; later: "create invoice related to the Sale Order and record payment on invoice ... someone can create invoice (with invoice lines) without creating sales order." Admin-only. */
@Injectable({ providedIn: 'root' })
export class InvoiceApiService {
  constructor(private http: HttpClient) {}

  /** Fourier: "on the customer ... view button where we can have button to view the list of invoices, payments etc" — optional `customerId` narrows this to one customer's own invoices. */
  listAll(customerId?: number | null): Observable<Invoice[]> {
    const params: Record<string, string> = {};
    if (customerId != null) params['customerId'] = String(customerId);
    return this.http.get<Invoice[]>(`${API_BASE}/invoices`, { params });
  }

  getById(id: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${API_BASE}/invoices/${id}`);
  }

  /** Fourier: "Add credit note Menu (Void) Where we can printout them" — every voided invoice, store-wide. */
  listCreditNotes(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${API_BASE}/invoices/credit-notes`);
  }

  /** The credit note's own printable PDF — only valid once this invoice is VOID. */
  creditNotePdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/invoices/${id}/credit-note-pdf`, { responseType: 'blob' });
  }

  /** Emits null when no invoice has ever been generated for this Sales Order (the backend returns 204 — Angular's HttpClient surfaces that as a null body) — see the backend InvoiceController.getBySalesOrder's javadoc. */
  getBySalesOrder(salesOrderId: number): Observable<Invoice | null> {
    return this.http.get<Invoice | null>(`${API_BASE}/invoices/by-sales-order/${salesOrderId}`);
  }

  generate(salesOrderId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE}/invoices/from-sales-order/${salesOrderId}`, {});
  }

  /** A brand-new standalone invoice, starting as DRAFT — see InvoiceService.createDraft's backend javadoc. */
  create(payload: InvoicePayload): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE}/invoices`, payload);
  }

  /** Only while DRAFT. */
  update(id: number, payload: InvoicePayload): Observable<Invoice> {
    return this.http.put<Invoice>(`${API_BASE}/invoices/${id}`, payload);
  }

  /** DRAFT -&gt; ISSUED — the moment it becomes a real, numbered, payable document. */
  issue(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE}/invoices/${id}/issue`, {});
  }

  /** Only while DRAFT — a draft was never a real document. */
  deleteDraft(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/invoices/${id}`);
  }

  voidInvoice(id: number, reason: string | null): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE}/invoices/${id}/void`, { reason });
  }

  /** Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit" — the separate cancel path for a standalone invoice that already has a payment on it (voidInvoice above stays refused for that case). */
  cancelPaidInvoice(id: number, payload: CancelPaidInvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${API_BASE}/invoices/${id}/cancel-paid`, payload);
  }

  /** Fourier: "sending ... invoices ... to the customer's email" — emails a PDF of this invoice, at any status. `email` overrides the customer's own saved one; omit to use that. */
  email(id: number, email?: string | null): Observable<void> {
    return this.http.post<void>(`${API_BASE}/invoices/${id}/email`, email ? { email } : {});
  }

  /** Fourier: "add print (PDF with A4)" — the same A4 PDF `email` attaches, for opening in a new tab to print (see download-file.ts's openPdfForPrint). */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/invoices/${id}/pdf`, { responseType: 'blob' });
  }

  /** Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — the whole amount always applies to this one invoice. */
  recordPayment(id: number, payload: RecordInvoicePaymentRequest): Observable<CustomerPayment> {
    return this.http.post<CustomerPayment>(`${API_BASE}/invoices/${id}/payments`, payload);
  }

  /** This invoice's own payment history. */
  paymentHistory(id: number): Observable<CustomerPayment[]> {
    return this.http.get<CustomerPayment[]>(`${API_BASE}/invoices/${id}/payments`);
  }
}
