import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { RestaurantOrder, WaitlistEntry } from '../models';

export interface WaitlistPayload {
  guestName: string;
  guestPhone?: string | null;
  partySize: number;
  notes?: string | null;
  /** Minutes promised; blank = estimated from the tables in use. */
  quotedMinutes?: number | null;
}

/** Restaurant > Waiting list: walk-in parties waiting for a table. */
@Injectable({ providedIn: 'root' })
export class WaitlistApiService {
  constructor(private http: HttpClient) {}

  /** One page; the status filter is blank for "still waiting", ALL for everyone, or one status. */
  page(f: ListFilter, page: number, size: number): Observable<PageResult<WaitlistEntry>> {
    return pagedList<WaitlistEntry>(this.http, `${API_BASE}/restaurant/waitlist`, f, page, size);
  }

  add(payload: WaitlistPayload): Observable<WaitlistEntry> {
    return this.http.post<WaitlistEntry>(`${API_BASE}/restaurant/waitlist`, payload);
  }

  notify(id: number): Observable<WaitlistEntry> {
    return this.http.post<WaitlistEntry>(`${API_BASE}/restaurant/waitlist/${id}/notify`, {});
  }

  seat(id: number, tableId: number): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/waitlist/${id}/seat`, { tableId });
  }

  leave(id: number): Observable<WaitlistEntry> {
    return this.http.post<WaitlistEntry>(`${API_BASE}/restaurant/waitlist/${id}/leave`, {});
  }
}
