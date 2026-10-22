import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ExpoOrder, KitchenSummary, RestaurantOrder } from '../models';

/** The kitchen screens beyond the ticket list: expo, summary, bump, serve and send-back. */
@Injectable({ providedIn: 'root' })
export class KitchenApiService {
  constructor(private http: HttpClient) {}

  expo(): Observable<ExpoOrder[]> {
    return this.http.get<ExpoOrder[]>(`${API_BASE}/restaurant/kitchen/expo`);
  }

  summary(): Observable<KitchenSummary> {
    return this.http.get<KitchenSummary>(`${API_BASE}/restaurant/kitchen/summary`);
  }

  /** Everything cooking at a station on one order becomes ready (blank station = every station). */
  bump(orderId: number, station?: string | null): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/kitchen/orders/${orderId}/bump`, { station: station || null });
  }

  serveReady(orderId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/kitchen/orders/${orderId}/serve-ready`, {});
  }

  refire(orderId: number, lineId: number, reason?: string | null): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/kitchen/orders/${orderId}/lines/${lineId}/refire`, { reason: reason || null });
  }
}
