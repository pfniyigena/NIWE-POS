import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { AppUser, Permission } from '../models';

export interface CreateUserPayload {
  username: string;
  password: string;
  fullName: string;
  /** Fourier: "Add forget password feature where user will input the email" — optional, needed only if this staff member should be able to use forgot-password. */
  email?: string | null;
  role: 'ADMIN' | 'CASHIER';
  /** Fourier: "Build a full permissions system" — additive ADMIN-tier grants beyond the plain role. */
  permissions: Permission[];
  /** Limit this account to these branches; empty/omitted = all branches. */
  branchIds?: number[];
  /** Optional custom role (Staff > Roles); ignored for an Admin. */
  customRoleId?: number | null;
}

export interface UpdateUserPayload {
  fullName: string;
  email?: string | null;
  role: 'ADMIN' | 'CASHIER';
  active: boolean;
  permissions: Permission[];
  branchIds?: number[];
  customRoleId?: number | null;
  /** Always true from the staff form: tells the backend customRoleId is intentional, so null clears the role. */
  customRoleProvided?: boolean;
}

/** Staff/user account management — every endpoint here is ADMIN-only on the backend. */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>(`${API_BASE}/users`);
  }

  create(payload: CreateUserPayload): Observable<AppUser> {
    return this.http.post<AppUser>(`${API_BASE}/users`, payload);
  }

  update(id: number, payload: UpdateUserPayload): Observable<AppUser> {
    return this.http.put<AppUser>(`${API_BASE}/users/${id}`, payload);
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/users/${id}/reset-password`, { newPassword });
  }

  /** Sets/changes this staff member's PIN — used to identify them at a shared terminal (see CashierSession). */
  setPin(id: number, pin: string): Observable<AppUser> {
    return this.http.post<AppUser>(`${API_BASE}/users/${id}/pin`, { pin });
  }
}
