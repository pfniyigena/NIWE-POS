import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { getPaged, PageResult } from './paged';
import { ImportResult } from './data-import-export-api.service';

export interface PriceListRow {
  id: number;
  name: string;
  description: string | null;
  adjustPercent: number;
  validFrom: string | null;
  validTo: string | null;
  active: boolean;
  current: boolean;
  entries: number;
  groups: number;
}
export interface PriceListPayload {
  name: string;
  description: string | null;
  adjustPercent: number;
  validFrom: string | null;
  validTo: string | null;
  active: boolean;
}
export interface PriceEntry {
  id: number;
  productId: number;
  productName: string | null;
  sku: string | null;
  basePrice: number | null;
  minQuantity: number;
  price: number;
}
export interface GroupRow {
  id: number;
  name: string;
  description: string | null;
  priceListId: number | null;
  priceListName: string | null;
  active: boolean;
  customers: number;
}
export interface GroupPayload {
  name: string;
  description: string | null;
  priceListId: number | null;
  active: boolean;
}
export interface ResolvedPrice {
  listPrice: number;
  unitPrice: number;
  note: string | null;
}

/** Price lists, customer groups, and "what would this customer pay" — backed by /api/pricing. */
@Injectable({ providedIn: 'root' })
export class PricingApiService {
  private base = `${API_BASE}/pricing`;
  constructor(private http: HttpClient) {}

  lists(): Observable<PriceListRow[]> {
    return this.http.get<PriceListRow[]>(`${this.base}/price-lists`);
  }
  list(id: number): Observable<PriceListRow> {
    return this.http.get<PriceListRow>(`${this.base}/price-lists/${id}`);
  }
  createList(p: PriceListPayload): Observable<PriceListRow> {
    return this.http.post<PriceListRow>(`${this.base}/price-lists`, p);
  }
  updateList(id: number, p: PriceListPayload): Observable<PriceListRow> {
    return this.http.put<PriceListRow>(`${this.base}/price-lists/${id}`, p);
  }
  deleteList(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/price-lists/${id}`);
  }

  entries(listId: number, q: string, page: number, size: number): Observable<PageResult<PriceEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return getPaged<PriceEntry>(this.http, `${this.base}/price-lists/${listId}/entries`, params);
  }
  saveEntry(listId: number, productId: number, minQuantity: number, price: number): Observable<PriceEntry> {
    return this.http.post<PriceEntry>(`${this.base}/price-lists/${listId}/entries`, { productId, minQuantity, price });
  }
  deleteEntry(listId: number, entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/price-lists/${listId}/entries/${entryId}`);
  }
  importEntries(listId: number, file: File, dryRun: boolean): Observable<ImportResult> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/price-lists/${listId}/import`, fd, { params: { dryRun: String(dryRun) } });
  }
  template(): Observable<Blob> {
    return this.http.get(`${this.base}/import-template`, { responseType: 'blob' });
  }
  exportList(listId: number): Observable<Blob> {
    return this.http.get(`${this.base}/price-lists/${listId}/export`, { responseType: 'blob' });
  }

  groups(): Observable<GroupRow[]> {
    return this.http.get<GroupRow[]>(`${this.base}/groups`);
  }
  createGroup(p: GroupPayload): Observable<GroupRow> {
    return this.http.post<GroupRow>(`${this.base}/groups`, p);
  }
  updateGroup(id: number, p: GroupPayload): Observable<GroupRow> {
    return this.http.put<GroupRow>(`${this.base}/groups/${id}`, p);
  }
  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/groups/${id}`);
  }
  assign(customerIds: number[], groupId: number | null): Observable<{ changed: number }> {
    return this.http.post<{ changed: number }>(`${this.base}/assign`, { customerIds, groupId });
  }

  resolve(customerId: number | null, productId: number, quantity: number): Observable<ResolvedPrice> {
    let params = new HttpParams().set('productId', productId).set('quantity', quantity);
    if (customerId != null) params = params.set('customerId', customerId);
    return this.http.get<ResolvedPrice>(`${this.base}/resolve`, { params });
  }
}
