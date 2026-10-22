import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type StatementStatus = 'DRAFT' | 'FINAL' | 'PAID';
export type StatementLineKind = 'RENT' | 'FEE' | 'VAT' | 'REPAIR' | 'ADJUSTMENT';

export const STATEMENT_STATUS_LABEL: Record<StatementStatus, string> = { DRAFT: 'Draft', FINAL: 'Agreed, to pay', PAID: 'Paid' };
export function statementBadge(s: string): string { return s === 'PAID' ? 'badge-success' : s === 'FINAL' ? 'badge-warning' : 'badge-secondary'; }

export interface StatementRow {
  id: number; code: string; ownerId: number; ownerName: string | null; periodFrom: string; periodTo: string; status: StatementStatus; rentCollected: number; managementFee: number; vat: number;
  repairs: number; adjustments: number; netPayable: number; paidDate: string | null;
}
export interface StatementLine {
  id: number; kind: StatementLineKind; propertyId: number | null; propertyName: string | null; unitId: number | null; unitNo: string | null; tenantId: number | null; tenantName: string | null;
  date: string; description: string; reference: string | null; baseAmount: number | null; feePercent: number | null; amount: number; editable: boolean;
}
export interface StatementPropertyRow { propertyId: number | null; propertyName: string; rent: number; feePercent: number | null; fee: number; vat: number; repairs: number; adjustments: number; net: number; }
export interface StatementDto {
  statement: StatementRow; notes: string | null; lines: StatementLine[]; properties: StatementPropertyRow[]; warnings: string[]; ownerPhone: string | null; ownerBankName: string | null;
  ownerBankAccount: string | null; finalizedDate: string | null; finalizedByName: string | null; paidMethod: string | null; paidReference: string | null; canManage: boolean;
  createdByName: string | null; createdAt: string; postedToAccounts: boolean;
  ownerEmail: string | null; emailedAt: string | null; emailedTo: string | null;
}
export interface AutoRunResult { ran: boolean; message: string; periodFrom: string; periodTo: string; created: number; agreed: number; emailed: number; notes: string[]; }
export interface EmailAllResult { sent: number; skipped: number; failed: number; notes: string[]; }
export interface GenerateAllResult { created: number; skipped: number; notes: string[]; statements: StatementRow[]; }
export interface AdjustmentPayload { propertyId?: number | null; description: string; amount: number; date?: string | null; }
export interface PayPayload { paidDate?: string | null; method?: string | null; reference?: string; }

@Injectable({ providedIn: 'root' })
export class PmStatementApiService {
  private base = `${API_BASE}/propertymgmt/statements`;

  constructor(private http: HttpClient) {}

  list(f: { ownerId?: number | null; status?: string; from?: string; to?: string; unpaidOnly?: boolean }): Observable<StatementRow[]> {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
    return this.http.get<StatementRow[]>(this.base, { params: p });
  }
  get(id: number): Observable<StatementDto> { return this.http.get<StatementDto>(`${this.base}/${id}`); }
  generate(ownerId: number, periodFrom: string, periodTo: string, notes?: string): Observable<StatementDto> { return this.http.post<StatementDto>(this.base, { ownerId, periodFrom, periodTo, notes }); }
  generateAll(periodFrom: string, periodTo: string): Observable<GenerateAllResult> { return this.http.post<GenerateAllResult>(`${this.base}/generate-all`, { periodFrom, periodTo }); }
  refresh(id: number): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/refresh`, {}); }
  addLine(id: number, p: AdjustmentPayload): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/lines`, p); }
  removeLine(id: number, lineId: number): Observable<StatementDto> { return this.http.delete<StatementDto>(`${this.base}/${id}/lines/${lineId}`); }
  notes(id: number, notes: string): Observable<StatementDto> { return this.http.put<StatementDto>(`${this.base}/${id}/notes`, { notes }); }
  finalize(id: number): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/finalize`, {}); }
  reopen(id: number): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/reopen`, {}); }
  pay(id: number, p: PayPayload): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/pay`, p); }
  email(id: number, p: { to: string | null; message: string | null }): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/email`, p); }
  emailAll(p: { includePaid: boolean; resend: boolean; ids: number[] | null }): Observable<EmailAllResult> { return this.http.post<EmailAllResult>(`${this.base}/email-all`, p); }
  autoRun(force: boolean): Observable<AutoRunResult> { return this.http.post<AutoRunResult>(`${this.base}/auto-run?force=${force}`, {}); }
  unpay(id: number): Observable<StatementDto> { return this.http.post<StatementDto>(`${this.base}/${id}/unpay`, {}); }
  remove(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`); }
  file(id: number, format: 'pdf' | 'xlsx'): Observable<Blob> { return this.http.get(`${this.base}/${id}/export`, { params: { format }, responseType: 'blob' }); }
}
