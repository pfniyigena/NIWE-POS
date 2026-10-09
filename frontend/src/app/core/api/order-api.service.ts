import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Order, PaymentMethod } from '../models';

export interface CheckoutPaymentEntry {
  method: PaymentMethod;
  amount: number;
}

/**
 * Task: "local queuing of sales that sync once connectivity returns" — the whole payload
 * `OfflineSyncService` sends for one queued sale, replayed server-side in one shot by
 * `POST /api/orders/offline-sync`. Mirrors the backend's `OfflineSaleRequest`.
 */
export interface OfflineSaleRequest {
  clientTempId: string;
  cashierSessionId: number;
  customerId: number | null;
  /** ISO instant. */
  queuedAt: string;
  lines: { productId: number; quantity: number; discountPercent: number }[];
  payments: CheckoutPaymentEntry[];
  /** ISO date (yyyy-MM-dd), or null. */
  dueDate: string | null;
  clientEstimatedTotal: number;
}

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  constructor(private http: HttpClient) {}

  /** Scoped to one cashier's own working session, not the whole shift — see CashierSession in models.ts. */
  listOpenOrders(cashierSessionId: number): Observable<Order[]> {
    return this.http.get<Order[]>(`${API_BASE}/cashier-sessions/${cashierSessionId}/orders`);
  }

  startNewOrder(cashierSessionId: number): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/cashier-sessions/${cashierSessionId}/orders`, {});
  }

  /** A cashier's own order history on one cashier session — every status, not just open tabs. Used by the "Sessions" screen's per-session "Orders" view. */
  listAllForCashierSession(cashierSessionId: number): Observable<Order[]> {
    return this.http.get<Order[]>(`${API_BASE}/cashier-sessions/${cashierSessionId}/orders/all`);
  }

  getOrder(orderId: number): Observable<Order> {
    return this.http.get<Order>(`${API_BASE}/orders/${orderId}`);
  }

  getByInvoiceNumber(invoiceNumber: string): Observable<Order> {
    return this.http.get<Order>(`${API_BASE}/orders/by-invoice/${encodeURIComponent(invoiceNumber)}`);
  }

  addLine(orderId: number, productId: number, quantity = 1): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/lines`, { productId, quantity });
  }

  addLineByBarcode(orderId: number, barcode: string, quantity = 1): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/lines/by-barcode`, { barcode, quantity });
  }

  updateLine(orderId: number, lineId: number, changes: { quantity?: number; discountPercent?: number; serialNumbers?: string[] }): Observable<Order> {
    return this.http.patch<Order>(`${API_BASE}/orders/${orderId}/lines/${lineId}`, changes);
  }

  removeLine(orderId: number, lineId: number): Observable<Order> {
    return this.http.delete<Order>(`${API_BASE}/orders/${orderId}/lines/${lineId}`);
  }

  setCustomer(orderId: number, customerId: number | null): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/customer`, { customerId });
  }

  park(orderId: number, note: string): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/park`, { note });
  }

  /** Cancels an open cart without completing it (never touches stock — checkout is the only place that does). */
  discard(orderId: number): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/discard`, {});
  }

  /** `dueDate` (ISO yyyy-MM-dd) is only meaningful when at least one payment entry is a credit-flagged method — see backend CheckoutRequest.dueDate's javadoc. */
  checkout(orderId: number, payments: CheckoutPaymentEntry[], dueDate?: string | null): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/checkout`, { payments, dueDate: dueDate ?? null });
  }

  /**
   * Recent completed sales for the Receipts screen (lookup + reprint). Pass a sessionId to scope
   * the list to one till session (used when Receipts is opened from inside the terminal); omit
   * it for the app-wide standalone Receipts page.
   */
  listCompleted(sessionId?: number): Observable<Order[]> {
    const query = sessionId != null ? `?sessionId=${sessionId}` : '';
    return this.http.get<Order[]>(`${API_BASE}/orders/completed${query}`);
  }

  /** Task: "local queuing of sales that sync once connectivity returns" — replays one whole sale rung offline. Idempotent by `clientTempId`; see OfflineSyncService. */
  syncOffline(request: OfflineSaleRequest): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/offline-sync`, request);
  }

  /** Re-attempts a failed EFRIS submission for an already-completed sale — the receipt screen's "Retry EFRIS submission" button. A no-op if this sale isn't actually in a failed state. */
  retryEfris(orderId: number): Observable<Order> {
    return this.http.post<Order>(`${API_BASE}/orders/${orderId}/efris/retry`, {});
  }

  /** Fourier: "sending ... receipts ... to the customer's email" — emails a PDF receipt for this sale. `email` overrides the customer's own saved one; omit to use that. */
  emailReceipt(orderId: number, email?: string | null): Observable<void> {
    return this.http.post<void>(`${API_BASE}/orders/${orderId}/email-receipt`, email ? { email } : {});
  }
}
