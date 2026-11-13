import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { LoyaltyCustomer, LoyaltyList, LoyaltyView, OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Loyalty points: the shop customers (accounts) and their points, with the history of one customer and a form to add or remove points by hand
 * (a gift, a correction). The rules (points per 100, the share of an order points may pay) are set in Shop settings. These points belong to shop accounts and
 * are separate from the till's loyalty programme in Sales.
 */
@Component({
  selector: 'app-store-loyalty',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Loyalty points' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
      </div>
      <p class="text-muted">{{ 'Customers who are signed in earn points when an order is completed and spend them at checkout. These points belong to shop accounts and are separate from the loyalty programme at the till. Switch them on in Shop settings.' | translate }}</p>

      <ng-container *ngIf="!selected">
        <p *ngIf="list" class="lo-summary">{{ 'Customers with points: {p0}. Points outstanding: {p1}.' | translate: { p0: list.customersWithPoints, p1: list.pointsOutstanding } }}</p>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <input class="form-control lo-search" style="max-width:300px;" [placeholder]="'Search by name, e-mail or phone' | translate" [(ngModel)]="query" name="q" (keyup.enter)="load()" />
          <button class="btn btn-outline-primary lo-search-btn" (click)="load()">{{ 'Search' | translate }}</button>
        </div>
        <table class="table-sm" *ngIf="list && list.items.length > 0">
          <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th>{{ 'Phone' | translate }}</th><th>{{ 'Points' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of list.items">
              <td>{{ c.name }}</td><td>{{ c.email }}</td><td>{{ c.phone }}</td><td class="lo-points">{{ c.points }}</td>
              <td><button class="btn btn-sm btn-outline-primary lo-open" (click)="open(c)">{{ 'Open' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted lo-empty" *ngIf="list && list.items.length === 0">{{ 'No customer accounts found.' | translate }}</p>
      </ng-container>

      <ng-container *ngIf="selected && view">
        <button class="btn btn-outline-secondary mb-2 lo-back" (click)="back()">{{ 'Back to the list' | translate }}</button>
        <h4>{{ selected.name || selected.email || selected.phone }}</h4>
        <p><strong class="lo-balance">{{ view.points }}</strong> {{ 'points' | translate }}</p>

        <div class="card" style="padding:0.75rem; max-width:520px;" *ngIf="canAdjust">
          <h5>{{ 'Add or remove points' | translate }}</h5>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <input class="form-control lo-adj-points" type="number" style="max-width:140px;" [placeholder]="'Points (minus to remove)' | translate" [(ngModel)]="adjPoints" name="adjpoints" />
            <input class="form-control lo-adj-reason" style="max-width:280px;" maxlength="200" [placeholder]="'Why' | translate" [(ngModel)]="adjReason" name="adjreason" />
            <button class="btn btn-primary lo-adj-save" [disabled]="busy || !adjPoints || !adjReason.trim()" (click)="adjust()">{{ 'Save' | translate }}</button>
          </div>
        </div>

        <h5 class="mt-3">{{ 'History' | translate }}</h5>
        <table class="table-sm" *ngIf="view.entries.length > 0">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What happened' | translate }}</th><th>{{ 'Order' | translate }}</th><th>{{ 'Points' | translate }}</th><th>{{ 'Balance' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let e of view.entries">
              <td>{{ e.at | date: 'short' }}</td>
              <td>{{ label(e.kind) | translate }}<span *ngIf="e.note" class="text-muted"> · {{ e.note }}</span></td>
              <td>{{ e.orderCode }}</td>
              <td [style.color]="e.points < 0 ? '#b42318' : '#1a7f37'">{{ e.points > 0 ? '+' : '' }}{{ e.points }}</td>
              <td>{{ e.balanceAfter }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="view.entries.length === 0">{{ 'No points yet.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class StoreLoyaltyComponent implements OnInit {
  list: LoyaltyList | null = null;
  query = '';
  selected: LoyaltyCustomer | null = null;
  view: LoyaltyView | null = null;
  adjPoints: number | null = null;
  adjReason = '';
  error: string | null = null;
  message: string | null = null;
  busy = false;
  /** The server refuses an adjustment without the Operations manage right; a viewer simply gets the error. */
  canAdjust = true;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.loyaltyList(this.query.trim()).subscribe({
      next: (l) => {
        this.list = l;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }

  open(c: LoyaltyCustomer): void {
    this.api.loyaltyLedger(c.id).subscribe({
      next: (v) => {
        this.selected = c;
        this.view = v;
        this.error = null;
        this.message = null;
      },
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }

  back(): void {
    this.selected = null;
    this.view = null;
    this.message = null;
    this.load();
  }

  label(kind: string): string {
    switch (kind) {
      case 'EARN': return 'Order completed';
      case 'SPEND': return 'Used on an order';
      case 'SPEND_BACK': return 'Order cancelled: points returned';
      case 'RET_BACK': return 'Return: points returned';
      case 'RET_TAKE': return 'Return: points taken back';
      default: return 'Changed by the shop';
    }
  }

  adjust(): void {
    if (!this.selected || !this.adjPoints) return;
    this.busy = true;
    this.api.loyaltyAdjust(this.selected.id, this.adjPoints, this.adjReason.trim()).subscribe({
      next: (v) => {
        this.busy = false;
        this.view = v;
        this.adjPoints = null;
        this.adjReason = '';
        this.error = null;
        this.message = T('Saved.');
      },
      error: (e) => {
        this.busy = false;
        this.message = null;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
