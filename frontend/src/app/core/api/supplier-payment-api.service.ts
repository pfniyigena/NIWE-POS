import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { OutstandingPurchaseOrder, RecordSupplierPaymentRequest, SupplierPayment } from '../models';

/**
 * "Add Supplier payment" — the accounts-payable mirror of CustomerCreditApiService. Every write
 * here is ADMIN-only server-side, same "who settles" convention as customer payments;
 * `outstandingBalance` is the one read open to any logged-in user.
 */
@Injectable({ providedIn: 'root' })
export class SupplierPaymentApiService {
  constructor(private http: HttpClient) {}

  outstandingBalance(supplierId: number): Observable<number> {
    return this.http.get<number>(`${API_BASE}/suppliers/${supplierId}/outstanding-balance`);
  }

  /** Oldest-first outstanding purchase orders for the Record Supplier Payment screen. ADMIN-only. */
  outstandingOrders(supplierId: number): Observable<OutstandingPurchaseOrder[]> {
    return this.http.get<OutstandingPurchaseOrder[]>(`${API_BASE}/suppliers/${supplierId}/outstanding-orders`);
  }

  recordPayment(supplierId: number, payload: RecordSupplierPaymentRequest): Observable<SupplierPayment> {
    return this.http.post<SupplierPayment>(`${API_BASE}/suppliers/${supplierId}/payments`, payload);
  }

  paymentHistory(supplierId: number): Observable<SupplierPayment[]> {
    return this.http.get<SupplierPayment[]>(`${API_BASE}/suppliers/${supplierId}/payments`);
  }

  /** Store-wide payment history, across every supplier — backs the Supplier Payments list screen. */
  allPayments(): Observable<SupplierPayment[]> {
    return this.http.get<SupplierPayment[]>(`${API_BASE}/supplier-payments`);
  }

  /** Fourier: "Add view details on supplier payments list" — one payment by its own id, regardless of supplier. */
  getReceipt(paymentId: number): Observable<SupplierPayment> {
    return this.http.get<SupplierPayment>(`${API_BASE}/supplier-payments/${paymentId}`);
  }

  pdf(paymentId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/supplier-payments/${paymentId}/pdf`, { responseType: 'blob' });
  }
}
