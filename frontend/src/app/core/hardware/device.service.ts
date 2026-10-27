import { Injectable } from '@angular/core';
import {
  DEFAULT_DEVICE_SETTINGS,
  DeviceSettings,
  KitchenPrinterConn,
  PrinterConn,
  STORAGE_KEY,
  colsFor,
  mergeSettings,
} from './device-settings';
import { EscPosBuilder, previewText } from './escpos';

export type PrinterSlot = 'receipt' | 'kitchen' | 'bar';

export interface SendResult {
  ok: boolean;
  /** Why it did not go to the device (the caller then falls back to the Windows driver). */
  error?: string;
}

const nav = (): any => (typeof navigator !== 'undefined' ? (navigator as any) : {});

/**
 * The devices of this workstation: receipt, kitchen and bar printers (Windows driver, or raw ESC/POS over USB / serial)
 * and the COM customer display. Settings are kept in this browser's local storage.
 */
@Injectable({ providedIn: 'root' })
export class DeviceService {
  private current: DeviceSettings;
  /** Last error per slot, shown on the Devices page. */
  readonly lastError: Record<string, string | null> = { receipt: null, kitchen: null, bar: null, display: null };

  constructor() {
    this.current = this.read();
  }

  get settings(): DeviceSettings {
    return this.current;
  }

  get supportsSerial(): boolean {
    return !!nav().serial;
  }

  get supportsUsb(): boolean {
    return !!nav().usb;
  }

  private read(): DeviceSettings {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return mergeSettings(raw ? JSON.parse(raw) : null);
    } catch {
      return mergeSettings(null);
    }
  }

  save(next: DeviceSettings): void {
    this.current = mergeSettings(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.current));
    } catch {
      /* private window: the settings last for this page only */
    }
  }

  reset(): void {
    this.save(JSON.parse(JSON.stringify(DEFAULT_DEVICE_SETTINGS)));
  }

  conn(slot: PrinterSlot): PrinterConn | KitchenPrinterConn {
    return this.current[slot];
  }

  /** True when the slot sends raw ESC/POS to a device instead of using the browser's print dialog. */
  isDirect(slot: PrinterSlot): boolean {
    const c = this.current[slot];
    return (c.mode === 'usb' && !!c.usb) || (c.mode === 'serial' && !!c.serial);
  }

  /** Roll width and characters per line for a job; serverPaper is POS settings > Receipt paper. */
  layout(serverPaper: string | null | undefined): { paper: '80' | '58'; cols: number } {
    const p = this.current.paper === 'auto' ? (serverPaper === '58' ? '58' : '80') : this.current.paper;
    return { paper: p, cols: colsFor(p, this.current.cols) };
  }

  newBuilder(serverPaper: string | null | undefined): EscPosBuilder {
    const l = this.layout(serverPaper);
    return new EscPosBuilder(l.cols, this.current.codepage);
  }

  /** Which printer takes a station's ticket: a printer whose station list names it, else the kitchen printer, else the receipt printer. */
  slotForStation(station: string | null | undefined): PrinterSlot {
    const st = (station ?? '').trim().toLowerCase();
    const names = (c: KitchenPrinterConn) => c.stations.split(',').map((x) => x.trim().toLowerCase()).filter((x) => x);
    if (this.isDirect('bar') && st && names(this.current.bar).includes(st)) return 'bar';
    if (this.isDirect('kitchen') && st && names(this.current.kitchen).includes(st)) return 'kitchen';
    if (this.isDirect('kitchen')) return 'kitchen';
    if (this.isDirect('bar') && !names(this.current.bar).length) return 'bar';
    return 'receipt';
  }

  // ---- pairing (must run from a click) -------------------------------------------------------------------------------

  async pairUsb(slot: PrinterSlot): Promise<void> {
    const usb = nav().usb;
    if (!usb) throw new Error('This browser has no WebUSB. Use Chrome or Edge, or connect through a serial (COM) port.');
    const dev = await usb.requestDevice({ filters: [] });
    const next = JSON.parse(JSON.stringify(this.current)) as DeviceSettings;
    next[slot].mode = 'usb';
    next[slot].usb = { vendorId: dev.vendorId, productId: dev.productId, serialNumber: dev.serialNumber || undefined, name: dev.productName || undefined };
    this.save(next);
  }

  async pairSerial(slot: PrinterSlot, baud: number, flow: 'none' | 'hardware'): Promise<void> {
    const serial = nav().serial;
    if (!serial) throw new Error('This browser has no Web Serial. Use Chrome or Edge on this PC.');
    const port = await serial.requestPort();
    const ports = await serial.getPorts();
    const info = port.getInfo ? port.getInfo() : {};
    const idx = Math.max(0, ports.indexOf(port));
    const next = JSON.parse(JSON.stringify(this.current)) as DeviceSettings;
    next[slot].mode = 'serial';
    next[slot].serial = { baud, flow, usbVendorId: info.usbVendorId, usbProductId: info.usbProductId, index: idx, name: this.portName(info, idx) };
    this.save(next);
  }

  async pairDisplay(): Promise<void> {
    const serial = nav().serial;
    if (!serial) throw new Error('This browser has no Web Serial. Use Chrome or Edge on this PC.');
    const port = await serial.requestPort();
    const ports = await serial.getPorts();
    const info = port.getInfo ? port.getInfo() : {};
    const idx = Math.max(0, ports.indexOf(port));
    const next = JSON.parse(JSON.stringify(this.current)) as DeviceSettings;
    next.display = { ...next.display, enabled: true, usbVendorId: info.usbVendorId, usbProductId: info.usbProductId, index: idx, name: this.portName(info, idx) };
    this.save(next);
  }

  private portName(info: any, idx: number): string {
    return info && info.usbVendorId ? `USB serial ${hex4(info.usbVendorId)}:${hex4(info.usbProductId ?? 0)}` : `Serial port #${idx + 1}`;
  }

  forget(slot: PrinterSlot | 'display'): void {
    const next = JSON.parse(JSON.stringify(this.current)) as DeviceSettings;
    if (slot === 'display') {
      next.display.enabled = false;
      next.display.usbVendorId = undefined;
      next.display.usbProductId = undefined;
      next.display.name = undefined;
    } else {
      next[slot].mode = 'driver';
      next[slot].usb = null;
      next[slot].serial = null;
    }
    this.save(next);
  }

  // ---- sending -------------------------------------------------------------------------------------------------------

  /** Sends raw bytes to a slot's device. Never throws. */
  async send(slot: PrinterSlot, bytes: Uint8Array): Promise<SendResult> {
    const c = this.current[slot];
    try {
      if (c.mode === 'usb' && c.usb) await this.sendUsb(c.usb, bytes);
      else if (c.mode === 'serial' && c.serial) await this.sendSerial(c.serial, bytes);
      else return { ok: false, error: 'No direct printer is set for this printer.' };
      this.lastError[slot] = null;
      return { ok: true };
    } catch (e: any) {
      const msg = describe(e);
      this.lastError[slot] = msg;
      return { ok: false, error: msg };
    }
  }

  /** The bytes of a job, once per copy. */
  withCopies(build: () => Uint8Array): Uint8Array {
    const n = Math.min(5, Math.max(1, Math.round(this.current.copies || 1)));
    const one = build();
    if (n === 1) return one;
    const out = new Uint8Array(one.length * n);
    for (let i = 0; i < n; i++) out.set(one, i * one.length);
    return out;
  }

  /** Opens the cash drawer through the receipt printer. */
  async openDrawer(): Promise<SendResult> {
    if (!this.isDirect('receipt')) {
      return { ok: false, error: 'The cash drawer opens through the receipt printer in direct mode. With the Windows driver, switch on "Open cash drawer" in the Epson driver settings.' };
    }
    const b = new EscPosBuilder(48, this.current.codepage);
    b.drawer(this.current.drawerPin);
    return this.send('receipt', b.bytes());
  }

  private async sendUsb(id: NonNullable<PrinterConn['usb']>, bytes: Uint8Array): Promise<void> {
    const usb = nav().usb;
    if (!usb) throw new Error('This browser has no WebUSB.');
    const devices: any[] = await usb.getDevices();
    const dev = devices.find((d) => d.vendorId === id.vendorId && d.productId === id.productId && (!id.serialNumber || d.serialNumber === id.serialNumber));
    if (!dev) throw new Error('The USB printer is not available. Connect it again on the Devices page.');
    await dev.open();
    try {
      if (!dev.configuration) await dev.selectConfiguration(1);
      let ifaceNum = -1;
      let epNum = -1;
      for (const iface of dev.configuration.interfaces as any[]) {
        const alt = iface.alternates[0];
        const ep = (alt.endpoints as any[]).find((e) => e.direction === 'out' && e.type === 'bulk');
        if (ep && (ifaceNum < 0 || alt.interfaceClass === 7)) {
          ifaceNum = iface.interfaceNumber;
          epNum = ep.endpointNumber;
          if (alt.interfaceClass === 7) break;
        }
      }
      if (ifaceNum < 0) throw new Error('The USB device has no printer endpoint.');
      await dev.claimInterface(ifaceNum);
      try {
        const chunk = 16384;
        for (let o = 0; o < bytes.length; o += chunk) await dev.transferOut(epNum, bytes.slice(o, o + chunk));
      } finally {
        try {
          await dev.releaseInterface(ifaceNum);
        } catch {
          /* ignore */
        }
      }
    } finally {
      try {
        await dev.close();
      } catch {
        /* ignore */
      }
    }
  }

  private async sendSerial(id: NonNullable<PrinterConn['serial']>, bytes: Uint8Array): Promise<void> {
    const port = await this.findPort(id.usbVendorId, id.usbProductId, id.index);
    await port.open({ baudRate: id.baud || 9600, flowControl: id.flow === 'hardware' ? 'hardware' : 'none' });
    try {
      const writer = port.writable.getWriter();
      try {
        const chunk = 4096;
        for (let o = 0; o < bytes.length; o += chunk) await writer.write(bytes.slice(o, o + chunk));
      } finally {
        writer.releaseLock();
      }
    } finally {
      try {
        await port.close();
      } catch {
        /* ignore */
      }
    }
  }

  /** A granted serial port by USB ids, else by its position. */
  async findPort(vid: number | undefined, pid: number | undefined, index: number): Promise<any> {
    const serial = nav().serial;
    if (!serial) throw new Error('This browser has no Web Serial.');
    const ports: any[] = await serial.getPorts();
    if (!ports.length) throw new Error('The serial port is not available. Connect it again on the Devices page.');
    if (vid !== undefined) {
      const hit = ports.find((p) => {
        const i = p.getInfo ? p.getInfo() : {};
        return i.usbVendorId === vid && (pid === undefined || i.usbProductId === pid);
      });
      if (hit) return hit;
    }
    return ports[Math.min(index, ports.length - 1)];
  }

  // ---- the Windows driver fallback ----------------------------------------------------------------------------------

  /**
   * Prints a text job (the same lines the ESC/POS job has) through the browser print dialog on a roll-width page: monospace, black,
   * no margins and no blank page after it. Used when a printer is in driver mode and for tickets that have no HTML page of their own.
   */
  printTextViaDriver(text: string, paper: '80' | '58', title = 'Ticket'): void {
    const mm = paper === '58' ? 48 : 72;
    const cols = paper === '58' ? 32 : 48;
    const iframe = document.createElement('iframe');
    iframe.setAttribute('aria-hidden', 'true');
    iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;';
    document.body.appendChild(iframe);
    const doc = iframe.contentDocument!;
    const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;');
    doc.open();
    doc.write(
      `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title><style>` +
        `@page{size:${paper === '58' ? '58mm' : '80mm'} auto;margin:0}` +
        `html,body{margin:0;padding:0;background:#fff}` +
        `pre{margin:0;padding:2mm 2mm 6mm;width:${mm}mm;font:700 ${paper === '58' ? 11 : 12}px/1.25 'Courier New',monospace;color:#000;white-space:pre-wrap;word-break:break-all}` +
        `</style></head><body><pre data-cols="${cols}">${esc}</pre></body></html>`
    );
    doc.close();
    const done = () => setTimeout(() => iframe.remove(), 1000);
    iframe.contentWindow!.addEventListener('afterprint', done);
    setTimeout(() => {
      try {
        iframe.contentWindow!.focus();
        iframe.contentWindow!.print();
      } catch {
        done();
      }
    }, 100);
  }

  /** Sends a job to a slot: direct when the slot has a device, otherwise (or on failure) through the Windows driver. Resolves how it went. */
  async dispatch(slot: PrinterSlot, build: (b: EscPosBuilder) => void, serverPaper: string | null | undefined, title = 'Ticket'): Promise<{ via: 'direct' | 'driver'; error?: string }> {
    const mk = () => {
      const b = this.newBuilder(serverPaper);
      build(b);
      return b;
    };
    if (this.isDirect(slot)) {
      const r = await this.send(slot, this.withCopies(() => mk().bytes()));
      if (r.ok) return { via: 'direct' };
      this.printTextViaDriver(previewText(mk().bytes()), this.layout(serverPaper).paper, title);
      return { via: 'driver', error: r.error };
    }
    this.printTextViaDriver(previewText(mk().bytes()), this.layout(serverPaper).paper, title);
    return { via: 'driver' };
  }
}

function hex4(n: number): string {
  return n.toString(16).padStart(4, '0');
}

function describe(e: any): string {
  const m = (e && (e.message || e.name)) || String(e);
  if (/busy|in use|Access denied|claimInterface|Unable to claim/i.test(m)) {
    return `${m} (the port or USB interface is in use or owned by the Windows printer driver; for USB the printer needs the WinUSB driver, otherwise use its serial / virtual COM port)`;
  }
  return m;
}
