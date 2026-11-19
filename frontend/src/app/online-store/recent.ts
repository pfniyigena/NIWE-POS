/** The products the visitor looked at lately, kept in their own browser only (never sent anywhere except as ids when the shop shows them again). */
const KEY = 'niwe.shop.recent';
const MAX = 12;

export function recentIds(): number[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(v) ? v.filter((x) => Number.isInteger(x) && x > 0).slice(0, MAX) : [];
  } catch {
    return [];
  }
}

export function rememberProduct(id: number): void {
  try {
    const list = [id, ...recentIds().filter((x) => x !== id)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {
    // browser storage can be blocked: the feature is only a convenience
  }
}
