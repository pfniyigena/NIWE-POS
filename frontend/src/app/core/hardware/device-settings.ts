import { Codepage } from './escpos';

/** How a printer is reached: the Windows driver (browser print dialog), or raw ESC/POS over USB or a serial (COM) port from Chrome / Edge. */
export type PrinterMode = 'driver' | 'usb' | 'serial';

export interface PrinterConn {
  mode: PrinterMode;
  /** Remembered USB device (WebUSB). */
  usb?: { vendorId: number; productId: number; serialNumber?: string; name?: string } | null;
  /** Remembered serial port (Web Serial); index is the position among the ports this site may use, the fallback when the port has no USB ids. */
  serial?: { baud: number; flow: 'none' | 'hardware'; usbVendorId?: number; usbProductId?: number; index: number; name?: string } | null;
}

export interface KitchenPrinterConn extends PrinterConn {
  /** Comma-separated station names this printer takes (empty on the kitchen printer = every station not taken by another). */
  stations: string;
}

export type DisplayProtocol = 'escpos' | 'cd5220' | 'plain';

export interface DisplayConn {
  enabled: boolean;
  protocol: DisplayProtocol;
  baud: number;
  dataBits: 7 | 8;
  parity: 'none' | 'even' | 'odd';
  stopBits: 1 | 2;
  /** Characters per line (usually 20; the display has two lines). */
  cols: number;
  usbVendorId?: number;
  usbProductId?: number;
  index: number;
  name?: string;
  /** Shown when the till is idle. */
  welcome1: string;
  welcome2: string;
}

/** Settings of the devices on this workstation. They live in this browser (each till has its own hardware), not on the server. */
export interface DeviceSettings {
  receipt: PrinterConn;
  kitchen: KitchenPrinterConn;
  bar: KitchenPrinterConn;
  /** 'auto' follows POS settings > Receipt paper; otherwise this printer's own roll width. */
  paper: 'auto' | '80' | '58';
  /** Characters per line; 0 = automatic (48 on 80 mm, 32 on 58 mm). */
  cols: number;
  codepage: Codepage;
  cut: boolean;
  /** Open the cash drawer when a sale with a cash payment is printed (direct mode), and for the "Open drawer" button. */
  drawerOnCash: boolean;
  drawerPin: 0 | 1;
  /** Print a kitchen / bar ticket automatically on "Send to kitchen". */
  autoKitchen: boolean;
  copies: number;
  display: DisplayConn;
}

export const DEFAULT_DEVICE_SETTINGS: DeviceSettings = {
  receipt: { mode: 'driver', usb: null, serial: null },
  kitchen: { mode: 'driver', usb: null, serial: null, stations: '' },
  bar: { mode: 'driver', usb: null, serial: null, stations: 'Bar' },
  paper: 'auto',
  cols: 0,
  codepage: 'cp858',
  cut: true,
  drawerOnCash: true,
  drawerPin: 0,
  autoKitchen: false,
  copies: 1,
  display: {
    enabled: false,
    protocol: 'escpos',
    baud: 9600,
    dataBits: 8,
    parity: 'none',
    stopBits: 1,
    cols: 20,
    index: 0,
    welcome1: 'WELCOME',
    welcome2: '',
  },
};

export const STORAGE_KEY = 'niwe.devices.v1';

/** Merges stored settings over the defaults (so a newer build never meets a missing field). */
export function mergeSettings(stored: any): DeviceSettings {
  const d = DEFAULT_DEVICE_SETTINGS;
  const s = stored && typeof stored === 'object' ? stored : {};
  return {
    ...d,
    ...s,
    receipt: { ...d.receipt, ...(s.receipt ?? {}) },
    kitchen: { ...d.kitchen, ...(s.kitchen ?? {}) },
    bar: { ...d.bar, ...(s.bar ?? {}) },
    display: { ...d.display, ...(s.display ?? {}) },
  };
}

/** The characters per line for a roll width. */
export function colsFor(paper: '80' | '58', override: number): number {
  if (override >= 24 && override <= 80) return Math.round(override);
  return paper === '58' ? 32 : 48;
}
