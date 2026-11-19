import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { SignaturePadComponent } from './signature-pad.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { Driver, KeptLine, OnlineStoreApiService, StoreOrder } from '../core/api/online-store-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption, Warehouse, WarehouseLocation } from '../core/models';
import { T } from '../core/i18n/t';

interface LocationOption { id: number; label: string; type: string | null; primaryWarehouse: boolean }

/**
 * Online Store > Orders: what customers ordered on the public shop. Accepting makes a confirmed sale order (it holds the stock); the order then
 * moves to packed, out for delivery or ready for pick-up, and completing it takes the stock out, issues the invoice and records the cash. The list
 * refreshes by itself so it can stay open on a counter screen.
 */
@Component({
  selector: 'app-store-orders',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, SignaturePadComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Online orders' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/returns">{{ 'Returns' | translate }}<span *ngIf="returnsWaiting > 0"> ({{ returnsWaiting }})</span></a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/payments">{{ 'Online payments' | translate }}<span *ngIf="attention > 0"> ({{ attention }})</span></a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
          <a class="btn btn-outline-secondary" href="/shop" target="_blank" rel="noopener">{{ 'Open the shop' | translate }}</a>
        </div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm so-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span class="so-tab-count" *ngIf="badge(t.key) > 0"> ({{ badge(t.key) }}<ng-container *ngIf="t.key === 'PLACED' && overdue > 0">, {{ overdue }} {{ 'late' | translate }}</ng-container>)</span>
        </button>
        <input class="form-control so-search" style="width:220px;" [placeholder]="'Search code, name, phone' | translate" [(ngModel)]="q" name="soq" (keyup.enter)="reload()" (blur)="reload()" />
        <select *ngIf="drivers.length > 0" class="form-select so-driver-filter" style="width:200px;" [(ngModel)]="driverFilter" name="sodf" (ngModelChange)="reload()" [attr.aria-label]="'Driver' | translate">
          <option [ngValue]="null">{{ 'All drivers' | translate }}</option>
          <option [ngValue]="0">{{ 'Not assigned' | translate }}</option>
          <option *ngFor="let d of drivers" [ngValue]="d.id">{{ d.name }}<ng-container *ngIf="d.openOrders > 0"> ({{ d.openOrders }})</ng-container></option>
        </select>
        <button *ngIf="driverFilter !== null" class="btn btn-sm btn-outline-secondary so-run" [disabled]="busy" (click)="runSheet()">🖨 {{ 'Run sheet' | translate }}</button>
      </div>

      <div class="card so-card mb-2" *ngFor="let o of list.rows" style="padding:0.75rem;" [style.border-left]="o.status === 'PLACED' && late(o) ? '4px solid #b91c1c' : ''">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="so-code">{{ o.code }}</strong>
            <span class="badge" style="margin-left:0.4rem;">{{ o.deliveryMethod === 'DELIVERY' ? ('Delivery' | translate) : ('Pick-up' | translate) }}</span>
            <span class="so-status text-muted" style="margin-left:0.4rem;">{{ statusLabel(o.status) | translate }}<ng-container *ngIf="o.decidedBy"> · {{ o.decidedBy }}</ng-container></span>
            <div>{{ o.customerName }} · {{ o.customerPhone }}<a class="so-wa" *ngIf="waLink(o)" [href]="waLink(o)" target="_blank" rel="noopener" style="margin-left:0.5rem; font-size:0.85rem; white-space:nowrap;" [title]="'Message this customer on WhatsApp' | translate"><svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" style="fill:#25D366; vertical-align:-3px; margin-right:0.25rem;"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/></svg>WhatsApp</a><span class="text-muted" *ngIf="o.customerEmail"> · {{ o.customerEmail }}</span></div>
            <div class="text-muted" *ngIf="o.customerAddress">📍 {{ o.zoneName ? o.zoneName + ' — ' : '' }}{{ o.customerAddress }}</div>
            <div class="text-muted" *ngIf="o.notes">“{{ o.notes }}”</div>
          </div>
          <div style="text-align:right;">
            <div><strong>{{ o.total | number: '1.0-2' }}</strong></div>
            <div class="so-pay" *ngIf="o.paymentMethod === 'ONLINE'" style="font-size:0.8rem; font-weight:600;" [style.color]="payColor(o)">{{ payLabel(o) | translate }}<span *ngIf="o.paymentChannel"> · {{ channelLabel(o.paymentChannel) | translate }}</span></div>
            <div class="text-muted" *ngIf="o.paymentReference" style="font-size:0.75rem;">{{ o.paymentReference }}</div>
            <div class="text-muted" style="font-size:0.8rem;">{{ o.createdAt | date: 'short' }}</div>
            <div *ngIf="o.status === 'PLACED'" class="so-waiting" style="font-size:0.8rem;" [style.color]="late(o) ? '#b91c1c' : ''" [style.font-weight]="late(o) ? '600' : '400'">{{ 'Waiting {p0} min' | translate: { p0: waiting(o) } }}</div>
            <a *ngIf="o.salesOrderId" [routerLink]="['/admin/sales-orders', o.salesOrderId]">{{ o.salesOrderNumber }}</a>
            <a *ngIf="o.invoiceId" style="margin-left:0.5rem;" [routerLink]="['/admin/invoices', o.invoiceId]">{{ o.invoiceNumber }}</a>
          </div>
        </div>
        <ul style="margin:0.4rem 0; padding-left:1.1rem;">
          <li *ngFor="let l of o.lines">{{ l.quantity }} × {{ l.name }} <span class="text-muted">({{ l.lineTotal | number: '1.0-2' }})</span><span class="so-part-line" *ngIf="l.deliveredQuantity !== null && l.deliveredQuantity < l.quantity" style="color:#b45309; font-weight:600;"> · {{ 'delivered {p0}' | translate: { p0: l.deliveredQuantity } }}</span></li>
          <li class="so-delivered-total" *ngIf="o.deliveredTotal !== null">{{ 'Invoiced for the goods delivered: {p0}' | translate: { p0: (o.deliveredTotal | number: '1.0-2') } }}</li>
          <li *ngIf="o.deliveryFee > 0">{{ 'Delivery fee {p0}' | translate: { p0: (o.deliveryFee | number: '1.0-2') } }}</li>
          <li *ngIf="o.couponCode && o.discountAmount - o.loyaltyDiscount > 0">{{ 'Coupon {p0}: {p1} off the items' | translate: { p0: o.couponCode, p1: (o.discountAmount - o.loyaltyDiscount | number: '1.0-2') } }}</li>
          <li *ngIf="o.loyaltyPointsUsed > 0">{{ 'Loyalty points used: {p0} ({p1} off the items)' | translate: { p0: o.loyaltyPointsUsed, p1: (o.loyaltyDiscount | number: '1.0-2') } }}</li>
          <li *ngIf="o.loyaltyPointsEarned > 0">{{ 'Loyalty points earned on this order: {p0}' | translate: { p0: o.loyaltyPointsEarned } }}</li>
          <li *ngIf="o.couponCode && o.feeWaived > 0">{{ 'Coupon {p0}: delivery fee waived ({p1})' | translate: { p0: o.couponCode, p1: (o.feeWaived | number: '1.0-2') } }}</li>
        </ul>
        <div class="so-driver flex gap-2 mt-1" *ngIf="o.deliveryMethod === 'DELIVERY' && canAssign(o)" style="flex-wrap:wrap; align-items:center;">
          <label class="text-muted" [attr.for]="'drv' + o.id">{{ 'Driver' | translate }}</label>
          <select class="form-select so-driver-pick" style="width:200px;" [id]="'drv' + o.id" [ngModel]="o.driverId" (ngModelChange)="assign(o, $event)" [name]="'drv' + o.id" [disabled]="busy">
            <option [ngValue]="null">{{ 'No driver yet' | translate }}</option>
            <option *ngFor="let d of driversFor(o)" [ngValue]="d.id">{{ d.name }}<ng-container *ngIf="!d.active"> ({{ 'switched off' | translate }})</ng-container></option>
          </select>
          <button class="btn btn-sm btn-outline-secondary so-slip" [disabled]="busy" (click)="slip(o)">🖨 {{ 'Delivery slip' | translate }}</button>
          <a class="so-wa-driver" *ngIf="driverLink(o)" [href]="driverLink(o)" target="_blank" rel="noopener" style="font-size:0.85rem; white-space:nowrap;">{{ 'Send to the driver on WhatsApp' | translate }}</a>
        </div>
        <div class="text-muted" *ngIf="o.deliveryMethod === 'DELIVERY' && !canAssign(o) && o.driverName" style="font-size:0.85rem;">{{ 'Driver: {p0}' | translate: { p0: o.driverName } }}<span *ngIf="o.driverPhone"> · {{ o.driverPhone }}</span></div>
        <div class="so-attempts" *ngIf="o.deliveryAttempts > 0 && (o.status === 'PACKED' || o.status === 'OUT_FOR_DELIVERY')" style="color:#b45309; font-size:0.9rem;">
          ⚠ {{ 'Delivery failed {p0} time(s). Last reason: {p1}' | translate: { p0: o.deliveryAttempts, p1: o.lastFailureReason } }}<span *ngIf="o.deliveryAttempts >= 3"> — {{ 'Consider cancelling the order.' | translate }}</span>
        </div>
        <div class="text-muted" *ngIf="o.rejectReason">{{ 'Reason: {p0}' | translate: { p0: (o.rejectReason) } }}</div>

        <div *ngIf="o.status === 'AWAITING_PAYMENT'" class="mt-2">
          <div class="text-muted">{{ 'Waiting for the customer to pay. The order comes to New once the payment is confirmed, or is cancelled after a day.' | translate }}</div>
          <button class="btn btn-outline-danger so-cancel mt-1" [disabled]="busy" (click)="cancel(o)">{{ 'Cancel order' | translate }}</button>
        </div>

        <div *ngIf="o.paymentStatus === 'REFUND_DUE'" class="so-refund mt-2" style="border:1px solid #b91c1c; border-radius:6px; padding:0.6rem;">
          <strong style="color:#b91c1c;">{{ 'Refund due: {p0}' | translate: { p0: (o.paidAmount | number: '1.0-2') } }}</strong>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'The customer paid online and the order will not be delivered. Give the money back, then record it here.' | translate }}</div>
          <div class="flex gap-2 mt-1" style="flex-wrap:wrap; align-items:flex-end;">
            <button class="btn btn-primary so-refund-provider" [disabled]="busy" (click)="refund(o, false)">{{ 'Refund through the provider' | translate }}</button>
            <input class="form-control so-refund-ref" style="width:200px;" maxlength="80" [placeholder]="'Reference of a refund you made yourself' | translate" [(ngModel)]="refundRef[o.id]" [name]="'rrf' + o.id" />
            <button class="btn btn-outline-secondary so-refund-manual" [disabled]="busy" (click)="refund(o, true)">{{ 'I refunded it myself' | translate }}</button>
          </div>
        </div>
        <div *ngIf="(o.refundDifference ?? 0) > 0" class="so-refund-diff mt-2" style="border:1px solid #b45309; border-radius:6px; padding:0.6rem;">
          <strong style="color:#b45309;">{{ 'Refund due for the goods not delivered: {p0}' | translate: { p0: (o.refundDifference | number: '1.0-2') } }}</strong>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'The customer paid online for more than was delivered. Give the difference back, then record it here.' | translate }}</div>
          <div class="flex gap-2 mt-1" style="flex-wrap:wrap; align-items:flex-end;">
            <button class="btn btn-primary so-diff-provider" [disabled]="busy" (click)="refundDiff(o, false)">{{ 'Refund through the provider' | translate }}</button>
            <input class="form-control so-diff-ref" style="width:200px;" maxlength="80" [placeholder]="'Reference of a refund you made yourself' | translate" [(ngModel)]="refundRef[o.id]" [name]="'rdf' + o.id" />
            <button class="btn btn-outline-secondary so-diff-manual" [disabled]="busy" (click)="refundDiff(o, true)">{{ 'I refunded it myself' | translate }}</button>
          </div>
        </div>
        <div class="text-muted mt-1 so-part-refund" *ngIf="o.paymentStatus === 'PAID' && o.refundedTotal > 0" style="font-size:0.85rem;">{{ 'Given back so far for returns: {p0} of {p1}' | translate: { p0: (o.refundedTotal | number: '1.0-2'), p1: (o.paidAmount | number: '1.0-2') } }}</div>
        <div class="text-muted mt-1" *ngIf="o.paymentStatus === 'REFUNDED'" style="font-size:0.85rem;">{{ 'Refunded {p0} · reference {p1}' | translate: { p0: (o.paidAmount | number: '1.0-2'), p1: o.refundReference } }}</div>

        <div *ngIf="o.status === 'PLACED'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <button class="btn btn-primary so-accept" [disabled]="busy" (click)="accept(o, false)">{{ 'Accept' | translate }}</button>
          <button *ngIf="shortage[o.id]" class="btn btn-outline-warning so-accept-anyway" [disabled]="busy" (click)="accept(o, true)">{{ 'Accept anyway' | translate }}</button>
          <input class="form-control so-reason" style="width:220px;" maxlength="160" [placeholder]="'Reason, if you refuse' | translate" [(ngModel)]="reason[o.id]" [name]="'rsn' + o.id" />
          <button class="btn btn-outline-danger so-reject" [disabled]="busy" (click)="reject(o)">{{ 'Reject' | translate }}</button>
        </div>

        <div *ngIf="o.status === 'ACCEPTED' || o.status === 'PACKED'" class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary so-pack" *ngIf="o.status === 'ACCEPTED'" [disabled]="busy" (click)="run(api.pack(o.id))">{{ 'Mark packed' | translate }}</button>
          <button class="btn btn-primary so-out" *ngIf="o.deliveryMethod === 'DELIVERY'" [disabled]="busy" (click)="run(api.outForDelivery(o.id))">{{ 'Out for delivery' | translate }}</button>
          <button class="btn btn-primary so-ready" *ngIf="o.deliveryMethod === 'PICKUP'" [disabled]="busy" (click)="run(api.readyForPickup(o.id))">{{ 'Ready for pick-up' | translate }}</button>
          <button class="btn btn-outline-danger so-cancel" [disabled]="busy" (click)="cancel(o)">{{ 'Cancel order' | translate }}</button>
        </div>

        <div *ngIf="o.status === 'OUT_FOR_DELIVERY' || o.status === 'READY_FOR_PICKUP'" class="so-partial mt-2">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="so-part-toggle" [ngModel]="part[o.id] === true" (ngModelChange)="part[o.id] = $event" [name]="'part' + o.id" /> {{ 'The customer takes only part of the goods' | translate }}</label>
          <div *ngIf="part[o.id]" class="so-part-box" style="margin:0.4rem 0 0 1.4rem;">
            <div *ngFor="let l of o.lines; let i = index" class="flex gap-2" style="align-items:center; padding:0.15rem 0;">
              <input type="number" class="form-control so-kept" style="width:90px;" min="0" [max]="l.quantity" step="any" [ngModel]="keptOf(o, i)" (ngModelChange)="setKept(o, i, $event)" [name]="'kept' + o.id + '-' + i" />
              <span>{{ 'of {p0} × {p1}' | translate: { p0: l.quantity, p1: l.name } }}</span>
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'What is not delivered stays in the shop: the invoice, the stock and the cash to collect cover only what the customer takes. The delivery fee stays. If the customer paid online, the difference is then to be refunded.' | translate }}</div>
          </div>
        </div>
        <div *ngIf="o.status === 'OUT_FOR_DELIVERY' && o.deliveryMethod === 'DELIVERY'" class="so-failed mt-2">
          <button class="btn btn-sm btn-outline-warning so-fail-open" *ngIf="!failOpen[o.id]" (click)="failOpen[o.id] = true">{{ 'Delivery failed' | translate }}</button>
          <div *ngIf="failOpen[o.id]" class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <select class="form-select so-fail-reason" style="width:250px;" [ngModel]="failReason[o.id] || failReasons[0]" (ngModelChange)="failReason[o.id] = $event" [name]="'frs' + o.id">
              <option *ngFor="let r of failReasons" [value]="r">{{ r | translate }}</option>
            </select>
            <input class="form-control so-fail-note" style="width:220px;" maxlength="100" [placeholder]="'Details (optional)' | translate" [(ngModel)]="failNote[o.id]" [name]="'fnt' + o.id" />
            <button class="btn btn-warning so-fail-send" [disabled]="busy" (click)="failDelivery(o)">{{ 'Back to packed, try again later' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary" (click)="failOpen[o.id] = false">{{ 'Cancel' | translate }}</button>
          </div>
          <div class="text-muted" *ngIf="failOpen[o.id]" style="font-size:0.8rem;">{{ 'The order goes back to Packed with the reason. The customer is told and the stock stays reserved. Send it out again later, or cancel it.' | translate }}</div>
        </div>
        <div *ngIf="o.status === 'OUT_FOR_DELIVERY' || o.status === 'READY_FOR_PICKUP'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" *ngIf="multiWarehouse || fulfilOptions.length > 1">
            <label class="form-label">{{ 'Take stock from' | translate }}</label>
            <select class="form-select" style="width:240px;" [(ngModel)]="location[o.id]" [name]="'loc' + o.id">
              <option [ngValue]="null" [disabled]="multiWarehouse">{{ multiWarehouse ? ('Select a location…' | translate) : ('Default (main warehouse)' | translate) }}</option>
              <option *ngFor="let l of fulfilOptions" [ngValue]="l.id">{{ l.label }}</option>
            </select>
          </div>
          <span *ngIf="o.paymentStatus === 'PAID'" class="text-muted">{{ 'Already paid online: nothing to collect.' | translate }}</span>
          <label *ngIf="o.paymentStatus !== 'PAID'" class="flex gap-1" style="align-items:center;"><input type="checkbox" [ngModel]="collect[o.id] !== false" (ngModelChange)="collect[o.id] = $event" [name]="'col' + o.id" /> {{ 'Cash collected' | translate }}</label>
          <select *ngIf="o.paymentStatus !== 'PAID' && collect[o.id] !== false && methods.length > 1" class="form-select" style="width:160px;" [(ngModel)]="method[o.id]" [name]="'met' + o.id">
            <option *ngFor="let m of methods" [ngValue]="m.code">{{ m.name }}</option>
          </select>
          <button class="btn btn-primary so-complete" [disabled]="busy" (click)="complete(o)">{{ (o.paymentStatus === 'PAID' ? 'Complete: hand over and invoice' : 'Complete: hand over, invoice and record payment') | translate }}</button>
          <button class="btn btn-outline-danger so-cancel" [disabled]="busy" (click)="cancel(o)">{{ 'Cancel order' | translate }}</button>
        </div>

        <!-- v1.56.0: the customer holds a code and can sign: either completes the order (stock out, invoice, cash recorded) -->
        <div *ngIf="o.status === 'OUT_FOR_DELIVERY' || o.status === 'READY_FOR_PICKUP'" class="so-receive mt-2" style="border-top:1px dashed #9ca3af; padding-top:0.5rem;">
          <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" style="margin:0;">
              <label class="form-label">{{ "Customer's delivery code" | translate }}</label>
              <input class="form-control so-rcode" style="width:130px; letter-spacing:0.25em; font-weight:600;" maxlength="4" inputmode="numeric" autocomplete="off" [(ngModel)]="rcode[o.id]" [name]="'rcode' + o.id" placeholder="••••" />
            </div>
            <button class="btn btn-primary so-confirm-code" [disabled]="busy || !(rcode[o.id] || '').trim()" (click)="receive(o, true)">{{ 'Confirm with the code' | translate }}</button>
            <button class="btn btn-outline-secondary so-sign-open" *ngIf="!sigOpen[o.id]" (click)="sigOpen[o.id] = true">✍ {{ 'The customer signs here' | translate }}</button>
          </div>
          <div *ngIf="sigOpen[o.id]" class="mt-2" style="max-width:420px;">
            <app-signature-pad [height]="140" (signature)="sig[o.id] = $event"></app-signature-pad>
            <input class="form-control mt-1 so-signed-name" maxlength="120" [(ngModel)]="sigName[o.id]" [name]="'sname' + o.id" [placeholder]="'Name of the person who signs (optional)' | translate" />
            <button class="btn btn-primary mt-1 so-confirm-sign" [disabled]="busy || !sig[o.id]" (click)="receive(o, false)">{{ 'Confirm with the signature' | translate }}</button>
          </div>
          <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'The code is in the customer\\'s account and message; it is never shown here. After five wrong codes it is blocked for half an hour.' | translate }}
            <button class="btn btn-sm btn-outline-secondary so-reissue" style="margin-left:0.5rem;" [disabled]="busy" (click)="reissue(o)">{{ 'Send a new code to the customer' | translate }}</button></div>
        </div>
        <div *ngIf="o.receivedAt" class="so-received mt-2" style="font-size:0.9rem;">
          ✔ {{ 'Received' | translate }} {{ o.receivedAt | date: 'short' }} · {{ receivedHow(o) | translate }}<span *ngIf="o.signedName"> · {{ o.signedName }}</span>
          <button class="btn btn-sm btn-outline-secondary so-signature ml-2" *ngIf="o.hasSignature" style="margin-left:0.5rem;" (click)="showSignature(o)">{{ 'View signature' | translate }}</button>
        </div>

        <details class="mt-2" *ngIf="o.events.length > 1">
          <summary class="text-muted" style="cursor:pointer;">{{ 'History' | translate }}</summary>
          <div *ngFor="let e of o.events" class="text-muted" style="font-size:0.85rem;">{{ e.at | date: 'short' }} · {{ statusLabel(e.status) | translate }}<span *ngIf="e.by"> · {{ e.by }}</span><span *ngIf="e.note"> — {{ e.note }}</span></div>
        </details>
      </div>
      <p class="text-muted so-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'PLACED' ? ('Nothing is waiting for an answer.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreOrdersComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'PLACED', label: 'New' },
    { key: 'AWAITING_PAYMENT', label: 'Awaiting payment' },
    { key: 'OPEN', label: 'Open' },
    { key: 'REFUND_DUE', label: 'Refunds due' },
    { key: 'COMPLETED', label: 'Completed' },
    { key: 'REJECTED', label: 'Rejected' },
    { key: 'CANCELLED', label: 'Cancelled' },
    { key: '', label: 'Everything' },
  ];
  tab = 'PLACED';
  q = '';
  pending = 0;
  overdue = 0;
  alertMinutes = 0;
  awaiting = 0;
  refundsDue = 0;
  /** How many orders each of the other tabs holds. */
  openCount = 0;
  completedCount = 0;
  rejectedCount = 0;
  cancelledCount = 0;
  allCount = 0;
  /** Returns waiting for an answer or for the goods: the badge on the Returns button. */
  returnsWaiting = 0;
  /** Refunds to give and payments to look at: the badge on the Online payments button. */
  attention = 0;
  refundRef: Record<number, string> = {};
  error: string | null = null;
  busy = false;
  reason: Record<number, string> = {};
  shortage: Record<number, boolean> = {};
  location: Record<number, number | null> = {};
  collect: Record<number, boolean> = {};
  method: Record<number, string> = {};
  multiWarehouse = false;
  fulfilOptions: LocationOption[] = [];
  methods: PaymentMethodOption[] = [];
  list = new ListState<StoreOrder>((f, p, sz) => this.api.page(f, p, sz, this.driverFilter), 'Failed to load the online orders');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(public api: OnlineStoreApiService, private warehouseApi: WarehouseApiService, private storeSettingsApi: StoreSettingsApiService, private paymentMethodApi: PaymentMethodApiService) {}

  drivers: Driver[] = [];
  /** null = all orders, 0 = delivery orders without a driver, else one driver's orders. */
  driverFilter: number | null = null;

  /** The country code of the shop's phone numbers, to turn 0788... into 250788... for a WhatsApp link. */
  private countryCode = '';

  /** A wa.me link that opens a WhatsApp chat with the customer of the order, with a short opening line the staff member can change before sending. */
  waLink(o: { customerPhone: string; customerName: string; code: string }): string | null {
    const d = this.waNumber(o.customerPhone);
    return d ? `https://wa.me/${d}?text=${encodeURIComponent(T('Hello {p0}, this is the shop about your order {p1}.', { p0: o.customerName, p1: o.code }))}` : null;
  }

  private waNumber(phone: string | null): string | null {
    let d = (phone ?? '').replace(/[^0-9]/g, '');
    if (d.startsWith('0') && this.countryCode) d = this.countryCode + d.substring(1);
    return d.length < 8 ? null : d;
  }

  /** A WhatsApp message to the order's driver with what he needs: who, where, how much to collect. */
  driverLink(o: StoreOrder): string | null {
    const d = this.waNumber(o.driverPhone);
    if (!d) return null;
    const collect = o.paymentStatus === 'PAID' ? T('nothing (already paid)') : String(o.total);
    const where = (o.zoneName ? o.zoneName + ' — ' : '') + (o.customerAddress ?? '');
    const text = T('Delivery {p0}: {p1}, {p2}. Address: {p3}. To collect: {p4}.', { p0: o.code, p1: o.customerName, p2: o.customerPhone, p3: where, p4: collect });
    return `https://wa.me/${d}?text=${encodeURIComponent(text)}`;
  }

  /** The driver can be set or changed until the order is delivered. */
  canAssign(o: StoreOrder): boolean {
    return ['PLACED', 'ACCEPTED', 'PACKED', 'OUT_FOR_DELIVERY'].includes(o.status);
  }

  /** The drivers to pick from: those switched on, plus the order's current one even if switched off since. */
  driversFor(o: StoreOrder): Driver[] {
    return this.drivers.filter((d) => d.active || d.id === o.driverId);
  }

  assign(o: StoreOrder, driverId: number | null): void {
    this.run(this.api.assignDriver(o.id, driverId));
  }

  private open(call: import('rxjs').Observable<Blob>): void {
    this.busy = true;
    this.error = null;
    call.subscribe({
      next: (b) => {
        this.busy = false;
        window.open(URL.createObjectURL(b), '_blank');
      },
      error: (e) => {
        this.busy = false;
        this.error = T('Could not open the file');
        void e;
      },
    });
  }

  slip(o: StoreOrder): void {
    this.open(this.api.deliverySlip(o.id));
  }

  runSheet(): void {
    if (this.driverFilter !== null) this.open(this.api.deliveryRun(this.driverFilter));
  }

  private loadDrivers(): void {
    this.api.drivers().pipe(catchError(() => of([] as Driver[]))).subscribe((d) => (this.drivers = d));
  }

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.countryCode = (s.textCountryCode ?? '').replace(/[^0-9]/g, '')), error: () => {} });
    this.loadOptions();
    this.loadDrivers();
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** Never blocks the page: if settings, warehouses or payment methods cannot be read, the pickers stay hidden and the defaults apply. */
  private loadOptions(): void {
    this.paymentMethodApi.listActive().pipe(catchError(() => of([] as PaymentMethodOption[]))).subscribe((m) => (this.methods = m.filter((x) => x.cash)));
    this.storeSettingsApi.get().pipe(catchError(() => of(null))).subscribe((settings) => {
      this.multiWarehouse = !!settings?.multiWarehouseEnabled;
      this.warehouseApi.listAll().pipe(catchError(() => of([] as Warehouse[]))).subscribe((all) => {
        const active = all.filter((w) => w.active);
        if (active.length === 0) return;
        forkJoin(active.map((w) => this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[]))))).subscribe((perWarehouse) => {
          const options: LocationOption[] = [];
          active.forEach((w, i) => {
            for (const l of perWarehouse[i]) {
              if (l.active) options.push({ id: l.id, label: `${w.name} / ${l.name}`, type: l.type, primaryWarehouse: w.primary });
            }
          });
          // Goods only ever leave from SALES locations, and with multi-warehouse on never from the Main warehouse.
          this.fulfilOptions = options.filter((o) => o.type === 'SALES' && !(this.multiWarehouse && o.primaryWarehouse));
        });
      });
    });
  }

  statusLabel(s: string): string {
    const labels: Record<string, string> = {
      AWAITING_PAYMENT: 'Awaiting payment', PLACED: 'New', ACCEPTED: 'Accepted', PACKED: 'Packed', OUT_FOR_DELIVERY: 'Out for delivery', READY_FOR_PICKUP: 'Ready for pick-up',
      COMPLETED: 'Completed', REJECTED: 'Rejected', CANCELLED: 'Cancelled',
    };
    return labels[s] ?? s;
  }

  /** The number of orders on a tab (shown in brackets after its name when there are some). */
  badge(key: string): number {
    switch (key) {
      case 'PLACED': return this.pending;
      case 'AWAITING_PAYMENT': return this.awaiting;
      case 'OPEN': return this.openCount;
      case 'REFUND_DUE': return this.refundsDue;
      case 'COMPLETED': return this.completedCount;
      case 'REJECTED': return this.rejectedCount;
      case 'CANCELLED': return this.cancelledCount;
      default: return this.allCount;
    }
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  reload(): void {
    this.loadDrivers();
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab;
    this.list.apply(f);
    this.count();
  }

  /** Whole minutes since the customer placed the order. */
  waiting(o: StoreOrder): number {
    return Math.max(0, Math.floor((Date.now() - new Date(o.createdAt).getTime()) / 60000));
  }

  /** Waiting longer than the alert time set in the shop settings (no alert time = never late). */
  late(o: StoreOrder): boolean {
    return this.alertMinutes > 0 && this.waiting(o) >= this.alertMinutes;
  }

  count(): void {
    this.api.pendingCount().subscribe({
      next: (r) => {
        this.pending = r.pending;
        this.overdue = r.overdue ?? 0;
        this.alertMinutes = r.alertMinutes ?? 0;
        this.awaiting = r.awaitingPayment ?? 0;
        this.refundsDue = r.refundsDue ?? 0;
        this.openCount = r.open ?? 0;
        this.completedCount = r.completed ?? 0;
        this.rejectedCount = r.rejected ?? 0;
        this.cancelledCount = r.cancelled ?? 0;
        this.allCount = r.all ?? 0;
        this.attention = (r.refundsDue ?? 0) + (r.paymentsToReview ?? 0);
        this.returnsWaiting = (r.returnsRequested ?? 0) + (r.returnsToRefund ?? 0);
      },
      error: () => {},
    });
  }

  /** Runs one order action and refreshes the list. */
  run(call: import('rxjs').Observable<StoreOrder>, fail?: (e: any) => void): void {
    this.busy = true;
    this.error = null;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        if (fail) fail(e);
        else this.error = e?.error?.message ?? T('That did not work');
        this.list.load();
      },
    });
  }

  accept(o: StoreOrder, anyway: boolean): void {
    this.run(this.api.accept(o.id, anyway), (e) => {
      // 409: the stock does not cover the order. Staff may accept it anyway (the sale order is then over-promised) or reject it.
      this.shortage[o.id] = e?.status === 409;
      this.error = e?.error?.message ?? T('Could not accept the order');
    });
  }

  reject(o: StoreOrder): void {
    const reason = (this.reason[o.id] ?? '').trim();
    if (!reason) {
      this.error = T('Write a reason: the customer will see it.');
      return;
    }
    this.run(this.api.reject(o.id, reason));
  }

  payLabel(o: StoreOrder): string {
    switch (o.paymentStatus) {
      case 'PAID': return 'Paid online';
      case 'AWAITING': return 'Awaiting payment';
      case 'FAILED': return 'Payment failed';
      case 'REFUND_DUE': return 'Refund due';
      case 'REFUNDED': return 'Refunded';
      default: return o.paymentStatus;
    }
  }

  payColor(o: StoreOrder): string {
    return o.paymentStatus === 'PAID' ? '#15803d' : o.paymentStatus === 'REFUND_DUE' || o.paymentStatus === 'FAILED' ? '#b91c1c' : o.paymentStatus === 'AWAITING' ? '#b45309' : '';
  }

  channelLabel(c: string): string {
    return c === 'CARD' ? 'Card' : c === 'MOBILE_MONEY' ? 'Mobile money' : c;
  }

  refund(o: StoreOrder, manual: boolean): void {
    const ref = (this.refundRef[o.id] ?? '').trim();
    if (manual && ref.length < 3) {
      this.error = T('Enter the reference of the refund you made (at least 3 characters)');
      return;
    }
    if (!confirm(T(manual ? 'Record that you refunded {p0} for order {p1}?' : 'Refund {p0} for order {p1} through the payment provider?', { p0: String(o.paidAmount ?? ''), p1: o.code }))) return;
    this.run(this.api.refund(o.id, { manual, reference: manual ? ref : null }));
  }

  cancel(o: StoreOrder): void {
    const text = o.paymentStatus === 'PAID' ? 'Cancel order {p0}? The customer paid online, so a refund will be due.' : 'Cancel order {p0}? The stock it holds is released.';
    if (!confirm(T(text, { p0: o.code }))) return;
    this.run(this.api.cancelOrder(o.id, ''));
  }

  part: Record<number, boolean> = {};
  keptQty: Record<number, Record<number, number>> = {};
  failOpen: Record<number, boolean> = {};
  failReason: Record<number, string> = {};
  failNote: Record<number, string> = {};
  failReasons = ['Customer not at home', 'Customer refused the goods', 'Wrong or unreachable address', 'Customer did not answer the phone', 'Other'];

  /** The quantity of line i the customer takes (the whole quantity until staff change it). */
  keptOf(o: StoreOrder, i: number): number {
    return this.keptQty[o.id]?.[i] ?? o.lines[i].quantity;
  }

  setKept(o: StoreOrder, i: number, v: number | string | null): void {
    const n = Math.min(Math.max(Number(v) || 0, 0), o.lines[i].quantity);
    (this.keptQty[o.id] ??= {})[i] = n;
  }

  /** The quantities kept per product when "only part of the goods" is ticked, else null (everything is delivered). */
  kept(o: StoreOrder): KeptLine[] | null {
    if (!this.part[o.id]) return null;
    const by = new Map<number, number>();
    o.lines.forEach((l, i) => by.set(l.productId, (by.get(l.productId) ?? 0) + this.keptOf(o, i)));
    return Array.from(by, ([productId, quantity]) => ({ productId, quantity }));
  }

  reissue(o: StoreOrder): void {
    if (!confirm(T('Send a new code for order {p0} to the customer? The old code will stop working.', { p0: o.code }))) return;
    this.run(this.api.reissueCode(o.id));
  }

  failDelivery(o: StoreOrder): void {
    const reason = this.failReason[o.id] || this.failReasons[0];
    const note = (this.failNote[o.id] ?? '').trim();
    this.run(this.api.deliveryFailed(o.id, T(reason) + (note ? ': ' + note : '')), undefined);
    this.failOpen[o.id] = false;
    this.failNote[o.id] = '';
  }

  refundDiff(o: StoreOrder, manual: boolean): void {
    const ref = (this.refundRef[o.id] ?? '').trim();
    if (manual && ref.length < 3) {
      this.error = T('Enter the reference of the refund you made (at least 3 characters)');
      return;
    }
    if (!confirm(T(manual ? 'Record that you refunded {p0} for order {p1}?' : 'Refund {p0} for order {p1} through the payment provider?', { p0: String(o.refundDifference ?? ''), p1: o.code }))) return;
    this.run(this.api.refundDifference(o.id, { manual, reference: manual ? ref : null }));
  }

  rcode: Record<number, string> = {};
  sigOpen: Record<number, boolean> = {};
  sig: Record<number, string | null> = {};
  sigName: Record<number, string> = {};

  /** The goods are in the customer's hands: the code the customer gave, or the signature drawn here. Completes the order. */
  receive(o: StoreOrder, byCode: boolean): void {
    if (this.multiWarehouse && (this.location[o.id] ?? null) === null) {
      this.error = T('Choose the warehouse location the goods leave from.');
      return;
    }
    const code = byCode ? (this.rcode[o.id] ?? '').trim() : '';
    if (byCode && !code) return;
    this.run(this.api.receive(o.id, { code: byCode ? code : null, signature: byCode ? null : this.sig[o.id] ?? null, signedName: byCode ? null : (this.sigName[o.id] ?? '').trim() || null, locationId: this.location[o.id] ?? null, kept: this.kept(o) }));
  }

  receivedHow(o: StoreOrder): string {
    return o.receivedVia === 'CODE' ? 'the customer\'s code was typed' : o.receivedVia === 'DRIVER' ? 'the customer signed on the phone' : 'the customer confirmed it in the shop';
  }

  showSignature(o: StoreOrder): void {
    this.open(this.api.orderSignature(o.id));
  }

  complete(o: StoreOrder): void {
    if (this.multiWarehouse && (this.location[o.id] ?? null) === null) {
      this.error = T('Choose the warehouse location the goods leave from.');
      return;
    }
    this.run(this.api.complete(o.id, { locationId: this.location[o.id] ?? null, collectPayment: this.collect[o.id] !== false, method: this.method[o.id] || 'CASH', kept: this.kept(o) }));
  }
}
