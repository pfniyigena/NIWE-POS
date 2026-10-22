import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE } from '../constants';
import { downloadBlob } from '../download-file';
import { PageParams, PageResult, getPaged } from './paged';

export type FeeInvoiceStatus = 'ISSUED' | 'VOID';
export const FEE_INVOICE_STATUS_LABEL: Record<FeeInvoiceStatus, string> = { ISSUED: 'Issued', VOID: 'Void' };
export function feeInvoiceBadge(s: string): string { return s === 'ISSUED' ? 'badge-success' : 'badge-secondary'; }

export interface FeeInvoiceRow {
  id: number; number: string; status: FeeInvoiceStatus; issueDate: string; ownerId: number; ownerName: string; ownerTaxNumber: string | null; statementId: number; statementCode: string;
  periodFrom: string; periodTo: string; netAmount: number; vatPercent: number | null; vatAmount: number; totalAmount: number;
}
export interface FeeInvoiceLine { id: number; propertyId: number | null; propertyName: string | null; description: string; baseAmount: number | null; feePercent: number | null; netAmount: number; vatAmount: number; total: number; }
export interface FeeInvoice {
  invoice: FeeInvoiceRow; taxInvoice: boolean; ownerAddress: string | null; sellerName: string | null; sellerTaxId: string | null; sellerAddress: string | null; sellerPhone: string | null; sellerEmail: string | null;
  issuedByName: string | null; voidReason: string | null; voidedAt: string | null; lines: FeeInvoiceLine[]; statementExists: boolean; canManage: boolean;
}
export interface FeeInvoiceTotals { count: number; net: number; vat: number; total: number; }
export interface FeeInvoiceFilter extends PageParams { ownerId?: number | null; status?: string; from?: string; to?: string; q?: string; }

function params(f: object): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
  return p;
}

/** The invoices to owners for the management fee: one per agreed statement, a document only (the statement books the money). */
@Injectable({ providedIn: 'root' })
export class PmFeeInvoiceApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  list(f: FeeInvoiceFilter): Observable<PageResult<FeeInvoiceRow>> { return getPaged<FeeInvoiceRow>(this.http, `${this.base}/fee-invoices`, params(f)); }
  totals(f: FeeInvoiceFilter): Observable<FeeInvoiceTotals> { return this.http.get<FeeInvoiceTotals>(`${this.base}/fee-invoices/totals`, { params: params({ ownerId: f.ownerId, from: f.from, to: f.to, q: f.q }) }); }
  get(id: number): Observable<FeeInvoice> { return this.http.get<FeeInvoice>(`${this.base}/fee-invoices/${id}`); }
  /** The invoice of a statement, or null when it has none (the server answers 204). */
  forStatement(statementId: number): Observable<FeeInvoice | null> { return this.http.get<FeeInvoice | null>(`${this.base}/statements/${statementId}/fee-invoice`); }
  issue(statementId: number): Observable<FeeInvoice> { return this.http.post<FeeInvoice>(`${this.base}/statements/${statementId}/fee-invoice`, {}); }
  pdf(id: number, number: string): Observable<void> {
    return this.http.get(`${this.base}/fee-invoices/${id}/pdf`, { responseType: 'blob' }).pipe(map((b) => downloadBlob(b, `${number.toLowerCase()}.pdf`)));
  }
}
