import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { TaxRateOption } from '../models';

export interface CreateTaxRatePayload {
  code: string;
  name: string;
  ratePercent: number;
  inclusive: boolean;
}

export interface UpdateTaxRatePayload {
  name: string;
  ratePercent: number;
  active: boolean;
  inclusive: boolean;
}

/** Admin-only end to end — see TaxRateController's backend javadoc for why there's no "active list" endpoint for cashiers the way payment methods have one. */
@Injectable({ providedIn: 'root' })
export class TaxRateApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<TaxRateOption[]> {
    return this.http.get<TaxRateOption[]>(`${API_BASE}/tax-rates`);
  }

  create(payload: CreateTaxRatePayload): Observable<TaxRateOption> {
    return this.http.post<TaxRateOption>(`${API_BASE}/tax-rates`, payload);
  }

  update(id: number, payload: UpdateTaxRatePayload): Observable<TaxRateOption> {
    return this.http.put<TaxRateOption>(`${API_BASE}/tax-rates/${id}`, payload);
  }
}
