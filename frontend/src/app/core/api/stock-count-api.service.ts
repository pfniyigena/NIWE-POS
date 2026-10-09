import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { StockCount } from '../models';

export interface StockCountStartPayload {
  warehouseId: number;
  categoryId?: number | null;
  notes?: string | null;
}

/** Physical stock counts — start/count/submit are admin; approve is ADMIN or PERM_APPROVE_STOCK_ADJUSTMENT (server-side). */
@Injectable({ providedIn: 'root' })
export class StockCountApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<StockCount[]> {
    return this.http.get<StockCount[]>(`${API_BASE}/stock-counts`);
  }

  get(id: number): Observable<StockCount> {
    return this.http.get<StockCount>(`${API_BASE}/stock-counts/${id}`);
  }

  start(payload: StockCountStartPayload): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts`, payload);
  }

  saveCounts(id: number, lines: { lineId: number; countedQuantity: number | null }[]): Observable<StockCount> {
    return this.http.put<StockCount>(`${API_BASE}/stock-counts/${id}/counts`, { lines });
  }

  addLine(id: number, productId: number, locationId: number): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts/${id}/lines`, { productId, locationId });
  }

  removeLine(id: number, lineId: number): Observable<StockCount> {
    return this.http.delete<StockCount>(`${API_BASE}/stock-counts/${id}/lines/${lineId}`);
  }

  submit(id: number): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts/${id}/submit`, {});
  }

  reopen(id: number): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts/${id}/reopen`, {});
  }

  approve(id: number): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts/${id}/approve`, {});
  }

  cancel(id: number): Observable<StockCount> {
    return this.http.post<StockCount>(`${API_BASE}/stock-counts/${id}/cancel`, {});
  }
}
