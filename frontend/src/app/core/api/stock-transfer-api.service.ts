import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { StockTransfer } from '../models';

export interface StockTransferPayload {
  productId: number;
  fromLocationId: number;
  toLocationId: number;
  quantity: number;
  note?: string | null;
}

/**
 * Admin > Inventory > Stock Transfers — Fourier: "User can make stock transfer to any location."
 * List/create are ADMIN-only; confirm/reject are gated server-side to ADMIN or
 * PERM_APPROVE_STOCK_TRANSFER (mirrors StockAdjustmentApiService).
 */
@Injectable({ providedIn: 'root' })
export class StockTransferApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<StockTransfer[]> {
    return this.http.get<StockTransfer[]>(`${API_BASE}/stock-transfers`);
  }

  create(payload: StockTransferPayload): Observable<StockTransfer> {
    return this.http.post<StockTransfer>(`${API_BASE}/stock-transfers`, payload);
  }

  /** ADMIN or PERM_APPROVE_STOCK_TRANSFER — moves a PENDING entry to CONFIRMED, actually moving the stock. */
  confirm(id: number): Observable<StockTransfer> {
    return this.http.post<StockTransfer>(`${API_BASE}/stock-transfers/${id}/confirm`, {});
  }

  /** Receiving branch confirms an in-transit inter-branch transfer arrived; fewer than sent books the shortfall as a loss. */
  receive(id: number, receivedQuantity: number, note?: string | null): Observable<StockTransfer> {
    return this.http.post<StockTransfer>(`${API_BASE}/stock-transfers/${id}/receive`, { receivedQuantity, note: note ?? null });
  }

  /** ADMIN or PERM_APPROVE_STOCK_TRANSFER — discards a PENDING entry with no stock impact (or, for an in-transit one, returns the stock to the sender). */
  reject(id: number): Observable<StockTransfer> {
    return this.http.post<StockTransfer>(`${API_BASE}/stock-transfers/${id}/reject`, {});
  }
}
