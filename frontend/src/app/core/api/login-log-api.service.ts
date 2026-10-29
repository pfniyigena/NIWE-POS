import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PageResponse } from '../models';

/** v1.14.0: one sign-in attempt, as the sign-in log shows it. */
export interface LoginEvent {
  id: number;
  at: string;
  username: string | null;
  success: boolean;
  reason: string | null;
  ip: string | null;
  deviceId: string | null;
  userAgent: string | null;
}

export interface LoginLogSummary {
  okLast24h: number;
  failedLast24h: number;
  failingAddressesLast24h: number;
}

/** v1.14.0: Admin > Sign-in log. */
@Injectable({ providedIn: 'root' })
export class LoginLogApiService {
  constructor(private http: HttpClient) {}

  search(q: string | null, outcome: string, from: string | null, to: string | null, page: number, size: number): Observable<PageResponse<LoginEvent>> {
    const params: string[] = [`page=${page}`, `size=${size}`];
    if (q) params.push(`q=${encodeURIComponent(q)}`);
    if (outcome) params.push(`outcome=${encodeURIComponent(outcome)}`);
    if (from) params.push(`from=${from}`);
    if (to) params.push(`to=${to}`);
    return this.http.get<PageResponse<LoginEvent>>(`${API_BASE}/admin/login-log?${params.join('&')}`);
  }

  summary(): Observable<LoginLogSummary> {
    return this.http.get<LoginLogSummary>(`${API_BASE}/admin/login-log/summary`);
  }
}
