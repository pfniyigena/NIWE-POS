import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { OnlineStoreApiService, StoreReview } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Reviews: what customers wrote about products they bought on a completed order. A review is shown on the shop only after it is approved;
 * a hidden one is kept but not shown; a delete removes it for good.
 */
@Component({
  selector: 'app-store-reviews',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Reviews' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/newsletter">{{ 'Newsletter' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'Customers can review a product after their order is completed. A review is shown on the shop only after you approve it.' | translate }}</p>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm rv-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span *ngIf="t.key === 'PENDING' && pending > 0"> ({{ pending }})</span>
        </button>
        <input class="form-control rv-search" style="width:220px;" [placeholder]="'Search product, name, order' | translate" [(ngModel)]="q" name="rvq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card rv-card mb-2" *ngFor="let r of list.rows" style="padding:0.75rem;">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="rv-product">{{ r.productName }}</strong>
            <span class="badge rv-status" style="margin-left:0.4rem;" [style.color]="color(r)">{{ label(r.status) | translate }}</span>
            <div><span style="color:#d97706;">{{ stars(r.rating) }}</span> · {{ r.reviewerName }}<span class="text-muted" *ngIf="r.orderCode"> · {{ 'Order {p0}' | translate: { p0: r.orderCode } }}</span></div>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ r.createdAt | date: 'short' }}<span *ngIf="r.moderatedBy"> · {{ r.moderatedBy }}</span></div>
        </div>
        <p class="rv-text mt-1" *ngIf="r.text" style="margin-bottom:0;">{{ r.text }}</p>
        <p class="text-muted mt-1" *ngIf="!r.text" style="margin-bottom:0;">{{ 'Rating only, no words.' | translate }}</p>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm btn-primary rv-approve" *ngIf="r.status !== 'APPROVED'" [disabled]="busy" (click)="approve(r)">{{ 'Approve' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary rv-hide" *ngIf="r.status !== 'HIDDEN'" [disabled]="busy" (click)="hide(r)">{{ 'Hide' | translate }}</button>
          <button class="btn btn-sm btn-outline-danger rv-delete" [disabled]="busy" (click)="remove(r)">{{ 'Delete' | translate }}</button>
        </div>
      </div>
      <p class="text-muted rv-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'PENDING' ? ('No review is waiting.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreReviewsComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'PENDING', label: 'Waiting' },
    { key: 'APPROVED', label: 'Shown' },
    { key: 'HIDDEN', label: 'Hidden' },
    { key: '', label: 'Everything' },
  ];
  tab = 'PENDING';
  q = '';
  pending = 0;
  error: string | null = null;
  message: string | null = null;
  busy = false;
  list = new ListState<StoreReview>((f, p, sz) => this.api.reviewsPage(f, p, sz), 'Failed to load the reviews');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 20000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  label(s: string): string {
    return ({ PENDING: 'Waiting', APPROVED: 'Shown', HIDDEN: 'Hidden' } as Record<string, string>)[s] ?? s;
  }

  color(r: StoreReview): string {
    return r.status === 'APPROVED' ? '#15803d' : r.status === 'HIDDEN' ? '#6b7280' : '#b45309';
  }

  stars(n: number): string {
    return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n));
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  reload(): void {
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab;
    this.list.apply(f);
    this.count();
  }

  count(): void {
    this.api.pendingCount().subscribe({ next: (c) => (this.pending = c.reviewsPending ?? 0), error: () => {} });
  }

  private run(call: import('rxjs').Observable<unknown>, done: string): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.message = done;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
        this.list.load();
      },
    });
  }

  approve(r: StoreReview): void {
    this.run(this.api.approveReview(r.id), T('The review is now shown on the shop.'));
  }

  hide(r: StoreReview): void {
    this.run(this.api.hideReview(r.id), T('The review is hidden.'));
  }

  remove(r: StoreReview): void {
    if (!confirm(T('Delete this review for good?'))) return;
    this.run(this.api.deleteReview(r.id), T('The review was deleted.'));
  }
}
