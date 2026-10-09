import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Bom, BomPayload } from '../models';

/** Manufacturing Module — "Bill of Materials" (Fourier's own list, item 1). Admin-only. */
@Injectable({ providedIn: 'root' })
export class BomApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Bom[]> {
    return this.http.get<Bom[]>(`${API_BASE}/manufacturing/boms`);
  }

  listForProduct(productId: number): Observable<Bom[]> {
    return this.http.get<Bom[]>(`${API_BASE}/manufacturing/boms/product/${productId}`);
  }

  getById(id: number): Observable<Bom> {
    return this.http.get<Bom>(`${API_BASE}/manufacturing/boms/${id}`);
  }

  create(payload: BomPayload): Observable<Bom> {
    return this.http.post<Bom>(`${API_BASE}/manufacturing/boms`, payload);
  }

  update(id: number, payload: BomPayload): Observable<Bom> {
    return this.http.put<Bom>(`${API_BASE}/manufacturing/boms/${id}`, payload);
  }
}
