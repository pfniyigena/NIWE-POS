import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOnlineApiService } from '../core/api/restaurant-online-api.service';
import { RestaurantOrderApiService, StaffOption } from '../core/api/restaurant-order-api.service';
import { ListState, emptyFilter } from '../core/api/list-state';
import { PagerComponent } from '../shared/pager.component';
import { OnlineOrder } from '../core/models';

/**
 * Restaurant > Online orders: requests from the table QR codes and the ordering page. Accepting one puts it on a real bill and sends it to the kitchen;
 * rejecting needs a reason the guest will see. The list refreshes by itself so it can stay open on a counter screen.
 */
@Component({
  selector: 'app-online-orders',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Online orders' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/online-setup">Delivery areas &amp; QR codes</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/settings">{{ 'Settings' | translate }}</a>
        </div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button class="btn btn-sm oo-tab oo-tab-PENDING" [class.btn-primary]="tab === 'PENDING'" [class.btn-outline-secondary]="tab !== 'PENDING'" (click)="setTab('PENDING')">{{ 'Waiting for an answer' | translate }} <span class="oo-pending-n" *ngIf="pending > 0">({{ pending }})</span></button>
        <button class="btn btn-sm oo-tab oo-tab-ACCEPTED" [class.btn-primary]="tab === 'ACCEPTED'" [class.btn-outline-secondary]="tab !== 'ACCEPTED'" (click)="setTab('ACCEPTED')">{{ 'Accepted' | translate }}</button>
        <button class="btn btn-sm oo-tab oo-tab-REJECTED" [class.btn-primary]="tab === 'REJECTED'" [class.btn-outline-secondary]="tab !== 'REJECTED'" (click)="setTab('REJECTED')">{{ 'Rejected' | translate }}</button>
        <button class="btn btn-sm oo-tab oo-tab-ALL" [class.btn-primary]="tab === 'ALL'" [class.btn-outline-secondary]="tab !== 'ALL'" (click)="setTab('ALL')">{{ 'Everything' | translate }}</button>
        <select class="form-select oo-mode" style="width:150px;" [(ngModel)]="mode" name="oom" (ngModelChange)="reload()">
          <option value="">{{ 'All types' | translate }}</option><option value="TABLE">{{ 'At table' | translate }}</option><option value="TAKEAWAY">{{ 'Takeaway' | translate }}</option><option value="DELIVERY">{{ 'Delivery' | translate }}</option>
        </select>
        <input class="form-control oo-search" style="width:200px;" [placeholder]="'Search code, name, phone' | translate" [(ngModel)]="q" name="ooq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card oo-card mb-2" *ngFor="let o of list.rows" style="padding:0.75rem;" [style.borderColor]="o.status === 'PENDING' && o.waitingMinutes >= 5 ? '#dc2626' : ''">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="oo-code">{{ o.code }}</strong>
            <span class="badge oo-type" style="margin-left:0.4rem;">{{ typeLabel(o) }}</span>
            <span class="oo-status text-muted" style="margin-left:0.4rem;">{{ pretty(o.status) }}<ng-container *ngIf="o.decidedBy"> · {{ o.decidedBy }}</ng-container></span>
            <div class="oo-who" *ngIf="o.mode !== 'TABLE'">{{ o.customerName }} · {{ o.customerPhone }}<span class="text-muted" *ngIf="o.customerEmail"> · {{ o.customerEmail }}</span></div>
            <div class="oo-who" *ngIf="o.mode === 'TABLE' && o.customerName && o.customerName !== o.tableName">{{ o.customerName }}</div>
            <div class="text-muted" *ngIf="o.customerAddress">📍 {{ o.zoneName ? o.zoneName + ' — ' : '' }}{{ o.customerAddress }}</div>
            <div class="text-muted" *ngIf="o.notes">“{{ o.notes }}”</div>
          </div>
          <div style="text-align:right;">
            <div class="oo-total"><strong>{{ o.estimatedTotal | number: '1.0-2' }}</strong></div>
            <div class="text-muted" style="font-size:0.8rem;">{{ '{p0} · {p1} min{p2}' | translate: { p0: (o.createdAt | date: 'shortTime'), p1: (o.waitingMinutes), p2: (o.status === 'PENDING' ? ' waiting' : '') } }}</div>
            <a *ngIf="o.restaurantOrderId" [routerLink]="['/admin/restaurant/orders', o.restaurantOrderId]">{{ 'Open the bill' | translate }}</a>
          </div>
        </div>
        <ul class="oo-lines" style="margin:0.4rem 0; padding-left:1.1rem;">
          <li *ngFor="let l of o.lines">{{ l.quantity }} × {{ l.name }}<span class="text-muted" *ngIf="l.options"> ({{ l.options }})</span><span class="text-muted" *ngIf="l.notes"> — “{{ l.notes }}”</span></li>
          <li *ngIf="o.deliveryFee > 0">{{ 'Delivery fee {p0}' | translate: { p0: (o.deliveryFee | number: '1.0-2') } }}</li>
        </ul>
        <div class="oo-problem" style="color:#b45309;" *ngIf="o.problem">⚠ {{ o.problem }}</div>
        <div class="text-muted" *ngIf="o.rejectReason">{{ 'Reason: {p0}' | translate: { p0: (o.rejectReason) } }}</div>
        <div class="text-muted" *ngIf="o.status === 'ACCEPTED' && o.etaMinutes && o.mode !== 'TABLE'">{{ 'Promised in about {p0} min' | translate: { p0: (o.etaMinutes) } }}</div>

        <div *ngIf="o.status === 'PENDING'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" *ngIf="o.mode !== 'TABLE'"><label class="form-label">{{ 'Ready in (min)' | translate }}</label>
            <input class="form-control oo-eta" type="number" min="1" max="480" style="width:110px;" [(ngModel)]="eta[o.id]" [name]="'eta' + o.id" [placeholder]="'usual' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Waiter' | translate }}</label>
            <select class="form-select oo-server" style="width:170px;" [(ngModel)]="server[o.id]" [name]="'srv' + o.id"><option [ngValue]="null">{{ '— none —' | translate }}</option><option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option></select></div>
          <button class="btn btn-primary oo-accept" [disabled]="busy" (click)="accept(o, false)">{{ o.problem ? ('Accept without the missing dish' | translate) : ('Accept' | translate) }}</button>
          <input class="form-control oo-reason" style="width:200px;" maxlength="120" [placeholder]="'Reason, if you refuse' | translate" [(ngModel)]="reason[o.id]" [name]="'rsn' + o.id" />
          <button class="btn btn-outline-danger oo-reject" [disabled]="busy" (click)="reject(o)">{{ 'Reject' | translate }}</button>
        </div>
      </div>
      <p class="text-muted oo-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'PENDING' ? ('Nothing is waiting for an answer.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class OnlineOrdersComponent implements OnInit, OnDestroy {
  tab = 'PENDING';
  mode = '';
  q = '';
  pending = 0;
  error: string | null = null;
  busy = false;
  staff: StaffOption[] = [];
  eta: Record<number, number | null> = {};
  server: Record<number, number | null> = {};
  reason: Record<number, string> = {};
  list = new ListState<OnlineOrder>((f, p, sz) => this.api.page(f, p, sz, this.mode), 'Failed to load the online orders');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: RestaurantOnlineApiService, private orders: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.orders.listActiveStaff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 8000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  reload(): void {
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab === 'ALL' ? 'ALL' : this.tab === 'REJECTED' ? '' : this.tab;
    if (this.tab === 'REJECTED') f.status = 'REJECTED';
    this.list.apply(f);
    this.count();
  }

  count(): void {
    this.api.pendingCount().subscribe({ next: (r) => (this.pending = r.pending), error: () => {} });
  }

  typeLabel(o: OnlineOrder): string {
    return o.mode === 'TABLE' ? 'Table ' + (o.tableName ?? '') : o.mode === 'TAKEAWAY' ? 'Takeaway' : 'Delivery';
  }

  pretty(s: string): string {
    return s.charAt(0) + s.slice(1).toLowerCase();
  }

  accept(o: OnlineOrder, _again: boolean): void {
    this.busy = true;
    this.error = null;
    this.api.accept(o.id, { etaMinutes: this.eta[o.id] || null, serverId: this.server[o.id] ?? null, skipUnavailable: !!o.problem }).subscribe({
      next: () => {
        this.busy = false;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not accept the order';
        this.list.load();
      },
    });
  }

  reject(o: OnlineOrder): void {
    this.busy = true;
    this.error = null;
    this.api.reject(o.id, this.reason[o.id] ?? '').subscribe({
      next: () => {
        this.busy = false;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not reject the order';
      },
    });
  }
}
