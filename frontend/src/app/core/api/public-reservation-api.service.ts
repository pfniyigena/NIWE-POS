import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

/** Fourier: "Public reservations should show available table for that day." Deliberately slim — no live status/openOrderId, see backend PublicTableDto's own javadoc. */
export interface PublicTable {
  id: number;
  name: string;
  section: string | null;
  capacity: number;
}

export interface PublicReservationPayload {
  guestName: string;
  guestPhone: string;
  partySize: number;
  reservedFor: string;
  tableId: number;
  notes?: string | null;
  guestEmail?: string | null;
}

export interface PublicReservationInfo {
  restaurantName: string;
  openTime: string;
  closeTime: string;
  slotMinutes: number;
  turnMinutes: number;
  bookingDaysAhead: number;
  depositPerGuest: number;
  today: string;
  timeZone: string;
}

export interface PublicSlot {
  time: string;
  label: string;
  tablesFree: number;
}

export interface PublicReservationConfirmation {
  id: number;
  guestName: string;
  partySize: number;
  reservedFor: string;
  tableName: string | null;
  status: string;
  notes: string | null;
  /** The "reservation number" — see backend PublicReservationDto's own javadoc. */
  confirmationCode: string;
  durationMinutes?: number;
  depositAmount?: number;
  depositStatus?: string;
}

/**
 * Fourier: "Public be able to make reservation" — the one API service in the app that calls
 * endpoints requiring no login (PublicReservationController, /api/public/reservations). Used only
 * by PublicReservationComponent, the unauthenticated booking page.
 */
@Injectable({ providedIn: 'root' })
export class PublicReservationApiService {
  constructor(private http: HttpClient) {}

  info(): Observable<PublicReservationInfo> {
    return this.http.get<PublicReservationInfo>(`${API_BASE}/public/reservations/info`);
  }

  /** The times still open on a date for a party. */
  slots(date: string, partySize: number): Observable<PublicSlot[]> {
    return this.http.get<PublicSlot[]>(`${API_BASE}/public/reservations/slots`, { params: { date, partySize } });
  }

  /** date is an ISO 'YYYY-MM-DD' string; with `at` (an ISO instant) only the tables free for that sitting. */
  availableTables(date: string, at?: string, partySize?: number): Observable<PublicTable[]> {
    const params: Record<string, string> = { date };
    if (at) params['at'] = at;
    if (partySize) params['partySize'] = String(partySize);
    return this.http.get<PublicTable[]>(`${API_BASE}/public/reservations/available-tables`, { params });
  }

  create(payload: PublicReservationPayload): Observable<PublicReservationConfirmation> {
    return this.http.post<PublicReservationConfirmation>(`${API_BASE}/public/reservations`, payload);
  }
}
