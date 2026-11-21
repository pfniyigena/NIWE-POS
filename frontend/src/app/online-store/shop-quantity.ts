/** Quantities in the shop's cart (v1.69.0): whole pieces 1 to 20, or - for a product sold by weight or length - steps of 0.5 up to 100. */
export interface Measured { measureUnit?: string | null }

/** How much one tap on + or − changes the quantity. */
export function qtyStep(p: Measured): number {
  return p.measureUnit ? 0.5 : 1;
}

/** The most the cart may hold of the product. */
export function qtyMax(p: Measured): number {
  return p.measureUnit ? 100 : 20;
}

/** A typed quantity made valid: rounded to 3 decimals and kept between the least (0.1 for weight or length) and the most; empty, zero or below = 0 (take it out). */
export function qtyClamp(p: Measured, n: number): number {
  const r = Math.round(n * 1000) / 1000;
  if (!(r > 0)) return 0;
  return Math.max(p.measureUnit ? 0.1 : 1, Math.min(qtyMax(p), p.measureUnit ? r : Math.round(r)));
}

/** The quantity after a change: a tap on + or − (by = 1 or -1) moves one step; any other number is added as it is. Rounded to 3 decimals, kept between 0 and the most. */
export function qtyAfter(p: Measured, current: number, by: number): number {
  const delta = Math.abs(by) === 1 ? by * qtyStep(p) : by;
  const n = Math.round((current + delta) * 1000) / 1000;
  const clamped = Math.max(0, Math.min(qtyMax(p), n));
  return p.measureUnit && clamped > 0 && clamped < 0.1 ? 0 : clamped;
}
