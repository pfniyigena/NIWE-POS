import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicRealEstateApiService, PublicCard, PublicInfo } from '../core/api/public-realestate-api.service';
import { PublicSettingsApiService } from '../core/api/public-settings-api.service';
import { PROPERTY_TYPES, typeLabel } from '../core/api/realestate-api.service';

import { T } from '../core/i18n/t';
/**
 * The public listings page (/listings): no login. Cards for every property marked "Show on the public website" that is Available, with filters and paging.
 * Owner details, street addresses and map positions never reach this page (the server leaves them out).
 */
@Component({
  selector: 'app-public-listings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="pl-page">
      <header class="pl-hero">
        <div class="pl-inner">
          <div class="flex align-center gap-2 mb-2">
            <img *ngIf="logoDataUrl" [src]="logoDataUrl" alt="" style="height:44px; width:44px; object-fit:contain; border-radius:0.4rem; background:#fff;" />
            <a routerLink="/" class="pl-brand">{{ businessName || ('Properties' | translate) }}</a>
          </div>
          <h1 class="pl-title">{{ info?.title || ('Properties for sale and rent' | translate) }}</h1>
          <p class="pl-intro" *ngIf="info?.intro">{{ info?.intro }}</p>
          <div class="pl-contact" *ngIf="info?.phone || info?.email">
            <span *ngIf="info?.phone">📞 {{ info?.phone }}</span><span *ngIf="info?.email"> · ✉️ {{ info?.email }}</span>
          </div>
        </div>
      </header>

      <main class="pl-inner pl-main">
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <form class="pl-filters" (ngSubmit)="search()">
          <select class="form-select" name="pu" [(ngModel)]="purpose" (ngModelChange)="search()"><option value="">{{ 'Buy or rent' | translate }}</option><option value="SALE">{{ 'Buy' | translate }}</option><option value="RENT">{{ 'Rent' | translate }}</option></select>
          <select class="form-select" name="ty" [(ngModel)]="type" (ngModelChange)="search()"><option value="">{{ 'Any type' | translate }}</option><option *ngFor="let t of types" [value]="t.value">{{ t.label }}</option></select>
          <select class="form-select" name="ar" [(ngModel)]="area" (ngModelChange)="search()"><option value="">{{ 'Any area' | translate }}</option><option *ngFor="let a of info?.areas" [value]="a">{{ a }}</option></select>
          <select class="form-select" name="bd" [(ngModel)]="bedrooms" (ngModelChange)="search()"><option [ngValue]="null">{{ 'Any bedrooms' | translate }}</option><option *ngFor="let n of [1,2,3,4,5]" [ngValue]="n">{{ '{p0}+ bedrooms' | translate: { p0: (n) } }}</option></select>
          <input class="form-control" type="number" min="0" name="mn" [placeholder]="'Min price' | translate" [(ngModel)]="minPrice" (change)="search()" />
          <input class="form-control" type="number" min="0" name="mx" [placeholder]="'Max price' | translate" [(ngModel)]="maxPrice" (change)="search()" />
          <input class="form-control" name="q" [placeholder]="'Search' | translate" [(ngModel)]="q" />
          <button class="btn btn-primary" type="submit">{{ 'Search' | translate }}</button>
        </form>

        <div class="text-muted mb-2" *ngIf="page">{{ page.total }} {{ page.total === 1 ? 'property' : 'properties' }}</div>
        <div class="pl-grid">
          <a *ngFor="let c of cards" class="card pl-card" [routerLink]="['/listings', c.id]">
            <div class="pl-photo">
              <img *ngIf="c.coverPhotoId" [src]="photo(c)" [alt]="c.title" loading="lazy" />
              <div *ngIf="!c.coverPhotoId" class="pl-nophoto">🏠</div>
              <span class="pl-tag" *ngIf="c.featured">{{ 'Featured' | translate }}</span>
              <span class="pl-tag pl-tag-right">{{ c.purpose === 'RENT' ? 'To rent' : c.purpose === 'SALE' ? ('For sale' | translate) : ('Sale or rent' | translate) }}</span>
            </div>
            <div class="pl-body">
              <div class="pl-price">{{ priceText(c) }}</div>
              <div class="pl-name">{{ c.title }}</div>
              <div class="text-muted" style="font-size:0.9rem;">{{ typeName(c.type) }}<span *ngIf="c.area"> · {{ c.area }}</span></div>
              <div class="pl-facts">
                <span *ngIf="c.bedrooms != null">🛏 {{ c.bedrooms }}</span>
                <span *ngIf="c.bathrooms != null">🛁 {{ c.bathrooms }}</span>
                <span *ngIf="c.sizeSqm != null">{{ '📐 {p0} m²' | translate: { p0: (c.sizeSqm | number: '1.0-0') } }}</span>
              </div>
            </div>
          </a>
        </div>
        <p class="text-muted" *ngIf="page && !cards.length" style="text-align:center; padding:2rem 0;">{{ 'No properties match. Try fewer filters.' | translate }}</p>

        <div class="flex gap-2 mt-3" style="justify-content:center;" *ngIf="page && page.total > page.size">
          <button class="btn btn-outline-secondary" type="button" [disabled]="page.page === 0" (click)="go(page.page - 1)">{{ '← Previous' | translate }}</button>
          <span class="text-muted" style="align-self:center;">{{ 'Page {p0} of {p1}' | translate: { p0: (page.page + 1), p1: (pages()) } }}</span>
          <button class="btn btn-outline-secondary" type="button" [disabled]="page.page + 1 >= pages()" (click)="go(page.page + 1)">{{ 'Next →' | translate }}</button>
        </div>
      </main>
      <footer class="pl-foot text-muted">&copy; {{ year }} {{ businessName }} · &copy; 2026 NIWE GROUP</footer>
    </div>
  `,
  styles: [`
    .pl-page { min-height: 100vh; display: flex; flex-direction: column; }
    .pl-hero { background: linear-gradient(135deg, var(--surface) 0%, var(--bg) 100%); border-bottom: 1px solid var(--border); padding: 2rem 1rem; }
    .pl-inner { max-width: 1100px; margin: 0 auto; width: 100%; }
    .pl-main { flex: 1; padding: 1.5rem 1rem 2.5rem; }
    .pl-brand { color: inherit; font-weight: 600; text-decoration: none; font-size: 1.1rem; }
    .pl-title { margin: 0.3rem 0; font-size: 1.9rem; }
    .pl-intro { color: var(--muted); max-width: 46rem; margin: 0 0 0.6rem; }
    .pl-contact { font-size: 0.95rem; }
    .pl-filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.5rem; margin-bottom: 1rem; }
    .pl-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr)); gap: 1rem; }
    .pl-card { padding: 0; overflow: hidden; text-decoration: none; color: inherit; transition: transform 0.15s ease, border-color 0.15s ease; }
    .pl-card:hover { transform: translateY(-2px); border-color: var(--accent); }
    .pl-photo { position: relative; aspect-ratio: 16 / 10; background: rgba(127,127,127,0.15); }
    .pl-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .pl-nophoto { height: 100%; display: flex; align-items: center; justify-content: center; font-size: 2.5rem; }
    .pl-tag { position: absolute; left: 0.5rem; top: 0.5rem; background: #b45309; color: #fff; font-size: 0.72rem; padding: 0.15rem 0.5rem; border-radius: 3px; }
    .pl-tag-right { left: auto; right: 0.5rem; background: rgba(15, 23, 42, 0.8); }
    .pl-body { padding: 0.8rem 0.9rem 1rem; }
    .pl-price { font-size: 1.2rem; font-weight: 600; }
    .pl-name { font-weight: 500; margin: 0.15rem 0; }
    .pl-facts { display: flex; gap: 0.9rem; margin-top: 0.4rem; font-size: 0.9rem; }
    .pl-foot { text-align: center; padding: 1.2rem 1rem; border-top: 1px solid var(--border); }
  `],
})
export class PublicListingsComponent implements OnInit {
  info: PublicInfo | null = null;
  page: { total: number; page: number; size: number } | null = null;
  cards: PublicCard[] = [];
  businessName: string | null = null;
  logoDataUrl: string | null = null;
  year = new Date().getFullYear();
  types = PROPERTY_TYPES;
  purpose = '';
  type = '';
  area = '';
  bedrooms: number | null = null;
  minPrice: number | null = null;
  maxPrice: number | null = null;
  q = '';
  error: string | null = null;

  constructor(private api: PublicRealEstateApiService, private branding: PublicSettingsApiService) {}

  ngOnInit(): void {
    this.branding.branding().subscribe({ next: (b) => { this.businessName = b.businessName; this.logoDataUrl = b.logoDataUrl; }, error: () => {} });
    this.api.info().subscribe({ next: (i) => (this.info = i), error: () => {} });
    this.load(0);
  }

  search(): void { this.load(0); }
  go(p: number): void { this.load(p); window.scrollTo({ top: 0, behavior: 'smooth' }); }
  pages(): number { return this.page ? Math.max(1, Math.ceil(this.page.total / this.page.size)) : 1; }

  private load(p: number): void {
    this.error = null;
    this.api.list({ purpose: this.purpose, type: this.type, area: this.area, bedrooms: this.bedrooms, minPrice: this.minPrice, maxPrice: this.maxPrice, q: this.q.trim(), page: p, size: 12 }).subscribe({
      next: (r) => { this.cards = r.items; this.page = { total: r.total, page: r.page, size: r.size }; },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the properties')),
    });
  }

  photo(c: PublicCard): string { return this.api.photoUrl(c.id, c.coverPhotoId!); }
  typeName(t: string): string { return typeLabel(t); }

  priceText(c: PublicCard): string {
    const f = (n: number) => Math.round(n).toLocaleString();
    const parts: string[] = [];
    if (c.salePrice != null) parts.push(f(c.salePrice));
    if (c.rentPrice != null) parts.push(`${f(c.rentPrice)} / month`);
    return parts.length ? parts.join(' or ') : 'Price on request';
  }
}
