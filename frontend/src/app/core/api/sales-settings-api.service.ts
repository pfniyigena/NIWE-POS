import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { SalesSettings, SalesSettingsPayload } from '../models';

/** Admin > Sales > Settings — Fourier: "Add invoices receiver emails in sales settings." */
@Injectable({ providedIn: 'root' })
export class SalesSettingsApiService {
  constructor(private http: HttpClient) {}

  get(): Observable<SalesSettings> {
    return this.http.get<SalesSettings>(`${API_BASE}/sales/settings`);
  }

  update(payload: SalesSettingsPayload): Observable<SalesSettings> {
    return this.http.put<SalesSettings>(`${API_BASE}/sales/settings`, payload);
  }
}
