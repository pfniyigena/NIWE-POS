import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { EfrisChadLogos, EfrisChadTerminal } from '../models';

/**
 * Task: "Now I want to add new app (EFRIS Chad) with will need the following settings: url,
 * basic_auth_username, basic_password, sdc_number, branch_code, mrc, integration_key." Mirrors
 * EfrisApiService (Rwanda's own settings service) but deliberately separate — EFRIS Chad's
 * connection fields don't overlap Rwanda's, and this is settings storage only for now (no
 * submission call yet, per Fourier's "I will share the resulting parsing later").
 */
@Injectable({ providedIn: 'root' })
export class EfrisChadApiService {
  constructor(private http: HttpClient) {}

  listTerminals(): Observable<EfrisChadTerminal[]> {
    return this.http.get<EfrisChadTerminal[]>(`${API_BASE}/apps/efris-chad/terminals`);
  }

  saveTerminalSettings(
    registerId: number,
    settings: {
      url: string | null;
      basicAuthUsername: string | null;
      basicPassword: string | null;
      sdcNumber: string | null;
      branchCode: string | null;
      mrc: string | null;
      integrationKey: string | null;
      craLogoDataUrl: string | null;
      efrisLogoDataUrl: string | null;
    }
  ): Observable<EfrisChadTerminal> {
    return this.http.put<EfrisChadTerminal>(`${API_BASE}/apps/efris-chad/terminals/${registerId}`, settings);
  }

  /**
   * Task: "One Terminal can have only one EFRIS mapped with" — unmaps this terminal from EFRIS
   * Chad (deletes its EfrisChadSetting row), either to stop using EFRIS Chad on that till or to
   * free it up to be mapped to EFRIS Rwanda instead.
   */
  clearTerminalSettings(registerId: number): Observable<EfrisChadTerminal> {
    return this.http.delete<EfrisChadTerminal>(`${API_BASE}/apps/efris-chad/terminals/${registerId}`);
  }

  /**
   * Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — the
   * narrow, non-admin-gated lookup ReceiptComponent/ReturnReceiptComponent call, mirroring
   * EfrisApiService.getLogosForRegister (Rwanda's equivalent).
   */
  getLogosForRegister(registerId: number): Observable<EfrisChadLogos> {
    return this.http.get<EfrisChadLogos>(`${API_BASE}/apps/efris-chad/terminals/${registerId}/logos`);
  }
}
