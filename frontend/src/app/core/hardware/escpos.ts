/**
 * ESC/POS command builder for thermal receipt printers (Epson TM-T20 / T82 / T88 and compatibles)
 * and the text encoder shared with the COM customer display. Pure TypeScript: no Angular and no
 * browser API is needed here, so it is unit-tested with plain Node.
 */

export type Codepage = 'cp858' | 'cp850' | 'ascii';
export type Align = 'left' | 'center' | 'right';

/** Upper half (0x80-0xFF) of code page 850; 858 only differs at 0xD5 (euro sign instead of dotless i). */
const CP850_HIGH =
  'ÇüéâäàåçêëèïîìÄÅ' +
  'ÉæÆôöòûùÿÖÜø£Ø×ƒ' +
  'áíóúñÑªº¿®¬½¼¡«»' +
  '░▒▓│┤ÁÂÀ©╣║╗╝¢¥┐' +
  '└┴┬├─┼ãÃ╚╔╩╦╠═╬¤' +
  'ðÐÊËÈıÍÎÏ┘┌█▄¦Ì▀' +
  'ÓßÔÒõÕµþÞÚÛÙýÝ¯´' +
  '­±‗¾¶§÷¸°¨·¹³²■ ';

const ENCODE_850 = new Map<string, number>();
const ENCODE_858 = new Map<string, number>();
for (let i = 0; i < CP850_HIGH.length; i++) {
  ENCODE_850.set(CP850_HIGH[i], 0x80 + i);
  ENCODE_858.set(CP850_HIGH[i], 0x80 + i);
}
ENCODE_858.delete('ı');
ENCODE_858.set('€', 0xd5);

/** Characters that have no code-page equivalent but a sensible plain replacement. */
const SUBSTITUTE: Record<string, string> = {
  '–': '-', '—': '-', '−': '-', '‘': "'", '’': "'", '“': '"', '”': '"',
  '…': '...', '×': 'x', '•': '*', '·': '-', '₦': 'N', ' ': ' ', '→': '>', '✓': 'v', 'œ': 'oe', 'Œ': 'OE',
};

/** Removes accents so the text survives a printer without a Latin code page. */
export function foldAscii(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

/** Encodes text for the chosen code page; anything unknown becomes '?'. */
export function encodeText(s: string, cp: Codepage = 'cp858'): number[] {
  const out: number[] = [];
  for (const raw of s) {
    let ch = raw;
    if (SUBSTITUTE[ch] !== undefined && !(cp !== 'ascii' && ch === '×' && (cp === 'cp850' || cp === 'cp858'))) ch = SUBSTITUTE[ch];
    for (const c of ch) {
      const code = c.codePointAt(0)!;
      if (code === 10) { out.push(10); continue; }
      if (code >= 0x20 && code < 0x7f) { out.push(code); continue; }
      if (cp === 'ascii') {
        const f = foldAscii(c);
        out.push(f.length === 1 && f.charCodeAt(0) >= 0x20 && f.charCodeAt(0) < 0x7f ? f.charCodeAt(0) : 0x3f);
        continue;
      }
      const hit = (cp === 'cp858' ? ENCODE_858 : ENCODE_850).get(c);
      if (hit !== undefined) { out.push(hit); continue; }
      const f = foldAscii(c);
      out.push(f.length === 1 && f.charCodeAt(0) >= 0x20 && f.charCodeAt(0) < 0x7f ? f.charCodeAt(0) : 0x3f);
    }
  }
  return out;
}

/** Word-wraps to the given width; words longer than the width are cut. Always returns at least one line. */
export function wrapText(text: string, width: number): string[] {
  const lines: string[] = [];
  for (const para of String(text ?? '').split(/\r?\n/)) {
    let cur = '';
    for (const word of para.split(/\s+/).filter((w) => w.length > 0)) {
      let w = word;
      while (w.length > width) {
        if (cur) { lines.push(cur); cur = ''; }
        lines.push(w.slice(0, width));
        w = w.slice(width);
      }
      if (!cur) cur = w;
      else if (cur.length + 1 + w.length <= width) cur += ' ' + w;
      else { lines.push(cur); cur = w; }
    }
    lines.push(cur);
  }
  return lines.length ? lines : [''];
}

/** A black-and-white bitmap, 1 = black, rows packed 8 pixels to the byte, most significant bit first. */
export interface Raster {
  width: number;
  height: number;
  data: Uint8Array;
}

/** Converts RGBA pixels (as from a canvas) to a Raster. Transparent pixels count as white. */
export function rasterFromRgba(rgba: ArrayLike<number>, width: number, height: number, threshold = 160): Raster {
  const bytesPerRow = Math.ceil(width / 8);
  const data = new Uint8Array(bytesPerRow * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const a = rgba[i + 3] / 255;
      const lum = (0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2]) * a + 255 * (1 - a);
      if (lum < threshold) data[y * bytesPerRow + (x >> 3)] |= 0x80 >> (x & 7);
    }
  }
  return { width, height, data };
}

/** Browser only: loads an image (data URL) and returns it as a Raster at most maxWidth dots wide; null if it cannot be read. */
export async function rasterFromDataUrl(url: string | null | undefined, maxWidth: number, maxHeight = 200): Promise<Raster | null> {
  if (!url || typeof document === 'undefined') return null;
  try {
    const img = new Image();
    await new Promise<void>((resolve, reject) => {
      img.onload = () => resolve();
      img.onerror = () => reject(new Error('image'));
      img.src = url;
    });
    const scale = Math.min(1, maxWidth / (img.naturalWidth || 1), maxHeight / (img.naturalHeight || 1));
    const w = Math.max(8, Math.round((img.naturalWidth || 1) * scale));
    const h = Math.max(1, Math.round((img.naturalHeight || 1) * scale));
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, 0, 0, w, h);
    return rasterFromRgba(ctx.getImageData(0, 0, w, h).data, w, h);
  } catch {
    return null;
  }
}

/** Puts one picture at the left edge and one at the right edge of a strip `width` dots wide (revenue-authority logo and EFRIS logo). */
export function composeEnds(left: Raster | null, right: Raster | null, width: number): Raster | null {
  if (!left && !right) return null;
  const w = Math.max(8, width);
  const h = Math.max(left?.height ?? 0, right?.height ?? 0);
  const bpr = Math.ceil(w / 8);
  const data = new Uint8Array(bpr * h);
  const blit = (src: Raster | null, x0: number) => {
    if (!src) return;
    const sb = Math.ceil(src.width / 8);
    for (let y = 0; y < src.height; y++) {
      for (let x = 0; x < src.width; x++) {
        if (src.data[y * sb + (x >> 3)] & (0x80 >> (x & 7))) {
          const dx = x0 + x;
          if (dx >= 0 && dx < w) data[y * bpr + (dx >> 3)] |= 0x80 >> (dx & 7);
        }
      }
    }
  };
  blit(left, 0);
  blit(right, w - (right?.width ?? 0));
  return { width: w, height: h, data };
}

export class EscPosBuilder {
  private buf: number[] = [];
  private widthMul = 1;

  /** cols = characters per line in the normal font (48 on 80 mm, 32 on 58 mm paper with Font A). */
  constructor(public readonly cols = 48, public readonly codepage: Codepage = 'cp858') {
    this.init();
  }

  raw(...bytes: number[]): this {
    for (const b of bytes) this.buf.push(b & 0xff);
    return this;
  }

  /** ESC @ and the code page. */
  init(): this {
    this.raw(0x1b, 0x40);
    if (this.codepage === 'cp858') this.raw(0x1b, 0x74, 19);
    else if (this.codepage === 'cp850') this.raw(0x1b, 0x74, 2);
    this.widthMul = 1;
    return this;
  }

  align(a: Align): this {
    return this.raw(0x1b, 0x61, a === 'left' ? 0 : a === 'center' ? 1 : 2);
  }

  bold(on = true): this {
    return this.raw(0x1b, 0x45, on ? 1 : 0);
  }

  /** Character size multiplier 1-8 for width and height. */
  size(w = 1, h = 1): this {
    const cw = Math.min(8, Math.max(1, Math.round(w)));
    const ch = Math.min(8, Math.max(1, Math.round(h)));
    this.widthMul = cw;
    return this.raw(0x1d, 0x21, ((cw - 1) << 4) | (ch - 1));
  }

  /** White text on black. */
  invert(on = true): this {
    return this.raw(0x1d, 0x42, on ? 1 : 0);
  }

  /** Characters per line at the current size. */
  get width(): number {
    return Math.max(8, Math.floor(this.cols / this.widthMul));
  }

  text(s: string): this {
    for (const b of encodeText(s, this.codepage)) this.buf.push(b);
    return this;
  }

  /** Prints a line (no wrapping). */
  line(s = ''): this {
    return this.text(s).raw(0x0a);
  }

  /** Prints wrapped text with the current alignment. */
  wrap(s: string, indent = 0): this {
    const pad = ' '.repeat(Math.max(0, indent));
    for (const l of wrapText(s, Math.max(4, this.width - pad.length))) this.line(pad + l);
    return this;
  }

  centered(s: string): this {
    this.align('center');
    this.wrap(s);
    return this.align('left');
  }

  /** Left text and right text on one line; the left part wraps when it does not fit. */
  row(left: string, right = '', rightMin = 0): this {
    const w = this.width;
    const r = String(right ?? '');
    const rw = Math.max(r.length, rightMin);
    const leftW = Math.max(4, w - rw - 1);
    const parts = wrapText(left, leftW);
    for (let i = 0; i < parts.length; i++) {
      const last = i === parts.length - 1;
      if (last) {
        const pad = Math.max(1, w - parts[i].length - r.length);
        this.line(parts[i] + ' '.repeat(pad) + r);
      } else this.line(parts[i]);
    }
    return this;
  }

  rule(ch = '-'): this {
    return this.line(ch.repeat(this.width));
  }

  feed(n = 1): this {
    return this.raw(0x1b, 0x64, Math.min(255, Math.max(0, n)));
  }

  /** Feeds and cuts (partial cut). Printers without a cutter ignore the command. */
  cut(feedDots = 0): this {
    return this.raw(0x1d, 0x56, 66, feedDots);
  }

  /** Cash drawer pulse on pin 2 (pin 0) or pin 5 (pin 1); durations in 2 ms units. */
  drawer(pin: 0 | 1 = 0, on = 25, off = 250): this {
    return this.raw(0x1b, 0x70, pin, on, off);
  }

  /** Native QR code (model 2). Printers without GS ( k support ignore it. */
  qr(data: string, moduleSize = 6, ec: 'L' | 'M' | 'Q' | 'H' = 'M'): this {
    const bytes = encodeText(data, 'ascii');
    const len = bytes.length + 3;
    if (len > 7000) return this;
    this.raw(0x1d, 0x28, 0x6b, 4, 0, 0x31, 0x41, 0x32, 0);
    this.raw(0x1d, 0x28, 0x6b, 3, 0, 0x31, 0x43, Math.min(16, Math.max(1, moduleSize)));
    this.raw(0x1d, 0x28, 0x6b, 3, 0, 0x31, 0x45, 0x30 + { L: 0, M: 1, Q: 2, H: 3 }[ec]);
    this.raw(0x1d, 0x28, 0x6b, len & 0xff, len >> 8, 0x31, 0x50, 0x30);
    for (const b of bytes) this.buf.push(b);
    this.raw(0x1d, 0x28, 0x6b, 3, 0, 0x31, 0x51, 0x30);
    return this;
  }

  /** GS v 0 raster image, centred by the current alignment. */
  image(r: Raster | null): this {
    if (!r || r.width <= 0 || r.height <= 0) return this;
    const bpr = Math.ceil(r.width / 8);
    // The raster command takes at most 255 rows x 8 per block on some printers; send in strips.
    const strip = 128;
    for (let y = 0; y < r.height; y += strip) {
      const h = Math.min(strip, r.height - y);
      this.raw(0x1d, 0x76, 0x30, 0, bpr & 0xff, bpr >> 8, h & 0xff, h >> 8);
      for (let i = y * bpr; i < (y + h) * bpr; i++) this.buf.push(r.data[i]);
    }
    return this;
  }

  bytes(): Uint8Array {
    return Uint8Array.from(this.buf);
  }
}

/** Plain-text view of an ESC/POS job for tests and the on-screen preview: control sequences are dropped, line feeds kept. */
export function previewText(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i];
    if (b === 0x1b) {
      const c = bytes[i + 1];
      if (c === 0x40) i += 1;
      else if (c === 0x70) i += 4;
      else if (c === 0x64 || c === 0x74 || c === 0x61 || c === 0x45 || c === 0x4d || c === 0x2d) i += 2;
      else i += 1;
    } else if (b === 0x1d) {
      const c = bytes[i + 1];
      if (c === 0x21 || c === 0x42) i += 2;
      else if (c === 0x56) i += 3;
      else if (c === 0x28 && bytes[i + 2] === 0x6b) i += 4 + (bytes[i + 3] | (bytes[i + 4] << 8));
      else if (c === 0x76 && bytes[i + 2] === 0x30) {
        const bpr = bytes[i + 4] | (bytes[i + 5] << 8);
        const h = bytes[i + 6] | (bytes[i + 7] << 8);
        out += '[image]\n';
        i += 7 + bpr * h;
      } else i += 1;
    } else if (b === 0x0a) out += '\n';
    else if (b >= 0x20 && b < 0x7f) out += String.fromCharCode(b);
    else if (b >= 0x80) out += CP850_HIGH[b - 0x80] ?? '?';
  }
  return out;
}

/** Number with thousands separators, "12,345.60". */
export function money(n: number | null | undefined, decimals = 2): string {
  const v = Number(n ?? 0);
  if (!isFinite(v)) return '0';
  const s = Math.abs(v).toFixed(decimals);
  const [i, d] = s.split('.');
  const withSep = i.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return (v < 0 && Number(s) !== 0 ? '-' : '') + withSep + (d ? '.' + d : '');
}

/** Quantity with up to four decimals, trailing zeros removed ("2", "2.5"). */
export function qty(n: number | null | undefined): string {
  const v = Number(n ?? 0);
  if (!isFinite(v)) return '0';
  return String(parseFloat(v.toFixed(4)));
}
