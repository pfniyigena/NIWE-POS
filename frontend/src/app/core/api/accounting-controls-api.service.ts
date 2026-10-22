import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface LockLine { previousDate: string | null; newDate: string | null; changedBy: string | null; reason: string | null; at: string; }
export interface LockState { lockDate: string | null; updatedBy: string | null; updatedAt: string | null; history: LockLine[]; }

export interface MoneyAccount { id: number; code: string; name: string; }

export interface ReconRow {
  id: number;
  reconciliationNumber: string;
  accountId: number;
  accountCode: string;
  accountName: string;
  statementDate: string;
  statementBalance: number;
  status: 'DRAFT' | 'DONE';
  ticked: number;
  difference: number;
  note: string | null;
  createdBy: string | null;
  completedBy: string | null;
  completedAt: string | null;
  canUndo: boolean;
}

export interface ReconLine { lineId: number; date: string; entryNumber: string; memo: string | null; description: string | null; debit: number; credit: number; ticked: boolean; }

export interface ReconDetail {
  header: ReconRow;
  clearedBefore: number;
  tickedDeposits: number;
  tickedWithdrawals: number;
  cleared: number;
  difference: number;
  lines: ReconLine[];
  truncated: boolean;
}

export interface OpeningLineView { accountId: number; accountCode: string; accountName: string; debit: number; credit: number; }
export interface OpeningState { recorded: boolean; entryId: number | null; entryNumber: string | null; asOf: string | null; totalDebit: number; totalCredit: number; lines: OpeningLineView[]; }
export interface OpeningLinePayload { accountId: number; debit: number | null; credit: number | null; }

/** Accounting > Controls: the closing date, bank reconciliation and the opening balances of the ledger. See ControlsController on the backend. */
@Injectable({ providedIn: 'root' })
export class AccountingControlsApiService {
  private base = `${API_BASE}/accounting/controls`;

  constructor(private http: HttpClient) {}

  lock(): Observable<LockState> {
    return this.http.get<LockState>(`${this.base}/lock`);
  }

  setLock(lockDate: string | null, reason: string): Observable<LockState> {
    return this.http.put<LockState>(`${this.base}/lock`, { lockDate, reason });
  }

  moneyAccounts(): Observable<MoneyAccount[]> {
    return this.http.get<MoneyAccount[]>(`${this.base}/money-accounts`);
  }

  reconciliations(): Observable<ReconRow[]> {
    return this.http.get<ReconRow[]>(`${this.base}/reconciliations`);
  }

  reconciliation(id: number, from: string | null): Observable<ReconDetail> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    return this.http.get<ReconDetail>(`${this.base}/reconciliations/${id}`, { params });
  }

  startReconciliation(accountId: number, statementDate: string, statementBalance: number, note: string | null): Observable<ReconDetail> {
    return this.http.post<ReconDetail>(`${this.base}/reconciliations`, { accountId, statementDate, statementBalance, note });
  }

  updateReconciliation(id: number, statementDate: string | null, statementBalance: number | null, note: string | null): Observable<ReconDetail> {
    return this.http.put<ReconDetail>(`${this.base}/reconciliations/${id}`, { statementDate, statementBalance, note });
  }

  tick(id: number, lineIds: number[], ticked: boolean): Observable<ReconDetail> {
    return this.http.post<ReconDetail>(`${this.base}/reconciliations/${id}/tick`, { lineIds, ticked });
  }

  tickAll(id: number, from: string | null, ticked: boolean): Observable<ReconDetail> {
    return this.http.post<ReconDetail>(`${this.base}/reconciliations/${id}/tick-all`, { from, to: null, ticked });
  }

  adjustment(id: number, otherAccountId: number, amount: number, direction: 'OUT' | 'IN', date: string | null, memo: string | null): Observable<ReconDetail> {
    return this.http.post<ReconDetail>(`${this.base}/reconciliations/${id}/adjustment`, { otherAccountId, amount, direction, date, memo });
  }

  finish(id: number): Observable<ReconDetail> {
    return this.http.post<ReconDetail>(`${this.base}/reconciliations/${id}/finish`, {});
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/reconciliations/${id}`);
  }

  undo(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/reconciliations/${id}/undo`, {});
  }

  opening(): Observable<OpeningState> {
    return this.http.get<OpeningState>(`${this.base}/opening-balances`);
  }

  recordOpening(asOf: string, balanceToEquity: boolean, memo: string | null, lines: OpeningLinePayload[]): Observable<OpeningState> {
    return this.http.post<OpeningState>(`${this.base}/opening-balances`, { asOf, balanceToEquity, memo, lines });
  }

  reverseOpening(): Observable<OpeningState> {
    return this.http.post<OpeningState>(`${this.base}/opening-balances/reverse`, {});
  }
}
