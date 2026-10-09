import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Routing, RoutingPayload } from '../models';

/** Manufacturing Module — "Settings (Production routings etc)" (Fourier's own naming, item 5). Admin-only. */
@Injectable({ providedIn: 'root' })
export class RoutingApiService {
  constructor(private http: HttpClient) {}

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
