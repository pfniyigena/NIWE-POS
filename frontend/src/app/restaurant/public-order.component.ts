import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { NiweFooterComponent } from '../shared/niwe-footer.component';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RestaurantOnlineApiService } from '../core/api/restaurant-online-api.service';
import { PublicItem, PublicMenu, PublicOrderLine } from '../core/models';

interface CartLine {
  item: PublicItem;
  quantity: number;
  notes: string;
  optionIds: number[];
  optionNames: string;
  unit: number;
}

/**
 * The guest's ordering page, with no login. Opened from the QR code on a table (/menu/:token, orders go to that table) or from the restaurant's
 * website (/order, takeaway or delivery). The order is only a request: staff accept it and then it goes to the kitchen.
 */
@Component({
  selector: 'app-public-order',
  standalone: true,
  imports: [TranslatePipe, NiweFooterComponent, CommonModule, FormsModule],
  template: `
    <div class="po-page" style="max-width:640px; margin:0 auto; padding:0.75rem 0.75rem 6rem;">
      <div class="alert alert-danger po-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!menu && !error" class="text-muted">{{ 'Loading the menu…' | translate }}</div>

      <ng-container *ngIf="menu">
        <div style="text-align:center;" class="mb-2">
          <h2 class="po-title" style="margin-bottom:0.2rem;">{{ menu.restaurantName }}</h2>
          <div class="text-muted po-sub" *ngIf="menu.tableName">{{ 'Table {p0}' | translate: { p0: (menu.tableName) } }}</div>
          <div class="text-muted po-sub" *ngIf="!menu.tableName">{{ 'Open {p0}–{p1}' | translate: { p0: (menu.openTime), p1: (menu.closeTime) } }}</div>
        </div>
        <div class="alert alert-info po-message" *ngIf="menu.message">{{ menu.message }}</div>
        <div class="alert alert-warning po-paused" *ngIf="menu.paused">{{ 'We are not taking online orders right now.' | translate }}</div>
        <div class="alert alert-warning po-closed" *ngIf="!menu.paused && !menu.openNow">{{ 'We are closed at the moment. Orders are taken between {p0} and {p1}.' | translate: { p0: (menu.openTime), p1: (menu.closeTime) } }}</div>

        <!-- the menu -->
        <ng-container *ngIf="!reviewing">
          <div class="flex gap-1 mb-2" style="flex-wrap:wrap; position:sticky; top:0; background:var(--bg, #fff); padding:0.4rem 0; z-index:2;">
            <button type="button" class="btn btn-sm po-cat" *ngFor="let c of menu.categories" [class.btn-primary]="c.id === catId" [class.btn-outline-secondary]="c.id !== catId" (click)="catId = c.id">{{ c.name }}</button>
          </div>
          <div *ngFor="let c of menu.categories">
            <ng-container *ngIf="c.id === catId">
              <div class="card po-item" *ngFor="let i of c.items" style="padding:0.75rem; margin-bottom:0.5rem;" [style.opacity]="i.available ? 1 : 0.55">
                <div class="flex justify-between" style="gap:0.75rem;">
                  <div>
                    <div class="po-name" style="font-weight:600;">{{ i.name }}</div>
                    <div class="text-muted" style="font-size:0.8rem;" *ngIf="i.allergens">{{ 'Contains: {p0}' | translate: { p0: (i.allergens) } }}</div>
                    <div class="text-muted po-unavailable" style="font-size:0.8rem;" *ngIf="!i.available">{{ i.unavailableReason || ('Not available' | translate) }}</div>
                  </div>
                  <div style="text-align:right; white-space:nowrap;">
                    <div class="po-price">{{ i.price | number: '1.0-2' }}</div>
                    <button type="button" class="btn btn-sm btn-primary po-add" *ngIf="i.available" [disabled]="!canOrder()" (click)="pick(i)">{{ 'Add' | translate }}</button>
                  </div>
                </div>
                <div *ngIf="picking?.id === i.id" class="po-picker mt-2" style="border-top:1px solid var(--border, #ddd); padding-top:0.5rem;">
                  <div *ngFor="let g of i.groups" class="mb-2">
                    <div style="font-weight:600; font-size:0.9rem;">{{ g.name }} <span class="text-muted" style="font-weight:400;">{{ g.required ? (g.maxSelect > 1 ? ('(choose up to {p0})' | translate: { p0: g.maxSelect }) : ('(choose one)' | translate)) : (g.maxSelect > 1 ? ('(optional, up to {p0})' | translate: { p0: g.maxSelect }) : ('(optional)' | translate)) }}</span></div>
                    <label *ngFor="let o of g.options" class="po-opt flex gap-2" style="align-items:center; cursor:pointer; padding:0.15rem 0;">
                      <input [type]="g.maxSelect === 1 ? 'radio' : 'checkbox'" [name]="'g' + g.id" [checked]="chosen.includes(o.id)" (change)="toggle(g, o.id)" />
                      <span>{{ o.name }}</span><span class="text-muted" *ngIf="o.priceDelta">+{{ o.priceDelta | number: '1.0-2' }}</span>
                    </label>
                  </div>
                  <div class="flex gap-2" style="align-items:center; flex-wrap:wrap;">
                    <input class="form-control po-note" style="flex:1; min-width:160px;" maxlength="200" [placeholder]="'Note for the kitchen (optional)' | translate" [(ngModel)]="pickNote" name="pn" />
                    <div class="flex gap-1" style="align-items:center;">
                      <button type="button" class="btn btn-sm btn-outline-secondary" (click)="pickQty = pickQty > 1 ? pickQty - 1 : 1">−</button>
                      <strong class="po-qty">{{ pickQty }}</strong>
                      <button type="button" class="btn btn-sm btn-outline-secondary" (click)="pickQty = pickQty < 20 ? pickQty + 1 : 20">+</button>
                    </div>
                  </div>
                  <div class="alert alert-danger mt-2 po-pick-error" *ngIf="pickError">{{ pickError }}</div>
                  <div class="flex gap-2 mt-2">
                    <button type="button" class="btn btn-primary po-pick-add" (click)="confirmPick()">{{ 'Add to my order' | translate }}</button>
                    <button type="button" class="btn btn-link" (click)="picking = null">{{ 'Cancel' | translate }}</button>
                  </div>
                </div>
              </div>
            </ng-container>
          </div>
        </ng-container>

        <!-- review and send -->
        <ng-container *ngIf="reviewing">
          <h4>{{ 'Your order' | translate }}</h4>
          <div class="card po-line" *ngFor="let l of cart; let idx = index" style="padding:0.6rem; margin-bottom:0.4rem;">
            <div class="flex justify-between" style="gap:0.5rem;">
              <div><strong>{{ l.item.name }}</strong>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="l.optionNames">{{ l.optionNames }}</div>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="l.notes">“{{ l.notes }}”</div></div>
              <div style="text-align:right; white-space:nowrap;">{{ l.unit * l.quantity | number: '1.0-2' }}
                <div class="flex gap-1 mt-1" style="justify-content:flex-end; align-items:center;">
                  <button type="button" class="btn btn-sm btn-outline-secondary" (click)="bump(l, -1)">−</button><span>{{ l.quantity }}</span><button type="button" class="btn btn-sm btn-outline-secondary" (click)="bump(l, 1)">+</button>
                  <button type="button" class="btn btn-sm btn-outline-danger po-remove" (click)="removeLine(idx)">✕</button>
                </div></div>
            </div>
          </div>

          <div class="card mt-2 mb-2" style="padding:0.75rem;">
            <div *ngIf="!menu.tableOrdering" class="flex gap-2 mb-2">
              <button type="button" class="btn po-mode-takeaway" *ngIf="menu.takeaway" [class.btn-primary]="mode === 'TAKEAWAY'" [class.btn-outline-secondary]="mode !== 'TAKEAWAY'" (click)="mode = 'TAKEAWAY'">{{ 'Pick up' | translate }}</button>
              <button type="button" class="btn po-mode-delivery" *ngIf="menu.delivery" [class.btn-primary]="mode === 'DELIVERY'" [class.btn-outline-secondary]="mode !== 'DELIVERY'" (click)="mode = 'DELIVERY'">{{ 'Delivery' | translate }}</button>
            </div>
            <div class="form-group mb-2" *ngIf="!menu.tableOrdering"><label class="form-label">{{ 'Your name' | translate }}</label>
              <input class="form-control po-cname" maxlength="80" [(ngModel)]="name" name="cn" autocomplete="name" /></div>
            <div class="form-group mb-2" *ngIf="!menu.tableOrdering"><label class="form-label">{{ 'Phone number' | translate }}</label>
              <input class="form-control po-cphone" type="tel" maxlength="40" [(ngModel)]="phone" name="cp" autocomplete="tel" /></div>
            <div class="form-group mb-2" *ngIf="menu.tableOrdering"><label class="form-label">{{ 'Your name (optional)' | translate }}</label>
              <input class="form-control po-cname" maxlength="80" [(ngModel)]="name" name="cn2" /></div>
            <div class="form-group mb-2"><label class="form-label">{{ 'E-mail (optional, for updates)' | translate }}</label>
              <input class="form-control po-cemail" type="email" maxlength="160" [(ngModel)]="email" name="ce" autocomplete="email" /></div>
            <ng-container *ngIf="mode === 'DELIVERY'">
              <div class="form-group mb-2" *ngIf="menu.zones.length"><label class="form-label">{{ 'Delivery area' | translate }}</label>
                <select class="form-select po-zone" [(ngModel)]="zoneId" name="zn">
                  <option [ngValue]="null">{{ '— Choose your area —' | translate }}</option>
                  <option *ngFor="let z of menu.zones" [ngValue]="z.id">{{ '{p0} (fee {p1})' | translate: { p0: (z.name), p1: (z.fee | number: '1.0-2') } }}</option>
                </select></div>
              <div class="form-group mb-2"><label class="form-label">{{ 'Delivery address' | translate }}</label>
                <textarea class="form-control po-caddr" rows="2" maxlength="240" [(ngModel)]="address" name="ca"></textarea></div>
            </ng-container>
            <div class="form-group mb-2"><label class="form-label">{{ 'Anything else?' | translate }}</label>
              <input class="form-control po-notes" maxlength="300" [(ngModel)]="notes" name="nt" /></div>
            <div class="flex justify-between"><span>{{ 'Items' | translate }}</span><strong class="po-subtotal">{{ subtotal() | number: '1.0-2' }}</strong></div>
            <div class="flex justify-between" *ngIf="mode === 'DELIVERY' && fee() > 0"><span>{{ 'Delivery fee' | translate }}</span><strong class="po-fee">{{ fee() | number: '1.0-2' }}</strong></div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Tax and any service charge are added by the restaurant. You pay {p0}.' | translate: { p0: (menu.tableOrdering ? 'at the table' : mode === 'DELIVERY' ? 'on delivery' : 'when you collect') } }}</div>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="minimum() > 0">{{ 'Minimum order {p0}.' | translate: { p0: (minimum() | number: '1.0-2') } }}</div>
            <!-- hidden from real people; bots fill it -->
            <input type="text" tabindex="-1" autocomplete="off" aria-hidden="true" [(ngModel)]="website" name="website" style="position:absolute; left:-9999px; height:0; width:0; opacity:0;" />
          </div>
          <div class="alert alert-danger po-place-error" *ngIf="placeError">{{ placeError }}</div>
          <div class="flex gap-2">
            <button type="button" class="btn btn-primary po-place" [disabled]="busy || !canOrder()" (click)="place()">{{ busy ? ('Sending…' | translate) : ('Send my order' | translate) }}</button>
            <button type="button" class="btn btn-outline-secondary po-back" (click)="reviewing = false">{{ '← Back to the menu' | translate }}</button>
          </div>
        </ng-container>
      </ng-container>

      <div *ngIf="menu && !reviewing && cart.length" class="po-cart" style="position:fixed; left:0; right:0; bottom:0; padding:0.6rem 0.75rem; background:var(--bg, #fff); border-top:1px solid var(--border, #ddd); z-index:5;">
        <div style="max-width:640px; margin:0 auto;">
          <button type="button" class="btn btn-primary po-open-cart" style="width:100%;" (click)="reviewing = true; placeError = null">
            {{ 'View my order — {p0} item{p1} · {p2}' | translate: { p0: (count()), p1: (count() === 1 ? '' : 's'), p2: (subtotal() | number: '1.0-2') } }}
          </button>
        </div>
      </div>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
})
export class PublicOrderComponent implements OnInit {
  menu: PublicMenu | null = null;
  error: string | null = null;
  placeError: string | null = null;
  token: string | null = null;
  catId: number | null = null;
  cart: CartLine[] = [];
  reviewing = false;
  busy = false;

  picking: PublicItem | null = null;
  pickQty = 1;
  pickNote = '';
  pickError: string | null = null;
  chosen: number[] = [];

  mode = 'TAKEAWAY';
  name = '';
  phone = '';
  email = '';
  address = '';
  notes = '';
  zoneId: number | null = null;
  website = '';

  constructor(private api: RestaurantOnlineApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token');
    this.api.menu(this.token).subscribe({
      next: (m) => {
        this.menu = m;
        this.catId = m.categories[0]?.id ?? null;
        this.mode = m.tableOrdering ? 'TABLE' : m.takeaway ? 'TAKEAWAY' : 'DELIVERY';
      },
      error: (e) => (this.error = e?.error?.message ?? 'This page is not available right now.'),
    });
  }

  canOrder(): boolean {
    return !!this.menu && !this.menu.paused && (this.menu.openNow || this.menu.tableOrdering);
  }

  pick(item: PublicItem): void {
    if (!item.groups.length) {
      this.addToCart(item, [], [], 1, '');
      return;
    }
    this.picking = item;
    this.pickQty = 1;
    this.pickNote = '';
    this.pickError = null;
    this.chosen = [];
  }

  toggle(g: { id: number; maxSelect: number; options: { id: number }[] }, optionId: number): void {
    const ids = g.options.map((o) => o.id);
    if (g.maxSelect === 1) {
      this.chosen = [...this.chosen.filter((x) => !ids.includes(x)), optionId];
      return;
    }
    if (this.chosen.includes(optionId)) {
      this.chosen = this.chosen.filter((x) => x !== optionId);
    } else if (this.chosen.filter((x) => ids.includes(x)).length < g.maxSelect) {
      this.chosen = [...this.chosen, optionId];
    }
  }

  confirmPick(): void {
    const item = this.picking;
    if (!item) return;
    for (const g of item.groups) {
      const n = g.options.filter((o) => this.chosen.includes(o.id)).length;
      if (g.required && n === 0) {
        this.pickError = `Please choose from “${g.name}”.`;
        return;
      }
    }
    const names = item.groups.flatMap((g) => g.options.filter((o) => this.chosen.includes(o.id)).map((o) => o.name));
    const extra = item.groups.flatMap((g) => g.options.filter((o) => this.chosen.includes(o.id))).reduce((s, o) => s + (o.priceDelta ?? 0), 0);
    this.addToCart(item, [...this.chosen], names, this.pickQty, this.pickNote.trim(), extra);
    this.picking = null;
  }

  private addToCart(item: PublicItem, optionIds: number[], names: string[], qty: number, notes: string, extra = 0): void {
    const key = (l: CartLine) => `${l.item.id}|${[...l.optionIds].sort().join(',')}|${l.notes}`;
    const line: CartLine = { item, quantity: qty, notes, optionIds, optionNames: names.join(', '), unit: item.price + extra };
    const same = this.cart.find((l) => key(l) === key(line));
    if (same) same.quantity = Math.min(20, same.quantity + qty);
    else this.cart.push(line);
  }

  removeLine(idx: number): void {
    this.cart.splice(idx, 1);
    if (!this.cart.length) this.reviewing = false;
  }

  bump(l: CartLine, d: number): void {
    l.quantity = Math.max(1, Math.min(20, l.quantity + d));
  }

  count(): number {
    return this.cart.reduce((s, l) => s + l.quantity, 0);
  }

  subtotal(): number {
    return this.cart.reduce((s, l) => s + l.unit * l.quantity, 0);
  }

  fee(): number {
    return this.mode === 'DELIVERY' ? this.menu?.zones.find((z) => z.id === this.zoneId)?.fee ?? 0 : 0;
  }

  minimum(): number {
    if (!this.menu || this.menu.tableOrdering) return 0;
    const z = this.mode === 'DELIVERY' ? this.menu.zones.find((x) => x.id === this.zoneId) : null;
    return Math.max(this.menu.minOrder ?? 0, z?.minOrder ?? 0);
  }

  place(): void {
    if (!this.menu) return;
    this.placeError = null;
    const lines: PublicOrderLine[] = this.cart.map((l) => ({ menuItemId: l.item.id, quantity: l.quantity, notes: l.notes || null, optionIds: l.optionIds }));
    this.busy = true;
    this.api
      .place({
        mode: this.menu.tableOrdering ? 'TABLE' : this.mode,
        tableToken: this.token,
        customerName: this.name,
        customerPhone: this.phone,
        customerEmail: this.email || null,
        customerAddress: this.mode === 'DELIVERY' ? this.address : null,
        zoneId: this.mode === 'DELIVERY' ? this.zoneId : null,
        notes: this.notes || null,
        lines,
        website: this.website,
      })
      .subscribe({
        next: (t) => {
          this.busy = false;
          this.router.navigate(['/order/track', t.code], { queryParams: this.token ? { t: this.token } : {} });
        },
        error: (e) => {
          this.busy = false;
          this.placeError = e?.error?.message ?? 'We could not send your order. Please try again.';
        },
      });
  }
}
