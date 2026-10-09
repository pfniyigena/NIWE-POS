import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CashierSession, CashierSessionZReport } from '../models';

/**
 * Lets more than one cashier work the same shift (till session) at once — each one identifies
 * themselves with their own PIN rather than a full login, so the terminal's tab bar can be scoped
 * to "my carts" even while someone else is also ringing sales on the same register. See
 * CashierSession's doc comment in models.ts and the backend's CashierSessionService.
 */
@Injectable({ providedIn: 'root' })
export class CashierSessionApiService {
  constructor(private http: HttpClient) {}

  /** A *different* cashier than whoever's logged in steps up to this terminal, identified by PIN alone. */
  join(registerSessionId: number, pin: string): Observable<CashierSession> {
    return this.http.post<CashierSession>(`${API_BASE}/sessions/${registerSessionId}/cashier-sessions`, { pin });
  }

  /** No PIN needed — resolves (or silently creates) the logged-in browser's own cashier session on this shift. */
  mine(registerSessionId: number): Observable<CashierSession> {
    return this.http.get<CashierSession>(`${API_BASE}/sessions/${registerSessionId}/cashier-sessions/mine`);
  }

  /** Every cashier session on this shift — ADMIN-only on the backend; used by the admin Shifts screen. */
  listForShift(registerSessionId: number): Observable<CashierSession[]> {
    return this.http.get<CashierSession[]>(`${API_BASE}/sessions/${registerSessionId}/cashier-sessions`);
  }

  /**
   * ADMIN-only on the backend. Adds a specific staff member's cashier session to this shift
   * directly — no PIN needed. This is how an admin gets a *different* cashier a session on a
   * shift without knowing that cashier's PIN or standing at the physical terminal (see the
   * "Switch cashier" comment on `join` above, and CashierSessionService.assignCashier).
   */
  assign(registerSessionId: number, cashierId: number): Observable<CashierSession> {
    return this.http.post<CashierSession>(`${API_BASE}/sessions/${registerSessionId}/cashier-sessions/assign`, { cashierId });
  }

  /**
   * Backs the plain "Sessions" screen. Open to any logged-in user, but scoped by role on the
   * backend, not by what's asked for here: an ADMIN gets every cashier session across every
   * shift; anyone else gets only their own.
   */
  listAll(): Observable<CashierSession[]> {
    return this.http.get<CashierSession[]>(`${API_BASE}/cashier-sessions`);
  }

  /** Ends a cashier session — anyone can end their own; only an admin can end someone else's. */
  close(registerSessionId: number, cashierSessionId: number): Observable<CashierSession> {
    return this.http.post<CashierSession>(
      `${API_BASE}/sessions/${registerSessionId}/cashier-sessions/${cashierSessionId}/close`,
      {}
    );
  }

  /** ADMIN-only on the backend — ends every open cashier session on this shift without closing the shift itself. */
  closeAllForShift(registerSessionId: number): Observable<CashierSession[]> {
    return this.http.post<CashierSession[]>(`${API_BASE}/sessions/${registerSessionId}/cashier-sessions/close-all`, {});
  }

  /**
   * A single cashier session by id — polled by the customer-facing display (Task: "Add
   * customer-facing double screen") to learn which order is currently on screen. Ownership-or-
   * admin gated on the backend: the display needs to be logged in as either the cashier this
   * session belongs to, or an admin.
   */
  getById(cashierSessionId: number): Observable<CashierSession> {
    return this.http.get<CashierSession>(`${API_BASE}/cashier-sessions/${cashierSessionId}`);
  }

  /** Called by the terminal whenever the cashier switches tabs — never by the display, which only ever reads this via getById. Pass null to clear (no tab selected). */
  setActiveOrder(cashierSessionId: number, orderId: number | null): Observable<CashierSession> {
    return this.http.put<CashierSession>(`${API_BASE}/cashier-sessions/${cashierSessionId}/active-order`, { orderId });
  }

  /**
   * This one cashier session's own report — deliberately a different read from
   * RegisterApiService.getSessionReport (the whole shift's Z-report). Task: "We need to
   * differentiate CashierSession Z Report with RegisterSession Z Report."
   */
  getReport(cashierSessionId: number): Observable<CashierSessionZReport> {
    return this.http.get<CashierSessionZReport>(`${API_BASE}/cashier-sessions/${cashierSessionId}/report`);
  }
}
