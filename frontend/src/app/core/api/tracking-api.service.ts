import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

/** One batch/lot still holding stock — mirrors the backend's BatchDto. */
export interface StockBatchRow {
  id: number;
  productId: number;
  sku: string | null;
  productName: string;
  warehouseName: string | null;
  locationName: string | null;
  inTransit: boolean;
  batchNumber: string | null;
  expiryDate: string | null;
  daysToExpiry: number | null;
  quantity: number;
  unitCost: number | null;
  source: string | null;
}

/** One serial-numbered unit — mirrors the backend's SerialDto. */
export interface StockSerialRow {
  id: number;
  productId: number;
  sku: string | null;
  productName: string;
  serialNumber: string;
  status: 'IN_STOCK' | 'IN_TRANSIT' | 'SOLD' | 'REMOVED';
  warehouseName: string | null;
  locationName: string | null;
  receivedAt: string | null;
  source: string | null;
  soldAt: string | null;
  soldRef: string | null;
}

/** Batch/expiry and serial-number lookups (admin). */
@Injectable({ providedIn: 'root' })
export class TrackingApiService {
  constructor(private http: HttpClient) {}

  batches(productId: number): Observable<StockBatchRow[]> {
    return this.http.get<StockBatchRow[]>(`${API_BASE}/tracking/products/${productId}/batches`);
  }

  serials(productId: number): Observable<StockSerialRow[]> {
    return this.http.get<StockSerialRow[]>(`${API_BASE}/tracking/products/${productId}/serials`);
  }
}
