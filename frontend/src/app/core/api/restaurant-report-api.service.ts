import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DayCloseRow, EndOfDay, EodCashEvent, RestaurantAnalysis } from '../models';
import { getPaged, PageResult } from './paged';
import { downloadBlob } from '../download-file';

/** Sales analysis, the end-of-day cash-up and paid in / paid out entries. */
@Injectable({ providedIn: 'root' })
export class RestaurantReportApiService {
  private base = `${API_BASE}/restaurant`;

  constructor(private http: HttpClient) {}

  analysis(from: string, to: string): Observable<RestaurantAnalysis> {
    return this.http.get<RestaurantAnalysis>(`${this.base}/reports/analysis`, { params: new HttpParams().set('from', from).set('to', to) });
  }

  endOfDay(date: string, openingFloat?: number | null): Observable<EndOfDay> {
    let p = new HttpParams().set('date', date);
    if (openingFloat !== null && openingFloat !== undefined) p = p.set('openingFloat', String(openingFloat));
    return this.http.get<EndOfDay>(`${this.base}/reports/end-of-day`, { params: p });
  }

  close(body: { date: string; cashCounted: number; floatKept?: number | null; openingFloat?: number | null; notes?: string | null; closeWithOpenOrders?: boolean }): Observable<EndOfDay> {
    return this.http.post<EndOfDay>(`${this.base}/reports/end-of-day/close`, body);
  }

  reopen(date: string, reason: string): Observable<EndOfDay> {
    return this.http.post<EndOfDay>(`${this.base}/reports/end-of-day/${date}/reopen`, { reason });
  }

  history(page: number, size: number): Observable<PageResult<DayCloseRow>> {
    return getPaged<DayCloseRow>(this.http, `${this.base}/reports/end-of-day/history`, new HttpParams().set('page', page).set('size', size));
  }

  addCash(kind: string, amount: number, reason: string): Observable<EodCashEvent> {
    return this.http.post<EodCashEvent>(`${this.base}/cash-events`, { kind, amount, reason });
  }

  removeCash(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/cash-events/${id}`);
  }

  /** Downloads one analysis table, the Z-report ("end-of-day") or the closed days ("history"). */
  download(what: string, params: Record<string, string>, format: 'xlsx' | 'pdf' | 'csv', done: (error: boolean) => void): void {
    const path = what === 'end-of-day' ? 'reports/end-of-day/export' : what === 'history' ? 'reports/end-of-day/history/export' : 'reports/analysis/export';
    let p = new HttpParams().set('format', format);
    if (what !== 'end-of-day' && what !== 'history') p = p.set('table', what);
    for (const [k, v] of Object.entries(params)) if (v) p = p.set(k, v);
    this.http.get(`${this.base}/${path}`, { params: p, responseType: 'blob' }).subscribe({
      next: (blob) => {
        downloadBlob(blob, `restaurant-${what}.${format}`);
        done(false);
      },
      error: () => done(true),
    });
  }
}
