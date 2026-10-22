import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface HotelPublicInfo {
  hotelName: string; address: string | null; phone: string | null; email: string | null; intro: string | null; checkInTime: string; checkOutTime: string;
  taxPercent: number; currency: string | null; depositPercent: number; holdHours: number; maxNights: number; maxDaysAhead: number; paymentInstructions: string | null;
}
export interface HotelPublicNight { date: string; gross: number }
export interface HotelPublicOffer {
  roomTypeId: number; name: string; description: string | null; amenities: string | null; maxOccupancy: number; roomsLeft: number; nights: number;
  net: number; tax: number; gross: number; averageRate: number; depositDue: number; items: HotelPublicNight[];
}
export interface HotelPublicAvailability { from: string; to: string; nights: number; adults: number; children: number; offers: HotelPublicOffer[] }
export interface HotelPublicBooking {
  code: string; bookingNumber: string; status: string; roomTypeName: string; checkInDate: string; checkOutDate: string; nights: number; adults: number; children: number;
  guestName: string; total: number; depositDue: number | null; depositPaid: number; holdExpiresAt: string | null; paymentInstructions: string | null;
  canCancel: boolean; cancelNote: string | null; checkInTime: string; checkOutTime: string;
}
export interface HotelPublicBookPayload {
  roomTypeId: number; checkInDate: string; checkOutDate: string; adults: number; children: number; name: string; phone: string; email: string | null; notes: string | null; website: string;
}

/** The hotel's public booking page: calls the endpoints that need no login (HotelPublicController, /api/public/hotel). */
@Injectable({ providedIn: 'root' })
export class HotelPublicApiService {
  private base = `${API_BASE}/public/hotel`;

  constructor(private http: HttpClient) {}

  info(): Observable<HotelPublicInfo> { return this.http.get<HotelPublicInfo>(`${this.base}/info`); }
  availability(from: string, to: string, adults: number, children: number): Observable<HotelPublicAvailability> {
    const p = new HttpParams().set('from', from).set('to', to).set('adults', adults).set('children', children);
    return this.http.get<HotelPublicAvailability>(`${this.base}/availability`, { params: p });
  }
  book(p: HotelPublicBookPayload): Observable<HotelPublicBooking> { return this.http.post<HotelPublicBooking>(`${this.base}/bookings`, p); }
  lookup(code: string, phone: string): Observable<HotelPublicBooking> { return this.http.post<HotelPublicBooking>(`${this.base}/bookings/${encodeURIComponent(code)}/lookup`, { phone }); }
  cancel(code: string, phone: string): Observable<HotelPublicBooking> { return this.http.post<HotelPublicBooking>(`${this.base}/bookings/${encodeURIComponent(code)}/cancel`, { phone }); }
}
