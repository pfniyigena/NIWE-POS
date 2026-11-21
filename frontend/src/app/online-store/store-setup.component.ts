import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { Driver, DriverAccess, OnlineStoreApiService, TaxOption, StoreBanner, StorePageContent, PaymentProvider, StoreListing, ListingDetails, ProductSpec, StoreSettings, StoreZone, TextProvider, DeliveryWindow, SlotSettings, ClosedDay, ShopStockSetup } from '../core/api/online-store-api.service';
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
          <div class="form-group"><label class="form-label">{{ 'WhatsApp number of the shop (empty = the phone above)' | translate }}</label><input class="form-control st-whatsapp" name="whatsappNumber" maxlength="40" inputmode="tel" [(ngModel)]="s.whatsappNumber" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Customers can send their order to this number on WhatsApp from the order page.' | translate }}</div></div>
          <div class="form-group" style="min-width:260px;"><label class="form-label">{{ 'Pick-up address' | translate }}</label><input class="form-control" name="pickupAddress" maxlength="240" [(ngModel)]="s.pickupAddress" /></div>
        </div>
        <div class="form-group mt-2" style="max-width:420px;"><label class="form-label">{{ 'Send new-order e-mails to (empty = business e-mail)' | translate }}</label><input class="form-control" name="notifyEmail" type="email" maxlength="160" [(ngModel)]="s.notifyEmail" /></div>
        <div class="form-group mt-2" style="max-width:420px;"><label class="form-label">{{ 'Alert by e-mail when an order waits this many minutes (0 = never)' | translate }}</label><input class="form-control" name="alertMinutes" type="number" min="0" max="1440" [(ngModel)]="s.unansweredAlertMinutes" /></div>
        <div class="form-group mt-2"><label class="form-label">{{ 'Welcome text' | translate }}</label><input class="form-control" name="intro" maxlength="300" [(ngModel)]="s.introText" /></div>
        <div class="form-group mt-2" *ngIf="s.paused"><label class="form-label">{{ 'Message shown while paused' | translate }}</label><input class="form-control" name="pauseMessage" maxlength="200" [(ngModel)]="s.pauseMessage" /></div>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Units kept back from every product' | translate }}</label><input class="form-control" type="number" min="0" max="1000" style="width:140px;" name="buffer" [(ngModel)]="s.stockBuffer" /></div>
          <div class="form-group"><label class="form-label">{{ 'Share kept for the till (%)' | translate }}</label><input class="form-control ss-till" type="number" min="0" max="100" style="width:140px;" name="tillShare" [(ngModel)]="s.tillSharePercent" /></div>
          <div class="form-group"><label class="form-label">{{ 'Do not sell goods with fewer days left than' | translate }}</label><input class="form-control ss-mindays" type="number" min="0" max="3650" style="width:140px;" name="minDays" [(ngModel)]="s.minDaysLeft" /></div>
          <div class="form-group"><label class="form-label">{{ 'Release unanswered orders after (hours, 0 = never)' | translate }}</label><input class="form-control ss-release" type="number" min="0" max="720" style="width:140px;" name="autoRelease" [(ngModel)]="s.autoReleaseHours" /></div>
          <div class="form-group"><label class="form-label">{{ 'Minimum order' | translate }}</label><input class="form-control" type="number" min="0" style="width:140px;" name="minOrder" [(ngModel)]="s.minOrder" /></div>
          <div class="form-group ss-shoploc" *ngIf="stockSetup?.multiWarehouse">
            <label class="form-label">{{ 'Stock the shop sells from' | translate }}</label>
            <select class="form-select ss-shoploc-select" style="width:280px;" name="shopLoc" [ngModel]="stockSetup!.locationId" (ngModelChange)="saveShopStock($event)">
              <option [ngValue]="null">{{ 'Automatic: every Sales location outside the Main warehouse' | translate }}</option>
              <option *ngFor="let o of stockSetup!.options" [ngValue]="o.id">{{ o.label }}</option>
            </select>
            <div class="text-muted ss-shoploc-none" style="font-size:0.85rem; max-width:34rem;" *ngIf="stockSetup!.options.length === 0">{{ 'No Sales location outside the Main warehouse yet. Make one in Inventory > Warehouses, or the shop has nothing to sell.' | translate }}</div>
            <div class="text-muted" style="font-size:0.85rem; max-width:34rem;">{{ 'Only the stock of this location is offered in the shop, and deliveries leave from it. Stock in the Main warehouse, Receiving or Storage locations is not offered.' | translate }}</div>
          </div>
          <div class="form-group"><label class="form-label">{{ 'Free delivery from' | translate }}</label><input class="form-control" type="number" min="0" style="width:140px;" name="freeFrom" [(ngModel)]="s.freeDeliveryOver" [placeholder]="'never' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Orders waiting per phone' | translate }}</label><input class="form-control" type="number" min="1" max="50" style="width:140px;" name="pendingPhone" [(ngModel)]="s.maxPendingPerPhone" /></div>
          <div class="form-group"><label class="form-label">{{ 'Orders per connection per hour' | translate }}</label><input class="form-control" type="number" min="1" max="1000" style="width:140px;" name="perIp" [(ngModel)]="s.maxPerIpPerHour" /></div>
        </div>
        <p class="text-muted ss-stock-note" style="font-size:0.85rem; max-width:44rem; margin:0.4rem 0 0;">{{ 'A shop order holds its units from the moment it is placed, until staff accept it (then the sale order keeps them) or it is cancelled. An order nobody answers is cancelled after the hours above and the customer is told, unless it was paid online: that one waits for staff. Batches that are expired, or have fewer days left than the setting above, are not offered. A product can have its own buffer in the list of products below; the share kept for the till is taken from what is available, rounded up.' | translate }}</p>
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
        <h4 class="mt-3 mb-2">{{ 'Loyalty points' | translate }}</h4>
        <p class="text-muted">{{ 'Customers who are signed in earn points when an order is completed (a share of what they paid for the goods) and spend them at checkout: 1 point takes 1 off, up to the share of the goods you set. Points do not expire. They belong to shop accounts and are separate from the loyalty programme at the till. Needs Customer accounts to be on.' | translate }}</p>
        <label class="flex gap-1 mb-2" style="align-items:center;"><input type="checkbox" name="loyalty" [(ngModel)]="s.loyaltyEnabled" /> {{ 'Customers earn and spend loyalty points' | translate }} <a routerLink="/admin/online-store/loyalty" style="margin-left:0.5rem;">{{ 'See the points' | translate }}</a></label>
        <div class="flex gap-3" style="flex-wrap:wrap;" *ngIf="s.loyaltyEnabled">
          <div class="form-group"><label class="form-label">{{ 'Points earned per 100 paid for the goods' | translate }}</label><input class="form-control" type="number" min="1" max="100" style="max-width:140px;" name="loyaltyPer100" [(ngModel)]="s.loyaltyPointsPer100" /></div>
          <div class="form-group"><label class="form-label">{{ 'Points may pay at most (% of the goods)' | translate }}</label><input class="form-control" type="number" min="0" max="100" style="max-width:140px;" name="loyaltyMax" [(ngModel)]="s.loyaltyMaxPercent" /></div>
        </div>
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
            <label class="form-label">{{ 'Payment providers' | translate }}</label>
            <label *ngFor="let p of providers" class="flex gap-1 st-provider" style="align-items:center;">
              <input type="checkbox" [checked]="hasProvider(p.code)" [disabled]="!p.available && !hasProvider(p.code)" (change)="toggleProvider(p.code, $any($event.target).checked)" [attr.data-provider]="p.code" />
              {{ p.label }}{{ p.available ? '' : (' (' + ('not set up' | translate) + ')') }}
            </label>
            <div class="text-muted" style="font-size:0.85rem;">{{ 'Customers choose between the ones you tick at checkout.' | translate }}</div>
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
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="min-width:220px;"><label class="form-label">{{ 'Shop colour (buttons and links)' | translate }}</label>
            <div class="flex gap-1" style="align-items:center;">
              <input type="color" class="fp-color-pick" style="width:44px; height:36px; padding:0; border:0; background:none;" name="fpColorPick" [ngModel]="pc.brandColor || '#1d4ed8'" (ngModelChange)="pc.brandColor = $event" />
              <input class="form-control fp-color" maxlength="7" style="width:110px;" name="fpColor" [(ngModel)]="pc.brandColor" placeholder="#1d4ed8" />
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="pc.brandColor = null">{{ 'Standard' | translate }}</button>
            </div></div>
          <div class="form-group" style="min-width:260px;"><label class="form-label">{{ 'Estimated delivery, in days after the order is accepted' | translate }}</label>
            <div class="flex gap-1" style="align-items:center;">
              <input class="form-control fp-days-min" type="number" min="0" max="60" style="width:80px;" name="fpDMin" [(ngModel)]="pc.deliveryDaysMin" />
              <span>–</span>
              <input class="form-control fp-days-max" type="number" min="0" max="60" style="width:80px;" name="fpDMax" [(ngModel)]="pc.deliveryDaysMax" />
              <span class="text-muted">{{ 'days' | translate }}</span>
            </div></div>
          <div class="form-group" style="min-width:260px; max-width:420px;"><label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="fp-autoslide" name="fpAuto" [(ngModel)]="pc.autoSlideRows" /> {{ 'Product rows slide by themselves' | translate }}</label>
            <div class="text-muted" style="font-size:0.85rem;">{{ 'Off (recommended): visitors slide the rows with the arrows, a swipe or the trackpad. On: the rows also move on their own and stop while pointed at.' | translate }}</div></div>
        </div>
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
          <span>{{ p.name }} <span class="text-muted">{{ p.sku }} · {{ p.unitPrice | number: '1.0-2' }}</span><span class="text-muted" *ngIf="p.variantAttributes"> · {{ 'with options' | translate }}: {{ p.variantAttributes }}</span></span>
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
                <div class="text-muted lst-optsum" *ngIf="l.variantCount > 0" style="font-size:0.8rem;">{{ l.variantOptions }} · {{ '{p0} of {p1} options in stock' | translate: { p0: optionsInStock(l), p1: optionsOf(l).length } }}<span *ngIf="!l.hasImage"> · {{ 'No picture yet' | translate }}</span></div>
                <div class="text-muted" *ngIf="!l.variantCount" style="font-size:0.8rem;">
                  {{ 'Price' | translate }}: {{ l.unitPrice | number: '1.0-2' }} · {{ 'Stock' | translate }}: {{ stockLabel(l.stock) | translate }}<span *ngIf="!l.hasImage"> · {{ 'No picture yet' | translate }}</span>
                </div>
                <div class="lst-shopprice" *ngIf="!l.variantCount" style="font-size:0.85rem; margin-top:0.15rem;"><strong>{{ 'Customers pay on the shop' | translate }}: {{ shopPrice(l) | number: '1.0-2' }}</strong><span class="text-muted" *ngIf="l.taxOnTopPercent"> ({{ 'the price {p0} plus {p1}% tax' | translate: { p0: (l.onlinePrice ?? l.unitPrice) | number: '1.0-2', p1: l.taxOnTopPercent } }})</span><span class="text-muted" *ngIf="!l.taxOnTopPercent"> ({{ 'no tax added' | translate }})</span></div>
                <div class="lst-stock" *ngIf="!l.variantCount" style="font-size:0.8rem; margin-top:0.15rem;" [style.color]="l.stock === 'SOLD_OUT' ? '#b91c1c' : '#4b5563'">
                  <ng-container *ngIf="l.stockInfo.tracked; else untracked">{{ 'In sellable warehouse locations {p0} − expired or too close to expiry {p6} − reserved on confirmed orders {p1} − held for orders nobody answered yet {p2} − buffer {p3} − kept for the till {p4} = sellable now {p5}' | translate: { p0: l.stockInfo.inWarehouses, p1: l.stockInfo.reserved, p2: l.stockInfo.held, p3: l.stockInfo.buffer, p4: l.stockInfo.tillShare, p5: l.stockInfo.sellable, p6: l.stockInfo.expired } }}</ng-container>
                  <ng-template #untracked>{{ 'Stock is not tracked for this product: the shop always shows it as in stock.' | translate }}</ng-template>
                </div>
              </div>
              <div style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveListing(l)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="removeListing(l)">{{ 'Remove' | translate }}</button></div>
            </div>
            <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(160px, 1fr)); gap:0.6rem; margin-top:0.6rem;">
              <div style="grid-column: span 2; min-width:0;"><label class="form-label" style="font-size:0.8rem;">{{ 'Title on the shop' | translate }}</label>
                <input class="form-control" style="width:100%;" maxlength="160" [name]="'t' + l.id" [(ngModel)]="l.webTitle" [placeholder]="l.productName" /></div>
              <div *ngIf="!l.variantCount"><label class="form-label" style="font-size:0.8rem;">{{ 'Online price' | translate }}</label>
                <input class="form-control" type="number" min="0" style="width:100%;" [name]="'p' + l.id" [(ngModel)]="l.onlinePrice" /></div>
              <div *ngIf="!l.variantCount"><label class="form-label" style="font-size:0.8rem;">{{ 'Buffer for this product' | translate }}</label>
                <input class="form-control lst-buffer" type="number" min="0" max="1000" style="width:100%;" [name]="'b' + l.id" [(ngModel)]="l.stockBuffer" [placeholder]="'Shop-wide' | translate" /></div>
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
            <div class="lst-options" *ngIf="l.variantCount > 0" style="margin-top:0.75rem;">
              <button type="button" class="btn btn-sm btn-outline-secondary lst-opts-btn" (click)="optsFor = optsFor === l.productId ? null : l.productId">{{ optsFor === l.productId ? ('Hide the options' | translate) : ('Options: price, stock, pictures' | translate) }} ({{ l.variantCount }})</button>
              <div class="lst-opts mt-2" *ngIf="optsFor === l.productId" style="overflow-x:auto;">
                <p class="text-muted" style="font-size:0.8rem; margin:0 0 0.4rem;">{{ 'Each option is sold with its own price and stock. The title, description, specifications and tags above are shared by all options. Leave the online price empty to use the option’s normal price.' | translate }}</p>
                <div class="lst-opt" *ngFor="let o of optionsOf(l)" style="border-top:1px solid #e5e7eb; padding:0.5rem 0;">
                  <div class="flex gap-2" style="flex-wrap:wrap; align-items:center;">
                    <div style="flex:1 1 160px; min-width:0;"><strong class="lst-opt-label">{{ o.variantLabel }}</strong><div class="text-muted" style="font-size:0.78rem;">{{ o.sku }} · {{ o.unitPrice | number: '1.0-2' }} · <span [style.color]="o.stock === 'SOLD_OUT' ? '#b91c1c' : '#4b5563'">{{ stockLabel(o.stock) | translate }}</span> ({{ o.stockInfo.sellable ?? '–' }})</div></div>
                    <input class="form-control lst-opt-price" type="number" min="0" style="width:120px;" [name]="'op' + o.id" [(ngModel)]="o.onlinePrice" [placeholder]="'Online price' | translate" />
                    <input class="form-control lst-opt-buffer" type="number" min="0" max="1000" style="width:110px;" [name]="'ob' + o.id" [(ngModel)]="o.stockBuffer" [placeholder]="'Buffer' | translate" />
                    <label style="display:flex; align-items:center; gap:0.35rem;"><input type="checkbox" class="lst-opt-on" [name]="'ov' + o.id" [(ngModel)]="o.listed" /> {{ 'Sold online' | translate }}</label>
                    <button type="button" class="btn btn-sm btn-primary lst-opt-save" (click)="saveOption(o)">{{ 'Save' | translate }}</button>
                    <button type="button" class="btn btn-sm btn-outline-secondary lst-opt-pics" (click)="toggleDetails(o)">{{ detailsFor === o.productId ? ('Hide pictures' | translate) : ('Pictures' | translate) }}</button>
                  </div>
                  <div class="lst-opt-gallery mt-2" *ngIf="detailsFor === o.productId" style="padding:0.6rem; background:rgba(128,128,128,0.12); border-radius:8px;">
                    <div class="text-muted" *ngIf="!details" style="font-size:0.85rem;">{{ 'Loading…' | translate }}</div>
                    <ng-container *ngIf="details">
                      <div class="text-muted" style="font-size:0.8rem; margin-bottom:0.3rem;">{{ 'Pictures of this option: shown first when the customer chooses it.' | translate }}</div>
                      <div class="flex gap-2" style="flex-wrap:wrap;">
                        <div class="lst-pic" *ngFor="let i of details.images; let k = index" style="width:110px; border:1px solid #e5e7eb; border-radius:8px; padding:0.3rem; background:rgba(128,128,128,0.1);">
                          <img [src]="pictureThumb(i.url)" [alt]="i.fileName" style="width:100%; height:70px; object-fit:cover; border-radius:6px;" />
                          <div class="flex gap-1 mt-1" style="justify-content:center;">
                            <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="k === 0 || detailsBusy" [attr.aria-label]="'Move earlier' | translate" (click)="movePicture(i.id, 'up')">←</button>
                            <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="k === details.images.length - 1 || detailsBusy" [attr.aria-label]="'Move later' | translate" (click)="movePicture(i.id, 'down')">→</button>
                            <button type="button" class="btn btn-sm btn-outline-danger lst-pic-del" [disabled]="detailsBusy" [attr.aria-label]="'Remove' | translate" (click)="deletePicture(i.id)">✕</button>
                          </div>
                        </div>
                      </div>
                      <input type="file" class="form-control lst-pic-file mt-1" accept="image/jpeg,image/png,image/webp,image/gif" multiple [disabled]="detailsBusy || details.images.length >= details.maxImages" (change)="pickPictures($event, o)" style="max-width:360px;" />
                    </ng-container>
                  </div>
                </div>
              </div>
            </div>
            <div style="margin-top:0.6rem;">
              <button type="button" class="btn btn-sm btn-outline-secondary lst-details-btn" (click)="toggleDetails(l)">{{ detailsFor === l.productId ? ('Hide the product page details' | translate) : ('Product page: pictures, description, specifications' | translate) }}</button>
            </div>
            <div class="lst-details" *ngIf="detailsFor === l.productId" style="margin-top:0.6rem; padding:0.75rem; background:rgba(128,128,128,0.12); border-radius:8px;">
              <div class="text-muted" *ngIf="!details" style="font-size:0.85rem;">{{ 'Loading…' | translate }}</div>
              <ng-container *ngIf="details">
                <label class="form-label" style="font-size:0.8rem;">{{ 'Description on the shop' | translate }}</label>
                <textarea class="form-control lst-desc" rows="4" maxlength="2000" style="width:100%;" [name]="'d' + l.id" [(ngModel)]="l.webDescription" [placeholder]="'Empty: the product description is shown' | translate"></textarea>
                <div class="text-muted" style="font-size:0.8rem;">{{ 'Press Save above to keep the description.' | translate }}</div>

                <label class="form-label mt-3" style="font-size:0.8rem;">{{ 'More pictures (the product picture is always shown first)' | translate }}</label>
                <div class="flex gap-2" style="flex-wrap:wrap;">
                  <div class="lst-pic" *ngFor="let i of details.images; let k = index" style="width:120px; border:1px solid #e5e7eb; border-radius:8px; padding:0.3rem; background:rgba(128,128,128,0.1);">
                    <img [src]="pictureThumb(i.url)" [alt]="i.fileName" style="width:100%; height:80px; object-fit:cover; border-radius:6px;" />
                    <div class="flex gap-1 mt-1" style="justify-content:center;">
                      <button type="button" class="btn btn-sm btn-outline-secondary lst-pic-up" [disabled]="k === 0 || detailsBusy" [attr.aria-label]="'Move earlier' | translate" (click)="movePicture(i.id, 'up')">←</button>
                      <button type="button" class="btn btn-sm btn-outline-secondary lst-pic-down" [disabled]="k === details.images.length - 1 || detailsBusy" [attr.aria-label]="'Move later' | translate" (click)="movePicture(i.id, 'down')">→</button>
                      <button type="button" class="btn btn-sm btn-outline-danger lst-pic-del" [disabled]="detailsBusy" [attr.aria-label]="'Remove' | translate" (click)="deletePicture(i.id)">✕</button>
                    </div>
                  </div>
                </div>
                <input type="file" class="form-control lst-pic-file mt-1" accept="image/jpeg,image/png,image/webp,image/gif" multiple [disabled]="detailsBusy || details.images.length >= details.maxImages" (change)="pickPictures($event, l)" style="max-width:360px;" />
                <div class="text-muted" style="font-size:0.8rem;">{{ 'Up to {p0} more pictures, each at most 5 MB.' | translate: { p0: details.maxImages } }}</div>

                <label class="form-label mt-3" style="font-size:0.8rem;">{{ 'Specifications (for example Size: 5 kg)' | translate }}</label>
                <div class="lst-spec flex gap-1 mb-1" *ngFor="let r of specRows; let k = index" style="align-items:center;">
                  <input class="form-control" style="flex:1; min-width:0;" maxlength="60" [name]="'sl' + l.id + '_' + k" [(ngModel)]="r.label" [placeholder]="'Name' | translate" />
                  <input class="form-control" style="flex:2; min-width:0;" maxlength="200" [name]="'sv' + l.id + '_' + k" [(ngModel)]="r.value" [placeholder]="'Value' | translate" />
                  <button type="button" class="btn btn-sm btn-outline-danger" [attr.aria-label]="'Remove' | translate" (click)="specRows.splice(k, 1)">✕</button>
                </div>
                <div class="flex gap-2 mt-1">
                  <button type="button" class="btn btn-sm btn-outline-secondary lst-spec-add" [disabled]="specRows.length >= 20" (click)="specRows.push({ label: '', value: '' })">{{ 'Add a row' | translate }}</button>
                  <button type="button" class="btn btn-sm btn-primary lst-spec-save" [disabled]="detailsBusy" (click)="saveSpecs(l)">{{ 'Save the specifications' | translate }}</button>
                </div>
              </ng-container>
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
        <p class="text-muted dz-daysnote">{{ 'Delivery days: how many days after we accept the order it arrives in this area. Leave empty to use the estimate of the whole shop (Front page).' | translate }}</p>
        <p class="text-muted dz-taxhelp">{{ 'The fee is the amount the customer pays. The tax you choose (from your existing taxes) is the part of it that is tax, included in the fee, so the invoice always equals the order total. Without a choice the tax of the delivery product is used.' | translate }}</p>
        <div style="overflow-x:auto;">
        <table class="table-sm">
          <thead><tr><th>{{ 'Area' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th><th>{{ 'Tax' | translate }}</th><th class="text-end">{{ 'Minimum order' | translate }}</th><th>{{ 'Delivery days' | translate }}</th><th>{{ 'Active' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let z of zones">
              <td><input class="form-control" style="width:200px;" maxlength="80" [name]="'zn' + z.id" [(ngModel)]="z.name" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" [name]="'zf' + z.id" [(ngModel)]="z.fee" /></td>
              <td>
                <select class="form-select dz-tax" style="width:190px;" [name]="'zt' + z.id" [(ngModel)]="z.taxRateId">
                  <option [ngValue]="null">{{ "The delivery product's tax" | translate }}</option>
                  <option *ngFor="let t of taxOptions" [ngValue]="t.id">{{ taxLabel(t) }}</option>
                </select>
              </td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" [name]="'zm' + z.id" [(ngModel)]="z.minOrder" /></td>
              <td style="white-space:nowrap;"><input class="form-control dz-dmin" type="number" min="0" max="60" style="width:70px; display:inline-block;" [name]="'zdn' + z.id" [(ngModel)]="z.deliveryDaysMin" [placeholder]="'from' | translate" /> – <input class="form-control dz-dmax" type="number" min="0" max="60" style="width:70px; display:inline-block;" [name]="'zdx' + z.id" [(ngModel)]="z.deliveryDaysMax" [placeholder]="'to' | translate" /></td>
              <td><input type="checkbox" [name]="'za' + z.id" [(ngModel)]="z.active" /></td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveZone(z)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="deleteZone(z)">{{ 'Remove' | translate }}</button></td>
            </tr>
            <tr>
              <td><input class="form-control" style="width:200px;" maxlength="80" name="newZoneName" [(ngModel)]="newZone.name" [placeholder]="'New area' | translate" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" name="newZoneFee" [(ngModel)]="newZone.fee" /></td>
              <td>
                <select class="form-select dz-newtax" style="width:190px;" name="newZoneTax" [(ngModel)]="newZone.taxRateId">
                  <option [ngValue]="null">{{ "The delivery product's tax" | translate }}</option>
                  <option *ngFor="let t of taxOptions" [ngValue]="t.id">{{ taxLabel(t) }}</option>
                </select>
              </td>
              <td class="text-end"><input class="form-control" type="number" min="0" style="width:110px;" name="newZoneMin" [(ngModel)]="newZone.minOrder" /></td>
              <td style="white-space:nowrap;"><input class="form-control dz-newdmin" type="number" min="0" max="60" style="width:70px; display:inline-block;" name="newZoneDmin" [(ngModel)]="newZone.deliveryDaysMin" [placeholder]="'from' | translate" /> – <input class="form-control dz-newdmax" type="number" min="0" max="60" style="width:70px; display:inline-block;" name="newZoneDmax" [(ngModel)]="newZone.deliveryDaysMax" [placeholder]="'to' | translate" /></td>
              <td></td>
              <td><button class="btn btn-sm btn-primary" type="button" (click)="addZone()">{{ 'Add area' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>

      <div class="card mb-3 slots-card" style="padding:1rem;">
        <h4 class="mb-2">{{ 'Delivery times' | translate }}</h4>
        <p class="text-muted">{{ 'Let customers choose a day and a time window for their delivery. Each window takes a limited number of orders per day; a full window cannot be chosen. Only delivery orders use windows.' | translate }}</p>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="slotSet">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="sl-enabled" name="slEnabled" [(ngModel)]="slotSet.enabled" /> {{ 'Customers choose a delivery time' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="sl-required" name="slRequired" [(ngModel)]="slotSet.required" /> {{ 'They must choose one (otherwise As soon as possible is allowed)' | translate }}</label>
          <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Days ahead' | translate }}</label><input class="form-control sl-ahead" type="number" min="0" max="60" style="width:90px;" name="slAhead" [(ngModel)]="slotSet.daysAhead" /></div>
          <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Notice (hours before the window starts)' | translate }}</label><input class="form-control sl-notice" type="number" min="0" max="72" style="width:90px;" name="slNotice" [(ngModel)]="slotSet.noticeHours" /></div>
          <button class="btn btn-primary sl-save-set" type="button" (click)="saveSlotSettings()">{{ 'Save' | translate }}</button>
        </div>
        <div style="overflow-x:auto;">
        <table class="table-sm">
          <thead><tr><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th class="text-end">{{ 'Orders per day' | translate }}</th><th>{{ 'Days' | translate }}</th><th>{{ 'Active' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let w of windows" class="sl-row">
              <td><input class="form-control" type="time" style="width:150px;" [name]="'ws' + w.id" [(ngModel)]="w.start" /></td>
              <td><input class="form-control" type="time" style="width:150px;" [name]="'we' + w.id" [(ngModel)]="w.end" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" max="1000" style="width:100px;" [name]="'wm' + w.id" [(ngModel)]="w.maxOrders" [placeholder]="'0 = no limit' | translate" /></td>
              <td style="white-space:nowrap;"><button type="button" *ngFor="let n of dayNames; let i = index" class="btn btn-sm" style="padding:0.1rem 0.35rem; margin-right:2px;" [ngClass]="w.days[i] === '1' ? 'btn-primary' : 'btn-outline-secondary'" (click)="w.days = flipDay(w.days, i)">{{ n | translate }}</button></td>
              <td><input type="checkbox" [name]="'wa' + w.id" [(ngModel)]="w.active" /></td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-primary sl-save" type="button" (click)="saveWindow(w)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="deleteWindow(w)">{{ 'Remove' | translate }}</button></td>
            </tr>
            <tr>
              <td><input class="form-control sl-new-start" type="time" style="width:150px;" name="nwStart" [(ngModel)]="newWindow.start" /></td>
              <td><input class="form-control sl-new-end" type="time" style="width:150px;" name="nwEnd" [(ngModel)]="newWindow.end" /></td>
              <td class="text-end"><input class="form-control" type="number" min="0" max="1000" style="width:100px;" name="nwMax" [(ngModel)]="newWindow.maxOrders" /></td>
              <td style="white-space:nowrap;"><button type="button" *ngFor="let n of dayNames; let i = index" class="btn btn-sm" style="padding:0.1rem 0.35rem; margin-right:2px;" [ngClass]="newWindow.days[i] === '1' ? 'btn-primary' : 'btn-outline-secondary'" (click)="newWindow.days = flipDay(newWindow.days, i)">{{ n | translate }}</button></td>
              <td></td>
              <td><button class="btn btn-sm btn-primary sl-add" type="button" (click)="addWindow()">{{ 'Add window' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        </div>
        <h5 class="mt-3 mb-1">{{ 'Days without deliveries' | translate }}</h5>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
          <input class="form-control sl-closed-day" type="date" style="width:170px;" name="cdDay" [(ngModel)]="closedDay" />
          <input class="form-control" maxlength="80" style="width:220px;" name="cdReason" [(ngModel)]="closedReason" [placeholder]="'Reason (optional)' | translate" />
          <button class="btn btn-sm btn-primary sl-closed-add" type="button" (click)="addClosedDay()" [disabled]="!closedDay">{{ 'Close this day' | translate }}</button>
        </div>
        <div class="text-muted" *ngIf="!closedDays.length">{{ 'No closed days.' | translate }}</div>
        <div class="flex gap-2 sl-closed" style="flex-wrap:wrap;">
          <span *ngFor="let c of closedDays" class="badge badge-secondary" style="font-size:0.85rem; padding:0.35rem 0.55rem;">{{ c.day }}<ng-container *ngIf="c.reason"> · {{ c.reason }}</ng-container>
            <a href="javascript:void(0)" style="margin-left:0.4rem;" (click)="removeClosedDay(c)">✕</a></span>
        </div>
      </div>

      <div class="card mb-3 drivers-card" style="padding:1rem;">
        <h4 class="mb-2">{{ 'Drivers' | translate }}</h4>
        <p class="text-muted">{{ 'The people who deliver orders. On an order you choose the driver, print the delivery slip, and the customer sees the driver\\'s name and phone once the order is on its way. A driver is not an employee record: just a name and a phone number.' | translate }}</p>
        <div style="overflow-x:auto;">
        <table class="table-sm">
          <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'Phone' | translate }}</th><th>{{ 'Note' | translate }}</th><th>{{ 'Active' | translate }}</th><th class="text-end">{{ 'To deliver' | translate }}</th><th>{{ 'Link and PIN' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let d of drivers">
              <td><input class="form-control" style="width:170px;" maxlength="80" [name]="'dn' + d.id" [(ngModel)]="d.name" /></td>
              <td><input class="form-control" style="width:140px;" maxlength="40" [name]="'dp' + d.id" [(ngModel)]="d.phone" /></td>
              <td><input class="form-control" style="width:200px;" maxlength="200" [name]="'dt' + d.id" [(ngModel)]="d.notes" /></td>
              <td><input type="checkbox" [name]="'da' + d.id" [(ngModel)]="d.active" /></td>
              <td class="text-end">{{ d.openOrders }}</td>
              <td style="white-space:nowrap;">
                <ng-container *ngIf="!d.accessOn"><button class="btn btn-sm btn-outline-primary dr-make" type="button" (click)="makeLink(d)">{{ 'Give a link' | translate }}</button></ng-container>
                <ng-container *ngIf="d.accessOn">
                  <span class="text-muted dr-on" style="font-size:0.8rem;">{{ d.accessLastUsedAt ? (('Last used' | translate) + ' ' + (d.accessLastUsedAt | date: 'short')) : ('Not used yet' | translate) }}</span>
                  <button class="btn btn-sm btn-outline-secondary dr-show" type="button" (click)="showLink(d)">{{ 'Show link' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary dr-pin" type="button" (click)="newPin(d)">{{ 'New PIN' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger dr-off" type="button" (click)="linkOff(d)">{{ 'Switch off' | translate }}</button>
                </ng-container>
              </td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-primary" type="button" (click)="saveDriver(d)">{{ 'Save' | translate }}</button> <button class="btn btn-sm btn-outline-danger" type="button" (click)="deleteDriver(d)">{{ 'Remove' | translate }}</button></td>
            </tr>
            <tr>
              <td><input class="form-control" style="width:170px;" maxlength="80" name="newDriverName" [(ngModel)]="newDriver.name" [placeholder]="'New driver' | translate" /></td>
              <td><input class="form-control" style="width:140px;" maxlength="40" name="newDriverPhone" [(ngModel)]="newDriver.phone" /></td>
              <td><input class="form-control" style="width:200px;" maxlength="200" name="newDriverNotes" [(ngModel)]="newDriver.notes" /></td>
              <td></td><td></td><td></td>
              <td><button class="btn btn-sm btn-primary" type="button" (click)="addDriver()">{{ 'Add driver' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        </div>
        <div class="dr-access mt-3" *ngIf="access" style="border:1px solid #2563eb; border-radius:8px; padding:0.8rem;">
          <strong>{{ 'Delivery link of {p0}' | translate: { p0: access.driver.name } }}</strong>
          <p class="text-muted" style="margin:0.3rem 0;">{{ 'The driver opens this link on his phone and types his PIN. He sees only his own orders that are out for delivery, and confirms each with the customer\\'s code or signature. Send the PIN apart from the link.' | translate }}</p>
          <div *ngIf="access.a.url" class="flex gap-2" style="flex-wrap:wrap; align-items:center;">
            <input class="form-control dr-url" style="flex:1; min-width:240px;" readonly [value]="access.a.url" (focus)="$any($event.target).select()" />
            <button class="btn btn-sm btn-outline-secondary dr-copy" type="button" (click)="copy(access.a.url)">{{ 'Copy' | translate }}</button>
            <a class="btn btn-sm btn-outline-secondary dr-wa-link" *ngIf="waLink(access.driver, access.a, false) as w" [href]="w" target="_blank" rel="noopener">{{ 'Send the link on WhatsApp' | translate }}</a>
          </div>
          <div *ngIf="!access.a.url" class="alert alert-warning mt-2">{{ 'The address of the app is not set (app.frontend-url), so only the end of the link can be shown:' | translate }} <code>{{ access.a.path }}</code></div>
          <div *ngIf="access.a.pin" class="mt-2 flex gap-2" style="align-items:center; flex-wrap:wrap;">
            <span>{{ 'PIN' | translate }}:</span> <strong class="dr-pin-shown" style="font-size:1.6rem; letter-spacing:0.3em;">{{ access.a.pin }}</strong>
            <a class="btn btn-sm btn-outline-secondary dr-wa-pin" *ngIf="waLink(access.driver, access.a, true) as w" [href]="w" target="_blank" rel="noopener">{{ 'Send the PIN on WhatsApp' | translate }}</a>
            <span class="text-muted" style="font-size:0.85rem;">{{ 'Shown only now: write it down or send it. A new PIN can be made at any time.' | translate }}</span>
          </div>
          <button class="btn btn-sm btn-outline-secondary mt-2" type="button" (click)="access = null">{{ 'Close' | translate }}</button>
        </div>
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
  newZone: { name: string; fee: number; minOrder: number; taxRateId: number | null; deliveryDaysMin: number | null; deliveryDaysMax: number | null } = { name: '', fee: 0, minOrder: 0, taxRateId: null, deliveryDaysMin: null, deliveryDaysMax: null };
  taxOptions: TaxOption[] = [];
  drivers: Driver[] = [];
  /** The link (and the PIN, just made) of a driver, shown under the table. */
  access: { driver: Driver; a: DriverAccess } | null = null;
  newDriver: { name: string; phone: string; notes: string } = { name: '', phone: '', notes: '' };
  error: string | null = null;
  message: string | null = null;
  busy = false;

  providers: PaymentProvider[] = [];

  /** The providers ticked: the settings keep their codes separated by commas. */
  private chosenProviders(): string[] {
    // Only codes still on offer count: a provider that is no longer listed (e.g. the pretend one) is dropped on the next save.
    const offered = this.providers.map((p) => p.code);
    return (this.s?.paymentProvider ?? '').split(',').map((c) => c.trim()).filter((c) => c && (!offered.length || offered.includes(c)));
  }

  hasProvider(code: string): boolean {
    return this.chosenProviders().includes(code);
  }

  toggleProvider(code: string, on: boolean): void {
    if (!this.s) return;
    const list = this.chosenProviders().filter((c) => c !== code);
    if (on) list.push(code);
    this.s.paymentProvider = list.length ? list.join(',') : null;
  }
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
    this.loadDrivers();
    this.loadSlots();
    this.loadShopStock();
    this.catalog.searchProducts(null, null).subscribe({ next: (all) => (this.serviceProducts = all.filter((p) => (p as any).service && p.active)), error: () => {} });
  }

  // ---- the product page details of one product (pictures and specifications); the description is saved with the listing's own Save button
  detailsFor: number | null = null;
  details: ListingDetails | null = null;
  specRows: ProductSpec[] = [];
  detailsBusy = false;

  toggleDetails(l: StoreListing): void {
    if (this.detailsFor === l.productId) {
      this.detailsFor = null;
      this.details = null;
      return;
    }
    this.detailsFor = l.productId;
    this.details = null;
    this.api.listingDetails(l.productId).subscribe({
      next: (d) => this.applyDetails(d),
      error: (e) => { this.detailsFor = null; this.fail(e, 'Could not load the details'); },
    });
  }

  private applyDetails(d: ListingDetails): void {
    this.details = d;
    this.specRows = d.specs.map((r) => ({ ...r }));
    if (!this.specRows.length) this.specRows.push({ label: '', value: '' });
  }

  pictureThumb(url: string): string {
    return this.api.imageUrl(url) + '?w=240';
  }

  /** Uploads the chosen pictures one after the other; one that is refused is reported and the rest carry on. */
  pickPictures(ev: Event, l: StoreListing): void {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length || !this.details) return;
    this.detailsBusy = true;
    this.error = null;
    this.message = null;
    const queue = files.slice(0, Math.max(0, this.details.maxImages - this.details.images.length));
    const next = (): void => {
      const f = queue.shift();
      if (!f) {
        this.detailsBusy = false;
        if (!this.error) this.ok('Pictures saved');
        return;
      }
      this.api.addListingImage(l.productId, f).subscribe({
        next: (d) => { this.details = d; next(); },
        error: (e) => { this.error = (e?.error?.message ?? T('Could not add the picture')) + ' (' + f.name + ')'; next(); },
      });
    };
    next();
  }

  movePicture(id: number, direction: 'up' | 'down'): void {
    this.detailsBusy = true;
    this.api.moveListingImage(id, direction).subscribe({
      next: (d) => { this.details = d; this.detailsBusy = false; },
      error: (e) => { this.detailsBusy = false; this.fail(e, 'Could not move the picture'); },
    });
  }

  deletePicture(id: number): void {
    if (!confirm(T('Remove this picture?'))) return;
    this.detailsBusy = true;
    this.api.deleteListingImage(id).subscribe({
      next: (d) => { this.details = d; this.detailsBusy = false; this.ok('Removed'); },
      error: (e) => { this.detailsBusy = false; this.fail(e, 'Could not remove the picture'); },
    });
  }

  saveSpecs(l: StoreListing): void {
    this.detailsBusy = true;
    this.api.saveListingSpecs(l.productId, this.specRows).subscribe({
      next: (d) => { this.applyDetails(d); this.detailsBusy = false; this.ok('Saved'); },
      error: (e) => { this.detailsBusy = false; this.fail(e, 'Could not save the specifications'); },
    });
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

  /** The options (sizes, colours, models) of a product with options. */
  optionsOf(l: StoreListing): StoreListing[] {
    return this.listings.filter((o) => o.variantParentId === l.productId).sort((x, y) => x.productId - y.productId);
  }

  optionsInStock(l: StoreListing): number {
    return this.optionsOf(l).filter((o) => o.listed && o.stock !== 'SOLD_OUT').length;
  }

  /** An empty buffer box means the shop-wide buffer (sent as -1). */
  private bufferValue(v: number | string | null | undefined): number {
    return v === null || v === undefined || v === '' ? -1 : Number(v);
  }

  /** The product whose options are open. */
  optsFor: number | null = null;

  /** An option keeps its own online price and whether it is sold online; everything else is the product's. */
  saveOption(o: StoreListing): void {
    this.api.saveListing({ productId: o.productId, listed: o.listed, onlinePrice: o.onlinePrice === null || (o.onlinePrice as any) === '' ? null : Number(o.onlinePrice), stockBuffer: this.bufferValue(o.stockBuffer) })
      .subscribe({ next: () => { this.loadListings(); this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save the product') });
  }

  /** The products of the list: options are shown under their product, not as rows of their own. */
  private tops(): StoreListing[] {
    return this.listings.filter((l) => !l.variantParentId);
  }

  filteredListings(): StoreListing[] {
    const q = this.listFilter.trim().toLowerCase();
    if (!q) return this.tops();
    const hit = (l: StoreListing): boolean => l.productName.toLowerCase().includes(q) || (l.webTitle ?? '').toLowerCase().includes(q) || (l.sku ?? '').toLowerCase().includes(q) || (l.category ?? '').toLowerCase().includes(q);
    return this.tops().filter((l) => hit(l) || this.optionsOf(l).some(hit));
  }

  listingPages(): number {
    return Math.max(1, Math.ceil(this.filteredListings().length / this.listSize));
  }

  /** The price customers see on the shop for a listing: its online price (or the product price) with the tax added on top when the tax rate is not included. */
  shopPrice(l: StoreListing): number {
    const base = Number(l.onlinePrice ?? l.unitPrice) || 0;
    return l.taxOnTopPercent ? Math.round(base * (1 + l.taxOnTopPercent / 100) * 100) / 100 : base;
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
    this.catalog.searchProducts(null, this.search.trim() || null).subscribe({ next: (p) => (this.found = p.filter((x) => (x.active || !!x.variantAttributes) && !x.variantParentId).slice(0, 20)), error: (e) => this.fail(e, 'Search failed') });
  }

  add(p: Product): void {
    this.api.saveListing({ productId: p.id, listed: true }).subscribe({ next: () => { this.loadListings(); this.ok('Added to the shop'); }, error: (e) => this.fail(e, 'Could not add the product') });
  }

  saveListing(l: StoreListing): void {
    this.api.saveListing({ productId: l.productId, featured: l.featured, bestSeller: l.bestSeller, specialOffer: l.specialOffer, newArrival: l.newArrival, listed: l.listed, webTitle: l.webTitle, webDescription: l.webDescription, onlinePrice: l.onlinePrice === null || (l.onlinePrice as any) === '' ? null : l.onlinePrice, sortOrder: l.sortOrder, stockBuffer: this.bufferValue(l.stockBuffer) })
      .subscribe({ next: () => { this.loadListings(); this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save the product') });
  }

  removeListing(l: StoreListing): void {
    if (!confirm(T('Remove {p0} from the shop?', { p0: l.productName }))) return;
    this.api.removeListing(l.id).subscribe({ next: () => { this.loadListings(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove the product') });
  }

  loadZones(): void {
    this.api.zones().subscribe({ next: (z) => (this.zones = z), error: (e) => this.fail(e, 'Failed to load the delivery areas') });
    this.api.taxOptions().subscribe({ next: (t) => (this.taxOptions = t), error: () => (this.taxOptions = []) });
  }

  addZone(): void {
    if (!this.newZone.name.trim()) { this.error = T('Give the area a name'); return; }
    this.api.saveZone(null, { name: this.newZone.name, fee: this.newZone.fee || 0, minOrder: this.newZone.minOrder || 0, taxRateId: this.newZone.taxRateId, deliveryDaysMin: this.newZone.deliveryDaysMin, deliveryDaysMax: this.newZone.deliveryDaysMax, active: true }).subscribe({
      next: () => { this.newZone = { name: '', fee: 0, minOrder: 0, taxRateId: null, deliveryDaysMin: null, deliveryDaysMax: null }; this.loadZones(); this.ok('Area added'); },
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

  // ---- delivery times (windows, limits, closed days)
  dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  windows: DeliveryWindow[] = [];
  slotSet: SlotSettings | null = null;
  stockSetup: ShopStockSetup | null = null;

  loadShopStock(): void {
    this.api.shopStock().subscribe({ next: (x) => (this.stockSetup = x), error: () => {} });
  }

  saveShopStock(id: number | null): void {
    this.api.saveShopStock(id).subscribe({ next: (x) => { this.stockSetup = x; this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save') });
  }
  closedDays: ClosedDay[] = [];
  closedDay = '';
  closedReason = '';
  newWindow = { start: '08:00', end: '11:00', maxOrders: 0, days: '1111111' };

  loadSlots(): void {
    this.api.windows().subscribe({ next: (w) => (this.windows = w), error: (e) => this.fail(e, 'Failed to load the delivery windows') });
    this.api.slotSettings().subscribe({ next: (s) => (this.slotSet = s), error: () => {} });
    this.api.closedDays().subscribe({ next: (c) => (this.closedDays = c), error: () => {} });
  }

  flipDay(days: string, i: number): string {
    const a = days.split('');
    a[i] = a[i] === '1' ? '0' : '1';
    return a.join('');
  }

  saveSlotSettings(): void {
    if (!this.slotSet) return;
    this.api.saveSlotSettings(this.slotSet).subscribe({ next: (s) => { this.slotSet = s; this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save') });
  }

  addWindow(): void {
    this.api.saveWindow(null, { ...this.newWindow, active: true, sortOrder: this.windows.length + 1 }).subscribe({
      next: () => { this.loadSlots(); this.ok('Window added'); },
      error: (e) => this.fail(e, 'Could not add the window'),
    });
  }

  saveWindow(w: DeliveryWindow): void {
    this.api.saveWindow(w.id, { start: w.start, end: w.end, maxOrders: w.maxOrders || 0, days: w.days, active: w.active, sortOrder: w.sortOrder }).subscribe({
      next: () => { this.loadSlots(); this.ok('Saved'); },
      error: (e) => this.fail(e, 'Could not save the window'),
    });
  }

  deleteWindow(w: DeliveryWindow): void {
    if (!confirm(T('Remove the window {p0}?', { p0: w.start + ' - ' + w.end }))) return;
    this.api.deleteWindow(w.id).subscribe({ next: () => { this.loadSlots(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove the window') });
  }

  addClosedDay(): void {
    this.api.addClosedDay(this.closedDay, this.closedReason.trim() || null).subscribe({
      next: () => { this.closedDay = ''; this.closedReason = ''; this.loadSlots(); this.ok('Day closed'); },
      error: (e) => this.fail(e, 'Could not close the day'),
    });
  }

  removeClosedDay(c: ClosedDay): void {
    this.api.removeClosedDay(c.id).subscribe({ next: () => { this.loadSlots(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove') });
  }

  loadDrivers(): void {
    this.api.drivers().subscribe({ next: (d) => (this.drivers = d), error: (e) => this.fail(e, 'Failed to load the drivers') });
  }

  addDriver(): void {
    if (!this.newDriver.name.trim()) { this.error = T('Enter the driver\'s name.'); return; }
    this.api.saveDriver(null, { name: this.newDriver.name, phone: this.newDriver.phone.trim() || null, notes: this.newDriver.notes.trim() || null, active: true }).subscribe({
      next: () => { this.newDriver = { name: '', phone: '', notes: '' }; this.loadDrivers(); this.ok('Driver added'); },
      error: (e) => this.fail(e, 'Could not add the driver'),
    });
  }

  saveDriver(d: Driver): void {
    this.api.saveDriver(d.id, { name: d.name, phone: d.phone, notes: d.notes, active: d.active }).subscribe({ next: () => { this.loadDrivers(); this.ok('Saved'); }, error: (e) => this.fail(e, 'Could not save the driver') });
  }

  /** A tax as the screen shows it: its name, with the rate added when the name does not already say it. */
  taxLabel(t: TaxOption): string {
    return t.name.includes('%') ? t.name : `${t.name} (${t.ratePercent}%)`;
  }

  makeLink(d: Driver): void {
    if (d.accessOn && !confirm(T('Make a new link and PIN for {p0}? The old ones stop working.', { p0: d.name }))) return;
    this.api.driverAccessMake(d.id).subscribe({ next: (a) => { this.access = { driver: d, a }; this.loadDrivers(); }, error: (e) => this.fail(e, 'Could not make the link') });
  }

  showLink(d: Driver): void {
    this.api.driverAccessLink(d.id).subscribe({ next: (a) => (this.access = { driver: d, a }), error: (e) => this.fail(e, 'Could not show the link') });
  }

  newPin(d: Driver): void {
    if (!confirm(T('Make a new PIN for {p0}? The old PIN stops working.', { p0: d.name }))) return;
    this.api.driverAccessPin(d.id).subscribe({ next: (a) => { this.access = { driver: d, a }; this.loadDrivers(); }, error: (e) => this.fail(e, 'Could not make a new PIN') });
  }

  linkOff(d: Driver): void {
    if (!confirm(T('Switch off the link of {p0}? He will not be able to open it any more.', { p0: d.name }))) return;
    this.api.driverAccessOff(d.id).subscribe({ next: () => { this.access = null; this.loadDrivers(); this.ok('Link switched off'); }, error: (e) => this.fail(e, 'Could not switch the link off') });
  }

  copy(text: string): void {
    try { void navigator.clipboard.writeText(text); this.ok('Copied'); } catch { this.error = T('Select the link and copy it.'); }
  }

  /** WhatsApp to the driver (his phone, written with the shop's country code) with the link, or with the PIN: sent apart on purpose. */
  waLink(d: Driver, a: DriverAccess, pin: boolean): string | null {
    let n = (d.phone ?? '').replace(/[^0-9]/g, '');
    const cc = (this.s?.textCountryCode ?? '').replace(/[^0-9]/g, '');
    if (n.startsWith('0') && cc) n = cc + n.substring(1);
    if (n.length < 8) return null;
    const text = pin ? T('Your PIN for the delivery page: {p0}', { p0: a.pin ?? '' }) : T('Your delivery page: {p0}', { p0: a.url ?? '' });
    return `https://wa.me/${n}?text=${encodeURIComponent(text)}`;
  }

  deleteDriver(d: Driver): void {
    if (!confirm(T('Remove the driver {p0}?', { p0: d.name }))) return;
    this.api.deleteDriver(d.id).subscribe({ next: () => { this.loadDrivers(); this.ok('Removed'); }, error: (e) => this.fail(e, 'Could not remove the driver') });
  }
}
