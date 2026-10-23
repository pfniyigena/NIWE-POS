import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import {
  GenerateOrderFromPlanLinePayload,
  GenerateProductionPlanPayload,
  ManufacturingOrder,
  ProductionPlan,
} from '../models';

/** Manufacturing Module — "Production plans" (Fourier's own list, item 2; full MRP-style per "I need full features"). Admin-only. */
@Injectable({ providedIn: 'root' })
export class ProductionPlanApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<ProductionPlan>> {
    return pagedList<ProductionPlan>(this.http, `${API_BASE}/manufacturing/production-plans`, f, page, size, extra);
  }

  listAll(): Observable<ProductionPlan[]> {
    return this.http.get<ProductionPlan[]>(`${API_BASE}/manufacturing/production-plans`);
  }

  getById(id: number): Observable<ProductionPlan> {
    return this.http.get<ProductionPlan>(`${API_BASE}/manufacturing/production-plans/${id}`);
  }

  generate(payload: GenerateProductionPlanPayload): Observable<ProductionPlan> {
    return this.http.post<ProductionPlan>(`${API_BASE}/manufacturing/production-plans/generate`, payload);
  }

  generateOrder(planId: number, lineId: number, payload: GenerateOrderFromPlanLinePayload): Observable<ManufacturingOrder> {
    return this.http.post<ManufacturingOrder>(`${API_BASE}/manufacturing/production-plans/${planId}/lines/${lineId}/generate-order`, payload);
  }
}
