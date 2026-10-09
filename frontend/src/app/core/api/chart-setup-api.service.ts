import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../constants';

export type ChartCountryOption = 'RWANDA' | 'CHAD';

export interface ChartSetupStatus {
  needsSelection: boolean;
  suggestedCountry: ChartCountryOption;
}

/**
 * Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add this
 * on the screen then at first time a user select the country?" — backs both the first-run setup
 * wizard (ChartCountrySetupComponent) and the admin route guard's own redirect check (see
 * auth.guard.ts's adminGuard).
 */
@Injectable({ providedIn: 'root' })
export class ChartSetupApiService {
  // Once the backend has confirmed the chart is seeded, there's no reason to keep asking on every
  // single admin navigation for the rest of this session — see adminGuard's own use of this.
  private confirmedDone = false;

  constructor(private http: HttpClient) {}

  status(): Observable<ChartSetupStatus> {
    return this.http.get<ChartSetupStatus>(`${API_BASE}/accounting/chart-setup`).pipe(
      tap((status) => {
        if (!status.needsSelection) {
          this.confirmedDone = true;
        }
      })
    );
  }

  isConfirmedDone(): boolean {
    return this.confirmedDone;
  }

  select(country: ChartCountryOption): Observable<ChartSetupStatus> {
    return this.http.post<ChartSetupStatus>(`${API_BASE}/accounting/chart-setup`, { country }).pipe(
      tap(() => (this.confirmedDone = true))
    );
  }
}
