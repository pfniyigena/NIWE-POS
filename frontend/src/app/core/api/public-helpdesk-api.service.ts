import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PublicHelpDeskCategory, PublicHelpDeskTicketConfirmation, PublicHelpDeskTicketStatus } from '../models';

export interface PublicHelpDeskTicketPayload {
  subject: string;
  description: string;
  categoryId?: number | null;
  requesterName: string;
  requesterEmail: string;
  requesterPhone?: string | null;
}

/**
 * Fourier: "Add Help Desk module ... supporting both authenticated and guest ticket creation" —
 * the no-login half, same pattern as PublicReservationApiService (this is the second, and only
 * other, unauthenticated API namespace in the app). Used only by PublicHelpDeskComponent.
 */
@Injectable({ providedIn: 'root' })
export class PublicHelpDeskApiService {
  constructor(private http: HttpClient) {}

  categories(): Observable<PublicHelpDeskCategory[]> {
    return this.http.get<PublicHelpDeskCategory[]>(`${API_BASE}/public/helpdesk/categories`);
  }

  create(payload: PublicHelpDeskTicketPayload): Observable<PublicHelpDeskTicketConfirmation> {
    return this.http.post<PublicHelpDeskTicketConfirmation>(`${API_BASE}/public/helpdesk/tickets`, payload);
  }

  lookup(ticketNumber: string, email: string): Observable<PublicHelpDeskTicketStatus> {
    return this.http.get<PublicHelpDeskTicketStatus>(`${API_BASE}/public/helpdesk/tickets/lookup`, {
      params: { ticketNumber, email },
    });
  }

  reply(ticketNumber: string, email: string, body: string): Observable<PublicHelpDeskTicketStatus> {
    return this.http.post<PublicHelpDeskTicketStatus>(`${API_BASE}/public/helpdesk/tickets/reply`, { ticketNumber, email, body });
  }

  rate(ticketNumber: string, email: string, rating: number, comment: string | null): Observable<PublicHelpDeskTicketStatus> {
    return this.http.post<PublicHelpDeskTicketStatus>(`${API_BASE}/public/helpdesk/tickets/rate`, { ticketNumber, email, rating, comment });
  }

  addFile(ticketNumber: string, email: string, file: File): Observable<PublicHelpDeskTicketStatus> {
    const fd = new FormData();
    fd.append('ticketNumber', ticketNumber);
    fd.append('email', email);
    fd.append('file', file, file.name);
    return this.http.post<PublicHelpDeskTicketStatus>(`${API_BASE}/public/helpdesk/tickets/attachments`, fd);
  }

  fileBlob(id: number, ticketNumber: string, email: string): Observable<Blob> {
    return this.http.get(`${API_BASE}/public/helpdesk/tickets/attachments/${id}`, { params: { ticketNumber, email }, responseType: 'blob' });
  }
}
