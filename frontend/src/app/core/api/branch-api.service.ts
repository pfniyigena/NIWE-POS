import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { API_BASE } from '../constants';
import { Branch, BranchReceiptInfo, BranchRequest } from '../models';

/** Admin > Branches — ADMIN-only on the backend. See Branch's own model note. */
@Injectable({ providedIn: 'root' })
export class BranchApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Branch[]> {
    return this.http.get<Branch[]>(`${API_BASE}/branches`);
  }

  create(payload: BranchRequest): Observable<Branch> {
    return this.http.post<Branch>(`${API_BASE}/branches`, payload);
  }

  update(id: number, payload: BranchRequest): Observable<Branch> {
    return this.http.put<Branch>(`${API_BASE}/branches/${id}`, payload);
  }

  /** Receipt header details — open to any signed-in user (the till uses it). */
  receiptInfo(id: number): Observable<BranchReceiptInfo> {
    return this.http.get<BranchReceiptInfo>(`${API_BASE}/branches/${id}/receipt-info`);
  }

  /** Refused by the backend for the head office. */
  setActive(id: number, active: boolean): Observable<Branch> {
    return this.http.post<Branch>(`${API_BASE}/branches/${id}/active`, { active });
  }

  /**
   * Active branches for a picker. Never errors: if the list can't be read (it is admin-only) the
   * result is empty and the calling screen hides its picker. Screens show a picker only when
   * there is more than one active branch — a single-branch shop sees no change.
   */
  loadChoices(): Observable<Branch[]> {
    return this.listAll().pipe(
      map((all) => all.filter((b) => b.active)),
      catchError(() => of([] as Branch[]))
    );
  }
}
