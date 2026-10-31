import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import {
  Expense, ExpenseBudget, ExpenseBudgetStatus, ExpenseBulkResult, ExpenseEvent, ExpenseFile, ExpenseFilter, ExpensePayload, ExpensePayment,
  ExpensePaymentAccount, ExpenseSettings, ExpenseSummary,
} from '../models';

/** Admin > Accounting > Expenses — the full lifecycle (log, edit, approve/reject, void, pay), files, history, budgets, rules and the overview. */
@Injectable({ providedIn: 'root' })
export class ExpenseApiService {
  constructor(private http: HttpClient) {}

  private base = `${API_BASE}/accounting/expenses`;

  listAll(): Observable<Expense[]> {
    return this.http.get<Expense[]>(this.base);
  }

  /** Server-side filtering: status, category, search text, dates, amounts, and views (pending, unpaid, overdue, due-soon, mine). */
  list(f: ExpenseFilter): Observable<Expense[]> {
    let params = new HttpParams();
    Object.entries(f).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') params = params.set(k, String(v));
    });
    return this.http.get<Expense[]>(this.base, { params });
  }

  getById(id: number): Observable<Expense> {
    return this.http.get<Expense>(`${this.base}/${id}`);
  }

  create(payload: ExpensePayload): Observable<Expense> {
    return this.http.post<Expense>(this.base, payload);
  }

  /** Only while PENDING — see backend ExpenseService.update. */
  update(id: number, payload: ExpensePayload): Observable<Expense> {
    return this.http.put<Expense>(`${this.base}/${id}`, payload);
  }

  /** Only a pending or rejected expense. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** `overrideBudget` (administrators) goes ahead when blocking over-budget expenses is switched on. */
  approve(id: number, overrideBudget = false): Observable<Expense> {
    return this.http.post<Expense>(`${this.base}/${id}/approve`, { overrideBudget });
  }

  reject(id: number, reason: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${this.base}/${id}/reject`, { reason });
  }

  voidExpense(id: number, reason: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${this.base}/${id}/void`, { reason });
  }

  bulk(ids: number[], action: 'APPROVE' | 'REJECT', reason?: string | null): Observable<ExpenseBulkResult> {
    return this.http.post<ExpenseBulkResult>(`${this.base}/bulk`, { ids, action, reason: reason ?? null });
  }

  /** Pays the rest of a bill in one go. */
  settle(id: number, settledDate: string | null): Observable<Expense> {
    return this.http.post<Expense>(`${this.base}/${id}/settle`, { settledDate });
  }

  /** A full or part payment on an approved unpaid bill. */
  pay(id: number, body: { amount: number; paidOn?: string | null; paidFromAccountId?: number | null; note?: string | null }): Observable<Expense> {
    return this.http.post<Expense>(`${this.base}/${id}/pay`, body);
  }

  payments(id: number): Observable<ExpensePayment[]> {
    return this.http.get<ExpensePayment[]>(`${this.base}/${id}/payments`);
  }

  events(id: number): Observable<ExpenseEvent[]> {
    return this.http.get<ExpenseEvent[]>(`${this.base}/${id}/events`);
  }

  paymentAccounts(): Observable<ExpensePaymentAccount[]> {
    return this.http.get<ExpensePaymentAccount[]>(`${this.base}/payment-accounts`);
  }

  summary(): Observable<ExpenseSummary> {
    return this.http.get<ExpenseSummary>(`${this.base}/summary`);
  }

  files(id: number): Observable<ExpenseFile[]> {
    return this.http.get<ExpenseFile[]>(`${this.base}/${id}/files`);
  }

  addFile(id: number, file: File): Observable<ExpenseFile> {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<ExpenseFile>(`${this.base}/${id}/files`, fd);
  }

  fileBlob(id: number, fileId: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/files/${fileId}`, { responseType: 'blob' });
  }

  deleteFile(id: number, fileId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/files/${fileId}`);
  }

  /** The register report as a download (xlsx or pdf) for the dates and status chosen on the list. */
  registerExport(format: 'xlsx' | 'pdf', from: string, to: string, status: string | null): Observable<Blob> {
    let params = new HttpParams().set('from', from).set('to', to).set('format', format);
    if (status) params = params.set('status', status);
    return this.http.get(`${API_BASE}/reports/expense-register`, { params, responseType: 'blob' });
  }

  getSettings(): Observable<ExpenseSettings> {
    return this.http.get<ExpenseSettings>(`${API_BASE}/accounting/expense-settings`);
  }

  saveSettings(s: Partial<ExpenseSettings>): Observable<ExpenseSettings> {
    return this.http.put<ExpenseSettings>(`${API_BASE}/accounting/expense-settings`, s);
  }

  budgets(year: number): Observable<ExpenseBudget[]> {
    return this.http.get<ExpenseBudget[]>(`${API_BASE}/accounting/expense-budgets`, { params: { year } });
  }

  budgetStatus(year: number, month: number): Observable<ExpenseBudgetStatus[]> {
    return this.http.get<ExpenseBudgetStatus[]>(`${API_BASE}/accounting/expense-budgets/status`, { params: { year, month } });
  }

  createBudget(b: { accountId?: number | null; costCenterId?: number | null; year: number; monthlyAmount: number; note?: string | null }): Observable<ExpenseBudget> {
    return this.http.post<ExpenseBudget>(`${API_BASE}/accounting/expense-budgets`, b);
  }

  updateBudget(id: number, b: { monthlyAmount: number; note?: string | null }): Observable<ExpenseBudget> {
    return this.http.put<ExpenseBudget>(`${API_BASE}/accounting/expense-budgets/${id}`, b);
  }

  deleteBudget(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/accounting/expense-budgets/${id}`);
  }
}
