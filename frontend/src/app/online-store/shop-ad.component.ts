import { Component, ElementRef, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { OnlineStoreApiService, ShopAd } from '../core/api/online-store-api.service';

/**
 * One advert space of the shop (HOME_TOP, PRODUCT or ORDER). With several ads for the space they take turns (starting at a random one, so every ad
 * gets its share). An ad is counted as shown only once it is really on screen, and a click goes through the server, which counts it and sends the
 * visitor on to the advertiser. Every ad is labelled "Advertisement".
 */
@Component({
  selector: 'app-shop-ad',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <div class="shop-ad" *ngIf="current as ad" [attr.data-placement]="placement" (mouseenter)="hold = true" (mouseleave)="hold = false"
         style="margin:1rem 0 0.5rem; text-align:center;">
      <div class="shop-ad-label text-muted" style="font-size:0.68rem; text-transform:uppercase; letter-spacing:0.06em; margin-bottom:0.2rem;">{{ 'Advertisement' | translate }}</div>
      <a class="shop-ad-link" [href]="href(ad)" target="_blank" rel="noopener sponsored nofollow" style="display:block;">
        <img class="shop-ad-img" [src]="src(ad)" [alt]="ad.altText || ad.title" style="width:100%; object-fit:contain; border-radius:8px; background:#f3f4f6;" [style.max-height.px]="maxHeight" />
      </a>
      <div class="flex gap-1" *ngIf="ads.length > 1" style="justify-content:center; margin-top:0.3rem;">
        <button type="button" class="shop-ad-dot" *ngFor="let a of ads; let i = index" [attr.aria-label]="(i + 1) + ' / ' + ads.length" (click)="show(i)"
                style="width:8px; height:8px; border-radius:50%; border:0; padding:0; cursor:pointer;" [style.background]="i === index ? '#6b7280' : '#d1d5db'"></button>
      </div>
    </div>
  `,
})
export class ShopAdComponent implements OnInit, OnChanges, OnDestroy {
  @Input() ads: ShopAd[] = [];
  /** HOME_TOP, PRODUCT or ORDER. */
  @Input() placement = '';

  index = 0;
  hold = false;
  private visible = false;
  private reported = new Set<number>();
  private timer: any = null;
  private observer: IntersectionObserver | null = null;

  constructor(private api: OnlineStoreApiService, private host: ElementRef<HTMLElement>) {}

  get current(): ShopAd | null {
    return this.ads.length ? this.ads[this.index % this.ads.length] : null;
  }

  /** How tall the picture may be: the top of the home page is the biggest space. */
  get maxHeight(): number {
    return this.placement === 'HOME_TOP' ? 220 : this.placement === 'PRODUCT' ? 160 : 140;
  }

  src(a: ShopAd): string {
    return this.api.imageUrl(a.imageUrl);
  }

  href(a: ShopAd): string {
    return `${this.api.imageUrl(a.clickUrl)}?p=${encodeURIComponent(this.placement)}`;
  }

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.visible = true;
      return;
    }
    // Counted as shown only when at least half of it is on screen.
    this.observer = new IntersectionObserver((entries) => {
      this.visible = entries.some((e) => e.isIntersecting);
      this.report();
    }, { threshold: 0.5 });
    this.observer.observe(this.host.nativeElement);
  }

  ngOnChanges(): void {
    this.index = this.ads.length > 1 ? Math.floor(Math.random() * this.ads.length) : 0;
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    if (this.ads.length > 1) this.timer = setInterval(() => { if (!this.hold && !document.hidden) { this.index = (this.index + 1) % this.ads.length; this.report(); } }, 7000);
    this.report();
  }

  show(i: number): void {
    this.index = i;
    this.report();
  }

  private report(): void {
    const ad = this.current;
    if (!ad || !this.visible || this.reported.has(ad.id)) return;
    this.reported.add(ad.id);
    this.api.adSeen(this.placement, [ad.id]).subscribe({ error: () => {} });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    this.observer?.disconnect();
  }
}
