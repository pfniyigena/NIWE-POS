import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface SkippedRow {
  rowNumber: number;
  reason: string;
}

export interface ImportResult {
  imported: number;
  skipped: number;
  skippedRows: SkippedRow[];
}

/** Admin > Data > Import / Export / Year-end archive (Fourier: "no ... bulk import-export anywhere in the backend"). See DataImportExportController on the backend. */
@Injectable({ providedIn: 'root' })
export class DataImportExportApiService {
  constructor(private http: HttpClient) {}

  /** Fourier: "a button to download a template to upload" — a filled-in example CSV with the exact columns each importer expects. */
  downloadProductsTemplate(): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/import/products/template`, { responseType: 'blob' });
  }

  downloadCustomersTemplate(): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/import/customers/template`, { responseType: 'blob' });
  }

  downloadOpeningStockTemplate(): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/import/opening-stock/template`, { responseType: 'blob' });
  }

  importProducts(file: File): Observable<ImportResult> {
    return this.upload('/data/import/products', file);
  }

  importCustomers(file: File): Observable<ImportResult> {
    return this.upload('/data/import/customers', file);
  }

  importOpeningStock(file: File): Observable<ImportResult> {
    return this.upload('/data/import/opening-stock', file);
  }

  exportProducts(): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/export/products`, { responseType: 'blob' });
  }

  exportCustomers(): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/export/customers`, { responseType: 'blob' });
  }

  exportYearEnd(year: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/export/year-end`, { params: { year: String(year) }, responseType: 'blob' });
  }

  private upload(path: string, file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>(`${API_BASE}${path}`, formData);
  }
}
