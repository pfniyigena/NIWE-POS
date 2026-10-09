import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Income, IncomePayload } from '../models';

/** Admin > Accounting > Incomes — ExpenseApiService's mirror image (see backend AccountingService.postIncome). Admin-only. */
@Injectable({ providedIn: 'root' })
export class IncomeApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Income[]> {
    return this.http.get<Income[]>(`${API_BASE}/accounting/incomes`);
  }

  getById(id: number): Observable<Income> {
    return this.http.get<Income>(`${API_BASE}/accounting/incomes/${id}`);
  }

  create(payload: IncomePayload): Observable<Income> {
    return this.http.post<Income>(`${API_BASE}/accounting/incomes`, payload);
  }
}
