import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CreditSummary, CustomerPayment, OutstandingOrder, RecordPaymentRequest } from '../models';

/**
 * The store-credit/AR endpoints nested under one customer, plus receipt reprint-by-id. Every
 * write here is ADMIN-only server-side (per the "who settles" decision); `creditSummary` is the
 * one read open to any logged-in user, since checkout/terminal need it for every cashier.
 */
@Injectable({ providedIn: 'root' })
export class CustomerCreditApiService {
  constructor(private http: HttpClient) {}

  /** Task: "Customer balance on POS screen". */
  creditSummary(customerId: number): Observable<CreditSummary> {
    return this.http.get<CreditSummary>(`${API_BASE}/customers/${customerId}/credit-summary`);
  }

  /** Task: "Payment allocation to orders" — oldest-first outstanding orders for the Record Payment screen. ADMIN-only. */
  outstandingOrders(customerId: number): Observable<OutstandingOrder[]> {
    return this.http.get<OutstandingOrder[]>(`${API_BASE}/customers/${customerId}/outstanding-orders`);
  }

  /** Task family: "Customer payment later" / "Partial settlement" / "Multiple payments against one order". ADMIN-only. */
  recordPayment(customerId: number, payload: RecordPaymentRequest): Observable<CustomerPayment> {
    return this.http.post<CustomerPayment>(`${API_BASE}/customers/${customerId}/payments`, payload);
  }

  /** Task: "Payment history". ADMIN-only. */
  paymentHistory(customerId: number): Observable<CustomerPayment[]> {
    return this.http.get<CustomerPayment[]>(`${API_BASE}/customers/${customerId}/payments`);
  }

  /** Store-wide payment history, across every customer — backs the Customer Payments list screen. ADMIN-only. */
  allPayments(): Observable<CustomerPayment[]> {
    return this.http.get<CustomerPayment[]>(`${API_BASE}/customer-payments`);
  }

  /** Task: "Payment receipt" — reprint by the payment's own id. ADMIN-only. */
  getReceipt(paymentId: number): Observable<CustomerPayment> {
    return this.http.get<CustomerPayment>(`${API_BASE}/customer-payments/${paymentId}`);
  }

  /** Fourier: "Change payment receipt printout. Make a PDF with A4" — open in a new tab via openPdfForPrint, same pattern as InvoiceApiService.pdf. ADMIN-only. */
  pdf(paymentId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/customer-payments/${paymentId}/pdf`, { responseType: 'blob' });
  }
}
