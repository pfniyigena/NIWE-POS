import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Quotation, QuotationPayload, SalesOrder } from '../models';

/** Fourier: "Add Quotation ... without touching warehousing." Admin-only, mirrors SalesOrderApiService's shape. */
@Injectable({ providedIn: 'root' })
export class QuotationApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<Quotation[]> {
    return this.http.get<Quotation[]>(`${API_BASE}/quotations`);
  }

  getById(id: number): Observable<Quotation> {
    return this.http.get<Quotation>(`${API_BASE}/quotations/${id}`);
  }

  create(payload: QuotationPayload): Observable<Quotation> {
    return this.http.post<Quotation>(`${API_BASE}/quotations`, payload);
  }

  /** Only while the quote is still DRAFT — see the backend QuotationService.updateDraft's javadoc. */
  update(id: number, payload: QuotationPayload): Observable<Quotation> {
    return this.http.put<Quotation>(`${API_BASE}/quotations/${id}`, payload);
  }

  send(id: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${API_BASE}/quotations/${id}/send`, {});
  }

  accept(id: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${API_BASE}/quotations/${id}/accept`, {});
  }

  reject(id: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${API_BASE}/quotations/${id}/reject`, {});
  }

  expire(id: number): Observable<Quotation> {
    return this.http.post<Quotation>(`${API_BASE}/quotations/${id}/expire`, {});
  }

  /** Returns the newly-created SalesOrder (not the quotation) — see the backend QuotationController.convert's javadoc. */
  convert(id: number): Observable<SalesOrder> {
    return this.http.post<SalesOrder>(`${API_BASE}/quotations/${id}/convert`, {});
  }

  /** Fourier: "sending ... quotations ... to the customer's email" — emails a PDF of this quote, at any status. `email` overrides the customer's own saved one; omit to use that. */
  email(id: number, email?: string | null): Observable<void> {
    return this.http.post<void>(`${API_BASE}/quotations/${id}/email`, email ? { email } : {});
  }

  /** Fourier: "Add print A4 on Quotation and Label it as proforma" — an A4 PDF of this quotation, for opening in a new tab to print (see download-file.ts's openPdfForPrint). Headed "Proforma Invoice" on the printed page itself — see the backend DocumentPdfService.quotationPdf's javadoc. */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/quotations/${id}/pdf`, { responseType: 'blob' });
  }
}
