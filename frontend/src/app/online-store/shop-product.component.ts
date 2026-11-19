import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OnlineStoreApiService, ProductDetail, ProductVariant, Shop, ShopAd, ShopProduct } from '../core/api/online-store-api.service';
import { LanguageService } from '../core/i18n/language.service';
import { recentIds, rememberProduct } from './recent';
import { ShopRailComponent } from './shop-rail.component';
import { WishlistService } from './wishlist.service';
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
  imports: [TranslatePipe, CommonModule, RouterLink, ShopMoneyPipe, ShopAdComponent, ShopRailComponent],
  template: `
    <div class="shop-pp" style="margin-top:0.75rem;">
      <a class="shop-pp-back" [routerLink]="[]" [queryParams]="{ product: null }" queryParamsHandling="merge" style="font-size:0.9rem;">← {{ 'Back to the shop' | translate }}</a>

      <p class="text-muted mt-3 shop-pp-loading" *ngIf="!d && !error">{{ 'Loading…' | translate }}</p>
      <div class="alert alert-warning mt-3 shop-pp-error" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="d as x">
        <div class="shop-pp-main" style="margin-top:1rem; display:grid; grid-template-columns:repeat(auto-fit,minmax(300px,1fr)); gap:1.25rem; align-items:start;">
          <div class="shop-pp-gallery">
            <div class="shop-pp-zoom" (mousemove)="zoomMove($event)" (mouseleave)="zoomOn = false" (click)="lightbox = true" [title]="'Click to enlarge' | translate"
                 style="background:#f3f4f6; border-radius:10px; display:flex; align-items:center; justify-content:center; min-height:260px; overflow:hidden; cursor:zoom-in;">
              <img *ngIf="gal.length" class="shop-pp-big" [src]="big(gal[sel])" [alt]="x.product.name" [style.transform]="zoomOn ? 'scale(2)' : 'none'" [style.transform-origin]="origin"
                   style="width:100%; max-height:460px; object-fit:contain; transition:transform 0.12s;" />
              <span *ngIf="!gal.length" style="font-size:4rem;">🛍️</span>
            </div>
            <div class="flex gap-1 mt-2" *ngIf="gal.length > 1" style="flex-wrap:wrap;">
              <button type="button" class="shop-pp-thumb" *ngFor="let u of gal; let i = index" (click)="sel = i" [attr.aria-label]="(i + 1) + ' / ' + gal.length"
                      [style.border]="i === sel ? '2px solid var(--shop-brand, #1d4ed8)' : '2px solid #e5e7eb'" style="padding:0; width:64px; height:64px; border-radius:8px; overflow:hidden; cursor:pointer; background:#fff;">
                <img [src]="thumb(u)" alt="" style="width:100%; height:100%; object-fit:cover;" />
              </button>
            </div>
          </div>

          <div class="shop-pp-info">
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="x.product.category || x.brand">{{ x.product.category }}<ng-container *ngIf="x.product.category && x.brand"> · </ng-container>{{ x.brand }}</div>
            <h2 class="shop-pp-name" style="margin:0.15rem 0 0.4rem;">{{ x.product.name }}</h2>
            <div class="shop-pp-rating" *ngIf="x.rating.count > 0" style="color:#d97706;">{{ stars(x.rating.average) }} <span class="text-muted">{{ x.rating.average }} / 5 · {{ '{p0} ratings' | translate: { p0: x.rating.count } }}</span></div>
            <div class="text-muted shop-pp-norating" *ngIf="x.rating.count === 0" style="font-size:0.85rem;">{{ 'No ratings yet' | translate }}</div>
            <div class="mt-2" style="font-size:1.6rem;"><strong class="shop-pp-price">{{ it.price | shopMoney: cur }}</strong><s class="text-muted shop-pp-was" *ngIf="it.wasPrice" style="font-size:1rem; margin-left:0.5rem;">{{ it.wasPrice | shopMoney: cur }}</s>
              <span *ngIf="it.wasPrice" style="margin-left:0.5rem; background:#dc2626; color:#fff; border-radius:4px; padding:0 0.4rem; font-size:0.75rem; vertical-align:middle;">{{ 'Offer' | translate }}</span></div>
            <div class="mt-1 shop-pp-stock" [style.color]="it.stock === 'IN_STOCK' ? '#15803d' : it.stock === 'LOW' ? '#b45309' : '#6b7280'">
              {{ it.stock === 'IN_STOCK' ? ('In stock' | translate) : it.stock === 'LOW' ? ('Few left' | translate) : ('Sold out' | translate) }}
            </div>
            <div class="shop-pp-options mt-3" *ngIf="x.options?.length && x.variants?.length">
              <div class="shop-pp-opt" *ngFor="let o of x.options; let oi = index" style="margin-bottom:0.75rem;">
                <div style="font-size:0.9rem;"><strong>{{ o.name }}</strong>: <span class="shop-pp-opt-now">{{ chosen[oi] }}</span></div>
                <div class="flex gap-1 mt-1" style="flex-wrap:wrap;">
                  <button type="button" class="shop-pp-val" *ngFor="let v of o.values" (click)="choose(oi, v)" [attr.aria-pressed]="chosen[oi] === v"
                          [attr.data-state]="valueState(oi, v)" [title]="valueState(oi, v) === 'out' ? ('Out of stock' | translate) : ''"
                          [style.border]="chosen[oi] === v ? '2px solid var(--shop-brand, #1d4ed8)' : '1px solid #d1d5db'"
                          [style.opacity]="valueState(oi, v) === 'out' ? 0.5 : 1" [style.text-decoration]="valueState(oi, v) === 'out' ? 'line-through' : 'none'"
                          [style.background]="valueState(oi, v) === 'out' ? '#f3f4f6' : '#fff'" style="min-width:2.6rem; padding:0.35rem 0.8rem; border-radius:8px; cursor:pointer; color:#111827;">{{ v }}</button>
                </div>
              </div>
              <div class="text-muted shop-pp-sku" *ngIf="curVariant?.sku" style="font-size:0.8rem;">{{ 'SKU' | translate }}: {{ curVariant?.sku }}</div>
            </div>
            <div class="mt-3" style="max-width:320px;">
              <div class="flex gap-1" *ngIf="qty(it) > 0; else addBtn" style="align-items:center; justify-content:space-between;">
                <button class="btn btn-outline-secondary shop-pp-minus" (click)="change.emit({ p: it, by: -1 })">−</button>
                <span>{{ qty(it) }} {{ 'in your cart' | translate }}</span>
                <button class="btn btn-outline-secondary shop-pp-plus" (click)="change.emit({ p: it, by: 1 })" [disabled]="qty(it) >= 20">+</button>
              </div>
              <ng-template #addBtn>
                <div class="flex gap-2" style="align-items:center;">
                  <div class="flex gap-1 shop-pp-qty" style="align-items:center;">
                    <button type="button" class="btn btn-outline-secondary shop-pp-qminus" [disabled]="pick <= 1" [attr.aria-label]="'Less' | translate" (click)="pick = pick - 1">−</button>
                    <span class="shop-pp-qnum" style="min-width:1.8em; text-align:center;">{{ pick }}</span>
                    <button type="button" class="btn btn-outline-secondary shop-pp-qplus" [disabled]="pick >= 20" [attr.aria-label]="'More' | translate" (click)="pick = pick + 1">+</button>
                  </div>
                  <button class="btn btn-primary shop-pp-add" style="flex:1;" [disabled]="it.stock === 'SOLD_OUT' || !open" (click)="change.emit({ p: it, by: pick }); pick = 1">{{ it.stock === 'SOLD_OUT' && curVariant ? ('Out of stock' | translate) : ('Add to cart' | translate) }}</button>
                </div>
              </ng-template>
              <button type="button" class="btn btn-outline-secondary shop-pp-wish mt-2" style="width:100%;" [attr.aria-pressed]="wish.has(x.product.id)" (click)="wish.toggle(x.product.id)"><span style="color:#dc2626; margin-right:0.4rem;">{{ wish.has(x.product.id) ? '♥' : '♡' }}</span>{{ (wish.has(x.product.id) ? 'In my wishlist' : 'Add to my wishlist') | translate }}</button>
              <a class="btn btn-outline-secondary shop-pp-wa mt-2" [href]="waLink(it)" target="_blank" rel="noopener" style="width:100%;"><svg class="shop-pp-wa-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" style="fill:#25D366; vertical-align:-4px; margin-right:0.4rem;"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>{{ 'Share on WhatsApp' | translate }}</a>
            </div>

            <div class="shop-pp-delivery mt-3" *ngIf="shop && (shop.delivery || shop.pickup)" style="font-size:0.9rem; border:1px solid rgba(128,128,128,0.3); border-radius:8px; padding:0.6rem 0.75rem;">
              <strong>{{ 'Delivery' | translate }}</strong>
              <div *ngIf="etaText() as eta" class="shop-pp-eta">🚚 {{ 'Estimated delivery' | translate }}: {{ eta }}</div>
              <div *ngIf="shop.delivery && fromFee() !== null" class="shop-pp-fee">{{ 'Delivery from {p0}' | translate: { p0: (fromFee() | shopMoney: cur) } }}<ng-container *ngIf="shop.freeDeliveryOver"> · {{ 'free over {p0}' | translate: { p0: (shop.freeDeliveryOver | shopMoney: cur) } }}</ng-container></div>
              <div *ngIf="shop.delivery && fromFee() === null">{{ 'Delivery available' | translate }}</div>
              <div *ngIf="shop.pickup" class="shop-pp-pickup">🏬 {{ 'Pick up in store' | translate }}<ng-container *ngIf="shop.pickupAddress">: {{ shop.pickupAddress }}</ng-container></div>
              <div class="text-muted" *ngIf="etaText()" style="font-size:0.78rem;">{{ 'The date counts from today, if the shop accepts your order today.' | translate }}</div>
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

        <section class="shop-pp-bought" style="margin-top:2rem;" *ngIf="x.bought.length">
          <h3 style="margin:0 0 0.6rem;">{{ 'Frequently bought together' | translate }}</h3>
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:stretch;">
            <ng-container *ngFor="let p of boughtAll(x); let i = index">
              <span *ngIf="i > 0" style="align-self:center; font-size:1.4rem;">+</span>
              <a class="card shop-pp-bt" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="padding:0.5rem; width:150px; color:inherit; text-decoration:none;">
                <img *ngIf="p.imageUrl" [src]="thumb(p.imageUrl)" [alt]="p.name" loading="lazy" style="width:100%; height:90px; object-fit:cover; border-radius:6px;" />
                <div *ngIf="!p.imageUrl" style="width:100%; height:90px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:1.8rem;">🛍️</div>
                <div style="font-size:0.85rem; line-height:1.25; margin-top:0.25rem; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;">{{ p.name }}</div>
                <strong style="font-size:0.9rem;">{{ p.price | shopMoney: cur }}</strong>
              </a>
            </ng-container>
          </div>
          <div class="mt-2"><strong class="shop-pp-bt-total">{{ boughtTotal(x) | shopMoney: cur }}</strong>
            <button type="button" class="btn btn-primary shop-pp-bt-add" style="margin-left:0.75rem;" [disabled]="!open" (click)="addAll(x)">{{ 'Add all to cart' | translate }}</button></div>
        </section>

        <app-shop-ad *ngIf="ads.length" placement="PRODUCT" [ads]="ads"></app-shop-ad>

        <section class="shop-pp-reviews" id="shop-pp-reviews" style="margin-top:2rem;">
          <h3 style="margin:0 0 0.6rem;">{{ 'Customer ratings and reviews' | translate }}</h3>
          <div *ngIf="x.rating.count > 0; else noRev" style="display:flex; gap:2rem; flex-wrap:wrap; align-items:center;">
            <div style="text-align:center;">
              <div style="font-size:2.2rem; font-weight:700;">{{ x.rating.average }}</div>
              <div style="color:#d97706;">{{ stars(x.rating.average) }}</div>
              <div class="text-muted" style="font-size:0.85rem;">{{ '{p0} ratings' | translate: { p0: x.rating.count } }}</div>
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
            <h4 class="shop-pp-reviews-title" style="margin:0 0 0.3rem;">{{ 'Reviews' | translate }}</h4>
            <div class="shop-pp-review" *ngFor="let r of x.reviews" style="padding:0.6rem 0; border-top:1px solid rgba(128,128,128,0.3);">
              <span style="color:#d97706;">{{ stars(r.rating) }}</span> <strong style="margin-left:0.4rem;">{{ r.reviewerName }}</strong> <span class="text-muted" style="font-size:0.8rem; margin-left:0.4rem;">{{ r.at | date: 'mediumDate' }}</span><span class="text-muted shop-pp-rv-variant" *ngIf="x.options?.length" style="font-size:0.8rem; margin-left:0.4rem;">· {{ variantOf(r.productName) }}</span>
              <div>{{ r.text }}</div>
            </div>
          </div>
          <ng-template #noRev><p class="text-muted shop-pp-noreviews">{{ 'No ratings yet' | translate }}</p></ng-template>
          <p class="text-muted mt-2" style="font-size:0.85rem;">{{ 'Only customers who bought this product can rate or review it, from their order page.' | translate }}</p>
        </section>

        <section class="shop-pp-related" style="margin-top:2rem;" *ngIf="x.related.length">
          <h3 style="margin:0 0 0.6rem;">{{ 'You may also like' | translate }}</h3>
          <app-shop-rail style="--rail-item:180px;" [auto]="!!shop?.page?.autoSlideRows" [count]="x.related.length">
            <div *ngFor="let p of x.related"><a class="card shop-pp-rel" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="padding:0.6rem; color:inherit; text-decoration:none; flex:1 1 auto; min-width:0; display:flex; flex-direction:column;">
              <img *ngIf="p.imageUrl" [src]="thumb(p.imageUrl)" [alt]="p.name" loading="lazy" style="width:100%; height:110px; object-fit:cover; border-radius:6px;" />
              <div *ngIf="!p.imageUrl" style="width:100%; height:110px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2rem;">🛍️</div>
              <div class="mt-1"><strong>{{ p.name }}</strong></div>
              <div *ngIf="p.ratingCount > 0" style="color:#d97706; font-size:0.85rem;">{{ stars(p.rating) }} <span class="text-muted">({{ p.ratingCount }})</span></div>
              <div><strong>{{ p.price | shopMoney: cur }}</strong><s class="text-muted" *ngIf="p.wasPrice" style="font-size:0.8rem; margin-left:0.3rem;">{{ p.wasPrice | shopMoney: cur }}</s></div>
            </a></div>
          </app-shop-rail>
        </section>

        <section class="shop-pp-recent" style="margin-top:2rem;" *ngIf="recent.length">
          <h3 style="margin:0 0 0.6rem;">{{ 'Recently viewed' | translate }}</h3>
          <app-shop-rail style="--rail-item:180px;" [auto]="!!shop?.page?.autoSlideRows" [count]="recent.length">
            <div *ngFor="let p of recent"><a class="card shop-pp-rv" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="padding:0.6rem; color:inherit; text-decoration:none; flex:1 1 auto; min-width:0; display:flex; flex-direction:column;">
              <img *ngIf="p.imageUrl" [src]="thumb(p.imageUrl)" [alt]="p.name" loading="lazy" style="width:100%; height:110px; object-fit:cover; border-radius:6px;" />
              <div *ngIf="!p.imageUrl" style="width:100%; height:110px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2rem;">🛍️</div>
              <div class="mt-1"><strong>{{ p.name }}</strong></div>
              <div><strong>{{ p.price | shopMoney: cur }}</strong></div>
            </a></div>
          </app-shop-rail>
        </section>
      </ng-container>

      <div class="shop-pp-lightbox" *ngIf="lightbox && d && gal.length" (click)="lightbox = false" style="position:fixed; inset:0; background:rgba(0,0,0,0.88); z-index:70; display:flex; align-items:center; justify-content:center; padding:1rem;">
        <button type="button" class="shop-pp-lb-prev" *ngIf="gal.length > 1" (click)="$event.stopPropagation(); step(-1)" [attr.aria-label]="'Previous picture' | translate" style="position:absolute; left:0.75rem; top:50%; width:44px; height:44px; border-radius:50%; border:0; font-size:1.4rem; cursor:pointer;">‹</button>
        <img [src]="huge(gal[sel])" [alt]="d.product.name" (click)="$event.stopPropagation()" style="max-width:100%; max-height:100%; object-fit:contain;" />
        <button type="button" class="shop-pp-lb-next" *ngIf="gal.length > 1" (click)="$event.stopPropagation(); step(1)" [attr.aria-label]="'Next picture' | translate" style="position:absolute; right:0.75rem; top:50%; width:44px; height:44px; border-radius:50%; border:0; font-size:1.4rem; cursor:pointer;">›</button>
        <button type="button" class="shop-pp-lb-close" (click)="lightbox = false" [attr.aria-label]="'Close' | translate" style="position:absolute; right:0.9rem; top:0.6rem; border:0; background:none; color:#fff; font-size:2rem; cursor:pointer;">×</button>
      </div>
    </div>
  `,
})
export class ShopProductComponent implements OnChanges {
  @Input() productId = 0;
  /** The shop itself (delivery and pick-up details). */
  @Input() shop: Shop | null = null;
  @Input() cur = '';
  @Input() open = true;
  /** The cart of the shop: product id to quantity. */
  @Input() cart: Record<number, number> = {};
  @Input() ads: ShopAd[] = [];
  @Output() change = new EventEmitter<{ p: ShopProduct; by: number }>();

  d: ProductDetail | null = null;
  /** What the customer is looking at and may buy: the product, or the option chosen (its own id, name, price and stock). */
  it!: ShopProduct;
  /** The choice made for each option (Colour, Size ...), in the order of the options. */
  chosen: string[] = [];
  curVariant: ProductVariant | null = null;
  /** The pictures shown: those of the chosen option first, then the product's. */
  gal: string[] = [];
  error: string | null = null;
  /** The picture shown big. */
  sel = 0;
  private seq = 0;

  /** How many the visitor is about to add. */
  pick = 1;
  zoomOn = false;
  origin = '50% 50%';
  lightbox = false;
  recent: ShopProduct[] = [];
  constructor(private api: OnlineStoreApiService, private lang: LanguageService, public wish: WishlistService) {}

  @HostListener('document:keydown.escape')
  esc(): void {
    this.lightbox = false;
  }

  ngOnChanges(c: SimpleChanges): void {
    if (c['productId'] && this.productId) this.load();
  }

  private load(): void {
    const mine = ++this.seq;
    this.d = null;
    this.gal = [];
    this.error = null;
    this.sel = 0;
    this.pick = 1;
    this.lightbox = false;
    this.recent = [];
    window.scrollTo({ top: 0 });
    this.api.productDetail(this.productId).subscribe({
      next: (r) => {
        if (mine !== this.seq) return;
        this.d = r;
        this.chosen = [];
        const first = r.variants?.find((v) => v.id === r.selectedVariantId) ?? r.variants?.[0];
        if (r.options?.length && first) this.chosen = [...first.values];
        this.refresh();
        const before = recentIds().filter((i) => i !== this.productId);
        rememberProduct(this.productId);
        if (before.length) this.api.recent(before.slice(0, 8)).subscribe({ next: (list) => { if (mine === this.seq) { this.recent = list; } }, error: () => {} });
      },
      error: (e) => {
        if (mine === this.seq) this.error = e?.status === 404 ? T('This product is not available.') : (e?.error?.message ?? T('The shop is not available right now.'));
      },
    });
  }

  /** The option that matches the choices made, the item to buy and the pictures to show. */
  private refresh(): void {
    const x = this.d;
    if (!x) return;
    this.curVariant = x.options?.length ? (x.variants ?? []).find((v) => v.values.every((val, i) => val === this.chosen[i])) ?? null : null;
    const v = this.curVariant;
    this.it = v ? { ...x.product, id: v.id, name: x.product.name + ' - ' + v.label, price: v.price, wasPrice: v.wasPrice, stock: v.stock, variants: 0, priceFrom: false } : x.product;
    this.gal = v && v.pictures.length ? [...v.pictures, ...x.images.filter((u) => !v.pictures.includes(u))] : x.images;
    this.sel = 0;
  }

  /** ok = can be bought with the other choices made; out = sold out; none = this combination is not sold. */
  valueState(oi: number, value: string): 'ok' | 'out' | 'none' {
    const m = (this.d?.variants ?? []).find((v) => v.values[oi] === value && v.values.every((val, j) => j === oi || val === this.chosen[j]));
    return !m ? 'none' : m.stock === 'SOLD_OUT' ? 'out' : 'ok';
  }

  /** The customer picked a value. When that combination is not sold, the closest one is taken (one that is in stock first). */
  choose(oi: number, value: string): void {
    const all = (this.d?.variants ?? []).filter((v) => v.values[oi] === value);
    if (!all.length) return;
    const exact = all.find((v) => v.values.every((val, j) => j === oi || val === this.chosen[j]));
    const pick = exact ?? all.find((v) => v.stock !== 'SOLD_OUT') ?? all[0];
    this.chosen = [...pick.values];
    this.pick = 1;
    this.refresh();
  }

  /** "Red / M" out of the review's product name "Classic Polo Shirt - Red / M". */
  variantOf(name: string): string {
    const i = name.lastIndexOf(' - ');
    return i >= 0 ? name.substring(i + 3) : name;
  }

  qty(p: ShopProduct): number {
    return this.cart[p.id] ?? 0;
  }

  big(u: string): string {
    return this.api.imageUrl(u) + '?w=1200';
  }

  huge(u: string): string {
    return this.api.imageUrl(u) + '?w=1600';
  }

  zoomMove(e: MouseEvent): void {
    const el = e.currentTarget as HTMLElement;
    const r = el.getBoundingClientRect();
    this.origin = `${((e.clientX - r.left) * 100) / r.width}% ${((e.clientY - r.top) * 100) / r.height}%`;
    this.zoomOn = true;
  }

  step(by: number): void {
    const n = this.gal.length;
    if (n > 1) this.sel = (this.sel + by + n) % n;
  }

  waLink(p: ShopProduct): string {
    return 'https://wa.me/?text=' + encodeURIComponent(`${p.name} - ${window.location.href}`);
  }

  /** The cheapest delivery fee of the shop's zones, or null when it has no zone. */
  fromFee(): number | null {
    const z = this.shop?.zones ?? [];
    return z.length ? Math.min(...z.map((x) => x.fee)) : null;
  }

  /** "Thu 26 Nov - Sat 28 Nov" from the delivery days staff set, counted from today; empty when staff set none. */
  etaText(): string {
    const page = this.shop?.page;
    if (!this.shop?.delivery || page?.deliveryDaysMin === null || page?.deliveryDaysMin === undefined || page.deliveryDaysMax === null) return '';
    const fmt = (days: number): string => {
      const d = new Date();
      d.setDate(d.getDate() + days);
      return d.toLocaleDateString(this.lang.current === 'fr' ? 'fr-FR' : 'en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
    };
    const a = fmt(page.deliveryDaysMin);
    const b = fmt(page.deliveryDaysMax);
    return a === b ? a : `${a} – ${b}`;
  }

  /** This product and the ones often bought with it, in the order they are shown. */
  boughtAll(x: ProductDetail): ShopProduct[] {
    return [x.product, ...x.bought];
  }

  boughtTotal(x: ProductDetail): number {
    return this.boughtAll(x).reduce((sum, p) => sum + p.price, 0);
  }

  /** Puts this product (when not in the cart yet) and the others that are not sold out into the cart. */
  addAll(x: ProductDetail): void {
    for (const p of this.boughtAll(x)) {
      if (p.stock !== 'SOLD_OUT' && this.qty(p) === 0) this.change.emit({ p, by: 1 });
    }
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
