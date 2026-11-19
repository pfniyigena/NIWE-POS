import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { InfoLink, InfoPage, OnlineStoreApiService, Shop } from '../core/api/online-store-api.service';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

type Block = { h?: string; p?: string; ul?: string[] };

/**
 * The shop's information pages at /shop/info/<terms|privacy|returns|delivery> (staff edit them in Online Store > Shop pages) and the contact page at
 * /shop/info/contact: the shop's details and a form whose message lands in Online Store > Messages. A text is paragraphs; "## " starts a heading and
 * "- " a bullet. It is shown as plain text, never as HTML.
 */
@Component({
  selector: 'app-shop-info',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="info-page" style="max-width:780px; margin:0 auto; padding:1rem 0.75rem 3rem;">
      <a class="info-back" routerLink="/shop" style="font-size:0.9rem;">← {{ 'Back to the shop' | translate }}<ng-container *ngIf="shop"> · {{ shop.name }}</ng-container></a>

      <div class="alert alert-warning mt-3 info-error" *ngIf="error">{{ error }}</div>
      <p class="text-muted mt-3" *ngIf="!page && !error && slug !== 'contact'">{{ 'Loading…' | translate }}</p>

      <ng-container *ngIf="slug !== 'contact' && page as x">
        <h2 class="info-title mt-3">{{ x.title }}</h2>
        <div class="info-body">
          <ng-container *ngFor="let b of blocks">
            <h4 *ngIf="b.h" style="margin:1.3rem 0 0.3rem;">{{ b.h }}</h4>
            <p *ngIf="b.p" style="margin:0 0 0.6rem; white-space:pre-line;">{{ b.p }}</p>
            <ul *ngIf="b.ul" style="margin:0 0 0.6rem; padding-left:1.3rem;"><li *ngFor="let li of b.ul">{{ li }}</li></ul>
          </ng-container>
        </div>
        <p class="mt-3"><a class="btn btn-outline-secondary info-to-contact" routerLink="/shop/info/contact">{{ 'Questions? Contact us' | translate }}</a></p>
      </ng-container>

      <ng-container *ngIf="slug === 'contact'">
        <h2 class="info-title mt-3">{{ 'Contact us' | translate }}</h2>
        <div class="info-details" *ngIf="shop" style="margin-bottom:1rem;">
          <div *ngIf="shop.phone">📞 <a [href]="'tel:' + shop.phone">{{ shop.phone }}</a></div>
          <div *ngIf="shop.page.contactEmail">✉️ <a [href]="'mailto:' + shop.page.contactEmail">{{ shop.page.contactEmail }}</a></div>
          <div *ngIf="shop.whatsapp">💬 <a [href]="'https://wa.me/' + shop.whatsapp" target="_blank" rel="noopener">WhatsApp</a></div>
          <div *ngIf="shop.pickupAddress">📍 {{ shop.pickupAddress }}</div>
          <div *ngIf="shop.page.openingHours">🕒 {{ shop.page.openingHours }}</div>
        </div>
        <div class="alert alert-success info-sent" *ngIf="sent">{{ 'Thank you. We received your message and will answer you as soon as we can.' | translate }}</div>
        <form *ngIf="!sent" class="card" style="padding:1rem;" (ngSubmit)="send()" #f="ngForm">
          <h4 style="margin:0 0 0.6rem;">{{ 'Send us a message' | translate }}</h4>
          <div class="alert alert-danger info-form-error" *ngIf="formError">{{ formError }}</div>
          <div style="display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:0.6rem;">
            <div class="form-group"><label class="form-label">{{ 'Your name' | translate }} *</label><input class="form-control info-name" name="name" maxlength="80" [(ngModel)]="form.name" required /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control info-email" name="email" type="email" maxlength="160" inputmode="email" [(ngModel)]="form.email" /></div>
            <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control info-phone" name="phone" maxlength="40" inputmode="tel" [(ngModel)]="form.phone" /></div>
            <div class="form-group"><label class="form-label">{{ 'Order code (if it is about an order)' | translate }}</label><input class="form-control info-order" name="orderCode" maxlength="20" [(ngModel)]="form.orderCode" /></div>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin:-0.2rem 0 0.5rem;">{{ 'Give an e-mail address or a phone number so we can answer you.' | translate }}</div>
          <div class="form-group"><label class="form-label">{{ 'Subject' | translate }}</label><input class="form-control info-subject" name="subject" maxlength="120" [(ngModel)]="form.subject" /></div>
          <div class="form-group"><label class="form-label">{{ 'Your message' | translate }} *</label><textarea class="form-control info-message" name="message" rows="6" maxlength="2000" [(ngModel)]="form.message" required></textarea></div>
          <input type="text" name="website" tabindex="-1" autocomplete="off" aria-hidden="true" [(ngModel)]="form.website" style="position:absolute; left:-5000px; width:1px; height:1px; opacity:0;" />
          <div><button class="btn btn-primary info-send" type="submit" [disabled]="busy">{{ 'Send the message' | translate }}</button></div>
        </form>
      </ng-container>

      <nav class="info-links mt-4" *ngIf="links.length" style="display:flex; flex-wrap:wrap; gap:0.4rem 1.2rem; font-size:0.9rem; border-top:1px solid rgba(128,128,128,0.3); padding-top:0.8rem;">
        <a *ngFor="let l of links" class="info-link" [routerLink]="['/shop/info', l.slug]" [style.font-weight]="l.slug === slug ? 700 : 400">{{ l.title }}</a>
        <a class="info-link" routerLink="/shop/info/contact" [style.font-weight]="slug === 'contact' ? 700 : 400">{{ 'Contact us' | translate }}</a>
      </nav>
    </div>
  `,
})
export class ShopInfoComponent implements OnInit {
  slug = '';
  page: InfoPage | null = null;
  blocks: Block[] = [];
  links: InfoLink[] = [];
  shop: Shop | null = null;
  error: string | null = null;
  formError: string | null = null;
  sent = false;
  busy = false;
  form = { name: '', email: '', phone: '', subject: '', message: '', orderCode: '', language: 'en', website: '' };

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute, private lang: LanguageService) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((m) => {
      this.slug = m.get('slug') ?? '';
      this.load();
    });
  }

  private load(): void {
    this.page = null;
    this.blocks = [];
    this.error = null;
    this.sent = false;
    window.scrollTo({ top: 0 });
    const lang = this.lang.current === 'fr' ? 'fr' : 'en';
    this.form.language = lang;
    const order = this.route.snapshot.queryParamMap.get('order');
    if (order && !this.form.orderCode) this.form.orderCode = order;
    this.api.shop().subscribe({ next: (s) => (this.shop = s), error: () => {} });
    this.api.infoLinks(lang).subscribe({ next: (l) => (this.links = l), error: () => {} });
    if (this.slug === 'contact') return;
    this.api.infoPage(this.slug, lang).subscribe({
      next: (p) => {
        this.page = p;
        this.blocks = parse(p.body);
      },
      error: (e) => (this.error = e?.status === 404 ? T('This page is not available.') : (e?.error?.message ?? T('The shop is not available right now.'))),
    });
  }

  send(): void {
    this.formError = null;
    if (!this.form.name.trim()) { this.formError = T('Enter your name.'); return; }
    if (!this.form.email.trim() && this.form.phone.replace(/\D/g, '').length < 6) { this.formError = T('Give an e-mail address or a phone number so we can answer you.'); return; }
    if (this.form.message.trim().length < 5) { this.formError = T('Write your message.'); return; }
    this.busy = true;
    this.api.sendContact(this.form).subscribe({
      next: () => { this.busy = false; this.sent = true; window.scrollTo({ top: 0 }); },
      error: (e) => { this.busy = false; this.formError = e?.error?.message ?? T('That did not work'); },
    });
  }
}

/** Text into blocks: "## " heading, "- " bullets (consecutive ones make one list), anything else a paragraph (blank lines separate paragraphs). */
export function parse(body: string): Block[] {
  const out: Block[] = [];
  let para: string[] = [];
  let list: string[] | null = null;
  const flush = (): void => {
    if (para.length) out.push({ p: para.join('\n') });
    para = [];
    if (list) out.push({ ul: list });
    list = null;
  };
  for (const raw of body.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line) { flush(); continue; }
    if (line.startsWith('## ')) { flush(); out.push({ h: line.substring(3).trim() }); continue; }
    if (line.startsWith('- ')) {
      if (para.length) { out.push({ p: para.join('\n') }); para = []; }
      (list ??= []).push(line.substring(2).trim());
      continue;
    }
    if (list) { out.push({ ul: list }); list = null; }
    para.push(line);
  }
  flush();
  return out;
}
