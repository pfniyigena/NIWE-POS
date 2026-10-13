import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

/** An admin-defined role: a named set of access areas, each at View or Manage level. */
export interface CustomRole {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  /** Grant codes like 'INVENTORY_MANAGE' or 'REPORTS_VIEW'. */
  grants: string[];
  userCount: number;
}

export interface CustomRolePayload {
  name: string;
  description: string | null;
  active: boolean;
  grants: string[];
}

export interface AccessAreaInfo {
  code: string;
  label: string;
  description: string;
  /** True for areas that can only be viewed (Reports). */
  viewOnly: boolean;
}

/** Custom roles — every endpoint is ADMIN-only on the backend. */
@Injectable({ providedIn: 'root' })
export class RoleApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<CustomRole[]> {
    return this.http.get<CustomRole[]>(`${API_BASE}/roles`);
  }

  areas(): Observable<AccessAreaInfo[]> {
    return this.http.get<AccessAreaInfo[]>(`${API_BASE}/roles/areas`);
  }

  create(payload: CustomRolePayload): Observable<CustomRole> {
    return this.http.post<CustomRole>(`${API_BASE}/roles`, payload);
  }

  update(id: number, payload: CustomRolePayload): Observable<CustomRole> {
    return this.http.put<CustomRole>(`${API_BASE}/roles/${id}`, payload);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/roles/${id}`);
  }
}
