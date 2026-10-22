/** Small helpers for the time tracking screens. Dates are plain "YYYY-MM-DD" strings in the user's own calendar. */

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n);
}

export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function parseIso(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const d = parseIso(iso);
  d.setDate(d.getDate() + days);
  return isoDate(d);
}

/** The Monday of the week containing this date. */
export function mondayOf(iso: string): string {
  const d = parseIso(iso);
  const back = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - back);
  return isoDate(d);
}

export function today(): string {
  return isoDate(new Date());
}

/**
 * Reads what a person types for a duration: "1.5", "1,5", "1:30", "90m", "2h", "1h30". Returns whole minutes, or null when it
 * is not understood.
 */
export function parseDuration(text: string | number | null | undefined): number | null {
  if (text == null) return null;
  const t = String(text).trim().toLowerCase().replace(',', '.');
  if (!t) return null;
  let m = /^(\d+):(\d{1,2})$/.exec(t);
  if (m) return Number(m[1]) * 60 + Number(m[2]);
  m = /^(\d+(?:\.\d+)?)\s*h(?:ours?|rs?)?\s*(?:(\d+)\s*(?:m(?:in(?:ute)?s?)?)?)?$/.exec(t);
  if (m) return Math.round(Number(m[1]) * 60 + (m[2] ? Number(m[2]) : 0));
  m = /^(\d+)\s*m(?:in(?:ute)?s?)?$/.exec(t);
  if (m) return Number(m[1]);
  m = /^\d+(?:\.\d+)?$/.exec(t);
  if (m) return Math.round(Number(t) * 60);
  return null;
}

/** 90 → "1:30". */
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}:${pad(m)}`;
}

/** 3725 seconds → "1:02:05". */
export function formatClock(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h}:${pad(m)}:${pad(s)}`;
}
