import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DiningTable, Reservation, RestaurantOrder } from '../models';

export interface ReservationPayload {
  guestName: string;
  guestPhone?: string | null;
  partySize: number;
  /** ISO instant. */
  reservedFor: string;
  tableId?: number | null;
  notes?: string | null;
}

/** Admin/staff > Restaurant > Reservations (Fourier: "Reservations"). */
@Injectable({ providedIn: 'root' })
export class ReservationApiService {
  constructor(private http: HttpClient) {}

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

  /** ADMIN-only server-side (Fourier: "the reservation can be cancelled anytime by admin"). */
  cancel(id: number): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE}/restaurant/reservations/${id}/cancel`, {});
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
