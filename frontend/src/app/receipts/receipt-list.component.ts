import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OrderApiService } from '../core/api/order-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { RegisterApiService } from '../core/api/register-api.service';
import { Order, PaymentMethodOption, RegisterSummary } from '../core/models';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/**
 * Completed sales: lookup + reprint. Open to any logged-in user (a cashier reprinting a receipt for a customer doesn't need admin rights).
 * Search by invoice, customer, cashier, register or product; filter by day, payment method, register and kind (rung offline, sync problem,
 * EFRIS failed, on credit, charged to a room); 25 to a page; Excel, PDF and CSV of the same filtered list for those with Sales view.
 */
@Component({
  selector: 'app-receipt-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <h3 class="mb-3">{{ 'Receipts' | translate }}</h3>
      <app-list-filter exportPath="/orders/completed/export" [exportExtra]="extra" [extraActive]="method !== '' || registerId !== ''" [placeholder]="'Search invoice, customer, cashier, product' | translate" [statuses]="kinds" statusAll="All sales" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select rl-method" style="width:auto;" [(ngModel)]="method" (ngModelChange)="extraChanged()" name="method" [title]="'Payment method' | translate">
          <option value="">{{ 'Any payment method' | translate }}</option>
          <option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option>
        </select>
        <select class="form-select rl-register" style="width:auto;" [(ngModel)]="registerId" (ngModelChange)="extraChanged()" name="register" [title]="'Register' | translate">
          <option value="">{{ 'Any register' | translate }}</option>
          <option *ngFor="let r of registers" [value]="r.id">{{ r.name }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;">{{ 'Dates filter by the day the sale was completed. With no filter the latest 200 sales show.' | translate }}</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Invoice' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th>{{ 'Cashier' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th>{{ 'Payment' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let o of list.rows">
          <tr class="rl-row">
            <td>
              {{ o.completedAt | date: 'yyyy-MM-dd HH:mm' }}
              <!-- Task: "local queuing of sales that sync once connectivity returns" — this sale was rung offline and replayed once back online. -->
              <span *ngIf="o.offlineQueuedAt" class="text-muted" style="font-size:0.8rem;" [title]="'Rung offline at ' + (o.offlineQueuedAt | date: 'short')">📴</span>
              <span *ngIf="o.syncNote" class="text-danger" style="font-size:0.8rem;" [title]="o.syncNote">⚠</span>
            </td>
            <td>{{ o.invoiceNumber }}</td>
            <td>{{ o.customerName }}</td>
            <td>{{ o.cashierName }}</td>
            <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
            <td>
              <span *ngFor="let p of o.payments; let last = last">{{ p.method }}{{ !last ? ' + ' : '' }}</span>
            </td>
            <td class="flex gap-2">
              <span *ngIf="(o.printCount || 0) > 1" class="text-muted rl-printed" style="font-size:0.8rem;" [title]="'Times the receipt was printed' | translate">{{ 'printed ×{p0}' | translate: { p0: (o.printCount) } }}</span>
              <span *ngIf="o.voidedAt" class="badge badge-secondary rl-voided" [title]="'Voided by ' + (o.voidedBy || '') + ': ' + (o.voidReason || '')">{{ 'VOIDED' | translate }}</span>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/receipt', o.id]">{{ 'View / Reprint' | translate }}</a>
              <button *ngIf="!o.voidedAt" class="btn btn-sm btn-outline-danger rl-void" (click)="startVoid(o)">{{ 'Void' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="voiding && voiding.id === o.id" class="rl-void-row">
            <td colspan="7" style="background:rgba(255,255,255,0.03);">
              <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
                <strong>{{ 'Void {p0} ({p1})' | translate: { p0: (o.invoiceNumber), p1: (o.total | number: '1.2-2') } }}</strong>
                <span class="text-muted">{{ 'The whole sale is refunded and stays on record as voided.' | translate }}</span>
              </div>
              <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
                <input class="form-control rl-void-reason" style="width:280px;" [placeholder]="'Why is it being voided?' | translate" [(ngModel)]="voidReason" name="voidReason" maxlength="200" />
                <input *ngIf="needPin" class="form-control rl-void-pin" style="width:160px;" type="password" [placeholder]="'Manager PIN' | translate" [(ngModel)]="voidPin" name="voidPin" autocomplete="off" />
                <button class="btn btn-danger btn-sm rl-void-confirm" (click)="confirmVoid()" [disabled]="busy || !voidReason.trim()">{{ 'Void this sale' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" (click)="voiding = null">{{ 'Cancel' | translate }}</button>
              </div>
              <div class="text-danger mt-2 rl-void-error" *ngIf="voidError">{{ voidError }}</div>
            </td>
          </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || method || registerId ? ('No sales match these filters.' | translate) : ('No completed sales yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ReceiptListComponent implements OnInit {
  kinds = [
    { value: 'OFFLINE', label: T('Rung offline') },
    { value: 'SYNC_ISSUE', label: T('Sync problem') },
    { value: 'EFRIS_FAILED', label: T('EFRIS failed') },
    { value: 'CREDIT', label: T('Part on credit') },
    { value: 'ROOM_CHARGE', label: T('Charged to a room') },
    { value: 'VOIDED', label: T('Voided sales') },
  ];
  voiding: Order | null = null;
  voidReason = '';
  voidPin = '';
  needPin = false;
  busy = false;
  voidError: string | null = null;
  methods: PaymentMethodOption[] = [];
  registers: RegisterSummary[] = [];
  method = '';
  registerId = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<Order>((f, p, sz) => this.orderApi.completedPage(f, p, sz, { method: this.method, registerId: this.registerId }), 'Failed to load receipts');

  constructor(private orderApi: OrderApiService, private methodApi: PaymentMethodApiService, private registerApi: RegisterApiService, private returnsApi: ReturnsApiService) {}

  ngOnInit(): void {
    this.list.load();
    this.methodApi.listAll().subscribe({ next: (m) => (this.methods = m), error: () => {} });
    this.registerApi.listRegisters().subscribe({ next: (r) => (this.registers = r), error: () => {} });
  }

  startVoid(o: Order): void {
    this.voiding = o;
    this.voidReason = '';
    this.voidPin = '';
    this.needPin = false;
    this.voidError = null;
  }

  confirmVoid(): void {
    if (!this.voiding) return;
    this.busy = true;
    this.voidError = null;
    this.returnsApi.voidSale({ orderId: this.voiding.id, reason: this.voidReason.trim(), approverPin: this.voidPin || undefined }).subscribe({
      next: () => {
        this.busy = false;
        this.voiding = null;
        this.list.load();
      },
      error: (err) => {
        this.busy = false;
        const msg: string = err?.error?.message ?? 'Could not void the sale';
        if (msg.includes('Manager approval needed')) this.needPin = true;
        this.voidError = msg;
      },
    });
  }

  extraChanged(): void {
    this.extra = { method: this.method, registerId: this.registerId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.method = '';
    this.registerId = '';
    this.extra = {};
  }
}
