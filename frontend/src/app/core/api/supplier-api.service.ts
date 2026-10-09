import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Supplier } from '../models';

export interface SupplierPayload {
  name: string;
  contactName?: string | null;
  phone?: string | null;
  email?: string | null;
  taxId?: string | null;
  address?: string | null;
  notes?: string | null;
  active: boolean;
}

/** Task: "Inventory depth ... purchase orders and supplier records." Admin-only, same tier as CatalogApiService's product endpoints. */
@Injectable({ providedIn: 'root' })
export class SupplierApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${API_BASE}/suppliers`);
  }

  /** Powers the purchase-order form's supplier picker — active suppliers only. */
  listActive(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${API_BASE}/suppliers/active`);
  }

  create(payload: SupplierPayload): Observable<Supplier> {
    return this.http.post<Supplier>(`${API_BASE}/suppliers`, payload);
  }

  update(id: number, payload: SupplierPayload): Observable<Supplier> {
    return this.http.put<Supplier>(`${API_BASE}/suppliers/${id}`, payload);
  }

  /** Hard-deletes an unreferenced supplier, or deactivates one that already has purchase order history — see the backend SupplierService.delete's javadoc. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/suppliers/${id}`);
  }
}
