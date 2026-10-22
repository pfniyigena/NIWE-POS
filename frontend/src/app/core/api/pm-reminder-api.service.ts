import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface ReminderSettings {
  remindersEnabled: boolean; reminderBeforeDays: number; overdueFirstDays: number; overdueRepeatDays: number; overdueMaxReminders: number; lateFeeEnabled: boolean; lateFeeAuto: boolean;
  lateFeeGraceDays: number; lateFeeType: 'PERCENT' | 'FIXED'; lateFeeValue: number | null; lateFeeMax: number | null; lastRun: string | null; lastSummary: string | null; mailReady: boolean;
}
export interface ReminderSettingsPayload {
  remindersEnabled?: boolean; reminderBeforeDays?: number; overdueFirstDays?: number; overdueRepeatDays?: number; overdueMaxReminders?: number; lateFeeEnabled?: boolean; lateFeeAuto?: boolean;
  lateFeeGraceDays?: number; lateFeeType?: string; lateFeeValue?: number; lateFeeMax?: number; clearLateFeeMax?: boolean;
}
export interface PlanRow {
  chargeId: number; leaseId: number; leaseCode: string | null; tenantName: string | null; tenantEmail: string | null; unit: string; invoiceNumber: string | null; dueDate: string;
  outstanding: number; daysOverdue: number | null; reminder: 'BEFORE' | 'OVERDUE' | null; lateFee: number | null; problem: string | null;
}
export interface ReminderRun { reminded: number; fees: number; skipped: number; failed: number; notes: string[]; }
export interface ReminderSent { sentTo: string; }
export interface LateFee { chargeId: number; amount: number; invoiceNumber: string; chargedOn: string; }

/** Rent reminders to tenants and late fees on overdue rent. */
@Injectable({ providedIn: 'root' })
export class PmReminderApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  settings(): Observable<ReminderSettings> { return this.http.get<ReminderSettings>(`${this.base}/reminders/settings`); }
  saveSettings(p: ReminderSettingsPayload): Observable<ReminderSettings> { return this.http.put<ReminderSettings>(`${this.base}/reminders/settings`, p); }
  due(): Observable<PlanRow[]> { return this.http.get<PlanRow[]>(`${this.base}/reminders/due`); }
  run(): Observable<ReminderRun> { return this.http.post<ReminderRun>(`${this.base}/reminders/run`, {}); }
  remind(chargeId: number, p?: { email?: string; message?: string }): Observable<ReminderSent> { return this.http.post<ReminderSent>(`${this.base}/rent/${chargeId}/remind`, p ?? {}); }
  lateFee(chargeId: number): Observable<LateFee> { return this.http.post<LateFee>(`${this.base}/rent/${chargeId}/late-fee`, {}); }
}
