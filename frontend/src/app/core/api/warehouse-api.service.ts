import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { LocationType, ProductStockByLocation, Warehouse, WarehouseLocation, WarehouseLocationRequest, WarehouseRequest } from '../models';

/** Admin > Inventory > Warehouses — ADMIN-only on the backend. See Warehouse's own model javadoc for scope. */
@Injectable({ providedIn: 'root' })
export class WarehouseApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Warehouse[]> {
    return this.http.get<Warehouse[]>(`${API_BASE}/warehouses`);
  }

  create(payload: WarehouseRequest): Observable<Warehouse> {
    return this.http.post<Warehouse>(`${API_BASE}/warehouses`, payload);
  }

  update(id: number, payload: WarehouseRequest): Observable<Warehouse> {
    return this.http.put<Warehouse>(`${API_BASE}/warehouses/${id}`, payload);
  }

  /** Same convention as PackagingApiService.setRuleActive. Refused by the backend for the primary warehouse. */
  setActive(id: number, active: boolean): Observable<Warehouse> {
    return this.http.post<Warehouse>(`${API_BASE}/warehouses/${id}/active`, { active });
  }

  /** Multi-warehouse phase 1: where one product's stock sits, by warehouse and location (read-only). */
  stockByLocation(productId: number): Observable<ProductStockByLocation> {
    return this.http.get<ProductStockByLocation>(`${API_BASE}/warehouses/stock/product/${productId}`);
  }

  // ---- Locations — sub-areas within a warehouse; see WarehouseLocation model's own note ----

  listLocations(warehouseId: number): Observable<WarehouseLocation[]> {
    return this.http.get<WarehouseLocation[]>(`${API_BASE}/warehouses/${warehouseId}/locations`);
  }

  createLocation(warehouseId: number, payload: WarehouseLocationRequest): Observable<WarehouseLocation> {
    return this.http.post<WarehouseLocation>(`${API_BASE}/warehouses/${warehouseId}/locations`, payload);
  }

  /** Refused by the backend for a warehouse's RECEIVING location, or its last active SALES location. */
  setLocationActive(id: number, active: boolean): Observable<WarehouseLocation> {
    return this.http.post<WarehouseLocation>(`${API_BASE}/warehouses/locations/${id}/active`, { active });
  }

  /** Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — replaces the old setDefaultLocation. Refused by the backend when retyping away from RECEIVING at all, away from a last-active SALES location, or onto a RECEIVING type the warehouse already has. */
  setLocationType(id: number, type: LocationType): Observable<WarehouseLocation> {
    return this.http.post<WarehouseLocation>(`${API_BASE}/warehouses/locations/${id}/type`, { type });
  }
}
