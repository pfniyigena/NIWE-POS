import { AfterViewInit, Component, ElementRef, Input, NgZone, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * One row of product cards that slides sideways: Featured, Best sellers, New arrivals, Special offers, You may also like, Recently viewed.
 * The cards stay on one line and keep their size (the row gives each child the same width). The visitor moves the row with the arrow buttons,
 * a horizontal mouse wheel / trackpad swipe, or a finger swipe; the scrollbar is hidden and the movement is smooth.
 * Automatic sliding is off unless `auto` is set (a shop setting); then it moves one screen every few seconds, stops while the visitor points at
 * or touches the row or the tab is hidden, and is never used when the device asks for reduced motion.
 */
@Component({
  selector: 'app-shop-rail',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  styles: [`
    :host { display: block; }
    .rail-wrap { position: relative; }
    .rail { display: flex; gap: 0.75rem; overflow-x: auto; overflow-y: hidden; scroll-snap-type: x proximity; scroll-behavior: smooth; overscroll-behavior-x: contain;
            -webkit-overflow-scrolling: touch; padding: 0.25rem 0.1rem 0.5rem; scrollbar-width: none; -ms-overflow-style: none; }
    .rail::-webkit-scrollbar { display: none; height: 0; }
    .rail ::ng-deep > * { flex: 0 0 var(--rail-item, 210px); width: var(--rail-item, 210px); scroll-snap-align: start; display: flex; min-width: 0; }
    .arrow { position: absolute; top: 42%; z-index: 2; width: 36px; height: 36px; border-radius: 50%; border: 1px solid #d1d5db; background: #fff; color: #111;
             box-shadow: 0 1px 4px rgba(0,0,0,0.25); font-size: 1.3rem; line-height: 1; cursor: pointer; padding: 0; }
    .arrow:disabled { opacity: 0.35; cursor: default; }
    .arrow.prev { left: -6px; }
    .arrow.next { right: -6px; }
  `],
  template: `
    <div class="rail-wrap shop-carousel" (mouseenter)="held = true" (mouseleave)="held = false" (touchstart)="held = true" (touchend)="held = false" (touchcancel)="held = false" (focusin)="held = true" (focusout)="held = false">
      <button type="button" class="arrow prev shop-car-prev" *ngIf="overflow" [disabled]="atStart && !auto" [attr.aria-label]="'Previous products' | translate" (click)="go(-1)">‹</button>
      <div #rail class="rail car-rail" (scroll)="measure()"><ng-content></ng-content></div>
      <button type="button" class="arrow next shop-car-next" *ngIf="overflow" [disabled]="atEnd && !auto" [attr.aria-label]="'Next products' | translate" (click)="go(1)">›</button>
    </div>
  `,
})
export class ShopRailComponent implements AfterViewInit, OnChanges, OnDestroy {
  /** Slide by itself (the shop's setting). */
  @Input() auto = false;
  /** The number of cards: changing it makes the row measure itself again. */
  @Input() count = 0;
  @ViewChild('rail', { static: true }) railRef!: ElementRef<HTMLElement>;

  held = false;
  overflow = false;
  atStart = true;
  atEnd = false;
  private timer: ReturnType<typeof setInterval> | null = null;
  private watcher: ResizeObserver | null = null;

  constructor(private zone: NgZone) {}

  ngAfterViewInit(): void {
    const el = this.railRef.nativeElement;
    if (typeof ResizeObserver !== 'undefined') {
      this.watcher = new ResizeObserver(() => this.zone.run(() => this.measure()));
      this.watcher.observe(el);
    }
    setTimeout(() => this.measure());
    this.syncTimer();
  }

  ngOnChanges(c: SimpleChanges): void {
    if (c['count']) setTimeout(() => this.measure());
    if (c['auto'] && this.railRef) this.syncTimer();
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    this.watcher?.disconnect();
  }

  /** Are there cards beyond the edges? Decides whether the arrows show and which one is dimmed. */
  measure(): void {
    const el = this.railRef.nativeElement;
    this.overflow = el.scrollWidth > el.clientWidth + 4;
    this.atStart = el.scrollLeft <= 4;
    this.atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - 4;
  }

  /** One screen to the left or right. Past the last card, automatic sliding returns to the first one. */
  go(dir: number): void {
    const el = this.railRef.nativeElement;
    const step = Math.max(200, el.clientWidth * 0.8);
    if (dir > 0 && this.atEnd && this.auto) el.scrollTo({ left: 0, behavior: 'smooth' });
    else if (dir < 0 && this.atStart && this.auto) el.scrollTo({ left: el.scrollWidth, behavior: 'smooth' });
    else el.scrollBy({ left: dir * step, behavior: 'smooth' });
  }

  private syncTimer(): void {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
    const calm = typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!this.auto || calm) return;
    this.timer = setInterval(() => {
      if (this.held || document.hidden || !this.overflow) return;
      this.go(1);
    }, 4500);
  }
}
