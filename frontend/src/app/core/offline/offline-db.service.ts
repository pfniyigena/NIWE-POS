import { Injectable } from '@angular/core';
import { Category, Customer, PaymentMethodOption, Product } from '../models';
import { QueuedOfflineSale } from './offline-models';

const DB_NAME = 'pos-offline';
const DB_VERSION = 1;
const STORE_PRODUCTS = 'products';
const STORE_CATEGORIES = 'categories';
const STORE_PAYMENT_METHODS = 'paymentMethods';
const STORE_CUSTOMERS = 'customers';
const STORE_SALE_QUEUE = 'saleQueue';

/**
 * Thin promise wrapper around the browser's native IndexedDB — deliberately no external library,
 * to avoid a new dependency for what's a handful of simple key/value stores. Two kinds of data
 * live here (Task: "local queuing of sales that sync once connectivity returns"):
 *
 * <p>1. A read-through **cache** of the sellable catalog (products/categories/active payment
 * methods), refreshed from the server whenever the terminal is online — see OfflineSyncService.
 * This is what lets the terminal's product grid/search keep working with no connection at all.
 *
 * <p>2. The **queue** of sales rung while offline, each keyed by a client-generated
 * `clientTempId` (a UUID) so a sale is never accidentally enqueued or synced twice.
 *
 * <p>Every method degrades to a no-op/empty-result rather than throwing when IndexedDB itself is
 * unavailable (a private-browsing context in some browsers, or a very old one) — offline mode
 * simply doesn't work there, same as it wouldn't on any site, but the rest of the terminal (the
 * normal online flow) must never be affected by that.
 */
@Injectable({ providedIn: 'root' })
export class OfflineDbService {
  private dbPromise: Promise<IDBDatabase | null> | null = null;

  private open(): Promise<IDBDatabase | null> {
    if (!this.dbPromise) {
      this.dbPromise = this.openInternal();
    }
    return this.dbPromise;
  }

  private openInternal(): Promise<IDBDatabase | null> {
    return new Promise((resolve) => {
      if (typeof indexedDB === 'undefined') {
        resolve(null);
        return;
      }
      try {
        const request = indexedDB.open(DB_NAME, DB_VERSION);
        request.onupgradeneeded = () => {
          const db = request.result;
          if (!db.objectStoreNames.contains(STORE_PRODUCTS)) {
            db.createObjectStore(STORE_PRODUCTS, { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains(STORE_CATEGORIES)) {
            db.createObjectStore(STORE_CATEGORIES, { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains(STORE_PAYMENT_METHODS)) {
            db.createObjectStore(STORE_PAYMENT_METHODS, { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains(STORE_CUSTOMERS)) {
            db.createObjectStore(STORE_CUSTOMERS, { keyPath: 'id' });
          }
          if (!db.objectStoreNames.contains(STORE_SALE_QUEUE)) {
            db.createObjectStore(STORE_SALE_QUEUE, { keyPath: 'clientTempId' });
          }
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => resolve(null);
      } catch {
        resolve(null);
      }
    });
  }

  private async replaceAll<T>(storeName: string, rows: T[]): Promise<void> {
    const db = await this.open();
    if (!db) return;
    return new Promise((resolve) => {
      const tx = db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      store.clear();
      for (const row of rows) {
        store.put(row);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  }

  private async getAll<T>(storeName: string): Promise<T[]> {
    const db = await this.open();
    if (!db) return [];
    return new Promise((resolve) => {
      const tx = db.transaction(storeName, 'readonly');
      const request = tx.objectStore(storeName).getAll();
      request.onsuccess = () => resolve((request.result as T[]) ?? []);
      request.onerror = () => resolve([]);
    });
  }

  // ---- catalog cache ----

  replaceProducts(products: Product[]): Promise<void> {
    return this.replaceAll(STORE_PRODUCTS, products);
  }

  getAllProducts(): Promise<Product[]> {
    return this.getAll<Product>(STORE_PRODUCTS);
  }

  replaceCategories(categories: Category[]): Promise<void> {
    return this.replaceAll(STORE_CATEGORIES, categories);
  }

  getAllCategories(): Promise<Category[]> {
    return this.getAll<Category>(STORE_CATEGORIES);
  }

  replacePaymentMethods(methods: PaymentMethodOption[]): Promise<void> {
    return this.replaceAll(STORE_PAYMENT_METHODS, methods);
  }

  getAllPaymentMethods(): Promise<PaymentMethodOption[]> {
    return this.getAll<PaymentMethodOption>(STORE_PAYMENT_METHODS);
  }

  replaceCustomers(customers: Customer[]): Promise<void> {
    return this.replaceAll(STORE_CUSTOMERS, customers);
  }

  getAllCustomers(): Promise<Customer[]> {
    return this.getAll<Customer>(STORE_CUSTOMERS);
  }

  // ---- offline sale queue ----

  async enqueueSale(sale: QueuedOfflineSale): Promise<void> {
    const db = await this.open();
    if (!db) return;
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_SALE_QUEUE, 'readwrite');
      tx.objectStore(STORE_SALE_QUEUE).put(sale);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  }

  getQueuedSales(): Promise<QueuedOfflineSale[]> {
    return this.getAll<QueuedOfflineSale>(STORE_SALE_QUEUE);
  }

  async updateQueuedSale(sale: QueuedOfflineSale): Promise<void> {
    return this.enqueueSale(sale);
  }

  async removeQueuedSale(clientTempId: string): Promise<void> {
    const db = await this.open();
    if (!db) return;
    return new Promise((resolve) => {
      const tx = db.transaction(STORE_SALE_QUEUE, 'readwrite');
      tx.objectStore(STORE_SALE_QUEUE).delete(clientTempId);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  }
}
