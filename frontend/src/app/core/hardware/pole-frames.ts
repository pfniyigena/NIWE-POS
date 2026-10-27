import { DisplayProtocol } from './device-settings';
import { encodeText } from './escpos';

/** The bytes that put two lines on a 2 x N customer display. Exported for tests. */
export function displayFrame(protocol: DisplayProtocol, cols: number, line1: string, line2: string, clear = false): Uint8Array {
  const fit = (s: string) => {
    const t = String(s ?? '');
    return t.length > cols ? t.slice(0, cols) : t.padEnd(cols, ' ');
  };
  const out: number[] = [];
  const add = (...b: number[]) => out.push(...b);
  const txt = (s: string) => out.push(...encodeText(s, 'ascii').filter((b) => b !== 10));
  if (protocol === 'cd5220') {
    if (clear) add(0x0c);
    add(0x1b, 0x51, 0x41);
    txt(fit(line1));
    add(0x0d);
    add(0x1b, 0x51, 0x42);
    txt(fit(line2));
    add(0x0d);
  } else if (protocol === 'plain') {
    add(0x0c);
    txt(fit(line1));
    add(0x0d, 0x0a);
    txt(fit(line2));
  } else {
    // Epson DM-D / ESC/POS style: position the cursor with US $ x y, then write the line.
    if (clear) add(0x0c);
    add(0x1f, 0x24, 1, 1);
    txt(fit(line1));
    add(0x1f, 0x24, 1, 2);
    txt(fit(line2));
  }
  return Uint8Array.from(out);
}

/** Left and right text on one display line. */
export function lr(left: string, right: string, cols: number): string {
  const r = String(right);
  const l = String(left).slice(0, Math.max(0, cols - r.length - 1));
  return l + ' '.repeat(Math.max(1, cols - l.length - r.length)) + r;
}

