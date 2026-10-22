import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface LicenseHistoryRow {
  licenseId: string;
  serial: number;
  customer: string;
  issuedAt: string;
  expiresAt: string | null;
  installedAt: string;
  installedBy: string | null;
  active: boolean;
  modules: number;
}

/** What Settings > Modules shows about the license. */
export interface LicenseStatus {
  installationId: string;
  /** False when this build has no license key, so no license can be installed. */
  keyConfigured: boolean;
  hasLicense: boolean;
  /** The installed license passed every check; false with a problem when it cannot be believed. */
  valid: boolean;
  problem: string | null;
  customer: string | null;
  licenseId: string | null;
  serial: number | null;
  issuedAt: string | null;
  expiresAt: string | null;
  expired: boolean;
  daysLeft: number | null;
  expiringSoon: boolean;
  modules: string[];
  /** The developer switch is on: every module counts as licensed. */
  devAllModules: boolean;
  history: LicenseHistoryRow[];
}

export interface ModuleRequestInfo {
  id: number;
  module: string;
  moduleName: string;
  note: string | null;
  status: 'PENDING' | 'FULFILLED' | 'WITHDRAWN';
  requestedBy: string;
  requestedAt: string;
  closedAt: string | null;
  closedNote: string | null;
  emailed: boolean;
}

export interface RequestResult {
  request: ModuleRequestInfo;
  emailSent: boolean;
  emailNote: string;
  /** What to send to NIWE by hand when it was not e-mailed. */
  text: string;
}

/** The license part of Settings > Modules (admin only). */
@Injectable({ providedIn: 'root' })
export class LicenseApiService {
  private base = `${API_BASE}/license`;

  constructor(private http: HttpClient) {}

  status(): Observable<LicenseStatus> {
    return this.http.get<LicenseStatus>(this.base);
  }

  /** Installs a license file; the argument is the text of the file. */
  install(license: string): Observable<LicenseStatus> {
    return this.http.post<LicenseStatus>(this.base, { license });
  }

  requests(): Observable<ModuleRequestInfo[]> {
    return this.http.get<ModuleRequestInfo[]>(`${this.base}/requests`);
  }

  request(module: string, note: string | null): Observable<RequestResult> {
    return this.http.post<RequestResult>(`${this.base}/requests`, { module, note });
  }

  withdraw(id: number): Observable<ModuleRequestInfo> {
    return this.http.delete<ModuleRequestInfo>(`${this.base}/requests/${id}`);
  }
}
