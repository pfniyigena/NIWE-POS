import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import { Routing, RoutingPayload } from '../models';

/** Manufacturing Module — "Settings (Production routings etc)" (Fourier's own naming, item 5). Admin-only. */
@Injectable({ providedIn: 'root' })
export class RoutingApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<Routing>> {
    return pagedList<Routing>(this.http, `${API_BASE}/manufacturing/routings`, f, page, size, extra);
  }

  listAll(): Observable<Routing[]> {
    return this.http.get<Routing[]>(`${API_BASE}/manufacturing/routings`);
  }

  listActive(): Observable<Routing[]> {
    return this.http.get<Routing[]>(`${API_BASE}/manufacturing/routings/active`);
  }

  getById(id: number): Observable<Routing> {
    return this.http.get<Routing>(`${API_BASE}/manufacturing/routings/${id}`);
  }

  create(payload: RoutingPayload): Observable<Routing> {
    return this.http.post<Routing>(`${API_BASE}/manufacturing/routings`, payload);
  }

  update(id: number, payload: RoutingPayload): Observable<Routing> {
    return this.http.put<Routing>(`${API_BASE}/manufacturing/routings/${id}`, payload);
  }
}
