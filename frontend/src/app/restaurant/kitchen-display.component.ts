import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { KitchenApiService } from '../core/api/kitchen-api.service';
import { MenuApiService } from '../core/api/menu-api.service';
import { KitchenSummary, KitchenTicket, MenuItem } from '../core/models';

/**
 * Restaurant > Kitchen display (Fourier: "Kitchen Order Tickets / Kitchen Display"). Every dish
 * currently SENT/PREPARING/READY across every open order, oldest first. Each ticket runs a clock
 * against the time the dish should take and turns amber, then red. Station tabs filter the feed;
 * "bump" clears a whole order at a station; the 86 board marks dishes sold out. Polls every few
 * seconds so a screen left open stays current.
 */
@Component({
  selector: 'app-kitchen-display',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Kitchen display' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary kd-86-toggle" (click)="toggleBoard()">{{ showBoard ? ('Hide' | translate) : ('86 board' | translate) }}<span *ngIf="summary && summary.soldOut.length > 0"> ({{ summary.soldOut.length }})</span></button>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/kitchen/expo">{{ 'Expo' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">{{ '← Floor plan' | translate }}</a>
        </div>
      </div>

      <div class="kd-summary flex gap-3 mb-3" *ngIf="summary" style="flex-wrap:wrap;">
        <div class="kd-stat"><div class="kd-n kd-s-waiting">{{ summary.waiting }}</div><div class="text-muted">{{ 'waiting' | translate }}</div></div>
        <div class="kd-stat"><div class="kd-n kd-s-cooking">{{ summary.cooking }}</div><div class="text-muted">{{ 'cooking' | translate }}</div></div>
        <div class="kd-stat"><div class="kd-n kd-s-ready">{{ summary.ready }}</div><div class="text-muted">{{ 'ready' | translate }}</div></div>
        <div class="kd-stat" [class.kd-bad]="summary.late > 0"><div class="kd-n kd-s-late">{{ summary.late }}</div><div class="text-muted">{{ 'late' | translate }}</div></div>
        <div class="kd-stat"><div class="kd-n">{{ summary.oldestMinutes }}<small> {{ 'min' | translate }}</small></div><div class="text-muted">{{ 'oldest' | translate }}</div></div>
        <div class="kd-stat" *ngFor="let s of summary.stations"><div class="kd-n" style="font-size:1rem;">{{ s.station }}</div>
          <div class="text-muted" style="font-size:0.8rem;">{{ '{p0} open' | translate: { p0: (s.waiting + s.cooking) } }}<span *ngIf="s.doneToday > 0"> {{ '· avg {p0} min ({p1} done)' | translate: { p0: (s.averagePrepMinutes), p1: (s.doneToday) } }}</span></div></div>
      </div>

      <div class="card mb-3 kd-board" style="padding:1rem;" *ngIf="showBoard">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Sold out (86)' | translate }}</h4>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <input class="form-control kd-86-q" style="max-width:240px;" [placeholder]="'Search the menu' | translate" [(ngModel)]="boardQuery" name="kd86q" />
          <input class="form-control kd-86-reason" style="max-width:260px;" [placeholder]="'Reason (optional)' | translate" [(ngModel)]="boardReason" name="kd86r" />
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm kd-86-btn" *ngFor="let m of boardItems()" [class.btn-danger]="m.soldOut" [class.btn-outline-secondary]="!m.soldOut" (click)="toggleItem(m)">
            {{ m.product?.name }}<span *ngIf="m.soldOut"> {{ '— sold out' | translate }}</span>
          </button>
        </div>
      </div>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="stations.length > 1">
        <button class="btn btn-sm" [class.btn-primary]="activeStation === null" [class.btn-outline-secondary]="activeStation !== null" (click)="activeStation = null">
          {{ 'All ({p0})' | translate: { p0: (tickets.length) } }}
        </button>
        <button class="btn btn-sm kd-tab" *ngFor="let s of stations" [class.btn-primary]="activeStation === s" [class.btn-outline-secondary]="activeStation !== s" (click)="activeStation = s">
          {{ s }} ({{ countForStation(s) }})
        </button>
      </div>
      <div class="ticket-grid">
        <div class="ticket" *ngFor="let t of filteredTickets" [class.ready]="t.kitchenStatus === 'READY'" [class.warn]="urgency(t) === 'WARN'" [class.late]="urgency(t) === 'LATE'">
          <div class="flex justify-between">
            <a [routerLink]="['/admin/restaurant/orders', t.orderId]"><strong>{{ t.tableName }}</strong></a>
            <span class="kd-timer" [style.color]="urgency(t) === 'LATE' ? '#b91c1c' : urgency(t) === 'WARN' ? '#b45309' : ''" style="font-weight:700;">{{ clock(t) }}<small class="text-muted"> {{ '/ {p0}m' | translate: { p0: (t.targetMinutes ?? 15) } }}</small></span>
          </div>
          <div class="text-muted" style="font-size:0.75rem;">{{ t.kitchenStatus }}<span *ngIf="t.seat"> {{ '· seat {p0}' | translate: { p0: (t.seat) } }}</span><span *ngIf="t.guestCount"> {{ '· {p0} guests' | translate: { p0: (t.guestCount) } }}</span></div>
          <div>{{ t.quantity }}× {{ t.description }}<span class="k-course badge" *ngIf="(t.course ?? 1) > 1" style="margin-left:6px;">{{ 'Course {p0}' | translate: { p0: (t.course) } }}</span><span class="kd-remade" *ngIf="(t.remakeCount ?? 0) > 0" style="margin-left:6px; color:#b45309; font-size:0.75rem;">{{ 'REMAKE ×{p0}' | translate: { p0: (t.remakeCount) } }}</span></div>
          <div class="k-options" *ngIf="t.options && t.options.length > 0" style="font-weight:600;">{{ t.options.join(', ') }}</div>
          <div class="text-muted" *ngIf="t.station">{{ t.station }}</div>
          <div class="k-notes" *ngIf="t.notes" style="font-style:italic;">“{{ t.notes }}”</div>
          <div class="k-allergens text-muted" *ngIf="t.allergens" style="font-size:0.8rem;">{{ 'Contains: {p0}' | translate: { p0: (pretty(t.allergens)) } }}</div>
          <div class="k-conflict" *ngIf="t.allergyConflict" style="margin-top:4px; padding:4px 6px; background:#fee2e2; color:#991b1b; border-radius:4px; font-weight:700;">
            {{ '⚠ ALLERGY — guest cannot have {p0}' | translate: { p0: (pretty(t.allergyConflict)) } }}
          </div>
          <div class="k-guest-allergies" *ngIf="t.guestAllergens && !t.allergyConflict" style="font-size:0.8rem; color:#b45309;">{{ 'Guest allergies: {p0}' | translate: { p0: (pretty(t.guestAllergens)) } }}</div>
          <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-outline-secondary kd-start" *ngIf="t.kitchenStatus === 'SENT'" (click)="advance(t, 'PREPARING')">{{ 'Start preparing' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary kd-ready" *ngIf="t.kitchenStatus === 'PREPARING'" (click)="advance(t, 'READY')">{{ 'Mark ready' | translate }}</button>
            <button class="btn btn-sm btn-primary kd-served" *ngIf="t.kitchenStatus === 'READY'" (click)="advance(t, 'SERVED')">{{ 'Mark served' | translate }}</button>
            <button class="btn btn-sm btn-outline-primary kd-bump" *ngIf="canBump(t)" (click)="bump(t)">{{ 'Bump all {p0} for this order' | translate: { p0: (t.station || 'Unassigned') } }}</button>
            <button class="btn btn-sm btn-link kd-remake" *ngIf="t.kitchenStatus === 'READY'" (click)="remake(t)">{{ 'Remake' | translate }}</button>
          </div>
        </div>
        <p class="text-muted" *ngIf="filteredTickets.length === 0">{{ 'No tickets in the kitchen right now.' | translate }}</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .ticket-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
        gap: 12px;
      }
      .ticket {
        border: 1px solid var(--border, #ddd);
        border-radius: 8px;
        padding: 0.75rem;
      }
      .ticket.ready {
        background: rgba(47, 190, 114, 0.12);
        border-color: rgba(47, 190, 114, 0.4);
      }
      .ticket.warn {
        background: rgba(245, 158, 11, 0.14);
        border-color: rgba(245, 158, 11, 0.6);
      }
      .ticket.late {
        background: rgba(220, 38, 38, 0.12);
        border-color: rgba(220, 38, 38, 0.7);
      }
      .kd-stat {
        min-width: 80px;
        text-align: center;
        border: 1px solid var(--border, #ddd);
        border-radius: 8px;
        padding: 0.4rem 0.8rem;
      }
      .kd-stat.kd-bad {
        border-color: #dc2626;
        color: #b91c1c;
      }
      .kd-n {
        font-size: 1.6rem;
        font-weight: 700;
      }
    `,
  ],
})
export class KitchenDisplayComponent implements OnInit, OnDestroy {
  tickets: KitchenTicket[] = [];
  summary: KitchenSummary | null = null;
  error: string | null = null;
  private poll: ReturnType<typeof setInterval> | null = null;
  private tick: ReturnType<typeof setInterval> | null = null;
  now = Date.now();

  /** Fourier: "Kitchen stations" — client-side filter/tab bar over the same feed, no backend query param needed. */
  activeStation: string | null = null;

  showBoard = false;
  menu: MenuItem[] = [];
  boardQuery = '';
  boardReason = '';
  private boardCache: MenuItem[] = [];
  private boardKey = '';

  constructor(private orderApi: RestaurantOrderApiService, private kitchenApi: KitchenApiService, private menuApi: MenuApiService) {}

  ngOnInit(): void {
    this.load();
    this.poll = setInterval(() => this.load(), 8000);
    this.tick = setInterval(() => (this.now = Date.now()), 1000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
    if (this.tick) clearInterval(this.tick);
  }

  load(): void {
    this.orderApi.listKitchenTickets().subscribe({
      next: (t) => {
        this.tickets = t;
        this.error = null;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load kitchen tickets'),
    });
    this.kitchenApi.summary().subscribe({ next: (s) => (this.summary = s), error: () => {} });
    if (this.showBoard) this.loadMenu();
  }

  /** Seconds this ticket has been waiting (frozen once it is ready). */
  seconds(t: KitchenTicket): number {
    if (!t.sentAt) return (t.elapsedMinutes ?? 0) * 60;
    const end = t.kitchenStatus === 'READY' && t.readyAt ? Date.parse(t.readyAt) : this.now;
    return Math.max(0, Math.floor((end - Date.parse(t.sentAt)) / 1000));
  }

  clock(t: KitchenTicket): string {
    const s = this.seconds(t);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  urgency(t: KitchenTicket): 'OK' | 'WARN' | 'LATE' {
    if (t.kitchenStatus === 'READY') return 'OK';
    if (this.seconds(t) >= (t.targetMinutes ?? 15) * 60) return 'LATE';
    return t.urgency ?? 'OK';
  }

  pretty(csv: string | null | undefined): string {
    return (csv ?? '')
      .split(',')
      .filter((x) => x)
      .map((x) => x.charAt(0) + x.slice(1).toLowerCase())
      .join(', ');
  }

  /** Distinct stations across the current ticket feed, "Unassigned" last for tickets with no station set. */
  get stations(): string[] {
    const set = new Set<string>();
    let hasUnassigned = false;
    for (const t of this.tickets) {
      if (t.station) set.add(t.station);
      else hasUnassigned = true;
    }
    const list = Array.from(set).sort();
    if (hasUnassigned) list.push('Unassigned');
    return list;
  }

  get filteredTickets(): KitchenTicket[] {
    if (this.activeStation === null) return this.tickets;
    if (this.activeStation === 'Unassigned') return this.tickets.filter((t) => !t.station);
    return this.tickets.filter((t) => t.station === this.activeStation);
  }

  countForStation(station: string): number {
    return station === 'Unassigned' ? this.tickets.filter((t) => !t.station).length : this.tickets.filter((t) => t.station === station).length;
  }

  advance(t: KitchenTicket, status: string): void {
    this.error = null;
    this.orderApi.setLineKitchenStatus(t.orderId, t.lineId, status).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update ticket'),
    });
  }

  private cookingAt(t: KitchenTicket): number {
    return this.tickets.filter((x) => x.orderId === t.orderId && (x.station ?? '') === (t.station ?? '') && (x.kitchenStatus === 'SENT' || x.kitchenStatus === 'PREPARING')).length;
  }

  canBump(t: KitchenTicket): boolean {
    return (t.kitchenStatus === 'SENT' || t.kitchenStatus === 'PREPARING') && this.cookingAt(t) > 1;
  }

  bump(t: KitchenTicket): void {
    this.error = null;
    this.kitchenApi.bump(t.orderId, t.station || 'Unassigned').subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to bump the order'),
    });
  }

  remake(t: KitchenTicket): void {
    if (!confirm(`Make ${t.description} for ${t.tableName} again?`)) return;
    this.error = null;
    this.kitchenApi.refire(t.orderId, t.lineId).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to send it back'),
    });
  }

  // ---- 86 board ----

  toggleBoard(): void {
    this.showBoard = !this.showBoard;
    if (this.showBoard) this.loadMenu();
  }

  private loadMenu(): void {
    this.menuApi.listItems().subscribe({ next: (m) => (this.menu = m), error: () => {} });
  }

  /** Cached so the buttons are not rebuilt on every change-detection pass. */
  boardItems(): MenuItem[] {
    const key = this.boardQuery.trim().toLowerCase() + '|' + this.menu.map((m) => m.id + ':' + (m.soldOut ? 1 : 0)).join(',');
    if (key !== this.boardKey) {
      this.boardKey = key;
      const q = this.boardQuery.trim().toLowerCase();
      this.boardCache = this.menu.filter((m) => m.active && (!q || (m.product?.name ?? '').toLowerCase().includes(q)));
    }
    return this.boardCache;
  }

  toggleItem(m: MenuItem): void {
    this.error = null;
    this.menuApi.setSoldOut(m.id, !m.soldOut, m.soldOut ? null : this.boardReason.trim() || null).subscribe({
      next: () => {
        this.boardReason = '';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update the item'),
    });
  }
}
