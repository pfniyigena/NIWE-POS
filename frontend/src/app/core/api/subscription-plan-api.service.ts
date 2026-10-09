import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { SubscriptionPlan, SubscriptionPlanPayload } from '../models';

/** Admin > Sales > Subscription plans — ADMIN-only catalog a customer subscribes to (see the backend SubscriptionPlanService). */
@Injectable({ providedIn: 'root' })
export class SubscriptionPlanApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${API_BASE}/subscription-plans`);
  }

  /** Active plans only — what the "New subscription" picker offers. */
  listActive(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${API_BASE}/subscription-plans/active`);
  }

  getById(id: number): Observable<SubscriptionPlan> {
    return this.http.get<SubscriptionPlan>(`${API_BASE}/subscription-plans/${id}`);
  }

  create(payload: SubscriptionPlanPayload): Observable<SubscriptionPlan> {
    return this.http.post<SubscriptionPlan>(`${API_BASE}/subscription-plans`, payload);
  }

  update(id: number, payload: SubscriptionPlanPayload): Observable<SubscriptionPlan> {
    return this.http.put<SubscriptionPlan>(`${API_BASE}/subscription-plans/${id}`, payload);
  }
}
