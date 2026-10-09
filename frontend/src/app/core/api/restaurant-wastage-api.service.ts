import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { RestaurantWastage, RestaurantWastageReason } from '../models';

export interface RestaurantWastagePayload {
  productId: number;
  quantity: number;
  reason: RestaurantWastageReason;
  note?: string | null;
}

/**
 * Fourier: "Restaurants need a dedicated wastage module." Open to any logged-in staff (mirrors
 * RestaurantWastageController's own tier — logging a dropped plate or a burnt batch is
 * kitchen/front-of-house work, not back-office admin, unlike StockAdjustmentApiService which is
 * admin-only).
 */
@Injectable({ providedIn: 'root' })
export class RestaurantWastageApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<RestaurantWastage[]> {
    return this.http.get<RestaurantWastage[]>(`${API_BASE}/restaurant/wastage`);
  }

  create(payload: RestaurantWastagePayload): Observable<RestaurantWastage> {
    return this.http.post<RestaurantWastage>(`${API_BASE}/restaurant/wastage`, payload);
  }

  /** ADMIN-only (Fourier: "should be confirmed by admin once confirmed inventory and account will be impacted"). */
  confirm(id: number): Observable<RestaurantWastage> {
    return this.http.post<RestaurantWastage>(`${API_BASE}/restaurant/wastage/${id}/confirm`, {});
  }

  /** ADMIN-only — discards a PENDING entry with no stock/accounting impact. */
  reject(id: number): Observable<RestaurantWastage> {
    return this.http.post<RestaurantWastage>(`${API_BASE}/restaurant/wastage/${id}/reject`, {});
  }
}
