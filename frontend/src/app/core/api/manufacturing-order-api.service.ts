import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CreateManufacturingOrderPayload, ManufacturingOrder, ProduceManufacturingOrderPayload } from '../models';

/** Manufacturing Module — "Manufacturing orders" (Fourier's own list, item 3). Admin-only. */
@Injectable({ providedIn: 'root' })
export class ManufacturingOrderApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<ManufacturingOrder[]> {
    return this.http.get<ManufacturingOrder[]>(`${API_BASE}/manufacturing/orders`);
  }

  getById(id: number): Observable<ManufacturingOrder> {
    return this.http.get<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}`);
  }

  create(payload: CreateManufacturingOrderPayload): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders`, payload);
  }

  confirm(id: number): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}/confirm`, {});
  }

  produce(id: number, payload: ProduceManufacturingOrderPayload): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}/produce`, payload);
  }

  cancel(id: number): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}/cancel`, {});
  }
}
