/** Quantities are decimal numbers kept to four decimals; this tidies floating-point noise (0.1 + 0.2) after arithmetic. */
export function q4(n: number): number {
  return Math.round(n * 10000) / 10000;
}
