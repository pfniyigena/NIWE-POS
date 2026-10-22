import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface AccountLine { date: string; kind: 'RENT' | 'PAYMENT'; description: string; reference: string | null; charge: number | null; payment: number | null; balance: number; paymentId: number | null; }
export interface TenantAccount {
  tenantId: number; tenantCode: string; tenantName: string; tenantEmail: string | null; from: string; to: string; opening: number; lines: AccountLine[];
  charged: number; paid: number; closing: number; overdue: number;
}
export interface PaymentRow {
  paymentId: number; receiptNumber: string | null; paidDate: string; method: string | null; reference: string | null; amount: number; tenantId: number | null; tenantName: string | null;
  leaseId: number | null; leaseCode: string | null; unit: string | null; invoices: string[];
}
export interface MailSent { sentTo: string; }

/** A tenant's account statement and rent receipts: to read, to download as PDF, and to e-mail to the tenant. */
@Injectable({ providedIn: 'root' })
export class PmAccountApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  private range(from?: string, to?: string): HttpParams {
    let p = new HttpParams();
    if (from) p = p.set('from', from);
    if (to) p = p.set('to', to);
    return p;
  }

  account(tenantId: number, from?: string, to?: string): Observable<TenantAccount> { return this.http.get<TenantAccount>(`${this.base}/tenants/${tenantId}/account`, { params: this.range(from, to) }); }
  accountPdf(tenantId: number, from?: string, to?: string): Observable<Blob> { return this.http.get(`${this.base}/tenants/${tenantId}/account.pdf`, { params: this.range(from, to), responseType: 'blob' }); }
  emailAccount(tenantId: number, p: { from?: string; to?: string; email?: string; message?: string }): Observable<MailSent> { return this.http.post<MailSent>(`${this.base}/tenants/${tenantId}/account/email`, p); }

  payments(f: { leaseId?: number; tenantId?: number }): Observable<PaymentRow[]> {
    let p = new HttpParams();
    if (f.leaseId) p = p.set('leaseId', f.leaseId);
    if (f.tenantId) p = p.set('tenantId', f.tenantId);
    return this.http.get<PaymentRow[]>(`${this.base}/payments`, { params: p });
  }
  receiptPdf(paymentId: number): Observable<Blob> { return this.http.get(`${this.base}/payments/${paymentId}/receipt.pdf`, { responseType: 'blob' }); }
  emailReceipt(paymentId: number, p: { email?: string; message?: string }): Observable<MailSent> { return this.http.post<MailSent>(`${this.base}/payments/${paymentId}/receipt/email`, p); }
}
