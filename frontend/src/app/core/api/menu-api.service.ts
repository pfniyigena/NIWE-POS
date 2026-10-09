import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { MenuCategory, MenuItem } from '../models';

export interface MenuCategoryPayload {
  name: string;
  sortOrder: number;
  active: boolean;
}

export interface MenuItemPayload {
  categoryId: number;
  productId: number;
  sortOrder: number;
  active: boolean;
  priceOverride?: number | null;
  station?: string | null;
}

/** Admin > Restaurant > Menu (Fourier: "Menu & menu categories," reusing existing products). */
@Injectable({ providedIn: 'root' })
export class MenuApiService {
  constructor(private http: HttpClient) {}

  listCategories(): Observable<MenuCategory[]> {
    return this.http.get<MenuCategory[]>(`${API_BASE}/restaurant/menu/categories`);
  }

  createCategory(payload: MenuCategoryPayload): Observable<MenuCategory> {
    return this.http.post<MenuCategory>(`${API_BASE}/restaurant/menu/categories`, payload);
  }

  updateCategory(id: number, payload: MenuCategoryPayload): Observable<MenuCategory> {
    return this.http.put<MenuCategory>(`${API_BASE}/restaurant/menu/categories/${id}`, payload);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/restaurant/menu/categories/${id}`);
  }

  listItems(categoryId?: number | null): Observable<MenuItem[]> {
    const query = categoryId != null ? `?categoryId=${categoryId}` : '';
    return this.http.get<MenuItem[]>(`${API_BASE}/restaurant/menu/items${query}`);
  }

  addItem(payload: MenuItemPayload): Observable<MenuItem> {
    return this.http.post<MenuItem>(`${API_BASE}/restaurant/menu/items`, payload);
  }

  updateItem(id: number, payload: MenuItemPayload): Observable<MenuItem> {
    return this.http.put<MenuItem>(`${API_BASE}/restaurant/menu/items/${id}`, payload);
  }

  removeItem(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/restaurant/menu/items/${id}`);
  }
}
