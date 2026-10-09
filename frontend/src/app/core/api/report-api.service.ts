import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface ReportFilter {
  from: string | null; // yyyy-MM-dd
  to: string | null; // yyyy-MM-dd
  registerId: number | null;
  categoryId?: number | null;
  /** Fourier: "can we have Stock Movement History for a specific product" — narrows to one product's own history, independent of (and combinable with) categoryId. Only the Stock Movement History report reads it (ReportDef.productFilter). */
  productId?: number | null;
  /** Multi-warehouse phase 1 — Stock On Hand report only: show this one warehouse's sellable stock instead of the primary warehouse's sales stock. */
  warehouseId?: number | null;
  /** Branch filter — POS sales/profit reports and Stock Movement History only (ReportDef.branchFilter); null = every branch. */
  branchId?: number | null;
  /** Multi-warehouse phase 1 — Stock On Hand report only: sum sellable stock across every warehouse. Ignored when warehouseId is set. */
  allWarehouses?: boolean;
  /** Required by the Customer Statement report only (see customer-statement.component.ts); every other report ignores it. */
  customerId?: number | null;
  /** Required by the Supplier Statement report only (Task 5 — see supplier-statement.component.ts); every other report ignores it. */
  supplierId?: number | null;
}

/** Backs every screen under /admin/reports — see ../report-defs.ts for what each report looks like. */
@Injectable({ providedIn: 'root' })
export class ReportApiService {
  constructor(private http: HttpClient) {}

  /** The on-screen table — JSON rows shaped per report (see report-defs.ts's columns). */
  run(endpoint: string, filter: ReportFilter): Observable<Record<string, any>[]> {
    return this.http.get<Record<string, any>[]>(`${API_BASE}/reports/${endpoint}`, { params: this.toParams(filter) });
  }

  /** Same filters, but as a downloadable file — see ReportExportService on the backend. */
  download(endpoint: string, filter: ReportFilter, format: 'xlsx' | 'pdf'): Observable<Blob> {
    return this.http.get(`${API_BASE}/reports/${endpoint}`, {
      params: { ...this.toParams(filter), format },
      responseType: 'blob',
    });
  }

  private toParams(filter: ReportFilter): Record<string, string> {
    const params: Record<string, string> = {};
    if (filter.from) params['from'] = filter.from;
    if (filter.to) params['to'] = filter.to;
    if (filter.registerId != null) params['registerId'] = String(filter.registerId);
    if (filter.categoryId != null) params['categoryId'] = String(filter.categoryId);
    if (filter.productId != null) params['productId'] = String(filter.productId);
    if (filter.branchId != null) params['branchId'] = String(filter.branchId);
    if (filter.warehouseId != null) params['warehouseId'] = String(filter.warehouseId);
    if (filter.allWarehouses) params['allWarehouses'] = 'true';
    if (filter.customerId != null) params['customerId'] = String(filter.customerId);
    if (filter.supplierId != null) params['supplierId'] = String(filter.supplierId);
    return params;
  }
}
