import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { MenuApiService } from '../core/api/menu-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { MenuCategory, MenuItem, PaymentMethodOption, RestaurantOrder } from '../core/models';

interface PaymentRow {
  method: string;
  amount: number;
}

/**
 * The dine-in order-taking screen for one table's open order (Fourier: "New dedicated screen,"
 * kept entirely separate from the walk-up POS terminal's own checkout flow). Add items from the
 * menu (grouped by category — the same menu built in Admin &gt; Restaurant &gt; Menu), send a
 * round to the kitchen, and settle the bill with one or more payments (Fourier: "split & merge
 * bills").
 */
@Component({
  selector: 'app-table-order',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!order && !error" class="text-muted">Loading…</div>

      <div *ngIf="order">
        <div class="flex justify-between align-center mb-1">
          <h3>
            {{ order.displayLabel }}
            <ng-container *ngIf="order.orderType === 'DINE_IN'"> — {{ order.guestCount }} guest{{ order.guestCount === 1 ? '' : 's' }}</ng-container>
            <span class="text-muted" style="font-size:0.9rem;"> ({{ order.status }})</span>
          </h3>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="order.status === 'CLOSED'" [routerLink]="['/admin/restaurant/receipts', order.id]">🧾 View receipt</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'DINE_IN'" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'DELIVERY'" routerLink="/admin/restaurant/deliveries">← Deliveries board</a>
            <a class="btn btn-outline-secondary" *ngIf="order.orderType === 'TAKEAWAY'" routerLink="/admin/restaurant/orders/new">← New order</a>
          </div>
        </div>
        <div class="text-muted mb-3" *ngIf="order.orderType !== 'DINE_IN'">
          <span *ngIf="order.customerPhone">📞 {{ order.customerPhone }}</span>
          <span *ngIf="order.customerAddress"> · 📍 {{ order.customerAddress }}</span>
          <span *ngIf="order.orderType === 'DELIVERY' && order.deliveryStatus">
            · {{ order.deliveryStatus.replace('_', ' ') }}<ng-container *ngIf="order.driverName"> ({{ order.driverName }})</ng-container>
          </span>
        </div>

        <div class="flex gap-3" style="align-items:flex-start;">
          <div class="card" style="padding:1rem; flex:1;" *ngIf="order.status === 'OPEN'">
            <h4 style="font-size:1rem;" class="mb-2">Menu</h4>
            <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
              <button
                *ngFor="let c of categories"
                class="btn btn-sm"
                [class.btn-primary]="selectedCategory?.id === c.id"
                [class.btn-outline-secondary]="selectedCategory?.id !== c.id"
                (click)="selectCategory(c)"
              >
                {{ c.name }}
              </button>
            </div>
            <div class="menu-grid">
              <button type="button" class="menu-item" *ngFor="let item of items" (click)="addLine(item)">
                <div>{{ item.product?.name }}</div>
                <div class="text-muted">{{ item.effectivePrice | number: '1.2-2' }}</div>
              </button>
              <p class="text-muted" *ngIf="items.length === 0">No items in this category yet.</p>
            </div>
          </div>

          <div class="card" style="padding:1rem; flex:1;">
            <h4 style="font-size:1rem;" class="mb-2">Order</h4>
            <table class="table-sm mb-2">
              <thead>
                <tr>
                  <th>Item</th>
                  <th class="text-end">Qty</th>
                  <th class="text-end">Total</th>
                  <th>Kitchen</th>
                  <th *ngIf="order.status === 'OPEN'"></th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let line of order.lines">
                  <td>{{ line.description }}<span class="text-muted" *ngIf="line.notes"> — {{ line.notes }}</span></td>
                  <td class="text-end">
                    <ng-container *ngIf="order.status === 'OPEN'">
                      <button class="btn btn-sm btn-outline-secondary" (click)="changeQty(line, -1)">−</button>
                      {{ line.quantity }}
                      <button class="btn btn-sm btn-outline-secondary" (click)="changeQty(line, 1)">+</button>
                    </ng-container>
                    <span *ngIf="order.status !== 'OPEN'">{{ line.quantity }}</span>
                  </td>
                  <td class="text-end">{{ line.lineTotal | number: '1.2-2' }}</td>
                  <td><span class="text-muted">{{ line.kitchenStatus }}</span></td>
                  <td *ngIf="order.status === 'OPEN'">
                    <button class="btn btn-sm btn-outline-danger" (click)="removeLine(line)">✕</button>
                  </td>
                </tr>
                <tr *ngIf="order.lines.length === 0">
                  <td colspan="5" class="text-muted">No items yet — add from the menu.</td>
                </tr>
              </tbody>
            </table>

            <div class="totals mb-3">
              <div class="flex justify-between"><span>Subtotal</span><span>{{ order.subtotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between"><span>Tax</span><span>{{ order.taxTotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between" style="font-weight:600;"><span>Total</span><span>{{ order.total | number: '1.2-2' }}</span></div>
              <div class="flex justify-between" *ngIf="order.paidTotal > 0"><span>Paid</span><span>{{ order.paidTotal | number: '1.2-2' }}</span></div>
              <div class="flex justify-between" *ngIf="order.paidTotal > 0" style="font-weight:600;"><span>Balance due</span><span>{{ order.balanceDue | number: '1.2-2' }}</span></div>
            </div>

            <div class="flex gap-2 mb-3" *ngIf="order.status === 'OPEN'">
              <button class="btn btn-outline-primary" [disabled]="order.lines.length === 0" (click)="sendToKitchen()">Send to kitchen</button>
              <button class="btn btn-primary" [disabled]="order.lines.length === 0" (click)="toggleCheckout()">Checkout</button>
              <button class="btn btn-outline-secondary" (click)="onToggleMerge()">Merge into…</button>
              <button class="btn btn-outline-danger" (click)="cancelOrder()">Cancel order</button>
            </div>

            <div class="card" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));" *ngIf="showCheckout">
              <h4 style="font-size:1rem;" class="mb-2">Payments</h4>

              <div class="flex gap-2 mb-2">
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'custom'" [class.btn-outline-secondary]="splitMode !== 'custom'" (click)="setSplitMode('custom')">
                  Custom amount
                </button>
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'equal'" [class.btn-outline-secondary]="splitMode !== 'equal'" (click)="setSplitMode('equal')">
                  Split equally
                </button>
                <button class="btn btn-sm" [class.btn-primary]="splitMode === 'byItem'" [class.btn-outline-secondary]="splitMode !== 'byItem'" (click)="setSplitMode('byItem')">
                  Split by item
                </button>
              </div>

              <div class="flex gap-2 align-center mb-2" *ngIf="splitMode === 'equal'">
                <label class="form-label" style="margin:0;">Split into</label>
                <input class="form-control" type="number" min="2" style="width:80px;" [(ngModel)]="splitCount" name="splitCount" />
                <span>guests' shares</span>
                <button class="btn btn-sm btn-outline-primary" (click)="applyEqualSplit()">Apply</button>
              </div>

              <div class="mb-2" *ngIf="splitMode === 'byItem'">
                <div class="flex gap-2 align-center mb-2">
                  <label class="form-label" style="margin:0;">Number of guests' shares</label>
                  <input class="form-control" type="number" min="2" style="width:80px;" [(ngModel)]="splitCount" name="splitCountItem" />
                </div>
                <div class="flex justify-between align-center mb-1" *ngFor="let line of order.lines">
                  <span>{{ line.quantity }}× {{ line.description }} — {{ line.lineGrandTotal | number: '1.2-2' }}</span>
                  <select
                    class="form-select"
                    style="width:140px;"
                    [ngModel]="lineShares[line.id] ?? null"
                    (ngModelChange)="setLineShare(line.id, $event)"
                    [name]="'share' + line.id"
                  >
                    <option [ngValue]="null">Unassigned</option>
                    <option *ngFor="let n of shareRange()" [ngValue]="n">Share {{ n }}</option>
                  </select>
                </div>
                <button class="btn btn-sm btn-outline-primary mt-2" (click)="applyItemSplit()">Apply</button>
              </div>

              <div class="flex gap-2 align-center mb-2" *ngFor="let row of paymentRows; let i = index">
                <span class="text-muted" style="width:70px;" *ngIf="splitMode !== 'custom'">Share {{ i + 1 }}</span>
                <select class="form-select" style="flex:1;" [(ngModel)]="row.method" [name]="'method' + i">
                  <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
                </select>
                <input class="form-control" type="number" style="width:120px;" min="0" step="0.01" [(ngModel)]="row.amount" [name]="'amount' + i" />
                <button class="btn btn-sm btn-outline-danger" (click)="removePaymentRow(i)">✕</button>
              </div>
              <div class="flex gap-2 mb-2">
                <button class="btn btn-sm btn-outline-secondary" (click)="addPaymentRow()">+ Add payment</button>
                <span class="text-muted" style="align-self:center;">
                  Entered: {{ paymentRowsTotal() | number: '1.2-2' }} / due {{ order.total | number: '1.2-2' }}
                </span>
              </div>
              <button class="btn btn-primary" [disabled]="checkingOut" (click)="doCheckout()">{{ checkingOut ? 'Processing…' : 'Confirm checkout' }}</button>
            </div>

            <div class="card" style="padding:1rem; background:var(--hover, rgba(0,0,0,0.03));" *ngIf="showMerge">
              <h4 style="font-size:1rem;" class="mb-2">Merge this order into another open table</h4>
              <div class="flex gap-2">
                <select class="form-select" [(ngModel)]="mergeTargetId" name="mergeTarget">
                  <option [ngValue]="null">Select a table…</option>
                  <option *ngFor="let o of otherOpenOrders" [ngValue]="o.id">{{ o.displayLabel }} ({{ o.total | number: '1.2-2' }})</option>
                </select>
                <button class="btn btn-primary" [disabled]="!mergeTargetId" (click)="doMerge()">Merge</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .menu-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
        gap: 8px;
      }
      .menu-item {
        border: 1px solid var(--border, #ddd);
        border-radius: 6px;
        padding: 0.6rem;
        text-align: center;
        background: var(--surface, #fff);
        cursor: pointer;
      }
      .menu-item:hover {
        background: var(--hover, rgba(0, 0, 0, 0.05));
      }
      .totals {
        border-top: 1px solid var(--border, #ddd);
        padding-top: 0.5rem;
      }
    `,
  ],
})
export class TableOrderComponent implements OnInit {
  orderId!: number;
  order: RestaurantOrder | null = null;
  error: string | null = null;

  categories: MenuCategory[] = [];
  selectedCategory: MenuCategory | null = null;
  items: MenuItem[] = [];

  showCheckout = false;
  checkingOut = false;
  paymentMethods: PaymentMethodOption[] = [];
  paymentRows: PaymentRow[] = [];

  /**
   * Fourier: "Include split & merge bills." "Custom" is the original free-form payment rows;
   * "equal"/"byItem" are calculators that populate those same rows for you (see applyEqualSplit/
   * applyItemSplit) — checkout itself is unchanged either way, since the backend just wants a
   * list of {method, amount} payments that add up to the total (see
   * RestaurantOrderApiService.checkout).
   */
  splitMode: 'custom' | 'equal' | 'byItem' = 'custom';
  splitCount = 2;
  /** Line id -> 1-based share number, for "split by item." Unassigned lines are left out of the map. */
  lineShares: Record<number, number | null> = {};

  showMerge = false;
  otherOpenOrders: RestaurantOrder[] = [];
  mergeTargetId: number | null = null;

  constructor(
    private orderApi: RestaurantOrderApiService,
    private menuApi: MenuApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.orderId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadOrder();
    this.menuApi.listCategories().subscribe({
      next: (c) => {
        this.categories = c;
        if (c.length > 0) this.selectCategory(c[0]);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load menu categories'),
    });
    this.paymentMethodApi.listActive().subscribe({
      next: (methods) => {
        this.paymentMethods = methods;
      },
      error: () => {},
    });
  }

  loadOrder(): void {
    this.orderApi.getById(this.orderId).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load order'),
    });
  }

  selectCategory(c: MenuCategory): void {
    this.selectedCategory = c;
    this.menuApi.listItems(c.id).subscribe({
      next: (i) => (this.items = i),
      error: () => (this.items = []),
    });
  }

  addLine(item: MenuItem): void {
    this.error = null;
    this.orderApi.addLine(this.orderId, { menuItemId: item.id, quantity: 1 }).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to add item'),
    });
  }

  changeQty(line: RestaurantOrder['lines'][number], delta: number): void {
    const newQty = line.quantity + delta;
    this.error = null;
    if (newQty <= 0) {
      this.removeLine(line);
      return;
    }
    this.orderApi.updateLine(this.orderId, line.id, { quantity: newQty }).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to update line'),
    });
  }

  removeLine(line: RestaurantOrder['lines'][number]): void {
    this.error = null;
    this.orderApi.removeLine(this.orderId, line.id).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to remove line'),
    });
  }

  sendToKitchen(): void {
    this.error = null;
    this.orderApi.sendToKitchen(this.orderId).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to send to kitchen'),
    });
  }

  toggleCheckout(): void {
    this.showCheckout = !this.showCheckout;
    if (this.showCheckout) {
      this.splitMode = 'custom';
      this.lineShares = {};
      if (this.paymentRows.length === 0) {
        this.addPaymentRow();
      }
    }
  }

  addPaymentRow(): void {
    const due = this.order ? this.order.total - this.paymentRowsTotal() : 0;
    this.paymentRows.push({ method: this.paymentMethods[0]?.code ?? '', amount: Math.max(due, 0) });
  }

  removePaymentRow(i: number): void {
    this.paymentRows.splice(i, 1);
  }

  paymentRowsTotal(): number {
    return this.paymentRows.reduce((sum, r) => sum + (Number(r.amount) || 0), 0);
  }

  setSplitMode(mode: 'custom' | 'equal' | 'byItem'): void {
    this.splitMode = mode;
    this.error = null;
  }

  shareRange(): number[] {
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    return Array.from({ length: n }, (_, i) => i + 1);
  }

  setLineShare(lineId: number, share: number | null): void {
    this.lineShares[lineId] = share;
  }

  /** Divides the total evenly across `splitCount` shares — the last share absorbs the rounding remainder so the rows always sum to exactly the total. */
  applyEqualSplit(): void {
    if (!this.order) return;
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    const total = this.order.total;
    const base = Math.floor((total / n) * 100) / 100;
    const rows: PaymentRow[] = [];
    let allocated = 0;
    for (let i = 0; i < n; i++) {
      const amount = i === n - 1 ? Math.round((total - allocated) * 100) / 100 : base;
      rows.push({ method: this.paymentMethods[0]?.code ?? '', amount });
      allocated += amount;
    }
    this.paymentRows = rows;
    this.error = null;
  }

  /** Sums each share's assigned lines' lineGrandTotal (see the model's own javadoc — it's already the exact fair-share amount, tax included, whichever way that line's tax is set up). Refuses until every line has a share so nobody's items get left out of the bill. */
  applyItemSplit(): void {
    if (!this.order) return;
    const n = Math.max(2, Math.floor(this.splitCount) || 2);
    const shareAmounts: number[] = new Array(n).fill(0);
    let unassigned = 0;
    for (const line of this.order.lines) {
      const share = this.lineShares[line.id];
      if (!share || share < 1 || share > n) {
        unassigned++;
        continue;
      }
      shareAmounts[share - 1] += line.lineGrandTotal;
    }
    if (unassigned > 0) {
      this.error = `${unassigned} item${unassigned === 1 ? '' : 's'} still need a share assigned before applying.`;
      return;
    }
    this.paymentRows = shareAmounts.map((amount) => ({
      method: this.paymentMethods[0]?.code ?? '',
      amount: Math.round(amount * 100) / 100,
    }));
    this.error = null;
  }

  doCheckout(): void {
    if (!this.order) return;
    if (this.paymentRows.length === 0) this.addPaymentRow();
    this.error = null;
    this.checkingOut = true;
    this.orderApi.checkout(this.orderId, { payments: this.paymentRows }).subscribe({
      next: (o) => {
        this.order = o;
        this.checkingOut = false;
        this.showCheckout = false;
        this.router.navigate(['/admin/restaurant/tables/floor-plan']);
      },
      error: (err) => {
        this.checkingOut = false;
        this.error = err?.error?.message ?? 'Failed to check out';
      },
    });
  }

  cancelOrder(): void {
    if (!confirm('Cancel this order? This frees the table without recording a sale.')) return;
    this.error = null;
    this.orderApi.cancel(this.orderId).subscribe({
      next: () => this.router.navigate(['/admin/restaurant/tables/floor-plan']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel order'),
    });
  }

  onToggleMerge(): void {
    this.showMerge = !this.showMerge;
    if (this.showMerge) {
      this.orderApi.listOpen().subscribe({
        next: (orders) => (this.otherOpenOrders = orders.filter((o) => o.id !== this.orderId)),
        error: () => (this.otherOpenOrders = []),
      });
    }
  }

  doMerge(): void {
    if (!this.mergeTargetId) return;
    this.error = null;
    this.orderApi.merge(this.orderId, this.mergeTargetId).subscribe({
      next: () => this.router.navigate(['/admin/restaurant/orders', this.mergeTargetId]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to merge orders'),
    });
  }
}
