import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE } from '../constants';
import { downloadBlob } from '../download-file';

import { T } from '../i18n/t';
export type PmDocSubject = 'PROPERTY' | 'UNIT' | 'OWNER' | 'TENANT' | 'LEASE' | 'MAINTENANCE' | 'INSPECTION';
export type PmDocCategory = 'LEASE_AGREEMENT' | 'ID_DOCUMENT' | 'TITLE_DEED' | 'INSPECTION' | 'PERMIT' | 'INSURANCE' | 'PHOTO' | 'RECEIPT' | 'NOTICE' | 'OTHER';

export const PM_DOC_CATEGORIES: { value: PmDocCategory; label: string }[] = [
  { value: 'LEASE_AGREEMENT', get label() { return T('Lease agreement'); } }, { value: 'ID_DOCUMENT', get label() { return T('ID document'); } }, { value: 'TITLE_DEED', get label() { return T('Title deed'); } }, { value: 'INSPECTION', get label() { return T('Inspection report'); } },
  { value: 'PERMIT', get label() { return T('Permit / licence'); } }, { value: 'INSURANCE', get label() { return T('Insurance'); } }, { value: 'PHOTO', get label() { return T('Photo'); } }, { value: 'RECEIPT', get label() { return T('Receipt / invoice'); } },
  { value: 'NOTICE', get label() { return T('Notice / letter'); } }, { value: 'OTHER', get label() { return T('Other'); } },
];
export function pmDocCategoryLabel(c: string): string { return PM_DOC_CATEGORIES.find((x) => x.value === c)?.label ?? c; }

export interface PmDocument {
  id: number; subjectType: PmDocSubject; subjectId: number; subjectLabel: string | null; name: string; category: PmDocCategory; description: string | null; expiryDate: string | null;
  expiryState: 'NONE' | 'OK' | 'SOON' | 'EXPIRED'; daysToExpiry: number | null; fileName: string; contentType: string; sizeBytes: number; uploadedByName: string | null; createdAt: string;
}
export interface PmDocumentPayload { name?: string | null; category?: PmDocCategory | null; description?: string | null; expiryDate?: string | null; clearExpiry?: boolean; }

/** Files attached to properties, units, owners, tenants, leases and repair requests. */
@Injectable({ providedIn: 'root' })
export class PmDocumentsApiService {
  private base = `${API_BASE}/propertymgmt/documents`;

  constructor(private http: HttpClient) {}

  list(f: { subjectType?: PmDocSubject | null; subjectId?: number | null; category?: string; q?: string; expiryOnly?: boolean }): Observable<PmDocument[]> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '' && v !== false) params = params.set(k, String(v));
    return this.http.get<PmDocument[]>(this.base, { params });
  }

  upload(subjectType: PmDocSubject, subjectId: number, file: File, d: PmDocumentPayload): Observable<PmDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('subjectType', subjectType);
    form.append('subjectId', String(subjectId));
    if (d.name) form.append('name', d.name);
    if (d.category) form.append('category', d.category);
    if (d.description) form.append('description', d.description);
    if (d.expiryDate) form.append('expiryDate', d.expiryDate);
    return this.http.post<PmDocument>(this.base, form);
  }

  update(id: number, d: PmDocumentPayload): Observable<PmDocument> { return this.http.put<PmDocument>(`${this.base}/${id}`, d); }
  remove(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`); }

  /** The file is fetched with the sign-in token and handed to the browser as a download. */
  download(d: { id: number; fileName: string }): Observable<void> {
    return this.http.get(`${this.base}/${d.id}/file`, { responseType: 'blob' }).pipe(map((b) => downloadBlob(b, d.fileName)));
  }
}
