import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import { Bom, BomPayload } from '../models';

/** Manufacturing Module — "Bill of Materials" (Fourier's own list, item 1). Admin-only. */
@Injectable({ providedIn: 'root' })
export class BomApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<Bom>> {
    return pagedList<Bom>(this.http, `${API_BASE}/manufacturing/boms`, f, page, size, extra);
  }

  listAll(): Observable<Bom[]> {
    return this.http.get<Bom[]>(`${API_BASE}/manufacturing/boms`);
  }

  listForProduct(productId: number): Observable<Bom[]> {
    return this.http.get<Bom[]>(`${API_BASE}/manufacturing/boms/product/${productId}`);
  }

  getById(id: number): Observable<Bom> {
    return this.http.get<Bom>(`${API_BASE}/manufacturing/boms/${id}`);
  }

  create(payload: BomPayload): Observable<Bom> {
    return this.http.post<Bom>(`${API_BASE}/manufacturing/boms`, payload);
  }

  /** MF-6: a new inactive version with the same components, by-products and routing. */
  copy(id: number): Observable<Bom> {
    return this.http.post<Bom>(`${API_BASE}/manufacturing/boms/${id}/copy`, {});
  }

  /** MF-6: make this version the active one for its product (the product's other versions are switched off). */
  activate(id: number): Observable<Bom> {
    return this.http.post<Bom>(`${API_BASE}/manufacturing/boms/${id}/activate`, {});
  }

  update(id: number, payload: BomPayload): Observable<Bom> {
    return this.http.put<Bom>(`${API_BASE}/manufacturing/boms/${id}`, payload);
  }
}
