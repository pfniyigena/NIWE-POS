import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { StockAdjustment, StockAdjustmentReason } from '../models';

export interface StockAdjustmentPayload {
  productId: number;
  quantityDelta: number;
  reason: StockAdjustmentReason;
  note?: string | null;
  /** Multi-warehouse phase 2 — optional; omitted/null keeps the legacy behaviour (the product's primary-warehouse stock figure). */
  locationId?: number | null;
  /** Batch products, increases: lot number and expiry date. */
  batchNumber?: string | null;
  expiryDate?: string | null;
  /** Serial products: serial numbers added (increase) or removed (decrease). */
  serialNumbers?: string[];
}

/**
 * Task: "Inventory depth ... stock adjustments/write-offs with a reason code." Fourier: "Build a
 * full permissions system" — list/create are open to any logged-in staff; confirm/reject are
 * gated server-side to ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT (mirrors RestaurantWastageApiService).
 */
@Injectable({ providedIn: 'root' })
export class StockAdjustmentApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<StockAdjustment[]> {
    return this.http.get<StockAdjustment[]>(`${API_BASE}/stock-adjustments`);
  }

  create(payload: StockAdjustmentPayload): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE}/stock-adjustments`, payload);
  }

  /** ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT — moves a PENDING entry to CONFIRMED, applying the real stock change and posting to Accounting. */
  confirm(id: number): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE}/stock-adjustments/${id}/confirm`, {});
  }

  /** ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT — discards a PENDING entry with no stock/accounting impact. */
  reject(id: number): Observable<StockAdjustment> {
    return this.http.post<StockAdjustment>(`${API_BASE}/stock-adjustments/${id}/reject`, {});
  }
}
