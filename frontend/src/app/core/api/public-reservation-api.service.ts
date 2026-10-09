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
}

/**
 * Fourier: "Public be able to make reservation" — the one API service in the app that calls
 * endpoints requiring no login (PublicReservationController, /api/public/reservations). Used only
 * by PublicReservationComponent, the unauthenticated booking page.
 */
@Injectable({ providedIn: 'root' })
export class PublicReservationApiService {
  constructor(private http: HttpClient) {}

  /** date is an ISO 'YYYY-MM-DD' string. */
  availableTables(date: string): Observable<PublicTable[]> {
    return this.http.get<PublicTable[]>(`${API_BASE}/public/reservations/available-tables`, { params: { date } });
  }

  create(payload: PublicReservationPayload): Observable<PublicReservationConfirmation> {
    return this.http.post<PublicReservationConfirmation>(`${API_BASE}/public/reservations`, payload);
  }
}
