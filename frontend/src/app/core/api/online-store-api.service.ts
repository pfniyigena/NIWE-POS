import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

export interface ShopZone { id: number; name: string; fee: number; minOrder: number }
export interface ShopProduct { id: number; name: string; description: string | null; price: number; category: string | null; imageUrl: string | null; stock: 'IN_STOCK' | 'LOW' | 'SOLD_OUT' }
export interface Shop {
  name: string; intro: string | null; phone: string | null; pickupAddress: string | null; open: boolean; message: string | null;
  pickup: boolean; delivery: boolean; cash: boolean; minOrder: number; freeDeliveryOver: number | null;
  zones: ShopZone[]; products: ShopProduct[];
}
export interface ShopOrderPayload {
  name: string; phone: string; email: string | null; address: string | null; method: 'DELIVERY' | 'PICKUP'; zoneId: number | null;
  notes: string | null; language: string; website: string; lines: { productId: number; quantity: number }[];
}
export interface ShopTrack {
  code: string; status: string; deliveryMethod: string; name: string; zoneName: string | null; subtotal: number; deliveryFee: number; total: number;
  rejectReason: string | null; placedAt: string; lines: { name: string; quantity: number; unitPrice: number; lineTotal: number }[];
  history: { status: string; note: string | null; at: string }[];
}

export interface StoreOrder {
  id: number; code: string; status: string; deliveryMethod: string; paymentMethod: string; customerName: string; customerPhone: string;
  customerEmail: string | null; customerAddress: string | null; zoneName: string | null; deliveryFee: number; notes: string | null;
  subtotal: number; total: number; language: string; salesOrderId: number | null; salesOrderNumber: string | null; invoiceId: number | null;
  invoiceNumber: string | null; rejectReason: string | null; decidedBy: string | null; decidedAt: string | null; createdAt: string;
  lines: { productId: number; name: string; quantity: number; unitPrice: number; lineTotal: number }[];
  events: { status: string; note: string | null; by: string | null; at: string }[];
}
export interface StoreSettings {
  enabled: boolean; paused: boolean; pauseMessage: string | null; shopName: string | null; introText: string | null; contactPhone: string | null;
  pickupAddress: string | null; pickupEnabled: boolean; deliveryEnabled: boolean; codEnabled: boolean; stockBuffer: number; minOrder: number;
  freeDeliveryOver: number | null; maxPendingPerPhone: number; maxPerIpPerHour: number; deliveryProductId: number | null; deliveryProductName: string | null;
}
export interface StoreListing {
  id: number; productId: number; productName: string; sku: string | null; category: string | null; unitPrice: number; listed: boolean;
  webTitle: string | null; webDescription: string | null; onlinePrice: number | null; sortOrder: number; hasImage: boolean; stock: string;
}
export interface StoreZone { id: number; name: string; fee: number; minOrder: number; active: boolean; sortOrder: number }

/** The public shop (no login) and the staff screens of the Online Store module. */
@Injectable({ providedIn: 'root' })
export class OnlineStoreApiService {
  private pub = `${API_BASE}/public/store`;
  private base = `${API_BASE}/online-store`;

  constructor(private http: HttpClient) {}

  // ---- customers
  shop(): Observable<Shop> { return this.http.get<Shop>(`${this.pub}/shop`); }
  place(payload: ShopOrderPayload): Observable<ShopTrack> { return this.http.post<ShopTrack>(`${this.pub}/orders`, payload); }
  track(code: string): Observable<ShopTrack> { return this.http.get<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}`); }
  cancel(code: string): Observable<ShopTrack> { return this.http.post<ShopTrack>(`${this.pub}/orders/${encodeURIComponent(code)}/cancel`, {}); }
  imageUrl(path: string): string { return `${API_BASE.replace(/\/api$/, '')}${path}`; }

  // ---- staff: orders
  page(f: ListFilter, page: number, size: number): Observable<PageResult<StoreOrder>> {
    return pagedList<StoreOrder>(this.http, `${this.base}/orders`, f, page, size, {});
  }
  pendingCount(): Observable<{ pending: number }> { return this.http.get<{ pending: number }>(`${this.base}/orders/pending-count`); }
  accept(id: number, overrideShortage: boolean): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/accept`, { overrideShortage }); }
  reject(id: number, reason: string): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/reject`, { reason }); }
  pack(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/pack`, {}); }
  outForDelivery(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/out-for-delivery`, {}); }
  readyForPickup(id: number): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/ready-for-pickup`, {}); }
  complete(id: number, body: { locationId?: number | null; collectPayment?: boolean; method?: string }): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/complete`, body); }
  cancelOrder(id: number, note: string): Observable<StoreOrder> { return this.http.post<StoreOrder>(`${this.base}/orders/${id}/cancel`, { note }); }

  // ---- staff: setup
  settings(): Observable<StoreSettings> { return this.http.get<StoreSettings>(`${this.base}/settings`); }
  saveSettings(s: StoreSettings): Observable<StoreSettings> { return this.http.put<StoreSettings>(`${this.base}/settings`, s); }
  listings(): Observable<StoreListing[]> { return this.http.get<StoreListing[]>(`${this.base}/listings`); }
  saveListing(body: { productId: number; listed?: boolean; webTitle?: string | null; webDescription?: string | null; onlinePrice?: number | null; sortOrder?: number }): Observable<StoreListing> {
    return this.http.post<StoreListing>(`${this.base}/listings`, body);
  }
  removeListing(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/listings/${id}`); }
  zones(): Observable<StoreZone[]> { return this.http.get<StoreZone[]>(`${this.base}/zones`); }
  saveZone(id: number | null, body: Partial<StoreZone>): Observable<StoreZone> {
    return id == null ? this.http.post<StoreZone>(`${this.base}/zones`, body) : this.http.put<StoreZone>(`${this.base}/zones/${id}`, body);
  }
  deleteZone(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/zones/${id}`); }
}
