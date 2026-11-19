import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountProfile, CouponCheckResult, OnlineStoreApiService, Reorder, Shop, ShopAddress, ShopAd, ShopFilter, ShopSuggest, ShopHome, ShopPayProvider, ShopPage, ShopProduct, shopToken } from '../core/api/online-store-api.service';
import { PublicSettingsApiService } from '../core/api/public-settings-api.service';
import { ShopAccountComponent } from './shop-account.component';
import { PayProviderPickerComponent } from './pay-provider-picker.component';
import { ShopAdComponent } from './shop-ad.component';
import { ShopProductComponent } from './shop-product.component';
import { ShopRailComponent } from './shop-rail.component';
import { ShopQuickViewComponent } from './shop-quick-view.component';
import { recentIds } from './recent';
import { WishlistService } from './wishlist.service';
import { PwaService } from '../core/pwa.service';
import { ShopMoneyPipe } from './shop-money.pipe';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

/**
 * The public shop at /shop (no login). A visitor browses what the shop sells, fills a cart, and sends an order that waits for staff to accept it.
 * Prices and stock come from the server and are checked again when the order is sent, so nothing here can change what is charged.
 */
@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShopAccountComponent, ShopMoneyPipe, PayProviderPickerComponent, ShopAdComponent, ShopProductComponent, ShopQuickViewComponent, ShopRailComponent],
  host: { '[class.shop-branded]': '!!brand', '[style.--shop-brand]': 'brand' },
  template: `
    <div class="shop-announce" *ngIf="shop?.page?.announcementText" [style.background]="brand || '#1d4ed8'" style="color:#fff; text-align:center; padding:0.45rem 0.75rem; font-size:0.9rem;">
      <a *ngIf="shop?.page?.announcementLink; else announceText" class="shop-announce-link" style="color:#fff; text-decoration:underline; cursor:pointer;" (click)="openLink(shop?.page?.announcementLink)">{{ shop?.page?.announcementText }}</a>
      <ng-template #announceText>{{ shop?.page?.announcementText }}</ng-template>
    </div>
    <div class="shop-header" *ngIf="shop" style="background:#fff; border-bottom:1px solid #e5e7eb; position:sticky; top:0; z-index:20;">
      <div style="max-width:1100px; margin:0 auto; padding:0.6rem 0.75rem; display:flex; align-items:center; gap:0.75rem; flex-wrap:wrap;">
        <a class="shop-brand" (click)="goHome()" style="display:flex; align-items:center; gap:0.5rem; cursor:pointer; color:inherit; text-decoration:none;">
          <img *ngIf="logo" [src]="logo" alt="" class="shop-logo" style="height:36px; max-width:120px; object-fit:contain;" />
          <strong class="shop-name" style="font-size:1.2rem;">{{ shop.name }}</strong>
        </a>
        <div *ngIf="step === 'shop' && !showAccount" style="position:relative; flex:1; min-width:150px; max-width:420px;">
          <input class="form-control shop-search" style="width:100%;" [placeholder]="'Search by name, SKU, brand or category' | translate" [ngModel]="q" (ngModelChange)="searchChanged($event)" (keydown.enter)="sugg = null" (keydown.escape)="sugg = null" (blur)="blurSuggest()" (focus)="suggest()" autocomplete="off" name="q" />
          <div class="shop-sugg card" *ngIf="sugg && hasSuggestions()" style="position:absolute; left:0; right:0; top:100%; margin-top:2px; z-index:40; padding:0.3rem; max-height:70vh; overflow:auto; box-shadow:0 6px 18px rgba(0,0,0,0.18);">
            <a class="shop-sugg-item shop-sugg-product" *ngFor="let p of sugg.products" (mousedown)="openSuggested(p)" style="display:flex; gap:0.5rem; align-items:center; padding:0.3rem 0.4rem; cursor:pointer; color:inherit; text-decoration:none; border-radius:6px;">
              <img *ngIf="p.imageUrl" [src]="thumb(p)" alt="" style="width:36px; height:36px; object-fit:cover; border-radius:4px;" />
              <span *ngIf="!p.imageUrl" style="width:36px; text-align:center;">🛍️</span>
              <span style="flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">{{ p.name }}</span>
              <strong style="font-size:0.85rem;">{{ p.price | shopMoney: cur }}</strong>
            </a>
            <a class="shop-sugg-item shop-sugg-category" *ngFor="let c of sugg.categories" (mousedown)="suggestedCategory(c)" style="display:block; padding:0.3rem 0.4rem; cursor:pointer; color:inherit; text-decoration:none;">{{ 'In category' | translate }}: <strong>{{ c }}</strong></a>
            <a class="shop-sugg-item shop-sugg-brand" *ngFor="let b of sugg.brands" (mousedown)="suggestedBrand(b)" style="display:block; padding:0.3rem 0.4rem; cursor:pointer; color:inherit; text-decoration:none;">{{ 'Brand' | translate }}: <strong>{{ b }}</strong></a>
          </div>
        </div>
        <div style="margin-left:auto; display:flex; gap:0.5rem; align-items:center; flex-wrap:wrap;">
          <button *ngIf="step === 'shop' && !showAccount" class="btn btn-sm shop-wish-open" [class.btn-primary]="showWish" [class.btn-outline-secondary]="!showWish" [attr.aria-label]="'My wishlist' | translate" (click)="openWishlist()"><span [style.color]="showWish ? '#fff' : '#dc2626'">♥</span> {{ wish.count }}</button>
          <button *ngIf="shop.accounts" class="btn btn-sm shop-account-btn" [class.btn-primary]="showAccount" [class.btn-outline-secondary]="!showAccount" (click)="showAccount = !showAccount">👤 {{ account ? (account.name || account.email || account.phone) : ('Sign in' | translate) }}</button>
          <button *ngIf="count() > 0 && step === 'shop' && !showAccount" class="btn btn-sm btn-outline-secondary shop-cart-btn" (click)="checkout()">🛒 {{ count() }}</button>
          <button class="btn btn-sm" [class.btn-primary]="lang.current === 'en'" [class.btn-outline-secondary]="lang.current !== 'en'" (click)="lang.choose('en')">EN</button>
          <button class="btn btn-sm" [class.btn-primary]="lang.current === 'fr'" [class.btn-outline-secondary]="lang.current !== 'fr'" (click)="lang.choose('fr')">FR</button>
          <a class="btn btn-sm btn-outline-secondary shop-staff-login" routerLink="/login">{{ 'Staff Login' | translate }}</a>
        </div>
      </div>
    </div>
    <div class="shop-page" style="max-width:1100px; margin:0 auto; padding:1rem 0.75rem 2rem;">
      <div class="alert alert-danger shop-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!shop && !error" class="text-muted">{{ 'Loading the shop…' | translate }}</div>

      <ng-container *ngIf="shop">
        <div class="alert alert-warning shop-paused mt-2" *ngIf="!shop.open">{{ shop.message || ('We are not taking orders right now.' | translate) }}</div>

        <div class="alert alert-warning shop-reorder-note mt-2" *ngIf="reorderNote">{{ reorderNote }}</div>
        <app-shop-account *ngIf="showAccount && shop.accounts" [profile]="account" [currency]="cur" [textChannel]="shop.textChannel" [loyalty]="shop.loyalty" [addresses]="addresses" [zones]="shop.zones" (addressesChange)="addresses = $event" (profileChange)="accountChanged($event)" (reordered)="reordered($event)" (closed)="showAccount = false"></app-shop-account>

        <section class="shop-wishlist" *ngIf="step === 'shop' && !showAccount && !viewId && showWish" style="margin-top:1rem;">
          <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem; margin-bottom:0.75rem;">
            <h3 style="margin:0;"><span style="color:#dc2626;">♥</span> {{ 'My wishlist' | translate }}</h3>
            <button class="btn btn-sm btn-outline-secondary shop-wish-close" (click)="showWish = false">{{ 'Back to the shop' | translate }}</button>
          </div>
          <p class="text-muted shop-wish-empty" *ngIf="wishShown.length === 0 && !wishBusy">{{ 'Your wishlist is empty. Tap the heart on a product to keep it here.' | translate }}</p>
          <p class="text-muted shop-wish-hint" *ngIf="!account && shop.accounts && wishShown.length > 0" style="font-size:0.85rem;">{{ 'It is kept in this browser. Sign in (👤 above) to keep your wishlist on every device.' | translate }}</p>
          <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
            <ng-container *ngFor="let p of wishShown"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></ng-container>
          </div>
        </section>

        <app-shop-product *ngIf="step === 'shop' && !showAccount && viewId" [productId]="viewId" [shop]="shop" [cur]="cur" [open]="shop.open" [cart]="cart" [ads]="adsFor('PRODUCT')" (change)="change($event.p, $event.by)"></app-shop-product>

        <ng-container *ngIf="step === 'shop' && !showAccount && !viewId && !showWish">
        <ng-container *ngIf="landing">
          <section class="shop-hero" style="margin-top:1rem; border-radius:14px; color:#fff; background-color:#1f2937; position:relative; overflow:hidden; min-height:300px; display:flex; align-items:center;"
                   (mouseenter)="heroHold = true" (mouseleave)="heroHold = false" (touchstart)="heroTouchStart($event)" (touchend)="heroTouchEnd($event)">
            <div class="shop-hero-track" *ngIf="bannerUrls.length" [style.transform]="'translateX(-' + (heroIndex * 100) + '%)'"
                 style="position:absolute; inset:0; display:flex; transition:transform 0.8s ease-in-out;">
              <div class="shop-hero-slide" *ngFor="let u of bannerUrls" style="flex:0 0 100%; height:100%; background-size:cover; background-position:center;" [style.background-image]="slide(u)"></div>
            </div>
            <div style="position:absolute; inset:0; background:rgba(17,24,39,0.5);"></div>
            <div style="position:relative; max-width:600px; padding:2.5rem 3rem 3rem;">
              <h1 class="shop-hero-title" style="margin:0 0 0.5rem; font-size:2rem; line-height:1.2;">{{ heroTitle }}</h1>
              <p class="shop-hero-sub" *ngIf="heroSubtitle" style="margin:0 0 1rem; font-size:1.05rem;">{{ heroSubtitle }}</p>
              <button class="btn btn-primary shop-hero-cta" style="font-size:1.05rem; padding:0.6rem 1.4rem;" (click)="cta()">{{ heroButton }}</button>
            </div>
            <ng-container *ngIf="bannerUrls.length > 1">
              <button type="button" class="shop-hero-prev" [attr.aria-label]="'Previous picture' | translate" (click)="heroGo(-1)" style="position:absolute; left:0.5rem; top:50%; transform:translateY(-50%); width:36px; height:36px; border-radius:50%; border:0; background:rgba(255,255,255,0.85); color:#111827; font-size:1.3rem; cursor:pointer; line-height:1;">‹</button>
              <button type="button" class="shop-hero-next" [attr.aria-label]="'Next picture' | translate" (click)="heroGo(1)" style="position:absolute; right:0.5rem; top:50%; transform:translateY(-50%); width:36px; height:36px; border-radius:50%; border:0; background:rgba(255,255,255,0.85); color:#111827; font-size:1.3rem; cursor:pointer; line-height:1;">›</button>
              <div style="position:absolute; left:0; right:0; bottom:0.6rem; display:flex; justify-content:center; gap:0.4rem;">
                <button type="button" class="shop-hero-dot" *ngFor="let u of bannerUrls; let i = index" [attr.aria-label]="(i + 1) + ' / ' + bannerUrls.length" (click)="heroSet(i)"
                        [style.background]="i === heroIndex ? '#ffffff' : 'rgba(255,255,255,0.45)'" style="width:10px; height:10px; border-radius:50%; border:0; padding:0; cursor:pointer;"></button>
              </div>
            </ng-container>
          </section>

          <app-shop-ad *ngIf="adsFor('HOME_TOP').length" placement="HOME_TOP" [ads]="adsFor('HOME_TOP')"></app-shop-ad>

          <section class="shop-tiles" *ngIf="shop.categoryTiles?.length" style="margin-top:2rem;">
            <h3 style="margin:0 0 0.75rem;">{{ 'Shop by category' | translate }}</h3>
            <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(130px,1fr)); gap:0.9rem;">
              <button type="button" class="shop-tile" *ngFor="let t of shop.categoryTiles" (click)="tile(t.name)" style="border:0; padding:0; background:none; cursor:pointer; text-align:center; color:inherit;">
                <div style="aspect-ratio:1/1; border-radius:14px; overflow:hidden; background:#f3f4f6; display:flex; align-items:center; justify-content:center; box-shadow:0 1px 4px rgba(0,0,0,0.12);">
                  <img *ngIf="t.imageUrl" [src]="tileImg(t.imageUrl)" [alt]="t.name" loading="lazy" style="width:100%; height:100%; object-fit:cover;" />
                  <span *ngIf="!t.imageUrl" style="font-size:2.4rem;">🛍️</span>
                </div>
                <div class="mt-1"><strong>{{ t.name }}</strong></div>
                <div class="text-muted" style="font-size:0.8rem;">{{ '{p0} products' | translate: { p0: t.count } }}</div>
              </button>
            </div>
          </section>

          <!-- Front page order: New arrivals, All products, Featured products, Best-selling (then special offers). -->
          <ng-container *ngFor="let r of rowsBefore; trackBy: trackRow"><ng-container *ngTemplateOutlet="rowTpl; context: { $implicit: r }"></ng-container></ng-container>
          <h3 id="shop-all" style="margin:2rem 0 0;">{{ 'All products' | translate }}</h3>
        </ng-container>

        <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:center;">
          <button class="btn btn-sm" [class.btn-primary]="category === ''" [class.btn-outline-secondary]="category !== ''" (click)="pickCategory('')">{{ 'All' | translate }}</button>
          <button *ngFor="let c of shop.categories" class="btn btn-sm" [class.btn-primary]="category === c" [class.btn-outline-secondary]="category !== c" (click)="pickCategory(c)">{{ c }}</button>
        </div>

        <div class="shop-filters flex gap-2 mt-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div><label class="form-label" style="font-size:0.78rem; margin:0;">{{ 'Sort by' | translate }}</label>
            <select class="form-control shop-sort" name="fsort" [(ngModel)]="f.sort" (ngModelChange)="filtersChanged()">
              <option value="">{{ 'Recommended' | translate }}</option>
              <option value="newest">{{ 'Newest' | translate }}</option>
              <option value="popular">{{ 'Most popular' | translate }}</option>
              <option value="rating">{{ 'Best rated' | translate }}</option>
              <option value="price_asc">{{ 'Price: low to high' | translate }}</option>
              <option value="price_desc">{{ 'Price: high to low' | translate }}</option>
            </select></div>
          <div><label class="form-label" style="font-size:0.78rem; margin:0;">{{ 'Price' | translate }}</label>
            <div class="flex gap-1" style="align-items:center;">
              <input class="form-control shop-price-min" type="number" min="0" style="width:90px;" name="fmin" [placeholder]="'Min' | translate" [(ngModel)]="f.minPrice" (ngModelChange)="filtersChanged(true)" />
              <span>–</span>
              <input class="form-control shop-price-max" type="number" min="0" style="width:90px;" name="fmax" [placeholder]="'Max' | translate" [(ngModel)]="f.maxPrice" (ngModelChange)="filtersChanged(true)" />
            </div></div>
          <div *ngIf="shop.brands?.length"><label class="form-label" style="font-size:0.78rem; margin:0;">{{ 'Brand' | translate }}</label>
            <select class="form-control shop-filter-brand" name="fbrand" [(ngModel)]="f.brand" (ngModelChange)="filtersChanged()">
              <option value="">{{ 'All brands' | translate }}</option>
              <option *ngFor="let b of shop.brands" [value]="b">{{ b }}</option>
            </select></div>
          <div><label class="form-label" style="font-size:0.78rem; margin:0;">{{ 'Rating' | translate }}</label>
            <select class="form-control shop-rating-filter" name="frating" [(ngModel)]="f.minRating" (ngModelChange)="filtersChanged()">
              <option [ngValue]="0">{{ 'Any rating' | translate }}</option>
              <option [ngValue]="4">4 ★ {{ 'and up' | translate }}</option>
              <option [ngValue]="3">3 ★ {{ 'and up' | translate }}</option>
              <option [ngValue]="2">2 ★ {{ 'and up' | translate }}</option>
            </select></div>
          <label style="display:flex; align-items:center; gap:0.35rem; padding-bottom:0.45rem;"><input type="checkbox" class="shop-instock" name="fstock" [(ngModel)]="f.inStock" (ngModelChange)="filtersChanged()" /> {{ 'In stock only' | translate }}</label>
          <button type="button" class="btn btn-sm btn-outline-secondary shop-clear" *ngIf="filtersActive || f.sort" (click)="clearFilters()">{{ 'Clear filters' | translate }}</button>
        </div>

        <app-shop-ad *ngIf="adsFor('PRODUCT').length" placement="PRODUCT" [ads]="adsFor('PRODUCT')"></app-shop-ad>
        <div id="shop-top"></div>
        <div class="shop-grid mt-3" [style.opacity]="loading ? 0.5 : 1" style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
          <ng-container *ngFor="let p of items"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></ng-container>
        </div>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && !q.trim() && !category && !filtersActive">{{ 'Nothing is for sale yet. Please come back soon.' | translate }}</p>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && (q.trim() || category || filtersActive)">{{ 'No products match.' | translate }}</p>

        <nav class="shop-pager flex justify-between align-center mt-3" *ngIf="totalPages > 1" style="flex-wrap:wrap; gap:0.5rem;">
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Showing {p0}–{p1} of {p2}' | translate: { p0: firstShown(), p1: lastShown(), p2: total } }}</span>
          <div class="flex gap-1" style="align-items:center;">
            <button class="btn btn-sm btn-outline-secondary shop-prev" [disabled]="page === 0 || loading" (click)="go(page - 1)">{{ 'Previous' | translate }}</button>
            <span style="font-size:0.85rem;">{{ 'Page {p0} of {p1}' | translate: { p0: page + 1, p1: totalPages } }}</span>
            <button class="btn btn-sm btn-outline-secondary shop-next" [disabled]="page + 1 >= totalPages || loading" (click)="go(page + 1)">{{ 'Next' | translate }}</button>
          </div>
        </nav>

        <ng-container *ngIf="landing">
          <ng-container *ngFor="let r of rowsAfter; trackBy: trackRow"><ng-container *ngTemplateOutlet="rowTpl; context: { $implicit: r }"></ng-container></ng-container>
          <ng-container *ngIf="recent.length"><ng-container *ngTemplateOutlet="rowTpl; context: { $implicit: recentRow }"></ng-container></ng-container>
        </ng-container>

        <ng-container *ngIf="landing">
          <section class="shop-reviews" style="margin-top:2rem;">
            <h3>{{ 'Customer ratings and reviews' | translate }}</h3>
            <div *ngIf="home && (home.reviews.length > 0 || home.ratingCount > 0); else noReviews">
              <div class="shop-reviews-avg" *ngIf="home.ratingAverage !== null && home.ratingCount > 0" style="display:flex; gap:2rem; flex-wrap:wrap; align-items:center; margin-bottom:0.75rem;">
                <div style="text-align:center;">
                  <div style="font-size:2.2rem; font-weight:700;">{{ home.ratingAverage }}</div>
                  <div style="color:#d97706;">{{ stars(home.ratingAverage) }}</div>
                  <div class="text-muted" style="font-size:0.85rem;">{{ '{p0} ratings' | translate: { p0: home.ratingCount } }}</div>
                </div>
                <div style="flex:1; min-width:200px; max-width:360px;">
                  <div *ngFor="let n of [5, 4, 3, 2, 1]" class="flex gap-2" style="align-items:center; font-size:0.85rem;">
                    <span style="width:2.2em;">{{ n }} ★</span>
                    <div style="flex:1; height:8px; background:rgba(128,128,128,0.3); border-radius:4px; overflow:hidden;"><div style="height:100%; background:#d97706;" [style.width.%]="starShare(n)"></div></div>
                    <span class="text-muted" style="width:2.5em; text-align:right;">{{ home.ratingStars[n - 1] }}</span>
                  </div>
                </div>
              </div>
              <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:0.75rem;">
                <div class="card shop-review" *ngFor="let r of home.reviews" style="padding:0.75rem;">
                  <div style="color:#d97706;">{{ stars(r.rating) }}</div>
                  <div class="mt-1">{{ r.text }}</div>
                  <div class="text-muted mt-1" style="font-size:0.8rem;">{{ r.reviewerName }} · {{ r.productName }}</div>
                </div>
              </div>
            </div>
            <ng-template #noReviews><p class="text-muted shop-reviews-none">{{ 'No ratings yet. When your order is completed, you can rate the products from your order page.' | translate }}</p></ng-template>
          </section>

          <section class="shop-newsletter card" style="margin-top:2rem; padding:1.25rem; text-align:center;">
            <h3 style="margin-top:0;">{{ 'Newsletter' | translate }}</h3>
            <p class="text-muted">{{ 'Get news and offers by e-mail. We send you a message to confirm your address first.' | translate }}</p>
            <form class="flex gap-2" style="justify-content:center; flex-wrap:wrap;" (ngSubmit)="subscribe()">
              <input class="form-control shop-nl-email" type="email" style="max-width:320px;" maxlength="160" [placeholder]="'Your e-mail address' | translate" [(ngModel)]="nlEmail" name="nlemail" required />
              <input class="shop-nl-hp" name="nlweb" [(ngModel)]="nlWebsite" tabindex="-1" autocomplete="off" aria-hidden="true" style="position:absolute; left:-9999px; width:1px; height:1px;" />
              <button class="btn btn-primary shop-nl-send" type="submit" [disabled]="nlBusy">{{ 'Subscribe' | translate }}</button>
            </form>
            <div class="alert alert-success mt-2 shop-nl-done" *ngIf="nlDone">{{ nlDone }}</div>
            <div class="alert alert-danger mt-2 shop-nl-error" *ngIf="nlError">{{ nlError }}</div>
          </section>
        </ng-container>
        </ng-container>

        <button class="btn btn-sm btn-outline-secondary mt-3 shop-back" *ngIf="step === 'order' && !showAccount" (click)="back()">← {{ 'Continue shopping' | translate }}</button>

        <div class="card mt-3 shop-checkout" id="checkout" style="padding:1rem;" *ngIf="step === 'order' && count() > 0 && !showAccount">
          <h4 class="mb-2">{{ 'Your order' | translate }}</h4>
          <div class="alert alert-danger shop-order-error" id="order-error" role="alert" *ngIf="orderError">{{ orderError }}</div>
          <div *ngFor="let l of cartLines()" class="flex justify-between align-center" style="padding:0.25rem 0; gap:0.5rem;">
            <span style="flex:1;">{{ l.p.name }}</span>
            <span class="flex gap-1" style="align-items:center;">
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, -1)">−</button>
              <span style="min-width:1.5em; text-align:center;">{{ l.qty }}</span>
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, 1)" [disabled]="l.qty >= 20">+</button>
            </span>
            <span style="min-width:4.5em; text-align:right;">{{ l.qty * l.p.price | shopMoney: cur }}</span>
          </div>
          <hr />

          <div class="text-muted mb-2 shop-account-hint" *ngIf="shop.accounts" style="font-size:0.85rem;">
            <ng-container *ngIf="account">{{ 'Signed in as {p0}. This order will be saved in your account.' | translate: { p0: account.email || account.phone } }}</ng-container>
            <ng-container *ngIf="!account">{{ 'Buying as a guest.' | translate }} <a href="javascript:void(0)" class="shop-signin-link" (click)="showAccount = true">{{ 'Sign in' | translate }}</a> {{ 'to save your details and see your orders.' | translate }}</ng-container>
          </div>

          <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
            <label *ngIf="shop.delivery" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="DELIVERY" [(ngModel)]="method" (ngModelChange)="recheckCoupon()" /> {{ 'Delivery' | translate }}</label>
            <label *ngIf="shop.pickup" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="PICKUP" [(ngModel)]="method" (ngModelChange)="recheckCoupon()" /> {{ 'Pick-up' | translate }}</label>
          </div>
          <div class="text-muted mb-2" *ngIf="method === 'PICKUP' && shop.pickupAddress">📍 {{ shop.pickupAddress }}</div>

          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Your name' | translate }}</label><input class="form-control shop-name-input" maxlength="80" name="cname" [(ngModel)]="name" /></div>
            <div class="form-group"><label class="form-label">{{ 'Your phone number' | translate }}</label><input class="form-control shop-phone" maxlength="40" inputmode="tel" name="cphone" [(ngModel)]="phone" /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail (optional)' | translate }}</label><input class="form-control" maxlength="160" inputmode="email" name="cemail" [(ngModel)]="email" (change)="cartChanged()" /></div>
          </div>
          <div class="text-muted shop-cart-note mb-2" *ngIf="shop.cartReminders" style="font-size:0.85rem;">{{ 'If you do not finish your order, we may remind you about your cart by e-mail: once after about 1 hour and once after 3 days. Each e-mail has a link to stop the reminders.' | translate }}</div>
          <ng-container *ngIf="method === 'DELIVERY'">
            <div class="form-group" *ngIf="shop.zones.length"><label class="form-label">{{ 'Delivery area' | translate }}</label>
              <select class="form-select" style="max-width:320px;" name="zone" [(ngModel)]="zoneId" (ngModelChange)="recheckCoupon()">
                <option [ngValue]="null" disabled>{{ 'Choose your area…' | translate }}</option>
                <option *ngFor="let z of shop.zones" [ngValue]="z.id">{{ z.name }} ({{ z.fee | shopMoney: cur }})</option>
              </select></div>
            <div class="form-group shop-saved-addr" *ngIf="account && addresses.length"><label class="form-label">{{ 'My saved addresses' | translate }}</label>
              <select class="form-select" style="max-width:320px;" name="savedaddr" [ngModel]="pickedAddr" (ngModelChange)="pickAddress($event)">
                <option [ngValue]="null">{{ 'Choose a saved address…' | translate }}</option>
                <option *ngFor="let a of addresses" [ngValue]="a.id">{{ a.label }}: {{ a.address }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Delivery address' | translate }}</label><input class="form-control shop-address" maxlength="240" name="caddr" [(ngModel)]="address" /></div>
            <div class="form-group shop-save-addr" *ngIf="account && address.trim() && !addressSaved()">
              <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="saveaddr" [(ngModel)]="saveAddr" /> {{ 'Save this address in my account' | translate }}</label>
              <input *ngIf="saveAddr" class="form-control shop-addr-label mt-1" style="max-width:240px;" maxlength="60" name="addrlabel" [(ngModel)]="addrLabel" [placeholder]="'Name (Home, Work…)' | translate" />
            </div>
          </ng-container>
          <div class="form-group"><label class="form-label">{{ 'Note for the shop (optional)' | translate }}</label><input class="form-control" maxlength="300" name="cnotes" [(ngModel)]="notes" /></div>
          <label class="flex gap-1 mb-2 shop-text-opt" *ngIf="shop.textChannel" style="align-items:flex-start;">
            <input type="checkbox" name="textUpdates" [(ngModel)]="textUpdates" style="margin-top:0.25rem;" />
            <span>{{ shop.textChannel === 'WHATSAPP' ? ('Send me updates about this order by WhatsApp message' | translate) : ('Send me updates about this order by text message (SMS)' | translate) }}</span>
          </label>
          <!-- A real visitor never sees this field; a bot that fills in every box does. -->
          <input type="text" name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" aria-hidden="true" style="position:absolute; left:-9999px; width:1px; height:1px;" />

          <div class="mb-2" *ngIf="shop.payOnline">
            <label class="form-label">{{ 'How do you want to pay?' | translate }}</label>
            <div class="flex gap-3" style="flex-wrap:wrap;">
              <label *ngIf="shop.cash" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="CASH" [(ngModel)]="payment" /> {{ method === 'DELIVERY' ? ('Cash on delivery' | translate) : ('Cash at pick-up' | translate) }}</label>
              <label *ngIf="shop.payMethods.includes('MOBILE_MONEY')" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="MOBILE_MONEY" [(ngModel)]="payment" /> {{ 'Mobile money' | translate }}</label>
              <label *ngIf="shop.payMethods.includes('CARD')" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="CARD" [(ngModel)]="payment" /> {{ 'Card' | translate }}</label>
            </div>
            <div class="mt-1" *ngIf="providersFor(payment).length > 1">
              <div class="form-label">{{ 'Pay with' | translate }}</div>
              <app-pay-provider-picker [providers]="providersFor(payment)" [(value)]="payProvider"></app-pay-provider-picker>
            </div>
            <div class="form-group mt-1" *ngIf="payment === 'MOBILE_MONEY'">
              <label class="flex gap-1 shop-pay-same" style="align-items:center;"><input type="checkbox" name="paysame" [(ngModel)]="paySame" /> {{ 'Pay from my phone number above' | translate }}<span *ngIf="phone.trim()">&nbsp;({{ phone.trim() }})</span></label>
              <ng-container *ngIf="!paySame">
                <label class="form-label mt-1">{{ 'Mobile money number to pay from' | translate }}</label>
                <input class="form-control shop-pay-phone" style="max-width:260px;" maxlength="40" inputmode="tel" name="payphone" [(ngModel)]="payPhone" />
              </ng-container>
            </div>
          </div>
          <div class="mb-2 shop-coupon-box" *ngIf="shop.coupons">
            <label class="form-label">{{ 'Coupon code (optional)' | translate }}</label>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <input class="form-control shop-coupon" style="max-width:220px; text-transform:uppercase;" maxlength="40" name="coupon" [(ngModel)]="couponCode" [disabled]="!!coupon" (keyup.enter)="applyCoupon()" />
              <button type="button" class="btn btn-outline-primary shop-coupon-apply" *ngIf="!coupon" [disabled]="couponBusy || !couponCode.trim()" (click)="applyCoupon()">{{ couponBusy ? ('Checking…' | translate) : ('Apply' | translate) }}</button>
              <button type="button" class="btn btn-outline-secondary shop-coupon-remove" *ngIf="coupon" (click)="removeCoupon()">{{ 'Remove' | translate }}</button>
            </div>
            <div class="shop-coupon-ok" *ngIf="coupon" style="color:#15803d; font-size:0.9rem;">{{ 'Coupon {p0} applied.' | translate: { p0: coupon.code } }}</div>
            <div class="shop-coupon-error" *ngIf="couponError" style="color:#b91c1c; font-size:0.9rem;">{{ couponError }}</div>
          </div>
          <div class="mb-2 shop-points-box" *ngIf="shop.loyalty && account && account.points > 0">
            <label class="form-label">{{ 'Use loyalty points (optional)' | translate }}</label>
            <div class="text-muted" style="font-size:0.85rem;">{{ 'You have {p0} points. Each point takes 1 off, up to {p1} on this order.' | translate: { p0: account.points, p1: pointsCap() } }}</div>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <input class="form-control shop-points" type="number" min="0" [max]="pointsCap()" style="max-width:160px;" name="pointsToUse" [(ngModel)]="pointsToUse" />
              <button type="button" class="btn btn-outline-primary shop-points-max" [disabled]="pointsCap() <= 0" (click)="pointsToUse = pointsCap()">{{ 'Use the most I can' | translate }}</button>
              <button type="button" class="btn btn-outline-secondary shop-points-remove" *ngIf="pointsUsed() > 0" (click)="pointsToUse = 0">{{ 'Remove' | translate }}</button>
            </div>
          </div>
          <div class="text-muted shop-points-hint mb-2" *ngIf="shop.loyalty && shop.accounts && !account" style="font-size:0.85rem;">{{ 'Sign in (👤 above) to earn loyalty points on your orders.' | translate }}</div>
          <hr />
          <div class="flex justify-between"><span>{{ 'Items' | translate }}</span><span>{{ subtotal() | shopMoney: cur }}</span></div>
          <div class="flex justify-between shop-discount" *ngIf="discount() > 0" style="color:#15803d;"><span>{{ 'Discount ({p0})' | translate: { p0: coupon?.code } }}</span><span>−{{ discount() | shopMoney: cur }}</span></div>
          <div class="flex justify-between shop-points-row" *ngIf="pointsUsed() > 0" style="color:#15803d;"><span>{{ 'Loyalty points' | translate }}</span><span>−{{ pointsUsed() | shopMoney: cur }}</span></div>
          <div class="flex justify-between" *ngIf="method === 'DELIVERY'"><span>{{ 'Delivery' | translate }}</span><span>{{ feeShown() | shopMoney: cur }}<span *ngIf="waived() > 0" style="color:#15803d;"> ({{ 'free with the coupon' | translate }})</span></span></div>
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="shop-total">{{ orderTotal() | shopMoney: cur }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="payment === 'CASH'">{{ method === 'DELIVERY' ? ('Pay in cash when your order arrives. Prices include tax.' | translate) : ('Pay in cash when you collect. Prices include tax.' | translate) }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="payment !== 'CASH'">{{ 'You pay now. The shop gets your order as soon as the payment is confirmed. Prices include tax.' | translate }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="shop.minOrder > 0">{{ 'Minimum order {p0}' | translate: { p0: (shop.minOrder | shopMoney: cur) } }}</div>
          <button class="btn btn-primary shop-place mt-2" style="width:100%;" [disabled]="busy || !shop.open || (!shop.cash && !shop.payOnline)" (click)="place()">{{ busy ? ('Sending…' | translate) : payment === 'CASH' ? ('Place order' | translate) : ('Place order and pay' | translate) }}</button>
        </div>

        <div *ngIf="step === 'shop' && !showAccount && count() > 0" style="position:fixed; left:0; right:0; bottom:0; background:#111827; color:#fff; padding:0.6rem 1rem; display:flex; justify-content:space-between; align-items:center; z-index:50;">
          <span>🛒 {{ count() }} · {{ subtotal() | shopMoney: cur }}</span>
          <button class="btn btn-sm btn-primary shop-checkout-btn" (click)="checkout()">{{ 'Proceed to checkout' | translate }}</button>
        </div>
      </ng-container>
    </div>

    <footer class="shop-footer" *ngIf="shop" style="background:#111827; color:#d1d5db; padding:2rem 0.75rem 6rem;">
      <div style="max-width:1100px; margin:0 auto; display:grid; grid-template-columns:repeat(auto-fit,minmax(210px,1fr)); gap:1.5rem;">
        <div>
          <strong style="color:#fff;">{{ shop.name }}</strong>
          <p *ngIf="shop.page.aboutText" class="shop-about" style="margin:0.4rem 0 0;">{{ shop.page.aboutText }}</p>
        </div>
        <div>
          <strong style="color:#fff;">{{ 'Quick links' | translate }}</strong>
          <ul style="list-style:none; padding:0; margin:0.4rem 0 0; line-height:1.9;">
            <li><a style="color:#d1d5db; cursor:pointer;" (click)="goHome()">{{ 'Shop' | translate }}</a></li>
            <li *ngFor="let l of shop.page.footerLinks"><a class="shop-footer-link" style="color:#d1d5db; cursor:pointer;" (click)="openLink(l.url)">{{ l.label }}</a></li>
            <li><a class="shop-footer-staff" style="color:#d1d5db;" routerLink="/login">{{ 'Staff Login' | translate }}</a></li>
          </ul>
        </div>
        <div>
          <strong style="color:#fff;">{{ 'Track your order' | translate }}</strong>
          <form class="flex gap-1 mt-1" (ngSubmit)="trackOrder()">
            <input class="form-control shop-track-code" style="max-width:150px;" maxlength="20" [placeholder]="'Order code' | translate" [(ngModel)]="trackCode" name="trackcode" />
            <button class="btn btn-sm btn-primary shop-track-go" type="submit">{{ 'Go' | translate }}</button>
          </form>
        </div>
        <div class="shop-contact">
          <strong style="color:#fff;">{{ 'Contact' | translate }}</strong>
          <div style="margin-top:0.4rem;">
            <div *ngIf="shop.phone">📞 {{ shop.phone }}</div>
            <div *ngIf="shop.page.contactEmail">✉️ <a style="color:#d1d5db;" [href]="'mailto:' + shop.page.contactEmail">{{ shop.page.contactEmail }}</a></div>
            <div *ngIf="shop.pickupAddress">📍 {{ shop.pickupAddress }}</div>
            <div *ngIf="shop.page.openingHours">🕒 {{ shop.page.openingHours }}</div>
            <div class="flex gap-2 mt-1" style="flex-wrap:wrap;"><a class="shop-social" *ngFor="let l of shop.page.socialLinks" style="color:#d1d5db; cursor:pointer; text-decoration:underline;" (click)="openLink(l.url)">{{ l.label }}</a></div>
          </div>
        </div>
      </div>
    </footer>

      <ng-template #rowTpl let-r>
          <section class="shop-row" style="margin-top:2rem;" [attr.data-row]="r.key">
            <h3>{{ r.title | translate }}</h3>
            <app-shop-rail [auto]="autoSlide" [count]="r.items.length">
              <div class="car-item" *ngFor="let p of r.items"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></div>
            </app-shop-rail>
          </section>
      </ng-template>

      <ng-template #card let-p>
          <div class="card shop-product" style="padding:0.6rem; flex:1 1 auto; min-width:0; display:flex; flex-direction:column;">
            <div style="position:relative;">
              <a class="shop-product-link" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="display:block;">
                <img *ngIf="p.imageUrl" [src]="img(p)" [alt]="p.name" loading="lazy" style="width:100%; aspect-ratio:4/3; object-fit:cover; border-radius:6px; display:block;" />
                <div *ngIf="!p.imageUrl" style="width:100%; aspect-ratio:4/3; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2.2rem;">🛍️</div>
              </a>
              <span class="shop-offbadge" *ngIf="offPercent(p) > 0" style="position:absolute; left:6px; top:6px; background:#dc2626; color:#fff; border-radius:4px; padding:0 0.4rem; font-size:0.75rem; font-weight:700;">-{{ offPercent(p) }}%</span>
              <button type="button" class="shop-wish-btn" (click)="wish.toggle(p.id)" [attr.aria-label]="(wish.has(p.id) ? 'Remove from my wishlist' : 'Add to my wishlist') | translate" [attr.aria-pressed]="wish.has(p.id)" [style.color]="wish.has(p.id) ? '#dc2626' : '#6b7280'" style="position:absolute; right:6px; top:6px; border:0; border-radius:999px; width:30px; height:30px; background:rgba(255,255,255,0.92); cursor:pointer; font-size:1.1rem; line-height:1; padding:0;">{{ wish.has(p.id) ? '♥' : '♡' }}</button>
              <button type="button" class="shop-qv-btn" *ngIf="!p.variants" (click)="qv = p" [attr.aria-label]="'Quick view' | translate" style="position:absolute; right:6px; bottom:6px; border:0; border-radius:999px; background:rgba(17,24,39,0.72); color:#fff; font-size:0.72rem; padding:0.2rem 0.6rem; cursor:pointer;">👁 {{ 'Quick view' | translate }}</button>
            </div>
            <a class="shop-product-name" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" [title]="p.name" style="display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; min-height:2.6em; line-height:1.3; margin-top:0.4rem; font-weight:600; color:inherit; text-decoration:none;">{{ p.name }}</a>
            <div class="shop-rating" style="min-height:1.3em; font-size:0.85rem;">
              <ng-container *ngIf="p.ratingCount > 0; else noStars"><span style="color:#d97706;" [attr.aria-label]="p.rating + ' / 5'">{{ stars(p.rating) }}</span> <span class="text-muted">({{ p.ratingCount }})</span></ng-container>
              <ng-template #noStars><span class="text-muted" style="font-size:0.78rem;">{{ 'No ratings yet' | translate }}</span></ng-template>
            </div>
            <div style="margin-top:0.2rem;"><span class="text-muted shop-from" *ngIf="p.priceFrom" style="font-size:0.8rem; margin-right:0.3rem;">{{ 'Starting at' | translate }}</span><strong class="shop-price" style="font-size:1.15rem;">{{ p.price | shopMoney: cur }}</strong><s class="text-muted shop-was" *ngIf="p.wasPrice" style="font-size:0.8rem; margin-left:0.4rem;">{{ p.wasPrice | shopMoney: cur }}</s></div>
            <div class="shop-avail" style="font-size:0.8rem; min-height:1.2em;" [style.color]="p.stock === 'SOLD_OUT' ? '#b91c1c' : p.stock === 'LOW' ? '#b45309' : '#15803d'">
              {{ p.stock === 'SOLD_OUT' ? ('Out of stock' | translate) : p.stock === 'LOW' ? ('Few left' | translate) : ('In stock' | translate) }}</div>
            <div style="margin-top:auto; padding-top:0.5rem;">
              <a class="btn btn-sm btn-primary shop-choose" *ngIf="p.variants; else plainBtn" [routerLink]="[]" [queryParams]="{ product: p.id }" queryParamsHandling="merge" style="width:100%; text-align:center;">{{ 'Choose options' | translate }}</a>
              <ng-template #plainBtn>
              <div class="flex gap-1" *ngIf="qty(p) > 0; else addBtn" style="align-items:center; justify-content:space-between;">
                <button class="btn btn-sm btn-outline-secondary" (click)="change(p, -1)">−</button>
                <span>{{ qty(p) }}</span>
                <button class="btn btn-sm btn-outline-secondary" (click)="change(p, 1)" [disabled]="qty(p) >= 20">+</button>
              </div>
              <ng-template #addBtn><button class="btn btn-sm btn-primary shop-add" style="width:100%;" [disabled]="p.stock === 'SOLD_OUT' || !shop?.open" (click)="change(p, 1)">{{ 'Add to cart' | translate }}</button></ng-template>
              </ng-template>
            </div>
          </div>
      </ng-template>

      <app-shop-quick-view *ngIf="qv" [p]="qv" [cur]="cur" [open]="!!shop?.open" [cart]="cart" (change)="change($event.p, $event.by)" (closed)="qv = null"></app-shop-quick-view>
  `,
  styles: [
    '.car-rail::-webkit-scrollbar { display: none; }',
    ':host(.shop-branded) ::ng-deep .btn-primary, :host(.shop-branded) ::ng-deep .btn-primary:hover { background: var(--shop-brand); border-color: var(--shop-brand); color: #fff; }',
    '.shop-sugg-item:hover { background: rgba(128, 128, 128, 0.18); }',
  ],
})
export class ShopComponent implements OnInit, OnDestroy {
  shop: Shop | null = null;
  error: string | null = null;
  /** shop = browsing; order = the cart and the place-order form. */
  step: 'shop' | 'order' = 'shop';
  /** Why the order could not be sent. Shown at the top of the order form, where the customer is looking. */
  orderError: string | null = null;
  q = '';
  category = '';
  /** What the visitor has put in the cart, kept with the product so it survives moving between pages and searches. */
  cart: Record<number, number> = {};
  cartProducts: Record<number, ShopProduct> = {};
  items: ShopProduct[] = [];
  page = 0;
  readonly size = 16;
  total = 0;
  totalPages = 0;
  loading = false;
  private seq = 0;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  method: 'DELIVERY' | 'PICKUP' = 'DELIVERY';
  name = '';
  phone = '';
  email = '';
  address = '';
  notes = '';
  website = '';
  zoneId: number | null = null;
  /** How the customer pays: cash on delivery or at pick-up, or online. */
  payment: 'CASH' | 'MOBILE_MONEY' | 'CARD' = 'CASH';
  payPhone = '';
  /** Ticked by default: the mobile money prompt goes to the contact phone number. Unticked: the customer types another number to pay from. */
  paySame = true;
  /** The provider chosen when the shop offers several for the way to pay (MTN, Airtel, ...). */
  payProvider = '';
  /** "Send me updates by text message": off unless ticked, and only shown when the shop texts customers. */
  textUpdates = false;
  busy = false;
  /** The signed-in customer (accounts are optional: null = guest). */
  account: AccountProfile | null = null;
  showAccount = false;
  /** The coupon box: what the customer typed, and what the server says it is worth on this cart (the order asks the server again). */
  couponCode = '';
  coupon: CouponCheckResult | null = null;
  couponError: string | null = null;
  couponBusy = false;
  private couponSeq = 0;
  /** Loyalty points the customer typed to use on this order. */
  pointsToUse = 0;
  /** What could not be put back in the cart when ordering again. */
  reorderNote: string | null = null;
  /** The pending save of the cart for the reminder e-mail, what was last sent, and whether the order was placed (then nothing more is sent). */
  private cartTimer: ReturnType<typeof setTimeout> | null = null;
  private cartSent = '';
  private ordered = false;

  /** The front page rows chosen by staff, the banner pictures and the approved reviews (null until loaded, or when they cannot be). */
  home: ShopHome | null = null;
  logo: string | null = null;
  trackCode = '';
  nlEmail = '';
  nlWebsite = '';
  nlBusy = false;
  nlDone: string | null = null;
  nlError: string | null = null;

  constructor(private api: OnlineStoreApiService, private router: Router, private route: ActivatedRoute, public lang: LanguageService, private publicSettings: PublicSettingsApiService, public wish: WishlistService, private pwa: PwaService) {}

  /** The landing sections (hero, rows, reviews, newsletter) show when the visitor is not searching or filtering. */
  get landing(): boolean {
    return this.step === 'shop' && !this.showAccount && !this.showWish && !this.q.trim() && !this.category && !this.filtersActive;
  }

  /** Every row that has products, in the order the front page shows them. */
  get rows(): { key: string; title: string; items: ShopProduct[] }[] {
    const h = this.home;
    if (!h) return [];
    return [
      { key: 'new', title: 'New arrivals', items: h.newArrivals },
      { key: 'featured', title: 'Featured products', items: h.featured },
      { key: 'best', title: 'Best-selling products', items: h.bestSellers },
      { key: 'offers', title: 'Special offers and discounts', items: h.offers },
    ].filter((r) => r.items.length > 0);
  }

  /** Keeps a row's screen elements alive between refreshes (the rows are rebuilt on every check), so a click on a card inside it is not lost. */
  trackRow(_index: number, r: { key: string }): string {
    return r.key;
  }

  /** The row above the product list: New arrivals. */
  get rowsBefore(): { key: string; title: string; items: ShopProduct[] }[] {
    return this.rows.filter((r) => r.key === 'new');
  }

  /** The rows under the product list: Featured, Best-selling, then special offers. */
  get rowsAfter(): { key: string; title: string; items: ShopProduct[] }[] {
    return this.rows.filter((r) => r.key !== 'new');
  }

  get heroTitle(): string {
    return this.shop?.page?.heroTitle || this.shop?.name || '';
  }

  get heroSubtitle(): string | null {
    return this.shop?.page?.heroSubtitle || this.shop?.intro || null;
  }

  get heroButton(): string {
    return this.shop?.page?.heroButtonText || T('Shop now');
  }

  /** The banner pictures staff uploaded, in sliding order (full addresses). */
  get bannerUrls(): string[] {
    return (this.home?.banners ?? []).map((u) => this.api.imageUrl(u));
  }

  // ---- the banner slideshow moves by itself and stops while the visitor points at it or the tab is hidden (product rows: see shop-rail.component.ts)
  /** Staff's setting: the product rows also slide by themselves (off by default). */
  get autoSlide(): boolean {
    return !!this.shop?.page?.autoSlideRows;
  }

  heroIndex = 0;
  heroHold = false;
  private heroTimer: ReturnType<typeof setInterval> | null = null;
  private touchX = 0;

  private get calm(): boolean {
    return typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private startMotion(): void {
    if (this.calm) return;
    if (!this.heroTimer) this.heroTimer = setInterval(() => { if (!this.heroHold && !document.hidden && this.bannerUrls.length > 1) this.heroGo(1); }, 5000);
  }

  ngOnDestroy(): void {
    if (this.cartTimer) clearTimeout(this.cartTimer);
    if (this.heroTimer) clearInterval(this.heroTimer);
  }

  /** The CSS value of a slide's picture. */
  slide(u: string): string {
    return `url("${u}")`;
  }

  heroGo(step: number): void {
    const n = this.bannerUrls.length;
    if (n > 0) this.heroIndex = (this.heroIndex + step + n) % n;
  }

  heroSet(i: number): void {
    this.heroIndex = i;
  }

  heroTouchStart(e: TouchEvent): void {
    this.touchX = e.touches[0]?.clientX ?? 0;
    this.heroHold = true;
  }

  heroTouchEnd(e: TouchEvent): void {
    const dx = (e.changedTouches[0]?.clientX ?? 0) - this.touchX;
    if (Math.abs(dx) > 40) this.heroGo(dx < 0 ? 1 : -1);
    this.heroHold = false;
  }

  /** The banner button: staff's link, or down to the products. */
  cta(): void {
    const link = this.shop?.page?.heroButtonLink;
    if (link) this.openLink(link);
    else this.scrollAll();
  }

  /** A link written by staff: a page of this site stays in the app, anything else opens as an address. Only / https:// http:// mailto: tel: are ever used. */
  openLink(url: string | null | undefined): void {
    if (!url) return;
    if (url.startsWith('/') && !url.startsWith('//')) {
      void this.router.navigateByUrl(url);
    } else if (/^(https?:\/\/|mailto:|tel:)/i.test(url)) {
      window.location.href = url;
    }
  }

  scrollAll(): void {
    setTimeout(() => (document.getElementById('shop-all') ?? document.getElementById('shop-top'))?.scrollIntoView({ behavior: 'smooth' }), 50);
  }

  /** The brand in the header: back to the start of the shop. */
  goHome(): void {
    this.closeProduct();
    this.step = 'shop';
    this.showAccount = false;
    this.showWish = false;
    this.q = '';
    this.category = '';
    this.f = { brand: '', minPrice: null, maxPrice: null, inStock: false, minRating: 0, sort: '' };
    this.page = 0;
    this.load();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** Share (in percent) of the shop's ratings that gave n stars. */
  starShare(n: number): number {
    const h = this.home;
    return h && h.ratingCount > 0 && h.ratingStars ? Math.round(((h.ratingStars[n - 1] ?? 0) * 100) / h.ratingCount) : 0;
  }

  stars(r: number): string {
    const n = Math.max(0, Math.min(5, Math.round(r)));
    return '★'.repeat(n) + '☆'.repeat(5 - n);
  }

  trackOrder(): void {
    const c = this.trackCode.trim().toUpperCase();
    if (c) void this.router.navigateByUrl('/shop/track/' + encodeURIComponent(c));
  }

  subscribe(): void {
    this.nlError = null;
    this.nlDone = null;
    if (!this.nlEmail.trim()) return;
    this.nlBusy = true;
    this.api.subscribeNewsletter(this.nlEmail.trim(), this.lang.current, this.nlWebsite).subscribe({
      next: () => {
        this.nlBusy = false;
        this.nlDone = T('Thank you. We sent you an e-mail: open the link in it to confirm your subscription.');
        this.nlEmail = '';
      },
      error: (e) => {
        this.nlBusy = false;
        this.nlError = e?.error?.message ?? T('That did not work');
      },
    });
  }

  /** The shop's currency, written after every price. */
  get cur(): string {
    return this.shop?.currency ?? '';
  }

  /** The ads the shop may show today, by space (empty until loaded, or when none is set up). */
  ads: Record<string, ShopAd[]> = {};

  adsFor(space: string): ShopAd[] {
    return this.ads[space] ?? [];
  }

  /** The product whose page is open (/shop?product=ID), or null while the shop's own pages show. It follows the address, so the back button and shared links work. */
  viewId: number | null = null;

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((m) => {
      const v = Number(m.get('product'));
      this.viewId = Number.isInteger(v) && v > 0 ? v : null;
      if (!this.viewId) this.loadRecent();
    });
    this.api.shopAds().subscribe({ next: (a) => (this.ads = a), error: () => {} });
    this.api.shop().subscribe({
      next: (s) => {
        this.shop = s;
        this.pwa.useShopManifest(s.name, s.page?.brandColor ?? null);
        this.method = s.delivery ? 'DELIVERY' : 'PICKUP';
        if (!s.cash && s.payMethods.length > 0) this.payment = s.payMethods[0] as 'MOBILE_MONEY' | 'CARD';
        if (s.accounts && shopToken.get()) {
          // A stale token is simply dropped: the customer shops as a guest.
          this.api.accountMe().subscribe({ next: (p) => this.accountChanged(p), error: () => shopToken.clear() });
        }
        this.load();
        // The "back to my cart" link of a reminder e-mail: /shop/cart/<token>.
        const cartToken = this.route.snapshot.paramMap.get('token');
        if (cartToken) this.restoreCart(cartToken);
        this.api.home().subscribe({ next: (h) => { this.home = h; this.startMotion(); }, error: () => (this.home = null) });
        this.publicSettings.branding().subscribe({ next: (b) => (this.logo = b.logoDataUrl || null), error: () => {} });
      },
      error: (e) => (this.error = e?.error?.message ?? T('The shop is not available right now.')),
    });
  }

  /** The customer signed in, saved their details, or signed out. The order form is filled from the account, never over what was already typed. */
  accountChanged(p: AccountProfile | null): void {
    const wasIn = !!this.account;
    this.account = p;
    this.wish.setSignedIn(!!p);
    if (!p) {
      this.addresses = [];
      this.pickedAddr = null;
      return;
    }
    if (!wasIn) this.loadAddresses();
    if (!this.name.trim() && p.name) this.name = p.name;
    if (!this.phone.trim() && p.phone) this.phone = p.phone;
    if (!this.email.trim() && p.email) this.email = p.email;
    if (!this.address.trim() && p.address) this.address = p.address;
  }

  // ---- wishlist and saved addresses (v1.47.0)
  showWish = false;
  wishProducts: ShopProduct[] = [];
  wishBusy = false;
  addresses: ShopAddress[] = [];
  pickedAddr: number | null = null;
  saveAddr = false;
  addrLabel = '';

  /** The wishlist's products that are still hearted, in the order of the list (a heart removed on this page leaves it at once). */
  get wishShown(): ShopProduct[] {
    const byId = new Map(this.wishProducts.map((p) => [p.id, p]));
    return this.wish.ids().map((id) => byId.get(id)).filter((p): p is ShopProduct => !!p);
  }

  openWishlist(): void {
    this.showWish = !this.showWish;
    this.closeProduct();
    if (!this.showWish) return;
    this.wishBusy = true;
    const ids = this.wish.ids();
    if (!ids.length) { this.wishProducts = []; this.wishBusy = false; return; }
    this.api.wished(ids).subscribe({ next: (r) => { this.wishProducts = r; this.wishBusy = false; }, error: () => { this.wishProducts = []; this.wishBusy = false; } });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  private loadAddresses(): void {
    this.api.addressList().subscribe({
      next: (list) => {
        this.addresses = list;
        const d = list.find((a) => a.isDefault);
        if (d && !this.address.trim()) this.useAddress(d);
      },
      error: () => (this.addresses = []),
    });
  }

  private useAddress(a: ShopAddress): void {
    this.pickedAddr = a.id;
    this.address = a.address;
    if (a.zoneId && this.shop?.zones.some((z) => z.id === a.zoneId)) this.zoneId = a.zoneId;
  }

  pickAddress(id: number | null): void {
    this.pickedAddr = id;
    const a = this.addresses.find((x) => x.id === id);
    if (!a) return;
    this.useAddress(a);
    this.recheckCoupon();
  }

  /** True when the address typed is already one of the saved addresses, so there is nothing to offer to save. */
  addressSaved(): boolean {
    const t = this.address.trim().toLowerCase();
    return this.addresses.some((a) => a.address.trim().toLowerCase() === t);
  }

  /** The cart behind the link of a reminder e-mail: today's prices go into the cart and the customer lands on the order form. */
  private restoreCart(token: string): void {
    this.api.restoreCart(token).subscribe({
      next: (r) => {
        if (r.email && !this.email.trim()) this.email = r.email;
        this.reordered({ lines: r.lines, unavailable: r.unavailable });
      },
      error: () => (this.reorderNote = T('This link is not valid, or it was already used.')),
    });
  }

  /** The cart is sent to the shop a moment after the customer types an e-mail address or changes the cart, so it can remind them if they leave. Only when the shop has reminders on. */
  cartChanged(): void {
    if (!this.shop?.cartReminders || this.ordered) return;
    if (this.cartTimer) clearTimeout(this.cartTimer);
    this.cartTimer = setTimeout(() => this.sendCart(), 1500);
  }

  private sendCart(): void {
    this.cartTimer = null;
    const email = this.email.trim();
    if (!this.shop?.cartReminders || this.ordered || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return;
    const lines = this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty }));
    const key = email.toLowerCase() + JSON.stringify(lines);
    if (key === this.cartSent) return;
    this.cartSent = key;
    this.api.saveCart({ email, language: this.lang.current, website: this.website, lines }).subscribe({ error: () => (this.cartSent = '') });
  }

  /** "Order again": today's products and prices go into the cart, and the customer lands on the order form. */
  reordered(r: Reorder): void {
    this.cart = {};
    this.cartProducts = {};
    for (const l of r.lines) {
      this.cart[l.product.id] = Math.max(1, Math.min(20, Math.round(l.quantity)));
      this.cartProducts[l.product.id] = l.product;
    }
    this.reorderNote = r.unavailable.length > 0 ? T('Not added, because they are gone or sold out: {p0}', { p0: r.unavailable.join(', ') }) : null;
    this.showAccount = false;
    this.cartChanged();
    if (this.count() > 0) this.checkout();
    else this.step = 'shop';
  }

  /** Fetches the current page. A slower, older answer never replaces a newer one. */
  load(scrollToTop = false): void {
    const mine = ++this.seq;
    this.loading = true;
    this.api.products(this.page, this.size, this.q, this.category, this.f).subscribe({
      next: (r: ShopPage) => {
        if (mine !== this.seq) return;
        if (r.totalPages > 0 && r.page >= r.totalPages) {
          this.page = r.totalPages - 1;
          this.load(scrollToTop);
          return;
        }
        this.items = r.items;
        this.total = r.total;
        this.totalPages = r.totalPages;
        this.loading = false;
        if (scrollToTop) document.getElementById('shop-top')?.scrollIntoView({ behavior: 'smooth' });
      },
      error: (e) => {
        if (mine !== this.seq) return;
        this.loading = false;
        this.error = e?.error?.message ?? T('The shop is not available right now.');
      },
    });
  }

  /** The shop's own colour, when staff chose one. */
  get brand(): string | null {
    return this.shop?.page?.brandColor || null;
  }

  // ---- filters and sorting of the product list
  f: ShopFilter = { brand: '', minPrice: null, maxPrice: null, inStock: false, minRating: 0, sort: '' };

  /** True when a filter (not just the sort order) narrows the list: the front page's rows then step aside. */
  get filtersActive(): boolean {
    const n = (v: number | null): boolean => v !== null && v !== undefined && `${v}` !== '';
    return !!this.f.brand || n(this.f.minPrice) || n(this.f.maxPrice) || this.f.inStock || this.f.minRating > 0;
  }

  filtersChanged(typing = false): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page = 0;
      this.load();
    }, typing ? 450 : 0);
  }

  clearFilters(): void {
    this.f = { brand: '', minPrice: null, maxPrice: null, inStock: false, minRating: 0, sort: '' };
    this.page = 0;
    this.load();
  }

  // ---- category tiles, quick view, recently viewed
  qv: ShopProduct | null = null;
  recent: ShopProduct[] = [];
  /** The Recently viewed products as one more sliding row (same look and movement as the other rows). */
  recentRow: { key: string; title: string; items: ShopProduct[] } = { key: 'recent', title: 'Recently viewed', items: [] };

  tileImg(u: string): string {
    return this.api.imageUrl(u) + '?w=320';
  }

  tile(name: string): void {
    this.pickCategory(name);
    window.scrollTo({ top: 0 });
  }

  offPercent(p: ShopProduct): number {
    return p.wasPrice && p.wasPrice > p.price ? Math.round(((p.wasPrice - p.price) * 100) / p.wasPrice) : 0;
  }

  loadRecent(): void {
    const ids = recentIds();
    if (!ids.length) {
      this.recent = [];
      return;
    }
    this.api.recent(ids).subscribe({ next: (r) => { this.recent = r; this.recentRow = { key: 'recent', title: 'Recently viewed', items: r }; }, error: () => { this.recent = []; this.recentRow = { key: 'recent', title: 'Recently viewed', items: [] }; } });
  }

  // ---- suggestions while typing
  sugg: ShopSuggest | null = null;
  private suggTimer: ReturnType<typeof setTimeout> | null = null;
  private suggSeq = 0;

  hasSuggestions(): boolean {
    return !!this.sugg && (this.sugg.products.length > 0 || this.sugg.categories.length > 0 || this.sugg.brands.length > 0);
  }

  suggest(): void {
    if (this.suggTimer) clearTimeout(this.suggTimer);
    const text = this.q.trim();
    if (text.length < 2) {
      this.sugg = null;
      return;
    }
    const mine = ++this.suggSeq;
    this.suggTimer = setTimeout(() => {
      this.api.suggest(text).subscribe({ next: (r) => { if (mine === this.suggSeq) this.sugg = r; }, error: () => {} });
    }, 250);
  }

  blurSuggest(): void {
    setTimeout(() => (this.sugg = null), 150);
  }

  thumb(p: ShopProduct): string {
    return this.api.imageUrl(p.imageUrl as string) + '?w=96';
  }

  openSuggested(p: ShopProduct): void {
    this.sugg = null;
    if (this.q) {
      this.q = '';
      this.load();
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams: { product: p.id }, queryParamsHandling: 'merge' });
  }

  suggestedCategory(c: string): void {
    this.sugg = null;
    this.q = '';
    this.closeProduct();
    this.pickCategory(c);
  }

  suggestedBrand(b: string): void {
    this.sugg = null;
    this.q = '';
    this.closeProduct();
    this.f.brand = b;
    this.filtersChanged();
  }

  /** Leaves a product page for the shop's own pages. */
  private closeProduct(): void {
    if (this.viewId) void this.router.navigate([], { relativeTo: this.route, queryParams: { product: null }, queryParamsHandling: 'merge' });
  }

  searchChanged(v: string): void {
    this.q = v ?? '';
    this.closeProduct();
    this.suggest();
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page = 0;
      this.load();
    }, 300);
  }

  pickCategory(c: string): void {
    this.category = c;
    this.page = 0;
    this.load();
  }

  go(p: number): void {
    if (p < 0 || p >= this.totalPages || p === this.page) return;
    this.page = p;
    this.load(true);
  }

  firstShown(): number {
    return this.total === 0 ? 0 : this.page * this.size + 1;
  }

  lastShown(): number {
    return Math.min(this.total, this.page * this.size + this.items.length);
  }

  img(p: ShopProduct): string {
    // A small copy is enough for the product cards; the server shrinks it once and keeps it.
    return this.api.imageUrl(p.imageUrl as string) + '?w=420';
  }

  qty(p: ShopProduct): number {
    return this.cart[p.id] ?? 0;
  }

  change(p: ShopProduct, by: number): void {
    const n = Math.max(0, Math.min(20, this.qty(p) + by));
    if (n === 0) {
      delete this.cart[p.id];
      delete this.cartProducts[p.id];
    } else {
      this.cart[p.id] = n;
      this.cartProducts[p.id] = p;
    }
    if (this.step === 'order' && this.count() === 0) this.back();
    this.recheckCoupon();
    this.cartChanged();
  }

  cartLines(): { p: ShopProduct; qty: number }[] {
    return Object.keys(this.cart).map((id) => ({ p: this.cartProducts[+id], qty: this.cart[+id] })).filter((l) => !!l.p && l.qty > 0);
  }

  count(): number {
    return Object.values(this.cart).reduce((a, b) => a + b, 0);
  }

  subtotal(): number {
    return this.cartLines().reduce((a, l) => a + l.qty * l.p.price, 0);
  }

  discount(): number {
    return this.coupon?.discount ?? 0;
  }

  waived(): number {
    return this.coupon?.feeWaived ?? 0;
  }

  /** The delivery fee the customer pays: the area's fee, less what a free-delivery coupon waives. */
  feeShown(): number {
    return Math.max(0, this.fee() - this.waived());
  }

  orderTotal(): number {
    return this.subtotal() - this.discount() - this.pointsUsed() + this.feeShown();
  }

  /** What loyalty points may take off this order: the signed-in customer's points, and at most the share of the goods the shop allows (the server checks again). */
  pointsCap(): number {
    if (!this.shop?.loyalty || !this.account) return 0;
    const payable = this.subtotal() - this.discount();
    const cap = Math.floor((payable * this.shop.loyaltyMaxPercent) / 100);
    return Math.max(0, Math.min(this.account.points ?? 0, cap));
  }

  /** The points that will be used: what the customer typed, within what is allowed. */
  pointsUsed(): number {
    const n = Math.floor(Number(this.pointsToUse) || 0);
    return Math.max(0, Math.min(n, this.pointsCap()));
  }

  /** "Apply": asks the server what the code is worth on this cart. silent = re-asking after the cart changed (a failure then just drops the coupon, with the reason). */
  applyCoupon(silent = false): void {
    const code = this.couponCode.trim();
    if (!code) return;
    const mine = ++this.couponSeq;
    if (!silent) {
      this.couponBusy = true;
      this.couponError = null;
    }
    this.api.couponCheck({ code, method: this.method, zoneId: this.method === 'DELIVERY' ? this.zoneId : null, phone: this.phone, lines: this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty })) }).subscribe({
      next: (r) => {
        if (mine !== this.couponSeq) return;
        this.couponBusy = false;
        this.coupon = r;
        this.couponCode = r.code;
        this.couponError = null;
      },
      error: (e) => {
        if (mine !== this.couponSeq) return;
        this.couponBusy = false;
        this.coupon = null;
        this.couponError = e?.error?.message ?? T('That coupon could not be checked. Please try again.');
      },
    });
  }

  removeCoupon(): void {
    this.couponSeq++;
    this.coupon = null;
    this.couponError = null;
    this.couponCode = '';
  }

  /** The cart, the way of getting it, or the area changed: what the coupon is worth may have changed too. */
  recheckCoupon(): void {
    if (!this.coupon) return;
    setTimeout(() => this.applyCoupon(true));
  }

  /** Same rule as the server (which checks it again): the area's fee, or nothing when the items reach the free-delivery amount. */
  fee(): number {
    if (!this.shop || this.method !== 'DELIVERY') return 0;
    if (this.shop.freeDeliveryOver != null && this.subtotal() >= this.shop.freeDeliveryOver) return 0;
    const z = this.shop.zones.find((x) => x.id === this.zoneId);
    return z ? z.fee : 0;
  }

  /** From the cart bar to the place-order step. */
  checkout(): void {
    if (this.count() === 0) return;
    this.orderError = null;
    this.step = 'order';
    window.scrollTo({ top: 0 });
  }

  back(): void {
    this.step = 'shop';
    this.orderError = null;
    window.scrollTo({ top: 0 });
  }

  /** The providers that offer this way to pay (when there are several the customer chooses). */
  providersFor(way: string): ShopPayProvider[] {
    return way === 'CASH' ? [] : (this.shop?.payProviders ?? []).filter((p) => p.methods.includes(way));
  }

  place(): void {
    this.orderError = null;
    if (this.payment === 'MOBILE_MONEY' && !this.paySame && !this.payPhone.trim()) {
      this.orderError = T('Enter the mobile money number to pay from');
      return;
    }
    const providers = this.providersFor(this.payment);
    const provider = this.payment === 'CASH' ? null : (providers.some((p) => p.code === this.payProvider) ? this.payProvider : (providers[0]?.code ?? null));
    this.busy = true;
    this.api
      .place({
        name: this.name, phone: this.phone, email: this.email || null, address: this.method === 'DELIVERY' ? this.address : null, method: this.method,
        zoneId: this.method === 'DELIVERY' ? this.zoneId : null, notes: this.notes || null, language: this.lang.current, website: this.website,
        lines: this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty })),
        payment: this.payment, payPhone: this.payment === 'MOBILE_MONEY' ? (this.paySame ? this.phone : this.payPhone.trim()) : null,
        couponCode: this.couponCode.trim() || null,
        pointsToUse: this.pointsUsed() || null,
        paymentProvider: provider,
        textUpdates: !!this.shop?.textChannel && this.textUpdates,
      })
      .subscribe({
        next: (t) => {
          this.busy = false;
          this.ordered = true;
          if (this.account && this.saveAddr && this.method === 'DELIVERY' && this.address.trim() && !this.addressSaved()) {
            this.api.addressAdd({ label: this.addrLabel.trim() || T('Home'), zoneId: this.zoneId, address: this.address.trim(), isDefault: this.addresses.length === 0 }).subscribe({ error: () => {} });
          }
          if (this.cartTimer) clearTimeout(this.cartTimer);
          // A card payment (or a provider that shows its own page) sends the customer there; otherwise the order page shows what to do next.
          if (t.payment?.status === 'AWAITING' && t.payment.redirectUrl) {
            window.location.assign(t.payment.redirectUrl);
            return;
          }
          this.router.navigate(['/shop/order', t.code]);
        },
        error: (e) => {
          this.busy = false;
          this.orderError = e?.error?.message ?? T('We could not send your order. Please try again.');
          setTimeout(() => document.getElementById('order-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
        },
      });
  }
}
