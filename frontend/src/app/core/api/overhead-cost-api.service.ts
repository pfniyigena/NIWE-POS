import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { OverheadCost, OverheadCostPayload } from '../models';

/** Manufacturing Module — "Indirect Costs" (Fourier's own term, item 4). Admin-only. */
@Injectable({ providedIn: 'root' })
export class OverheadCostApiService {
  constructor(private http: HttpClient) {}

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
