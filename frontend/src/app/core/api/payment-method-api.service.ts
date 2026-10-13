import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { PaymentMethodOption } from '../models';

export interface CreatePaymentMethodPayload {
  code: string;
  name: string;
  cash: boolean;
  credit: boolean;
  roomCharge?: boolean;
  sortOrder: number;
}

export interface UpdatePaymentMethodPayload {
  name: string;
  cash: boolean;
  credit: boolean;
  roomCharge?: boolean;
  active: boolean;
  sortOrder: number;
}

/** Was a hardcoded enum — now an admin-configurable list. See Payment.method's backend javadoc. */
@Injectable({ providedIn: 'root' })
export class PaymentMethodApiService {
  constructor(private http: HttpClient) {}

  /** Active methods only — what the checkout/refund dropdowns show. */
  listActive(): Observable<PaymentMethodOption[]> {
    return this.http.get<PaymentMethodOption[]>(`${API_BASE}/payment-methods`);
  }

  /** Admin-only: includes deactivated methods so they can be reactivated. */
  listAll(): Observable<PaymentMethodOption[]> {
    return this.http.get<PaymentMethodOption[]>(`${API_BASE}/payment-methods/all`);
  }

  create(payload: CreatePaymentMethodPayload): Observable<PaymentMethodOption> {
    return this.http.post<PaymentMethodOption>(`${API_BASE}/payment-methods`, payload);
  }

  update(id: number, payload: UpdatePaymentMethodPayload): Observable<PaymentMethodOption> {
    return this.http.put<PaymentMethodOption>(`${API_BASE}/payment-methods/${id}`, payload);
  }
}
