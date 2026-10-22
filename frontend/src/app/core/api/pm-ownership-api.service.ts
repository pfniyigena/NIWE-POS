import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface OwnerShare { ownerId: number; ownerCode: string | null; ownerName: string; percent: number; lead: boolean; }
/** key is the start date, or "start" for the split that holds from the beginning. */
export interface OwnerSplit { validFrom: string | null; key: string; current: boolean; shares: OwnerShare[]; locked: boolean; lockedReason: string | null; canRemove: boolean; }
export interface OwnershipView {
  propertyId: number; propertyCode: string; propertyName: string; leadOwnerId: number; leadOwnerName: string | null; splits: OwnerSplit[]; lockedThrough: string | null; rebuild: string[]; canManage: boolean;
}
export interface OwnershipPayload { validFrom: string | null; shares: { ownerId: number; percent: number }[]; leadOwnerId?: number | null; }

/** Who owns a property and the percentage each one has, from a date. */
@Injectable({ providedIn: 'root' })
export class PmOwnershipApiService {
  private base = `${API_BASE}/propertymgmt/properties`;

  constructor(private http: HttpClient) {}

  get(propertyId: number): Observable<OwnershipView> { return this.http.get<OwnershipView>(`${this.base}/${propertyId}/owners`); }
  set(propertyId: number, b: OwnershipPayload): Observable<OwnershipView> { return this.http.put<OwnershipView>(`${this.base}/${propertyId}/owners`, b); }
  remove(propertyId: number, key: string): Observable<OwnershipView> { return this.http.delete<OwnershipView>(`${this.base}/${propertyId}/owners/${encodeURIComponent(key)}`); }
}
