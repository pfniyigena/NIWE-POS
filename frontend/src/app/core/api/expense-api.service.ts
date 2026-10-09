import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Expense, ExpensePayload } from '../models';

/** Admin > Accounting > Expenses — full lifecycle (log, edit, approve/reject, void, settle) for a dedicated expense entry. Admin-only, with approve/reject/void additionally open to PERM_APPROVE_EXPENSE. */
@Injectable({ providedIn: 'root' })
export class ExpenseApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Expense[]> {
    return this.http.get<Expense[]>(`${API_BASE}/accounting/expenses`);
  }

  getById(id: number): Observable<Expense> {
    return this.http.get<Expense>(`${API_BASE}/accounting/expenses/${id}`);
  }

  create(payload: ExpensePayload): Observable<Expense> {
    return this.http.post<Expense>(`${API_BASE}/accounting/expenses`, payload);
  }

  /** Only while PENDING — see backend ExpenseService.update. */
  update(id: number, payload: ExpensePayload): Observable<Expense> {
    return this.http.put<Expense>(`${API_BASE}/accounting/expenses/${id}`, payload);
  }

  approve(id: number): Observable<Expense> {
    return this.http.post<Expense>(`${API_BASE}/accounting/expenses/${id}/approve`, {});
  }

  reject(id: number, reason: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${API_BASE}/accounting/expenses/${id}/reject`, { reason });
  }

  voidExpense(id: number, reason: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${API_BASE}/accounting/expenses/${id}/void`, { reason });
  }

  /** Records that an APPROVED, still-unpaid bill has since been paid off — see backend ExpenseService.settle. */
  settle(id: number, settledDate: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${API_BASE}/accounting/expenses/${id}/settle`, { settledDate });
  }
}
