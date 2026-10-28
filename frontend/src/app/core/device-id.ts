/**
 * A random id this browser keeps, so the server can tell which device is calling. It is sent on
 * every request as X-Device-Id and is what a till is "bound" to (see Registers > Bind to this device).
 * Clearing the browser's site data makes a new id, so a bound PC must then be bound again by an admin.
 */
const KEY = 'niwe.deviceId.v1';
let memo: string | null = null;

function fresh(): string {
  const c: Crypto | undefined = typeof crypto !== 'undefined' ? crypto : undefined;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  const b = new Uint8Array(16);
  if (c && c.getRandomValues) c.getRandomValues(b);
  else for (let i = 0; i < b.length; i++) b[i] = Math.floor(Math.random() * 256);
  return Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
}

export function getDeviceId(): string {
  if (memo) return memo;
  try {
    const v = localStorage.getItem(KEY);
    if (v && v.length >= 8 && v.length <= 64) return (memo = v);
  } catch { /* storage blocked: use an id for this page only */ }
  const id = fresh();
  try { localStorage.setItem(KEY, id); } catch { /* ignore */ }
  return (memo = id);
}
