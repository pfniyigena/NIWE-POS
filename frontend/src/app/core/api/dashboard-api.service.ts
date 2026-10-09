import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DashboardSummary } from '../models';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  constructor(private http: HttpClient) {}

  /** `branchId` (null/omitted = every branch) narrows today's sales figures to one branch; stock, customers and sessions stay business-wide. */
  summary(branchId: number | null = null): Observable<DashboardSummary> {
    const params: Record<string, string> = branchId != null ? { branchId: String(branchId) } : {};
    return this.http.get<DashboardSummary>(`${API_BASE}/dashboard/summary`, { params });
  }
}
