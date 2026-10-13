import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PublicBranding } from '../models';

/** Fourier: "it is better to have a home page with look nice like a website" — backs PublicHomeComponent's branding; the third, and only other, unauthenticated API namespace in the app alongside PublicReservationApiService/PublicHelpDeskApiService. */
@Injectable({ providedIn: 'root' })
export class PublicSettingsApiService {
  constructor(private http: HttpClient) {}

  branding(): Observable<PublicBranding> {
    return this.http.get<PublicBranding>(`${API_BASE}/public/settings/branding`);
  }

  /** Which public services are available — {RESTAURANT: bool, HELPDESK: bool}; follows Settings > Modules. */
  modules(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(`${API_BASE}/public/modules`);
  }
}
