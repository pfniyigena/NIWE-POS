import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

/** Admin > Subscriptions > Job settings — mirrors the backend's SubscriptionAutomationController.SettingsDto. */
export interface SubscriptionJobSettings {
  billingEnabled: boolean;
  /** "HH:mm" in the store time zone. */
  jobTime: string;
  reminderEnabled: boolean;
  reminderDays: number;
  reminderCustomer: boolean;
  reminderStore: boolean;
  digestEnabled: boolean;
  overdueAction: 'NONE' | 'PAUSE' | 'CANCEL';
  overdueDays: number;
  lastRunDate: string | null;
  lastSummary: string | null;
}

export type SubscriptionJobPayload = Omit<SubscriptionJobSettings, 'lastRunDate' | 'lastSummary'>;

export interface SubscriptionJobResult {
  billed: number;
  failed: number;
  remindersSent: number;
  overdueActioned: number;
  digestSent: boolean;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class SubscriptionAutomationApiService {
  constructor(private http: HttpClient) {}

  get(): Observable<SubscriptionJobSettings> {
    return this.http.get<SubscriptionJobSettings>(`${API_BASE}/subscriptions/automation`);
  }

  save(payload: SubscriptionJobPayload): Observable<SubscriptionJobSettings> {
    return this.http.put<SubscriptionJobSettings>(`${API_BASE}/subscriptions/automation`, payload);
  }

  /** Runs the job right now with the saved settings; counts as today's run. */
  run(): Observable<SubscriptionJobResult> {
    return this.http.post<SubscriptionJobResult>(`${API_BASE}/subscriptions/automation/run`, {});
  }
}
