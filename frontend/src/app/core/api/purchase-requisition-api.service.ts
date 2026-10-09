import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ConvertRequisitionPayload, PurchaseOrder, PurchaseRequisition, PurchaseRequisitionPayload } from '../models';

/** Fourier: "Add ... the purchase-requisition approval gate ... without touching warehousing." Admin-only, mirrors PurchaseOrderApiService's shape. */
@Injectable({ providedIn: 'root' })
export class PurchaseRequisitionApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<PurchaseRequisition[]> {
    return this.http.get<PurchaseRequisition[]>(`${API_BASE}/purchase-requisitions`);
  }

  getById(id: number): Observable<PurchaseRequisition> {
    return this.http.get<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}`);
  }

  create(payload: PurchaseRequisitionPayload): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/purchase-requisitions`, payload);
  }

  /** Only while the requisition is still DRAFT — see the backend PurchaseRequisitionService.updateDraft's javadoc. */
  update(id: number, payload: PurchaseRequisitionPayload): Observable<PurchaseRequisition> {
    return this.http.put<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}`, payload);
  }

  submit(id: number): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}/submit`, {});
  }

  approve(id: number): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}/approve`, {});
  }

  reject(id: number, reason: string | null): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}/reject`, { reason });
  }

  cancel(id: number): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/purchase-requisitions/${id}/cancel`, {});
  }

  /** Returns the newly-created PurchaseOrder (not the requisition) — see the backend PurchaseRequisitionController.convert's javadoc. */
  convert(id: number, payload: ConvertRequisitionPayload): Observable<PurchaseOrder> {
    return this.http.post<PurchaseOrder>(`${API_BASE}/purchase-requisitions/${id}/convert`, payload);
  }
}
