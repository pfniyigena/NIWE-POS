import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Customer } from '../models';

export interface CustomerPayload {
  name: string;
  phone?: string | null;
  email?: string | null;
  taxId?: string | null;
  creditEnabled?: boolean;
  creditLimit?: number | null;
  defaultCreditTermDays?: number | null;
}

@Injectable({ providedIn: 'root' })
export class CustomerApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${API_BASE}/customers`);
  }

  /** Fourier: "add one button (View) on customer then put all others buttons on view form" — backs CustomerDetailComponent's single-record load. */
  getById(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${API_BASE}/customers/${id}`);
  }

  create(payload: CustomerPayload): Observable<Customer> {
    return this.http.post<Customer>(`${API_BASE}/customers`, payload);
  }

  update(id: number, payload: CustomerPayload): Observable<Customer> {
    return this.http.put<Customer>(`${API_BASE}/customers/${id}`, payload);
  }
}
