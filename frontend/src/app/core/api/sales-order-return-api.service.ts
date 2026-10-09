import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ProcessSalesOrderReturnPayload, SalesOrderReturn } from '../models';

/** Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module." Admin-only, mirrors ReturnsApiService's shape on the POS side. */
@Injectable({ providedIn: 'root' })
export class SalesOrderReturnApiService {
  constructor(private http: HttpClient) {}

  /** Store-wide Sale Order Returns list. */
  listAll(): Observable<SalesOrderReturn[]> {
    return this.http.get<SalesOrderReturn[]>(`${API_BASE}/sales-order-returns`);
  }

  getById(id: number): Observable<SalesOrderReturn> {
    return this.http.get<SalesOrderReturn>(`${API_BASE}/sales-order-returns/${id}`);
  }

  /** This one sales order's own return history — the order detail screen's return history section. */
  listForOrder(salesOrderId: number): Observable<SalesOrderReturn[]> {
    return this.http.get<SalesOrderReturn[]>(`${API_BASE}/sales-order-returns/order/${salesOrderId}`);
  }

  /** lineId -> total quantity already returned against that line, across every return visit — a line with nothing returned is absent, not present with a zero. */
  returnedQuantitiesForOrder(salesOrderId: number): Observable<Record<number, number>> {
    return this.http.get<Record<number, number>>(`${API_BASE}/sales-order-returns/order/${salesOrderId}/quantities`);
  }

  process(payload: ProcessSalesOrderReturnPayload): Observable<SalesOrderReturn> {
    return this.http.post<SalesOrderReturn>(`${API_BASE}/sales-order-returns`, payload);
  }
}
