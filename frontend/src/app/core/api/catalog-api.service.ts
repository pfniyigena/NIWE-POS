import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { Brand, Category, PageResponse, Product, WarehouseLowStockRow } from '../models';

export interface CategoryPayload {
  name: string;
  colorHex: string;
}

export interface BrandPayload {
  name: string;
}

export interface ProductPayload {
  name: string;
  sku?: string | null;
  barcode?: string | null;
  /** Nullable, but must be unique if set — see Product.code. */
  code?: string | null;
  /** A data: URL, or null. */
  iconDataUrl?: string | null;
  /** Nullable — see Product.description. */
  description?: string | null;
  unitPrice: number;
  costPrice: number;
  stockQuantity: number;
  active: boolean;
  categoryId: number | null;
  /** Nullable — see Product.brand. */
  brandId?: number | null;
  taxRateId: number | null;
  /** EFRIS commodity/tariff code ("unsp_code" on an EFRIS invoice line) — see Product.unspCode. Optional. */
  unspCode?: string | null;
  /** Goods (false, default) or service (true) — feeds EFRIS Chad's item_nature. See Product.service. Optional. */
  service?: boolean;
  /** Optional reorder rule — see Product.reorderLevel. */
  reorderLevel?: number | null;
  maxStockLevel?: number | null;
  trackingMode?: 'NONE' | 'BATCH' | 'SERIAL';
}

@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  constructor(private http: HttpClient) {}

  listCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${API_BASE}/categories`);
  }

  createCategory(payload: CategoryPayload): Observable<Category> {
    return this.http.post<Category>(`${API_BASE}/categories`, payload);
  }

  updateCategory(id: number, payload: CategoryPayload): Observable<Category> {
    return this.http.put<Category>(`${API_BASE}/categories/${id}`, payload);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/categories/${id}`);
  }

  listBrands(): Observable<Brand[]> {
    return this.http.get<Brand[]>(`${API_BASE}/brands`);
  }

  createBrand(payload: BrandPayload): Observable<Brand> {
    return this.http.post<Brand>(`${API_BASE}/brands`, payload);
  }

  updateBrand(id: number, payload: BrandPayload): Observable<Brand> {
    return this.http.put<Brand>(`${API_BASE}/brands/${id}`, payload);
  }

  deleteBrand(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/brands/${id}`);
  }

  searchProducts(categoryId: number | null, q: string | null): Observable<Product[]> {
    let query = '';
    const params: string[] = [];
    if (categoryId != null) params.push(`categoryId=${categoryId}`);
    if (q) params.push(`q=${encodeURIComponent(q)}`);
    if (params.length) query = `?${params.join('&')}`;
    return this.http.get<Product[]>(`${API_BASE}/products${query}`);
  }

  /**
   * Admin-only, paged (Task: "check the loading of 100000 products" — this used to fetch every
   * product in one unbounded list). Includes deactivated products so they can be reactivated.
   */
  listAllProducts(q: string | null, page: number, size: number): Observable<PageResponse<Product>> {
    const params: string[] = [`page=${page}`, `size=${size}`];
    if (q) params.push(`q=${encodeURIComponent(q)}`);
    return this.http.get<PageResponse<Product>>(`${API_BASE}/products/all?${params.join('&')}`);
  }

  /** Admin-only: fetches one product by id, for the edit form — see the backend's CatalogService.getByIdForAdmin. */
  listVariants(templateId: number): Observable<Product[]> {
    return this.http.get<Product[]>(`${API_BASE}/products/${templateId}/variants`);
  }

  /** Generates one product per combination of the given options from a template product. */
  generateVariants(templateId: number, attributes: { name: string; values: string[] }[]): Observable<{ created: number; skipped: number; variants: Product[] }> {
    return this.http.post<{ created: number; skipped: number; variants: Product[] }>(`${API_BASE}/products/${templateId}/variants`, { attributes });
  }

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`${API_BASE}/products/${id}`);
  }

  /** Multi-warehouse phase 1: low stock inside one warehouse, using that warehouse's own sellable quantity. */
  listLowStockInWarehouse(warehouseId: number): Observable<WarehouseLowStockRow[]> {
    return this.http.get<WarehouseLowStockRow[]>(`${API_BASE}/products/low-stock-by-warehouse`, { params: { warehouseId: String(warehouseId) } });
  }

  listLowStock(): Observable<Product[]> {
    return this.http.get<Product[]>(`${API_BASE}/products/low-stock`);
  }

  /** Task: "local queuing of sales that sync once connectivity returns" — primes the terminal's offline catalog cache (every active product, up to a fixed ceiling — see the backend's CatalogService.listForOfflineCache). */
  getOfflineCache(): Observable<Product[]> {
    return this.http.get<Product[]>(`${API_BASE}/products/offline-cache`);
  }

  createProduct(payload: ProductPayload): Observable<Product> {
    return this.http.post<Product>(`${API_BASE}/products`, payload);
  }

  updateProduct(id: number, payload: ProductPayload): Observable<Product> {
    return this.http.put<Product>(`${API_BASE}/products/${id}`, payload);
  }

  /** Soft-delete: deactivates rather than removes, since historical order lines may reference it. */
  deactivateProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/products/${id}`);
  }

  /** Fourier: "barcode/label generation for products that don't already carry a manufacturer barcode" — see the backend CatalogService.generateBarcode's own javadoc. Only succeeds when the product doesn't already have a barcode. */
  generateBarcode(id: number): Observable<Product> {
    return this.http.post<Product>(`${API_BASE}/products/${id}/generate-barcode`, {});
  }
}
