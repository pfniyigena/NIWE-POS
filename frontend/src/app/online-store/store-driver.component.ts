import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { emptyFilter } from '../core/api/list-state';
import { Driver, OnlineStoreApiService, StoreOrder } from '../core/api/online-store-api.service';
import { SignaturePadComponent } from './signature-pad.component';
import { T } from '../core/i18n/t';

const DRIVER_KEY = 'niwe.driver.id';

/**
 * Online Store > My deliveries (v1.56.0): the screen of a delivery driver on his phone. It lists the orders that are out for delivery (his own, or
 * everybody's), with the customer's phone and address, what to collect, and for each one a way to hand over: the code the customer reads out, or the
 * customer's signature drawn on the phone. Either completes the order in the shop (stock out, invoice, cash recorded as collected).
 */
@Component({
  selector: 'app-store-driver',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, SignaturePadComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success sd-done" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h2 style="margin:0;">{{ 'My deliveries' | translate }}</h2>
        <div class="flex gap-2 align-center">
          <select *ngIf="drivers.length > 0" class="form-select sd-driver" style="width:210px;" [(ngModel)]="driverId" name="sddriver" (ngModelChange)="chooseDriver()" [attr.aria-label]="'Driver' | translate">
            <option [ngValue]="null">{{ 'All drivers' | translate }}</option>
            <option *ngFor="let d of drivers" [ngValue]="d.id">{{ d.name }}</option>
          </select>
          <button class="btn btn-outline-secondary btn-sm" (click)="load()">↻ {{ 'Refresh' | translate }}</button>
        </div>
      </div>
      <p class="text-muted sd-none" *ngIf="loaded && orders.length === 0">{{ 'Nothing to deliver right now.' | translate }}</p>

      <div class="card sd-order mb-3" *ngFor="let o of orders" style="padding:0.9rem;">
        <div class="flex justify-between" style="gap:0.5rem; flex-wrap:wrap;">
          <div><strong class="sd-code" style="font-size:1.15rem;">{{ o.code }}</strong> <span class="text-muted" *ngIf="o.driverName && !driverId">· {{ o.driverName }}</span></div>
          <strong class="sd-collect" [style.color]="o.paymentStatus === 'PAID' ? '#15803d' : '#b45309'">{{ o.paymentStatus === 'PAID' ? ('Already paid' | translate) : (('To collect' | translate) + ': ' + (o.total | number: '1.0-2')) }}</strong>
        </div>
        <div style="margin-top:0.35rem;">
          <div><strong>{{ o.customerName }}</strong> · <a class="sd-call" [href]="'tel:' + o.customerPhone">📞 {{ o.customerPhone }}</a></div>
          <div *ngIf="o.zoneName || o.customerAddress">📍 {{ o.zoneName }}<ng-container *ngIf="o.zoneName && o.customerAddress"> — </ng-container>{{ o.customerAddress }}
            <a *ngIf="o.customerAddress" class="sd-map" style="margin-left:0.4rem;" target="_blank" rel="noopener" [href]="mapLink(o)">{{ 'Map' | translate }}</a></div>
          <div class="text-muted" *ngIf="o.notes" style="font-size:0.9rem;">“{{ o.notes }}”</div>
          <ul style="margin:0.4rem 0 0; padding-left:1.1rem;"><li *ngFor="let l of o.lines">{{ l.quantity }} × {{ l.name }}</li></ul>
        </div>

        <button class="btn btn-primary mt-2 sd-handover-open" *ngIf="open !== o.id" (click)="start(o)">{{ 'Hand over' | translate }}</button>

        <div *ngIf="open === o.id" class="sd-handover mt-2" style="border-top:1px dashed #9ca3af; padding-top:0.6rem;">
          <div class="form-group">
            <label class="form-label">{{ "Customer's delivery code" | translate }}</label>
            <div class="flex gap-2">
              <input class="form-control sd-code-input" style="width:140px; letter-spacing:0.3em; font-weight:700; font-size:1.2rem;" maxlength="4" inputmode="numeric" autocomplete="off" [(ngModel)]="code" name="sdcode" placeholder="••••" />
              <button class="btn btn-primary sd-confirm-code" [disabled]="busy || !code.trim()" (click)="send(o, true)">{{ 'Confirm with the code' | translate }}</button>
            </div>
          </div>
          <div class="text-muted" style="text-align:center; margin:0.4rem 0;">— {{ 'or' | translate }} —</div>
          <div class="sd-sign-box">
            <div style="font-weight:600; margin-bottom:0.3rem;">{{ 'The customer signs' | translate }}</div>
            <app-signature-pad [height]="170" (signature)="signature = $event"></app-signature-pad>
            <input class="form-control mt-1 sd-signed-name" maxlength="120" [(ngModel)]="signedName" name="sdname" [placeholder]="'Name of the person who signs (optional)' | translate" />
            <button class="btn btn-success mt-2 sd-confirm-sign" style="width:100%;" [disabled]="busy || !signature" (click)="send(o, false)">✔ {{ 'Confirm with the signature' | translate }}</button>
          </div>
          <button class="btn btn-sm btn-outline-secondary mt-2" (click)="open = null">{{ 'Cancel' | translate }}</button>
        </div>
      </div>
    </app-shell>
  `,
})
export class StoreDriverComponent implements OnInit, OnDestroy {
  orders: StoreOrder[] = [];
  drivers: Driver[] = [];
  driverId: number | null = null;
  loaded = false;
  busy = false;
  error: string | null = null;
  message: string | null = null;
  open: number | null = null;
  code = '';
  signature: string | null = null;
  signedName = '';
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    try {
      const v = Number(localStorage.getItem(DRIVER_KEY));
      this.driverId = Number.isInteger(v) && v > 0 ? v : null;
    } catch {
      this.driverId = null;
    }
    this.api.drivers().subscribe({
      next: (d) => {
        this.drivers = d.filter((x) => x.active);
        if (this.driverId !== null && !this.drivers.some((x) => x.id === this.driverId)) this.driverId = null;
        this.load();
      },
      error: () => this.load(),
    });
    this.poll = setInterval(() => { if (!this.busy && this.open === null) this.load(); }, 20000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** A driver remembers who he is on his phone. */
  chooseDriver(): void {
    try {
      if (this.driverId === null) localStorage.removeItem(DRIVER_KEY);
      else localStorage.setItem(DRIVER_KEY, String(this.driverId));
    } catch {
      // not remembered: he picks his name again next time
    }
    this.open = null;
    this.load();
  }

  load(): void {
    const f = emptyFilter();
    f.status = 'OUT_FOR_DELIVERY';
    this.api.page(f, 0, 100, this.driverId).subscribe({
      next: (r) => { this.orders = r.items; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Failed to load the online orders'); },
    });
  }

  mapLink(o: StoreOrder): string {
    return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent([o.customerAddress, o.zoneName].filter(Boolean).join(', '));
  }

  start(o: StoreOrder): void {
    this.open = o.id;
    this.code = '';
    this.signature = null;
    this.signedName = '';
    this.message = null;
    this.error = null;
  }

  send(o: StoreOrder, byCode: boolean): void {
    this.busy = true;
    this.error = null;
    this.api.receive(o.id, { code: byCode ? this.code.trim() : null, signature: byCode ? null : this.signature, signedName: byCode ? null : this.signedName.trim() || null }).subscribe({
      next: (r) => {
        this.busy = false;
        this.open = null;
        this.message = T('Order {p0} delivered. Invoice {p1}.', { p0: r.code, p1: r.invoiceNumber ?? '' });
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
