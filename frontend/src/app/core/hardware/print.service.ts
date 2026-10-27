import { Injectable } from '@angular/core';
import type { BranchReceiptInfo, KitchenTicket, Order, PosSettings, RestaurantOrder, RestaurantOrderLine, SalesReturn, ShiftReport, StoreSettings } from '../models';
import { DeviceService, PrinterSlot, SendResult } from './device.service';
import { EscPosBuilder, Raster, previewText, rasterFromDataUrl } from './escpos';
import {
  buildKitchenTicket,
  buildRestaurantBill,
  buildReturnReceipt,
  buildSaleReceipt,
  buildShiftReport,
  dotsFor,
} from './receipt-escpos';

export interface SaleData {
  settings: StoreSettings | null;
  branch: BranchReceiptInfo | null;
  cfg: PosSettings | null;
  returned: Record<number, number>;
  copyNumber: number;
  authorityLogoUrl: string | null;
  efrisLogoUrl: string | null;
  efrisQr: string | null;
}

/**
 * Builds the thermal (ESC/POS) version of each document and sends it to the right printer of this PC.
 * The screen receipts stay as they are for the Windows driver; this is used when a printer is connected directly.
 */
@Injectable({ providedIn: 'root' })
export class PrintService {
  constructor(private devices: DeviceService) {}

  private finishFor(drawer: boolean) {
    const s = this.devices.settings;
    return { cut: s.cut, drawer: drawer && s.drawerOnCash, drawerPin: s.drawerPin };
  }

  private async raster(url: string | null | undefined, cols: number, maxH = 160): Promise<Raster | null> {
    return rasterFromDataUrl(url, Math.min(dotsFor(cols), 384), maxH);
  }

  /** True when a sale has a cash tender (the drawer opens). */
  hasCash(o: Order): boolean {
    return (o.payments ?? []).some((p) => String(p.method).toUpperCase() === 'CASH' && Number(p.amount) > 0);
  }

  /** Sends the sale receipt to the receipt printer. */
  async sale(o: Order, d: SaleData): Promise<SendResult> {
    const paper = d.cfg?.receiptPaper;
    const layout = this.devices.layout(paper);
    const logo = d.settings?.logoDataUrl ? await this.raster(d.settings.logoDataUrl, layout.cols, 120) : null;
    const authorityLogo = await this.raster(d.authorityLogoUrl, layout.cols, 80);
    const efrisLogo = await this.raster(d.efrisLogoUrl, layout.cols, 80);
    const bytes = this.devices.withCopies(() => {
      const b = this.devices.newBuilder(paper);
      buildSaleReceipt(b, o, {
        settings: d.settings,
        branch: d.branch,
        cfg: d.cfg,
        returned: d.returned,
        copyNumber: d.copyNumber,
        logo,
        authorityLogo,
        efrisLogo,
        efrisQr: d.efrisQr,
        ...this.finishFor(d.copyNumber === 0 && this.hasCash(o)),
      });
      return b.bytes();
    });
    return this.devices.send('receipt', bytes);
  }

  async salesReturn(r: SalesReturn, settings: StoreSettings | null, paper: string | null, authorityLogoUrl: string | null, efrisLogoUrl: string | null, efrisQr: string | null): Promise<SendResult> {
    const layout = this.devices.layout(paper);
    const authorityLogo = await this.raster(authorityLogoUrl, layout.cols, 80);
    const efrisLogo = await this.raster(efrisLogoUrl, layout.cols, 80);
    const bytes = this.devices.withCopies(() => {
      const b = this.devices.newBuilder(paper);
      buildReturnReceipt(b, r, { settings, authorityLogo, efrisLogo, efrisQr, ...this.finishFor(false) });
      return b.bytes();
    });
    return this.devices.send('receipt', bytes);
  }

  /** The restaurant bill / receipt. */
  async restaurantBill(o: RestaurantOrder, settings: StoreSettings | null, paper: string | null, preBill: boolean): Promise<SendResult> {
    const layout = this.devices.layout(paper);
    const logo = settings?.logoDataUrl ? await this.raster(settings.logoDataUrl, layout.cols, 120) : null;
    const copy = !preBill && (o.printCount ?? 0) > 1 ? (o.printCount ?? 1) - 1 : 0;
    const cash = !preBill && copy === 0 && (o.payments ?? []).some((p) => String(p.method).toUpperCase() === 'CASH' && Number(p.amount) > 0);
    const bytes = this.devices.withCopies(() => {
      const b = this.devices.newBuilder(paper);
      buildRestaurantBill(b, o, { settings, logo, preBill, copyNumber: copy, ...this.finishFor(cash) });
      return b.bytes();
    });
    return this.devices.send('receipt', bytes);
  }

  /** The bill as plain text for the Windows driver (a pre-bill or when no direct printer is set). */
  restaurantBillText(o: RestaurantOrder, settings: StoreSettings | null, paper: string | null, preBill: boolean): string {
    const b = this.devices.newBuilder(paper);
    buildRestaurantBill(b, o, { settings, logo: null, preBill, copyNumber: 0, cut: false, drawer: false, drawerPin: 0 });
    return previewText(b.bytes());
  }

  async shiftReport(r: ShiftReport, settings: StoreSettings | null, paper: string | null): Promise<SendResult> {
    const bytes = this.devices.withCopies(() => {
      const b = this.devices.newBuilder(paper);
      buildShiftReport(b, r, { settings, cut: this.devices.settings.cut });
      return b.bytes();
    });
    return this.devices.send('receipt', bytes);
  }

  /**
   * Prints the tickets for lines that were just sent to the kitchen: one ticket per station, each to the printer that takes
   * that station. Returns how many tickets went to a printer. With `onlyDirect` nothing is printed through the browser dialog.
   */
  async kitchenTickets(order: RestaurantOrder, lines: RestaurantOrderLine[], paper: string | null, opts: { onlyDirect: boolean; reprint?: boolean }): Promise<{ printed: number; errors: string[] }> {
    const errors: string[] = [];
    let printed = 0;
    const groups = new Map<string, RestaurantOrderLine[]>();
    for (const l of lines) {
      const k = l.station ?? '';
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k)!.push(l);
    }
    for (const [station, ls] of groups) {
      const slot: PrinterSlot = this.devices.slotForStation(station);
      if (opts.onlyDirect && !this.devices.isDirect(slot)) continue;
      const tl: KitchenTicket[] = ls.map((l) => ({
        orderId: order.id,
        lineId: l.id,
        tableName: order.displayLabel,
        description: l.description,
        quantity: l.quantity,
        notes: l.notes,
        kitchenStatus: l.kitchenStatus,
        orderOpenedAt: order.openedAt,
        station: l.station,
        course: l.course,
        options: (l.options ?? []).map((x) => x.name),
        allergens: l.allergens,
        allergyConflict: l.allergyConflict,
        seat: l.seat,
      }));
      const r = await this.devices.dispatch(
        slot,
        (b: EscPosBuilder) =>
          buildKitchenTicket(b, tl, {
            cut: this.devices.settings.cut,
            stationLabel: station || 'KITCHEN',
            tableLabel: order.displayLabel,
            server: order.serverName,
            orderNumber: order.ticketNumber || `#${order.id}`,
            guests: order.guestCount,
            reprint: opts.reprint,
          }),
        paper,
        'Kitchen ticket'
      );
      printed++;
      if (r.error) errors.push(r.error);
    }
    return { printed, errors };
  }

  /** A short test page for the Devices page. */
  async testPage(slot: PrinterSlot, serverPaper: string | null): Promise<SendResult> {
    const s = this.devices.settings;
    const bytes = this.devices.withCopies(() => {
      const b = this.devices.newBuilder(serverPaper);
      b.align('center').bold().size(2, 2).line('NIWE POS').size(1, 1).bold(false);
      b.line(`${slot.toUpperCase()} PRINTER TEST`).line(new Date().toLocaleString()).align('left');
      b.rule();
      b.row('Item with a long description that wraps', '1,234.56');
      b.row('Total', '1,234.56');
      b.rule('=');
      b.line(`Characters per line: ${b.cols}`).line('0123456789'.repeat(Math.ceil(b.cols / 10)).slice(0, b.cols));
      b.line('Accents: é è à ç ô ü ñ  €');
      b.align('center').qr('NIWE POS TEST', 6).line().align('left');
      b.feed(3);
      if (s.cut) b.cut();
      return b.bytes();
    });
    return this.devices.send(slot, bytes);
  }
}
