import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccountProfile, CouponCheckResult, OnlineStoreApiService, Reorder, Shop, ShopHome, ShopPage, ShopProduct, shopToken } from '../core/api/online-store-api.service';
import { PublicSettingsApiService } from '../core/api/public-settings-api.service';
import { ShopAccountComponent } from './shop-account.component';
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
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShopAccountComponent, ShopMoneyPipe],
  template: `
    <div class="shop-announce" *ngIf="shop?.page?.announcementText" style="background:#1d4ed8; color:#fff; text-align:center; padding:0.45rem 0.75rem; font-size:0.9rem;">
      <a *ngIf="shop?.page?.announcementLink; else announceText" class="shop-announce-link" style="color:#fff; text-decoration:underline; cursor:pointer;" (click)="openLink(shop?.page?.announcementLink)">{{ shop?.page?.announcementText }}</a>
      <ng-template #announceText>{{ shop?.page?.announcementText }}</ng-template>
    </div>
    <div class="shop-header" *ngIf="shop" style="background:#fff; border-bottom:1px solid #e5e7eb; position:sticky; top:0; z-index:20;">
      <div style="max-width:1100px; margin:0 auto; padding:0.6rem 0.75rem; display:flex; align-items:center; gap:0.75rem; flex-wrap:wrap;">
        <a class="shop-brand" (click)="goHome()" style="display:flex; align-items:center; gap:0.5rem; cursor:pointer; color:inherit; text-decoration:none;">
          <img *ngIf="logo" [src]="logo" alt="" class="shop-logo" style="height:36px; max-width:120px; object-fit:contain;" />
          <strong class="shop-name" style="font-size:1.2rem;">{{ shop.name }}</strong>
        </a>
        <input *ngIf="step === 'shop' && !showAccount" class="form-control shop-search" style="flex:1; min-width:150px; max-width:420px;" [placeholder]="'Search' | translate" [ngModel]="q" (ngModelChange)="searchChanged($event)" name="q" />
        <div style="margin-left:auto; display:flex; gap:0.5rem; align-items:center; flex-wrap:wrap;">
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
        <app-shop-account *ngIf="showAccount && shop.accounts" [profile]="account" [currency]="cur" [textChannel]="shop.textChannel" (profileChange)="accountChanged($event)" (reordered)="reordered($event)" (closed)="showAccount = false"></app-shop-account>

        <ng-container *ngIf="step === 'shop' && !showAccount">
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

          <section class="shop-row mt-4" *ngFor="let r of rows" [attr.data-row]="r.key">
            <h3>{{ r.title | translate }}</h3>
            <div class="shop-carousel" *ngIf="r.key === 'featured'; else plainRow" style="position:relative;" (mouseenter)="railHold = true" (mouseleave)="railHold = false" (touchstart)="railHold = true" (touchend)="railHold = false" (focusin)="railHold = true" (focusout)="railHold = false">
              <button type="button" class="shop-car-prev" [attr.aria-label]="'Previous products' | translate" (click)="railGo(-1)" style="position:absolute; left:-6px; top:45%; z-index:2; width:36px; height:36px; border-radius:50%; border:1px solid #d1d5db; background:#fff; box-shadow:0 1px 4px rgba(0,0,0,0.2); font-size:1.3rem; cursor:pointer; line-height:1;">‹</button>
              <div #featRail class="car-rail" style="display:flex; gap:0.75rem; overflow-x:auto; scroll-snap-type:x mandatory; scroll-behavior:smooth; padding:0.25rem 0.1rem 0.5rem; scrollbar-width:none;">
                <div class="car-item" *ngFor="let p of r.items" style="flex:0 0 210px; scroll-snap-align:start;"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></div>
              </div>
              <button type="button" class="shop-car-next" [attr.aria-label]="'Next products' | translate" (click)="railGo(1)" style="position:absolute; right:-6px; top:45%; z-index:2; width:36px; height:36px; border-radius:50%; border:1px solid #d1d5db; background:#fff; box-shadow:0 1px 4px rgba(0,0,0,0.2); font-size:1.3rem; cursor:pointer; line-height:1;">›</button>
            </div>
            <ng-template #plainRow>
              <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
                <ng-container *ngFor="let p of r.items"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></ng-container>
              </div>
            </ng-template>
          </section>
          <h3 class="mt-4" id="shop-all">{{ 'All products' | translate }}</h3>
        </ng-container>

        <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:center;">
          <button class="btn btn-sm" [class.btn-primary]="category === ''" [class.btn-outline-secondary]="category !== ''" (click)="pickCategory('')">{{ 'All' | translate }}</button>
          <button *ngFor="let c of shop.categories" class="btn btn-sm" [class.btn-primary]="category === c" [class.btn-outline-secondary]="category !== c" (click)="pickCategory(c)">{{ c }}</button>
        </div>

        <div id="shop-top"></div>
        <div class="shop-grid mt-3" [style.opacity]="loading ? 0.5 : 1" style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
          <ng-container *ngFor="let p of items"><ng-container *ngTemplateOutlet="card; context: { $implicit: p }"></ng-container></ng-container>
        </div>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && !q.trim() && !category">{{ 'Nothing is for sale yet. Please come back soon.' | translate }}</p>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && (q.trim() || category)">{{ 'No products match.' | translate }}</p>

        <nav class="shop-pager flex justify-between align-center mt-3" *ngIf="totalPages > 1" style="flex-wrap:wrap; gap:0.5rem;">
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Showing {p0}–{p1} of {p2}' | translate: { p0: firstShown(), p1: lastShown(), p2: total } }}</span>
          <div class="flex gap-1" style="align-items:center;">
            <button class="btn btn-sm btn-outline-secondary shop-prev" [disabled]="page === 0 || loading" (click)="go(page - 1)">{{ 'Previous' | translate }}</button>
            <span style="font-size:0.85rem;">{{ 'Page {p0} of {p1}' | translate: { p0: page + 1, p1: totalPages } }}</span>
            <button class="btn btn-sm btn-outline-secondary shop-next" [disabled]="page + 1 >= totalPages || loading" (click)="go(page + 1)">{{ 'Next' | translate }}</button>
          </div>
        </nav>

        <ng-container *ngIf="landing">
          <section class="shop-reviews mt-4">
            <h3>{{ 'Customer reviews' | translate }}</h3>
            <div *ngIf="home && home.reviews.length > 0; else noReviews">
              <div class="text-muted shop-reviews-avg" *ngIf="home.ratingAverage !== null" style="margin-bottom:0.5rem;"><span style="color:#d97706;">{{ stars(home.ratingAverage) }}</span> {{ home.ratingAverage }} / 5 · {{ '{p0} reviews' | translate: { p0: home.ratingCount } }}</div>
              <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(260px,1fr)); gap:0.75rem;">
                <div class="card shop-review" *ngFor="let r of home.reviews" style="padding:0.75rem;">
                  <div style="color:#d97706;">{{ stars(r.rating) }}</div>
                  <div class="mt-1">{{ r.text }}</div>
                  <div class="text-muted mt-1" style="font-size:0.8rem;">{{ r.reviewerName }} · {{ r.productName }}</div>
                </div>
              </div>
            </div>
            <ng-template #noReviews><p class="text-muted shop-reviews-none">{{ 'No reviews yet. When your order is completed, you can review the products from your order page.' | translate }}</p></ng-template>
          </section>

          <section class="shop-newsletter card mt-4" style="padding:1.25rem; text-align:center;">
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
            <div class="form-group"><label class="form-label">{{ 'E-mail (optional)' | translate }}</label><input class="form-control" maxlength="160" inputmode="email" name="cemail" [(ngModel)]="email" /></div>
          </div>
          <ng-container *ngIf="method === 'DELIVERY'">
            <div class="form-group" *ngIf="shop.zones.length"><label class="form-label">{{ 'Delivery area' | translate }}</label>
              <select class="form-select" style="max-width:320px;" name="zone" [(ngModel)]="zoneId" (ngModelChange)="recheckCoupon()">
                <option [ngValue]="null" disabled>{{ 'Choose your area…' | translate }}</option>
                <option *ngFor="let z of shop.zones" [ngValue]="z.id">{{ z.name }} ({{ z.fee | shopMoney: cur }})</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Delivery address' | translate }}</label><input class="form-control shop-address" maxlength="240" name="caddr" [(ngModel)]="address" /></div>
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
            <div class="form-group mt-1" *ngIf="payment === 'MOBILE_MONEY'">
              <label class="form-label">{{ 'Mobile money number to pay from' | translate }}</label>
              <input class="form-control shop-pay-phone" style="max-width:260px;" maxlength="40" inputmode="tel" name="payphone" [(ngModel)]="payPhone" [placeholder]="phone" />
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
          <hr />
          <div class="flex justify-between"><span>{{ 'Items' | translate }}</span><span>{{ subtotal() | shopMoney: cur }}</span></div>
          <div class="flex justify-between shop-discount" *ngIf="discount() > 0" style="color:#15803d;"><span>{{ 'Discount ({p0})' | translate: { p0: coupon?.code } }}</span><span>−{{ discount() | shopMoney: cur }}</span></div>
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

      <ng-template #card let-p>
          <div class="card shop-product" style="padding:0.6rem;">
            <img *ngIf="p.imageUrl" [src]="img(p)" [alt]="p.name" loading="lazy" style="width:100%; height:130px; object-fit:cover; border-radius:6px;" />
            <div *ngIf="!p.imageUrl" style="width:100%; height:130px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2rem;">🛍️</div>
            <div class="mt-1"><strong>{{ p.name }}</strong><span class="shop-offer-badge" *ngIf="p.wasPrice" style="margin-left:0.3rem; background:#dc2626; color:#fff; border-radius:4px; padding:0 0.35rem; font-size:0.7rem;">{{ 'Offer' | translate }}</span></div>
            <div class="shop-rating" *ngIf="p.ratingCount > 0" style="color:#d97706; font-size:0.85rem;" [attr.aria-label]="p.rating + ' / 5'">{{ stars(p.rating) }} <span class="text-muted">({{ p.ratingCount }})</span></div>
            <div class="text-muted" style="font-size:0.8rem; min-height:2.2em;" *ngIf="p.description">{{ p.description | slice: 0 : 80 }}</div>
            <div class="flex justify-between align-center mt-1">
              <span><strong>{{ p.price | shopMoney: cur }}</strong><s class="text-muted shop-was" *ngIf="p.wasPrice" style="font-size:0.8rem; margin-left:0.3rem;">{{ p.wasPrice | shopMoney: cur }}</s></span>
              <span *ngIf="p.stock === 'LOW'" class="text-muted" style="font-size:0.75rem; color:#b45309;">{{ 'Few left' | translate }}</span>
              <span *ngIf="p.stock === 'SOLD_OUT'" class="text-muted" style="font-size:0.75rem;">{{ 'Sold out' | translate }}</span>
            </div>
            <div class="flex gap-1 mt-1" *ngIf="qty(p) > 0; else addBtn" style="align-items:center; justify-content:space-between;">
              <button class="btn btn-sm btn-outline-secondary" (click)="change(p, -1)">−</button>
              <span>{{ qty(p) }}</span>
              <button class="btn btn-sm btn-outline-secondary" (click)="change(p, 1)" [disabled]="qty(p) >= 20">+</button>
            </div>
            <ng-template #addBtn><button class="btn btn-sm btn-primary mt-1 shop-add" style="width:100%;" [disabled]="p.stock === 'SOLD_OUT' || !shop?.open" (click)="change(p, 1)">{{ 'Add to cart' | translate }}</button></ng-template>
          </div>
      </ng-template>
  `,
  styles: ['.car-rail::-webkit-scrollbar { display: none; }'],
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
  /** What could not be put back in the cart when ordering again. */
  reorderNote: string | null = null;

  /** The front page rows chosen by staff, the banner pictures and the approved reviews (null until loaded, or when they cannot be). */
  home: ShopHome | null = null;
  logo: string | null = null;
  trackCode = '';
  nlEmail = '';
  nlWebsite = '';
  nlBusy = false;
  nlDone: string | null = null;
  nlError: string | null = null;

  constructor(private api: OnlineStoreApiService, private router: Router, public lang: LanguageService, private publicSettings: PublicSettingsApiService) {}

  /** The landing sections (hero, rows, reviews, newsletter) show when the visitor is not searching or filtering. */
  get landing(): boolean {
    return this.step === 'shop' && !this.showAccount && !this.q.trim() && !this.category;
  }

  get rows(): { key: string; title: string; items: ShopProduct[] }[] {
    const h = this.home;
    if (!h) return [];
    return [
      { key: 'featured', title: 'Featured products', items: h.featured },
      { key: 'best', title: 'Best-selling products', items: h.bestSellers },
      { key: 'offers', title: 'Special offers and discounts', items: h.offers },
      { key: 'new', title: 'New arrivals', items: h.newArrivals },
    ].filter((r) => r.items.length > 0);
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

  // ---- the banner slideshow and the Featured products rail: both move by themselves, and stop while the visitor points at them or the tab is hidden
  heroIndex = 0;
  heroHold = false;
  railHold = false;
  @ViewChild('featRail') featRail?: ElementRef<HTMLElement>;
  private heroTimer: ReturnType<typeof setInterval> | null = null;
  private railTimer: ReturnType<typeof setInterval> | null = null;
  private touchX = 0;

  private get calm(): boolean {
    return typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private startMotion(): void {
    if (this.calm) return;
    if (!this.heroTimer) this.heroTimer = setInterval(() => { if (!this.heroHold && !document.hidden && this.bannerUrls.length > 1) this.heroGo(1); }, 5000);
    if (!this.railTimer) this.railTimer = setInterval(() => { if (!this.railHold && !document.hidden) this.railGo(1); }, 3500);
  }

  ngOnDestroy(): void {
    if (this.heroTimer) clearInterval(this.heroTimer);
    if (this.railTimer) clearInterval(this.railTimer);
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

  /** One screen of Featured products to the left or right; on its own, the rail goes back to the start after the last product. */
  railGo(dir: number): void {
    const el = this.featRail?.nativeElement;
    if (!el) return;
    const step = Math.max(200, el.clientWidth * 0.8);
    const atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - 4;
    const atStart = el.scrollLeft <= 4;
    if (dir > 0 && atEnd) {
      el.scrollTo({ left: 0, behavior: 'smooth' });
    } else if (dir < 0 && atStart) {
      el.scrollTo({ left: el.scrollWidth, behavior: 'smooth' });
    } else {
      el.scrollBy({ left: dir * step, behavior: 'smooth' });
    }
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
    this.step = 'shop';
    this.showAccount = false;
    this.q = '';
    this.category = '';
    this.page = 0;
    this.load();
    window.scrollTo({ top: 0, behavior: 'smooth' });
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

  ngOnInit(): void {
    this.api.shop().subscribe({
      next: (s) => {
        this.shop = s;
        this.method = s.delivery ? 'DELIVERY' : 'PICKUP';
        if (!s.cash && s.payMethods.length > 0) this.payment = s.payMethods[0] as 'MOBILE_MONEY' | 'CARD';
        if (s.accounts && shopToken.get()) {
          // A stale token is simply dropped: the customer shops as a guest.
          this.api.accountMe().subscribe({ next: (p) => this.accountChanged(p), error: () => shopToken.clear() });
        }
        this.load();
        this.api.home().subscribe({ next: (h) => { this.home = h; this.startMotion(); }, error: () => (this.home = null) });
        this.publicSettings.branding().subscribe({ next: (b) => (this.logo = b.logoDataUrl || null), error: () => {} });
      },
      error: (e) => (this.error = e?.error?.message ?? T('The shop is not available right now.')),
    });
  }

  /** The customer signed in, saved their details, or signed out. The order form is filled from the account, never over what was already typed. */
  accountChanged(p: AccountProfile | null): void {
    this.account = p;
    if (!p) return;
    if (!this.name.trim() && p.name) this.name = p.name;
    if (!this.phone.trim() && p.phone) this.phone = p.phone;
    if (!this.email.trim() && p.email) this.email = p.email;
    if (!this.address.trim() && p.address) this.address = p.address;
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
    if (this.count() > 0) this.checkout();
    else this.step = 'shop';
  }

  /** Fetches the current page. A slower, older answer never replaces a newer one. */
  load(scrollToTop = false): void {
    const mine = ++this.seq;
    this.loading = true;
    this.api.products(this.page, this.size, this.q, this.category).subscribe({
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

  searchChanged(v: string): void {
    this.q = v ?? '';
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
    return this.subtotal() - this.discount() + this.feeShown();
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

  place(): void {
    this.orderError = null;
    this.busy = true;
    this.api
      .place({
        name: this.name, phone: this.phone, email: this.email || null, address: this.method === 'DELIVERY' ? this.address : null, method: this.method,
        zoneId: this.method === 'DELIVERY' ? this.zoneId : null, notes: this.notes || null, language: this.lang.current, website: this.website,
        lines: this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty })),
        payment: this.payment, payPhone: this.payment === 'MOBILE_MONEY' ? (this.payPhone || this.phone) : null,
        couponCode: this.couponCode.trim() || null,
        textUpdates: !!this.shop?.textChannel && this.textUpdates,
      })
      .subscribe({
        next: (t) => {
          this.busy = false;
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
