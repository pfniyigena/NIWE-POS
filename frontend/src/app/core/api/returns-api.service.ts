import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { SalesReturn } from '../models';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

export interface ProcessReturnPayload {
  orderId: number;
  lines: { orderLineId: number; quantity: number }[];
  refundMethod: string;
  reason?: string;
  /** A manager's PIN, when the return needs approval. */
  approverPin?: string;
}

export interface VoidSalePayload {
  orderId: number;
  reason: string;
  refundMethod?: string;
  approverPin?: string;
}

@Injectable({ providedIn: 'root' })
export class ReturnsApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<SalesReturn[]> {
    return this.http.get<SalesReturn[]>(`${API_BASE}/returns`);
  }

  /** Returns list with search, filters and pages (PO-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<SalesReturn>> {
    return pagedList<SalesReturn>(this.http, `${API_BASE}/returns`, f, page, size, extra);
  }

  /** One return's own detail (including its lines) — for the return receipt. Task: "print out the return". */
  getById(id: number): Observable<SalesReturn> {
    return this.http.get<SalesReturn>(`${API_BASE}/returns/${id}`);
  }

  /**
   * orderLineId -> total quantity returned against that line, across every return visit — a line
   * with nothing returned is simply absent, not present with a zero. Consumed by the sale receipt
   * to mark returned items. Task: "on the receipt which was returned, I should mark it as returned".
   */
  returnedQuantitiesForOrder(orderId: number): Observable<Record<number, number>> {
    return this.http.get<Record<number, number>>(`${API_BASE}/returns/order/${orderId}`);
  }

  process(payload: ProcessReturnPayload): Observable<SalesReturn> {
    return this.http.post<SalesReturn>(`${API_BASE}/returns`, payload);
  }

  /** Voids a completed sale: refunded in full, kept on record marked voided. */
  voidSale(payload: VoidSalePayload): Observable<SalesReturn> {
    return this.http.post<SalesReturn>(`${API_BASE}/returns/void-sale`, payload);
  }

  /** Re-attempts a failed EFRIS submission for an already-processed return — the return receipt's "Retry EFRIS submission" button, same shape as OrderApiService.retryEfris. A no-op if this return isn't actually in a failed state. */
  retryEfris(returnId: number): Observable<SalesReturn> {
    return this.http.post<SalesReturn>(`${API_BASE}/returns/${returnId}/efris/retry`, {});
  }
}
