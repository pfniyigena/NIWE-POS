import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PurchaseRequisition, ReorderSuggestion } from '../models';

@Injectable({ providedIn: 'root' })
export class ReorderApiService {
  constructor(private http: HttpClient) {}

  suggestions(): Observable<ReorderSuggestion[]> {
    return this.http.get<ReorderSuggestion[]>(`${API_BASE}/reorder`);
  }

  /** Creates a DRAFT purchase requisition from the chosen products. */
  createRequisition(lines: { productId: number; quantity: number }[], notes?: string | null): Observable<PurchaseRequisition> {
    return this.http.post<PurchaseRequisition>(`${API_BASE}/reorder/requisition`, { lines, notes: notes ?? null });
  }
}
