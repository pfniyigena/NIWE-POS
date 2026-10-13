import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OrderApiService } from '../core/api/order-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { CustomerApiService } from '../core/api/customer-api.service';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { AuthService } from '../core/auth.service';
import { TerminalSessionService } from './terminal-session.service';
import { ConnectivityService } from '../core/offline/connectivity.service';
import { OfflineSyncService } from '../core/offline/offline-sync.service';
import { OfflineDbService } from '../core/offline/offline-db.service';
import { QueuedOfflineLine, QueuedOfflinePayment, QueuedOfflineSale } from '../core/offline/offline-models';
import * as OfflineCalc from '../core/offline/offline-calculator';
import { CashierSession, Category, CreditSummary, Customer, Order, OrderLine, PaymentMethodOption, Product, RegisterSession } from '../core/models';

/** One offline payment-method row — same shape as checkout.component.ts's PaymentRow, for the inline offline payment step. */
interface OfflinePaymentRow {
  method: string;
  name: string;
  selected: boolean;
  amount: number | null;
  credit: boolean;
}

/**
 * The full-screen terminal: order tabs across the top (replacing the shared topbar — there's no
 * room for both once the cart panel and product grid are on screen), category tiles + product
 * grid + barcode/search on the left, the current order's cart on the right.
 *
 * More than one cashier can work this same shift at once (see CashierSession). Opening the
 * terminal at all now requires a PIN (Task: "To open terminal for selling should ask PIN and open
 * related session based on the PIN entered. If PIN is not valid the terminal shouldn't open") —
 * there's no more silent "my own session" auto-resolve by JWT identity; whichever staff member's
 * PIN is entered is who the resulting cashier session (and every sale rung under it) belongs to.
 * A "Switch cashier" button still lets someone else step up mid-session and re-identify with
 * their own PIN, without leaving/reloading the terminal.
 *
 * The customer picker has an inline search box and an "Add" panel (Tasks: "Add Customer button
 * from Terminal screen And the added customer should be selected" / "Add Search Input Customer
 * from Terminal screen") so a cashier can find or create a customer without leaving the terminal
 * and losing the cart they're building. The "Add" panel's fields mirror Admin > Customers' own
 * form, including Tax ID (Task: "Add Tax ID on Customer input inside Terminal screen") — same
 * optional, purely-informational field, not required to save.
 *
 * A **📺 Customer display** button (Task: "Add customer-facing double screen") opens a second,
 * read-only screen showing the current tab's cart/total and, once checkout finishes, a thank-you
 * screen — on a second monitor via the browser's own window placement, or on entirely separate
 * hardware pointed at the same URL. Every tab switch here calls `pushActiveOrder` to tell the
 * backend which order that display should be polling; see CustomerDisplayComponent for the rest.
 *
 * Once a PIN has resolved a cashier session for this register session, that session is cached in
 * `TerminalSessionService` (an in-memory, `providedIn: 'root'` singleton) so that navigating away
 * to Checkout or the receipt page and back — an in-app route change, which destroys and recreates
 * this component — doesn't show the PIN screen again (Tasks: "The back button on checkout should
 * not request PIN" / "The back button on print receipt should take back to terminal and not
 * request PIN"). A real browser reload still re-prompts, because that wipes the singleton too —
 * that PIN-on-open behavior was a deliberate Round 1 decision and stays intact.
 *
 * <p><strong>Offline mode</strong> (Task: "local queuing of sales that sync once connectivity
 * returns" — see `ConnectivityService`/`OfflineSyncService`/`OfflineDbService`): once a cashier
 * session is already open here and the backend becomes unreachable, the right-hand cart panel
 * swaps from the normal server-backed cart (`currentOrder`) to a local-only offline cart
 * (`offlineCart`) built against the last-synced product/customer/payment-method cache in
 * IndexedDB — the product grid on the left keeps working the same way either way, just reading
 * from the cache instead of the server underneath (see `reloadProducts`). Deliberately scoped:
 * whatever tab was already open on the server when connectivity dropped is left frozen (nothing
 * short of reconnecting can safely edit it further) while a *new* sale can start immediately in
 * the offline cart; a fresh PIN-gated open still requires connectivity (PIN validation is a
 * server call this deliberately never tries to replicate offline). A finished offline sale is
 * queued locally with its own estimated total (from a simplified client-side price/tax port, see
 * `offline-calculator.ts`) and replayed for real — the server remains the sole authority on the
 * final total — the moment the terminal is back online.
 */
@Component({
  selector: 'app-terminal',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePickerComponent],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; padding:2rem;" *ngIf="!cashierSession">
      <div class="card" style="width:360px; padding:1.5rem;">
        <h3 class="mb-1">Enter PIN</h3>
        <p class="text-muted mb-3">{{ session?.registerName ?? 'Terminal' }} — enter your PIN to open the terminal.</p>
        <div class="alert alert-danger" *ngIf="pinError">{{ pinError }}</div>
        <input
          class="form-control mb-2"
          type="password"
          inputmode="numeric"
          placeholder="PIN"
          [(ngModel)]="pinInput"
          (keyup.enter)="openWithPin()"
          [disabled]="openingWithPin"
          autofocus
        />
        <button class="btn btn-primary btn-block mb-2" (click)="openWithPin()" [disabled]="openingWithPin || !pinInput">
          {{ openingWithPin ? 'Checking…' : 'Open terminal' }}
        </button>
        <a class="btn btn-link btn-block" [routerLink]="[exitLink]">Cancel</a>
        <!-- Issue 2: "The Cashier is not able to sale in terminal (reason no selling cart
             available for him)" — traced to a cashier who was never given a PIN (Admin > Staff
             shows this as a "Not set" badge) getting stuck on this screen with no way forward: the
             terminal can't open, so no cashier session and no cart ever get created for them. A
             cashier can already self-serve a PIN from My Profile (Task: "Add change PIN inside my
             profile") — this screen just never pointed them there. -->
        <p class="text-muted text-center mt-2" style="font-size:0.8rem;">
          Don't have a PIN yet? Ask an admin to set one for you (Admin &gt; Staff), or
          <a [routerLink]="['/profile']">set your own in My Profile</a>.
        </p>
      </div>
    </div>

    <ng-container *ngIf="cashierSession">
    <div class="pos-tabs">
      <button
        *ngFor="let tab of orderTabs"
        class="pos-tab"
        [class.active]="currentOrder?.id === tab.id"
        (click)="selectTab(tab)"
      >
        #{{ tab.id }}<span *ngIf="tab.note"> &middot; {{ tab.note }}</span>
      </button>
      <button
        class="pos-new-tab"
        title="New order"
        (click)="newOrder()"
        [disabled]="!cashierSession || offline"
      >+</button>
      <span class="grow"></span>
      <button
        class="btn btn-sm btn-outline-secondary"
        style="margin-right:8px;"
        (click)="toggleOfflineQueue()"
        [class.active]="showOfflineQueue"
        *ngIf="offlineQueue.length > 0"
        [title]="failedQueueCount > 0 ? failedQueueCount + ' offline sale(s) need review' : 'Offline sales waiting to sync'"
      >
        📥 Offline queue ({{ offlineQueue.length }})<span *ngIf="failedQueueCount > 0"> ⚠</span>
      </button>
      <!-- Issue 1: "Missing 'Switch cashier' in the terminal" — the button always did this (see
           switchCashier() below), but its visible label used to be just the current cashier's own
           name with a repeat icon, so the words "Switch cashier" only ever appeared in the title
           tooltip. Now the action itself is spelled out on the button, with the current cashier's
           name kept alongside it as before so it's still obvious whose cart is on screen. -->
      <button
        class="btn btn-sm btn-outline-secondary"
        style="margin-right:8px;"
        (click)="switchCashier()"
        [disabled]="offline"
        title="Let another staff member ring sales on this shift under their own PIN — requires connectivity"
      >
        🔁 Switch cashier ({{ cashierSession.cashierName }})
      </button>
      <button
        class="btn btn-sm btn-outline-secondary"
        style="margin-right:8px;"
        (click)="endMySession()"
        [disabled]="!cashierSession || offline"
        title="Done working this shift — end your own session (any unfinished carts are discarded) — requires connectivity"
      >
        🚪 End my session
      </button>
      <button
        class="btn btn-sm btn-outline-secondary"
        style="margin-right:8px;"
        (click)="toggleReceipts()"
        [class.active]="showReceipts"
        [disabled]="offline"
        title="Requires connectivity"
      >
        🧾 Receipts
      </button>
      <button
        class="btn btn-sm btn-outline-secondary"
        style="margin-right:8px;"
        (click)="openCustomerDisplay()"
        title="Open a customer-facing screen — put it on a second monitor, or open the same link on a separate device"
      >
        📺 Customer display
      </button>
      <span class="text-muted" style="font-size:0.9rem; display:flex; align-items:center; gap:8px; padding-right:8px;">
        {{ session?.registerName ?? '—' }}
        <a [routerLink]="[exitLink]" style="color:var(--text); margin-left:6px;" title="Exit terminal">↩️ Exit</a>
      </span>
    </div>

    <div class="alert alert-warning" style="margin:8px 12px 0; padding:6px 12px;" *ngIf="offline">
      📴 Offline — the product grid is working from the catalog as of the last sync, and any tab that
      was already open on the server is frozen until you're back online. Start a sale below and it'll
      queue locally, then sync automatically the moment connectivity returns.
    </div>

    <div class="app-shell" *ngIf="showOfflineQueue">
      <main class="app-main wide" style="padding:1rem;">
        <div class="flex justify-between align-center mb-3">
          <h3>Offline sale queue</h3>
          <button class="btn btn-outline-secondary" (click)="toggleOfflineQueue()">&larr; Back to terminal</button>
        </div>
        <table>
          <thead>
            <tr>
              <th>Rung at</th>
              <th>Customer</th>
              <th class="text-end">Est. total</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let s of offlineQueue">
              <td>{{ s.queuedAt | date: 'short' }}</td>
              <td>{{ s.customerName }}</td>
              <td class="text-end">{{ s.clientEstimatedTotal | number: '1.2-2' }}</td>
              <td>
                <span *ngIf="s.status === 'pending'">⏳ Waiting to sync</span>
                <span *ngIf="s.status === 'failed'" class="text-danger">⚠ Failed — {{ s.lastError }}</span>
              </td>
              <td><button class="btn btn-sm btn-outline-danger" (click)="discardQueuedSale(s)">Discard</button></td>
            </tr>
            <tr *ngIf="offlineQueue.length === 0">
              <td colspan="5" class="text-muted">Nothing queued.</td>
            </tr>
          </tbody>
        </table>
      </main>
    </div>

    <div class="app-shell" *ngIf="showReceipts && !showOfflineQueue">
      <main class="app-main wide" style="padding:1rem;">
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <div class="flex justify-between align-center mb-3">
          <h3>Receipts — this shift</h3>
          <button class="btn btn-outline-secondary" (click)="toggleReceipts()">&larr; Back to terminal</button>
        </div>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Invoice</th>
              <th>Customer</th>
              <th class="text-end">Total</th>
              <th>Payment</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let o of receipts">
              <td>
                {{ o.completedAt | date: 'HH:mm' }}
                <span *ngIf="o.offlineQueuedAt" class="text-muted" style="font-size:0.8rem;" [title]="'Rung offline at ' + (o.offlineQueuedAt | date: 'short')">📴</span>
                <span *ngIf="o.syncNote" class="text-danger" style="font-size:0.8rem;" [title]="o.syncNote">⚠</span>
              </td>
              <td>{{ o.invoiceNumber }}</td>
              <td>{{ o.customerName }}</td>
              <td class="text-end">{{ o.total | number: '1.2-2' }}</td>
              <td>
                <span *ngFor="let p of o.payments; let last = last">{{ p.method }}{{ !last ? ' + ' : '' }}</span>
              </td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/receipt', o.id]">View / Reprint</a></td>
            </tr>
            <tr *ngIf="receipts.length === 0">
              <td colspan="6" class="text-muted">No completed sales on this shift yet.</td>
            </tr>
          </tbody>
        </table>
      </main>
    </div>

    <div class="app-shell" *ngIf="!showReceipts && !showOfflineQueue">
      <main class="app-main wide" style="padding:1rem;">
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <!-- Fourier: "POS Cart should also be mobile responsive" — product grid + cart panel sit
             side by side on desktop (the cart's own fixed width lives in .pos-cart-panel, not
             inline, specifically so the mobile media query in styles.scss can override it); below
             the mobile breakpoint this row stacks into a column instead (product grid scrollable
             on top, cart below), same breakpoint as the admin shell's own responsive pass. -->
        <div class="flex gap-3 pos-terminal-layout" style="align-items:flex-start;">
          <div class="grow" style="min-width:0;">
            <div class="flex gap-2 mb-2">
              <input
                class="form-control"
                placeholder="Scan or type barcode, then press Enter"
                [(ngModel)]="barcodeText"
                (keyup.enter)="addByBarcode()"
                #barcodeField
              />
              <input
                class="form-control"
                placeholder="Search products by name or SKU"
                [(ngModel)]="searchTerm"
                (input)="reloadProducts()"
              />
            </div>

            <div class="flex gap-2 mb-3" style="overflow-x:auto;">
              <button class="category-tile all" [class.selected]="selectedCategoryId === null" (click)="selectCategory(null)">
                All products
              </button>
              <button
                *ngFor="let c of categories"
                class="category-tile"
                [class.selected]="selectedCategoryId === c.id"
                [style.background]="c.colorHex"
                (click)="selectCategory(c.id)"
              >
                {{ c.name }}
              </button>
            </div>

            <div class="flex gap-2" style="flex-wrap:wrap; max-height:55vh; overflow-y:auto;">
              <button class="product-tile" style="width:140px;" *ngFor="let p of products" (click)="addProduct(p)">
                <img *ngIf="p.iconDataUrl" [src]="p.iconDataUrl" alt="" class="product-tile-icon" />
                <div>{{ p.name }}</div>
                <div class="price">{{ p.unitPrice | number: '1.2-2' }}</div>
                <!-- Task: "Show stock quantity on Product terminal" — a cashier ringing up items
                     otherwise has no visibility into stock at all from this screen (it's not shown
                     anywhere else in the sale flow). Red/"Out of stock" at <= 0 rather than hiding
                     or disabling the tile — this store may have "Allow negative stock" on (see
                     StoreSettings.allowNegativeStock), so a cashier can still legitimately sell
                     past zero; this is a heads-up, not a block. Fourier: "Add track stock attribute
                     on Product" — a non-tracked product is always sellable regardless of any
                     number here (see Product.trackStock's javadoc), so this caption is simply
                     omitted for one rather than showing a stock count that's never actually checked. -->
                <div class="text-muted product-tile-stock" *ngIf="p.trackStock" [class.text-danger]="p.stockQuantity <= 0">
                  {{ p.stockQuantity > 0 ? 'Stock: ' + p.stockQuantity : 'Out of stock' }}
                </div>
              </button>
              <div class="text-muted" *ngIf="products.length === 0">No products match.</div>
              <!--
                Task: "check the loading of 100000 products" — the backend caps this list at 200
                tiles regardless of category/search (see CatalogService.TERMINAL_SEARCH_CAP), so a
                huge catalog can't hand the terminal tens of thousands of buttons to render. This
                hint only shows when that cap is actually the reason the list stopped — i.e. there
                are exactly 200 results — so it stays silent for any store small enough to never hit it.
              -->
              <div class="text-muted" style="width:100%; font-size:0.8rem;" *ngIf="products.length === 200">
                Showing the first 200 matches — type to search for more.
              </div>
            </div>
          </div>

          <div class="card pos-cart-panel">
            <div class="card-body" *ngIf="!offline && currentOrder as order">
              <div style="padding:8px;">
                <label class="form-label">Customer</label>
                <div class="flex gap-1 mb-1">
                  <input
                    class="form-control form-control-sm"
                    placeholder="Search customer by name or phone"
                    [(ngModel)]="customerSearch"
                    (input)="filterCustomers()"
                  />
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-secondary"
                    (click)="toggleAddCustomer()"
                    title="Add a new customer"
                  >
                    👤+ Add
                  </button>
                </div>
                <select class="form-select" [(ngModel)]="customerId" (change)="setCustomer()">
                  <!-- Fourier: "Instead of No customer (in select box) replaced by Walk-in Customer
                       Customer from DB" — no more null/"No customer" placeholder option; the real
                       seeded "Walk-in Customer" row already appears below via filteredCustomers and
                       is pre-selected by default (see applyDefaultCustomer), so it's what shows here
                       instead. -->
                  <option *ngFor="let c of filteredCustomers" [ngValue]="c.id">{{ c.name }}</option>
                </select>

                <!-- Task: "Customer balance on POS screen" -->
                <div class="text-muted" style="font-size:0.8rem; margin-top:4px;" *ngIf="customerId && creditSummary">
                  <span *ngIf="creditSummary.creditEnabled">
                    Owes {{ creditSummary.outstandingBalance | number: '1.2-2' }} · available credit {{ creditSummary.availableCredit | number: '1.2-2' }}
                  </span>
                  <span *ngIf="!creditSummary.creditEnabled">Not enabled for credit</span>
                </div>

                <div class="card" style="margin-top:6px; padding:8px;" *ngIf="showAddCustomer">
                  <div class="alert alert-danger" style="padding:4px 8px; font-size:0.85rem;" *ngIf="addCustomerError">
                    {{ addCustomerError }}
                  </div>
                  <input class="form-control form-control-sm mb-1" placeholder="Name" [(ngModel)]="newCustomerName" />
                  <input class="form-control form-control-sm mb-1" placeholder="Phone" [(ngModel)]="newCustomerPhone" />
                  <input class="form-control form-control-sm mb-1" placeholder="Email" [(ngModel)]="newCustomerEmail" />
                  <input class="form-control form-control-sm mb-2" placeholder="Tax ID" [(ngModel)]="newCustomerTaxId" />
                  <div class="flex gap-2">
                    <button
                      type="button"
                      class="btn btn-sm btn-primary"
                      (click)="saveNewCustomer()"
                      [disabled]="!newCustomerName.trim() || savingCustomer"
                    >
                      {{ savingCustomer ? 'Saving…' : 'Save' }}
                    </button>
                    <button type="button" class="btn btn-sm btn-link" (click)="toggleAddCustomer()">Cancel</button>
                  </div>
                </div>
              </div>

              <div class="flex justify-between align-center pos-summary-bar" style="padding:8px;">
                <strong>{{ order.totalQuantity }} items</strong>
                <strong>Total: {{ order.total | number: '1.2-2' }}</strong>
              </div>

              <div class="flex cart-col-header" style="padding:6px 8px;">
                <div style="width:45%">Item</div>
                <div style="width:20%">Qty</div>
                <div style="width:20%">Dis.</div>
                <div style="width:15%" class="text-end">Price</div>
              </div>

              <table class="table-sm" style="margin-bottom:0;">
                <tbody>
                  <tr *ngFor="let l of order.lines">
                    <td>
                      <div>{{ l.description }}</div>
                      <div class="flex gap-1 mt-1">
                        <input
                          type="number"
                          class="form-control form-control-sm"
                          style="width:55px; padding:0.2rem 0.4rem;"
                          [ngModel]="l.quantity"
                          (change)="updateQty(l, $event)"
                        />
                        <!-- Task 2: "Only Admin will be allowed to gives discount" + Fourier's
                             "Build a full permissions system" — disabled (not hidden) for anyone
                             without canDiscount, so an applied discount is still visible on the
                             line, just not editable. Real enforcement is server-side
                             (OrderService.validateDiscount); this only prevents someone from
                             bothering to try. -->
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          [max]="effectiveMaxDiscountPercent"
                          class="form-control form-control-sm"
                          style="width:65px; padding:0.2rem 0.4rem;"
                          placeholder="disc %"
                          [ngModel]="l.discountPercent"
                          [disabled]="!canDiscount"
                          [title]="canDiscount ? '' : 'Only an admin (or someone granted the discount-override permission) can apply a discount'"
                          (change)="updateDiscount(l, $event)"
                        />
                        <button class="btn btn-sm btn-outline-danger" (click)="removeLine(l)" title="Remove">🗑</button>
                      </div>
                      <!-- Serial-numbered products: type or scan the serial of each unit sold (comma-separated).
                           Left empty, the oldest units in stock are used. -->
                      <input
                        *ngIf="l.trackingMode === 'SERIAL'"
                        type="text"
                        class="form-control form-control-sm mt-1"
                        style="padding:0.2rem 0.4rem;"
                        placeholder="Serial no(s). — {{ l.quantity }} unit(s), comma-separated"
                        [ngModel]="serialsText(l)"
                        (change)="updateSerials(l, $event)"
                      />
                    </td>
                    <td class="text-end">{{ l.lineTotal | number: '1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="order.lines.length === 0">
                    <td colspan="2" class="text-muted" style="padding:1rem;">Cart is empty — tap a product or scan a barcode.</td>
                  </tr>
                </tbody>
              </table>

              <table class="table-sm" style="margin-bottom:0;">
                <tbody>
                  <tr>
                    <td>Subtotal</td>
                    <td class="text-end">{{ order.subtotal | number: '1.2-2' }}</td>
                  </tr>
                  <tr>
                    <td>Tax</td>
                    <td class="text-end">{{ order.taxTotal | number: '1.2-2' }}</td>
                  </tr>
                </tbody>
              </table>

              <div class="flex gap-2" style="padding:8px;">
                <button class="btn btn-outline-secondary" (click)="hold()" [disabled]="order.lines.length === 0" title="Hold this sale and start a new one">⏸ Hold</button>
                <button class="btn btn-outline-danger" (click)="discard()" title="Discard this order — cannot be undone">🗑 Discard</button>
                <a
                  class="btn pos-payment-btn grow"
                  [class.disabled]="order.lines.length === 0"
                  [routerLink]="order.lines.length ? ['/checkout', order.id] : null"
                >
                  Payment
                </a>
              </div>
            </div>

            <!-- Offline cart — see the class doc comment's "Offline mode" section. Same layout/styling as the online cart above, bound to a local array instead of a server Order. -->
            <div class="card-body" *ngIf="offline">
              <div style="padding:8px;">
                <label class="form-label">Customer (offline — from last sync)</label>
                <select class="form-select" [(ngModel)]="offlineCustomerId">
                  <!-- Fourier: "Instead of No customer (in select box) replaced by Walk-in Customer
                       Customer from DB" — no more null/"No customer" placeholder option; the real
                       seeded "Walk-in Customer" row already appears below via offlineCustomers and
                       is pre-selected by default (see loadOfflineCatalog), so it's what shows here
                       instead. -->
                  <option *ngFor="let c of offlineCustomers" [ngValue]="c.id">{{ c.name }}</option>
                </select>
                <div class="text-muted" style="font-size:0.8rem; margin-top:4px;" *ngIf="offlineCatalog.length === 0">
                  No cached catalog yet — connect once while online so this device has products/customers to sell offline.
                </div>
              </div>

              <div class="flex justify-between align-center pos-summary-bar" style="padding:8px;">
                <strong>{{ offlineItemCount }} items</strong>
                <strong>Total (est.): {{ offlineTotal | number: '1.2-2' }}</strong>
              </div>

              <div class="flex cart-col-header" style="padding:6px 8px;" *ngIf="!offlineCheckoutOpen">
                <div style="width:45%">Item</div>
                <div style="width:20%">Qty</div>
                <div style="width:20%">Dis.</div>
                <div style="width:15%" class="text-end">Price</div>
              </div>

              <table class="table-sm" style="margin-bottom:0;" *ngIf="!offlineCheckoutOpen">
                <tbody>
                  <tr *ngFor="let l of offlineCart">
                    <td>
                      <div>{{ l.description }}</div>
                      <div class="flex gap-1 mt-1">
                        <input
                          type="number"
                          class="form-control form-control-sm"
                          style="width:55px; padding:0.2rem 0.4rem;"
                          [ngModel]="l.quantity"
                          (change)="updateOfflineQty(l, $event)"
                        />
                        <!-- Task 2 + Fourier's "Build a full permissions system": same gate as the online cart's discount input above. -->
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          [max]="effectiveMaxDiscountPercent"
                          class="form-control form-control-sm"
                          style="width:65px; padding:0.2rem 0.4rem;"
                          placeholder="disc %"
                          [ngModel]="l.discountPercent"
                          [disabled]="!canDiscount"
                          [title]="canDiscount ? '' : 'Only an admin (or someone granted the discount-override permission) can apply a discount'"
                          (change)="updateOfflineDiscount(l, $event)"
                        />
                        <button class="btn btn-sm btn-outline-danger" (click)="removeOfflineLine(l)" title="Remove">🗑</button>
                      </div>
                    </td>
                    <td class="text-end">{{ offlineLineTotal(l) | number: '1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="offlineCart.length === 0">
                    <td colspan="2" class="text-muted" style="padding:1rem;">Offline cart is empty — tap a cached product or scan a barcode.</td>
                  </tr>
                </tbody>
              </table>

              <table class="table-sm" style="margin-bottom:0;" *ngIf="!offlineCheckoutOpen">
                <tbody>
                  <tr>
                    <td>Subtotal (est.)</td>
                    <td class="text-end">{{ offlineSubtotal | number: '1.2-2' }}</td>
                  </tr>
                  <tr>
                    <td>Tax (est.)</td>
                    <td class="text-end">{{ offlineTaxTotal | number: '1.2-2' }}</td>
                  </tr>
                </tbody>
              </table>

              <div class="alert alert-danger" style="margin:8px; padding:4px 8px; font-size:0.85rem;" *ngIf="offlineError">
                {{ offlineError }}
              </div>

              <div class="flex gap-2" style="padding:8px;" *ngIf="!offlineCheckoutOpen">
                <button class="btn btn-outline-danger" (click)="discardOfflineCart()" [disabled]="offlineCart.length === 0" title="Discard this offline sale — cannot be undone">🗑 Discard</button>
                <button
                  class="btn pos-payment-btn grow"
                  [class.disabled]="offlineCart.length === 0"
                  (click)="openOfflineCheckout()"
                >
                  Payment
                </button>
              </div>

              <!-- Inline payment step — same shape as checkout.component.ts, but queues locally instead of calling the server. -->
              <div style="padding:8px;" *ngIf="offlineCheckoutOpen">
                <h6 class="mb-2">Payment — queued until you're back online</h6>
                <div class="flex gap-2 mb-2" style="align-items:center;" *ngFor="let row of offlinePaymentRows">
                  <button
                    type="button"
                    class="btn"
                    style="width:110px; flex-shrink:0;"
                    [class.btn-primary]="row.selected"
                    [class.btn-outline-secondary]="!row.selected"
                    (click)="toggleOfflinePayment(row)"
                  >
                    {{ row.name }}
                  </button>
                  <input
                    class="form-control"
                    type="number"
                    step="0.01"
                    min="0"
                    [max]="maxOfflineAmountFor(row)"
                    placeholder="Amount"
                    [ngModel]="row.amount"
                    (ngModelChange)="onOfflineAmountChange(row, $event)"
                    [disabled]="!row.selected"
                    [hidden]="!row.selected"
                  />
                </div>
                <p class="text-muted" style="font-size:0.85rem;" *ngIf="offlineCreditRowSelected() && !offlineCustomerId">
                  Select a customer on this sale before paying with store credit.
                </p>
                <p class="text-muted" style="font-size:0.85rem;" *ngIf="offlineCreditRowSelected()">
                  Credit limit isn't checked offline — it's enforced for real once this syncs; a sale that turns out
                  to exceed the limit will land in the offline queue as "failed" for review, not silently accepted.
                </p>
                <div class="form-group mb-2" *ngIf="offlineCreditRowSelected()">
                  <label class="form-label">Due date (optional)</label>
                  <app-date-picker [(ngModel)]="offlineDueDate" ></app-date-picker>
                </div>
                <div class="flex gap-2">
                  <button class="btn btn-link" (click)="offlineCheckoutOpen = false">&larr; Back to cart</button>
                  <button class="btn btn-success grow" (click)="queueOfflineSale()">Queue sale</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
    </ng-container>
  `,
})
export class TerminalComponent implements OnInit {
  @ViewChild('barcodeField') barcodeField?: ElementRef<HTMLInputElement>;

  sessionId!: number;
  session: RegisterSession | null = null;

  /** Whichever cashier is currently "at" this terminal — see the class doc comment above. Also
   * doubles as the PIN-gate: the terminal's main view only renders once this is non-null. */
  cashierSession: CashierSession | null = null;

  /** PIN-gate state — see the class doc comment above and `openWithPin()`. */
  pinInput = '';
  pinError: string | null = null;
  openingWithPin = false;

  orderTabs: Order[] = [];
  currentOrder: Order | null = null;

  showReceipts = false;
  receipts: Order[] = [];

  categories: Category[] = [];
  products: Product[] = [];
  customers: Customer[] = [];
  /** Customers shown in the dropdown — `customers` filtered by `customerSearch` (Task: "Add
   * Search Input Customer from Terminal screen"). Equal to `customers` when the search box is empty. */
  filteredCustomers: Customer[] = [];
  customerSearch = '';

  /** Inline "add a customer without leaving the terminal" panel (Task: "Add Customer button from
   * Terminal screen And the added customer should be selected"). */
  showAddCustomer = false;
  newCustomerName = '';
  newCustomerPhone = '';
  newCustomerEmail = '';
  /** Tax ID (Task: "Add Tax ID on Customer input inside Terminal screen") — same optional,
   * purely-informational field as Admin > Customers' form; not required to save a new customer. */
  newCustomerTaxId = '';
  savingCustomer = false;
  addCustomerError: string | null = null;

  selectedCategoryId: number | null = null;
  searchTerm = '';
  barcodeText = '';
  customerId: number | null = null;

  /**
   * Task: "since we have a default customer 'Walk-in Customer' it is better to set this customer
   * as default selected in the terminal ... instead of having null" — the id of whichever
   * customer `customerApi.list()` reports as `walkIn: true` (see Customer.walkIn's backend
   * javadoc), or null if none is flagged (a store that predates this feature and never had a
   * customer literally named "Walk-in Customer" for DataSeeder's backfill to find — the terminal
   * then just falls back to its old behavior of leaving a new cart's customer unset). Applied by
   * `applyDefaultCustomer` wherever a cart's own customer is still unset.
   */
  walkInCustomerId: number | null = null;

  /** Task: "Customer balance on POS screen" — refreshed whenever the current order's customer changes. */
  creditSummary: CreditSummary | null = null;

  /**
   * Fourier's own two-part request: Task 1 "configure the max discount" (System settings) + Task
   * 2 "Only Admin will be allowed to gives discount." Loaded once in ngOnInit; matches
   * StoreSettings.maxDiscountPercent's own default (100 — effectively unrestricted) until that GET
   * resolves, so there's no flash of "0% allowed" before the real value loads. This is purely a
   * client-side convenience for clamping/labeling the discount input before a round trip — the
   * real enforcement (both the cap and the admin-only check) lives in
   * OrderService.validateDiscount and can't be bypassed by tampering with this value. Not cached
   * for offline use (unlike the product/customer/payment-method catalog) — an offline sale still
   * clamps to the plain 0-100 range client-side (see updateOfflineDiscount) and gets the real
   * cap/role check applied server-side once it syncs back online.
   */
  maxDiscountPercent = 100;

  error: string | null = null;

  // ---- offline mode (Task: "local queuing of sales that sync once connectivity returns") ----

  /** True once ConnectivityService reports the backend unreachable — see the class doc comment above. */
  offline = false;
  /** The whole cached catalog (not the 200-cap search results) — see OfflineDbService/OfflineSyncService. */
  offlineCatalog: Product[] = [];
  offlineCustomers: Customer[] = [];
  offlinePaymentMethods: PaymentMethodOption[] = [];
  offlineQueue: QueuedOfflineSale[] = [];
  showOfflineQueue = false;

  offlineCart: QueuedOfflineLine[] = [];
  offlineCustomerId: number | null = null;
  offlineCheckoutOpen = false;
  offlinePaymentRows: OfflinePaymentRow[] = [];
  offlineDueDate: string | null = null;
  offlineError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderApi: OrderApiService,
    private catalogApi: CatalogApiService,
    private customerApi: CustomerApiService,
    private customerCreditApi: CustomerCreditApiService,
    private registerApi: RegisterApiService,
    private cashierSessionApi: CashierSessionApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private auth: AuthService,
    private terminalSession: TerminalSessionService,
    private connectivity: ConnectivityService,
    private offlineSync: OfflineSyncService,
    private offlineDb: OfflineDbService
  ) {}

  /**
   * Where "exit" and "End my session" send someone back to. Registers & Terminal is ADMIN-only
   * now (see app.routes.ts), so a cashier leaving the terminal goes to Sessions instead — sending
   * them to `/registers` would just bounce them there via adminGuard anyway, one redirect later.
   */
  get exitLink(): string {
    return this.auth.isAdmin() || this.auth.canManage('SALES') ? '/registers' : '/sessions';
  }

  /** Task 2: "Only Admin will be allowed to gives discount" — gates both discount inputs in the template below. */
  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  /** Fourier: "Build a full permissions system" — an ADMIN, or anyone granted OVERRIDE_DISCOUNT_CAP, can touch the discount inputs. */
  get canDiscount(): boolean {
    return this.isAdmin || this.auth.hasPermission('OVERRIDE_DISCOUNT_CAP');
  }

  /**
   * Fourier: "Build a full permissions system" — client-side-only convenience mirroring
   * OrderService.validateDiscount's own real enforcement: a plain ADMIN (no explicit grant) is
   * still clamped to the configured maxDiscountPercent, same as today; an explicit
   * OVERRIDE_DISCOUNT_CAP holder (admin or not) isn't. Literal grant check, not isAdmin — see
   * AuthService.hasPermission's own javadoc for why ADMIN isn't auto-included here.
   */
  get effectiveMaxDiscountPercent(): number {
    return this.auth.hasPermission('OVERRIDE_DISCOUNT_CAP') ? 100 : this.maxDiscountPercent;
  }

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('sessionId'));
    this.registerApi.getSession(this.sessionId).subscribe((s) => (this.session = s));
    this.catalogApi.listCategories().subscribe((c) => (this.categories = c));
    this.customerApi.list().subscribe((c) => {
      this.customers = c;
      this.filterCustomers();
      this.walkInCustomerId = c.find((cust) => cust.walkIn)?.id ?? null;
      // This can resolve after loadTabs() already showed a brand-new order — applyDefaultCustomer
      // would have no-opped then because walkInCustomerId wasn't known yet. Retry now that it is;
      // the guards inside applyDefaultCustomer make this a no-op itself whenever there's nothing
      // to do (no current order yet, or it already has a customer).
      this.applyDefaultCustomer();
    });
    // Task 1: "configure the max discount" — see maxDiscountPercent's own doc comment.
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.maxDiscountPercent = s.maxDiscountPercent),
      error: () => {}, // keep the 100 default — never blocks opening the terminal
    });

    // Offline mode setup — see the class doc comment above. Runs regardless of whether the PIN
    // screen is still up: the catalog cache isn't cashier-specific, and `offline` itself needs to
    // be known before the PIN screen can even decide whether a fresh open is possible.
    this.loadOfflineCatalog();
    this.offlineSync.refreshCatalogCache(true);
    this.offlineSync.queue$.subscribe((q) => (this.offlineQueue = q));
    this.connectivity.online$.subscribe((online) => {
      this.offline = !online;
      if (this.offline) {
        this.loadOfflineCatalog();
      }
      this.reloadProducts();
    });

    // If we already resolved a cashier session for this register session earlier in this same
    // app instance (TerminalSessionService — see the class doc comment above), skip the PIN
    // screen entirely: this is an in-app navigation back to the terminal, not a fresh open. A
    // real browser reload clears that cache, so the PIN screen still appears then as before.
    const cached = this.terminalSession.get(this.sessionId);
    if (cached) {
      this.cashierSession = cached;
      this.loadTabs();
      return;
    }
    // No auto-open here — the terminal stays on the PIN-entry screen (see the template above)
    // until openWithPin() resolves a cashier session. Tabs can't load until that happens, since
    // they're scoped to a cashierSessionId. Opening with a PIN always needs connectivity (see the
    // class doc comment) — there's no offline path into the terminal that skips this.
  }

  /** Refreshes the in-memory offline snapshot from IndexedDB — see OfflineDbService. Called at init (covers loading already-offline) and again on every transition into offline mode (picks up whatever was cached most recently while still online). */
  private loadOfflineCatalog(): void {
    this.offlineDb.getAllProducts().then((p) => (this.offlineCatalog = p));
    this.offlineDb.getAllCustomers().then((c) => {
      this.offlineCustomers = c;
      // Same default-customer treatment as the online cart (applyDefaultCustomer) — only when
      // nothing's selected yet, so a cashier's own pick (or an in-progress offline cart) is
      // never overwritten by a stale refresh.
      if (this.offlineCustomerId == null) {
        this.offlineCustomerId = c.find((cust) => cust.walkIn)?.id ?? null;
      }
    });
    this.offlineDb.getAllPaymentMethods().then((m) => {
      this.offlinePaymentMethods = m;
    });
  }

  /**
   * Resolves whichever cashier session belongs to the entered PIN (creating one if this cashier
   * has none yet on this shift) and only then opens the terminal proper — an invalid PIN leaves
   * `cashierSession` null, so the PIN screen stays up. Reuses the same `join` endpoint
   * "Switch cashier" already uses; the only difference here is that it's required up front rather
   * than optional mid-session.
   */
  openWithPin(): void {
    if (!this.pinInput) return;
    this.pinError = null;
    this.openingWithPin = true;
    this.cashierSessionApi.join(this.sessionId, this.pinInput).subscribe({
      next: (cs) => {
        this.openingWithPin = false;
        this.pinInput = '';
        this.cashierSession = cs;
        this.terminalSession.set(this.sessionId, cs);
        this.loadTabs();
      },
      error: (err) => {
        this.openingWithPin = false;
        this.pinError = err?.error?.message ?? 'Invalid PIN';
      },
    });
  }

  private loadTabs(selectOrderId?: number): void {
    if (!this.cashierSession) return;
    const cashierSessionId = this.cashierSession.id;
    this.orderApi.listOpenOrders(cashierSessionId).subscribe((tabs) => {
      if (tabs.length === 0) {
        this.orderApi.startNewOrder(cashierSessionId).subscribe((created) => {
          this.orderTabs = [created];
          this.setCurrentOrder(created);
        });
        return;
      }
      this.orderTabs = tabs;
      const toSelect = (selectOrderId && tabs.find((t) => t.id === selectOrderId)) || tabs[tabs.length - 1];
      this.setCurrentOrder(toSelect);
    });
  }

  reloadProducts(): void {
    if (this.offline) {
      this.products = this.filterOfflineProducts();
      return;
    }
    this.catalogApi.searchProducts(this.selectedCategoryId, this.searchTerm).subscribe({
      next: (p) => (this.products = p),
      error: () => {
        // A real network failure (not just "no matches") — flip to offline immediately rather
        // than leaving the grid stuck on whatever it last showed until the next connectivity ping.
        this.connectivity.reportNetworkFailure();
        this.products = this.filterOfflineProducts();
      },
    });
  }

  /** Same matching fields as the backend's ProductRepository.search, applied client-side against the cached catalog — same 200-tile cap as the online search too, for the same reason. */
  private filterOfflineProducts(): Product[] {
    const term = this.searchTerm.trim().toLowerCase();
    const matches = this.offlineCatalog.filter((p) => {
      if (this.selectedCategoryId != null && p.categoryId !== this.selectedCategoryId) return false;
      if (!term) return true;
      return (
        p.name.toLowerCase().includes(term) ||
        (p.sku ?? '').toLowerCase().includes(term) ||
        (p.barcode ?? '').toLowerCase().includes(term) ||
        (p.code ?? '').toLowerCase().includes(term)
      );
    });
    return matches.slice(0, 200);
  }

  selectCategory(id: number | null): void {
    this.selectedCategoryId = id;
    this.reloadProducts();
  }

  selectTab(tab: Order): void {
    this.setCurrentOrder(tab);
  }

  newOrder(): void {
    if (!this.cashierSession) return;
    this.orderApi.startNewOrder(this.cashierSession.id).subscribe((created) => {
      this.orderTabs = [...this.orderTabs, created];
      this.setCurrentOrder(created);
    });
  }

  /**
   * A different staff member steps up to this same physical terminal and identifies themselves
   * with their own PIN, so the tab bar switches to just their carts and any sale they ring gets
   * attributed to them — the shift (till/cash float) itself doesn't change. Uses a plain browser
   * prompt, matching how this app already handles other quick one-field inputs (e.g. Reset
   * password on the Staff screen).
   */
  switchCashier(): void {
    const pin = prompt('Enter your PIN to switch to your own session on this shift:');
    if (!pin) return;
    this.error = null;
    this.cashierSessionApi.join(this.sessionId, pin).subscribe({
      next: (cs) => {
        this.cashierSession = cs;
        this.terminalSession.set(this.sessionId, cs);
        this.orderTabs = [];
        this.currentOrder = null;
        this.loadTabs();
      },
      error: (err) => this.handleError(err),
    });
  }

  /**
   * The cashier is done working this shift for now — ends their own cashier session (never
   * someone else's from here; there's no picker) and leaves the terminal. Any cart they never
   * checked out is discarded server-side (see OrderService.discardAllOpenForCashierSession) since
   * it would otherwise be unreachable once their session is closed.
   */
  endMySession(): void {
    if (!this.cashierSession) return;
    if (!confirm('End your session on this shift? Any unfinished carts will be discarded.')) return;
    this.cashierSessionApi.close(this.sessionId, this.cashierSession.id).subscribe({
      next: () => {
        this.terminalSession.clear(this.sessionId);
        this.router.navigate([this.exitLink]);
      },
      error: (err) => this.handleError(err),
    });
  }

  private setCurrentOrder(order: Order): void {
    this.currentOrder = order;
    this.customerId = order.customerId;
    this.error = null;
    this.refreshCreditSummary();
    this.pushActiveOrder(order.id);
    // Every order shown on screen — brand-new or an existing open tab being reselected — should
    // land on Walk-in Customer if it has no customer at all yet. See applyDefaultCustomer's own
    // doc comment for why this used to be narrower and why that turned out to be the bug.
    this.applyDefaultCustomer();
  }

  /**
   * Task: "since we have a default customer 'Walk-in Customer' it is better to set this customer
   * as default selected in the terminal ... instead of having null." Runs from `setCurrentOrder`
   * — i.e. every time an order is shown on screen, whether it's a brand-new cart (`loadTabs`,
   * `newOrder`) or an existing open tab being reselected (reopening the terminal, switching tabs).
   * Originally this only ran right after `startNewOrder`, on the theory that a null `customerId`
   * on an *existing* tab meant a cashier had deliberately picked the old "No customer" option —
   * but that option was removed (Fourier: "Instead of No customer (in select box) replaced by
   * Walk-in Customer Customer from DB"), so a null `customerId` today never reflects a real
   * choice; it's always either a pre-existing sale or this same default silently failing to apply
   * because `walkInCustomerId` hadn't resolved yet (the `customerApi.list()` race — see the retry
   * in `ngOnInit`). Fourier: "every time I open terminal it requires me to select ... instead the
   * walkIn should have been selected by default" — that was carts reopening onto an already-open
   * tab, which this narrower version never touched. The `this.customerId != null` guard still does
   * real work here: it stops this from overwriting a customer the cashier picked for the tab
   * that's currently on screen. Persists it the same way manually picking one from the dropdown
   * would (via `setCustomer`, reusing `this.customerId`), so it lands on the order server-side —
   * not just a frontend display default — and so it's what a completed sale's EFRIS submission
   * (client_tin/client_name/client_phone) actually sees.
   */
  private applyDefaultCustomer(): void {
    if (!this.currentOrder || this.customerId != null || this.walkInCustomerId == null) return;
    this.customerId = this.walkInCustomerId;
    this.setCustomer();
  }

  /**
   * Tells the backend which tab is now on screen, for a customer-facing display (Task: "Add
   * customer-facing double screen") to poll via CashierSessionApiService.getById. Best-effort and
   * fire-and-forget — a failed push just means a customer display briefly shows the previous tab,
   * never something that should block or error out the cashier's own screen.
   */
  private pushActiveOrder(orderId: number): void {
    if (!this.cashierSession) return;
    this.cashierSessionApi.setActiveOrder(this.cashierSession.id, orderId).subscribe({ error: () => {} });
  }

  /**
   * Opens the customer-facing display in a new window — drag it to a second monitor on this same
   * machine, or open the same URL on a separate device/tablet (logged in as this same cashier, or
   * an admin — see CustomerDisplayComponent's doc comment for why). It polls on its own, so nothing
   * further needs to happen here once it's open.
   */
  openCustomerDisplay(): void {
    if (!this.cashierSession) return;
    window.open(`/customer-display/${this.cashierSession.id}`, '_blank');
  }

  /** Task: "Customer balance on POS screen" — null (hidden) for a walk-in with no customer selected. */
  private refreshCreditSummary(): void {
    this.creditSummary = null;
    if (this.customerId) {
      this.customerCreditApi.creditSummary(this.customerId).subscribe((s) => (this.creditSummary = s));
    }
  }

  private applyUpdatedOrder(updated: Order): void {
    this.currentOrder = updated;
    this.orderTabs = this.orderTabs.map((t) => (t.id === updated.id ? updated : t));
    this.error = null;
    // Keep the scanner field focused so a cashier can keep scanning items back-to-back.
    setTimeout(() => this.barcodeField?.nativeElement.focus());
  }

  private handleError(err: any): void {
    if (err?.status === 0) {
      // A genuine network failure, not a rejection from the server — flip global state so the
      // whole terminal (product grid included) knows to fall back to the offline cache, and give
      // a clear reason rather than the generic message below.
      this.connectivity.reportNetworkFailure();
      this.error =
        "Connection lost. This cart will be here once you're back online — use the offline cart below to keep selling in the meantime.";
      return;
    }
    this.error = err?.error?.message ?? 'Something went wrong';
  }

  addProduct(product: Product): void {
    if (this.offline) {
      this.addToOfflineCart(product, 1);
      return;
    }
    if (!this.currentOrder) return;
    this.orderApi.addLine(this.currentOrder.id, product.id).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  addByBarcode(): void {
    const barcode = this.barcodeText.trim();
    if (!barcode) return;
    if (this.offline) {
      this.barcodeText = '';
      this.addOfflineByBarcode(barcode);
      return;
    }
    if (!this.currentOrder) return;
    this.barcodeText = '';
    this.orderApi.addLineByBarcode(this.currentOrder.id, barcode).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  serialsText(line: OrderLine): string {
    return (line.serialNumbers ?? '').split(/\r?\n/).filter((x) => x).join(', ');
  }

  updateSerials(line: OrderLine, event: Event): void {
    if (!this.currentOrder) return;
    const serials = (event.target as HTMLInputElement).value
      .split(/[\n,;]+/)
      .map((x) => x.trim())
      .filter((x) => x.length > 0);
    this.orderApi.updateLine(this.currentOrder.id, line.id, { serialNumbers: serials }).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  updateQty(line: OrderLine, event: Event): void {
    if (!this.currentOrder) return;
    const value = Number((event.target as HTMLInputElement).value);
    if (!value || value < 1) return;
    this.orderApi.updateLine(this.currentOrder.id, line.id, { quantity: value }).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  /**
   * Task 1/2: clamps to `[0, maxDiscountPercent]` client-side before the request even goes out —
   * a friendlier first line of defense than waiting for the server's 400, though
   * OrderService.validateDiscount is what actually enforces both the cap and the admin-only rule
   * (a non-admin can't reach this at all in the ordinary UI, since the input itself is disabled —
   * see the template — but the server never trusts that alone).
   */
  updateDiscount(line: OrderLine, event: Event): void {
    if (!this.currentOrder || !this.canDiscount) return;
    const raw = Number((event.target as HTMLInputElement).value);
    const value = isNaN(raw) ? 0 : Math.min(this.effectiveMaxDiscountPercent, Math.max(0, raw));
    this.orderApi.updateLine(this.currentOrder.id, line.id, { discountPercent: value }).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  removeLine(line: OrderLine): void {
    if (!this.currentOrder) return;
    this.orderApi.removeLine(this.currentOrder.id, line.id).subscribe({
      next: (updated) => this.applyUpdatedOrder(updated),
      error: (err) => this.handleError(err),
    });
  }

  setCustomer(): void {
    if (!this.currentOrder) return;
    this.orderApi.setCustomer(this.currentOrder.id, this.customerId).subscribe({
      next: (updated) => {
        this.applyUpdatedOrder(updated);
        this.refreshCreditSummary();
      },
      error: (err) => this.handleError(err),
    });
  }

  /** Narrows the customer dropdown to whatever's typed, matching on name or phone — client-side,
   * same as the terminal's product search, since the customer list is small enough to filter in
   * memory rather than round-trip to the backend on every keystroke. */
  filterCustomers(): void {
    const term = this.customerSearch.trim().toLowerCase();
    this.filteredCustomers = !term
      ? this.customers
      : this.customers.filter(
          (c) => c.name.toLowerCase().includes(term) || (c.phone ?? '').toLowerCase().includes(term)
        );
  }

  /** Opens/closes the inline "add a customer" panel — kept inline rather than navigating to
   * Admin > Customers so a cashier never loses the cart they're mid-way through building. */
  toggleAddCustomer(): void {
    this.showAddCustomer = !this.showAddCustomer;
    if (this.showAddCustomer) {
      this.newCustomerName = this.customerSearch.trim();
      this.newCustomerPhone = '';
      this.newCustomerEmail = '';
      this.newCustomerTaxId = '';
      this.addCustomerError = null;
    }
  }

  /** Creates the customer and immediately selects them on the current order — "the added customer
   * should be selected" (Task 2). */
  saveNewCustomer(): void {
    const name = this.newCustomerName.trim();
    if (!name) return;
    this.addCustomerError = null;
    this.savingCustomer = true;
    this.customerApi
      .create({
        name,
        phone: this.newCustomerPhone.trim() || null,
        email: this.newCustomerEmail.trim() || null,
        taxId: this.newCustomerTaxId.trim() || null,
      })
      .subscribe({
        next: (created) => {
          this.savingCustomer = false;
          this.showAddCustomer = false;
          this.customers = [...this.customers, created];
          this.customerSearch = '';
          this.filterCustomers();
          this.customerId = created.id;
          this.setCustomer();
        },
        error: (err) => {
          this.savingCustomer = false;
          this.addCustomerError = err?.error?.message ?? 'Failed to add customer';
        },
      });
  }

  /** Marks the current tab as held (a note, purely informational) and opens a fresh tab to keep working. */
  hold(): void {
    if (!this.currentOrder) return;
    this.orderApi.park(this.currentOrder.id, 'Held at terminal').subscribe({
      next: (updated) => {
        this.applyUpdatedOrder(updated);
        this.newOrder();
      },
      error: (err) => this.handleError(err),
    });
  }

  /** Shows/hides an in-terminal panel of this shift's completed sales, without leaving the terminal. */
  toggleReceipts(): void {
    this.showReceipts = !this.showReceipts;
    if (this.showReceipts) {
      this.orderApi.listCompleted(this.sessionId).subscribe({
        next: (r) => (this.receipts = r),
        error: (err) => this.handleError(err),
      });
    }
  }

  /** Cancels the current tab outright — never touches stock, since checkout never ran for it. */
  discard(): void {
    if (!this.currentOrder) return;
    if (!confirm('Discard this order? This cannot be undone.')) return;
    const discardedId = this.currentOrder.id;
    this.orderApi.discard(discardedId).subscribe({
      next: () => {
        this.orderTabs = this.orderTabs.filter((t) => t.id !== discardedId);
        this.error = null;
        if (this.orderTabs.length > 0) {
          this.setCurrentOrder(this.orderTabs[this.orderTabs.length - 1]);
        } else {
          this.newOrder();
        }
      },
      error: (err) => this.handleError(err),
    });
  }

  // ---- offline cart (Task: "local queuing of sales that sync once connectivity returns") ----

  get offlineSubtotal(): number {
    return OfflineCalc.subtotal(this.offlineCart);
  }
  get offlineTaxTotal(): number {
    return OfflineCalc.taxTotal(this.offlineCart);
  }
  get offlineTotal(): number {
    return OfflineCalc.grandTotal(this.offlineCart);
  }
  get offlineItemCount(): number {
    return OfflineCalc.totalQuantity(this.offlineCart);
  }
  get failedQueueCount(): number {
    return this.offlineQueue.filter((s) => s.status === 'failed').length;
  }

  offlineLineTotal(line: QueuedOfflineLine): number {
    return OfflineCalc.lineTotal(line);
  }

  private addToOfflineCart(product: Product, quantity: number): void {
    const existing = this.offlineCart.find((l) => l.productId === product.id && l.discountPercent === 0);
    if (existing) {
      existing.quantity += quantity;
      return;
    }
    this.offlineCart.push({
      productId: product.id,
      description: product.name,
      unitPrice: product.unitPrice,
      quantity,
      discountPercent: 0,
      taxRatePercent: product.taxRatePercent,
      taxInclusive: product.taxRateInclusive,
    });
  }

  private addOfflineByBarcode(barcode: string): void {
    const product = this.offlineCatalog.find((p) => p.barcode === barcode || p.code === barcode);
    if (!product) {
      this.offlineError = `No cached product matches barcode "${barcode}" — it may not be in the offline catalog (see the README's offline-mode caveats).`;
      return;
    }
    this.offlineError = null;
    this.addToOfflineCart(product, 1);
  }

  updateOfflineQty(line: QueuedOfflineLine, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    if (!value || value < 1) return;
    line.quantity = value;
  }

  /** Task 1/2: same client-side clamp as updateDiscount above, plus the same admin-only guard — see its doc comment. */
  updateOfflineDiscount(line: QueuedOfflineLine, event: Event): void {
    if (!this.canDiscount) return;
    const value = Number((event.target as HTMLInputElement).value);
    line.discountPercent = isNaN(value) ? 0 : Math.min(this.effectiveMaxDiscountPercent, Math.max(0, value));
  }

  removeOfflineLine(line: QueuedOfflineLine): void {
    this.offlineCart = this.offlineCart.filter((l) => l !== line);
  }

  discardOfflineCart(): void {
    if (this.offlineCart.length === 0) return;
    if (!confirm('Discard this offline sale? This cannot be undone.')) return;
    this.offlineCart = [];
    this.offlineCheckoutOpen = false;
    this.offlineError = null;
  }

  /** Opens the inline offline payment step — same shape as checkout.component.ts's PaymentRow, built fresh each time so it reflects the current offline total. */
  openOfflineCheckout(): void {
    if (this.offlineCart.length === 0) return;
    const defaultIndex = Math.max(0, this.offlinePaymentMethods.findIndex((m) => m.cash));
    this.offlinePaymentRows = this.offlinePaymentMethods.map((m, i) => ({
      method: m.code,
      name: m.name,
      selected: i === defaultIndex,
      amount: i === defaultIndex ? this.offlineTotal : null,
      credit: m.credit,
    }));
    this.offlineError = null;
    this.offlineCheckoutOpen = true;
  }

  toggleOfflinePayment(row: OfflinePaymentRow): void {
    row.selected = !row.selected;
    if (row.selected && row.amount == null) {
      row.amount = this.maxOfflineAmountFor(row);
    }
  }

  /**
   * Same "cap at what's still owed" rule as checkout.component.ts's maxAmountFor — but with no
   * live credit-limit cap: that requires a fresh CreditSummary from the server, which offline
   * mode has no access to. A credit sale that turns out to exceed the customer's limit is caught
   * for real at sync time (OrderService.syncOfflineSale calls the same completeCheckout as any
   * online sale) and lands in the queue as 'failed', not silently accepted.
   */
  maxOfflineAmountFor(row: OfflinePaymentRow): number {
    const total = this.offlineTotal;
    const othersSum = this.offlinePaymentRows
      .filter((r) => r.selected && r !== row)
      .reduce((sum, r) => sum + (r.amount ?? 0), 0);
    return Math.max(0, Number((total - othersSum).toFixed(2)));
  }

  onOfflineAmountChange(row: OfflinePaymentRow, value: number | null): void {
    if (value == null || (value as unknown as string) === '' || isNaN(value)) {
      row.amount = null;
      return;
    }
    row.amount = Math.min(value, this.maxOfflineAmountFor(row));
  }

  offlineCreditRowSelected(): boolean {
    return this.offlinePaymentRows.some((r) => r.selected && r.credit);
  }

  /** Queues the offline sale locally and tries to sync right away (a no-op while still offline) — see OfflineSyncService.queueSale. */
  queueOfflineSale(): void {
    if (!this.cashierSession) return;
    const payments: QueuedOfflinePayment[] = this.offlinePaymentRows
      .filter((r) => r.selected && r.amount && r.amount > 0)
      .map((r) => ({ method: r.method, methodName: r.name, amount: r.amount as number }));
    if (payments.length === 0) {
      this.offlineError = 'Select a payment method and enter an amount';
      return;
    }
    const tendered = payments.reduce((sum, p) => sum + p.amount, 0);
    if (tendered < this.offlineTotal) {
      this.offlineError = `Payments (${tendered.toFixed(2)}) do not cover the estimated amount due (${this.offlineTotal.toFixed(2)})`;
      return;
    }
    if (this.offlineCreditRowSelected() && !this.offlineCustomerId) {
      this.offlineError = 'Select a customer before paying with store credit';
      return;
    }
    this.offlineError = null;
    const customer = this.offlineCustomers.find((c) => c.id === this.offlineCustomerId);
    const sale: QueuedOfflineSale = {
      clientTempId: this.generateClientTempId(),
      cashierSessionId: this.cashierSession.id,
      customerId: this.offlineCustomerId,
      customerName: customer?.name ?? 'Walk-in Customer',
      queuedAt: new Date().toISOString(),
      lines: this.offlineCart.map((l) => ({ ...l })),
      payments,
      dueDate: this.offlineCreditRowSelected() ? this.offlineDueDate : null,
      clientEstimatedTotal: this.offlineTotal,
      status: 'pending',
      lastError: null,
    };
    this.offlineSync.queueSale(sale);
    this.offlineCart = [];
    this.offlineCheckoutOpen = false;
    // Back to the walk-in default (if one's cached) for the next offline cart, same as a fresh
    // online cart gets via applyDefaultCustomer — not just cleared to nothing.
    this.offlineCustomerId = this.offlineCustomers.find((c) => c.walkIn)?.id ?? null;
    this.offlineDueDate = null;
  }

  private generateClientTempId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return 'offline-' + Date.now() + '-' + Math.random().toString(36).slice(2);
  }

  /** Shows/hides the offline queue panel — a cashier or admin can review 'failed' sales here and discard the ones that can't be fixed. */
  toggleOfflineQueue(): void {
    this.showOfflineQueue = !this.showOfflineQueue;
  }

  discardQueuedSale(sale: QueuedOfflineSale): void {
    if (!confirm('Discard this offline sale permanently? It will never be recorded, and this cannot be undone.')) return;
    this.offlineSync.discardQueuedSale(sale.clientTempId);
  }
}
