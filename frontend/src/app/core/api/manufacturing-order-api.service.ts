import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import { ComponentAvailability, CreateManufacturingOrderPayload, ManufacturingOrder, ProduceManufacturingOrderPayload, UpdateManufacturingOrderPayload } from '../models';

/** Manufacturing Module — "Manufacturing orders" (Fourier's own list, item 3). Admin-only. */
@Injectable({ providedIn: 'root' })
export class ManufacturingOrderApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<ManufacturingOrder>> {
    return pagedList<ManufacturingOrder>(this.http, `${API_BASE}/manufacturing/orders`, f, page, size, extra);
  }

  listAll(): Observable<ManufacturingOrder[]> {
    return this.http.get<ManufacturingOrder[]>(`${API_BASE}/manufacturing/orders`);
  }

  getById(id: number): Observable<ManufacturingOrder> {
    return this.http.get<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}`);
  }

  create(payload: CreateManufacturingOrderPayload): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders`, payload);
  }

  update(id: number, payload: UpdateManufacturingOrderPayload): Observable<ManufacturingOrder> {
    return this.http.put<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}`, payload);
  }

  copy(id: number): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/orders/${id}/copy`, {});
  }

  /** MF-6: the printable job card (A4 PDF). */
  jobCard(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/manufacturing/orders/${id}/job-card`, { responseType: 'blob' });
  }

  availability(id: number): Observable<ComponentAvailability> {
    return this.http.get<ComponentAvailability>(`${API_BASE}/manufacturing/orders/${id}/availability`);
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
