import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnlineStoreApiService, ShopTrack } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/** Where a customer follows a shop order by its code. Refreshes by itself. */
@Component({
  selector: 'app-shop-track',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="st-page" style="max-width:520px; margin:0 auto; padding:1rem 0.75rem 3rem;">
      <div class="alert alert-danger st-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!t && !error" class="text-muted">{{ 'Looking for your order…' | translate }}</div>
      <ng-container *ngIf="t">
        <div style="text-align:center;" class="mb-3">
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Your order code' | translate }}</div>
          <div class="st-code" style="font-size:2rem; font-weight:700; letter-spacing:0.12em;">{{ t.code }}</div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Keep this code to follow your order.' | translate }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem; text-align:center;" [style.borderColor]="bad() ? '#dc2626' : t.status === 'COMPLETED' ? '#16a34a' : ''">
          <div class="st-stage" style="font-weight:700;">{{ stageTitle() | translate }}</div>
          <div class="st-reason mt-1" *ngIf="t.rejectReason" style="color:#b91c1c;">{{ t.rejectReason }}</div>
          <div class="flex gap-1 mt-2" *ngIf="!bad()" style="justify-content:center;">
            <span *ngFor="let s of steps()" [style.background]="s.reached ? '#16a34a' : '#d1d5db'" style="height:6px; flex:1; border-radius:3px;" [title]="s.label | translate"></span>
          </div>
        </div>

        <div class="card mb-3" style="padding:0.75rem;">
          <div *ngFor="let l of t.lines" class="flex justify-between" style="padding:0.2rem 0;"><span>{{ l.quantity }} × {{ l.name }}</span><span>{{ l.lineTotal | number: '1.0-2' }}</span></div>
          <div class="flex justify-between" *ngIf="t.deliveryFee > 0" style="padding-top:0.3rem;"><span>{{ 'Delivery fee ({p0})' | translate: { p0: (t.zoneName) } }}</span><span>{{ t.deliveryFee | number: '1.0-2' }}</span></div>
          <hr />
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="st-total">{{ t.total | number: '1.0-2' }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;">{{ t.deliveryMethod === 'DELIVERY' ? ('Pay in cash when your order arrives.' | translate) : ('Pay in cash when you collect.' | translate) }}</div>
        </div>

        <div class="flex gap-2" style="flex-wrap:wrap; justify-content:center;">
          <button class="btn btn-outline-danger st-cancel" *ngIf="t.status === 'PLACED'" [disabled]="busy" (click)="cancel()">{{ 'Cancel this order' | translate }}</button>
          <a class="btn btn-primary" routerLink="/shop">{{ 'Order again' | translate }}</a>
        </div>
      </ng-container>
    </div>
  `,
})
export class ShopTrackComponent implements OnInit, OnDestroy {
  t: ShopTrack | null = null;
  error: string | null = null;
  busy = false;
  private code = '';
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    this.load();
    this.poll = setInterval(() => this.load(), 15000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  private load(): void {
    this.api.track(this.code).subscribe({
      next: (t) => { this.t = t; this.error = null; },
      error: (e) => { if (!this.t) this.error = e?.error?.message ?? T('We could not find that order. Check the code.'); },
    });
  }

  bad(): boolean {
    return this.t?.status === 'REJECTED' || this.t?.status === 'CANCELLED';
  }

  stageTitle(): string {
    switch (this.t?.status) {
      case 'PLACED': return 'We received your order. We will confirm it shortly.';
      case 'ACCEPTED': return 'Accepted: we are preparing your order.';
      case 'PACKED': return 'Packed and ready to go.';
      case 'OUT_FOR_DELIVERY': return 'On its way to you.';
      case 'READY_FOR_PICKUP': return 'Ready for you to collect.';
      case 'COMPLETED': return 'Done. Thank you for your order.';
      case 'REJECTED': return 'Sorry, we could not take this order.';
      case 'CANCELLED': return 'This order was cancelled.';
      default: return this.t?.status ?? '';
    }
  }

  steps(): { label: string; reached: boolean }[] {
    const delivery = this.t?.deliveryMethod === 'DELIVERY';
    const order = ['PLACED', 'ACCEPTED', delivery ? 'OUT_FOR_DELIVERY' : 'READY_FOR_PICKUP', 'COMPLETED'];
    const labels = ['Received', 'Accepted', delivery ? 'On its way' : 'Ready', 'Done'];
    const at = Math.max(order.indexOf(this.t?.status ?? ''), this.t?.status === 'PACKED' ? 1 : 0);
    return order.map((_, i) => ({ label: labels[i], reached: i <= at }));
  }

  cancel(): void {
    if (!confirm(T('Cancel this order?'))) return;
    this.busy = true;
    this.api.cancel(this.code).subscribe({
      next: (t) => { this.t = t; this.busy = false; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
