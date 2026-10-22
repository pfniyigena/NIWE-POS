import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListingPurpose, PropertyType } from './realestate-api.service';

export interface PublicInfo { title: string | null; intro: string | null; phone: string | null; email: string | null; areas: string[]; types: PropertyType[]; }
export interface PublicCard {
  id: number; code: string; title: string; type: PropertyType; purpose: ListingPurpose; area: string | null; bedrooms: number | null; bathrooms: number | null; sizeSqm: number | null;
  salePrice: number | null; rentPrice: number | null; negotiable: boolean; featured: boolean; coverPhotoId: number | null; photos: number; listedDate: string | null;
}
export interface PublicPage { items: PublicCard[]; total: number; page: number; size: number; }
export interface PublicDetail {
  id: number; code: string; title: string; type: PropertyType; purpose: ListingPurpose; description: string | null; area: string | null; bedrooms: number | null; bathrooms: number | null;
  parkingSpaces: number | null; sizeSqm: number | null; plotSqm: number | null; yearBuilt: number | null; furnished: boolean; features: string[]; salePrice: number | null;
  rentPrice: number | null; negotiable: boolean; agentName: string | null; listedDate: string | null; photoIds: number[];
}
export interface PublicFilter { purpose?: string; type?: string; area?: string; bedrooms?: number | null; minPrice?: number | null; maxPrice?: number | null; q?: string; page?: number; size?: number; }
export interface EnquiryPayload { name: string; phone: string | null; email: string | null; message: string | null; website: string; }

/** The public listings website (no login): GET/POST under /api/public/realestate. Pictures are plain image addresses, so the browser can cache them. */
@Injectable({ providedIn: 'root' })
export class PublicRealEstateApiService {
  private base = `${API_BASE}/public/realestate`;

  constructor(private http: HttpClient) {}

  info(): Observable<PublicInfo> { return this.http.get<PublicInfo>(`${this.base}/info`); }

  list(f: PublicFilter): Observable<PublicPage> {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    return this.http.get<PublicPage>(`${this.base}/properties`, { params: p });
  }

  detail(id: number): Observable<PublicDetail> { return this.http.get<PublicDetail>(`${this.base}/properties/${id}`); }
  photoUrl(propertyId: number, photoId: number): string { return `${this.base}/properties/${propertyId}/photos/${photoId}`; }
  enquire(id: number, p: EnquiryPayload): Observable<{ message: string }> { return this.http.post<{ message: string }>(`${this.base}/properties/${id}/enquiries`, p); }
}
