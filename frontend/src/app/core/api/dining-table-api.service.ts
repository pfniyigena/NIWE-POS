import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DiningTable } from '../models';

export interface TablePayload {
  name: string;
  section?: string | null;
  capacity: number;
  active: boolean;
}

/** Admin > Restaurant > Tables (Fourier: "add Restaurant management"). */
@Injectable({ providedIn: 'root' })
export class DiningTableApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<DiningTable[]> {
    return this.http.get<DiningTable[]>(`${API_BASE}/restaurant/tables`);
  }

  create(payload: TablePayload): Observable<DiningTable> {
    return this.http.post<DiningTable>(`${API_BASE}/restaurant/tables`, payload);
  }

  update(id: number, payload: TablePayload): Observable<DiningTable> {
    return this.http.put<DiningTable>(`${API_BASE}/restaurant/tables/${id}`, payload);
  }

  setStatus(id: number, status: string): Observable<DiningTable> {
    return this.http.post<DiningTable>(`${API_BASE}/restaurant/tables/${id}/status`, { status });
  }
}
