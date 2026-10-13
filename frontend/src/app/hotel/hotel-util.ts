import { BookingStatus } from '../core/api/hotel-api.service';

/** yyyy-MM-dd in the browser's local time (dates are plain dates on the server, no time zone). */
export function iso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export function today(): string {
  return iso(new Date());
}

export function addDays(date: string, days: number): string {
  const d = new Date(date + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return iso(d);
}

export function nightsBetween(from: string, to: string): number {
  const a = new Date(from + 'T00:00:00').getTime();
  const b = new Date(to + 'T00:00:00').getTime();
  return Math.round((b - a) / 86400000);
}

/** Badge class for a booking status. */
export function statusBadge(status: BookingStatus): string {
  switch (status) {
    case 'CHECKED_IN':
      return 'badge-success';
    case 'BOOKED':
      return 'badge-warning';
    case 'CANCELLED':
    case 'NO_SHOW':
      return 'badge-danger';
    default:
      return 'badge-secondary';
  }
}

export function statusLabel(status: string): string {
  return status.replace('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function errMsg(err: any, fallback: string): string {
  return err?.error?.message ?? fallback;
}
