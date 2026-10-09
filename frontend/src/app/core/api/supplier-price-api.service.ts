import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { SupplierPrice } from '../models';

export interface SupplierPricePayload {
  supplierId: number;
  productId: number;
  unitCost: number;
  validFrom?: string | null;
  validTo?: string | null;
  note?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SupplierPriceApiService {
  constructor(private http: HttpClient) {}

  list(supplierId?: number | null): Observable<SupplierPrice[]> {
    let params = new HttpParams();
    if (supplierId) params = params.set('supplierId', String(supplierId));
    return this.http.get<SupplierPrice[]>(`${API_BASE}/supplier-prices`, { params });
  }

  /** Prices in force today for one supplier. */
  current(supplierId: number): Observable<SupplierPrice[]> {
    return this.http.get<SupplierPrice[]>(`${API_BASE}/supplier-prices/current`, { params: { supplierId: String(supplierId) } });
  }

  create(payload: SupplierPricePayload): Observable<SupplierPrice> {
    return this.http.post<SupplierPrice>(`${API_BASE}/supplier-prices`, payload);
  }

  update(id: number, payload: SupplierPricePayload): Observable<SupplierPrice> {
    return this.http.put<SupplierPrice>(`${API_BASE}/supplier-prices/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/supplier-prices/${id}`);
  }
}
