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
  assetAccountId: number | null;
  assetAccountName: string | null;
  accumulatedAccountId: number | null;
  accumulatedAccountName: string | null;
  expenseAccountId: number | null;
  expenseAccountName: string | null;
}

export interface AssetCategoryPayload {
  name: string;
  method: DepreciationMethod;
  usefulLifeMonths: number;
  salvagePercent: number;
  prorateFirstMonth: boolean;
  active: boolean;
  assetAccountId?: number | null;
  accumulatedAccountId?: number | null;
  expenseAccountId?: number | null;
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
  warrantyExpiresOn: string | null;
  insuranceExpiresOn: string | null;
  insurancePolicy: string | null;
  custodianUserId: number | null;
  custodianName: string | null;
  checkedOutOn: string | null;
  originalCost: number;
  fileCount: number;
  warrantyState: 'OK' | 'EXPIRING' | 'EXPIRED' | null;
  insuranceState: 'OK' | 'EXPIRING' | 'EXPIRED' | null;
  nextCharge: number | null;
  monthsLeft: number | null;
  rebased: boolean;
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
  warrantyExpiresOn?: string | null;
  insuranceExpiresOn?: string | null;
  insurancePolicy?: string | null;
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

export interface AssetFilter {
  status?: AssetStatus | '';
  branchId?: number | null;
  categoryId?: number | null;
  q?: string;
  location?: string;
  custodian?: string;
  view?: string;
}

export interface AssetFile {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  createdAt: string;
  image: boolean;
}

export interface AssetEvent {
  id: number;
  kind: string;
  detail: string | null;
  createdByName: string | null;
  createdAt: string;
}

export interface AssetAdjustment {
  id: number;
  kind: 'IMPROVEMENT' | 'IMPAIRMENT' | 'TERMS';
  adjustmentDate: string;
  amount: number;
  funding: string | null;
  remainingLifeMonths: number | null;
  salvageValue: number | null;
  method: string | null;
  costAfter: number;
  bookValueAfter: number;
  note: string | null;
  createdByName: string | null;
}

export interface AssetCustody {
  id: number;
  custodianUserId: number | null;
  custodianName: string;
  checkedOutOn: string;
  checkedInOn: string | null;
  outCondition: string | null;
  inCondition: string | null;
  note: string | null;
  createdByName: string | null;
}

export interface AssetBulkResult {
  done: number;
  skipped: { id: number; code: string | null; reason: string }[];
}

export interface AssetBulkRequest {
  action: 'TRANSFER' | 'CHECK_OUT' | 'CHECK_IN';
  ids: number[];
  toBranchId?: number | null;
  toLocation?: string | null;
  date?: string | null;
  custodianUserId?: number | null;
  custodianName?: string | null;
  reason?: string | null;
}

export interface AssetSettings {
  alertDays: number;
  soonMonths: number;
  disposalApprovalLimit: number;
}

export interface Verification {
  id: number;
  name: string;
  status: 'OPEN' | 'CLOSED';
  branchId: number | null;
  branchName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  location: string | null;
  startedOn: string;
  closedOn: string | null;
  note: string | null;
  createdByName: string | null;
  closedByName: string | null;
  total: number;
  pending: number;
  found: number;
  missing: number;
  damaged: number;
}

export interface VerificationLine {
  id: number;
  assetId: number;
  assetCode: string;
  assetName: string;
  expectedLocation: string | null;
  expectedCustodian: string | null;
  bookValue: number;
  result: 'PENDING' | 'FOUND' | 'MISSING' | 'DAMAGED';
  foundLocation: string | null;
  note: string | null;
  checkedAt: string | null;
  checkedByName: string | null;
  writtenOff: boolean;
  assetStatus: string | null;
}

export interface NamedAmount {
  name: string;
  count: number;
  cost: number;
  bookValue: number;
}

export interface AttentionItem {
  id: number;
  code: string;
  name: string;
  detail: string;
  date: string | null;
  days: number | null;
}

export interface AssetOverview {
  assetCount: number;
  totalCost: number;
  totalAccumulated: number;
  totalBookValue: number;
  fullyDepreciatedCount: number;
  disposedCount: number;
  nextMonthCharge: number;
  depreciatedThrough: string | null;
  monthsBehind: number;
  withoutCustodian: number;
  openVerifications: number;
  alertDays: number;
  soonMonths: number;
  byCategory: NamedAmount[];
  byBranch: NamedAmount[];
  fullyDepreciatingSoon: AttentionItem[];
  warrantyExpiring: AttentionItem[];
  insuranceExpiring: AttentionItem[];
}

export interface Custodian {
  id: number;
  name: string;
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

  find(filter: AssetFilter): Observable<FixedAsset[]> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
    });
    return this.http.get<FixedAsset[]>(`${API_BASE}/assets`, { params });
  }

  overview(branchId?: number | null): Observable<AssetOverview> {
    let params = new HttpParams();
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<AssetOverview>(`${API_BASE}/assets/overview`, { params });
  }

  settings(): Observable<AssetSettings> {
    return this.http.get<AssetSettings>(`${API_BASE}/assets/settings`);
  }

  saveSettings(s: Partial<AssetSettings>): Observable<AssetSettings> {
    return this.http.put<AssetSettings>(`${API_BASE}/assets/settings`, s);
  }

  custodians(): Observable<Custodian[]> {
    return this.http.get<Custodian[]>(`${API_BASE}/assets/custodians`);
  }

  events(id: number): Observable<AssetEvent[]> {
    return this.http.get<AssetEvent[]>(`${API_BASE}/assets/${id}/events`);
  }

  adjustments(id: number): Observable<AssetAdjustment[]> {
    return this.http.get<AssetAdjustment[]>(`${API_BASE}/assets/${id}/adjustments`);
  }

  improve(id: number, p: { date: string; amount: number; funding: AcquisitionFunding; remainingLifeMonths: number | null; note: string | null }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/improvement`, p);
  }

  impair(id: number, p: { date: string; amount: number; remainingLifeMonths: number | null; note: string }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/impairment`, p);
  }

  changeTerms(id: number, p: { date: string; remainingLifeMonths: number; salvageValue: number | null; method: DepreciationMethod | null; note: string | null }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/terms`, p);
  }

  reverseDepreciation(id: number, reason: string | null): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/depreciation/reverse`, { reason });
  }

  undoDisposal(id: number, reason: string | null): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/undo-disposal`, { reason });
  }

  custody(id: number): Observable<AssetCustody[]> {
    return this.http.get<AssetCustody[]>(`${API_BASE}/assets/${id}/custody`);
  }

  checkOut(id: number, p: { custodianUserId: number | null; custodianName: string | null; date: string | null; condition: string | null; note: string | null }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/check-out`, p);
  }

  checkIn(id: number, p: { date: string | null; condition: string | null; note: string | null }): Observable<FixedAsset> {
    return this.http.post<FixedAsset>(`${API_BASE}/assets/${id}/check-in`, p);
  }

  handoverPdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/assets/${id}/handover-pdf`, { responseType: 'blob' });
  }

  files(id: number): Observable<AssetFile[]> {
    return this.http.get<AssetFile[]>(`${API_BASE}/assets/${id}/files`);
  }

  addFile(id: number, file: File): Observable<AssetFile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AssetFile>(`${API_BASE}/assets/${id}/files`, form);
  }

  fileBlob(id: number, fileId: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/assets/${id}/files/${fileId}`, { responseType: 'blob' });
  }

  deleteFile(id: number, fileId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/assets/${id}/files/${fileId}`);
  }

  bulk(req: AssetBulkRequest): Observable<AssetBulkResult> {
    return this.http.post<AssetBulkResult>(`${API_BASE}/assets/bulk`, req);
  }

  /** The asset register report as a download, with the same filters as the list. */
  registerExport(format: 'xlsx' | 'pdf', f: AssetFilter): Observable<Blob> {
    let params = new HttpParams().set('format', format);
    Object.entries(f).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
    });
    return this.http.get(`${API_BASE}/reports/asset-register`, { params, responseType: 'blob' });
  }

  verifications(): Observable<Verification[]> {
    return this.http.get<Verification[]>(`${API_BASE}/assets/verifications`);
  }

  startVerification(p: { name: string; branchId: number | null; categoryId: number | null; location: string | null; note: string | null }): Observable<Verification> {
    return this.http.post<Verification>(`${API_BASE}/assets/verifications`, p);
  }

  verification(id: number): Observable<Verification> {
    return this.http.get<Verification>(`${API_BASE}/assets/verifications/${id}`);
  }

  verificationLines(id: number): Observable<VerificationLine[]> {
    return this.http.get<VerificationLine[]>(`${API_BASE}/assets/verifications/${id}/lines`);
  }

  markLine(id: number, lineId: number, p: { result: string; foundLocation: string | null; note: string | null }): Observable<VerificationLine> {
    return this.http.put<VerificationLine>(`${API_BASE}/assets/verifications/${id}/lines/${lineId}`, p);
  }

  markRest(id: number, result: string): Observable<Verification> {
    return this.http.post<Verification>(`${API_BASE}/assets/verifications/${id}/mark-rest`, { result });
  }

  closeVerification(id: number, applyLocations: boolean): Observable<Verification> {
    return this.http.post<Verification>(`${API_BASE}/assets/verifications/${id}/close`, { applyLocations });
  }

  deleteVerification(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/assets/verifications/${id}`);
  }

  writeOffLine(id: number, lineId: number): Observable<VerificationLine> {
    return this.http.post<VerificationLine>(`${API_BASE}/assets/verifications/${id}/lines/${lineId}/write-off`, {});
  }
}
