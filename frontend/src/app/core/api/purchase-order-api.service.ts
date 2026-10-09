import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PurchaseOrder, PurchaseOrderPayload } from '../models';

export interface ReceivePurchaseOrderPayload {
  lines: { lineId: number; quantityReceived: number; batchNumber?: string | null; expiryDate?: string | null; serialNumbers?: string[] }[];
  /** Optional freight/duty/clearing for this delivery, spread over the delivered products' cost. */
  landedCost?: number | null;
  landedCostPaid?: boolean;
  landedCostNote?: string | null;
}

/** Task: "Inventory depth ... purchase orders ... a stock-receiving flow that increments quantity with a paper trail." Admin-only. */
@Injectable({ providedIn: 'root' })
export class PurchaseOrderApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<PurchaseOrder[]> {
    return this.http.get<PurchaseOrder[]>(`${API_BASE}/purchase-orders`);
  }

  getById(id: number): Observable<PurchaseOrder> {
    return this.http.get<PurchaseOrder>(`${API_BASE}/purchase-orders/${id}`);
  }

  create(payload: PurchaseOrderPayload): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${API_BASE}/purchase-orders`, payload);
  }

  /** Only while the order is still DRAFT — see the backend PurchaseOrderService.updateDraft's javadoc. */
  update(id: number, payload: PurchaseOrderPayload): Observable<PurchaseOrder> {
    return this.http.put<PurchaseOrder>(`${API_BASE}/purchase-orders/${id}`, payload);
  }

  send(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${API_BASE}/purchase-orders/${id}/send`, {});
  }

  cancel(id: number): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${API_BASE}/purchase-orders/${id}/cancel`, {});
  }

  /** Records stock actually arriving (possibly a partial delivery) — increments the matching products' live stock. */
  receive(id: number, payload: ReceivePurchaseOrderPayload): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${API_BASE}/purchase-orders/${id}/receive`, payload);
  }
}
