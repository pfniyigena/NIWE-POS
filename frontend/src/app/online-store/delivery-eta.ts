import { T } from '../core/i18n/t';

/** "1 to 2 days after the order is accepted", one wording per case (same words as the messages the server sends). */
export function etaDaysText(min: number | null | undefined, max: number | null | undefined): string {
  if (min === null || min === undefined || max === null || max === undefined) return '';
  if (max === 0) return T('Estimated delivery: the same day we accept your order.');
  if (min === max) return min === 1 ? T('Estimated delivery: 1 day after we accept your order.') : T('Estimated delivery: {p0} days after we accept your order.', { p0: min });
  if (min === 0) return T('Estimated delivery: within {p0} days after we accept your order.', { p0: max });
  return T('Estimated delivery: {p0} to {p1} days after we accept your order.', { p0: min, p1: max });
}

/** "Thu 26 Nov – Sat 28 Nov", counted from today (what the customer can expect if the order is accepted today). */
export function etaDates(min: number | null | undefined, max: number | null | undefined, lang: string): string {
  if (min === null || min === undefined || max === null || max === undefined) return '';
  const fmt = (days: number): string => {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toLocaleDateString(lang === 'fr' ? 'fr-FR' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
  };
  const a = fmt(min);
  const b = fmt(max);
  return a === b ? a : `${a} – ${b}`;
}
