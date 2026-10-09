import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { RegisterSession, RegisterSummary, SessionReport } from '../models';

@Injectable({ providedIn: 'root' })
export class RegisterApiService {
  constructor(private http: HttpClient) {}

  listRegisters(): Observable<RegisterSummary[]> {
    return this.http.get<RegisterSummary[]>(`${API_BASE}/registers`);
  }

  /** ADMIN-only on the backend. `warehouseId` null/omitted means no warehouse assigned — see RegisterSummary's own note. `defaultLocationId`, when set, must belong to `warehouseId`. */
  createRegister(payload: { name: string; location?: string; warehouseId?: number | null; defaultLocationId?: number | null }): Observable<RegisterSummary> {
    return this.http.post<RegisterSummary>(`${API_BASE}/registers`, payload);
  }

  /** ADMIN-only on the backend. */
  updateRegister(id: number, payload: { name: string; location?: string; warehouseId?: number | null; defaultLocationId?: number | null }): Observable<RegisterSummary> {
    return this.http.put<RegisterSummary>(`${API_BASE}/registers/${id}`, payload);
  }

  /**
   * ADMIN-only on the backend. `cashierId` is who this shift's first cashier session is assigned
   * to — the admin opening it usually isn't the one who'll ring sales on it. Anyone else who
   * wants to work this same shift joins afterward with their PIN (see CashierSessionApiService).
   */
  openSession(registerId: number, openingFloat: number, cashierId: number): Observable<RegisterSession> {
    return this.http.post<RegisterSession>(`${API_BASE}/registers/${registerId}/sessions`, { openingFloat, cashierId });
  }

  listSessions(): Observable<RegisterSession[]> {
    return this.http.get<RegisterSession[]>(`${API_BASE}/sessions`);
  }

  getSession(sessionId: number): Observable<RegisterSession> {
    return this.http.get<RegisterSession>(`${API_BASE}/sessions/${sessionId}`);
  }

  /** ADMIN-only on the backend. */
  closeSession(sessionId: number, countedCash: number): Observable<RegisterSession> {
    return this.http.post<RegisterSession>(`${API_BASE}/sessions/${sessionId}/close`, { countedCash });
  }

  getSessionReport(sessionId: number): Observable<SessionReport> {
    return this.http.get<SessionReport>(`${API_BASE}/sessions/${sessionId}/report`);
  }
}
