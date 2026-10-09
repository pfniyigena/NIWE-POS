import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { SidenavStateService } from '../core/sidenav-state.service';

/**
 * Shared topbar + sidebar chrome for every non-terminal screen (registers, sessions, returns,
 * and — for admins — dashboard/catalog/customers/staff management).
 * The terminal has its own full-screen layout (order tabs replace the topbar), so it doesn't use this.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <div class="topbar">
      <!-- Fourier: "Make Mobile responsive" (admin/back-office shell scope) — hamburger is hidden
           by default and only shown below the mobile breakpoint via CSS (see styles.scss); toggles
           the off-canvas sidebar via a plain component field rather than SidenavStateService,
           since ShellComponent itself is destroyed/recreated on every navigation (see isOpen()'s
           own javadoc below), so "closed again on the next page" is the right default for free. -->
      <button type="button" class="hamburger-btn" (click)="mobileNavOpen = !mobileNavOpen" aria-label="Toggle menu">☰</button>
      <!-- Task: "Add Business name and logo in the header of system template (where label POS)" —
           falls back to the plain "POS" brand label until settings load or when nothing's been
           configured, so this never regresses the topbar for stores that haven't set a business name. -->
      <span class="brand" *ngIf="!businessName">POS</span>
      <span class="brand" *ngIf="businessName" style="display:flex; align-items:center; gap:8px;">
        <img *ngIf="logoDataUrl" [src]="logoDataUrl" alt="" style="height:24px; max-width:120px; object-fit:contain;" />
        {{ businessName }}
      </span>
      <span class="spacer"></span>
      <div class="user-info">
        <span class="user-name">{{ auth.currentUser()?.fullName }}</span>
        <!-- Self-service "My Profile" (Task: "Add my profile, change my password") — every logged-in user, not just admins, so it lives in the topbar rather than the admin-only sidebar. -->
        <a class="btn btn-sm btn-outline-secondary" routerLink="/profile" routerLinkActive="active">👤 Profile</a>
        <button class="btn btn-sm btn-outline-secondary" (click)="logout()">Log out</button>
      </div>
    </div>
    <div class="app-shell">
      <!-- Only ever in the DOM while the mobile nav is open, and mobileNavOpen can only become
           true by clicking the hamburger button above, which CSS hides above the mobile breakpoint
           — so this backdrop never appears on desktop, even if the window is later resized down. -->
      <div class="sidebar-backdrop" *ngIf="mobileNavOpen" (click)="mobileNavOpen = false"></div>
      <nav class="sidebar" [class.open]="mobileNavOpen">
        <!-- Fourier: "Icon on Dashboard menu does not look like other menus" — this was plain
             inline content (icon span + a literal space + "Dashboard"), so the icon just sat
             wherever normal text flow put it; every top-level group button below lays its icon
             out in a flex row with a fixed gap instead (see sidenav-link-row, reused here rather
             than sidenav-toggle itself, since that class also resets padding/background/width for
             the <button> toggles — this is a plain link with no arrow, and sidenav-link already
             gives it the right padding/color/hover/active treatment). -->
        <a
          *ngIf="auth.currentUser()?.role === 'ADMIN'"
          class="sidenav-link sidenav-link-row"
          routerLink="/admin/dashboard"
          routerLinkActive="active"
          ><span class="sidenav-icon">📊</span><span>Dashboard</span></a
        >
        <!-- Sidebar groups are now an accordion (Task: "Once one menu is open other should be
             closed") — openGroup holds at most one group name, and toggleGroup() both opens a
             group and closes whichever other one was open, instead of each group keeping its
             own independent boolean. -->
        <!-- Fourier: "Icon on POS menu does not look like other menus" — this button was using the
             old sidenav-heading class (a small, muted, uppercase section-label style meant for
             plain headings), left over from before every other group button got its own icon;
             every other top-level group toggle uses sidenav-link, which is what actually styles
             the icon glyph to match them. -->
        <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('pos')">
          <span class="sidenav-icon">🛒</span>
          <span class="sidenav-label">POS</span>
          <span class="sidenav-arrow">{{ isOpen('pos') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('pos')">
          <!-- Registers & Terminal is where a shift is opened/browsed and where "Go to terminal" lives for any -->
          <!-- open register — ADMIN-only now (Task: "Remove Registers & Terminal menu from Cashier side so he -->
          <!-- can not create a session himself"): a cashier reaching an open register there could jump into a -->
          <!-- terminal for a shift nobody assigned them to, silently self-creating a cashier session on it via -->
          <!-- the terminal's mine() call. A cashier still reaches their own assigned shift's terminal via the -->
          <!-- "Terminal" link on their own row on Sessions instead. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link" routerLink="/registers" routerLinkActive="active"
            ><span class="sidenav-icon">🖥️</span> Registers &amp; Terminal</a
          >
          <!-- Sessions lists individual CashierSessions (not shifts). Open to any user; the backend scopes what comes back — an admin sees every session, a cashier sees only their own. -->
          <a class="sidenav-link" routerLink="/sessions" routerLinkActive="active"><span class="sidenav-icon">📋</span> Sessions</a>
          <a class="sidenav-link" routerLink="/returns" routerLinkActive="active"><span class="sidenav-icon">↩️</span> Returns</a>
          <a class="sidenav-link" routerLink="/receipts" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Receipts</a>
          <!-- Shifts moved here from the flat Admin list (Task: "Move Shifts menu under POS") — it's the other everyday POS oversight screen, just ADMIN-only within this group. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link" routerLink="/admin/shifts" routerLinkActive="active"
            ><span class="sidenav-icon">⏱️</span> Shifts</a
          >
          <!-- POS Reports (Task: "Add POS Report..."): a picker of Sales-Transactions/Profit reports, each with its own filters + Excel/PDF export. ADMIN-only, like every other reporting screen. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link" routerLink="/admin/reports" routerLinkActive="active"
            ><span class="sidenav-icon">📈</span> Reports</a
          >
        </ng-container>
        <!-- Restaurant group (Fourier: "I want now to add Restaurant management in our ERP"; later
             moved here, right after POS — Fourier: "move Restaurant menu next to POS menu").
             Always available — no enable/disable switch (Fourier: "make all modules available
             without enabling them"). Orders/Floor plan/Takeaway-Delivery/Deliveries board/Kitchen
             display/Reservations/Receipts/Wastage are open to any logged-in staff (Fourier's own
             dedicated-screen choice, kept separate from the POS terminal, is for day-to-day use by
             cashiers too — Wastage per "Restaurants need a dedicated wastage module":
             logging a dropped plate is kitchen/front-of-house work, not back-office admin);
             Tables/Menu/Reports are admin-only config, same tier as every
             other module's own setup/reporting screens. -->
        <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('restaurant')">
          <span class="sidenav-icon">🍽️</span>
          <span class="sidenav-label">Restaurant</span>
          <span class="sidenav-arrow">{{ isOpen('restaurant') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('restaurant')">
          <!-- Fourier: "There is no screen for Restaurant orders" — every open order (dine-in,
               takeaway, delivery) in one list; the natural landing link for the whole group, so
               it sits above the more specific screens below. -->
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/orders" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><span class="sidenav-icon">📝</span> Orders</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/tables/floor-plan" routerLinkActive="active"><span class="sidenav-icon">🍽️</span> Floor plan</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/orders/new" routerLinkActive="active"><span class="sidenav-icon">🥡</span> Takeaway/Delivery</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/deliveries" routerLinkActive="active"><span class="sidenav-icon">🛵</span> Deliveries board</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/kitchen" routerLinkActive="active"><span class="sidenav-icon">🍳</span> Kitchen display</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reservations" routerLinkActive="active"><span class="sidenav-icon">📅</span> Reservations</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/receipts" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Receipts</a>
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/wastage" routerLinkActive="active"><span class="sidenav-icon">🗑️</span> Wastage</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/tables" routerLinkActive="active"><span class="sidenav-icon">🪑</span> Tables</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/menu" routerLinkActive="active"><span class="sidenav-icon">📋</span> Menu</a>
        </ng-container>
        <!-- Sales group (Task: "Add Sale order and Customer payment(So that we can have POS & Sales)
             ... Then let have Sale as Menu and Move Customers into Sales (It becomes Submenu)") —
             originally also held Customers/Customer statement/Customer payments as submenus here,
             but Fourier's later request ("Lets have Customers Menu after Sales Menu and Move
             Customers, Customer Statements, Customer payments and it") split those back out into
             their own top-level Customers group just below. Every financial screen here (Sale
             orders, Sale order returns, Invoices, Credit notes, Reports) stays ADMIN-only. -->
        <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('sales')">
          <span class="sidenav-icon">💵</span>
          <span class="sidenav-label">Sales</span>
          <span class="sidenav-arrow">{{ isOpen('sales') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('sales')">
          <!-- Fourier: "Add Quotation ... without touching warehousing" — a non-binding price offer that precedes a sale order, placed just before it. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/quotations" routerLinkActive="active"><span class="sidenav-icon">💬</span> Quotations</a>
          <!-- Task: "Add Sale order... So that we can have POS & Sales" — a back-office order taken outside the register, entirely separate from POS checkout. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/sales-orders" routerLinkActive="active"><span class="sidenav-icon">📝</span> Sale orders</a>
          <!-- Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" — store-wide ledger; processing itself happens from a sale order's own detail page. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/sales-order-returns" routerLinkActive="active"><span class="sidenav-icon">↩️</span> Sale order returns</a>
          <!-- Fourier: "Add Invoice ... without touching warehousing" — formal documents generated from a sale order above; store-wide ledger, same convention as Sale order returns just above. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/invoices" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Invoices</a>
          <!-- Fourier: "Add Recurring Invoice" — a saved template with real invoice line items that auto-generates a brand-new, already-ISSUED invoice on a schedule, placed right after Invoices. Deliberately its own standalone module, not folded into the Subscriptions group further down (Fourier, via AskUserQuestion). -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/recurring-invoices" routerLinkActive="active"><span class="sidenav-icon">🔄</span> Recurring invoices</a>
          <!-- Fourier: "Add credit note Menu (Void) Where we can printout them" — every voided invoice, store-wide, same convention as Invoices just above. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/credit-notes" routerLinkActive="active"><span class="sidenav-icon">🧮</span> Credit notes</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/sales/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          <!-- Fourier: "Every time invoice is ISSUED send try to send email to customer and to
               configured emails. (Add invoices receiver emails in sales settings)" — same
               "Settings" placement/icon convention as Help Desk's own group. -->
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/sales/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
        </ng-container>
        <!-- Customers group (Fourier: "Lets have Customers Menu after Sales Menu and Move
             Customers, Customer Statements, Customer payments and it") — split out of Sales into
             its own top-level sidenav group, right after Sales. "Customers" itself stays open to
             any logged-in user (Task: "Cashier can add or modify customer" / "Give Cashier
             Customer menu"), same as it was inside Sales; Customer statement/Customer payments
             stay ADMIN-only, same as before. -->
        <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('customers')">
          <span class="sidenav-icon">👥</span>
          <span class="sidenav-label">Customers</span>
          <span class="sidenav-arrow">{{ isOpen('customers') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('customers')">
          <a class="sidenav-link sidenav-sublink" routerLink="/admin/customers" routerLinkActive="active"><span class="sidenav-icon">👥</span> Customers</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/customers/statement" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Customer statement</a>
          <a *ngIf="auth.currentUser()?.role === 'ADMIN'" class="sidenav-link sidenav-sublink" routerLink="/admin/customer-payments" routerLinkActive="active"><span class="sidenav-icon">💳</span> Customer payments</a>
        </ng-container>
        <!-- Purchases group (Fourier: "Lets have Purchases Menu after Customers menu and move
             Purchases requisitions, Purchases orders, Purchases return under it") — split out of
             the Inventory group below into its own top-level sidenav group, right after
             Customers; still ADMIN-only, same tier as before inside Inventory. -->
        <ng-container *ngIf="auth.currentUser()?.role === 'ADMIN'">
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('purchases')">
            <span class="sidenav-icon">📝</span>
            <span class="sidenav-label">Purchases</span>
            <span class="sidenav-arrow">{{ isOpen('purchases') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('purchases')">
            <!-- Fourier: "Add ... the purchase-requisition approval gate ... without touching warehousing" — the internal approval step placed just before Purchase orders below. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/purchase-requisitions" routerLinkActive="active"><span class="sidenav-icon">📋</span> Purchase requisitions</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/purchase-orders" routerLinkActive="active"><span class="sidenav-icon">📝</span> Purchase orders</a>
            <!-- Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory Module" — store-wide ledger; processing itself happens from a purchase order's own detail page. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/purchase-returns" routerLinkActive="active"><span class="sidenav-icon">↩️</span> Purchase returns</a>
            <!-- Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds equivalent with Invoices and Credit note)" — formal documents generated from a purchase order above; store-wide ledger, same convention as Invoices/Credit notes on the Sales side. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/bills" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Bills</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/refunds" routerLinkActive="active"><span class="sidenav-icon">🧮</span> Refunds</a>
          </ng-container>
        </ng-container>
        <!-- Supplier group (Fourier: "Lets have Supplier Menu after Purchases and move Suppliers,
             Supplier Statements, Supplier payments and it") — split out of the Inventory group
             below into its own top-level sidenav group, right after Purchases; still ADMIN-only,
             same tier as before inside Inventory. -->
        <ng-container *ngIf="auth.currentUser()?.role === 'ADMIN'">
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('suppliers')">
            <span class="sidenav-icon">🚚</span>
            <span class="sidenav-label">Supplier</span>
            <span class="sidenav-arrow">{{ isOpen('suppliers') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('suppliers')">
            <!-- Task: "Inventory depth ... purchase orders and supplier records ... stock-receiving flow ... stock adjustments/write-offs" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/suppliers" routerLinkActive="active"><span class="sidenav-icon">🚚</span> Suppliers</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/supplier-prices" routerLinkActive="active"><span class="sidenav-icon">🏷️</span> Supplier prices</a>
            <!-- Task 5: "Add Supplier Statement" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/suppliers/statement" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Supplier statement</a>
            <!-- Task: "Add Supplier payment" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/supplier-payments" routerLinkActive="active"><span class="sidenav-icon">💳</span> Supplier payments</a>
          </ng-container>
        </ng-container>
        <ng-container *ngIf="auth.currentUser()?.role === 'ADMIN'">
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('inventory')">
            <span class="sidenav-icon">📦</span>
            <span class="sidenav-label">Inventory</span>
            <span class="sidenav-arrow">{{ isOpen('inventory') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('inventory')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/products" routerLinkActive="active"><span class="sidenav-icon">📦</span> Products</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/categories" routerLinkActive="active"><span class="sidenav-icon">🏷️</span> Categories</a>
            <!-- Fourier: "Add product brands" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/brands" routerLinkActive="active"><span class="sidenav-icon">🏭</span> Brands</a>
            <!-- Suppliers/Purchase requisitions/Purchase orders/Purchase returns/Supplier payments
                 moved out into their own Purchases/Supplier top-level groups just above, per
                 Fourier's own later request. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/stock-adjustments" routerLinkActive="active"><span class="sidenav-icon">🔧</span> Stock adjustments</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/stock-counts" routerLinkActive="active"><span class="sidenav-icon">📋</span> Stock counts</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/reorder" routerLinkActive="active"><span class="sidenav-icon">🔁</span> Reorder</a>
            <!-- Fourier: "multi-location/multi-warehouse tracking ... start on the foundation
                 phase" — foundation phase only, see WarehouseListComponent's own javadoc: nothing
                 else in the app lets you pick a warehouse yet. -->
            <!-- Fourier: "How business branches can be introduced?" — a branch owns one or more warehouses. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/branches" routerLinkActive="active"><span class="sidenav-icon">🏢</span> Branches</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/warehouses" routerLinkActive="active"><span class="sidenav-icon">🏬</span> Warehouses</a>
            <!-- Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers" — the first
                 thing that actually moves stock between warehouses; a transfer is logged PENDING
                 here and only takes effect once confirmed, same review step as Stock adjustments. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/stock-transfers" routerLinkActive="active"><span class="sidenav-icon">🔀</span> Stock transfers</a>
            <!-- Fourier: "Product Packaging & Breakdown / Unit Conversion ... Whiskey Breakdown &
                 Beer Breakdown ... If not activated ignore packaging" — rules are always
                 reachable here; the toggle (Inventory settings below) only gates actually running
                 a breakdown, not these links, same "configuration always visible" treatment the
                 Accounting module gives itself. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/packaging/rules" routerLinkActive="active"><span class="sidenav-icon">🍾</span> Packaging rules</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/packaging/breakdown" routerLinkActive="active"><span class="sidenav-icon">🥃</span> Breakdown stock</a>
            <!-- Task 2 (this pass): "Move Inventory Report as submenu to Inventory Menu" — was only reachable via the general Admin > Reports picker before. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/inventory/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
            <!-- Task 2: "Inventory Setting (Example: allow negative stock)" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/inventory/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Inventory settings</a>
          </ng-container>
          <!-- Manufacturing group (Fourier: "We are now good with POS and Sale Module ... Add
               Manufacturing Module" — scoped via his own list "1.Bill of Materials 2.Production
               plans 3.Manufacturing orders 4.Indirects Costs 5.Workstations 5.Settings (Production
               routings etc)"). Same accordion/admin-only treatment as every other group here. -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('manufacturing')">
            <span class="sidenav-icon">🏭</span>
            <span class="sidenav-label">Manufacturing</span>
            <span class="sidenav-arrow">{{ isOpen('manufacturing') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('manufacturing')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/boms" routerLinkActive="active"
              ><span class="sidenav-icon">📋</span> Bill of materials</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/production-plans" routerLinkActive="active"
              ><span class="sidenav-icon">📈</span> Production plans</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/orders" routerLinkActive="active"
              ><span class="sidenav-icon">🛠️</span> Manufacturing orders</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/workstations" routerLinkActive="active"
              ><span class="sidenav-icon">⚙️</span> Workstations</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/routings" routerLinkActive="active"
              ><span class="sidenav-icon">🧭</span> Routings</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/overhead-costs" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Indirect costs</a
            >
          </ng-container>
          <!-- Subscriptions group (Fourier: "Make subscriptions module as new Menu", then "Let
               subscriptions comes after Manufacturing") — split out of the Sales group into its
               own top-level sidenav group, placed right after Manufacturing; same accordion/
               admin-only treatment as every other group here. Customer subscriptions to a fixed,
               admin-defined plan; each billing date auto-generates an invoice (see backend
               SubscriptionService). -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('subscriptions')">
            <span class="sidenav-icon">🔁</span>
            <span class="sidenav-label">Subscriptions</span>
            <span class="sidenav-arrow">{{ isOpen('subscriptions') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('subscriptions')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/subscription-plans" routerLinkActive="active"><span class="sidenav-icon">📋</span> Subscription plans</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions" routerLinkActive="active"><span class="sidenav-icon">🔁</span> Subscriptions</a>
          </ng-container>
          <!-- Help Desk group (Fourier: "Add Help Desk module (with its menu) should manage
               customer and internal support requests from creation through resolution") — same
               top-level accordion group/admin-only treatment as every other group here, placed
               right after Subscriptions. One unified ticket list for both customer and internal
               requests (filter by type on the Tickets screen), plus the configurable Categories
               tickets route through. Guests submit/check a ticket with no login at all via the
               separate public /helpdesk page (see app.routes.ts), not through this admin menu. -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('helpdesk')">
            <span class="sidenav-icon">🎫</span>
            <span class="sidenav-label">Help Desk</span>
            <span class="sidenav-arrow">{{ isOpen('helpdesk') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('helpdesk')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/tickets" routerLinkActive="active"><span class="sidenav-icon">🎫</span> Tickets</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/categories" routerLinkActive="active"><span class="sidenav-icon">📋</span> Categories</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          </ng-container>
          <!-- Expenses group (Fourier: "I prefer to have the Expenses as module separate from
               Accounting (with it own Menu just before Accounting menu)") — split out of the
               Accounting group below into its own top-level sidenav group, same admin-only/
               accordion treatment, placed immediately above Accounting. Incomes stays put in
               Accounting for now; only Expenses and its Recurring-expenses templates moved. -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('expenses')">
            <span class="sidenav-icon">💸</span>
            <span class="sidenav-label">Expenses</span>
            <span class="sidenav-arrow">{{ isOpen('expenses') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('expenses')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses" routerLinkActive="active"
              ><span class="sidenav-icon">💸</span> Expenses</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/recurring-expenses" routerLinkActive="active"
              ><span class="sidenav-icon">🔁</span> Recurring expenses</a
            >
          </ng-container>
          <!-- Accounting group (Fourier: "We need to add Accounting module. Then all POS, Sale,
               Inventory and Manufacturing module will be integrated"). Always available — no
               enable/disable switch (Fourier: "make all modules available without enabling them").
               Fourier's later, larger report-batch request ("Add Accounting Reports") first
               landed here as four extra sidenav accordion groups, one per report section — but
               that put two dozen-plus report links directly in the sidebar. Fourier's own
               follow-up ("Move those report to the Report Menu and accounting Report... same as
               we did for POS Report") replaced that with the POS Reports picker pattern: two
               single sublinks, each opening its own picker screen. Fourier then asked to fold
               the two together ("Move also Accounting Report to the report Menu"), so there is
               now just one sublink, opening AccountingReportMenuComponent's single grouped
               picker screen covering all four report sections. Same accordion (only one group
               open at a time, via SidenavStateService) and admin-only treatment as every other
               group here. Expenses (and its Recurring-expenses templates) moved out into its own
               group just above, per Fourier's own later request. -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('accounting')">
            <span class="sidenav-icon">📒</span>
            <span class="sidenav-label">Accounting</span>
            <span class="sidenav-arrow">{{ isOpen('accounting') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('accounting')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/accounts" routerLinkActive="active"
              ><span class="sidenav-icon">📋</span> Chart of accounts</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/cost-centers" routerLinkActive="active"
              ><span class="sidenav-icon">🏷️</span> Cost centers</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/journal-entries" routerLinkActive="active"
              ><span class="sidenav-icon">📝</span> Journal entries</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/incomes" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Incomes</a
            >
            <!-- Report Menu — single entry point for all 24 accounting reports (Accounting
                 Reports: Tax Report, Tax Declaration, Income Statement, Balance Sheet,
                 Profit & Loss, Financial Transactions, Cash Flow Report, Assets; plus Journals
                 Reports / Expenses Reports / Incomes Reports) — one link to
                 AccountingReportMenuComponent's own grouped picker screen. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/reports/menu" routerLinkActive="active"
              ><span class="sidenav-icon">📚</span> Reports</a
            >
          </ng-container>
          <!-- Users group (Task: "Move Staff menu to new Menu called Users") — same grouping treatment as Customers just above; "Staff" itself keeps its existing label and route. Fourier: "Move Users and Settings menus after Accounting Menu" — was directly after Inventory; both this and Settings below now sit after Accounting instead, order otherwise unchanged. -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('users')">
            <span class="sidenav-icon">🧑‍💼</span>
            <span class="sidenav-label">Users</span>
            <span class="sidenav-arrow">{{ isOpen('users') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('users')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/staff" routerLinkActive="active"><span class="sidenav-icon">🧑‍💼</span> Staff</a>
          </ng-container>
          <!-- Settings groups the "how the store is configured" screens (Task: "Move Payment Methods menu under new menu called Settings" / "Add taxes menu under Settings Menu"). -->
          <button type="button" class="sidenav-link sidenav-toggle" (click)="toggleGroup('settings')">
            <span class="sidenav-icon">⚙️</span>
            <span class="sidenav-label">Settings</span>
            <span class="sidenav-arrow">{{ isOpen('settings') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('settings')">
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/payment-methods" routerLinkActive="active"
              ><span class="sidenav-icon">💳</span> Payment methods</a
            >
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/settings/taxes" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Taxes</a
            >
            <!-- Task: "Under Settings menu add Business Details menu" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/settings/business" routerLinkActive="active"
              ><span class="sidenav-icon">🏢</span> Business details</a
            >
            <!-- Task: "Add System setting like currency, time zone, date format, etc" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/settings/system" routerLinkActive="active"
              ><span class="sidenav-icon">🌐</span> System settings</a
            >
            <!-- Fourier: "Configure smtp for sending email" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/settings/email" routerLinkActive="active"
              ><span class="sidenav-icon">✉️</span> Email settings</a
            >
            <!-- Task: "This will work as a plugin (or apps) to be installed and configured" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/apps" routerLinkActive="active"
              ><span class="sidenav-icon">🧩</span> Apps</a
            >
            <!-- Task: "Implement an audit log of who did what and when" -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/audit-log" routerLinkActive="active"
              ><span class="sidenav-icon">🕵️</span> Audit Log</a
            >
            <!-- Fourier: "no backup/restore or bulk import-export anywhere in the backend" — DB backup/restore, CSV import, and export/year-end archive, all on one screen. -->
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/data" routerLinkActive="active"
              ><span class="sidenav-icon">🗄️</span> Backup &amp; Data</a
            >
          </ng-container>
        </ng-container>
      </nav>
      <main class="app-main" [class.narrow]="narrow" [class.wide]="wide">
        <ng-content></ng-content>
      </main>
    </div>
  `,
  styles: [
    `
      .sidenav-toggle {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        text-align: left;
        background: none;
        border: none;
        cursor: pointer;
        font: inherit;
        color: inherit;
        padding: 0;
        /* Fourier: "Add some style on Menus to look good" — a little extra breathing room above
           each top-level group button so the sidebar reads as distinct sections rather than one
           dense, undifferentiated stack; Dashboard (the only non-toggle top-level item) sits
           first and doesn't need it. */
        margin-top: 10px;
      }
      /* Fourier: "Icon on Dashboard menu does not look like other menus" — the same icon+gap
         rhythm as .sidenav-toggle above, minus the button-reset properties (padding/background/
         width/text-align) that a plain sidenav-link link doesn't need and shouldn't lose. */
      .sidenav-link-row {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .sidenav-label {
        flex: 1;
      }
      .sidenav-arrow {
        font-size: 0.7rem;
        opacity: 0.7;
        transition: opacity 0.15s ease;
      }
      .sidenav-toggle:hover .sidenav-arrow {
        opacity: 1;
      }
      .sidenav-icon {
        display: inline-block;
        width: 1.2em;
        text-align: center;
        /* Fourier: "Add some style on Menus to look good" — a touch more visual presence than
           plain inherited text size, without turning the emoji glyphs cartoonish. */
        font-size: 1.05em;
      }
      .sidenav-sublink {
        padding-left: 28px;
      }
      /* Fourier: "Make Mobile responsive" — cosmetics only for the hamburger button; its
         display (hidden on desktop, shown below the mobile breakpoint) and the backdrop's
         display/positioning both live in the global @media block in styles.scss instead of here,
         so there's one single place deciding visibility rather than two stylesheets fighting
         over the same property at the same specificity. */
      .hamburger-btn {
        background: none;
        border: none;
        color: inherit;
        font-size: 1.4rem;
        line-height: 1;
        padding: 4px 10px;
        margin-right: 4px;
        border-radius: 0.375rem;
        cursor: pointer;
      }
      .hamburger-btn:hover {
        background: var(--surface-alt, rgba(0, 0, 0, 0.06));
      }
    `,
  ],
})
export class ShellComponent implements OnInit {
  @Input() narrow = false;
  @Input() wide = false;

  /** Task: "Add Business name and logo in the header of system template" — null/unset means the topbar keeps showing plain "POS". */
  businessName: string | null = null;
  logoDataUrl: string | null = null;

  /**
   * Fourier: "Make Mobile responsive" — whether the off-canvas sidebar is open on a narrow
   * screen. Deliberately a plain field, not SidenavStateService: that service is a true
   * app-lifetime singleton specifically because this component is destroyed/recreated on every
   * navigation (see isOpen()'s own javadoc below) — but for the mobile nav, starting closed on
   * every new page IS the wanted behavior, so no singleton is needed here.
   */
  mobileNavOpen = false;

  constructor(
    public auth: AuthService,
    private router: Router,
    private storeSettingsApi: StoreSettingsApiService,
    private sidenavState: SidenavStateService
  ) {}

  ngOnInit(): void {
    // Every screen using this shell is behind login (the shell itself assumes an authenticated
    // user — see `auth.currentUser()` above), and GET /api/settings is open to any authenticated
    // user as of this pass, so this fetch never needs its own error UI: on failure the topbar
    // just keeps showing plain "POS", same as before this pass existed.
    this.storeSettingsApi.get().subscribe({
      next: (s) => {
        this.businessName = s.businessName;
        this.logoDataUrl = s.logoDataUrl;
      },
      error: () => {},
    });
  }

  /**
   * Bug fix: "When I click on Inventory => Products, after loading product, POS Menu open its
   * submenu instead of keeping Inventory submenu opened." Delegates to SidenavStateService (a
   * true app-lifetime singleton) instead of a field on this component — see that service's
   * javadoc for why: ShellComponent itself gets destroyed and recreated on every navigation
   * (every routed page embeds its own `<app-shell>`, there's no persistent layout wrapping a
   * `<router-outlet>`), so state kept here alone can't survive a single link click.
   */
  isOpen(group: string): boolean {
    return this.sidenavState.isOpen(group);
  }

  toggleGroup(group: string): void {
    this.sidenavState.toggle(group);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
