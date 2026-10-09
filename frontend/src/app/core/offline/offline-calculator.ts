import { QueuedOfflineLine } from './offline-models';

/**
 * A simplified TypeScript port of the backend's `OrderCalculator` (see
 * `backend/.../order/OrderCalculator.java`) — used ONLY to show a running subtotal/tax/total on
 * the terminal while it's offline and there's no server to ask. This is an ESTIMATE: the server
 * remains the sole authority on the final price/tax at sync time (see `OrderService
 * .syncOfflineSale`), which is exactly why a queued sale's `clientEstimatedTotal` is compared
 * against the server's real total and flagged (not silently trusted) on any material mismatch.
 * Minor rounding differences between this and BigDecimal/HALF_UP on the backend are expected and
 * harmless for that reason — this never has the final say on what a customer is charged.
 */

interface LineLike {
  unitPrice: number;
  quantity: number;
  discountPercent: number;
  taxRatePercent: number | null;
  taxInclusive: boolean;
}

function round2(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

export function lineTotal(line: LineLike): number {
  const gross = line.unitPrice * line.quantity;
  const factor = 1 - (line.discountPercent || 0) / 100;
  return round2(gross * factor);
}

export function taxOnAmount(amount: number, ratePercent: number | null, inclusive: boolean): number {
  if (ratePercent == null) return 0;
  const fraction = ratePercent / 100;
  if (inclusive) {
    return round2((amount * fraction) / (1 + fraction));
  }
  return round2(amount * fraction);
}

export function lineTax(line: LineLike): number {
  return taxOnAmount(lineTotal(line), line.taxRatePercent, !!line.taxInclusive);
}

/** Pre-tax (net) contribution to the sale — same "back the tax out of an inclusive line" rule as OrderCalculator.lineNetTotal. */
export function lineNetTotal(line: LineLike): number {
  if (!line.taxInclusive) return lineTotal(line);
  return round2(lineTotal(line) - lineTax(line));
}

export function subtotal(lines: QueuedOfflineLine[]): number {
  return round2(lines.reduce((sum, l) => sum + lineNetTotal(l), 0));
}

export function taxTotal(lines: QueuedOfflineLine[]): number {
  return round2(lines.reduce((sum, l) => sum + lineTax(l), 0));
}

export function grandTotal(lines: QueuedOfflineLine[]): number {
  return round2(subtotal(lines) + taxTotal(lines));
}

export function totalQuantity(lines: QueuedOfflineLine[]): number {
  return lines.reduce((sum, l) => sum + l.quantity, 0);
}
