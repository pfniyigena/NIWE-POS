import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { AuditLogEntry, PageResponse } from '../models';

/**
 * Task: "Implement an audit log of who did what and when" — the admin-only viewer's own API
 * client, mirroring CatalogApiService.listAllProducts's paged-search shape.
 */
@Injectable({ providedIn: 'root' })
export class AuditLogApiService {
  constructor(private http: HttpClient) {}

  /**
   * @param from/@param to plain "yyyy-MM-dd" dates (matching ReportApiService's own convention),
   * or null for no bound on that side.
   */
  search(
    q: string | null,
    action: string | null,
    from: string | null,
    to: string | null,
    page: number,
    size: number
  ): Observable<PageResponse<AuditLogEntry>> {
    const params: string[] = [`page=${page}`, `size=${size}`];
    if (q) params.push(`q=${encodeURIComponent(q)}`);
    if (action) params.push(`action=${encodeURIComponent(action)}`);
    if (from) params.push(`from=${from}`);
    if (to) params.push(`to=${to}`);
    return this.http.get<PageResponse<AuditLogEntry>>(`${API_BASE}/audit-log?${params.join('&')}`);
  }

  /** Every distinct action code ever recorded — powers the viewer's filter dropdown. */
  listActions(): Observable<string[]> {
    return this.http.get<string[]>(`${API_BASE}/audit-log/actions`);
  }
}
