import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';
import { DiningTable, Reservation, ReservationReminder, ReservationSlot, RestaurantOrder } from '../models';

export interface ReservationPayload {
  guestName: string;
  guestPhone?: string | null;
  partySize: number;
  /** ISO instant. */
  reservedFor: string;
  tableId?: number | null;
  notes?: string | null;
  durationMinutes?: number | null;
  guestEmail?: string | null;
  depositAmount?: number | null;
  /** Payment method code: take the deposit now. */
  depositMethod?: string | null;
}

/** Admin/staff > Restaurant > Reservations (Fourier: "Reservations"). */
@Injectable({ providedIn: 'root' })
export class ReservationApiService {
  constructor(private http: HttpClient) {}

  /** One page of the filtered list (search, status, dates; the total comes back in X-Total-Count). */
  page(f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<Reservation>> {
    return pagedList<Reservation>(this.http, `${API_BASE}/restaurant/reservations`, f, page, size, extra);
  }

  listAll(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${API_BASE}/restaurant/reservations`);
  }

  /** Fourier: "show available table for that day" — date is an ISO 'YYYY-MM-DD' string. */
  availableTables(date: string): Observable<DiningTable[]> {
    return this.http.get<DiningTable[]>(`${API_BASE}/restaurant/reservations/available-tables`, { params: { date } });
  }

  create(payload: ReservationPayload): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations`, payload);
  }

  update(id: number, payload: ReservationPayload): Observable<Reservation> {
    return this.http.put<Reservation>(`${API_BASE}/restaurant/reservations/${id}`, payload);
  }

  /** The tables free for a party at one time. */
  tablesAt(at: string, partySize: number): Observable<DiningTable[]> {
    return this.http.get<DiningTable[]>(`${API_BASE}/restaurant/reservations/available-tables`, { params: { at, partySize } });
  }

  /** The bookable times on a date for a party. */
  slots(date: string, partySize: number): Observable<ReservationSlot[]> {
    return this.http.get<ReservationSlot[]>(`${API_BASE}/restaurant/reservations/slots`, { params: { date, partySize } });
  }

  /** ADMIN-only server-side (Fourier: "the reservation can be cancelled anytime by admin"). A held deposit is refunded or kept as chosen (blank = by the refund window). */
  cancel(id: number, reason?: string | null, deposit?: 'REFUND' | 'FORFEIT' | '' | null): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations/${id}/cancel`, { reason: reason || null, deposit: deposit || null });
  }

  noShow(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations/${id}/no-show`, {});
  }

  markNoShows(): Observable<{ marked: number }> {
    return this.http.post<{ marked: number }>(`${API_BASE}/restaurant/reservations/mark-no-shows`, {});
  }

  takeDeposit(id: number, method: string, amount?: number | null): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations/${id}/deposit`, { method, amount: amount ?? null });
  }

  remindersDue(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${API_BASE}/restaurant/reservations/reminders`);
  }

  reminder(id: number, send: boolean): Observable<ReservationReminder> {
    return this.http.post<ReservationReminder>(`${API_BASE}/restaurant/reservations/${id}/reminder`, { send });
  }

  markReminded(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations/${id}/reminded`, {});
  }

  /** Turns a booking into a real, open RestaurantOrder the moment the party arrives. */
  seat(id: number, tableId?: number | null): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/reservations/${id}/seat`, { tableId });
  }

  /** Staff preview before seating — looks a booking up by its reservation number without seating it yet. */
  lookupByCode(code: string): Observable<Reservation> {
    return this.http.get<Reservation>(`${API_BASE}/restaurant/reservations/lookup/${encodeURIComponent(code)}`);
  }

  /** Fourier: "To use the reserved table, you need to enter reservation number." */
  seatByCode(code: string, serverId?: number | null): Observable<RestaurantOrder> {
    return this.http.post<RestaurantOrder>(`${API_BASE}/restaurant/reservations/seat-by-code`, { code, serverId });
  }
}
