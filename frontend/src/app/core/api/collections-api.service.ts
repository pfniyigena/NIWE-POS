import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface Aging { current: number; days1to30: number; days31to60: number; days61to90: number; over90: number; }

export interface CollectionItem {
  kind: 'INVOICE' | 'POS_SALE';
  documentId: number;
  number: string | null;
  customerId: number;
  customerName: string;
  customerEmail: string | null;
  customerPhone: string | null;
  date: string;
  dueDate: string;
  dueAssumed: boolean;
  total: number;
  paid: number;
  outstanding: number;
  daysOverdue: number;
  bucket: string;
  branchId: number | null;
}

export interface CollectionSummary {
  totalOutstanding: number;
  totalOverdue: number;
  overdueItems: number;
  customersOwing: number;
  customersOverdue: number;
  aging: Aging;
}

export interface ItemsPage { rows: CollectionItem[]; total: number; summary: CollectionSummary; canRemind: boolean; mailReady: boolean; defaultTermDays: number; }

export interface CollectionCustomer {
  customerId: number;
  name: string;
  email: string | null;
  phone: string | null;
  items: number;
  overdueItems: number;
  outstanding: number;
  overdue: number;
  oldestDaysOverdue: number;
  aging: Aging;
  remindersSent: number;
  lastRemindedAt: string | null;
  creditEnabled: boolean;
}

export interface CustomersPage { rows: CollectionCustomer[]; total: number; summary: CollectionSummary; canRemind: boolean; mailReady: boolean; }

export interface ReminderItem { customerId: number; customerName: string; email: string | null; items: number; outstanding: number; problem: string | null; }
export interface RemindResult { mails: number; items: number; skipped: number; customers: ReminderItem[]; }
export interface CollectionSettings { autoEnabled: boolean; firstDays: number; repeatDays: number; maxReminders: number; defaultTermDays: number; mailReady: boolean; }
export interface CollectionLogRow { id: number; customerId: number; customerName: string; kind: string; email: string | null; items: number; amount: number; sentBy: string | null; sentAt: string; }

export interface CollectionQuery { overdueOnly: boolean; q: string; branchId: number | null; bucket?: string; customerId?: number | null; page: number; size: number; }

/** Customer collections: what customers owe and how late, statements, payment reminders. */
@Injectable({ providedIn: 'root' })
export class CollectionsApiService {
  constructor(private http: HttpClient) {}

  private params(f: CollectionQuery): HttpParams {
    let p = new HttpParams().set('overdueOnly', String(f.overdueOnly)).set('page', String(f.page)).set('size', String(f.size));
    if (f.q.trim()) p = p.set('q', f.q.trim());
    if (f.branchId != null) p = p.set('branchId', String(f.branchId));
    if (f.bucket) p = p.set('bucket', f.bucket);
    if (f.customerId != null) p = p.set('customerId', String(f.customerId));
    return p;
  }

  items(f: CollectionQuery): Observable<ItemsPage> {
    return this.http.get<ItemsPage>(`${API_BASE}/collections`, { params: this.params(f) });
  }

  customers(f: CollectionQuery): Observable<CustomersPage> {
    return this.http.get<CustomersPage>(`${API_BASE}/collections/customers`, { params: this.params(f).delete('bucket').delete('customerId') });
  }

  statementPdf(customerId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/collections/customers/${customerId}/statement`, { responseType: 'blob' });
  }

  emailStatement(customerId: number): Observable<{ email: string; message: string }> {
    return this.http.post<{ email: string; message: string }>(`${API_BASE}/collections/customers/${customerId}/statement/email`, {});
  }

  remind(customerIds: number[]): Observable<RemindResult> {
    return this.http.post<RemindResult>(`${API_BASE}/collections/remind`, { customerIds });
  }

  log(customerId?: number | null): Observable<CollectionLogRow[]> {
    let p = new HttpParams();
    if (customerId != null) p = p.set('customerId', String(customerId));
    return this.http.get<CollectionLogRow[]>(`${API_BASE}/collections/log`, { params: p });
  }

  settings(): Observable<CollectionSettings> {
    return this.http.get<CollectionSettings>(`${API_BASE}/collections/settings`);
  }

  saveSettings(s: Partial<CollectionSettings>): Observable<CollectionSettings> {
    return this.http.put<CollectionSettings>(`${API_BASE}/collections/settings`, s);
  }

  preview(): Observable<ReminderItem[]> {
    return this.http.get<ReminderItem[]>(`${API_BASE}/collections/reminders`);
  }

  runNow(): Observable<RemindResult> {
    return this.http.post<RemindResult>(`${API_BASE}/collections/reminders/run`, {});
  }

  /** The current screen as a file; `kind` is collections (the items) or collection-customers. */
  export(kind: 'collections' | 'collection-customers', f: CollectionQuery, format: string): Observable<Blob> {
    let p = this.params(f).delete('page').delete('size').set('format', format);
    if (kind === 'collection-customers') p = p.delete('bucket').delete('customerId');
    return this.http.get(`${API_BASE}/export/${kind}`, { params: p, responseType: 'blob' });
  }
}
