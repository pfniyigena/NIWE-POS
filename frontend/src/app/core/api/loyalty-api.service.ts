import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { getPaged, PageResult } from './paged';

export interface LoyaltySettings {
  enabled: boolean;
  spendAmount: number;
  pointsEarned: number;
  pointValue: number;
  minRedeemPoints: number;
  maxRedeemPercent: number;
}
export interface LoyaltyCustomerRow {
  customerId: number;
  name: string;
  phone: string | null;
  points: number;
  value: number;
  lastActivity: string | null;
}
export interface LoyaltyTxnRow {
  id: number;
  type: 'EARNED' | 'REDEEMED' | 'EARN_REVERSED' | 'REDEEM_REFUNDED' | 'ADJUSTED';
  points: number;
  balanceAfter: number;
  orderId: number | null;
  invoiceNumber: string | null;
  note: string | null;
  at: string;
}
export interface LoyaltyCustomerDetail {
  customerId: number;
  name: string;
  points: number;
  value: number;
  history: LoyaltyTxnRow[];
}
export interface LoyaltyBalance {
  customerId: number;
  enabled: boolean;
  points: number;
  value: number;
  pointValue: number;
  minRedeemPoints: number;
  maxRedeemPercent: number;
}
export interface LoyaltySummary {
  customers: number;
  outstandingPoints: number;
  outstandingValue: number;
}

/** Loyalty points — backed by /api/loyalty. */
@Injectable({ providedIn: 'root' })
export class LoyaltyApiService {
  private base = `${API_BASE}/loyalty`;
  constructor(private http: HttpClient) {}

  settings(): Observable<LoyaltySettings> {
    return this.http.get<LoyaltySettings>(`${this.base}/settings`);
  }
  saveSettings(s: LoyaltySettings): Observable<LoyaltySettings> {
    return this.http.put<LoyaltySettings>(`${this.base}/settings`, s);
  }
  summary(): Observable<LoyaltySummary> {
    return this.http.get<LoyaltySummary>(`${this.base}/summary`);
  }
  customers(q: string, page: number, size: number): Observable<PageResult<LoyaltyCustomerRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return getPaged<LoyaltyCustomerRow>(this.http, `${this.base}/customers`, params);
  }
  detail(customerId: number): Observable<LoyaltyCustomerDetail> {
    return this.http.get<LoyaltyCustomerDetail>(`${this.base}/customers/${customerId}`);
  }
  adjust(customerId: number, points: number, note: string): Observable<LoyaltyCustomerDetail> {
    return this.http.post<LoyaltyCustomerDetail>(`${this.base}/customers/${customerId}/adjust`, { points, note });
  }
  balance(customerId: number): Observable<LoyaltyBalance> {
    return this.http.get<LoyaltyBalance>(`${this.base}/balance/${customerId}`);
  }
}
