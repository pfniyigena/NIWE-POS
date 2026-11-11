import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOnlineApiService } from '../core/api/restaurant-online-api.service';
import { DeliveryZone, TableQr } from '../core/models';

import { T } from '../core/i18n/t';
/** Restaurant > Online orders > setup: delivery areas with their fees, and the QR code of every table. */
@Component({
  selector: 'app-online-setup',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Delivery areas &amp; QR codes</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/online-orders">{{ '← Online orders' | translate }}</a>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Ordering page for takeaway and delivery' | translate }}</h4>
        <div class="mb-2">{{ 'Share this address on your website, social media or a poster:' | translate }} <code class="os-link">{{ origin }}/order</code></div>
        <a class="btn btn-outline-secondary os-print-web" routerLink="/admin/restaurant/qr-print" [queryParams]="{ web: 1 }">{{ 'Print a poster QR code' | translate }}</a>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Delivery areas' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'With no areas, delivery is free and guests only give an address. With areas, guests must pick one; the fee is added to their bill.' | translate }}</div>
        <table>
          <thead><tr><th>{{ 'Area' | translate }}</th><th>{{ 'Fee' | translate }}</th><th>{{ 'Minimum order' | translate }}</th><th>{{ 'Extra minutes' | translate }}</th><th>{{ 'On' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr class="os-zone" *ngFor="let z of zones">
              <td><input class="form-control os-zname" [(ngModel)]="z.name" [name]="'zn' + z.id" /></td>
              <td><input class="form-control os-zfee" type="number" min="0" style="width:110px;" [(ngModel)]="z.fee" [name]="'zf' + z.id" /></td>
              <td><input class="form-control os-zmin" type="number" min="0" style="width:120px;" [(ngModel)]="z.minOrder" [name]="'zm' + z.id" /></td>
              <td><input class="form-control os-zextra" type="number" min="0" max="240" style="width:90px;" [(ngModel)]="z.extraMinutes" [name]="'ze' + z.id" /></td>
              <td><input type="checkbox" class="os-zactive" [(ngModel)]="z.active" [name]="'za' + z.id" /></td>
              <td class="flex gap-2"><button class="btn btn-sm btn-primary os-zsave" (click)="saveZone(z)">{{ 'Save' | translate }}</button><button class="btn btn-sm btn-outline-danger os-zdel" (click)="removeZone(z)">{{ 'Remove' | translate }}</button></td>
            </tr>
            <tr class="os-znew">
              <td><input class="form-control os-nname" [placeholder]="'New area, for example Kimihurura' | translate" [(ngModel)]="nz.name" name="nzn" /></td>
              <td><input class="form-control os-nfee" type="number" min="0" style="width:110px;" [(ngModel)]="nz.fee" name="nzf" /></td>
              <td><input class="form-control os-nmin" type="number" min="0" style="width:120px;" [(ngModel)]="nz.minOrder" name="nzm" /></td>
              <td><input class="form-control os-nextra" type="number" min="0" max="240" style="width:90px;" [(ngModel)]="nz.extraMinutes" name="nze" /></td>
              <td></td>
              <td><button class="btn btn-sm btn-primary os-nadd" [disabled]="!nz.name.trim()" (click)="addZone()">{{ 'Add the area' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="card" style="padding:1rem;">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <h4 style="font-size:1rem;">{{ 'Table QR codes' | translate }}</h4>
          <a class="btn btn-outline-primary os-print" routerLink="/admin/restaurant/qr-print">{{ 'Print all table QR codes' | translate }}</a>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Guests scan the code on their table, see the menu and send an order that you accept. If a code is shared or lost, make a new one: the old code stops working at once.' | translate }}</div>
        <table>
          <thead><tr><th>{{ 'Table' | translate }}</th><th>{{ 'Section' | translate }}</th><th>{{ 'Address' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr class="os-qr" *ngFor="let t of qr">
              <td>{{ t.name }}<span class="text-muted" *ngIf="!t.active"> {{ '(not in use)' | translate }}</span></td>
              <td>{{ t.section }}</td>
              <td><code class="os-qr-path">{{ origin }}{{ t.path }}</code></td>
              <td><button class="btn btn-sm btn-outline-secondary os-qr-new" (click)="renew(t)">{{ 'Make a new code' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class OnlineSetupComponent implements OnInit {
  zones: DeliveryZone[] = [];
  qr: TableQr[] = [];
  error: string | null = null;
  origin = typeof window !== 'undefined' ? window.location.origin : '';
  nz = { name: '', fee: 0, minOrder: 0, extraMinutes: 0 };

  constructor(private api: RestaurantOnlineApiService) {}

  ngOnInit(): void {
    this.loadZones();
    this.api.qr().subscribe({ next: (q) => (this.qr = q), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the QR codes')) });
  }

  loadZones(): void {
    this.api.zones().subscribe({ next: (z) => (this.zones = z), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the delivery areas')) });
  }

  addZone(): void {
    this.error = null;
    this.api.saveZone(null, { ...this.nz, active: true }).subscribe({
      next: () => {
        this.nz = { name: '', fee: 0, minOrder: 0, extraMinutes: 0 };
        this.loadZones();
      },
      error: (e) => (this.error = e?.error?.message ?? T('Could not add the area')),
    });
  }

  saveZone(z: DeliveryZone): void {
    this.error = null;
    this.api.saveZone(z.id, z).subscribe({ next: () => this.loadZones(), error: (e) => (this.error = e?.error?.message ?? T('Could not save the area')) });
  }

  removeZone(z: DeliveryZone): void {
    if (!confirm(T('Remove the area {p0}? If orders used it, it is only switched off.', { p0: z.name }))) return;
    this.api.deleteZone(z.id).subscribe({ next: () => this.loadZones(), error: (e) => (this.error = e?.error?.message ?? T('Could not remove the area')) });
  }

  renew(t: TableQr): void {
    if (!confirm(T('Make a new QR code for table {p0}? The printed one will stop working.', { p0: t.name }))) return;
    this.api.regenerateQr(t.tableId).subscribe({
      next: (n) => (this.qr = this.qr.map((x) => (x.tableId === n.tableId ? n : x))),
      error: (e) => (this.error = e?.error?.message ?? T('Could not make a new code')),
    });
  }
}
