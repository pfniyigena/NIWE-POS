import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type DepreciationMethod = 'STRAIGHT_LINE' | 'DECLINING_BALANCE';
export type AssetStatus = 'ACTIVE' | 'FULLY_DEPRECIATED' | 'DISPOSED';
export type AcquisitionFunding = 'NONE' | 'CASH' | 'PAYABLE';

export interface AssetCategory {
  id: number;
  name: string;
  method: DepreciationMethod;
  usefulLifeMonths: number;
  salvagePercent: number;
  prorateFirstMonth: boolean;
  active: boolean;
  assetCount: number;
}

export interface AssetCategoryPayload {
  name: string;
  method: DepreciationMethod;
  usefulLifeMonths: number;
  salvagePercent: number;
  prorateFirstMonth: boolean;
  active: boolean;
}

export interface FixedAsset {
  id: number;
  code: string;
  name: string;
  categoryId: number;
  categoryName: string;
  purchaseDate: string;
  cost: number;
  salvageValue: number;
  usefulLifeMonths: number;
  method: DepreciationMethod;
  depreciationStartDate: string;
  status: AssetStatus;
  location: string | null;
  assignedTo: string | null;
  serialNumber: string | null;
  supplierName: string | null;
  notes: string | null;
  accumulatedDepreciation: number;
  bookValue: number;
  lastDepreciatedThrough: string | null;
  acquisitionPosted: boolean;
  hasDepreciation: boolean;
  disposalDate: string | null;
  disposalProceeds: number | null;
  disposalGainLoss: number | null;
  disposalNote: string | null;
  prorateFirstMonth: boolean;
  purchaseOrderId: number | null;
  purchaseOrderNumber: string | null;
  branchId: number | null;
  branchName: string | null;
}

export interface FixedAssetPayload {
  code: string | null;
  name: string;
  categoryId: number;
  purchaseDate: string;
  cost: number;
  salvageValue: number | null;
  usefulLifeMonths: number | null;
  method: DepreciationMethod | null;
  depreciationStartDate: string | null;
  location: string | null;
  assignedTo: string | null;
  serialNumber: string | null;
  supplierName: string | null;
  notes: string | null;
  acquisitionFunding: AcquisitionFunding;
  openingAccumulatedDepreciation: number | null;
  prorateFirstMonth: boolean;
  branchId: number | null;
}

export interface AssetTransfer {
  id: number;
  assetId: number;
  assetCode: string;
  assetName: string;
  transferDate: string;
  fromBranchName: string | null;
  toBranchName: string | null;
  fromLocation: string | null;
  toLocation: string | null;
  reason: string | null;
}

export interface TransferPayload {
  toBranchId: number | null;
  transferDate: string;
  toLocation: string | null;
  assignedTo: string | null;
  reason: string | null;
}

export interface PurchaseLine {
  lineId: number;
  purchaseOrderId: number;
  purchaseOrderNumber: string;
  supplierName: string | null;
  orderDate: string;
  productName: string;
  quantityReceived: number;
  quantityRegistered: number;
  unitNetCost: number;
  stockTracked: boolean;
}

export interface FromPurchasePayload {
  purchaseOrderLineId: number;
  quantity: number;
  categoryId: number;
  name: string | null;
  purchaseDate: string | null;
  location: string | null;
  assignedTo: string | null;
  notes: string | null;
  prorateFirstMonth: boolean | null;
}

export interface DepreciationEntry {
  id: number;
  assetId: number;
  assetCode: string;
  assetName: string;
  periodEnd: string;
  amount: number;
  accumulatedAfter: number;
  bookValueAfter: number;
}

export interface AssetSummary {
  assetCount: number;
  totalCost: number;
  totalAccumulatedDepreciation: number;
  totalBookValue: number;
  fullyDepreciatedCount: number;
  disposedCount: number;
}

export interface DepreciationRunResult {
  monthsPosted: number;
  entriesCreated: number;
  totalAmount: number;
  upTo: string;
}

/** Assets Management module (/api/assets): categories, the asset register, monthly depreciation and disposal. */
@Injectable({ providedIn: 'root' })
export class AssetApiService {
  constructor(private http: HttpClient) {}

  categories(): Observable<AssetCategory[]> {
    return this.http.get<AssetCategory[]>(`${API_BASE}/assets/categories`);
  }

  createCategory(payload: AssetCategoryPayload): Observable<AssetCategory> {
    return this.http.post<AssetCategory>(`${API_BASE}/assets/categories`, payload);
  }

  updateCategory(id: number, payload: AssetCategoryPayload): Observable<AssetCategory> {
    return this.http.put<AssetCategory>(`${API_BASE}/assets/categories/${id}`, payload);
  }

  summary(branchId?: number | null): Observable<AssetSummary> {
    let params = new HttpParams();
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<AssetSummary>(`${API_BASE}/assets/summary`, { params });
  }

  list(status?: AssetStatus | '', branchId?: number | null): Observable<FixedAsset[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<FixedAsset[]>(`${API_BASE}/assets`, { params });
  }

  get(id: number): Observable<FixedAsset> {
    return this.http.get<FixedAsset>(`${API_BASE}/assets/${id}`);
  }

  depreciation(id: number): Observable<DepreciationEntry[]> {
    return this.http.get<DepreciationEntry[]>(`${API_BASE}/assets/${id}/depreciation`);
  }

  create(payload: FixedAssetPayload): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets`, payload);
  }

  update(id: number, payload: FixedAssetPayload): Observable<FixedAsset> {
    return this.http.put<FixedAsset>(`${API_BASE}/assets/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/assets/${id}`);
  }

  transfers(id: number): Observable<AssetTransfer[]> {
    return this.http.get<AssetTransfer[]>(`${API_BASE}/assets/${id}/transfers`);
  }

  transfer(id: number, payload: TransferPayload): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/transfer`, payload);
  }

  dispose(id: number, payload: { disposalDate: string; proceeds: number | null; note: string | null }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/dispose`, payload);
  }

  purchaseLines(): Observable<PurchaseLine[]> {
    return this.http.get<PurchaseLine[]>(`${API_BASE}/assets/purchase-lines`);
  }

  fromPurchase(payload: FromPurchasePayload): Observable<FixedAsset[]> {
    return this.http.post<FixedAsset[]>(`${API_BASE}/assets/from-purchase`, payload);
  }

  runDepreciation(upTo: string | null): Observable<DepreciationRunResult> {
    return this.http.post<DepreciationRunResult>(`${API_BASE}/assets/depreciation/run`, { upTo });
  }
}
