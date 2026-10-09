import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { RecurringExpenseTemplate, RecurringExpenseTemplatePayload } from '../models';

/** Admin > Accounting > Recurring expenses — ADMIN-only templates a daily scheduled sweep turns into new PENDING Expenses (see backend RecurringExpenseTemplateService). */
@Injectable({ providedIn: 'root' })
export class RecurringExpenseApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<RecurringExpenseTemplate[]> {
    return this.http.get<RecurringExpenseTemplate[]>(`${API_BASE}/accounting/recurring-expenses`);
  }

  getById(id: number): Observable<RecurringExpenseTemplate> {
    return this.http.get<RecurringExpenseTemplate>(`${API_BASE}/accounting/recurring-expenses/${id}`);
  }

  create(payload: RecurringExpenseTemplatePayload): Observable<RecurringExpenseTemplate> {
    return this.http.post<RecurringExpenseTemplate>(`${API_BASE}/accounting/recurring-expenses`, payload);
  }

  update(id: number, payload: RecurringExpenseTemplatePayload): Observable<RecurringExpenseTemplate> {
    return this.http.put<RecurringExpenseTemplate>(`${API_BASE}/accounting/recurring-expenses/${id}`, payload);
  }

  setActive(id: number, active: boolean): Observable<RecurringExpenseTemplate> {
    return this.http.post<RecurringExpenseTemplate>(`${API_BASE}/accounting/recurring-expenses/${id}/active`, { active });
  }

  /** Generates this template's next expense right now, without waiting for the daily scheduled sweep. */
  runNow(id: number): Observable<RecurringExpenseTemplate> {
    return this.http.post<RecurringExpenseTemplate>(`${API_BASE}/accounting/recurring-expenses/${id}/run-now`, {});
  }
}
