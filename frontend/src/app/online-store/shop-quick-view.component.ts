import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OnlineStoreApiService, ShopProduct } from '../core/api/online-store-api.service';
import { ShopMoneyPipe } from './shop-money.pipe';
import { qtyMax } from './shop-quantity';

/** A quick look at a product in a window over the shop: picture, price, stock, a short description and add to cart, without leaving the list. */
@Component({
  selector: 'app-shop-quick-view',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShopMoneyPipe],
  template: `
    <div class="shop-qv-back" (click)="closed.emit()" style="position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:60; display:flex; align-items:center; justify-content:center; padding:0.75rem;">
      <div class="card shop-qv" role="dialog" aria-modal="true" [attr.aria-label]="p.name" (click)="$event.stopPropagation()" style="max-width:760px; width:100%; max-height:92vh; overflow:auto; padding:1rem; position:relative;">
        <button type="button" class="shop-qv-close" (click)="closed.emit()" [attr.aria-label]="'Close' | translate" style="position:absolute; right:0.6rem; top:0.4rem; border:0; background:none; font-size:1.6rem; cursor:pointer; color:inherit;">×</button>
        <div style="display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:1rem; align-items:start;">
          <div style="background:#f3f4f6; border-radius:10px; display:flex; align-items:center; justify-content:center; min-height:220px; overflow:hidden;">
            <img *ngIf="p.imageUrl" [src]="img()" [alt]="p.name" style="width:100%; max-height:360px; object-fit:contain;" />
            <span *ngIf="!p.imageUrl" style="font-size:3.5rem;">🛍️</span>
          </div>
          <div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="p.category">{{ p.category }}</div>
            <h3 class="shop-qv-name" style="margin:0.15rem 1.5rem 0.4rem 0;">{{ p.name }}</h3>
            <div *ngIf="p.ratingCount > 0" style="color:#d97706;">{{ stars(p.rating) }} <span class="text-muted">({{ p.ratingCount }})</span></div>
            <div style="font-size:1.5rem; margin-top:0.4rem;"><strong>{{ p.price | shopMoney: cur }}</strong><span class="text-muted" *ngIf="p.measureUnit" style="font-size:0.95rem;"> / {{ p.measureUnit }}</span><s class="text-muted" *ngIf="p.wasPrice" style="font-size:0.95rem; margin-left:0.5rem;">{{ p.wasPrice | shopMoney: cur }}</s>
              <span *ngIf="discount() > 0" style="margin-left:0.5rem; background:#dc2626; color:#fff; border-radius:4px; padding:0 0.4rem; font-size:0.75rem; vertical-align:middle;">-{{ discount() }}%</span></div>
            <div style="margin-top:0.25rem;" [style.color]="p.stock === 'SOLD_OUT' ? '#b91c1c' : p.stock === 'LOW' ? '#b45309' : '#15803d'">
              {{ p.stock === 'SOLD_OUT' ? ('Out of stock' | translate) : p.stock === 'LOW' ? ('Few left' | translate) : ('In stock' | translate) }}</div>
            <p class="text-muted" *ngIf="p.description" style="margin:0.6rem 0 0; white-space:pre-line;">{{ p.description | slice: 0 : 220 }}<span *ngIf="p.description.length > 220">…</span></p>
            <div class="flex gap-2 mt-3" style="align-items:center; flex-wrap:wrap;">
              <div class="flex gap-1" *ngIf="qty() > 0; else addBtn" style="align-items:center;">
                <button class="btn btn-outline-secondary" (click)="change.emit({ p: p, by: -1 })">−</button>
                <span style="min-width:1.5em; text-align:center;">{{ qty() }} {{ p.measureUnit }}</span>
                <button class="btn btn-outline-secondary" (click)="change.emit({ p: p, by: 1 })" [disabled]="qty() >= qtyMax(p)">+</button>
              </div>
              <ng-template #addBtn><button class="btn btn-primary shop-qv-add" [disabled]="p.stock === 'SOLD_OUT' || !open" (click)="change.emit({ p: p, by: 1 })">{{ 'Add to cart' | translate }}</button></ng-template>
              <a class="shop-qv-full" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" (click)="closed.emit()">{{ 'View full details' | translate }} →</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class ShopQuickViewComponent {
  @Input({ required: true }) p!: ShopProduct;
  @Input() cur = '';
  @Input() open = true;
  @Input() cart: Record<number, number> = {};
  @Output() change = new EventEmitter<{ p: ShopProduct; by: number }>();
  @Output() closed = new EventEmitter<void>();

  constructor(private api: OnlineStoreApiService) {}

  @HostListener('document:keydown.escape')
  esc(): void {
    this.closed.emit();
  }

  img(): string {
    return this.api.imageUrl(this.p.imageUrl as string) + '?w=700';
  }

  qtyMax = qtyMax;

  qty(): number {
    return this.cart[this.p.id] ?? 0;
  }

  discount(): number {
    return this.p.wasPrice && this.p.wasPrice > this.p.price ? Math.round(((this.p.wasPrice - this.p.price) * 100) / this.p.wasPrice) : 0;
  }

  stars(r: number): string {
    const n = Math.max(0, Math.min(5, Math.round(r)));
    return '★'.repeat(n) + '☆'.repeat(5 - n);
  }
}
