import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DeviceStatus, CashMovement, CashSummary, ShiftReport, Denomination, PosException, PosSettings, RegisterSession, RegisterSummary, SessionReport } from '../models';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

/** PO-8 adds the till rules: on/off, float limits and who may work it. */
export interface RegisterPayload {
  name: string;
  location?: string;
  warehouseId?: number | null;
  defaultLocationId?: number | null;
  active?: boolean;
  defaultFloat?: number | null;
  maxFloat?: number | null;
  allowedCashierIds?: number[];
}

@Injectable({ providedIn: 'root' })
export class RegisterApiService {
  constructor(private http: HttpClient) {}

  listRegisters(): Observable<RegisterSummary[]> {
    return this.http.get<RegisterSummary[]>(`${API_BASE}/registers`);
  }

  /** ADMIN-only on the backend. `warehouseId` null/omitted means no warehouse assigned — see RegisterSummary's own note. `defaultLocationId`, when set, must belong to `warehouseId`. */
  createRegister(payload: RegisterPayload): Observable<RegisterSummary> {
    return this.http.post<RegisterSummary>(`${API_BASE}/registers`, payload);
  }

  /** ADMIN-only on the backend. */
  updateRegister(id: number, payload: RegisterPayload): Observable<RegisterSummary> {
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

  /** v1.13.0: is this till tied to a device, and is it this one? Any signed-in user may ask. */
  deviceStatus(registerId: number): Observable<DeviceStatus> {
    return this.http.get<DeviceStatus>(`${API_BASE}/registers/${registerId}/device-status`);
  }

  /** Admin: tie the till to the device making this call. `checkIp` false = do not require the current address. */
  bindDevice(registerId: number, deviceName: string, checkIp: boolean): Observable<RegisterSummary> {
    return this.http.post<RegisterSummary>(`${API_BASE}/registers/${registerId}/device-binding`, { deviceName, checkIp });
  }

  /** Admin: free the till so it can be tied to another device (works from any device). */
  unbindDevice(registerId: number): Observable<RegisterSummary> {
    return this.http.delete<RegisterSummary>(`${API_BASE}/registers/${registerId}/device-binding`);
  }

  /** Shifts with search, filters and pages (PO-1). */
  sessionsPage(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<RegisterSession>> {
    return pagedList<RegisterSession>(this.http, `${API_BASE}/sessions`, f, page, size, extra);
  }

  listSessions(): Observable<RegisterSession[]> {
    return this.http.get<RegisterSession[]>(`${API_BASE}/sessions`);
  }

  getSession(sessionId: number): Observable<RegisterSession> {
    return this.http.get<RegisterSession>(`${API_BASE}/sessions/${sessionId}`);
  }

  /** ADMIN-only on the backend. */
  closeSession(sessionId: number, body: { countedCash?: number | null; denominations?: Denomination[]; varianceNote?: string }): Observable<RegisterSession> {
    return this.http.post<RegisterSession>(`${API_BASE}/sessions/${sessionId}/close`, body);
  }

  xReport(sessionId: number): Observable<ShiftReport> {
    return this.http.get<ShiftReport>(`${API_BASE}/sessions/${sessionId}/x-report`);
  }

  /** `print` counts this reading as a printing; the second and later printings are marked as copies. */
  zReport(sessionId: number, print = false): Observable<ShiftReport> {
    return this.http.get<ShiftReport>(`${API_BASE}/sessions/${sessionId}/z-report`, { params: { print } });
  }

  // ---- cash control (PO-2) ----

  cashSummary(sessionId: number): Observable<CashSummary> {
    return this.http.get<CashSummary>(`${API_BASE}/sessions/${sessionId}/cash`);
  }

  recordCash(sessionId: number, body: { kind: string; reasonCode?: string; amount?: number | null; note?: string; approverPin?: string; denominations?: Denomination[] }): Observable<CashMovement> {
    return this.http.post<CashMovement>(`${API_BASE}/sessions/${sessionId}/cash-movements`, body);
  }

  voidCash(id: number, reason: string): Observable<CashMovement> {
    return this.http.post<CashMovement>(`${API_BASE}/cash-movements/${id}/void`, { reason });
  }

  cashReasons(): Observable<Record<string, Record<string, string>>> {
    return this.http.get<Record<string, Record<string, string>>>(`${API_BASE}/cash-movements/reasons`);
  }

  cashMovementsPage(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<CashMovement>> {
    return pagedList<CashMovement>(this.http, `${API_BASE}/cash-movements`, f, page, size, extra);
  }

  exceptionsPage(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<PosException>> {
    return pagedList<PosException>(this.http, `${API_BASE}/pos/exceptions`, f, page, size, extra);
  }

  posSettings(): Observable<PosSettings> {
    return this.http.get<PosSettings>(`${API_BASE}/pos/settings`);
  }

  savePosSettings(s: Partial<PosSettings>): Observable<PosSettings> {
    return this.http.put<PosSettings>(`${API_BASE}/pos/settings`, s);
  }

  getSessionReport(sessionId: number): Observable<SessionReport> {
    return this.http.get<SessionReport>(`${API_BASE}/sessions/${sessionId}/report`);
  }
}
