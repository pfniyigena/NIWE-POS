import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type PromotionType = 'PERCENT_OFF' | 'AMOUNT_OFF' | 'BUY_X_GET_Y';
export type PromotionScope = 'PRODUCT' | 'CATEGORY' | 'CART';

export interface PromotionRow {
  id: number;
  name: string;
  description: string | null;
  type: PromotionType;
  scope: PromotionScope;
  targetId: number | null;
  targetName: string | null;
  value: number | null;
  buyQuantity: number | null;
  freeQuantity: number | null;
  minQuantity: number;
  minSpend: number | null;
  validFrom: string | null;
  validTo: string | null;
  couponCode: string | null;
  maxUses: number | null;
  maxUsesPerCustomer: number | null;
  customerGroupId: number | null;
  customerGroupName: string | null;
  active: boolean;
  current: boolean;
  uses: number;
  given: number;
  summary: string;
}

export interface PromotionPayload {
  name: string;
  description: string | null;
  type: PromotionType;
  scope: PromotionScope;
  targetId: number | null;
  value: number | null;
  buyQuantity: number | null;
  freeQuantity: number | null;
  minQuantity: number;
  minSpend: number | null;
  validFrom: string | null;
  validTo: string | null;
  couponCode: string | null;
  maxUses: number | null;
  maxUsesPerCustomer: number | null;
  customerGroupId: number | null;
  active: boolean;
}

export interface PromotionUse {
  orderId: number;
  invoiceNumber: string | null;
  customerName: string | null;
  amount: number;
  at: string;
}

/** Promotions and coupons — backed by /api/promotions. (Adding a coupon to a cart is on the orders API.) */
@Injectable({ providedIn: 'root' })
export class PromotionsApiService {
  private base = `${API_BASE}/promotions`;
  constructor(private http: HttpClient) {}

  list(): Observable<PromotionRow[]> {
    return this.http.get<PromotionRow[]>(this.base);
  }
  create(p: PromotionPayload): Observable<PromotionRow> {
    return this.http.post<PromotionRow>(this.base, p);
  }
  update(id: number, p: PromotionPayload): Observable<PromotionRow> {
    return this.http.put<PromotionRow>(`${this.base}/${id}`, p);
  }
  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
  uses(id: number): Observable<PromotionUse[]> {
    return this.http.get<PromotionUse[]>(`${this.base}/${id}/uses`);
  }
}
