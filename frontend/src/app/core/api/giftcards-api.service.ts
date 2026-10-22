import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { getPaged, PageResult } from './paged';

export interface GiftCardRow {
  id: number;
  code: string;
  initialAmount: number;
  balance: number;
  status: 'ACTIVE' | 'BLOCKED';
  customerId: number | null;
  customerName: string | null;
  recipientName: string | null;
  expiresOn: string | null;
  expired: boolean;
  usable: boolean;
  note: string | null;
  createdAt: string;
}
export interface GiftCardTxnRow {
  id: number;
  type: 'SOLD' | 'TOPUP' | 'REDEEM' | 'REFUND' | 'RELEASED';
  amount: number;
  balanceAfter: number;
  paymentMethod: string | null;
  orderId: number | null;
  invoiceNumber: string | null;
  note: string | null;
  at: string;
}
export interface GiftCardDetail {
  card: GiftCardRow;
  history: GiftCardTxnRow[];
}
export interface GiftCardSummary {
  liability: number;
  cards: number;
  withBalance: number;
  blocked: number;
  expiredWithBalance: number;
  expiredBalance: number;
}
export interface GiftCardSell {
  amount: number;
  paymentMethod: string;
  customerId?: number | null;
  recipientName?: string | null;
  expiresOn?: string | null;
  code?: string | null;
  note?: string | null;
  /** The till's open cashier session; leave out for a back-office sale. */
  cashierSessionId?: number | null;
}
export interface GiftCardTopUp {
  amount: number;
  paymentMethod: string;
  cashierSessionId?: number | null;
  note?: string | null;
}

/** Gift cards — backed by /api/giftcards. */
@Injectable({ providedIn: 'root' })
export class GiftCardsApiService {
  private base = `${API_BASE}/giftcards`;
  constructor(private http: HttpClient) {}

  list(q: string, filter: string, page: number, size: number): Observable<PageResult<GiftCardRow>> {
    let params = new HttpParams().set('page', page).set('size', size).set('filter', filter);
    if (q) params = params.set('q', q);
    return getPaged<GiftCardRow>(this.http, this.base, params);
  }
  summary(): Observable<GiftCardSummary> {
    return this.http.get<GiftCardSummary>(`${this.base}/summary`);
  }
  detail(id: number): Observable<GiftCardDetail> {
    return this.http.get<GiftCardDetail>(`${this.base}/${id}`);
  }
  lookup(code: string): Observable<GiftCardRow> {
    return this.http.get<GiftCardRow>(`${this.base}/lookup`, { params: { code } });
  }
  sell(r: GiftCardSell): Observable<GiftCardRow> {
    return this.http.post<GiftCardRow>(this.base, r);
  }
  topUp(id: number, r: GiftCardTopUp): Observable<GiftCardRow> {
    return this.http.post<GiftCardRow>(`${this.base}/${id}/topup`, r);
  }
  block(id: number): Observable<GiftCardRow> {
    return this.http.post<GiftCardRow>(`${this.base}/${id}/block`, {});
  }
  unblock(id: number): Observable<GiftCardRow> {
    return this.http.post<GiftCardRow>(`${this.base}/${id}/unblock`, {});
  }
  setExpiry(id: number, expiresOn: string | null): Observable<GiftCardRow> {
    return this.http.put<GiftCardRow>(`${this.base}/${id}/expiry`, { expiresOn });
  }
  releaseExpired(): Observable<{ cards: number; amount: number }> {
    return this.http.post<{ cards: number; amount: number }>(`${this.base}/release-expired`, {});
  }
}
