/**
 * Task: "local queuing of sales that sync once connectivity returns". These are purely
 * client-side/local shapes (stored in IndexedDB — see OfflineDbService), distinct from the
 * server's Order/OrderLine models in `core/models.ts`: a queued sale hasn't been created on the
 * server at all yet, so it carries display fields (customerName, a per-line description/unitPrice
 * snapshot) the server DTOs don't need.
 */

export interface QueuedOfflineLine {
  productId: number;
  description: string;
  unitPrice: number;
  quantity: number;
  discountPercent: number;
  taxRatePercent: number | null;
  taxInclusive: boolean;
}

export interface QueuedOfflinePayment {
  method: string;
  methodName: string;
  amount: number;
}

/**
 * 'pending': queued, waiting for connectivity (or its first sync attempt). 'failed': the server
 * rejected it for a real reason (e.g. a credit-limit check) — see OfflineSyncService's javadoc-ish
 * comment on why this is kept, not silently dropped or retried forever.
 */
export type QueuedSaleStatus = 'pending' | 'failed';

export interface QueuedOfflineSale {
  clientTempId: string;
  cashierSessionId: number;
  customerId: number | null;
  /** Display only — never sent to the server, which resolves the customer fresh by id at sync time. */
  customerName: string;
  /** ISO instant — when this sale was actually rung at the terminal, not whenever it eventually syncs. */
  queuedAt: string;
  lines: QueuedOfflineLine[];
  payments: QueuedOfflinePayment[];
  /** ISO date (yyyy-MM-dd), or null. Only meaningful when a payment is on store credit. */
  dueDate: string | null;
  /** The terminal's own offline estimate of the grand total — see OfflineCalculator. */
  clientEstimatedTotal: number;
  status: QueuedSaleStatus;
  /** Set only when status is 'failed' — the server's own rejection message, so the cashier/admin knows what to fix. */
  lastError: string | null;
}
