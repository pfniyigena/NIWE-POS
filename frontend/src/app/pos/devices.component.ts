import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RegisterApiService } from '../core/api/register-api.service';
import { DeviceService, PrinterSlot } from '../core/hardware/device.service';
import { DeviceSettings, PrinterMode } from '../core/hardware/device-settings';
import { PoleDisplayService } from '../core/hardware/pole-display.service';
import { PrintService } from '../core/hardware/print.service';

/**
 * Devices of this PC: thermal receipt / kitchen / bar printers (Epson ESC/POS) and the COM customer display.
 * Kept per browser, because each till has its own hardware.
 */
@Component({
  selector: 'app-devices',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-header">
      <h1>{{ 'Devices' | translate }}</h1>
      <p class="text-muted">{{ 'Printers and the customer display of' | translate }} <strong>{{ 'this PC' | translate }}</strong>{{ '. They are kept in this browser; set them up on every till.' | translate }}</p>
    </div>

    <div class="alert alert-warning dev-nosupport" *ngIf="!devices.supportsSerial && !devices.supportsUsb">
      {{ 'This browser cannot talk to USB or COM devices directly. Open the till in Chrome or Edge to use direct thermal printing and the COM customer display.' | translate }}
      <span *ngIf="!secure">{{ 'The browser only allows this on a secure address: https://... or http://localhost (this page is on plain http).' | translate }}</span>
      {{ 'The "Windows driver" mode still works in any browser.' | translate }}
    </div>
    <div class="alert alert-info dev-msg" *ngIf="message">{{ message }}</div>
    <div class="alert alert-danger dev-err" *ngIf="error">{{ error }}</div>

    <ng-container *ngIf="s as d">
      <div class="card mb-3" *ngFor="let p of printers">
        <div class="card-body">
          <h3 class="card-title">{{ p.title }}</h3>
          <p class="text-muted" style="font-size:0.9rem;">{{ p.hint }}</p>
          <div class="row g-3 align-items-end">
            <div class="col-auto">
              <label class="form-label">{{ 'Connection' | translate }}</label>
              <select class="form-select dev-mode" [attr.data-slot]="p.slot" [(ngModel)]="d[p.slot].mode" [name]="p.slot + 'mode'">
                <option value="driver">{{ 'Windows driver (print dialog)' | translate }}</option>
                <option value="usb">{{ 'USB, direct ESC/POS' | translate }}</option>
                <option value="serial">{{ 'Serial / COM, direct ESC/POS' | translate }}</option>
              </select>
            </div>
            <ng-container *ngIf="d[p.slot].mode === 'serial'">
              <div class="col-auto">
                <label class="form-label">{{ 'Baud rate' | translate }}</label>
                <select class="form-select dev-baud" [attr.data-slot]="p.slot" [ngModel]="serialBaud(p.slot)" (ngModelChange)="setSerialBaud(p.slot, $event)" [name]="p.slot + 'baud'">
                  <option *ngFor="let b of bauds" [ngValue]="b">{{ b }}</option>
                </select>
              </div>
              <div class="col-auto">
                <label class="form-label">{{ 'Flow control' | translate }}</label>
                <select class="form-select" [ngModel]="serialFlow(p.slot)" (ngModelChange)="setSerialFlow(p.slot, $event)" [name]="p.slot + 'flow'">
                  <option value="none">{{ 'None' | translate }}</option>
                  <option value="hardware">{{ 'Hardware (RTS/CTS)' | translate }}</option>
                </select>
              </div>
            </ng-container>
            <div class="col-auto" *ngIf="d[p.slot].mode !== 'driver'">
              <button type="button" class="btn btn-outline-primary dev-connect" [attr.data-slot]="p.slot" (click)="connect(p.slot)">
                {{ connected(p.slot) ? ('Choose another device…' | translate) : ('Connect device…' | translate) }}
              </button>
            </div>
            <div class="col-auto" *ngIf="p.kitchen">
              <label class="form-label">{{ 'Stations on this printer' | translate }}</label>
              <input class="form-control dev-stations" [attr.data-slot]="p.slot" [ngModel]="stations(p.slot)" (ngModelChange)="setStations(p.slot, $event)" [name]="p.slot + 'st'" [placeholder]="'Grill, Pizza (empty = all other stations)' | translate" style="min-width:260px;" />
            </div>
          </div>
          <p class="mt-2 mb-2 dev-conn-name" [attr.data-slot]="p.slot" *ngIf="d[p.slot].mode !== 'driver'">
            <span *ngIf="connected(p.slot)">{{ 'Connected:' | translate }} <strong>{{ connName(p.slot) }}</strong></span>
            <span *ngIf="!connected(p.slot)" class="text-muted">{{ 'No device chosen yet.' | translate }}</span>
          </p>
          <div class="text-danger dev-slot-err" *ngIf="devices.lastError[p.slot]">{{ devices.lastError[p.slot] }}</div>
          <div class="d-flex gap-2 mt-2">
            <button type="button" class="btn btn-outline-secondary dev-test" [attr.data-slot]="p.slot" (click)="test(p.slot)">{{ 'Print test page' | translate }}</button>
            <button type="button" class="btn btn-outline-secondary dev-drawer" *ngIf="p.slot === 'receipt'" (click)="drawer()">{{ 'Open cash drawer' | translate }}</button>
            <button type="button" class="btn btn-outline-danger dev-forget" [attr.data-slot]="p.slot" *ngIf="connected(p.slot)" (click)="forget(p.slot)">{{ 'Forget' | translate }}</button>
          </div>
        </div>
      </div>

      <div class="card mb-3">
        <div class="card-body">
          <h3 class="card-title">{{ 'Paper and options' | translate }}</h3>
          <div class="row g-3">
            <div class="col-auto">
              <label class="form-label">{{ 'Paper width' | translate }}</label>
              <select class="form-select dev-paper" [(ngModel)]="d.paper" name="paper">
                <option value="auto">{{ 'As in POS settings' | translate }}</option>
                <option value="80">{{ '80 mm' | translate }}</option>
                <option value="58">{{ '58 mm' | translate }}</option>
              </select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Characters per line' | translate }}</label>
              <input class="form-control dev-cols" type="number" min="0" max="80" [(ngModel)]="d.cols" name="cols" style="width:110px;" />
              <small class="text-muted">{{ '0 = 48 on 80 mm, 32 on 58 mm' | translate }}</small>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Code page' | translate }}</label>
              <select class="form-select dev-codepage" [(ngModel)]="d.codepage" name="codepage">
                <option value="cp858">{{ 'PC858 (Latin, accents and €)' | translate }}</option>
                <option value="cp850">{{ 'PC850 (Latin)' | translate }}</option>
                <option value="ascii">{{ 'Plain ASCII (no accents)' | translate }}</option>
              </select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Copies' | translate }}</label>
              <input class="form-control dev-copies" type="number" min="1" max="5" [(ngModel)]="d.copies" name="copies" style="width:90px;" />
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Cash drawer pin' | translate }}</label>
              <select class="form-select" [ngModel]="d.drawerPin" (ngModelChange)="d.drawerPin = +$event === 1 ? 1 : 0" name="pin">
                <option [ngValue]="0">{{ 'Pin 2 (usual)' | translate }}</option>
                <option [ngValue]="1">{{ 'Pin 5' | translate }}</option>
              </select>
            </div>
          </div>
          <div class="mt-3">
            <label class="d-block"><input type="checkbox" class="dev-cut" [(ngModel)]="d.cut" name="cut" /> {{ 'Cut the paper after each document' | translate }}</label>
            <label class="d-block"><input type="checkbox" class="dev-drawer-cash" [(ngModel)]="d.drawerOnCash" name="dcash" /> {{ 'Open the cash drawer when a sale with a cash payment is printed (direct mode)' | translate }}</label>
            <label class="d-block"><input type="checkbox" class="dev-auto-kitchen" [(ngModel)]="d.autoKitchen" name="ak" /> {{ 'Print kitchen / bar tickets when items are sent to the kitchen (needs a direct kitchen or bar printer)' | translate }}</label>
          </div>
        </div>
      </div>

      <div class="card mb-3">
        <div class="card-body">
          <h3 class="card-title">{{ 'Customer display (COM)' | translate }}</h3>
          <p class="text-muted" style="font-size:0.9rem;">{{ 'A two-line pole display on a serial port (RS-232, or a USB-to-COM adapter). It shows each item as it is rung up, the total, the amount paid and a welcome text.' | translate }}</p>
          <label class="d-block mb-2"><input type="checkbox" class="dev-disp-on" [(ngModel)]="d.display.enabled" name="don" /> {{ 'Use a customer display on this PC' | translate }}</label>
          <div class="row g-3 align-items-end">
            <div class="col-auto">
              <button type="button" class="btn btn-outline-primary dev-disp-connect" (click)="connectDisplay()">{{ d.display.name ? ('Choose another port…' | translate) : ('Connect COM port…' | translate) }}</button>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Command set' | translate }}</label>
              <select class="form-select dev-disp-protocol" [(ngModel)]="d.display.protocol" name="dproto">
                <option value="escpos">{{ 'ESC/POS (Epson DM-D and compatible)' | translate }}</option>
                <option value="cd5220">{{ 'CD5220 (Logic Controls, Aedex)' | translate }}</option>
                <option value="plain">{{ 'Plain text' | translate }}</option>
              </select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Baud rate' | translate }}</label>
              <select class="form-select dev-disp-baud" [(ngModel)]="d.display.baud" name="dbaud">
                <option *ngFor="let b of bauds" [ngValue]="b">{{ b }}</option>
              </select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Data bits' | translate }}</label>
              <select class="form-select" [(ngModel)]="d.display.dataBits" name="dbits"><option [ngValue]="8">8</option><option [ngValue]="7">7</option></select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Parity' | translate }}</label>
              <select class="form-select" [(ngModel)]="d.display.parity" name="dpar"><option value="none">{{ 'None' | translate }}</option><option value="even">{{ 'Even' | translate }}</option><option value="odd">{{ 'Odd' | translate }}</option></select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Stop bits' | translate }}</label>
              <select class="form-select" [(ngModel)]="d.display.stopBits" name="dstop"><option [ngValue]="1">1</option><option [ngValue]="2">2</option></select>
            </div>
            <div class="col-auto">
              <label class="form-label">{{ 'Characters per line' | translate }}</label>
              <input class="form-control dev-disp-cols" type="number" min="8" max="40" [(ngModel)]="d.display.cols" name="dcols" style="width:100px;" />
            </div>
          </div>
          <div class="row g-3 mt-1">
            <div class="col-auto"><label class="form-label">{{ 'Welcome, line 1' | translate }}</label><input class="form-control dev-disp-w1" [(ngModel)]="d.display.welcome1" name="w1" maxlength="40" /></div>
            <div class="col-auto"><label class="form-label">{{ 'Welcome, line 2' | translate }}</label><input class="form-control dev-disp-w2" [(ngModel)]="d.display.welcome2" name="w2" maxlength="40" /></div>
          </div>
          <p class="mt-2 mb-2 dev-disp-name" *ngIf="d.display.name">{{ 'Port:' | translate }} <strong>{{ d.display.name }}</strong></p>
          <div class="text-danger dev-slot-err" *ngIf="devices.lastError['display']">{{ devices.lastError['display'] }}</div>
          <div class="d-flex gap-2 mt-2">
            <button type="button" class="btn btn-outline-secondary dev-disp-test" (click)="testDisplay()">{{ 'Send test message' | translate }}</button>
            <button type="button" class="btn btn-outline-danger dev-disp-forget" *ngIf="d.display.name" (click)="forget('display')">{{ 'Forget' | translate }}</button>
          </div>
        </div>
      </div>

      <div class="d-flex gap-2 mb-4">
        <button type="button" class="btn btn-primary dev-save" (click)="save()">{{ 'Save' | translate }}</button>
        <button type="button" class="btn btn-outline-secondary dev-reset" (click)="reset()">{{ 'Back to defaults' | translate }}</button>
        <a routerLink="/registers" class="btn btn-link">{{ 'Back' | translate }}</a>
      </div>

      <div class="card mb-4">
        <div class="card-body" style="font-size:0.9rem;">
          <h3 class="card-title">{{ 'Notes for Windows' | translate }}</h3>
          <ul class="mb-0">
            <li><strong>{{ 'Windows driver' | translate }}</strong> {{ 'works in every browser: install the Epson driver, choose the roll size (80 mm or 58 mm) and tick "Document cut" and "Open drawer" in the driver. Receipts are laid out for the roll with no blank page after them.' | translate }}</li>
            <li><strong>{{ 'Serial / COM' | translate }}</strong> {{ 'sends raw ESC/POS and works with a printer that has an RS-232 port or a virtual COM port (Epson "TM Virtual Port Driver"). Use the baud rate set on the printer (often 9600, 19200 or 115200).' | translate }}</li>
            <li><strong>{{ 'USB' | translate }}</strong> {{ 'needs the printer to use the WinUSB driver (for example with Zadig); with the normal Epson driver Windows keeps the printer and the browser cannot open it. Use the driver or a COM port instead in that case.' | translate }}</li>
            <li>{{ 'Chrome / Edge ask once to allow each device; the permission is remembered for this site.' | translate }}</li>
          </ul>
        </div>
      </div>
    </ng-container>
  `,
})
export class DevicesComponent implements OnInit {
  s: DeviceSettings | null = null;
  readonly secure = typeof window !== 'undefined' && window.isSecureContext;
  message: string | null = null;
  error: string | null = null;
  serverPaper: string | null = null;
  readonly bauds = [1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200];
  readonly printers: { slot: PrinterSlot; title: string; hint: string; kitchen: boolean }[] = [
    { slot: 'receipt', title: 'Receipt printer', hint: 'Sale receipts, returns, restaurant bills, X / Z reports and the cash drawer.', kitchen: false },
    { slot: 'kitchen', title: 'Kitchen printer (optional)', hint: 'Order tickets for the kitchen stations. Without one, tickets go to the receipt printer when you print them by hand.', kitchen: true },
    { slot: 'bar', title: 'Bar printer (optional)', hint: 'Order tickets for the stations listed here (for example Bar).', kitchen: true },
  ];

  constructor(
    readonly devices: DeviceService,
    private pole: PoleDisplayService,
    private prints: PrintService,
    private registerApi: RegisterApiService
  ) {}

  ngOnInit(): void {
    this.s = JSON.parse(JSON.stringify(this.devices.settings));
    this.registerApi.posSettings().subscribe({ next: (c) => (this.serverPaper = c.receiptPaper), error: () => (this.serverPaper = null) });
  }

  private refresh(): void {
    this.s = JSON.parse(JSON.stringify(this.devices.settings));
  }

  connected(slot: PrinterSlot): boolean {
    const c = this.s?.[slot];
    return !!c && ((c.mode === 'usb' && !!c.usb) || (c.mode === 'serial' && !!c.serial));
  }

  connName(slot: PrinterSlot): string {
    const c = this.s![slot];
    if (c.mode === 'usb' && c.usb) return c.usb.name || `USB ${c.usb.vendorId.toString(16)}:${c.usb.productId.toString(16)}`;
    return c.serial?.name || 'Serial port';
  }

  stations(slot: PrinterSlot): string {
    return slot === 'receipt' ? '' : this.s![slot].stations;
  }

  setStations(slot: PrinterSlot, v: string): void {
    if (slot !== 'receipt') this.s![slot].stations = v;
  }

  serialBaud(slot: PrinterSlot): number {
    return this.s![slot].serial?.baud ?? 9600;
  }

  serialFlow(slot: PrinterSlot): string {
    return this.s![slot].serial?.flow ?? 'none';
  }

  setSerialBaud(slot: PrinterSlot, baud: number): void {
    const c = this.s![slot];
    c.serial = { flow: 'none', index: 0, ...(c.serial ?? {}), baud: +baud };
  }

  setSerialFlow(slot: PrinterSlot, flow: string): void {
    const c = this.s![slot];
    c.serial = { baud: 9600, index: 0, ...(c.serial ?? {}), flow: flow === 'hardware' ? 'hardware' : 'none' };
  }

  private run(fn: () => Promise<void>): void {
    this.message = null;
    this.error = null;
    fn().catch((e) => (this.error = (e && e.message) || String(e)));
  }

  connect(slot: PrinterSlot): void {
    this.run(async () => {
      const keep = this.s!;
      const mode: PrinterMode = keep[slot].mode;
      this.devices.save(keep);
      if (mode === 'usb') await this.devices.pairUsb(slot);
      else await this.devices.pairSerial(slot, this.serialBaud(slot), this.serialFlow(slot) as 'none' | 'hardware');
      this.refresh();
      this.message = 'Device saved. Print a test page to check it.';
    });
  }

  connectDisplay(): void {
    this.run(async () => {
      this.devices.save(this.s!);
      await this.devices.pairDisplay();
      this.refresh();
      this.message = 'COM port saved. Send a test message to check it.';
    });
  }

  forget(slot: PrinterSlot | 'display'): void {
    this.devices.save(this.s!);
    this.devices.forget(slot);
    this.refresh();
  }

  test(slot: PrinterSlot): void {
    this.run(async () => {
      this.devices.save(this.s!);
      if (!this.devices.isDirect(slot)) {
        this.message = 'This printer uses the Windows driver: the test page opens the print dialog.';
        this.devices.printTextViaDriver('NIWE POS\nPRINTER TEST\n' + new Date().toLocaleString() + '\n0123456789012345678901234567890123456789\n', this.devices.layout(this.serverPaper).paper, 'Test');
        return;
      }
      const r = await this.prints.testPage(slot, this.serverPaper);
      if (r.ok) this.message = 'Test page sent.';
      else this.error = r.error || 'Could not print.';
    });
  }

  drawer(): void {
    this.run(async () => {
      this.devices.save(this.s!);
      const r = await this.devices.openDrawer();
      if (r.ok) this.message = 'Drawer pulse sent.';
      else this.error = r.error || 'Could not open the drawer.';
    });
  }

  testDisplay(): void {
    this.run(async () => {
      this.devices.save(this.s!);
      await this.pole.close();
      const err = await this.pole.test();
      if (err) this.error = err;
      else this.message = 'Test message sent to the display.';
    });
  }

  save(): void {
    this.devices.save(this.s!);
    this.refresh();
    this.message = 'Saved on this PC.';
    this.error = null;
  }

  reset(): void {
    if (!confirm('Reset the devices of this PC to the defaults?')) return;
    this.devices.reset();
    this.refresh();
    this.message = 'Reset.';
  }
}
