import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Account, AccountPayload, AccountType, LedgerLine } from '../models';

/** Admin > Accounting > Chart of Accounts. Admin-only, same as every other Accounting endpoint. */
@Injectable({ providedIn: 'root' })
export class AccountApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Account[]> {
    return this.http.get<Account[]>(`${API_BASE}/accounting/accounts`);
  }

  listActive(): Observable<Account[]> {
    return this.http.get<Account[]>(`${API_BASE}/accounting/accounts/active`);
  }

  /** The Expense/Income entry screens' own category picker — active accounts of exactly one type. */
  listActiveByType(type: AccountType): Observable<Account[]> {
    return this.http.get<Account[]>(`${API_BASE}/accounting/accounts/active/by-type/${type}`);
  }

  getById(id: number): Observable<Account> {
    return this.http.get<Account>(`${API_BASE}/accounting/accounts/${id}`);
  }

  create(payload: AccountPayload): Observable<Account> {
    return this.http.post<Account>(`${API_BASE}/accounting/accounts`, payload);
  }

  /** Deactivating a systemAccount is rejected server-side — see backend AccountService.update's javadoc. */
  update(id: number, payload: AccountPayload): Observable<Account> {
    return this.http.put<Account>(`${API_BASE}/accounting/accounts/${id}`, payload);
  }

  /** One account's own dated, running-balance ledger. */
  ledger(id: number, from: string | null, to: string | null): Observable<LedgerLine[]> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.http.get<LedgerLine[]>(`${API_BASE}/accounting/reports/ledger/${id}`, { params });
  }
}
