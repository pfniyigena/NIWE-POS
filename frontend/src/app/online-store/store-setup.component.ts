import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, StoreBanner, StorePageContent, PaymentProvider, StoreListing, StoreSettings, StoreZone, TextProvider } from '../core/api/online-store-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product } from '../core/models';
import { T } from '../core/i18n/t';
import { API_BASE } from '../core/constants';

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
        <div class="form-group mt-2" style="max-width:420px;"><label class="form-label">{{ 'Alert by e-mail when an order waits this many minutes (0 = never)' | translate }}</label><input class="form-control" name="alertMinutes" type="number" min="0" max="1440" [(ngModel)]="s.unansweredAlertMinutes" /></div>
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
        <h4 class="mt-3 mb-2">{{ 'Customer accounts' | translate }}</h4>
        <p class="text-muted">{{ 'Customers can sign in with an e-mailed code (or a texted one, when text messages are on) to see their orders, save their details and order again. Guests can still buy. Needs e-mail or text messages to be set up.' | translate }}</p>
        <label class="flex gap-1 mb-2" style="align-items:center;"><input type="checkbox" name="accounts" [(ngModel)]="s.accountsEnabled" /> {{ 'Customers can sign in to see their orders (optional, by e-mailed code)' | translate }}</label>
        <h4 class="mt-3 mb-2">{{ 'Abandoned-cart reminders' | translate }}</h4>
        <p class="text-muted">{{ 'When a visitor types an e-mail address at checkout and leaves without ordering, the shop reminds them about their cart by e-mail: once after 1 hour and once after 3 days. No discount is offered. Every e-mail has a link to stop the reminders, and nobody is reminded after they order. Needs e-mail to be set up (Settings > Email).' | translate }}</p>
        <label class="flex gap-1 mb-2" style="align-items:center;"><input type="checkbox" name="cartReminders" [(ngModel)]="s.cartRemindersEnabled" /> {{ 'Send reminders for abandoned carts' | translate }} <a routerLink="/admin/online-store/carts" style="margin-left:0.5rem;">{{ 'See the carts' | translate }}</a></label>
        <h4 class="mt-3 mb-2">{{ 'Text messages (SMS or WhatsApp)' | translate }}</h4>
        <p class="text-muted">{{ 'Text customers about their order (only those who ticked the box at checkout), text sign-in codes, and text the shop owner about new orders. Nothing is sent until you choose a provider.' | translate }}</p>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:260px;">
            <label class="form-label">{{ 'Text provider' | translate }}</label>
            <select class="form-select" name="textProvider" [(ngModel)]="s.textProvider">
              <option [ngValue]="null">{{ '— none (text messages are off) —' | translate }}</option>
              <option *ngFor="let p of textProviders" [ngValue]="p.code" [disabled]="!p.available">{{ p.label }}{{ p.available ? '' : (' (' + ('not set up' | translate) + ')') }}</option>
            </select>
          </div>
          <div class="form-group"><label class="form-label">{{ 'Send as' | translate }}</label>
            <select class="form-select" style="width:140px;" name="textChannel" [(ngModel)]="s.textChannel"><option value="SMS">SMS</option><option value="WHATSAPP">WhatsApp</option></select></div>
          <div class="form-group"><label class="form-label">{{ 'Country code' | translate }}</label><input class="form-control" style="width:110px;" name="textCc" maxlength="4" inputmode="numeric" [(ngModel)]="s.textCountryCode" /></div>
          <div class="form-group"><label class="form-label">{{ 'Most texts per day (0 = no limit)' | translate }}</label><input class="form-control" type="number" min="0" max="100000" style="width:160px;" name="textLimit" [(ngModel)]="s.textDailyLimit" /></div>
          <div class="form-group"><label class="form-label">{{ 'Text the owner at (empty = nobody)' | translate }}</label><input class="form-control" style="width:200px;" name="notifyPhone" maxlength="40" inputmode="tel" [(ngModel)]="s.notifyPhone" /></div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">{{ 'The country code is put in front of numbers that start with 0, for example 250 for Rwanda. The daily limit keeps the cost, and any misuse, in check.' | translate }} <a routerLink="/admin/online-store/texts">{{ 'Text messages sent' | translate }}</a></p>
        <h4 class="mt-3 mb-2">{{ 'Returns' | translate }}</h4>
        <div class="form-group" style="max-width:420px;"><label class="form-label">{{ 'Customers can return goods up to this many days after the order is completed (0 = no returns through the shop)' | translate }}</label><input class="form-control" name="returnDays" type="number" min="0" max="365" style="width:140px;" [(ngModel)]="s.returnWindowDays" /></div>
        <p class="text-muted" style="font-size:0.85rem;">{{ 'The customer asks from their order page with the phone number of the order; you approve or refuse on Online Store > Returns.' | translate }}</p>
        <h4 class="mt-3 mb-2">{{ 'Online payments' | translate }}</h4>
        <p class="text-muted">{{ 'Let customers pay at checkout (mobile money or card). Their order reaches the shop only once the payment is confirmed. Set the currency of the business to the currency you charge in.' | translate }}</p>
        <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="onlinePay" [(ngModel)]="s.onlinePaymentsEnabled" /> {{ 'Customers can pay online' | translate }}</label>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap;" *ngIf="s.onlinePaymentsEnabled || s.paymentProvider">
          <div class="form-group" style="min-width:260px;">
            <label class="form-label">{{ 'Payment provider' | translate }}</label>
            <select class="form-select" name="provider" [(ngModel)]="s.paymentProvider">
              <option [ngValue]="null">{{ '— none —' | translate }}</option>
              <option *ngFor="let p of providers" [ngValue]="p.code" [disabled]="!p.available">{{ p.label }}{{ p.available ? '' : (' (' + ('not set up' | translate) + ')') }}</option>
            </select>
          </div>
          <div class="form-group"><label class="form-label">{{ 'Minutes the customer has to pay' | translate }}</label><input class="form-control" type="number" min="5" max="1440" style="width:140px;" name="expiry" [(ngModel)]="s.paymentExpiryMinutes" /></div>
        </div>
        <p class="text-muted mt-1" *ngIf="s.onlinePaymentsEnabled" style="font-size:0.85rem;">{{ 'Tell the provider to send payment results to {p0}/public/store/payments/CODE/webhook, with CODE the provider code (for example SIMULATED).' | translate: { p0: apiBase } }}</p>
        <div class="mt-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save settings' | translate }}</button></div>
      </form>

      <form class="card mb-3 front-page-card" style="padding:1rem;" *ngIf="pc" (ngSubmit)="savePageContent()">
        <h4 class="mb-2">{{ 'Front page' | translate }}</h4>
        <p class="text-muted">{{ 'The wording of the shop’s first page. Everything is optional: a part you leave empty is not shown. Which products appear in the Featured, Best-selling, Special offers and New arrivals rows is set in the products table below.' | translate }}</p>
        <div class="form-group"><label class="form-label">{{ 'Announcement bar (a short message above the page)' | translate }}</label>
          <input class="form-control fp-announcement" maxlength="200" name="fpAnn" [(ngModel)]="pc.announcementText" /></div>
        <div class="form-group"><label class="form-label">{{ 'Announcement link (optional: / for a page of the shop, or https://)' | translate }}</label>
          <input class="form-control" maxlength="300" name="fpAnnLink" [(ngModel)]="pc.announcementLink" /></div>
        <div class="form-group"><label class="form-label">{{ 'Banner title' | translate }}</label>
          <input class="form-control fp-hero-title" maxlength="120" name="fpTitle" [(ngModel)]="pc.heroTitle" [placeholder]="'Empty = the shop name' | translate" /></div>
        <div class="form-group"><label class="form-label">{{ 'Banner text' | translate }}</label>
          <input class="form-control" maxlength="300" name="fpSub" [(ngModel)]="pc.heroSubtitle" [placeholder]="'Empty = the introduction above' | translate" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:200px;"><label class="form-label">{{ 'Banner button text' | translate }}</label>
            <input class="form-control" maxlength="40" name="fpBtn" [(ngModel)]="pc.heroButtonText" [placeholder]="'Shop now' | translate" /></div>
          <div class="form-group" style="flex:1; min-width:240px;"><label class="form-label">{{ 'Banner button link (empty = down to the products)' | translate }}</label>
            <input class="form-control" maxlength="300" name="fpBtnLink" [(ngModel)]="pc.heroButtonLink" /></div>
        </div>
        <div class="form-group fp-banners">
          <label class="form-label">{{ 'Banner pictures' | translate }}</label>
          <p class="text-muted" style="font-size:0.85rem; margin:0 0 0.4rem;">{{ 'Upload up to 8 pictures (JPEG, PNG, WebP or GIF, at most 8 MB each). They slide one after the other on the front page, in the order shown here. A wide picture works best (for example 1600 × 600).' | translate }}</p>
          <div style="display:flex; flex-wrap:wrap; gap:0.6rem; margin-bottom:0.5rem;">
            <div class="fp-banner" *ngFor="let b of banners; let i = index" style="width:200px; border:1px solid #e5e7eb; border-radius:8px; padding:0.4rem;">
              <img [src]="bannerThumb(b)" [alt]="b.fileName" style="width:100%; height:90px; object-fit:cover; border-radius:6px; background:#f3f4f6;" />
              <div class="text-muted" style="font-size:0.75rem; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" [title]="b.fileName">{{ i + 1 }}. {{ b.fileName }}</div>
              <div class="flex gap-1 mt-1">
                <button type="button" class="btn btn-sm btn-outline-secondary fp-banner-up" [disabled]="i === 0 || bannerBusy" [attr.aria-label]="'Move earlier' | translate" (click)="moveBanner(b, 'up')">←</button>
                <button type="button" class="btn btn-sm btn-outline-secondary fp-banner-down" [disabled]="i === banners.length - 1 || bannerBusy" [attr.aria-label]="'Move later' | translate" (click)="moveBanner(b, 'down')">→</button>
                <button type="button" class="btn btn-sm btn-outline-danger fp-banner-del" [disabled]="bannerBusy" (click)="deleteBanner(b)">{{ 'Remove' | translate }}</button>
              </div>
            </div>
          </div>
          <p class="text-muted" *ngIf="!banners.length" style="margin:0 0 0.4rem;">{{ 'No banner picture yet: the banner shows a plain colour.' | translate }}</p>
          <input type="file" class="form-control fp-banner-file" accept="image/jpeg,image/png,image/webp,image/gif" multiple [disabled]="bannerBusy || banners.length >= 8" (change)="pickBanners($event)" />
          <div class="text-muted" *ngIf="bannerBusy" style="font-size:0.85rem;">{{ 'Uploading…' | translate }}</div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'About the shop (footer)' | translate }}</label>
          <textarea class="form-control" rows="2" maxlength="400" name="fpAbout" [(ngModel)]="pc.aboutText"></textarea></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:220px;"><label class="form-label">{{ 'Contact e-mail (footer)' | translate }}</label>
            <input class="form-control" type="email" maxlength="160" name="fpMail" [(ngModel)]="pc.contactEmail" /></div>
          <div class="form-group" style="flex:1; min-width:220px;"><label class="form-label">{{ 'Opening hours (footer)' | translate }}</label>
            <input class="form-control" maxlength="200" name="fpHours" [(ngModel)]="pc.openingHours" /></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Footer links: one per line, written Label|address (at most 8). The address starts with / (a page of this site) or https://' | translate }}</label>
          <textarea class="form-control fp-links" rows="4" maxlength="1200" name="fpLinks" [(ngModel)]="pc.footerLinks" placeholder="Delivery|https://example.com/delivery"></textarea></div>
        <div class="form-group"><label class="form-label">{{ 'Social links: one per line, written Label|address (at most 6)' | translate }}</label>
          <textarea class="form-control" rows="3" maxlength="800" name="fpSocial" [(ngModel)]="pc.socialLinks" placeholder="Facebook|https://facebook.com/yourshop"></textarea></div>
        <div class="flex gap-2"><button class="btn btn-primary fp-save" type="submit" [disabled]="busy">{{ 'Save the front page' | translate }}</button></div>
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
        <div class="flex justify-between align-center mt-3 mb-1 listing-tools" *ngIf="listings.length" style="flex-wrap:wrap; gap:0.5rem;">
          <input class="form-control listing-filter" style="width:260px;" [placeholder]="'Filter the products on the shop' | translate" [ngModel]="listFilter" (ngModelChange)="filterChanged($event)" name="listFilter" />
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Showing {p0}–{p1} of {p2}' | translate: { p0: firstShown(), p1: lastShown(), p2: filteredListings().length } }}</span>
        </div>
        <div class="lst-rows mt-2" *ngIf="listings.length">
          <div class="lst-row" *ngFor="let l of pageListings()" style="border:1px solid #e5e7eb; border-radius:8px; padding:0.75rem; margin-bottom:0.6rem;">
            <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem; align-items:flex-start;">
              <div style="min-width:0;">
                <strong class="lst-name">{{ l.productName }}</strong>
                <div class="text-muted" style="font-size:0.8rem;">
                  {{ 'Price' | translate }}: {{ l.unitPrice | number: '1.0-2' }} · {{ 'Stock' | translate }}: {{ stockLabel(l.stock) | translate }}<span *ngIf="!l.hasImage"> · {{ 'No picture yet' | translate }}</span>
                </div>
                <div class="lst-stock" style="font-size:0.8rem; margin-top:0.15rem;" [style.color]="l.stock === 'SOLD_OUT' ? '#b91c1c' : '#4b5563'">
                  <ng-container *ngIf="l.stockInfo.tracked; else untracked">{{ 'In sellable warehouse locations {p0} − reserved on confirmed orders {p1} − shop buffer {p2} = sellable now {p3}' | translate: { p0: l.stockInfo.inWarehouses, p1: l.stockInfo.reserved, p2: l.stockInfo.buffer, p3: l.stockInfo.sellable } }}</ng-container>
                  <ng-template #untracked>{{ 'Stock is not tracked for this product: the shop always shows it as in stock.' | translate }}</ng-template>
                </div>
              </div>
              <div style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveListing(l)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="removeListing(l)">{{ 'Remove' | translate }}</button></div>
            </div>
            <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(160px, 1fr)); gap:0.6rem; margin-top:0.6rem;">
              <div style="grid-column: span 2; min-width:0;"><label class="form-label" style="font-size:0.8rem;">{{ 'Title on the shop' | translate }}</label>
                <input class="form-control" style="width:100%;" maxlength="160" [name]="'t' + l.id" [(ngModel)]="l.webTitle" [placeholder]="l.productName" /></div>
              <div><label class="form-label" style="font-size:0.8rem;">{{ 'Online price' | translate }}</label>
                <input class="form-control" type="number" min="0" style="width:100%;" [name]="'p' + l.id" [(ngModel)]="l.onlinePrice" /></div>
              <div><label class="form-label" style="font-size:0.8rem;">{{ 'Order' | translate }}</label>
                <input class="form-control" type="number" style="width:100%;" [name]="'o' + l.id" [(ngModel)]="l.sortOrder" /></div>
            </div>
            <div style="display:flex; flex-wrap:wrap; gap:0.4rem 1.2rem; margin-top:0.6rem;">
              <label style="display:flex; align-items:center; gap:0.35rem;"><input type="checkbox" [name]="'v' + l.id" [(ngModel)]="l.listed" /> {{ 'Shown' | translate }}</label>
              <label style="display:flex; align-items:center; gap:0.35rem;" title="{{ 'Featured products' | translate }}"><input type="checkbox" class="tag-featured" [name]="'tf' + l.id" [(ngModel)]="l.featured" /> {{ 'Featured' | translate }}</label>
              <label style="display:flex; align-items:center; gap:0.35rem;" title="{{ 'Best-selling products' | translate }}"><input type="checkbox" class="tag-best" [name]="'tb' + l.id" [(ngModel)]="l.bestSeller" /> {{ 'Best seller' | translate }}</label>
              <label style="display:flex; align-items:center; gap:0.35rem;" title="{{ 'Special offers and discounts' | translate }}"><input type="checkbox" class="tag-offer" [name]="'to' + l.id" [(ngModel)]="l.specialOffer" /> {{ 'Offer' | translate }}</label>
              <label style="display:flex; align-items:center; gap:0.35rem;" title="{{ 'New arrivals' | translate }}"><input type="checkbox" class="tag-new" [name]="'tn' + l.id" [(ngModel)]="l.newArrival" /> {{ 'New' | translate }}</label>
            </div>
          </div>
        </div>
        <p class="text-muted listing-none" *ngIf="listings.length && !filteredListings().length">{{ 'No product matches.' | translate }}</p>
        <nav class="flex justify-between align-center mt-2 listing-pager" *ngIf="listings.length && filteredListings().length" style="flex-wrap:wrap; gap:0.5rem;">
          <label class="flex gap-1" style="align-items:center; font-size:0.85rem;">{{ 'Per page' | translate }}
            <select class="form-select" style="width:auto;" name="listSize" [ngModel]="listSize" (ngModelChange)="sizeChanged($event)">
              <option *ngFor="let n of [10, 25, 50, 100]" [ngValue]="n">{{ n }}</option>
            </select>
          </label>
          <div class="flex gap-1" style="align-items:center;">
            <button class="btn btn-sm btn-outline-secondary listing-prev" type="button" [disabled]="listPage === 0" (click)="goListing(listPage - 1)">{{ 'Previous' | translate }}</button>
            <span style="font-size:0.85rem;">{{ 'Page {p0} of {p1}' | translate: { p0: listPage + 1, p1: listingPages() } }}</span>
            <button class="btn btn-sm btn-outline-secondary listing-next" type="button" [disabled]="listPage + 1 >= listingPages()" (click)="goListing(listPage + 1)">{{ 'Next' | translate }}</button>
          </div>
        </nav>
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
  /** The front page's wording. */
  pc: StorePageContent | null = null;
  /** The banner pictures of the front page, in the order they slide. */
  banners: StoreBanner[] = [];
  bannerBusy = false;
  /** The products table is paged on screen: what is typed in a row stays in the row while another page is shown. */
  listFilter = '';
  listSize = 10;
  listPage = 0;
  zones: StoreZone[] = [];
  serviceProducts: Product[] = [];
  found: Product[] = [];
  search = '';
  newZone: { name: string; fee: number; minOrder: number } = { name: '', fee: 0, minOrder: 0 };
  error: string | null = null;
  message: string | null = null;
  busy = false;

  providers: PaymentProvider[] = [];
  textProviders: TextProvider[] = [];
  apiBase = API_BASE;

  constructor(private api: OnlineStoreApiService, private catalog: CatalogApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => this.fail(e, 'Failed to load the settings') });
    this.api.providers().subscribe({ next: (p) => (this.providers = p), error: () => {} });
    this.api.textProviders().subscribe({ next: (p) => (this.textProviders = p), error: () => {} });
    this.api.pageContent().subscribe({ next: (c) => (this.pc = c), error: () => {} });
    this.api.banners().subscribe({ next: (b) => (this.banners = b), error: () => {} });
    this.loadListings();
    this.loadZones();
    this.catalog.searchProducts(null, null).subscribe({ next: (all) => (this.serviceProducts = all.filter((p) => (p as any).service && p.active)), error: () => {} });
  }

  bannerThumb(b: StoreBanner): string {
    return this.api.imageUrl(b.url) + '?w=300';
  }

  /** Uploads the chosen pictures one after the other; one that is refused is reported and the rest carry on. */
  pickBanners(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    this.bannerBusy = true;
    this.error = null;
    this.message = null;
    const room = Math.max(0, 8 - this.banners.length);
    const queue = files.slice(0, room);
    const next = (): void => {
      const f = queue.shift();
      if (!f) {
        this.bannerBusy = false;
        if (!this.error) this.ok('Banner pictures saved');
        return;
      }
      this.api.uploadBanner(f).subscribe({
        next: (b) => { this.banners = [...this.banners, b]; next(); },
        error: (e) => { this.error = (e?.error?.message ?? T('Could not add the picture')) + ' (' + f.name + ')'; next(); },
      });
    };
    next();
  }

  moveBanner(b: StoreBanner, direction: 'up' | 'down'): void {
    this.bannerBusy = true;
    this.api.moveBanner(b.id, direction).subscribe({
      next: (list) => { this.banners = list; this.bannerBusy = false; },
      error: (e) => { this.bannerBusy = false; this.fail(e, 'Could not move the picture'); },
    });
  }

  deleteBanner(b: StoreBanner): void {
    if (!confirm(T('Remove this banner picture?'))) return;
    this.bannerBusy = true;
    this.api.deleteBanner(b.id).subscribe({
      next: () => { this.banners = this.banners.filter((x) => x.id !== b.id); this.bannerBusy = false; this.ok('Removed'); },
      error: (e) => { this.bannerBusy = false; this.fail(e, 'Could not remove the picture'); },
    });
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

  savePageContent(): void {
    if (!this.pc) return;
    this.busy = true;
    this.api.savePageContent(this.pc).subscribe({
      next: (c) => { this.pc = c; this.busy = false; this.ok('Front page saved'); },
      error: (e) => { this.busy = false; this.fail(e, 'Could not save the front page'); },
    });
  }

  loadListings(): void {
    this.api.listings().subscribe({ next: (l) => { this.listings = l; this.listPage = Math.min(this.listPage, this.listingPages() - 1); }, error: (e) => this.fail(e, 'Failed to load the products') });
  }

  filteredListings(): StoreListing[] {
    const q = this.listFilter.trim().toLowerCase();
    if (!q) return this.listings;
    return this.listings.filter((l) => l.productName.toLowerCase().includes(q) || (l.webTitle ?? '').toLowerCase().includes(q) || (l.sku ?? '').toLowerCase().includes(q) || (l.category ?? '').toLowerCase().includes(q));
  }

  listingPages(): number {
    return Math.max(1, Math.ceil(this.filteredListings().length / this.listSize));
  }

  pageListings(): StoreListing[] {
    return this.filteredListings().slice(this.listPage * this.listSize, (this.listPage + 1) * this.listSize);
  }

  firstShown(): number {
    return this.filteredListings().length === 0 ? 0 : this.listPage * this.listSize + 1;
  }

  lastShown(): number {
    return Math.min(this.filteredListings().length, (this.listPage + 1) * this.listSize);
  }

  filterChanged(v: string): void {
    this.listFilter = v ?? '';
    this.listPage = 0;
  }

  sizeChanged(n: number): void {
    this.listSize = Number(n) || 10;
    this.listPage = 0;
  }

  goListing(p: number): void {
    this.listPage = Math.max(0, Math.min(this.listingPages() - 1, p));
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
    this.api.saveListing({ productId: l.productId, featured: l.featured, bestSeller: l.bestSeller, specialOffer: l.specialOffer, newArrival: l.newArrival, listed: l.listed, webTitle: l.webTitle, webDescription: l.webDescription, onlinePrice: l.onlinePrice === null || (l.onlinePrice as any) === '' ? null : l.onlinePrice, sortOrder: l.sortOrder })
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
