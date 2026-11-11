import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, StoreListing, StoreSettings, StoreZone } from '../core/api/online-store-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product } from '../core/models';
import { T } from '../core/i18n/t';

/**
 * Online Store > Shop settings: the shop's switches and limits, the products shown on it and the delivery areas. The shop stays closed until it is
 * switched on here, and only products added below are ever shown or sold online.
 */
@Component({
  selector: 'app-store-setup',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Shop settings' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
      </div>

      <form class="card mb-3" style="padding:1rem;" *ngIf="s" (ngSubmit)="saveSettings()">
        <h4 class="mb-2">{{ 'The shop' | translate }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="enabled" [(ngModel)]="s.enabled" /> {{ 'Shop is open to the public' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="paused" [(ngModel)]="s.paused" /> {{ 'Pause orders for now' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="delivery" [(ngModel)]="s.deliveryEnabled" /> {{ 'Delivery' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="pickup" [(ngModel)]="s.pickupEnabled" /> {{ 'Pick-up' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="cod" [(ngModel)]="s.codEnabled" /> {{ 'Pay cash on delivery or at pick-up' | translate }}</label>
        </div>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Shop name (empty = business name)' | translate }}</label><input class="form-control" name="shopName" maxlength="120" [(ngModel)]="s.shopName" /></div>
          <div class="form-group"><label class="form-label">{{ 'Phone shown on the shop' | translate }}</label><input class="form-control" name="phone" maxlength="40" [(ngModel)]="s.contactPhone" /></div>
          <div class="form-group" style="min-width:260px;"><label class="form-label">{{ 'Pick-up address' | translate }}</label><input class="form-control" name="pickupAddress" maxlength="240" [(ngModel)]="s.pickupAddress" /></div>
        </div>
        <div class="form-group mt-2" style="max-width:420px;"><label class="form-label">{{ 'Send new-order e-mails to (empty = business e-mail)' | translate }}</label><input class="form-control" name="notifyEmail" type="email" maxlength="160" [(ngModel)]="s.notifyEmail" /></div>
        <div class="form-group mt-2"><label class="form-label">{{ 'Welcome text' | translate }}</label><input class="form-control" name="intro" maxlength="300" [(ngModel)]="s.introText" /></div>
        <div class="form-group mt-2" *ngIf="s.paused"><label class="form-label">{{ 'Message shown while paused' | translate }}</label><input class="form-control" name="pauseMessage" maxlength="200" [(ngModel)]="s.pauseMessage" /></div>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Units kept back for the till' | translate }}</label><input class="form-control" type="number" min="0" max="1000" style="width:140px;" name="buffer" [(ngModel)]="s.stockBuffer" /></div>
          <div class="form-group"><label class="form-label">{{ 'Minimum order' | translate }}</label><input class="form-control" type="number" min="0" style="width:140px;" name="minOrder" [(ngModel)]="s.minOrder" /></div>
          <div class="form-group"><label class="form-label">{{ 'Free delivery from' | translate }}</label><input class="form-control" type="number" min="0" style="width:140px;" name="freeFrom" [(ngModel)]="s.freeDeliveryOver" [placeholder]="'never' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Orders waiting per phone' | translate }}</label><input class="form-control" type="number" min="1" max="50" style="width:140px;" name="pendingPhone" [(ngModel)]="s.maxPendingPerPhone" /></div>
          <div class="form-group"><label class="form-label">{{ 'Orders per connection per hour' | translate }}</label><input class="form-control" type="number" min="1" max="1000" style="width:140px;" name="perIp" [(ngModel)]="s.maxPerIpPerHour" /></div>
        </div>
        <div class="form-group mt-2" style="max-width:420px;">
          <label class="form-label">{{ 'Service product for the delivery fee' | translate }}</label>
          <select class="form-select" name="deliveryProduct" [(ngModel)]="s.deliveryProductId">
            <option [ngValue]="null">{{ '— none (needed before delivery orders with a fee can be accepted) —' | translate }}</option>
            <option *ngFor="let p of serviceProducts" [ngValue]="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div class="mt-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save settings' | translate }}</button></div>
      </form>

      <div class="card mb-3" style="padding:1rem;">
        <h4 class="mb-2">{{ 'Products on the shop' | translate }}</h4>
        <p class="text-muted">{{ 'Only the products you add here are shown and sold online. The photo is the product’s own picture. Leave the online price empty to use the normal price.' | translate }}</p>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <input class="form-control" style="width:260px;" [placeholder]="'Search a product to add' | translate" [(ngModel)]="search" name="search" (keyup.enter)="find()" />
          <button class="btn btn-outline-secondary" type="button" (click)="find()">{{ 'Search' | translate }}</button>
        </div>
        <div *ngFor="let p of found" class="flex justify-between" style="padding:0.2rem 0; border-bottom:1px solid #e5e7eb;">
          <span>{{ p.name }} <span class="text-muted">{{ p.sku }} · {{ p.unitPrice | number: '1.0-2' }}</span></span>
          <button class="btn btn-sm btn-primary" type="button" [disabled]="isListed(p.id)" (click)="add(p)">{{ isListed(p.id) ? ('Added' | translate) : ('Add' | translate) }}</button>
        </div>
        <table class="table-sm mt-2" *ngIf="listings.length">
          <thead><tr><th>{{ 'Product' | translate }}</th><th>{{ 'Title on the shop' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th class="text-end">{{ 'Online price' | translate }}</th><th>{{ 'Order' | translate }}</th><th>{{ 'Shown' | translate }}</th><th>{{ 'Stock' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let l of listings">
              <td>{{ l.productName }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="!l.hasImage">{{ 'No picture yet' | translate }}</div></td>
              <td><input class="form-control" style="width:200px;" maxlength="160" [name]="'t' + l.id" [(ngModel)]="l.webTitle" [placeholder]="l.productName" /></td>
              <td class="text-end">{{ l.unitPrice | number: '1.0-2' }}</td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" [name]="'p' + l.id" [(ngModel)]="l.onlinePrice" /></td>
              <td><input class="form-control" type="number" style="width:70px;" [name]="'o' + l.id" [(ngModel)]="l.sortOrder" /></td>
              <td><input type="checkbox" [name]="'v' + l.id" [(ngModel)]="l.listed" /></td>
              <td>{{ stockLabel(l.stock) | translate }}</td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveListing(l)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="removeListing(l)">{{ 'Remove' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!listings.length">{{ 'Nothing is on the shop yet.' | translate }}</p>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 class="mb-2">{{ 'Delivery areas' | translate }}</h4>
        <p class="text-muted">{{ 'With no area the shop delivers anywhere for free. With areas, the customer must choose one and pays its fee.' | translate }}</p>
        <table class="table-sm">
          <thead><tr><th>{{ 'Area' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th><th class="text-end">{{ 'Minimum order' | translate }}</th><th>{{ 'Active' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let z of zones">
              <td><input class="form-control" style="width:200px;" maxlength="80" [name]="'zn' + z.id" [(ngModel)]="z.name" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" [name]="'zf' + z.id" [(ngModel)]="z.fee" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" [name]="'zm' + z.id" [(ngModel)]="z.minOrder" /></td>
              <td><input type="checkbox" [name]="'za' + z.id" [(ngModel)]="z.active" /></td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveZone(z)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="deleteZone(z)">{{ 'Remove' | translate }}</button></td>
            </tr>
            <tr>
              <td><input class="form-control" style="width:200px;" maxlength="80" name="newZoneName" [(ngModel)]="newZone.name" [placeholder]="'New area' | translate" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" name="newZoneFee" [(ngModel)]="newZone.fee" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" name="newZoneMin" [(ngModel)]="newZone.minOrder" /></td>
              <td></td>
              <td><button class="btn btn-sm btn-primary" type="button" (click)="addZone()">{{ 'Add area' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class StoreSetupComponent implements OnInit {
  s: StoreSettings | null = null;
  listings: StoreListing[] = [];
  zones: StoreZone[] = [];
  serviceProducts: Product[] = [];
  found: Product[] = [];
  search = '';
  newZone: { name: string; fee: number; minOrder: number } = { name: '', fee: 0, minOrder: 0 };
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private api: OnlineStoreApiService, private catalog: CatalogApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => this.fail(e, 'Failed to load the settings') });
    this.loadListings();
    this.loadZones();
    this.catalog.searchProducts(null, null).subscribe({ next: (all) => (this.serviceProducts = all.filter((p) => (p as any).service && p.active)), error: () => {} });
  }

  private fail(e: any, fallback: string): void {
    this.message = null;
    this.error = e?.error?.message ?? T(fallback);
  }

  private ok(text: string): void {
    this.error = null;
    this.message = T(text);
  }

  stockLabel(s: string): string {
    return s === 'SOLD_OUT' ? 'Sold out' : s === 'LOW' ? 'Low stock' : 'In stock';
  }

  saveSettings(): void {
    if (!this.s) return;
    this.busy = true;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => { this.s = s; this.busy = false; this.ok('Settings saved'); },
      error: (e) => { this.busy = false; this.fail(e, 'Could not save the settings'); },
    });
  }

  loadListings(): void {
    this.api.listings().subscribe({ next: (l) => (this.listings = l), error: (e) => this.fail(e, 'Failed to load the products') });
  }

  isListed(productId: number): boolean {
    return this.listings.some((l) => l.productId === productId);
  }

  find(): void {
    this.catalog.searchProducts(null, this.search.trim() || null).subscribe({ next: (p) => (this.found = p.filter((x) => x.active).slice(0, 20)), error: (e) => this.fail(e, 'Search failed') });
  }

  add(p: Product): void {
    this.api.saveListing({ productId: p.id, listed: true }).subscribe({ next: () => { this.loadListings(); this.ok('Added to the shop'); }, error: (e) => this.fail(e, 'Could not add the product') });
  }

  saveListing(l: StoreListing): void {
    this.api.saveListing({ productId: l.productId, listed: l.listed, webTitle: l.webTitle, webDescription: l.webDescription, onlinePrice: l.onlinePrice === null || (l.onlinePrice as any) === '' ? null : l.onlinePrice, sortOrder: l.sortOrder })
      .subscribe({ next: () => { this.loadListings(); this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save the product') });
  }

  removeListing(l: StoreListing): void {
    if (!confirm(T('Remove {p0} from the shop?', { p0: l.productName }))) return;
    this.api.removeListing(l.id).subscribe({ next: () => { this.loadListings(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove the product') });
  }

  loadZones(): void {
    this.api.zones().subscribe({ next: (z) => (this.zones = z), error: (e) => this.fail(e, 'Failed to load the delivery areas') });
  }

  addZone(): void {
    if (!this.newZone.name.trim()) { this.error = T('Give the area a name'); return; }
    this.api.saveZone(null, { name: this.newZone.name, fee: this.newZone.fee || 0, minOrder: this.newZone.minOrder || 0, active: true }).subscribe({
      next: () => { this.newZone = { name: '', fee: 0, minOrder: 0 }; this.loadZones(); this.ok('Area added'); },
      error: (e) => this.fail(e, 'Could not add the area'),
    });
  }

  saveZone(z: StoreZone): void {
    this.api.saveZone(z.id, z).subscribe({ next: () => { this.loadZones(); this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save the area') });
  }

  deleteZone(z: StoreZone): void {
    if (!confirm(T('Remove the area {p0}?', { p0: z.name }))) return;
    this.api.deleteZone(z.id).subscribe({ next: () => { this.loadZones(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove the area') });
  }
}
