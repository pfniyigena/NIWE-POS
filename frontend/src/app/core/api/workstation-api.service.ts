import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { API_BASE } from '../constants';
import { Workstation, WorkstationPayload } from '../models';

/** Manufacturing Module — "Workstations" (Fourier's own list, item 5). Admin-only. */
@Injectable({ providedIn: 'root' })
export class WorkstationApiService {
  constructor(private http: HttpClient) {}

  /** A page of the list with search and filters (MF-1). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | null | undefined> = {}): Observable<PageResult<Workstation>> {
    return pagedList<Workstation>(this.http, `${API_BASE}/manufacturing/workstations`, f, page, size, extra);
  }

  listAll(): Observable<Workstation[]> {
    return this.http.get<Workstation[]>(`${API_BASE}/manufacturing/workstations`);
  }

  listActive(): Observable<Workstation[]> {
    return this.http.get<Workstation[]>(`${API_BASE}/manufacturing/workstations/active`);
  }

  getById(id: number): Observable<Workstation> {
    return this.http.get<Workstation>(`${API_BASE}/manufacturing/workstations/${id}`);
  }

  create(payload: WorkstationPayload): Observable<Workstation> {
    return this.http.post<Workstation>(`${API_BASE}/manufacturing/workstations`, payload);
  }

  update(id: number, payload: WorkstationPayload): Observable<Workstation> {
    return this.http.put<Workstation>(`${API_BASE}/manufacturing/workstations/${id}`, payload);
  }
}
