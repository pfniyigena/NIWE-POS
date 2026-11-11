import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { KitchenApiService } from '../core/api/kitchen-api.service';
import { ExpoOrder } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Restaurant > Expo: the pass. One card per order showing every dish the kitchen is making for it,
 * which stations are still cooking, and which dishes are ready to go out. A complete order is
 * highlighted so the expediter can take it out together; "Serve ready" marks what is ready as served.
 */
@Component({
  selector: 'app-kitchen-expo',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Expo' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/kitchen">{{ 'Kitchen display' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">{{ '← Floor plan' | translate }}</a>
        </div>
      </div>
      <div class="ex-grid">
        <div class="ex-card" *ngFor="let o of orders" [class.complete]="o.allReady" [class.warn]="!o.allReady && o.urgency === 'WARN'" [class.late]="!o.allReady && o.urgency === 'LATE'">
          <div class="flex justify-between align-center">
            <a [routerLink]="['/admin/restaurant/orders', o.orderId]"><strong style="font-size:1.1rem;">{{ o.label }}</strong></a>
            <span class="ex-age" style="font-weight:700;">{{ '{p0} min' | translate: { p0: (o.oldestMinutes) } }}</span>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ '{p0} guests' | translate: { p0: (o.guestCount) } }}<span *ngIf="o.serverName"> · {{ o.serverName }}</span><span *ngIf="o.heldCount > 0"> {{ '· {p0} held for a later course' | translate: { p0: (o.heldCount) } }}</span></div>
          <div class="ex-allergy" *ngIf="o.guestAllergens" style="font-size:0.8rem; color:#b45309;">{{ 'Guest allergies: {p0}' | translate: { p0: (pretty(o.guestAllergens)) } }}</div>
          <div class="ex-status mt-1" style="font-weight:600;">
            <span *ngIf="o.allReady" style="color:#047857;">{{ '✔ Complete — ready to go out' | translate }}</span>
            <span *ngIf="!o.allReady">{{ 'Waiting on {p0}' | translate: { p0: (o.waitingOn.join(', ')) } }}</span>
          </div>
          <table class="mt-2" style="width:100%;">
            <tr class="ex-line" *ngFor="let l of o.lines" [style.opacity]="l.status === 'READY' ? 1 : 0.9">
              <td style="vertical-align:top;">
                <span [style.color]="l.status === 'READY' ? '#047857' : l.urgency === 'LATE' ? '#b91c1c' : l.urgency === 'WARN' ? '#b45309' : ''">{{ l.status === 'READY' ? '✔' : l.status === 'PREPARING' ? '🔥' : '·' }}</span>
                {{ l.quantity }}× {{ l.description }}
                <span class="text-muted" style="font-size:0.75rem;" *ngIf="l.options.length > 0"> ({{ l.options.join(', ') }})</span>
                <span class="text-muted" style="font-size:0.75rem;" *ngIf="l.seat"> {{ 'seat {p0}' | translate: { p0: (l.seat) } }}</span>
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="l.notes">“{{ l.notes }}”</div>
                <div *ngIf="l.allergyConflict" style="font-size:0.75rem; color:#991b1b; font-weight:700;">{{ '⚠ allergy: {p0}' | translate: { p0: (pretty(l.allergyConflict)) } }}</div>
              </td>
              <td class="text-muted" style="font-size:0.75rem; white-space:nowrap; vertical-align:top;">{{ l.station }}</td>
              <td style="white-space:nowrap; text-align:right; vertical-align:top;">
                <span style="font-size:0.8rem;">{{ '{p0}/{p1}m' | translate: { p0: (l.elapsedMinutes), p1: (l.targetMinutes) } }}</span>
                <button class="btn btn-sm btn-link ex-remake" *ngIf="l.status === 'READY'" (click)="remake(o, l.lineId, l.description)">{{ 'Remake' | translate }}</button>
              </td>
            </tr>
          </table>
          <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-primary ex-serve" *ngIf="o.readyCount > 0" (click)="serve(o)">{{ 'Serve {p0}' | translate: { p0: (o.allReady ? 'all' : 'the ' + o.readyCount + ' ready') } }}</button>
            <button class="btn btn-sm btn-outline-secondary ex-bump" *ngIf="o.cookingCount > 0" (click)="bump(o)">{{ 'Mark everything ready' | translate }}</button>
          </div>
        </div>
        <p class="text-muted" *ngIf="loaded && orders.length === 0">{{ 'Nothing is in the kitchen right now.' | translate }}</p>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .ex-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 12px;
      }
      .ex-card {
        border: 1px solid var(--border, #ddd);
        border-radius: 8px;
        padding: 0.75rem;
      }
      .ex-card.complete {
        background: rgba(47, 190, 114, 0.14);
        border-color: rgba(47, 190, 114, 0.7);
      }
      .ex-card.warn {
        border-color: rgba(245, 158, 11, 0.8);
      }
      .ex-card.late {
        border-color: rgba(220, 38, 38, 0.8);
        background: rgba(220, 38, 38, 0.08);
      }
    `,
  ],
})
export class KitchenExpoComponent implements OnInit, OnDestroy {
  orders: ExpoOrder[] = [];
  error: string | null = null;
  loaded = false;
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: KitchenApiService) {}

  ngOnInit(): void {
    this.load();
    this.poll = setInterval(() => this.load(), 6000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  pretty(csv: string | null | undefined): string {
    return (csv ?? '')
      .split(',')
      .filter((x) => x)
      .map((x) => x.charAt(0) + x.slice(1).toLowerCase())
      .join(', ');
  }

  load(): void {
    this.api.expo().subscribe({
      next: (o) => {
        this.orders = o;
        this.loaded = true;
        this.error = null;
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load the expo screen')),
    });
  }

  serve(o: ExpoOrder): void {
    this.error = null;
    this.api.serveReady(o.orderId).subscribe({ next: () => this.load(), error: (err) => (this.error = err?.error?.message ?? T('Failed to serve')) });
  }

  bump(o: ExpoOrder): void {
    this.error = null;
    this.api.bump(o.orderId).subscribe({ next: () => this.load(), error: (err) => (this.error = err?.error?.message ?? T('Failed to update the order')) });
  }

  remake(o: ExpoOrder, lineId: number, name: string): void {
    if (!confirm(T('Make {p0} for {p1} again?', { p0: name, p1: o.label }))) return;
    this.error = null;
    this.api.refire(o.orderId, lineId).subscribe({ next: () => this.load(), error: (err) => (this.error = err?.error?.message ?? T('Failed to send it back')) });
  }
}
