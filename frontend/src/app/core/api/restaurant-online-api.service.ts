import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { DeliveryZone, OnlineOrder, OnlineTrack, PublicMenu, PublicOrderPayload, TableQr } from '../models';

/** The QR menu and ordering page (no login), the staff queue of online requests, delivery areas and table QR codes. */
@Injectable({ providedIn: 'root' })
export class RestaurantOnlineApiService {
  private pub = `${API_BASE}/public/restaurant`;
  private base = `${API_BASE}/restaurant`;

  constructor(private http: HttpClient) {}

  // ---- guests
  menu(tableToken?: string | null): Observable<PublicMenu> {
    let p = new HttpParams();
    if (tableToken) p = p.set('table', tableToken);
    return this.http.get<PublicMenu>(`${this.pub}/menu`, { params: p });
  }

  place(payload: PublicOrderPayload): Observable<OnlineTrack> {
    return this.http.post<OnlineTrack>(`${this.pub}/orders`, payload);
  }

  track(code: string): Observable<OnlineTrack> {
    return this.http.get<OnlineTrack>(`${this.pub}/orders/${encodeURIComponent(code)}`);
  }

  cancel(code: string): Observable<OnlineTrack> {
    return this.http.post<OnlineTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/cancel`, {});
  }

  // ---- staff
  page(f: ListFilter, page: number, size: number, mode: string): Observable<PageResult<OnlineOrder>> {
    return pagedList<OnlineOrder>(this.http, `${this.base}/online-orders`, f, page, size, { mode });
  }

  pendingCount(): Observable<{ pending: number }> {
    return this.http.get<{ pending: number }>(`${this.base}/online-orders/pending-count`);
  }

  accept(id: number, body: { etaMinutes?: number | null; serverId?: number | null; skipUnavailable?: boolean }): Observable<OnlineOrder> {
    return this.http.post<OnlineOrder>(`${this.base}/online-orders/${id}/accept`, body);
  }

  reject(id: number, reason: string): Observable<OnlineOrder> {
    return this.http.post<OnlineOrder>(`${this.base}/online-orders/${id}/reject`, { reason });
  }

  // ---- setup
  zones(): Observable<DeliveryZone[]> {
    return this.http.get<DeliveryZone[]>(`${this.base}/delivery-zones`);
  }

  saveZone(id: number | null, body: Partial<DeliveryZone>): Observable<DeliveryZone> {
    return id == null ? this.http.post<DeliveryZone>(`${this.base}/delivery-zones`, body) : this.http.put<DeliveryZone>(`${this.base}/delivery-zones/${id}`, body);
  }

  deleteZone(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/delivery-zones/${id}`);
  }

  qr(): Observable<TableQr[]> {
    return this.http.get<TableQr[]>(`${this.base}/qr`);
  }

  regenerateQr(tableId: number): Observable<TableQr> {
    return this.http.post<TableQr>(`${this.base}/qr/${tableId}/regenerate`, {});
  }
}
