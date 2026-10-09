import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PackagingBreakdown, PackagingBreakdownRequest, PackagingRule, PackagingRulePayload } from '../models';

/**
 * Admin > Inventory > Packaging — Fourier: "Product Packaging & Breakdown / Unit Conversion".
 * Rule CRUD is always available; only runBreakdown can be refused by the backend when
 * StoreSettings.productBreakdownEnabled is off (see PackagingService#breakdown's javadoc).
 */
@Injectable({ providedIn: 'root' })
export class PackagingApiService {
  constructor(private http: HttpClient) {}

  listRules(activeOnly = false): Observable<PackagingRule[]> {
    const url = activeOnly ? `${API_BASE}/packaging/rules?activeOnly=true` : `${API_BASE}/packaging/rules`;
    return this.http.get<PackagingRule[]>(url);
  }

  getRuleById(id: number): Observable<PackagingRule> {
    return this.http.get<PackagingRule>(`${API_BASE}/packaging/rules/${id}`);
  }

  createRule(payload: PackagingRulePayload): Observable<PackagingRule> {
    return this.http.post<PackagingRule>(`${API_BASE}/packaging/rules`, payload);
  }

  updateRule(id: number, payload: PackagingRulePayload): Observable<PackagingRule> {
    return this.http.put<PackagingRule>(`${API_BASE}/packaging/rules/${id}`, payload);
  }

  setRuleActive(id: number, active: boolean): Observable<PackagingRule> {
    return this.http.post<PackagingRule>(`${API_BASE}/packaging/rules/${id}/active`, { active });
  }

  /** Omit ruleId for the full cross-rule history. */
  listBreakdowns(ruleId?: number): Observable<PackagingBreakdown[]> {
    const url = ruleId != null ? `${API_BASE}/packaging/breakdowns?ruleId=${ruleId}` : `${API_BASE}/packaging/breakdowns`;
    return this.http.get<PackagingBreakdown[]>(url);
  }

  /** Executes a breakdown immediately — no draft/confirm step, see PackagingService#breakdown's javadoc. */
  runBreakdown(request: PackagingBreakdownRequest): Observable<PackagingBreakdown> {
    return this.http.post<PackagingBreakdown>(`${API_BASE}/packaging/breakdowns`, request);
  }
}
