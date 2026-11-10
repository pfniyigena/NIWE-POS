import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { PricingApiService, GroupRow } from '../../core/api/pricing-api.service';
import { PromotionPayload, PromotionRow, PromotionScope, PromotionType, PromotionUse, PromotionsApiService } from '../../core/api/promotions-api.service';
import { Category, Product } from '../../core/models';

/** Sales > Promotions. A promotion takes a percent or an amount off a product, a category or the whole cart, or gives items free (buy 2, get 1). With a coupon code it only works when the cashier types the code at the till. */
@Component({
  selector: 'app-promotions',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">{{ 'Promotions and coupons' | translate }}</h3>
        <button class="btn btn-primary promo-new" *ngIf="canManage && !editing" (click)="startNew()">{{ 'New promotion' | translate }}</button>
      </div>
      <p class="text-muted">{{ 'The till works these out by itself. Each line gets the one best promotion for its product or category, then the one best whole-cart promotion is taken off what is left. All of it comes after any discount the cashier types.' | translate }}</p>
      <div class="alert alert-danger promo-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success promo-done" *ngIf="done">{{ done }}</div>

      <div class="card mb-3 promo-form" *ngIf="editing" style="padding:1rem;">
        <h4>{{ editingId ? ('Edit promotion' | translate) : ('New promotion' | translate) }}</h4>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control promo-name" name="name" [(ngModel)]="f.name" /></div>
          <div class="form-group"><label class="form-label">{{ 'Note (optional)' | translate }}</label><input class="form-control promo-desc" name="description" [(ngModel)]="description" /></div>
          <div class="form-group"><label class="form-label">{{ 'What it does' | translate }}</label>
            <select class="form-control promo-type" name="type" [(ngModel)]="f.type">
              <option value="PERCENT_OFF">{{ 'Percent off' | translate }}</option>
              <option value="AMOUNT_OFF">{{ 'Amount off' | translate }}</option>
              <option value="BUY_X_GET_Y" [disabled]="f.scope === 'CART'">{{ 'Buy some, get some free' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'It applies to' | translate }}</label>
            <select class="form-control promo-scope" name="scope" [(ngModel)]="f.scope" (ngModelChange)="scopeChanged()">
              <option value="PRODUCT">{{ 'One product' | translate }}</option>
              <option value="CATEGORY">{{ 'A category' | translate }}</option>
              <option value="CART">{{ 'The whole cart' | translate }}</option>
            </select></div>
          <div class="form-group" *ngIf="f.scope === 'PRODUCT'" style="position:relative;">
            <label class="form-label">{{ 'Product' | translate }}</label>
            <input class="form-control promo-search-product" [placeholder]="'Type a name or SKU' | translate" [(ngModel)]="productSearch" name="productSearch" (input)="searchProducts()" autocomplete="off" />
            <div class="promo-picks" *ngIf="picks.length > 0" style="position:absolute; z-index:5; background:var(--surface); border:1px solid var(--border); width:100%; max-height:14rem; overflow:auto;">
              <button type="button" class="btn btn-link promo-pick" *ngFor="let p of picks" style="display:block; width:100%; text-align:left;" (click)="choose(p)">{{ p.name }} <span class="text-muted">{{ p.sku }}</span></button>
            </div>
          </div>
          <div class="form-group" *ngIf="f.scope === 'CATEGORY'">
            <label class="form-label">{{ 'Category' | translate }}</label>
            <select class="form-control promo-category" name="category" [(ngModel)]="f.targetId">
              <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group" *ngIf="f.type !== 'BUY_X_GET_Y'">
            <label class="form-label">{{ f.type === 'PERCENT_OFF' ? ('Percent off' | translate) : (f.scope === 'CART' ? ('Amount off the cart' | translate) : ('Amount off each item' | translate)) }}</label>
            <input class="form-control promo-value" type="number" min="0" step="0.01" name="value" [(ngModel)]="f.value" /></div>
          <div class="form-group" *ngIf="f.type === 'BUY_X_GET_Y'">
            <label class="form-label">{{ 'Buy this many' | translate }}</label><input class="form-control promo-buy" type="number" min="0.0001" step="any" name="buy" [(ngModel)]="f.buyQuantity" /></div>
          <div class="form-group" *ngIf="f.type === 'BUY_X_GET_Y'">
            <label class="form-label">{{ 'and get this many free' | translate }}</label><input class="form-control promo-free" type="number" min="0.0001" step="any" name="free" [(ngModel)]="f.freeQuantity" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Repeats: buy 2 get 1 free gives 2 free on 6.' | translate }}</div></div>
          <div class="form-group" *ngIf="f.type !== 'BUY_X_GET_Y'">
            <label class="form-label">{{ f.scope === 'CART' ? ('Needs at least this many items' | translate) : ('Needs at least this quantity' | translate) }}</label>
            <input class="form-control promo-minqty" type="number" min="0.0001" step="any" name="minq" [(ngModel)]="f.minQuantity" /></div>
          <div class="form-group" *ngIf="f.scope === 'CART'">
            <label class="form-label">{{ 'Needs at least this spend (optional)' | translate }}</label>
            <input class="form-control promo-minspend" type="number" min="0" step="0.01" name="minspend" [(ngModel)]="f.minSpend" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Counted before any promotion, after the cashier\\'s own discount.' | translate }}</div></div>
          <div class="form-group"><label class="form-label">{{ 'Valid from (optional)' | translate }}</label><input class="form-control promo-from" type="date" name="from" [(ngModel)]="validFrom" /></div>
          <div class="form-group"><label class="form-label">{{ 'Valid to (optional)' | translate }}</label><input class="form-control promo-to" type="date" name="to" [(ngModel)]="validTo" /></div>
          <div class="form-group"><label class="form-label">{{ 'Coupon code (optional)' | translate }}</label>
            <input class="form-control promo-code" name="code" [(ngModel)]="coupon" [placeholder]="'e.g. SPRING10' | translate" style="text-transform:uppercase;" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'With a code, the promotion only works on a cart where the cashier types it. Without one it works by itself.' | translate }}</div></div>
          <div class="form-group"><label class="form-label">{{ 'Customer group (optional)' | translate }}</label>
            <select class="form-control promo-group" name="group" [(ngModel)]="f.customerGroupId">
              <option [ngValue]="null">{{ 'Everyone' | translate }}</option>
              <option *ngFor="let g of groups" [ngValue]="g.id">{{ g.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Most times it can be used, in all (optional)' | translate }}</label><input class="form-control promo-maxuses" type="number" min="1" step="1" name="maxuses" [(ngModel)]="f.maxUses" /></div>
          <div class="form-group"><label class="form-label">{{ 'Most times one customer can use it (optional)' | translate }}</label><input class="form-control promo-maxper" type="number" min="1" step="1" name="maxper" [(ngModel)]="f.maxUsesPerCustomer" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'Not counted for the walk-in customer.' | translate }}</div></div>
          <div class="form-group"><label class="flex align-center gap-2" style="margin-top:1.6rem;"><input type="checkbox" class="promo-active" name="active" [(ngModel)]="f.active" /> {{ 'Switched on' | translate }}</label></div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary promo-save" (click)="save()">{{ 'Save' | translate }}</button>
          <button class="btn btn-link promo-cancel" (click)="cancel()">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <table>
        <thead><tr><th>{{ 'Promotion' | translate }}</th><th>{{ 'What it does' | translate }}</th><th>{{ 'Coupon' | translate }}</th><th>{{ 'Valid' | translate }}</th><th class="text-end">{{ 'Used' | translate }}</th><th class="text-end">{{ 'Given' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let p of rows" class="promo-row" [attr.data-name]="p.name">
            <td data-label="Promotion"><strong>{{ p.name }}</strong>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="p.description">{{ p.description }}</div>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="p.customerGroupName">{{ 'Only for {p0}' | translate: { p0: (p.customerGroupName) } }}</div></td>
            <td data-label="What it does">{{ p.summary }}
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="p.minQuantity > 1 || p.minSpend">{{ 'From {p0}{p1}{p2}' | translate: { p0: (p.minQuantity > 1 ? p.minQuantity + ' items' : ''), p1: (p.minQuantity > 1 && p.minSpend ? ' and ' : ''), p2: (p.minSpend ? 'a spend of ' + (p.minSpend | number: '1.0-2') : '') } }}</div></td>
            <td data-label="Coupon"><code *ngIf="p.couponCode">{{ p.couponCode }}</code><span *ngIf="!p.couponCode" class="text-muted">{{ 'Automatic' | translate }}</span></td>
            <td data-label="Valid">{{ p.validFrom || p.validTo ? (p.validFrom || 'always') + ' to ' + (p.validTo || 'no end') : 'Always' }}</td>
            <td data-label="Used" class="text-end">{{ p.uses }}<span class="text-muted" *ngIf="p.maxUses"> / {{ p.maxUses }}</span></td>
            <td data-label="Given" class="text-end">{{ p.given | number: '1.0-2' }}</td>
            <td data-label="Status"><span [ngClass]="p.current ? 'text-success' : 'text-muted'">{{ p.current ? ('Running' | translate) : (p.active ? ('Outside its dates' | translate) : ('Switched off' | translate)) }}</span></td>
            <td data-label="" class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary promo-uses" *ngIf="p.uses > 0" (click)="showUses(p)">{{ 'Sales' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary promo-edit" *ngIf="canManage" (click)="startEdit(p)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger promo-delete" *ngIf="canManage" (click)="remove(p)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="rows.length === 0"><td colspan="8" class="text-muted">{{ 'No promotions yet.' | translate }}</td></tr>
        </tbody>
      </table>

      <div class="card mt-3 promo-usebox" *ngIf="usesFor" style="padding:1rem;">
        <div class="flex justify-between align-center"><h4 style="margin:0;">{{ 'Sales that used "{p0}"' | translate: { p0: (usesFor.name) } }}</h4><button class="btn btn-link promo-usesclose" (click)="usesFor = null">{{ 'Close' | translate }}</button></div>
        <table>
          <thead><tr><th>{{ 'Receipt' | translate }}</th><th>{{ 'Customer' | translate }}</th><th class="text-end">{{ 'Took off' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let u of useRows" class="promo-userow"><td data-label="Receipt">{{ u.invoiceNumber || ('#' + u.orderId) }}</td><td data-label="Customer">{{ u.customerName || '-' }}</td><td data-label="Took off" class="text-end">{{ u.amount | number: '1.2-2' }}</td></tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class PromotionsComponent implements OnInit {
  rows: PromotionRow[] = [];
  categories: Category[] = [];
  groups: GroupRow[] = [];
  error: string | null = null;
  done: string | null = null;
  editing = false;
  editingId: number | null = null;
  f = this.blank();
  description = '';
  validFrom = '';
  validTo = '';
  coupon = '';
  productSearch = '';
  picks: Product[] = [];
  usesFor: PromotionRow | null = null;
  useRows: PromotionUse[] = [];

  constructor(private api: PromotionsApiService, private catalog: CatalogApiService, private pricing: PricingApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.load();
    this.catalog.listCategories().subscribe({ next: (c) => (this.categories = c), error: () => (this.categories = []) });
    this.pricing.groups().subscribe({ next: (g) => (this.groups = g), error: () => (this.groups = []) });
  }

  private blank() {
    return {
      name: '',
      type: 'PERCENT_OFF' as PromotionType,
      scope: 'PRODUCT' as PromotionScope,
      targetId: null as number | null,
      value: null as number | null,
      buyQuantity: 2 as number | null,
      freeQuantity: 1 as number | null,
      minQuantity: 1,
      minSpend: null as number | null,
      customerGroupId: null as number | null,
      maxUses: null as number | null,
      maxUsesPerCustomer: null as number | null,
      active: true,
    };
  }

  load(): void {
    this.api.list().subscribe({
      next: (r) => (this.rows = r),
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the promotions'),
    });
  }

  startNew(): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = null;
    this.f = this.blank();
    this.description = '';
    this.validFrom = '';
    this.validTo = '';
    this.coupon = '';
    this.productSearch = '';
    this.picks = [];
  }

  startEdit(p: PromotionRow): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = p.id;
    this.f = {
      name: p.name,
      type: p.type,
      scope: p.scope,
      targetId: p.targetId,
      value: p.value,
      buyQuantity: p.buyQuantity ?? 2,
      freeQuantity: p.freeQuantity ?? 1,
      minQuantity: p.minQuantity,
      minSpend: p.minSpend,
      customerGroupId: p.customerGroupId,
      maxUses: p.maxUses,
      maxUsesPerCustomer: p.maxUsesPerCustomer,
      active: p.active,
    };
    this.description = p.description ?? '';
    this.validFrom = p.validFrom ?? '';
    this.validTo = p.validTo ?? '';
    this.coupon = p.couponCode ?? '';
    this.productSearch = p.scope === 'PRODUCT' ? p.targetName ?? '' : '';
    this.picks = [];
  }

  cancel(): void {
    this.editing = false;
  }

  scopeChanged(): void {
    this.f.targetId = null;
    this.productSearch = '';
    this.picks = [];
    if (this.f.scope === 'CART' && this.f.type === 'BUY_X_GET_Y') this.f.type = 'PERCENT_OFF';
  }

  searchProducts(): void {
    this.f.targetId = null;
    const q = this.productSearch.trim();
    if (q.length < 2) {
      this.picks = [];
      return;
    }
    this.catalog.listAllProducts(q, 0, 8).subscribe({ next: (r) => (this.picks = r.content), error: () => (this.picks = []) });
  }

  choose(p: Product): void {
    this.f.targetId = p.id;
    this.productSearch = p.name;
    this.picks = [];
  }

  save(): void {
    this.error = null;
    const f = this.f;
    const bxgy = f.type === 'BUY_X_GET_Y';
    const payload: PromotionPayload = {
      name: f.name,
      description: this.description || null,
      type: f.type,
      scope: f.scope,
      targetId: f.scope === 'CART' ? null : f.targetId,
      value: bxgy || f.value === null || (f.value as unknown) === '' ? null : Number(f.value),
      buyQuantity: bxgy ? Number(f.buyQuantity) : null,
      freeQuantity: bxgy ? Number(f.freeQuantity) : null,
      minQuantity: Number(f.minQuantity) || 1,
      minSpend: f.scope === 'CART' && f.minSpend !== null && (f.minSpend as unknown) !== '' ? Number(f.minSpend) : null,
      validFrom: this.validFrom || null,
      validTo: this.validTo || null,
      couponCode: this.coupon.trim() || null,
      maxUses: f.maxUses ? Number(f.maxUses) : null,
      maxUsesPerCustomer: f.maxUsesPerCustomer ? Number(f.maxUsesPerCustomer) : null,
      customerGroupId: f.customerGroupId,
      active: f.active,
    };
    const call = this.editingId ? this.api.update(this.editingId, payload) : this.api.create(payload);
    call.subscribe({
      next: (p) => {
        this.done = 'Saved "' + p.name + '".';
        this.editing = false;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save the promotion'),
    });
  }

  remove(p: PromotionRow): void {
    this.error = null;
    this.done = null;
    if (!confirm('Delete the promotion "' + p.name + '"?')) return;
    this.api.remove(p.id).subscribe({
      next: () => {
        this.done = 'Deleted "' + p.name + '".';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not delete the promotion'),
    });
  }

  showUses(p: PromotionRow): void {
    this.usesFor = p;
    this.useRows = [];
    this.api.uses(p.id).subscribe({ next: (u) => (this.useRows = u), error: (err) => (this.error = err?.error?.message ?? 'Could not load the sales') });
  }
}
