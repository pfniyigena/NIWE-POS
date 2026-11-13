import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShopMoneyPipe } from './shop-money.pipe';
import { AccountOrder, AccountProfile, LoyaltyView, OnlineStoreApiService, Reorder, shopToken } from '../core/api/online-store-api.service';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

/**
 * The customer's account on the public shop. Signed out: type an e-mail address (or a phone number saved on an account), get a code, type it in.
 * Signed in: saved details, past orders with "Order again", sign out, delete the account. Nothing here is needed to buy: the shop works without it.
 */
@Component({
  selector: 'app-shop-account',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShopMoneyPipe],
  template: `
    <div class="card shop-account mt-3" style="padding:1rem; max-width:640px;">
      <div class="flex justify-between align-center mb-2" style="gap:0.5rem;">
        <h4 style="margin:0;">{{ 'My account' | translate }}</h4>
        <button class="btn btn-sm btn-outline-secondary acc-close" (click)="closed.emit()">{{ 'Close' | translate }}</button>
      </div>
      <div class="alert alert-danger acc-error" role="alert" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success acc-info" *ngIf="info">{{ info }}</div>

      <!-- Signed out: ask for a code -->
      <ng-container *ngIf="!profile && !codeSent">
        <p class="text-muted" style="font-size:0.9rem;">{{ 'Sign in to see your orders, save your details and order again in one tap. You do not need an account to buy.' | translate }}</p>
        <div class="form-group">
          <label class="form-label">{{ 'E-mail address or phone number' | translate }}</label>
          <input class="form-control acc-identifier" maxlength="160" inputmode="email" autocomplete="username" name="accid" [(ngModel)]="identifier" (keyup.enter)="sendCode()" />
        </div>
        <button class="btn btn-primary acc-send" [disabled]="busy || !identifier.trim()" (click)="sendCode()">{{ busy ? ('Sending…' | translate) : ('Send me a code' | translate) }}</button>
        <p class="text-muted mt-2 acc-hint-mail" *ngIf="!textChannel" style="font-size:0.8rem;">{{ 'A phone number only works once it is saved on your account. The first time, use your e-mail address.' | translate }}</p>
        <p class="text-muted mt-2 acc-hint-text" *ngIf="textChannel" style="font-size:0.8rem;">{{ 'Type your e-mail address and we e-mail you a code, or type your phone number and we text it to you.' | translate }}</p>
      </ng-container>

      <!-- Signed out: type the code -->
      <ng-container *ngIf="!profile && codeSent">
        <p>{{ 'If we found you, a code is on its way. It works for 10 minutes.' | translate }}</p>
        <div class="form-group">
          <label class="form-label">{{ 'The code' | translate }}</label>
          <input class="form-control acc-code" style="max-width:200px; letter-spacing:0.3em; font-size:1.2rem;" maxlength="12" inputmode="numeric" autocomplete="one-time-code" name="acccode" [(ngModel)]="code" (keyup.enter)="verify()" />
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <button class="btn btn-primary acc-verify" [disabled]="busy || !code.trim()" (click)="verify()">{{ busy ? ('Checking…' | translate) : ('Sign in' | translate) }}</button>
          <button class="btn btn-outline-secondary acc-again" [disabled]="busy" (click)="sendCode()">{{ 'Send a new code' | translate }}</button>
          <button class="btn btn-outline-secondary acc-other" [disabled]="busy" (click)="restart()">{{ 'Use another address' | translate }}</button>
        </div>
      </ng-container>

      <!-- Signed in -->
      <ng-container *ngIf="profile">
        <div class="text-muted mb-2">{{ 'Signed in as {p0}' | translate: { p0: profile.email || profile.phone } }}</div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Your name' | translate }}</label><input class="form-control acc-name" maxlength="80" name="accname" [(ngModel)]="form.name" /></div>
          <div class="form-group"><label class="form-label">{{ 'Your phone number' | translate }}</label><input class="form-control acc-phone" maxlength="40" inputmode="tel" name="accphone" [(ngModel)]="form.phone" [readonly]="!profile.email" /></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Delivery address' | translate }}</label><input class="form-control acc-address" maxlength="240" name="accaddr" [(ngModel)]="form.address" /></div>
        <div class="text-muted" *ngIf="!textChannel" style="font-size:0.8rem;">{{ 'Saved details fill in your next order for you. A saved phone number lets you sign in with it.' | translate }}</div>
        <div class="text-muted" *ngIf="textChannel" style="font-size:0.8rem;">{{ 'Saved details fill in your next order for you. A phone number you confirm by text message lets you sign in with it.' | translate }}</div>
        <button class="btn btn-primary mt-2 mb-3 acc-save" [disabled]="busy" (click)="save()">{{ 'Save my details' | translate }}</button>

        <!-- The phone number: proved with a text message before it can be used to sign in -->
        <div class="mt-2 acc-phone-box" *ngIf="textChannel && profile.phone">
          <div class="text-muted acc-phone-ok" *ngIf="profile.phoneVerified" style="font-size:0.85rem;">✓ {{ 'This number is confirmed: you can sign in with it.' | translate }}</div>
          <div class="text-muted acc-phone-fixed" *ngIf="profile.phoneVerified && !profile.email" style="font-size:0.85rem;">{{ 'This number is how you sign in, so it cannot be changed here.' | translate }}</div>
          <ng-container *ngIf="!profile.phoneVerified">
            <div class="text-muted" *ngIf="!phoneCodeSent" style="font-size:0.85rem;">{{ 'Confirm this number to sign in with it by text message.' | translate }}</div>
            <button class="btn btn-sm btn-outline-primary mt-1 acc-phone-send" *ngIf="!phoneCodeSent" [disabled]="busy || phoneUnsaved()" (click)="sendPhoneCode()">{{ 'Text me a code to confirm this number' | translate }}</button>
            <div class="text-muted" *ngIf="!phoneCodeSent && phoneUnsaved()" style="font-size:0.8rem;">{{ 'Save your details first.' | translate }}</div>
            <div *ngIf="phoneCodeSent">
              <div class="text-muted" style="font-size:0.85rem;">{{ 'A code was texted to {p0}. It works for 10 minutes.' | translate: { p0: profile.phone } }}</div>
              <div class="flex gap-2 mt-1" style="flex-wrap:wrap; align-items:center;">
                <input class="form-control acc-phone-code" style="max-width:180px; letter-spacing:0.3em;" maxlength="12" inputmode="numeric" autocomplete="one-time-code" name="accphonecode" [(ngModel)]="phoneCode" (keyup.enter)="verifyPhone()" />
                <button class="btn btn-sm btn-primary acc-phone-verify" [disabled]="busy || !phoneCode.trim()" (click)="verifyPhone()">{{ 'Confirm' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary acc-phone-again" [disabled]="busy" (click)="sendPhoneCode()">{{ 'Send a new code' | translate }}</button>
              </div>
            </div>
          </ng-container>
        </div>

        <ng-container *ngIf="loyalty && points && points.enabled">
          <h5 class="mt-3">{{ 'Loyalty points' | translate }}</h5>
          <div class="acc-points"><strong>{{ points.points }}</strong> {{ 'points' | translate }}</div>
          <p class="text-muted" style="font-size:0.85rem;">{{ 'You earn {p0} point(s) for every 100 you pay for goods, when the order is completed. Each point takes 1 off a later order, up to {p1}% of the goods.' | translate: { p0: points.pointsPer100, p1: points.maxPercent } }}</p>
          <div class="acc-point-row flex justify-between" *ngFor="let e of points.entries" style="padding:0.25rem 0; border-top:1px solid #e5e7eb; gap:0.5rem; flex-wrap:wrap; font-size:0.9rem;">
            <span>{{ e.at | date: 'mediumDate' }} · {{ pointLabel(e.kind) | translate }}<span *ngIf="e.orderCode"> ({{ e.orderCode }})</span></span>
            <strong [style.color]="e.points < 0 ? '#b42318' : '#15803d'">{{ e.points > 0 ? '+' : '' }}{{ e.points }}</strong>
          </div>
        </ng-container>
        <h5 class="mt-3">{{ 'My orders' | translate }}</h5>
        <div class="text-muted" *ngIf="orders === null">{{ 'Loading…' | translate }}</div>
        <div class="text-muted acc-no-orders" *ngIf="orders && orders.length === 0">{{ 'No orders yet. Orders you place while signed in, and earlier ones sent with your e-mail address, appear here.' | translate }}</div>
        <div class="acc-order flex justify-between align-center" *ngFor="let o of orders" style="padding:0.5rem 0; border-top:1px solid #e5e7eb; gap:0.5rem; flex-wrap:wrap;">
          <div>
            <a [routerLink]="['/shop/order', o.code]"><strong>{{ o.code }}</strong></a>
            <span class="text-muted" style="font-size:0.85rem;"> · {{ o.placedAt | date: 'mediumDate' }} · {{ statusLabel(o.status) | translate }}</span>
            <div class="text-muted" style="font-size:0.85rem;">{{ 'Total' | translate }} {{ o.total | shopMoney: currency }} · {{ o.items }} {{ 'items' | translate }}</div>
          </div>
          <button class="btn btn-sm btn-outline-primary acc-reorder" [disabled]="busy" (click)="reorder(o)">{{ 'Order again' | translate }}</button>
        </div>

        <hr />
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary acc-logout" [disabled]="busy" (click)="logout()">{{ 'Sign out' | translate }}</button>
          <button class="btn btn-outline-danger acc-delete" *ngIf="!confirmDelete" [disabled]="busy" (click)="confirmDelete = true">{{ 'Delete my account' | translate }}</button>
        </div>
        <div class="alert alert-warning mt-2 acc-delete-confirm" *ngIf="confirmDelete">
          {{ 'This deletes your account and saved details. Your past orders stay with the shop, but are no longer linked to you. This cannot be undone.' | translate }}
          <div class="flex gap-2 mt-1">
            <button class="btn btn-danger acc-delete-yes" [disabled]="busy" (click)="deleteAccount()">{{ 'Yes, delete it' | translate }}</button>
            <button class="btn btn-outline-secondary" (click)="confirmDelete = false">{{ 'Keep my account' | translate }}</button>
          </div>
        </div>
      </ng-container>
    </div>
  `,
})
export class ShopAccountComponent implements OnInit {
  /** The signed-in customer, or null. The shop page owns it; this component asks for changes through the outputs. */
  @Input() profile: AccountProfile | null = null;
  /** The shop's currency, written after amounts. */
  @Input() currency = '';
  /** SMS or WHATSAPP when the shop texts customers (phone sign-in and number confirmation then work), else null. */
  @Input() textChannel: string | null = null;
  /** True when the shop has loyalty points on: the balance and history show. */
  @Input() loyalty = false;
  @Output() profileChange = new EventEmitter<AccountProfile | null>();
  @Output() reordered = new EventEmitter<Reorder>();
  @Output() closed = new EventEmitter<void>();

  identifier = '';
  code = '';
  codeSent = false;
  busy = false;
  error: string | null = null;
  info: string | null = null;
  confirmDelete = false;
  orders: AccountOrder[] | null = null;
  points: LoyaltyView | null = null;
  form = { name: '', phone: '', address: '' };
  phoneCodeSent = false;
  phoneCode = '';

  constructor(private api: OnlineStoreApiService, private lang: LanguageService) {}

  ngOnInit(): void {
    if (this.profile) this.loaded();
  }

  private msg(e: any, fallback: string): string {
    return e?.error?.message ?? T(fallback);
  }

  statusLabel(s: string): string {
    switch (s) {
      case 'AWAITING_PAYMENT': return 'Waiting for payment';
      case 'PLACED': return 'Received';
      case 'ACCEPTED': return 'Accepted';
      case 'PACKED': return 'Packed';
      case 'OUT_FOR_DELIVERY': return 'On its way';
      case 'READY_FOR_PICKUP': return 'Ready for pick-up';
      case 'COMPLETED': return 'Done';
      case 'REJECTED': return 'Not accepted';
      case 'CANCELLED': return 'Cancelled';
      default: return s;
    }
  }

  private loaded(): void {
    const p = this.profile!;
    this.form = { name: p.name ?? '', phone: p.phone ?? '', address: p.address ?? '' };
    this.phoneCodeSent = false;
    this.phoneCode = '';
    this.orders = null;
    this.points = null;
    if (this.loyalty) this.api.accountLoyalty().subscribe({ next: (v) => (this.points = v), error: () => (this.points = null) });
    this.api.accountOrders().subscribe({
      next: (o) => (this.orders = o),
      error: (e) => this.expired(e),
    });
  }

  /** A 401 means the session ended (or was deleted elsewhere): back to signed out. */
  private expired(e: any): boolean {
    if (e?.status !== 401) {
      this.error = this.msg(e, 'Something went wrong. Please try again.');
      return false;
    }
    shopToken.clear();
    this.profile = null;
    this.profileChange.emit(null);
    this.codeSent = false;
    this.error = T('Please sign in again.');
    return true;
  }

  sendCode(): void {
    this.error = null;
    this.info = null;
    this.busy = true;
    this.api.accountCode(this.identifier.trim(), this.lang.current).subscribe({
      next: () => {
        this.busy = false;
        this.codeSent = true;
        this.code = '';
      },
      error: (e) => {
        this.busy = false;
        this.error = this.msg(e, 'We could not send the code. Please try again later.');
      },
    });
  }

  restart(): void {
    this.codeSent = false;
    this.code = '';
    this.error = null;
  }

  verify(): void {
    this.error = null;
    this.busy = true;
    this.api.accountVerify(this.identifier.trim(), this.code.trim()).subscribe({
      next: (s) => {
        this.busy = false;
        shopToken.set(s.token);
        this.profile = s.profile;
        this.codeSent = false;
        this.code = '';
        this.profileChange.emit(s.profile);
        this.loaded();
      },
      error: (e) => {
        this.busy = false;
        this.error = this.msg(e, 'That code is not right or has expired. Ask for a new one.');
      },
    });
  }

  save(): void {
    this.error = null;
    this.info = null;
    this.busy = true;
    this.api.accountSave({ name: this.form.name, phone: this.form.phone, address: this.form.address, language: this.lang.current }).subscribe({
      next: (p) => {
        this.busy = false;
        this.profile = p;
        this.form.phone = p.phone ?? '';
        this.profileChange.emit(p);
        this.info = T('Saved.');
      },
      error: (e) => {
        this.busy = false;
        this.expired(e);
      },
    });
  }

  /** The number in the box is not the one saved on the account yet: it has to be saved before it can be confirmed. */
  phoneUnsaved(): boolean {
    return (this.form.phone ?? '').trim() !== (this.profile?.phone ?? '');
  }

  sendPhoneCode(): void {
    this.error = null;
    this.info = null;
    this.busy = true;
    this.api.accountPhoneCode().subscribe({
      next: () => {
        this.busy = false;
        this.phoneCodeSent = true;
        this.phoneCode = '';
      },
      error: (e) => {
        this.busy = false;
        this.expired(e);
      },
    });
  }

  verifyPhone(): void {
    this.error = null;
    this.busy = true;
    this.api.accountPhoneVerify(this.phoneCode.trim()).subscribe({
      next: (p) => {
        this.busy = false;
        this.profile = p;
        this.profileChange.emit(p);
        this.phoneCodeSent = false;
        this.phoneCode = '';
        this.info = T('Your number is confirmed.');
      },
      error: (e) => {
        this.busy = false;
        this.expired(e);
      },
    });
  }

  pointLabel(kind: string): string {
    switch (kind) {
      case 'EARN': return 'Order completed';
      case 'SPEND': return 'Used on an order';
      case 'SPEND_BACK': return 'Order cancelled: points returned';
      case 'RET_BACK': return 'Return: points returned';
      case 'RET_TAKE': return 'Return: points taken back';
      default: return 'Changed by the shop';
    }
  }

  reorder(o: AccountOrder): void {
    this.error = null;
    this.busy = true;
    this.api.accountReorder(o.code).subscribe({
      next: (r) => {
        this.busy = false;
        this.reordered.emit(r);
      },
      error: (e) => {
        this.busy = false;
        this.expired(e);
      },
    });
  }

  logout(): void {
    this.busy = true;
    // Forget the token first: even if the server cannot be reached, this browser is signed out.
    const done = () => {
      this.busy = false;
      this.profile = null;
      this.orders = null;
      this.identifier = '';
      this.profileChange.emit(null);
    };
    const call = this.api.accountLogout();
    shopToken.clear();
    call.subscribe({ next: done, error: done });
  }

  deleteAccount(): void {
    this.error = null;
    this.busy = true;
    this.api.accountDelete().subscribe({
      next: () => {
        this.busy = false;
        shopToken.clear();
        this.profile = null;
        this.orders = null;
        this.confirmDelete = false;
        this.identifier = '';
        this.profileChange.emit(null);
        this.info = T('Your account was deleted.');
      },
      error: (e) => {
        this.busy = false;
        this.expired(e);
      },
    });
  }
}
