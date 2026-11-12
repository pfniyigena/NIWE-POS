import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, StoreCoupon } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Coupons: codes a customer types at checkout. Percent off the items (with an optional cap), a fixed amount off, or free delivery,
 * with a minimum order, dates, a total number of uses and "once per customer". The discount is worked out by the server, never by the customer's browser.
 */
@Component({
  selector: 'app-store-coupons',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Coupons' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
      </div>
      <p class="text-muted">{{ 'A coupon is a code the customer types at checkout. When there is at least one active coupon, the shop shows a coupon box. A discount lowers the prices on the sale order and invoice, so the invoice equals what the customer pays. Rejected and cancelled orders give their use back.' | translate }}</p>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
        <h4 class="mb-2">{{ editing ? ('Change the coupon' | translate) : ('New coupon' | translate) }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Code' | translate }}</label><input class="form-control cp-code" style="width:180px; text-transform:uppercase;" maxlength="40" name="code" [(ngModel)]="f.code" /></div>
          <div class="form-group"><label class="form-label">{{ 'What it gives' | translate }}</label>
            <select class="form-select cp-type" name="type" [(ngModel)]="f.type">
              <option value="PERCENT">{{ 'Percent off the items' | translate }}</option>
              <option value="FIXED">{{ 'Amount off the items' | translate }}</option>
              <option value="FREE_DELIVERY">{{ 'Free delivery' | translate }}</option>
            </select></div>
          <div class="form-group" *ngIf="f.type !== 'FREE_DELIVERY'"><label class="form-label">{{ f.type === 'PERCENT' ? ('Percent' | translate) : ('Amount' | translate) }}</label><input class="form-control cp-value" type="number" min="0" step="any" style="width:120px;" name="value" [(ngModel)]="f.value" /></div>
          <div class="form-group" *ngIf="f.type === 'PERCENT'"><label class="form-label">{{ 'Never more than (optional)' | translate }}</label><input class="form-control" type="number" min="0" step="any" style="width:140px;" name="max" [(ngModel)]="f.maxDiscount" /></div>
          <div class="form-group"><label class="form-label">{{ 'Minimum order' | translate }}</label><input class="form-control" type="number" min="0" step="any" style="width:130px;" name="min" [(ngModel)]="f.minOrder" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Valid from (optional)' | translate }}</label><input class="form-control" type="date" name="from" [(ngModel)]="f.validFrom" /></div>
          <div class="form-group"><label class="form-label">{{ 'Valid until (optional)' | translate }}</label><input class="form-control" type="date" name="until" [(ngModel)]="f.validUntil" /></div>
          <div class="form-group"><label class="form-label">{{ 'Orders it can be used on (optional)' | translate }}</label><input class="form-control" type="number" min="1" style="width:140px;" name="uses" [(ngModel)]="f.maxUses" /></div>
          <div class="form-group" style="min-width:220px;"><label class="form-label">{{ 'Note for staff (optional)' | translate }}</label><input class="form-control" maxlength="160" name="description" [(ngModel)]="f.description" /></div>
        </div>
        <div class="flex gap-3 mt-1" style="flex-wrap:wrap;">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="once" [(ngModel)]="f.oncePerCustomer" /> {{ 'Once per customer (same phone number or account)' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="active" [(ngModel)]="f.active" /> {{ 'Active' | translate }}</label>
        </div>
        <div class="flex gap-2 mt-2">
          <button class="btn btn-primary cp-save" type="submit" [disabled]="busy">{{ 'Save coupon' | translate }}</button>
          <button class="btn btn-outline-secondary" type="button" *ngIf="editing" (click)="reset()">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <div class="card" style="padding:1rem;">
        <table class="table-sm" *ngIf="list.length">
          <thead><tr><th>{{ 'Code' | translate }}</th><th>{{ 'Gives' | translate }}</th><th class="text-end">{{ 'Minimum order' | translate }}</th><th>{{ 'Valid' | translate }}</th><th class="text-end">{{ 'Used' | translate }}</th><th>{{ 'Active' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of list" class="cp-row">
              <td><strong>{{ c.code }}</strong><div class="text-muted" style="font-size:0.75rem;" *ngIf="c.description">{{ c.description }}</div></td>
              <td>{{ gives(c) }}<span class="text-muted" *ngIf="c.oncePerCustomer" style="font-size:0.75rem;"> · {{ 'once per customer' | translate }}</span></td>
              <td class="text-end">{{ c.minOrder | number: '1.0-2' }}</td>
              <td>{{ validity(c) }}</td>
              <td class="text-end">{{ c.used }}{{ c.maxUses ? ' / ' + c.maxUses : '' }}</td>
              <td>{{ c.active ? ('Yes' | translate) : ('No' | translate) }}</td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-outline-primary cp-edit" type="button" (click)="edit(c)">{{ 'Edit' | translate }}</button> <button class="btn btn-sm btn-outline-danger cp-delete" type="button" (click)="remove(c)">{{ 'Delete' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!list.length && !loading">{{ 'No coupons yet.' | translate }}</p>
      </div>
    </app-shell>
  `,
})
export class StoreCouponsComponent implements OnInit {
  list: StoreCoupon[] = [];
  loading = true;
  error: string | null = null;
  message: string | null = null;
  busy = false;
  editing: number | null = null;
  f = this.blank();

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private blank(): { code: string; type: 'PERCENT' | 'FIXED' | 'FREE_DELIVERY'; value: number | null; maxDiscount: number | null; minOrder: number; validFrom: string | null; validUntil: string | null; maxUses: number | null; oncePerCustomer: boolean; active: boolean; description: string } {
    return { code: '', type: 'PERCENT', value: 10, maxDiscount: null, minOrder: 0, validFrom: null, validUntil: null, maxUses: null, oncePerCustomer: true, active: true, description: '' };
  }

  load(): void {
    this.api.coupons().subscribe({ next: (l) => { this.list = l; this.loading = false; }, error: (e) => { this.loading = false; this.fail(e, 'Failed to load the coupons'); } });
  }

  private fail(e: any, fallback: string): void {
    this.message = null;
    this.error = e?.error?.message ?? T(fallback);
  }

  gives(c: StoreCoupon): string {
    if (c.type === 'FREE_DELIVERY') return T('Free delivery');
    if (c.type === 'FIXED') return T('{p0} off', { p0: c.value });
    return c.maxDiscount ? T('{p0}% off, at most {p1}', { p0: c.value, p1: c.maxDiscount }) : T('{p0}% off', { p0: c.value });
  }

  validity(c: StoreCoupon): string {
    if (!c.validFrom && !c.validUntil) return T('Always');
    if (c.validFrom && c.validUntil) return `${c.validFrom} → ${c.validUntil}`;
    return c.validFrom ? T('From {p0}', { p0: c.validFrom }) : T('Until {p0}', { p0: c.validUntil });
  }

  edit(c: StoreCoupon): void {
    this.editing = c.id;
    this.f = { code: c.code, type: c.type, value: c.value, maxDiscount: c.maxDiscount, minOrder: c.minOrder, validFrom: c.validFrom, validUntil: c.validUntil, maxUses: c.maxUses, oncePerCustomer: c.oncePerCustomer, active: c.active, description: c.description ?? '' };
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  reset(): void {
    this.editing = null;
    this.f = this.blank();
  }

  save(): void {
    this.error = null;
    this.message = null;
    this.busy = true;
    const body = { ...this.f, value: this.f.type === 'FREE_DELIVERY' ? 0 : this.f.value ?? 0, validFrom: this.f.validFrom || null, validUntil: this.f.validUntil || null, maxUses: this.f.maxUses || null, maxDiscount: this.f.type === 'PERCENT' ? this.f.maxDiscount || null : null };
    this.api.saveCoupon(this.editing, body).subscribe({
      next: () => { this.busy = false; this.message = T('Coupon saved'); this.reset(); this.load(); },
      error: (e) => { this.busy = false; this.fail(e, 'Could not save the coupon'); },
    });
  }

  remove(c: StoreCoupon): void {
    if (!confirm(T('Delete the coupon {p0}? Orders that used it keep their discount.', { p0: c.code }))) return;
    this.api.deleteCoupon(c.id).subscribe({ next: () => { this.message = T('Coupon deleted'); this.load(); }, error: (e) => this.fail(e, 'Could not delete the coupon') });
  }
}
