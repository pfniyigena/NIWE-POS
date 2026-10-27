import type { BranchReceiptInfo, KitchenTicket, Order, PosSettings, RestaurantOrder, SalesReturn, ShiftReport, StoreSettings } from '../models';
import { EscPosBuilder, Raster, composeEnds, money, qty } from './escpos';

/** Dots across the paper for logos: 576 on 80 mm, 384 on 58 mm. */
export function dotsFor(cols: number): number {
  return cols >= 40 ? 576 : 384;
}

/** A deduction: "-12.00", or "0.00" when there is nothing to take off. */
function neg(n: number | null | undefined): string {
  return Number(n ?? 0) ? '-' + money(n) : money(0);
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return String(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function splitDateTime(raw: string | null): { date: string; time: string } {
  if (!raw) return { date: '', time: '' };
  const i = raw.indexOf(' ');
  return i === -1 ? { date: raw, time: '' } : { date: raw.slice(0, i), time: raw.slice(i + 1) };
}

export interface Finish {
  /** Feed and cut after the job. */
  cut: boolean;
  /** Open the cash drawer with this job. */
  drawer: boolean;
  drawerPin: 0 | 1;
}

function finish(b: EscPosBuilder, f: Finish): void {
  b.feed(4);
  if (f.drawer) b.drawer(f.drawerPin);
  if (f.cut) b.cut();
}

function businessHeader(b: EscPosBuilder, s: StoreSettings | null, branch: BranchReceiptInfo | null, extra: (string | null | undefined)[], logo: Raster | null): void {
  b.align('center');
  if (logo) b.image(logo).line();
  if (s?.businessName) b.bold().size(1, 2).wrap(s.businessName).size(1, 1).bold(false);
  if (branch?.name) b.bold().wrap(branch.name).bold(false);
  const addr = branch?.address || s?.address;
  if (addr) b.wrap(addr);
  const phone = branch?.phone || s?.phone;
  const email = branch?.email || s?.email;
  if (phone || email) b.wrap([phone, email].filter((x) => x).join(' - '));
  const tin = branch?.taxId || s?.taxId;
  if (tin) b.wrap(`Tax ID: ${tin}`);
  for (const e of [branch?.receiptHeader, ...extra]) if (e) b.wrap(e);
  b.align('left');
}

interface Fiscal {
  sdcId: string | null;
  sdcDate: string | null;
  sdcReceiptNumber: string | null;
  internalData: string | null;
  signatureData: string | null;
  cisNumber: string | null;
  cisDate: string | null;
  mrcData: string | null;
}

/** The EFRIS "SDC INFORMATION" block, as on the screen receipt. */
function fiscalBlock(b: EscPosBuilder, o: Fiscal): void {
  if (!o.sdcId) return;
  b.rule().align('center').bold().line('SDC INFORMATION').bold(false).align('left');
  const sd = splitDateTime(o.sdcDate);
  if (o.sdcDate) b.row(`SDC DATE:${sd.date}`, `TIME:${sd.time}`);
  if (o.sdcReceiptNumber) b.row('SDC RECEIPT NUMBER:', o.sdcReceiptNumber);
  b.row('SDC ID:', o.sdcId);
  if (o.internalData) b.align('center').line('Internal Data:').wrap(o.internalData).align('left');
  if (o.signatureData) b.align('center').line('Receipt Signature:').wrap(o.signatureData).align('left');
  if (o.cisNumber || o.cisDate || o.mrcData) b.rule();
  if (o.cisNumber) b.row('CIS RECEIPT NUMBER:', o.cisNumber);
  if (o.cisDate) {
    const cd = splitDateTime(o.cisDate);
    b.row(`CIS DATE:${cd.date}`, `TIME:${cd.time}`);
  }
  if (o.mrcData) b.row('MRC:', o.mrcData);
}

export interface SaleReceiptCtx extends Finish {
  settings: StoreSettings | null;
  branch: BranchReceiptInfo | null;
  cfg: PosSettings | null;
  returned: Record<number, number>;
  copyNumber: number;
  /** Rasters prepared by the caller (null when none). */
  logo: Raster | null;
  authorityLogo: Raster | null;
  efrisLogo: Raster | null;
  /** The string the EFRIS QR carries; null when the sale was not submitted. */
  efrisQr: string | null;
}

/** The sale receipt, same content and order as the screen receipt. */
export function buildSaleReceipt(b: EscPosBuilder, o: Order, c: SaleReceiptCtx): void {
  if (c.copyNumber > 0) b.align('center').bold().wrap(`*** COPY ${c.copyNumber} - not the original ***`).bold(false).align('left').line();
  b.align('center');
  const ends = composeEnds(c.authorityLogo, c.efrisLogo, dotsFor(b.cols));
  if (ends) b.image(ends);
  b.bold().size(1, 2).line('Receipt').size(1, 1).bold(false).align('left');
  businessHeader(b, c.settings, c.branch, [c.cfg?.receiptHeader], c.cfg?.receiptShowLogo === false ? null : c.logo);
  b.align('center').line();
  if (o.invoiceNumber) b.bold().line(o.invoiceNumber).bold(false);
  b.wrap(`${o.registerName ?? ''} - served by ${o.cashierName ?? ''}`);
  if (o.completedAt || o.createdAt) b.line(fmtDate(o.completedAt || o.createdAt));
  b.align('left').line();
  b.wrap(`Customer: ${o.customerName ?? ''}`);
  if (o.customerTaxId) b.wrap(`Customer tax ID: ${o.customerTaxId}`);
  if (o.voidedAt) {
    b.align('center').bold().line(`*** VOIDED ${fmtDate(o.voidedAt)} ***`);
    if (o.voidReason) b.wrap(o.voidReason);
    b.bold(false).align('left');
  }
  b.rule();
  for (const l of o.lines) {
    b.row(l.description, money(l.lineTotal) + (l.taxCode ? ` ${l.taxCode}` : ''));
    let sub = `${qty(l.quantity)} x ${money(l.unitPrice)}`;
    if (l.discountPercent) sub += ` (-${qty(l.discountPercent)}%)`;
    if (l.taxInclusive) sub += ' (tax incl.)';
    b.wrap(sub.trimStart(), 2);
    if (l.promoNote) b.wrap(`${l.promoNote} (-${qty(l.promoPercent ?? 0)}%)`, 2);
    const back = c.returned[l.id];
    if (back) b.bold().wrap(`[RETURNED ${qty(back)} of ${qty(l.quantity)}]`, 2).bold(false);
  }
  b.rule();
  b.row('Subtotal', money(o.subtotal));
  b.row('Tax', money(o.taxTotal));
  b.bold().size(1, 2).row('TOTAL', money(o.total)).size(1, 1).bold(false);
  if (o.orderDiscountPercent && o.orderDiscountPercent > 0) {
    b.row(`Incl. discount ${qty(o.orderDiscountPercent)}%${o.orderDiscountReason ? ' (' + o.orderDiscountReason + ')' : ''}`, neg(o.orderDiscountValue));
  }
  if (o.promoSavings && o.promoSavings > 0) b.row('You saved with promotions', money(o.promoSavings));
  b.rule().bold().line('Payment').bold(false);
  for (const p of o.payments) {
    const name = p.method === 'GIFT_CARD' ? 'Gift card' : p.method === 'LOYALTY' ? 'Loyalty points' : p.method;
    b.row(name + (p.reference ? ` (${p.reference})` : ''), money(p.amount));
  }
  if (o.loyaltyEarned || o.loyaltyBalance) {
    const t = [o.loyaltyEarned ? `Points earned: ${o.loyaltyEarned}.` : '', o.loyaltyBalance != null ? `Points balance: ${o.loyaltyBalance}` : ''].filter((x) => x).join(' ');
    b.wrap(t);
  }
  fiscalBlock(b, o);
  if (o.sdcId && c.efrisQr) b.align('center').line().qr(c.efrisQr, b.cols >= 40 ? 6 : 5).line().align('left');
  else if (c.cfg?.receiptShowQr && o.invoiceNumber && !o.sdcId) b.align('center').line().qr(o.invoiceNumber, 6).line(o.invoiceNumber).align('left');
  const footer = c.cfg ? c.cfg.receiptFooter : 'Thank you!';
  if (footer) b.line().align('center').wrap(footer).align('left');
  finish(b, c);
}

export interface ReturnReceiptCtx extends Finish {
  settings: StoreSettings | null;
  authorityLogo: Raster | null;
  efrisLogo: Raster | null;
  efrisQr: string | null;
}

export function buildReturnReceipt(b: EscPosBuilder, r: SalesReturn, c: ReturnReceiptCtx): void {
  const ends = composeEnds(c.authorityLogo, c.efrisLogo, dotsFor(b.cols));
  b.align('center');
  if (ends) b.image(ends);
  b.bold().size(1, 2).line('Return Receipt').size(1, 1).bold(false);
  if (c.settings?.businessName) b.wrap(c.settings.businessName);
  b.line(fmtDate(r.returnDate)).align('left').line();
  b.wrap(`Original invoice: ${r.invoiceNumber ?? ''}`);
  b.wrap(`Customer: ${r.customerName ?? ''}`);
  b.rule();
  for (const l of r.lines) {
    b.row(l.description ?? '', l.refundAmount != null ? '-' + money(l.refundAmount) : '-');
    b.wrap(`Qty returned: ${qty(l.quantity)}`, 2);
  }
  if (!r.lines.length) b.line('No line detail recorded.');
  b.rule();
  b.bold().row('Refund total', neg(r.refundTotal)).bold(false);
  b.row('Refund method', r.refundMethod);
  if (r.reason) b.wrap(`Reason: ${r.reason}`);
  fiscalBlock(b, r as unknown as Fiscal);
  if (r.sdcId && c.efrisQr) b.align('center').line().qr(c.efrisQr, b.cols >= 40 ? 6 : 5).line().align('left');
  finish(b, c);
}

export interface BillCtx extends Finish {
  settings: StoreSettings | null;
  logo: Raster | null;
  /** True for the bill printed before payment. */
  preBill: boolean;
  copyNumber: number;
}

/** The restaurant bill (also the pre-bill shown before payment). */
export function buildRestaurantBill(b: EscPosBuilder, o: RestaurantOrder, c: BillCtx): void {
  b.align('center').bold().size(1, 2).line(c.preBill ? 'BILL (not a receipt)' : 'Receipt').size(1, 1).bold(false);
  if (c.copyNumber > 0) b.bold().line(`*** COPY ${c.copyNumber} ***`).bold(false);
  b.align('left');
  businessHeader(b, c.settings, null, [], c.logo);
  b.align('center').line();
  if (o.ticketNumber) b.bold().line(o.ticketNumber).bold(false);
  b.wrap(o.displayLabel + (o.serverName ? ` - served by ${o.serverName}` : ''));
  b.line(fmtDate(o.closedAt || o.openedAt)).align('left');
  if (o.guestCount) b.wrap(`Guests: ${o.guestCount}`);
  if (o.customerPhone) b.wrap(`Phone: ${o.customerPhone}`);
  if (o.customerAddress) b.wrap(`Address: ${o.customerAddress}`);
  b.rule();
  for (const l of o.lines) {
    b.row(l.description, money(l.lineGrandTotal));
    b.wrap(`${qty(l.quantity)} x ${money((l.unitPrice ?? 0) + (l.optionsPrice ?? 0))}`, 2);
    if (l.options && l.options.length) {
      b.wrap(l.options.map((x: any) => x.name + (x.priceDelta ? ` (${x.priceDelta > 0 ? '+' : ''}${money(x.priceDelta)})` : '')).join(', '), 2);
    }
    if (l.notes) b.wrap(`${l.notes}`, 2);
    if ((l.discountTotal ?? 0) > 0) b.wrap(`discount -${money(l.discountTotal)}`, 2);
  }
  b.rule();
  if ((o.discountTotal ?? 0) > 0) b.row(`Discounts given${o.discountReason ? ' (' + o.discountReason + ')' : ''}`, neg(o.discountTotal));
  b.row('Subtotal', money(o.subtotal));
  if ((o.serviceCharge ?? 0) > 0) b.row(`Service charge (${qty(o.serviceChargePercent)}%)`, money(o.serviceCharge));
  b.row('Tax', money(o.taxTotal));
  if ((o.roundingAdjustment ?? 0) !== 0) b.row('Rounding', money(o.roundingAdjustment));
  b.bold().size(1, 2).row('TOTAL', money(o.total)).size(1, 1).bold(false);
  if ((o.tipAmount ?? 0) > 0) b.row('Tip', money(o.tipAmount));
  if (!c.preBill) {
    b.rule().bold().line('Payment').bold(false);
    for (const p of o.payments) b.row(p.method, money(p.amount));
    if ((o.depositApplied ?? 0) > 0) b.row('Reservation deposit (paid earlier)', money(o.depositApplied));
    if ((o.changeGiven ?? 0) > 0) b.row('Change given', money(o.changeGiven));
  }
  b.line().align('center').line('Thank you!').align('left');
  finish(b, c);
}

export interface TicketCtx {
  cut: boolean;
  stationLabel: string;
  tableLabel: string;
  server: string | null;
  orderNumber: string;
  guests?: number;
  /** When true the ticket is a re-send / remake and says so. */
  reprint?: boolean;
}

/** A kitchen or bar ticket: large type, grouped by course, no prices. */
export function buildKitchenTicket(b: EscPosBuilder, lines: KitchenTicket[], c: TicketCtx): void {
  b.align('center').bold().size(2, 2).wrap(c.stationLabel || 'KITCHEN').size(1, 1).bold(false);
  if (c.reprint) b.bold().line('*** REPRINT ***').bold(false);
  b.size(1, 2).bold().wrap(c.tableLabel).bold(false).size(1, 1);
  b.align('left');
  b.row(c.orderNumber, fmtDate(new Date().toISOString()).slice(11));
  if (c.server) b.wrap(`Server: ${c.server}`);
  if (c.guests) b.wrap(`Guests: ${c.guests}`);
  b.rule('=');
  const byCourse = new Map<number, KitchenTicket[]>();
  for (const l of lines) {
    const k = l.course ?? 0;
    if (!byCourse.has(k)) byCourse.set(k, []);
    byCourse.get(k)!.push(l);
  }
  const keys = [...byCourse.keys()].sort((a, z) => a - z);
  for (const k of keys) {
    if (keys.length > 1 || k > 0) b.bold().line(`-- Course ${k || 1} --`).bold(false);
    for (const l of byCourse.get(k)!) {
      b.bold().size(1, 2).wrap(`${qty(l.quantity)} x ${l.description}`).size(1, 1).bold(false);
      if (l.seat) b.wrap(`seat ${l.seat}`, 3);
      if (l.options && l.options.length) b.wrap('+ ' + l.options.join(', '), 3);
      if (l.notes) b.bold().wrap(`NOTE: ${l.notes}`, 3).bold(false);
      if (l.allergyConflict) b.bold().wrap(`!! ALLERGY: ${l.allergyConflict}`, 3).bold(false);
      else if (l.guestAllergens) b.bold().wrap(`ALLERGENS: ${l.guestAllergens}`, 3).bold(false);
    }
  }
  b.rule('=');
  b.feed(4);
  if (c.cut) b.cut();
}

export interface ReportCtx {
  settings: StoreSettings | null;
  cut: boolean;
}

/** X / Z shift report. */
export function buildShiftReport(b: EscPosBuilder, r: ShiftReport, c: ReportCtx): void {
  b.align('center');
  if (c.settings?.businessName) b.bold().wrap(c.settings.businessName).bold(false);
  b.bold().size(1, 2).line(`${r.type}-Report${r.type === 'Z' && r.zNumber ? ' no. ' + r.zNumber : ''}`).size(1, 1).bold(false);
  if (r.copyNumber > 0) b.bold().line(`*** COPY ${r.copyNumber} ***`).bold(false);
  if (r.type === 'X') b.wrap('Shift still open - nothing has been closed or cleared');
  b.wrap(`${r.registerName} - ${r.cashierName}`);
  b.wrap(`${fmtDate(r.openedAt)} -> ${r.closedAt ? fmtDate(r.closedAt) : 'now'}`);
  b.line(`Printed ${fmtDate(r.generatedAt)}`).align('left');
  const h = (t: string) => b.rule().bold().line(t).bold(false);
  h('Sales');
  b.row('Number of sales', String(r.sales.count));
  b.row('Items sold', qty(r.sales.items));
  b.row('Sales at shelf price', money(r.sales.shelfValue));
  b.row('Discounts and promotions', neg(r.sales.discounts));
  b.row('Net sales (before tax)', money(r.sales.netSales));
  b.row('Tax', money(r.sales.tax));
  b.bold().row('Total sales', money(r.sales.totalSales)).bold(false);
  b.row('Average sale', money(r.sales.averageSale));
  if (r.payments.length) {
    h('Paid by');
    for (const p of r.payments) b.row(`${p.name} (${p.count})`, money(p.amount));
  }
  if (r.taxes.length) {
    h('Tax');
    for (const t of r.taxes) b.row(`${t.code}${t.ratePercent != null ? ' ' + qty(t.ratePercent) + '%' : ''} on ${money(t.net)}`, money(t.tax));
  }
  h('Refunds');
  b.row('Returns made', String(r.refunds.count));
  for (const p of r.refunds.byMethod) b.row(`  ${p.name} (${p.count})`, neg(p.amount));
  if (r.refunds.count) b.bold().row('Total refunded', neg(r.refunds.total)).bold(false);
  h('Cash drawer');
  const d = r.drawer;
  b.row('Opening float', money(d.openingFloat));
  b.row('Cash sales', money(d.cashSales));
  b.row('Cash refunds', neg(d.cashRefunds));
  if (d.paidIn) b.row('Paid in', money(d.paidIn));
  if (d.paidOut) b.row('Paid out', neg(d.paidOut));
  if (d.drops) b.row('Dropped into the safe', neg(d.drops));
  b.bold().row('Expected cash', money(d.expectedCash)).bold(false);
  if (d.countedCash != null) {
    b.row('Counted cash', money(d.countedCash));
    b.bold().row('Difference', money(d.variance ?? 0)).bold(false);
    if (d.varianceNote) b.wrap(`Note: ${d.varianceNote}`);
  }
  const moves = (r.cashMovements ?? []).filter((m: any) => !m.voided && m.kind !== 'COUNT');
  if (moves.length) {
    b.line().bold().line('Cash in and out').bold(false);
    for (const m of moves as any[]) b.row(`${fmtDate(m.createdAt).slice(11)} ${m.kind === 'PAID_IN' ? 'In' : m.kind === 'PAID_OUT' ? 'Out' : 'Drop'} ${m.reason ?? ''}`, money(m.amount));
  }
  h('Watch points');
  const k = r.controls;
  b.row('Sales with a discount', String(k.discountedSales));
  b.row('Carts dropped with items', String(k.abandonedCarts) + (k.abandonedCarts ? ` (${money(k.abandonedValue)})` : ''));
  if (k.offlineSales) b.row('Sales rung offline', String(k.offlineSales));
  if (k.creditSales) b.row('Sold on credit', money(k.creditSales));
  if (k.roomCharges) b.row('Charged to rooms', money(k.roomCharges));
  if (k.giftCardsSold) b.row('Gift cards sold', money(k.giftCardsSold));
  if (k.voidedCashMovements) b.row('Cash entries voided', String(k.voidedCashMovements));
  if (r.cashiers.length) {
    h('By cashier');
    for (const x of r.cashiers) b.row(`${x.name} (${x.sales})`, money(x.total));
  }
  if (r.closedBy) b.line().wrap(`Closed by ${r.closedBy}`);
  b.line().align('center').line(`- End of ${r.type}-Report -`).align('left');
  b.feed(4);
  if (c.cut) b.cut();
}
