import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RestaurantOnlineApiService } from '../core/api/restaurant-online-api.service';
import { OnlineTrack } from '../core/models';

/** Where a guest follows an online or QR order by its code. Refreshes by itself. */
@Component({
  selector: 'app-order-track',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="ot-page" style="max-width:520px; margin:0 auto; padding:1rem 0.75rem 3rem;">
      <div class="alert alert-danger ot-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!t && !error" class="text-muted">{{ 'Looking for your order…' | translate }}</div>
      <ng-container *ngIf="t">
        <div style="text-align:center;" class="mb-3">
          <div class="text-muted">{{ t.restaurantName }}</div>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'Your order code' | translate }}</div>
          <div class="ot-code" style="font-size:2rem; font-weight:700; letter-spacing:0.12em;">{{ t.code }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem; text-align:center;" [style.borderColor]="bad() ? '#dc2626' : done() ? '#16a34a' : ''">
          <div class="ot-stage" style="font-weight:700;">{{ stageTitle() }}</div>
          <div class="ot-text mt-1">{{ t.stageText }}</div>
          <div class="ot-eta text-muted mt-1" *ngIf="t.etaMinutes && !bad() && !done() && t.mode !== 'TABLE'">{{ 'About {p0} minutes' | translate: { p0: (t.etaMinutes) } }}</div>
          <div class="ot-reason mt-1" *ngIf="t.rejectReason" style="color:#b91c1c;">{{ t.rejectReason }}</div>
          <div class="flex gap-1 mt-2" *ngIf="!bad()" style="justify-content:center;">
            <span *ngFor="let s of steps()" class="ot-step" [style.background]="s.reached ? '#16a34a' : '#d1d5db'" style="height:6px; flex:1; border-radius:3px;" [title]="s.label"></span>
          </div>
          <div class="text-muted mt-1" style="font-size:0.75rem;" *ngIf="!bad()">{{ steps().map(labelOf).join(' → ') }}</div>
        </div>

        <div class="card mb-3" style="padding:0.75rem;">
          <div *ngFor="let l of t.lines" class="ot-line flex justify-between" style="padding:0.2rem 0;">
            <div>{{ l.quantity }} × {{ l.name }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="l.options">{{ l.options }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="l.notes">“{{ l.notes }}”</div></div>
            <div>{{ l.lineTotal | number: '1.0-2' }}</div>
          </div>
          <div class="flex justify-between" *ngIf="t.deliveryFee > 0" style="padding-top:0.3rem;"><span>{{ 'Delivery fee ({p0})' | translate: { p0: (t.zoneName) } }}</span><span>{{ t.deliveryFee | number: '1.0-2' }}</span></div>
          <hr />
          <div class="flex justify-between"><strong>{{ 'Total (estimate)' | translate }}</strong><strong class="ot-total">{{ t.total | number: '1.0-2' }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Includes tax and service charge. {p0}' | translate: { p0: (t.mode === 'TABLE' ? 'Pay at the table.' : t.mode === 'DELIVERY' ? 'Pay on delivery.' : 'Pay when you collect.') } }}</div>
        </div>

        <div class="flex gap-2" style="flex-wrap:wrap; justify-content:center;">
          <button class="btn btn-outline-danger ot-cancel" *ngIf="t.stage === 'WAITING'" [disabled]="busy" (click)="cancel()">{{ 'Cancel this order' | translate }}</button>
          <a class="btn btn-primary ot-more" *ngIf="token" [routerLink]="['/menu', token]">{{ 'Order something else' | translate }}</a>
          <a class="btn btn-primary ot-more" *ngIf="!token && t.mode !== 'TABLE'" routerLink="/order">{{ 'Order again' | translate }}</a>
        </div>
      </ng-container>
    </div>
  `,
})
export class OrderTrackComponent implements OnInit, OnDestroy {
  t: OnlineTrack | null = null;
  error: string | null = null;
  busy = false;
  token: string | null = null;
  private code = '';
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: RestaurantOnlineApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    this.token = this.route.snapshot.queryParamMap.get('t');
    this.load();
    this.poll = setInterval(() => this.load(), 5000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  load(): void {
    this.api.track(this.code).subscribe({
      next: (t) => {
        this.t = t;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'We could not find that order.'),
    });
  }

  bad(): boolean {
    return !!this.t && ['REJECTED', 'CANCELLED', 'EXPIRED'].includes(this.t.stage);
  }

  done(): boolean {
    return !!this.t && ['COMPLETED', 'SERVED'].includes(this.t.stage);
  }

  stageTitle(): string {
    const m: Record<string, string> = {
      WAITING: 'Waiting for confirmation', ACCEPTED: 'Accepted', PREPARING: 'Being prepared', READY: 'Ready', ON_THE_WAY: 'On its way',
      SERVED: 'Served', COMPLETED: 'Complete', REJECTED: 'Not accepted', CANCELLED: 'Cancelled', EXPIRED: 'No answer',
    };
    return m[this.t?.stage ?? ''] ?? '';
  }

  steps(): { label: string; reached: boolean }[] {
    if (!this.t) return [];
    const labels = this.t.mode === 'DELIVERY' ? ['Sent', 'Accepted', 'Preparing', 'On its way', 'Delivered'] : this.t.mode === 'TABLE' ? ['Sent', 'Accepted', 'Preparing', 'Ready', 'Served'] : ['Sent', 'Accepted', 'Preparing', 'Ready', 'Collected'];
    const order = ['WAITING', 'ACCEPTED', 'PREPARING', this.t.mode === 'DELIVERY' ? 'ON_THE_WAY' : 'READY', 'COMPLETED'];
    let idx = order.indexOf(this.t.stage);
    if (this.t.stage === 'SERVED') idx = 4;
    if (this.t.stage === 'READY' && this.t.mode === 'DELIVERY') idx = 2;
    return labels.map((label, i) => ({ label, reached: i <= idx }));
  }

  labelOf = (s: { label: string }) => s.label;

  cancel(): void {
    this.busy = true;
    this.api.cancel(this.code).subscribe({
      next: (t) => {
        this.t = t;
        this.busy = false;
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not cancel the order.';
      },
    });
  }
}
