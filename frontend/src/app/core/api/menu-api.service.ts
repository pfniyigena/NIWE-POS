import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { MenuCategory, MenuItem, OptionGroup } from '../models';

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
  allergens?: string | null;
  defaultCourse?: number | null;
  /** Minutes the kitchen should need; blank leaves it, 0 clears it. */
  prepMinutes?: number | null;
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

  /** Marks an item sold out (86'd) or back on; any signed-in user may. */
  setSoldOut(id: number, soldOut: boolean, reason?: string | null): Observable<MenuItem> {
    return this.http.post<MenuItem>(`${API_BASE}/restaurant/menu/items/${id}/sold-out`, { soldOut, reason: reason || null });
  }

  allergens(): Observable<string[]> {
    return this.http.get<string[]>(`${API_BASE}/restaurant/menu/allergens`);
  }

  listOptions(itemId: number): Observable<OptionGroup[]> {
    return this.http.get<OptionGroup[]>(`${API_BASE}/restaurant/menu/items/${itemId}/options`);
  }

  /** Saves the whole set of option groups of one item. */
  saveOptions(itemId: number, groups: OptionGroup[]): Observable<OptionGroup[]> {
    return this.http.put<OptionGroup[]>(`${API_BASE}/restaurant/menu/items/${itemId}/options`, groups);
  }

  removeItem(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/restaurant/menu/items/${id}`);
  }
}
