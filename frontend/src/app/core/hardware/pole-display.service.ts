import { Injectable } from '@angular/core';
import { DeviceService } from './device.service';
import { DisplayConn } from './device-settings';
import { money, qty } from './escpos';
import { displayFrame, lr } from './pole-frames';

interface PoleItem {
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/**
 * The customer display on a COM (serial) port, driven from the browser with Web Serial. It shows the item just rung up, the total,
 * what was paid and a welcome text. It stays quiet (and never gets in the cashier's way) when no display is set up on this PC.
 */
@Injectable({ providedIn: 'root' })
export class PoleDisplayService {
  private port: any = null;
  private writer: any = null;
  private opening: Promise<boolean> | null = null;
  private queue: Promise<void> = Promise.resolve();
  private idleTimer: any = null;
  private lastKey = '';

  constructor(private devices: DeviceService) {}

  get conf(): DisplayConn {
    return this.devices.settings.display;
  }

  get active(): boolean {
    return this.conf.enabled && this.devices.supportsSerial;
  }

  /** Opens the configured port (once). Resolves false when it cannot. */
  private async ensureOpen(): Promise<boolean> {
    if (this.writer) return true;
    if (this.opening) return this.opening;
    this.opening = (async () => {
      try {
        const c = this.conf;
        const port = await this.devices.findPort(c.usbVendorId, c.usbProductId, c.index);
        await port.open({ baudRate: c.baud, dataBits: c.dataBits, parity: c.parity, stopBits: c.stopBits, flowControl: 'none' });
        this.port = port;
        this.writer = port.writable.getWriter();
        this.devices.lastError['display'] = null;
        return true;
      } catch (e: any) {
        const m = (e && (e.message || e.name)) || String(e);
        // an already open port (another tab of this site) cannot be shared
        this.devices.lastError['display'] = m;
        this.port = null;
        this.writer = null;
        return false;
      } finally {
        this.opening = null;
      }
    })();
    return this.opening;
  }

  async close(): Promise<void> {
    try {
      this.writer?.releaseLock();
    } catch {
      /* ignore */
    }
    try {
      await this.port?.close();
    } catch {
      /* ignore */
    }
    this.writer = null;
    this.port = null;
  }

  private show(line1: string, line2: string, clear = false): void {
    if (!this.active) return;
    const key = line1 + '|' + line2;
    if (!clear && key === this.lastKey) return;
    this.lastKey = key;
    this.queue = this.queue
      .then(async () => {
        if (!(await this.ensureOpen())) return;
        const c = this.conf;
        try {
          if (clear) await this.writer.write(Uint8Array.from([0x1b, 0x40]));
          await this.writer.write(displayFrame(c.protocol, c.cols, line1, line2, clear));
        } catch (e: any) {
          this.devices.lastError['display'] = (e && e.message) || String(e);
          await this.close();
        }
      })
      .catch(() => {});
  }

  private cancelIdle(): void {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = null;
  }

  /** Welcome text; after a sale it comes back by itself. */
  idle(afterMs = 0): void {
    this.cancelIdle();
    const go = () => this.show(this.conf.welcome1, this.conf.welcome2, true);
    if (afterMs > 0) this.idleTimer = setTimeout(go, afterMs);
    else go();
  }

  /** The item that was just rung up: its name, then quantity x price and the line total. */
  item(it: PoleItem): void {
    this.cancelIdle();
    const c = this.conf.cols;
    const second = lr(`${qty(it.quantity)}x${money(it.unitPrice)}`, money(it.lineTotal), c);
    this.show(it.description, second);
  }

  /** The item just rung up; after a moment the display shows the running total. */
  itemThenTotal(it: PoleItem, total: number, totalAfterMs = 2500): void {
    this.item(it);
    if (!this.active) return;
    this.idleTimer = setTimeout(() => this.show('TOTAL', money(total).padStart(this.conf.cols, ' ')), totalAfterMs);
  }

  /** The amount to pay. */
  total(amount: number): void {
    this.cancelIdle();
    this.show('TOTAL', money(amount).padStart(this.conf.cols, ' '));
  }

  /** After payment: what was paid, change if any, thanks. */
  paid(amount: number, change = 0, thanksMs = 8000): void {
    this.cancelIdle();
    const c = this.conf.cols;
    if (change > 0) this.show(lr('PAID', money(amount), c), lr('CHANGE', money(change), c));
    else this.show(lr('PAID', money(amount), c), 'THANK YOU');
    this.idle(thanksMs);
  }

  /** A test message from the Devices page. */
  async test(): Promise<string | null> {
    this.lastKey = '';
    if (!this.devices.supportsSerial) return 'This browser has no Web Serial. Use Chrome or Edge.';
    if (!(await this.ensureOpen())) return this.devices.lastError['display'] || 'The display port could not be opened.';
    this.show('NIWE POS', 'DISPLAY TEST 123.45', true);
    await this.queue;
    return this.devices.lastError['display'];
  }
}
