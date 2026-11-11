import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantWastageApiService } from '../core/api/restaurant-wastage-api.service';
import { RestaurantWastage } from '../core/models';
import { AuthService } from '../core/auth.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/**
 * Admin > Restaurant > Wastage — Fourier: "Restaurants need a dedicated wastage module." Mirrors
 * StockAdjustmentListComponent's own layout, but open to any logged-in staff (see
 * RestaurantWastageController) and with the extra Status/Cost impact columns
 * ReportService.restaurantWastage also reports. Fourier's later "should be confirmed by admin
 * once confirmed inventory and account will be impacted" is why a PENDING row shows
 * Confirm/Reject buttons (ADMIN-only — a cashier who logs a loss can't also be the one who
 * approves its own accounting impact) and why Cost impact reads "—" until confirmed.
 */
@Component({
  selector: 'app-restaurant-wastage-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Restaurant wastage' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/restaurant/wastage/new">{{ '+ Record wastage' | translate }}</a>
      </div>
      <app-list-filter exportPath="/restaurant/wastage/export" [exportExtra]="extra" [extraActive]="reason !== ''" [placeholder]="'Search item or note' | translate" [statuses]="statuses" statusAll="All statuses" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select reason-filter" style="width:auto;" [(ngModel)]="reason" (ngModelChange)="extraChanged()" name="reason" [title]="'Reason' | translate">
          <option value="">{{ 'All reasons' | translate }}</option>
          <option *ngFor="let r of reasons" [value]="r">{{ r.replace('_', ' ') | titlecase }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Product' | translate }}</th>
            <th class="text-end">{{ 'Qty wasted' | translate }}</th>
            <th>{{ 'Reason' | translate }}</th>
            <th>{{ 'Note' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th class="text-end">{{ 'Cost impact' | translate }}</th>
            <th *ngIf="isAdmin"></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let w of list.rows">
            <td>{{ w.createdAt | date: 'medium' }}</td>
            <td>{{ w.productName }}</td>
            <td class="text-end" style="color: var(--danger-text)">-{{ w.quantity }}</td>
            <td>{{ w.reason }}</td>
            <td>{{ w.note }}</td>
            <td>
              <span
                [style.color]="
                  w.status === 'CONFIRMED' ? 'var(--success-text)' : w.status === 'REJECTED' ? 'var(--danger-text)' : 'inherit'
                "
                >{{ w.status }}</span
              >
            </td>
            <td class="text-end">{{ w.costImpact != null ? (w.costImpact | currency) : '—' }}</td>
            <td *ngIf="isAdmin" class="flex gap-2">
              <ng-container *ngIf="w.status === 'PENDING'">
                <button class="btn btn-sm btn-primary" [disabled]="busyId === w.id" (click)="confirm(w)">{{ 'Confirm' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="busyId === w.id" (click)="reject(w)">{{ 'Reject' | translate }}</button>
              </ng-container>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || reason ? ('No wastage matches these filters.' | translate) : ('No wastage recorded yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class RestaurantWastageListComponent implements OnInit {
  statuses = [{ value: 'PENDING', label: T('Pending') }, { value: 'CONFIRMED', label: T('Confirmed') }, { value: 'REJECTED', label: T('Rejected') }];
  reasons = ['SPOILAGE', 'BURNT_OR_OVERCOOKED', 'DROPPED_OR_SPILLED', 'EXPIRED', 'PREP_TRIM', 'CUSTOMER_COMPLAINT', 'OTHER'];
  reason = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<RestaurantWastage>((f, p, sz) => this.wastageApi.page(f, p, sz, { reason: this.reason }), 'Failed to load wastage records');
  error: string | null = null;
  busyId: number | null = null;

  constructor(private wastageApi: RestaurantWastageApiService, private auth: AuthService) {}

  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  extraChanged(): void {
    this.extra = { reason: this.reason };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.reason = '';
    this.extra = {};
  }

  confirm(w: RestaurantWastage): void {
    if (!confirm(T('Confirm {p0} {p1} wasted? This will decrement stock and post to Accounting.', { p0: w.quantity, p1: w.productName }))) return;
    this.error = null;
    this.busyId = w.id;
    this.wastageApi.confirm(w.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? T('Failed to confirm wastage entry');
      },
    });
  }

  reject(w: RestaurantWastage): void {
    if (!confirm(T('Reject the logged wastage of {p0} {p1}? No stock/accounting impact will be made.', { p0: w.quantity, p1: w.productName }))) return;
    this.error = null;
    this.busyId = w.id;
    this.wastageApi.reject(w.id).subscribe({
      next: () => {
        this.busyId = null;
        this.load();
      },
      error: (err) => {
        this.busyId = null;
        this.error = err?.error?.message ?? T('Failed to reject wastage entry');
      },
    });
  }
}
