import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { KitchenTicket } from '../core/models';

/**
 * Admin/staff > Restaurant > Kitchen display (Fourier: "Kitchen Order Tickets / Kitchen
 * Display"). Every line currently SENT/PREPARING/READY across every open table, oldest ticket
 * first — polls every few seconds so a kitchen screen left open stays current without a manual
 * refresh.
 */
@Component({
  selector: 'app-kitchen-display',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Kitchen display</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
      </div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="stations.length > 1">
        <button
          class="btn btn-sm"
          [class.btn-primary]="activeStation === null"
          [class.btn-outline-secondary]="activeStation !== null"
          (click)="activeStation = null"
        >
          All ({{ tickets.length }})
        </button>
        <button
          class="btn btn-sm"
          *ngFor="let s of stations"
          [class.btn-primary]="activeStation === s"
          [class.btn-outline-secondary]="activeStation !== s"
          (click)="activeStation = s"
        >
          {{ s }} ({{ countForStation(s) }})
        </button>
      </div>
      <div class="ticket-grid">
        <div class="ticket" *ngFor="let t of filteredTickets" [class.ready]="t.kitchenStatus === 'READY'">
          <div class="flex justify-between">
            <a [routerLink]="['/admin/restaurant/orders', t.orderId]"><strong>{{ t.tableName }}</strong></a>
            <span class="text-muted">{{ t.kitchenStatus }}</span>
          </div>
          <div>{{ t.quantity }}× {{ t.description }}</div>
          <div class="text-muted" *ngIf="t.station">{{ t.station }}</div>
          <div class="text-muted" *ngIf="t.notes">{{ t.notes }}</div>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-sm btn-outline-secondary" *ngIf="t.kitchenStatus === 'SENT'" (click)="advance(t, 'PREPARING')">Start preparing</button>
            <button class="btn btn-sm btn-outline-secondary" *ngIf="t.kitchenStatus === 'PREPARING'" (click)="advance(t, 'READY')">Mark ready</button>
            <button class="btn btn-sm btn-primary" *ngIf="t.kitchenStatus === 'READY'" (click)="advance(t, 'SERVED')">Mark served</button>
          </div>
        </div>
        <p class="text-muted" *ngIf="filteredTickets.length === 0">No tickets in the kitchen right now.</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .ticket-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
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
    `,
  ],
})
export class KitchenDisplayComponent implements OnInit, OnDestroy {
  tickets: KitchenTicket[] = [];
  error: string | null = null;
  private poll: ReturnType<typeof setInterval> | null = null;

  /** Fourier: "Kitchen stations" — client-side filter/tab bar over the same feed, no backend query param needed. */
  activeStation: string | null = null;

  constructor(private orderApi: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.load();
    this.poll = setInterval(() => this.load(), 8000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  load(): void {
    this.orderApi.listKitchenTickets().subscribe({
      next: (t) => (this.tickets = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load kitchen tickets'),
    });
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
}
