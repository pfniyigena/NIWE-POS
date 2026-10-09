import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Bill, RecordBillPaymentRequest, SupplierPayment, VoidBillRequest } from '../models';

/**
 * Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
 * equivalent with Invoices and Credit note)." The accounts-payable mirror of InvoiceApiService.
 * Admin-only.
 */
@Injectable({ providedIn: 'root' })
export class BillApiService {
  constructor(private http: HttpClient) {}

  /** Optional `supplierId` narrows this to one supplier's own bills. */
  listAll(supplierId?: number | null): Observable<Bill[]> {
    const params: Record<string, string> = {};
    if (supplierId != null) params['supplierId'] = String(supplierId);
    return this.http.get<Bill[]>(`${API_BASE}/bills`, { params });
  }

  getById(id: number): Observable<Bill> {
    return this.http.get<Bill>(`${API_BASE}/bills/${id}`);
  }

  /** Fourier: "Refunds equivalent with Credit note" — every voided bill, store-wide. */
  listRefunds(): Observable<Bill[]> {
    return this.http.get<Bill[]>(`${API_BASE}/bills/refunds`);
  }

  /** The refund's own printable PDF — only valid once this bill is VOID. */
  refundPdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/bills/${id}/refund-pdf`, { responseType: 'blob' });
  }

  /** Emits null when no bill has ever been generated for this Purchase Order (the backend returns 204). */
  getByPurchaseOrder(purchaseOrderId: number): Observable<Bill | null> {
    return this.http.get<Bill | null>(`${API_BASE}/bills/by-purchase-order/${purchaseOrderId}`);
  }

  generate(purchaseOrderId: number): Observable<Bill> {
    return this.http.post<Bill>(`${API_BASE}/bills/from-purchase-order/${purchaseOrderId}`, {});
  }

  /** DRAFT -&gt; ISSUED — the moment it becomes a real, numbered, payable document. */
  issue(id: number): Observable<Bill> {
    return this.http.post<Bill>(`${API_BASE}/bills/${id}/issue`, {});
  }

  /** Only while DRAFT — a draft was never a real document. */
  deleteDraft(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/bills/${id}`);
  }

  voidBill(id: number, payload: VoidBillRequest): Observable<Bill> {
    return this.http.post<Bill>(`${API_BASE}/bills/${id}/void`, payload);
  }

  /** The same A4 PDF for opening in a new tab to print. */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/bills/${id}/pdf`, { responseType: 'blob' });
  }

  /** The whole amount always applies to this one bill. */
  recordPayment(id: number, payload: RecordBillPaymentRequest): Observable<SupplierPayment> {
    return this.http.post<SupplierPayment>(`${API_BASE}/bills/${id}/payments`, payload);
  }

  /** This bill's own payment history. */
  paymentHistory(id: number): Observable<SupplierPayment[]> {
    return this.http.get<SupplierPayment[]>(`${API_BASE}/bills/${id}/payments`);
  }
}
