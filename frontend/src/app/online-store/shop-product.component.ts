import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OnlineStoreApiService, ProductDetail, ShopAd, ShopProduct } from '../core/api/online-store-api.service';
import { ShopAdComponent } from './shop-ad.component';
import { ShopMoneyPipe } from './shop-money.pipe';
import { T } from '../core/i18n/t';

/**
 * The page of one product of the shop, at /shop?product=ID: a gallery of pictures, the price and stock, the full description, the specifications,
 * the approved reviews and related products. The cart belongs to the shop, so adding to it is handed back to the shop through (change).
 */
@Component({
  selector: 'app-shop-product',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShopMoneyPipe, ShopAdComponent],
  template: `
    <div class="shop-pp" style="margin-top:0.75rem;">
      <a class="shop-pp-back" [routerLink]="[]" [queryParams]="{ product: null }" queryParamsHandling="merge" style="font-size:0.9rem;">← {{ 'Back to the shop' | translate }}</a>

      <p class="text-muted mt-3 shop-pp-loading" *ngIf="!d && !error">{{ 'Loading…' | translate }}</p>
      <div class="alert alert-warning mt-3 shop-pp-error" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="d as x">
        <div class="shop-pp-main" style="margin-top:1rem; display:grid; grid-template-columns:repeat(auto-fit,minmax(300px,1fr)); gap:1.25rem; align-items:start;">
          <div class="shop-pp-gallery">
            <div style="background:#f3f4f6; border-radius:10px; display:flex; align-items:center; justify-content:center; min-height:260px; overflow:hidden;">
              <img *ngIf="x.images.length" class="shop-pp-big" [src]="big(x.images[sel])" [alt]="x.product.name" style="width:100%; max-height:460px; object-fit:contain;" />
              <span *ngIf="!x.images.length" style="font-size:4rem;">🛍️</span>
            </div>
            <div class="flex gap-1 mt-2" *ngIf="x.images.length > 1" style="flex-wrap:wrap;">
              <button type="button" class="shop-pp-thumb" *ngFor="let u of x.images; let i = index" (click)="sel = i" [attr.aria-label]="(i + 1) + ' / ' + x.images.length"
                      [style.border]="i === sel ? '2px solid #1d4ed8' : '2px solid #e5e7eb'" style="padding:0; width:64px; height:64px; border-radius:8px; overflow:hidden; cursor:pointer; background:#fff;">
                <img [src]="thumb(u)" alt="" style="width:100%; height:100%; object-fit:cover;" />
              </button>
            </div>
          </div>

          <div class="shop-pp-info">
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="x.product.category || x.brand">{{ x.product.category }}<ng-container *ngIf="x.product.category && x.brand"> · </ng-container>{{ x.brand }}</div>
            <h2 class="shop-pp-name" style="margin:0.15rem 0 0.4rem;">{{ x.product.name }}</h2>
            <div class="shop-pp-rating" *ngIf="x.rating.count > 0" style="color:#d97706;">{{ stars(x.rating.average) }} <span class="text-muted">{{ x.rating.average }} / 5 · {{ '{p0} reviews' | translate: { p0: x.rating.count } }}</span></div>
            <div class="text-muted shop-pp-norating" *ngIf="x.rating.count === 0" style="font-size:0.85rem;">{{ 'No reviews yet' | translate }}</div>
            <div class="mt-2" style="font-size:1.6rem;"><strong class="shop-pp-price">{{ x.product.price | shopMoney: cur }}</strong><s class="text-muted shop-pp-was" *ngIf="x.product.wasPrice" style="font-size:1rem; margin-left:0.5rem;">{{ x.product.wasPrice | shopMoney: cur }}</s>
              <span *ngIf="x.product.wasPrice" style="margin-left:0.5rem; background:#dc2626; color:#fff; border-radius:4px; padding:0 0.4rem; font-size:0.75rem; vertical-align:middle;">{{ 'Offer' | translate }}</span></div>
            <div class="mt-1 shop-pp-stock" [style.color]="x.product.stock === 'IN_STOCK' ? '#15803d' : x.product.stock === 'LOW' ? '#b45309' : '#6b7280'">
              {{ x.product.stock === 'IN_STOCK' ? ('In stock' | translate) : x.product.stock === 'LOW' ? ('Few left' | translate) : ('Sold out' | translate) }}
            </div>
            <div class="mt-3" style="max-width:260px;">
              <div class="flex gap-1" *ngIf="qty(x.product) > 0; else addBtn" style="align-items:center; justify-content:space-between;">
                <button class="btn btn-outline-secondary shop-pp-minus" (click)="change.emit({ p: x.product, by: -1 })">−</button>
                <span>{{ qty(x.product) }}</span>
                <button class="btn btn-outline-secondary shop-pp-plus" (click)="change.emit({ p: x.product, by: 1 })" [disabled]="qty(x.product) >= 20">+</button>
              </div>
              <ng-template #addBtn><button class="btn btn-primary shop-pp-add" style="width:100%;" [disabled]="x.product.stock === 'SOLD_OUT' || !open" (click)="change.emit({ p: x.product, by: 1 })">{{ 'Add to cart' | translate }}</button></ng-template>
            </div>
          </div>
        </div>

        <section class="shop-pp-desc" style="margin-top:2rem;" *ngIf="x.product.description">
          <h3 style="margin:0 0 0.6rem;">{{ 'Description' | translate }}</h3>
          <p style="white-space:pre-line; margin:0;">{{ x.product.description }}</p>
        </section>

        <section class="shop-pp-specs" style="margin-top:2rem;" *ngIf="x.specs.length">
          <h3 style="margin:0 0 0.6rem;">{{ 'Specifications' | translate }}</h3>
          <table style="border-collapse:collapse; width:100%; max-width:640px;">
            <tr *ngFor="let r of x.specs" style="border-bottom:1px solid rgba(128,128,128,0.3);">
              <th style="text-align:left; padding:0.4rem 0.75rem 0.4rem 0; width:40%; font-weight:600; vertical-align:top; text-transform:none; letter-spacing:normal; font-size:inherit;">{{ r.label }}</th>
              <td style="padding:0.4rem 0;">{{ r.value }}</td>
            </tr>
          </table>
        </section>

        <app-shop-ad *ngIf="ads.length" placement="PRODUCT" [ads]="ads"></app-shop-ad>

        <section class="shop-pp-reviews" id="shop-pp-reviews" style="margin-top:2rem;">
          <h3 style="margin:0 0 0.6rem;">{{ 'Customer reviews' | translate }}</h3>
          <div *ngIf="x.rating.count > 0; else noRev" style="display:flex; gap:2rem; flex-wrap:wrap; align-items:center;">
            <div style="text-align:center;">
              <div style="font-size:2.2rem; font-weight:700;">{{ x.rating.average }}</div>
              <div style="color:#d97706;">{{ stars(x.rating.average) }}</div>
              <div class="text-muted" style="font-size:0.85rem;">{{ '{p0} reviews' | translate: { p0: x.rating.count } }}</div>
            </div>
            <div style="flex:1; min-width:200px; max-width:360px;">
              <div *ngFor="let n of [5, 4, 3, 2, 1]" class="flex gap-2" style="align-items:center; font-size:0.85rem;">
                <span style="width:2.2em;">{{ n }} ★</span>
                <div style="flex:1; height:8px; background:rgba(128,128,128,0.3); border-radius:4px; overflow:hidden;"><div style="height:100%; background:#d97706;" [style.width.%]="share(n)"></div></div>
                <span class="text-muted" style="width:2em; text-align:right;">{{ x.rating.stars[n - 1] }}</span>
              </div>
            </div>
          </div>
          <div class="mt-3" *ngIf="x.reviews.length">
            <div class="shop-pp-review" *ngFor="let r of x.reviews" style="padding:0.6rem 0; border-top:1px solid rgba(128,128,128,0.3);">
              <span style="color:#d97706;">{{ stars(r.rating) }}</span> <strong style="margin-left:0.4rem;">{{ r.reviewerName }}</strong> <span class="text-muted" style="font-size:0.8rem; margin-left:0.4rem;">{{ r.at | date: 'mediumDate' }}</span>
              <div>{{ r.text }}</div>
            </div>
          </div>
          <ng-template #noRev><p class="text-muted shop-pp-noreviews">{{ 'No reviews yet' | translate }}</p></ng-template>
          <p class="text-muted mt-2" style="font-size:0.85rem;">{{ 'Only customers who bought this product can review it, from their order page.' | translate }}</p>
        </section>

        <section class="shop-pp-related" style="margin-top:2rem;" *ngIf="x.related.length">
          <h3 style="margin:0 0 0.6rem;">{{ 'You may also like' | translate }}</h3>
          <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(170px,1fr)); gap:0.75rem;">
            <a class="card shop-pp-rel" *ngFor="let p of x.related" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="padding:0.6rem; color:inherit; text-decoration:none;">
              <img *ngIf="p.imageUrl" [src]="thumb(p.imageUrl)" [alt]="p.name" loading="lazy" style="width:100%; height:110px; object-fit:cover; border-radius:6px;" />
              <div *ngIf="!p.imageUrl" style="width:100%; height:110px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2rem;">🛍️</div>
              <div class="mt-1"><strong>{{ p.name }}</strong></div>
              <div *ngIf="p.ratingCount > 0" style="color:#d97706; font-size:0.85rem;">{{ stars(p.rating) }} <span class="text-muted">({{ p.ratingCount }})</span></div>
              <div><strong>{{ p.price | shopMoney: cur }}</strong><s class="text-muted" *ngIf="p.wasPrice" style="font-size:0.8rem; margin-left:0.3rem;">{{ p.wasPrice | shopMoney: cur }}</s></div>
            </a>
          </div>
        </section>
      </ng-container>
    </div>
  `,
})
export class ShopProductComponent implements OnChanges {
  @Input() productId = 0;
  @Input() cur = '';
  @Input() open = true;
  /** The cart of the shop: product id to quantity. */
  @Input() cart: Record<number, number> = {};
  @Input() ads: ShopAd[] = [];
  @Output() change = new EventEmitter<{ p: ShopProduct; by: number }>();

  d: ProductDetail | null = null;
  error: string | null = null;
  /** The picture shown big. */
  sel = 0;
  private seq = 0;

  constructor(private api: OnlineStoreApiService) {}

  ngOnChanges(c: SimpleChanges): void {
    if (c['productId'] && this.productId) this.load();
  }

  private load(): void {
    const mine = ++this.seq;
    this.d = null;
    this.error = null;
    this.sel = 0;
    window.scrollTo({ top: 0 });
    this.api.productDetail(this.productId).subscribe({
      next: (r) => {
        if (mine === this.seq) this.d = r;
      },
      error: (e) => {
        if (mine === this.seq) this.error = e?.status === 404 ? T('This product is not available.') : (e?.error?.message ?? T('The shop is not available right now.'));
      },
    });
  }

  qty(p: ShopProduct): number {
    return this.cart[p.id] ?? 0;
  }

  big(u: string): string {
    return this.api.imageUrl(u) + '?w=900';
  }

  thumb(u: string): string {
    return this.api.imageUrl(u) + '?w=200';
  }

  stars(r: number): string {
    const n = Math.max(0, Math.min(5, Math.round(r)));
    return '★'.repeat(n) + '☆'.repeat(5 - n);
  }

  /** Share (in percent) of the reviews that gave n stars. */
  share(n: number): number {
    const c = this.d?.rating.count ?? 0;
    return c === 0 ? 0 : Math.round(((this.d?.rating.stars[n - 1] ?? 0) * 100) / c);
  }
}
