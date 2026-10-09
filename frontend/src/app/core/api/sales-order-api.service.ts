import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { FulfillSalesOrderRequest, SalesOrder, SalesOrderPayload } from '../models';

/** Task: "Add Sale order... So that we can have POS & Sales." Admin-only, mirrors PurchaseOrderApiService's shape. */
@Injectable({ providedIn: 'root' })
export class SalesOrderApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<SalesOrder[]> {
    return this.http.get<SalesOrder[]>(`${API_BASE}/sales-orders`);
  }

  getById(id: number): Observable<SalesOrder> {
    return this.http.get<SalesOrder>(`${API_BASE}/sales-orders/${id}`);
  }

  create(payload: SalesOrderPayload): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${API_BASE}/sales-orders`, payload);
  }

  /** Only while the order is still DRAFT — see the backend SalesOrderService.updateDraft's javadoc. */
  update(id: number, payload: SalesOrderPayload): Observable<SalesOrder> {
    return this.http.put<SalesOrder>(`${API_BASE}/sales-orders/${id}`, payload);
  }

  /** DRAFT -> CONFIRMED — enforces the customer's credit limit against this order's whole amount. */
  /** override=true confirms even when there isn't enough unreserved stock (the server answers 409 with the shortage otherwise). */
  confirm(id: number, override = false): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${API_BASE}/sales-orders/${id}/confirm`, {}, { params: override ? { override: 'true' } : {} });
  }

  cancel(id: number): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${API_BASE}/sales-orders/${id}/cancel`, {});
  }

  /** Records stock actually going out (possibly a partial fulfillment). */
  fulfill(id: number, payload: FulfillSalesOrderRequest): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${API_BASE}/sales-orders/${id}/fulfill`, payload);
  }

  /** Fourier: "add print (PDF with A4)" — an A4 PDF of this Sale Order, for opening in a new tab to print (see download-file.ts's openPdfForPrint). */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/sales-orders/${id}/pdf`, { responseType: 'blob' });
  }
}
