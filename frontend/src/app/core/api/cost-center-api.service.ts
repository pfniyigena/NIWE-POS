import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CostCenter, CostCenterPayload } from '../models';

/** Admin > Accounting > Cost Centers. Admin-only, same as every other Accounting endpoint. */
@Injectable({ providedIn: 'root' })
export class CostCenterApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<CostCenter[]> {
    return this.http.get<CostCenter[]>(`${API_BASE}/accounting/cost-centers`);
  }

  listActive(): Observable<CostCenter[]> {
    return this.http.get<CostCenter[]>(`${API_BASE}/accounting/cost-centers/active`);
  }

  getById(id: number): Observable<CostCenter> {
    return this.http.get<CostCenter>(`${API_BASE}/accounting/cost-centers/${id}`);
  }

  create(payload: CostCenterPayload): Observable<CostCenter> {
    return this.http.post<CostCenter>(`${API_BASE}/accounting/cost-centers`, payload);
  }

  update(id: number, payload: CostCenterPayload): Observable<CostCenter> {
    return this.http.put<CostCenter>(`${API_BASE}/accounting/cost-centers/${id}`, payload);
  }
}
