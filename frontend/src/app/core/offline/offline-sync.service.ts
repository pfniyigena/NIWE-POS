import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { OfflineDbService } from './offline-db.service';
import { ConnectivityService } from './connectivity.service';
import { QueuedOfflineSale } from './offline-models';
import { OrderApiService, OfflineSaleRequest } from '../api/order-api.service';
import { CatalogApiService } from '../api/catalog-api.service';
import { PaymentMethodApiService } from '../api/payment-method-api.service';
import { CustomerApiService } from '../api/customer-api.service';

/**
 * Task: "local queuing of sales that sync once connectivity returns" — the two halves of offline
 * mode meet here:
 *
 * 1. **Catalog caching** ({@link refreshCatalogCache}) — called while online (terminal init, and
 *    periodically) to snapshot the sellable catalog into IndexedDB (see OfflineDbService) so the
 *    terminal's product grid/search still works with no connection at all.
 *
 * 2. **The sale queue** ({@link queueSale}, {@link syncQueue}) — a sale rung offline is queued
 *    locally with a client-generated id, then replayed on the server (one call per sale, in the
 *    order it was rung) the moment {@link ConnectivityService} reports the backend is reachable
 *    again. A sale rejected for a genuine reason (e.g. a credit-limit check) is kept, marked
 *    'failed' with the server's own message, and surfaced for the cashier/admin to review/discard
 *    — never silently dropped, and never retried forever in a tight loop. Everything else (a
 *    price/tax drift between what the terminal estimated offline and what the server computed) is
 *    accepted and flagged via SaleOrder.syncNote instead of being rejected — see
 *    OrderService.syncOfflineSale's javadoc for why (stock is never checked at checkout at all,
 *    online or offline, so there's nothing to reject there either).
 */
@Injectable({ providedIn: 'root' })
export class OfflineSyncService {
  private readonly queueSubject = new BehaviorSubject<QueuedOfflineSale[]>([]);
  /** Current contents of the offline sale queue — the terminal renders this directly. */
  readonly queue$ = this.queueSubject.asObservable();

  private syncing = false;
  private lastCatalogRefresh = 0;
  private static readonly CATALOG_REFRESH_INTERVAL_MS = 5 * 60 * 1000;

  constructor(
    private offlineDb: OfflineDbService,
    private connectivity: ConnectivityService,
    private orderApi: OrderApiService,
    private catalogApi: CatalogApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private customerApi: CustomerApiService
  ) {
    this.refreshQueueSubject();
    this.connectivity.online$.subscribe((online) => {
      if (online) {
        this.refreshCatalogCache();
        this.syncQueue();
      }
    });
  }

  private refreshQueueSubject(): void {
    this.offlineDb.getQueuedSales().then((sales) => this.queueSubject.next(sales));
  }

  /**
   * Snapshots products/categories/active payment methods into IndexedDB. Best-effort and
   * throttled to once every {@link CATALOG_REFRESH_INTERVAL_MS} — call it freely (e.g. from every
   * terminal ngOnInit); it no-ops if the last refresh was recent. `force` bypasses the throttle
   * (used right after the terminal resolves a cashier session, so a brand-new device isn't left
   * with an empty cache until the interval happens to elapse).
   */
  refreshCatalogCache(force = false): void {
    if (!this.connectivity.isOnline) return;
    const now = Date.now();
    if (!force && now - this.lastCatalogRefresh < OfflineSyncService.CATALOG_REFRESH_INTERVAL_MS) {
      return;
    }
    this.lastCatalogRefresh = now;
    this.catalogApi.getOfflineCache().subscribe({
      next: (products) => this.offlineDb.replaceProducts(products),
      error: () => {},
    });
    this.catalogApi.listCategories().subscribe({
      next: (categories) => this.offlineDb.replaceCategories(categories),
      error: () => {},
    });
    this.paymentMethodApi.listActive().subscribe({
      next: (methods) => this.offlineDb.replacePaymentMethods(methods),
      error: () => {},
    });
    this.customerApi.list().subscribe({
      next: (customers) => this.offlineDb.replaceCustomers(customers),
      error: () => {},
    });
  }

  /** Queues one finished offline sale and immediately tries to sync (a no-op if still offline). */
  async queueSale(sale: QueuedOfflineSale): Promise<void> {
    await this.offlineDb.enqueueSale(sale);
    this.refreshQueueSubject();
    if (this.connectivity.isOnline) {
      this.syncQueue();
    }
  }

  /** Removes one queue entry outright — for a 'failed' sale the cashier/admin has decided to discard rather than fix. */
  async discardQueuedSale(clientTempId: string): Promise<void> {
    await this.offlineDb.removeQueuedSale(clientTempId);
    this.refreshQueueSubject();
  }

  /**
   * Replays every 'pending' queued sale, oldest first, one at a time (so an admin reviewing the
   * queue never sees two in flight at once, and a later sale never lands before an earlier one on
   * the same order history). Stops at the first genuine network failure — that almost certainly
   * means connectivity dropped again mid-sync, and the rest will get another chance once
   * {@link ConnectivityService} reports online again. A real rejection from the server (a
   * validation error, not a network failure) marks that one sale 'failed' and moves on to the
   * next — it's a problem with that specific sale, not with connectivity.
   */
  async syncQueue(): Promise<void> {
    if (this.syncing) return;
    this.syncing = true;
    try {
      const queue = await this.offlineDb.getQueuedSales();
      const pending = queue
        .filter((s) => s.status === 'pending')
        .sort((a, b) => a.queuedAt.localeCompare(b.queuedAt));
      for (const sale of pending) {
        if (!this.connectivity.isOnline) break;
        const ok = await this.syncOne(sale);
        if (!ok.shouldContinue) break;
      }
    } finally {
      this.syncing = false;
      this.refreshQueueSubject();
    }
  }

  private syncOne(sale: QueuedOfflineSale): Promise<{ shouldContinue: boolean }> {
    const request: OfflineSaleRequest = {
      clientTempId: sale.clientTempId,
      cashierSessionId: sale.cashierSessionId,
      customerId: sale.customerId,
      queuedAt: sale.queuedAt,
      lines: sale.lines.map((l) => ({
        productId: l.productId,
        quantity: l.quantity,
        discountPercent: l.discountPercent,
      })),
      payments: sale.payments.map((p) => ({ method: p.method, amount: p.amount })),
      dueDate: sale.dueDate,
      clientEstimatedTotal: sale.clientEstimatedTotal,
    };
    return new Promise((resolve) => {
      this.orderApi.syncOffline(request).subscribe({
        next: async () => {
          await this.offlineDb.removeQueuedSale(sale.clientTempId);
          resolve({ shouldContinue: true });
        },
        error: async (err) => {
          if (err?.status === 0) {
            // No response at all — a genuine network failure, not a rejection. Connectivity almost
            // certainly just dropped again; leave this sale 'pending' and stop for now.
            this.connectivity.reportNetworkFailure();
            resolve({ shouldContinue: false });
            return;
          }
          // A real answer from the server saying no — keep the sale, mark why, keep going.
          sale.status = 'failed';
          sale.lastError = err?.error?.message ?? 'The server rejected this sale when syncing it';
          await this.offlineDb.updateQueuedSale(sale);
          resolve({ shouldContinue: true });
        },
      });
    });
  }
}
