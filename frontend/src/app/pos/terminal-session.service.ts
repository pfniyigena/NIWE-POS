import { Injectable } from '@angular/core';
import { CashierSession } from '../core/models';

/**
 * Caches, in memory, which cashier session is "at" each register session's terminal — keyed by
 * `registerSessionId` — so that navigating *within* the app (e.g. Checkout's or the receipt's
 * "Back to terminal" link) can return straight to the terminal without re-prompting for a PIN
 * (Tasks: "The back button on checkout should not request PIN" / "The back button on print
 * receipt should take back to terminal and not request PIN").
 *
 * This is deliberately a plain `providedIn: 'root'` singleton, not anything persisted to
 * localStorage/sessionStorage: Angular keeps one instance of it alive for as long as the SPA
 * itself is running, so it survives route navigations (TerminalComponent being destroyed and
 * recreated when you navigate away and back) but is wiped clean by an actual browser reload —
 * which is exactly the split this app wants. The PIN gate itself (Task: "To open terminal for
 * selling should ask PIN...") was a deliberate Round 1 decision and must keep working on a real
 * reload; only in-app back-navigation should skip it, and a full reload re-creates this service
 * from scratch (cache empty), so TerminalComponent falls back to showing the PIN screen exactly
 * as before. See TerminalComponent.ngOnInit for the read side of this cache.
 */
@Injectable({ providedIn: 'root' })
export class TerminalSessionService {
  private sessions = new Map<number, CashierSession>();

  get(registerSessionId: number): CashierSession | null {
    return this.sessions.get(registerSessionId) ?? null;
  }

  set(registerSessionId: number, cashierSession: CashierSession): void {
    this.sessions.set(registerSessionId, cashierSession);
  }

  clear(registerSessionId: number): void {
    this.sessions.delete(registerSessionId);
  }
}
