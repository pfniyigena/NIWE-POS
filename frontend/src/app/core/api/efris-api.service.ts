import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { EfrisLogos, EfrisTerminal } from '../models';

/** Task: "EFRIS_setting for one terminal" — per-terminal EFRIS URL config, admin-only. Deliberately separate from AppsApiService: this is EFRIS-specific structured data, not the generic Apps key/value settings store. */
@Injectable({ providedIn: 'root' })
export class EfrisApiService {
  constructor(private http: HttpClient) {}

  listTerminals(): Observable<EfrisTerminal[]> {
    return this.http.get<EfrisTerminal[]>(`${API_BASE}/apps/efris/terminals`);
  }

  saveTerminalSettings(
    registerId: number,
    settings: {
      efrisUrl: string | null;
      efrisDb: string | null;
      efrisUid: number | null;
      efrisPassword: string | null;
      rraLogoDataUrl: string | null;
      efrisLogoDataUrl: string | null;
    }
  ): Observable<EfrisTerminal> {
    return this.http.put<EfrisTerminal>(`${API_BASE}/apps/efris/terminals/${registerId}`, settings);
  }

  /**
   * Task: "One Terminal can have only one EFRIS mapped with" — unmaps this terminal from EFRIS
   * Rwanda (deletes its EfrisSetting row), either to stop using EFRIS Rwanda on that till or to
   * free it up to be mapped to EFRIS Chad instead.
   */
  clearTerminalSettings(registerId: number): Observable<EfrisTerminal> {
    return this.http.delete<EfrisTerminal>(`${API_BASE}/apps/efris/terminals/${registerId}`);
  }

  /**
   * Task: "add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the printout" — the
   * narrow, non-admin-gated lookup ReceiptComponent calls (any logged-in cashier can print a
   * receipt, not just an admin). See EfrisSettingController.getLogos's javadoc for why this is a
   * separate endpoint from listTerminals rather than reusing it.
   */
  getLogosForRegister(registerId: number): Observable<EfrisLogos> {
    return this.http.get<EfrisLogos>(`${API_BASE}/apps/efris/terminals/${registerId}/logos`);
  }
}
