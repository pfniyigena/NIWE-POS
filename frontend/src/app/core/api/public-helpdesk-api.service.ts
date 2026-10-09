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
}
