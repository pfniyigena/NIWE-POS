import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ProcessPurchaseReturnPayload, PurchaseReturn } from '../models';

/** Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory Module." Admin-only, mirrors SalesOrderReturnApiService's shape. */
@Injectable({ providedIn: 'root' })
export class PurchaseReturnApiService {
  constructor(private http: HttpClient) {}

  /** Store-wide Purchase Returns list. */
  listAll(): Observable<PurchaseReturn[]> {
    return this.http.get<PurchaseReturn[]>(`${API_BASE}/purchase-returns`);
  }

  getById(id: number): Observable<PurchaseReturn> {
    return this.http.get<PurchaseReturn>(`${API_BASE}/purchase-returns/${id}`);
  }

  /** This one purchase order's own return history — the order detail screen's return history section. */
  listForOrder(purchaseOrderId: number): Observable<PurchaseReturn[]> {
    return this.http.get<PurchaseReturn[]>(`${API_BASE}/purchase-returns/order/${purchaseOrderId}`);
  }

  /** lineId -> total quantity already returned against that line, across every return visit — a line with nothing returned is absent, not present with a zero. */
  returnedQuantitiesForOrder(purchaseOrderId: number): Observable<Record<number, number>> {
    return this.http.get<Record<number, number>>(`${API_BASE}/purchase-returns/order/${purchaseOrderId}/quantities`);
  }

  process(payload: ProcessPurchaseReturnPayload): Observable<PurchaseReturn> {
    return this.http.post<PurchaseReturn>(`${API_BASE}/purchase-returns`, payload);
  }
}
