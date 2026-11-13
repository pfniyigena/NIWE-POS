import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, StoreCart, StoreCartList } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Abandoned carts: the carts of visitors who typed an e-mail address at checkout, what state each is in (waiting for a reminder, reminded, ordered,
 * stopped) and when the reminders went out. The reminders themselves are switched on in Shop settings.
 */
@Component({
  selector: 'app-store-carts',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Abandoned carts' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
      </div>
      <p class="text-muted">{{ 'Carts of visitors who typed an e-mail address at checkout and did not order. Each gets a reminder after 1 hour and another after 3 days, with no discount. Switch the reminders on in Shop settings.' | translate }}</p>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="data">
        <button class="btn btn-sm cart-chip" [class.btn-primary]="filter === ''" [class.btn-outline-secondary]="filter !== ''" (click)="show('')">{{ 'All' | translate }}</button>
        <button *ngFor="let s of states" class="btn btn-sm cart-chip" [class.btn-primary]="filter === s.code" [class.btn-outline-secondary]="filter !== s.code" (click)="show(s.code)">
          {{ s.label | translate }} ({{ count(s.code) }})
        </button>
      </div>

      <table class="table-sm" *ngIf="data && data.items.length > 0">
        <thead><tr>
          <th>{{ 'E-mail' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Products' | translate }}</th><th>{{ 'Pieces' | translate }}</th>
          <th>{{ 'Last change' | translate }}</th><th>{{ 'First reminder' | translate }}</th><th>{{ 'Second reminder' | translate }}</th><th></th>
        </tr></thead>
        <tbody>
          <tr *ngFor="let c of data.items">
            <td class="cart-email">{{ c.email }}</td>
            <td [style.color]="color(c)">{{ label(c.status) | translate }}<span *ngIf="c.lastError" class="text-muted" [title]="c.lastError"> ⚠</span></td>
            <td>{{ c.items }}</td>
            <td>{{ c.quantity }}</td>
            <td>{{ c.savedAt | date: 'short' }}</td>
            <td>{{ c.firstReminderAt ? (c.firstReminderAt | date: 'short') : '' }}</td>
            <td>{{ c.secondReminderAt ? (c.secondReminderAt | date: 'short') : '' }}</td>
            <td><button class="btn btn-sm btn-outline-danger cart-remove" (click)="remove(c)" [disabled]="busy">{{ 'Remove' | translate }}</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted cart-empty" *ngIf="data && data.items.length === 0">{{ 'No carts here yet.' | translate }}</p>
    </app-shell>
  `,
})
export class StoreCartsComponent implements OnInit {
  data: StoreCartList | null = null;
  filter = '';
  error: string | null = null;
  busy = false;

  readonly states = [
    { code: 'ACTIVE', label: 'Waiting' },
    { code: 'REMINDED', label: 'Reminded once' },
    { code: 'DONE', label: 'Reminded twice' },
    { code: 'RECOVERED', label: 'Ordered' },
    { code: 'STOPPED', label: 'Stopped' },
    { code: 'EXPIRED', label: 'Nothing to remind' },
  ];

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  show(code: string): void {
    this.filter = code;
    this.reload();
  }

  reload(): void {
    this.api.carts(this.filter).subscribe({
      next: (d) => {
        this.data = d;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }

  count(code: string): number {
    const d = this.data;
    if (!d) return 0;
    switch (code) {
      case 'ACTIVE': return d.waiting;
      case 'REMINDED': return d.reminded;
      case 'DONE': return d.done;
      case 'RECOVERED': return d.recovered;
      case 'STOPPED': return d.stopped;
      default: return d.expired;
    }
  }

  label(status: string): string {
    return this.states.find((s) => s.code === status)?.label ?? status;
  }

  color(c: StoreCart): string {
    return c.status === 'RECOVERED' ? 'var(--success, #1a7f37)' : c.status === 'STOPPED' ? 'var(--danger, #b42318)' : '';
  }

  remove(c: StoreCart): void {
    if (!confirm(T('Remove this cart and its e-mail address?'))) return;
    this.busy = true;
    this.api.deleteCart(c.id).subscribe({
      next: () => {
        this.busy = false;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
