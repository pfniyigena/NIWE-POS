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
  /** True when the file was only checked: nothing was saved. */
  dryRun?: boolean;
  /** Rows read from the file. */
  total?: number;
  /** The first rows that are fine, described in words (what will be created). */
  accepted?: SkippedRow[];
  /** Side effects worth knowing about, such as new categories or the total owed. */
  notes?: string[];
}

export type ImportKind =
  | 'products'
  | 'customers'
  | 'suppliers'
  | 'categories'
  | 'brands'
  | 'opening-stock'
  | 'customer-balances'
  | 'supplier-balances'
  | 'ledger-balances';

/** Admin > Data > Import / Export / Year-end archive (Fourier: "no ... bulk import-export anywhere in the backend"). See DataImportExportController on the backend. */
@Injectable({ providedIn: 'root' })
export class DataImportExportApiService {
  constructor(private http: HttpClient) {}

  /** A filled-in example CSV with the exact columns each importer expects. */
  downloadTemplate(kind: ImportKind): Observable<Blob> {
    return this.http.get(`${API_BASE}/data/import/${kind}/template`, { responseType: 'blob' });
  }

  /** dryRun=true reads the file and reports exactly what would happen, saving nothing. */
  importFile(kind: ImportKind, file: File, dryRun: boolean, balanceToEquity = false): Observable<ImportResult> {
    return this.upload(`/data/import/${kind}`, file, dryRun, balanceToEquity);
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

  private upload(path: string, file: File, dryRun: boolean, balanceToEquity: boolean): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    const params: Record<string, string> = { dryRun: String(dryRun) };
    if (balanceToEquity) params['balanceToEquity'] = 'true';
    return this.http.post<ImportResult>(`${API_BASE}${path}`, formData, { params });
  }
}
