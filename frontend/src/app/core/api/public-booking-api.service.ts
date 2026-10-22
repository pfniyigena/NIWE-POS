import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { AppointmentStatus, Option, PaymentMode } from './booking-api.service';

export interface PublicService { id: number; name: string; description: string | null; durationMinutes: number; price: number; paymentMode: PaymentMode; amountDue: number; staff: Option[]; }
export interface PublicInfo { title: string | null; intro: string | null; paymentInstructions: string | null; maxAdvanceDays: number; workDays: string; services: PublicService[]; }

export interface PublicConfirmation {
  confirmationCode: string; number: string; status: AppointmentStatus; serviceName: string; startAt: string; endAt: string; staffName: string | null; price: number;
  amountDue: number; amountPaid: number; invoiceNumber: string | null; holdExpiresAt: string | null; paymentInstructions: string | null; canCancel: boolean;
}

export interface PublicBookPayload { serviceId: number; date: string; time: string; staffUserId: number | null; name: string; phone: string; email: string | null; notes: string | null; website: string; }

/** The public booking page: calls the endpoints that need no login (PublicBookingController, /api/public/booking). */
@Injectable({ providedIn: 'root' })
export class PublicBookingApiService {
  private base = `${API_BASE}/public/booking`;

  constructor(private http: HttpClient) {}

  info(): Observable<PublicInfo> { return this.http.get<PublicInfo>(`${this.base}/info`); }
  slots(serviceId: number, date: string, staffId: number | null): Observable<string[]> {
    let p = new HttpParams().set('serviceId', serviceId).set('date', date);
    if (staffId) p = p.set('staffId', staffId);
    return this.http.get<string[]>(`${this.base}/slots`, { params: p });
  }
  book(p: PublicBookPayload): Observable<PublicConfirmation> { return this.http.post<PublicConfirmation>(`${this.base}/appointments`, p); }
  lookup(code: string, phone: string): Observable<PublicConfirmation> { return this.http.post<PublicConfirmation>(`${this.base}/appointments/${encodeURIComponent(code)}/lookup`, { phone }); }
  cancel(code: string, phone: string): Observable<PublicConfirmation> { return this.http.post<PublicConfirmation>(`${this.base}/appointments/${encodeURIComponent(code)}/cancel`, { phone }); }
}
