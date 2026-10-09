import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { AppSummary } from '../models';

/** Task: "plugin (or apps) to be installed and configured" — Admin > Settings > Apps. Admin-only, mirrored on the backend's AppsController. */
@Injectable({ providedIn: 'root' })
export class AppsApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<AppSummary[]> {
    return this.http.get<AppSummary[]>(`${API_BASE}/apps`);
  }

  install(key: string): Observable<AppSummary> {
    return this.http.post<AppSummary>(`${API_BASE}/apps/${key}/install`, {});
  }

  uninstall(key: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/apps/${key}/uninstall`, {});
  }

  enable(key: string): Observable<AppSummary> {
    return this.http.post<AppSummary>(`${API_BASE}/apps/${key}/enable`, {});
  }

  disable(key: string): Observable<AppSummary> {
    return this.http.post<AppSummary>(`${API_BASE}/apps/${key}/disable`, {});
  }
}
