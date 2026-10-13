import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface ReportSchedule {
  id: number;
  name: string;
  reportKey: string;
  reportLabel: string;
  format: 'XLSX' | 'PDF';
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  runTime: string;
  dayOfWeek: number | null;
  dayOfMonth: number | null;
  recipients: string | null;
  categoryId: number | null;
  warehouseId: number | null;
  branchId: number | null;
  dayWindow: number | null;
  active: boolean;
  lastRunAt: string | null;
  lastStatus: 'OK' | 'FAILED' | 'SKIPPED' | null;
  lastMessage: string | null;
}

export interface ReportSchedulePayload {
  name: string;
  reportKey: string;
  format: string;
  frequency: string;
  runTime: string;
  dayOfWeek?: number | null;
  dayOfMonth?: number | null;
  recipients?: string | null;
  categoryId?: number | null;
  warehouseId?: number | null;
  branchId?: number | null;
  dayWindow?: number | null;
  active: boolean;
}

/** One schedulable report and which optional filters apply to it. */
export interface ReportOption {
  key: string;
  label: string;
  section: string;
  periodic: boolean;
  categoryFilter: boolean;
  warehouseFilter: boolean;
  branchFilter: boolean;
  daysFilter: boolean;
  defaultDays: number | null;
}

@Injectable({ providedIn: 'root' })
export class ReportScheduleApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<ReportSchedule[]> {
    return this.http.get<ReportSchedule[]>(`${API_BASE}/report-schedules`);
  }

  reports(): Observable<ReportOption[]> {
    return this.http.get<ReportOption[]>(`${API_BASE}/report-schedules/reports`);
  }

  create(payload: ReportSchedulePayload): Observable<ReportSchedule> {
    return this.http.post<ReportSchedule>(`${API_BASE}/report-schedules`, payload);
  }

  update(id: number, payload: ReportSchedulePayload): Observable<ReportSchedule> {
    return this.http.put<ReportSchedule>(`${API_BASE}/report-schedules/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/report-schedules/${id}`);
  }

  /** Sends the report now, whatever its schedule. */
  run(id: number): Observable<ReportSchedule> {
    return this.http.post<ReportSchedule>(`${API_BASE}/report-schedules/${id}/run`, {});
  }
}
