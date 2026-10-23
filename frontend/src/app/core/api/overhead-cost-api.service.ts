import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import { OverheadCost, OverheadCostPayload } from '../models';

/** Manufacturing Module — "Indirect Costs" (Fourier's own term, item 4). Admin-only. */
@Injectable({ providedIn: 'root' })
export class OverheadCostApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<OverheadCost>> {
    return pagedList<OverheadCost>(this.http, `${API_BASE}/manufacturing/overhead-costs`, f, page, size, extra);
  }

  listAll(): Observable<OverheadCost[]> {
    return this.http.get<OverheadCost[]>(`${API_BASE}/manufacturing/overhead-costs`);
  }

  listActive(): Observable<OverheadCost[]> {
    return this.http.get<OverheadCost[]>(`${API_BASE}/manufacturing/overhead-costs/active`);
  }

  getById(id: number): Observable<OverheadCost> {
    return this.http.get<OverheadCost>(`${API_BASE}/manufacturing/overhead-costs/${id}`);
  }

  create(payload: OverheadCostPayload): Observable<OverheadCost> {
    return this.http.post<OverheadCost>(`${API_BASE}/manufacturing/overhead-costs`, payload);
  }

  update(id: number, payload: OverheadCostPayload): Observable<OverheadCost> {
    return this.http.put<OverheadCost>(`${API_BASE}/manufacturing/overhead-costs/${id}`, payload);
  }
}
