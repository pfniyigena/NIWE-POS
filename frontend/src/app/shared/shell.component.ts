import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { SidenavStateService } from '../core/sidenav-state.service';
import { ModuleApiService, ModuleKey } from '../core/api/module-api.service';
import { HotelApiService } from '../core/api/hotel-api.service';

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
        <a class="btn btn-sm btn-outline-secondary" routerLink="/settings/appearance" routerLinkActive="active" title="Colour theme">🎨 Theme</a>
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
          *ngIf="canSee('/admin/dashboard')"
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
        <button type="button" *ngIf="modules.on('POS')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('pos')">
          <span class="sidenav-icon">🛒</span>
          <span class="sidenav-label">POS</span>
          <span class="sidenav-arrow">{{ isOpen('pos') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('pos') && modules.on('POS')">
          <!-- Registers & Terminal is where a shift is opened/browsed and where "Go to terminal" lives for any -->
          <!-- open register — ADMIN-only now (Task: "Remove Registers & Terminal menu from Cashier side so he -->
          <!-- can not create a session himself"): a cashier reaching an open register there could jump into a -->
          <!-- terminal for a shift nobody assigned them to, silently self-creating a cashier session on it via -->
          <!-- the terminal's mine() call. A cashier still reaches their own assigned shift's terminal via the -->
          <!-- "Terminal" link on their own row on Sessions instead. -->
          <a *ngIf="canSee('/registers')" class="sidenav-link" routerLink="/registers" routerLinkActive="active"
            ><span class="sidenav-icon">🖥️</span> Registers &amp; Terminal</a
          >
          <!-- Sessions lists individual CashierSessions (not shifts). Open to any user; the backend scopes what comes back — an admin sees every session, a cashier sees only their own. -->
          <a class="sidenav-link" routerLink="/sessions" routerLinkActive="active"><span class="sidenav-icon">📋</span> Sessions</a>
          <a class="sidenav-link" routerLink="/returns" routerLinkActive="active"><span class="sidenav-icon">↩️</span> Returns</a>
          <a class="sidenav-link" routerLink="/receipts" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Receipts</a>
          <!-- Shifts moved here from the flat Admin list (Task: "Move Shifts menu under POS") — it's the other everyday POS oversight screen, just ADMIN-only within this group. -->
          <a *ngIf="canSee('/admin/shifts')" class="sidenav-link" routerLink="/admin/shifts" routerLinkActive="active"
            ><span class="sidenav-icon">⏱️</span> Shifts</a
          >
          <!-- POS Reports (Task: "Add POS Report..."): a picker of Sales-Transactions/Profit reports, each with its own filters + Excel/PDF export. ADMIN-only, like every other reporting screen. -->
          <a *ngIf="canSee('/admin/reports')" class="sidenav-link" routerLink="/admin/reports" routerLinkActive="active"
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
        <button type="button" *ngIf="modules.on('RESTAURANT')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('restaurant')">
          <span class="sidenav-icon">🍽️</span>
          <span class="sidenav-label">Restaurant</span>
          <span class="sidenav-arrow">{{ isOpen('restaurant') ? '▲' : '▼' }}</span>
        </button>
        <ng-container *ngIf="isOpen('restaurant') && modules.on('RESTAURANT')">
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
          <a *ngIf="canSee('/admin/restaurant/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          <a *ngIf="canSee('/admin/restaurant/tables')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/tables" routerLinkActive="active"><span class="sidenav-icon">🪑</span> Tables</a>
          <a *ngIf="canSee('/admin/restaurant/menu')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/menu" routerLinkActive="active"><span class="sidenav-icon">📋</span> Menu</a>
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
          <a *ngIf="canSee('/admin/quotations')" class="sidenav-link sidenav-sublink" routerLink="/admin/quotations" routerLinkActive="active"><span class="sidenav-icon">💬</span> Quotations</a>
          <!-- Task: "Add Sale order... So that we can have POS & Sales" — a back-office order taken outside the register, entirely separate from POS checkout. -->
          <a *ngIf="canSee('/admin/sales-orders')" class="sidenav-link sidenav-sublink" routerLink="/admin/sales-orders" routerLinkActive="active"><span class="sidenav-icon">📝</span> Sale orders</a>
          <!-- Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" — store-wide ledger; processing itself happens from a sale order's own detail page. -->
          <a *ngIf="canSee('/admin/sales-order-returns')" class="sidenav-link sidenav-sublink" routerLink="/admin/sales-order-returns" routerLinkActive="active"><span class="sidenav-icon">↩️</span> Sale order returns</a>
          <!-- Fourier: "Add Invoice ... without touching warehousing" — formal documents generated from a sale order above; store-wide ledger, same convention as Sale order returns just above. -->
          <a *ngIf="canSee('/admin/invoices')" class="sidenav-link sidenav-sublink" routerLink="/admin/invoices" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Invoices</a>
          <!-- Fourier: "Add Recurring Invoice" — a saved template with real invoice line items that auto-generates a brand-new, already-ISSUED invoice on a schedule, placed right after Invoices. Deliberately its own standalone module, not folded into the Subscriptions group further down (Fourier, via AskUserQuestion). -->
          <a *ngIf="canSee('/admin/recurring-invoices')" class="sidenav-link sidenav-sublink" routerLink="/admin/recurring-invoices" routerLinkActive="active"><span class="sidenav-icon">🔄</span> Recurring invoices</a>
          <!-- Fourier: "Add credit note Menu (Void) Where we can printout them" — every voided invoice, store-wide, same convention as Invoices just above. -->
          <a *ngIf="canSee('/admin/credit-notes')" class="sidenav-link sidenav-sublink" routerLink="/admin/credit-notes" routerLinkActive="active"><span class="sidenav-icon">🧮</span> Credit notes</a>
          <a *ngIf="canSee('/admin/sales/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/sales/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          <!-- Fourier: "Every time invoice is ISSUED send try to send email to customer and to
               configured emails. (Add invoices receiver emails in sales settings)" — same
               "Settings" placement/icon convention as Help Desk's own group. -->
          <a *ngIf="canSee('/admin/sales/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/sales/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
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
          <a *ngIf="canSee('/admin/customers/statement')" class="sidenav-link sidenav-sublink" routerLink="/admin/customers/statement" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Customer statement</a>
          <a *ngIf="canSee('/admin/customer-payments')" class="sidenav-link sidenav-sublink" routerLink="/admin/customer-payments" routerLinkActive="active"><span class="sidenav-icon">💳</span> Customer payments</a>
        </ng-container>
        <!-- Purchases group (Fourier: "Lets have Purchases Menu after Customers menu and move
             Purchases requisitions, Purchases orders, Purchases return under it") — split out of
             the Inventory group below into its own top-level sidenav group, right after
             Customers; still ADMIN-only, same tier as before inside Inventory. -->
        <ng-container *ngIf="showGroup('purchases')">
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
            <!-- Purchases > Reports: purchases by supplier / product / category, bills, returns, supplier payments, payables aging. -->
            <a *ngIf="canSee('/admin/purchases/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/purchases/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          </ng-container>
        </ng-container>
        <!-- Supplier group (Fourier: "Lets have Supplier Menu after Purchases and move Suppliers,
             Supplier Statements, Supplier payments and it") — split out of the Inventory group
             below into its own top-level sidenav group, right after Purchases; still ADMIN-only,
             same tier as before inside Inventory. -->
        <ng-container *ngIf="showGroup('suppliers')">
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
        <ng-container *ngIf="auth.isAdmin() || auth.hasAnyArea()">
          <button type="button" *ngIf="showGroup('inventory')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('inventory')">
            <span class="sidenav-icon">📦</span>
            <span class="sidenav-label">Inventory</span>
            <span class="sidenav-arrow">{{ isOpen('inventory') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('inventory') && showGroup('inventory')">
            <a *ngIf="canSee('/admin/products')" class="sidenav-link sidenav-sublink" routerLink="/admin/products" routerLinkActive="active"><span class="sidenav-icon">📦</span> Products</a>
            <a *ngIf="canSee('/admin/categories')" class="sidenav-link sidenav-sublink" routerLink="/admin/categories" routerLinkActive="active"><span class="sidenav-icon">🏷️</span> Categories</a>
            <!-- Fourier: "Add product brands" -->
            <a *ngIf="canSee('/admin/brands')" class="sidenav-link sidenav-sublink" routerLink="/admin/brands" routerLinkActive="active"><span class="sidenav-icon">🏭</span> Brands</a>
            <!-- Suppliers/Purchase requisitions/Purchase orders/Purchase returns/Supplier payments
                 moved out into their own Purchases/Supplier top-level groups just above, per
                 Fourier's own later request. -->
            <a *ngIf="canSee('/admin/stock-adjustments')" class="sidenav-link sidenav-sublink" routerLink="/admin/stock-adjustments" routerLinkActive="active"><span class="sidenav-icon">🔧</span> Stock adjustments</a>
            <a *ngIf="canSee('/admin/stock-counts')" class="sidenav-link sidenav-sublink" routerLink="/admin/stock-counts" routerLinkActive="active"><span class="sidenav-icon">📋</span> Stock counts</a>
            <a *ngIf="canSee('/admin/reorder')" class="sidenav-link sidenav-sublink" routerLink="/admin/reorder" routerLinkActive="active"><span class="sidenav-icon">🔁</span> Reorder</a>
            <!-- Fourier: "multi-location/multi-warehouse tracking ... start on the foundation
                 phase" — foundation phase only, see WarehouseListComponent's own javadoc: nothing
                 else in the app lets you pick a warehouse yet. -->
            <!-- Fourier: "How business branches can be introduced?" — a branch owns one or more warehouses. -->
            <a *ngIf="canSee('/admin/branches')" class="sidenav-link sidenav-sublink" routerLink="/admin/branches" routerLinkActive="active"><span class="sidenav-icon">🏢</span> Branches</a>
            <a *ngIf="canSee('/admin/warehouses')" class="sidenav-link sidenav-sublink" routerLink="/admin/warehouses" routerLinkActive="active"><span class="sidenav-icon">🏬</span> Warehouses</a>
            <!-- Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers" — the first
                 thing that actually moves stock between warehouses; a transfer is logged PENDING
                 here and only takes effect once confirmed, same review step as Stock adjustments. -->
            <a *ngIf="canSee('/admin/stock-transfers')" class="sidenav-link sidenav-sublink" routerLink="/admin/stock-transfers" routerLinkActive="active"><span class="sidenav-icon">🔀</span> Stock transfers</a>
            <!-- Fourier: "Product Packaging & Breakdown / Unit Conversion ... Whiskey Breakdown &
                 Beer Breakdown ... If not activated ignore packaging" — rules are always
                 reachable here; the toggle (Inventory settings below) only gates actually running
                 a breakdown, not these links, same "configuration always visible" treatment the
                 Accounting module gives itself. -->
            <a *ngIf="canSee('/admin/packaging/rules')" class="sidenav-link sidenav-sublink" routerLink="/admin/packaging/rules" routerLinkActive="active"><span class="sidenav-icon">🍾</span> Packaging rules</a>
            <a *ngIf="canSee('/admin/packaging/breakdown')" class="sidenav-link sidenav-sublink" routerLink="/admin/packaging/breakdown" routerLinkActive="active"><span class="sidenav-icon">🥃</span> Breakdown stock</a>
            <!-- Task 2 (this pass): "Move Inventory Report as submenu to Inventory Menu" — was only reachable via the general Admin > Reports picker before. -->
            <a *ngIf="canSee('/admin/inventory/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/inventory/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
            <!-- Task 2: "Inventory Setting (Example: allow negative stock)" -->
            <a *ngIf="canSee('/admin/inventory/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/inventory/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Inventory settings</a>
          </ng-container>
          <!-- Manufacturing group (Fourier: "We are now good with POS and Sale Module ... Add
               Manufacturing Module" — scoped via his own list "1.Bill of Materials 2.Production
               plans 3.Manufacturing orders 4.Indirects Costs 5.Workstations 5.Settings (Production
               routings etc)"). Same accordion/admin-only treatment as every other group here. -->
          <button type="button" *ngIf="showGroup('manufacturing')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('manufacturing')">
            <span class="sidenav-icon">🏭</span>
            <span class="sidenav-label">Manufacturing</span>
            <span class="sidenav-arrow">{{ isOpen('manufacturing') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('manufacturing') && showGroup('manufacturing')">
            <a *ngIf="canSee('/admin/manufacturing/boms')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/boms" routerLinkActive="active"
              ><span class="sidenav-icon">📋</span> Bill of materials</a
            >
            <a *ngIf="canSee('/admin/manufacturing/production-plans')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/production-plans" routerLinkActive="active"
              ><span class="sidenav-icon">📈</span> Production plans</a
            >
            <a *ngIf="canSee('/admin/manufacturing/orders')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/orders" routerLinkActive="active"
              ><span class="sidenav-icon">🛠️</span> Manufacturing orders</a
            >
            <a *ngIf="canSee('/admin/manufacturing/workstations')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/workstations" routerLinkActive="active"
              ><span class="sidenav-icon">⚙️</span> Workstations</a
            >
            <a *ngIf="canSee('/admin/manufacturing/routings')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/routings" routerLinkActive="active"
              ><span class="sidenav-icon">🧭</span> Routings</a
            >
            <a *ngIf="canSee('/admin/manufacturing/overhead-costs')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/overhead-costs" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Indirect costs</a
            >
          </ng-container>
          <!-- Subscriptions group (Fourier: "Make subscriptions module as new Menu", then "Let
               subscriptions comes after Manufacturing") — split out of the Sales group into its
               own top-level sidenav group, placed right after Manufacturing; same accordion/
               admin-only treatment as every other group here. Customer subscriptions to a fixed,
               admin-defined plan; each billing date auto-generates an invoice (see backend
               SubscriptionService). -->
          <button type="button" *ngIf="showGroup('subscriptions')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('subscriptions')">
            <span class="sidenav-icon">🔁</span>
            <span class="sidenav-label">Subscriptions</span>
            <span class="sidenav-arrow">{{ isOpen('subscriptions') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('subscriptions') && showGroup('subscriptions')">
            <a *ngIf="canSee('/admin/subscription-plans')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscription-plans" routerLinkActive="active"><span class="sidenav-icon">📋</span> Subscription plans</a>
            <a *ngIf="canSee('/admin/subscriptions')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><span class="sidenav-icon">🔁</span> Subscriptions</a>
            <a *ngIf="canSee('/admin/subscriptions/job-settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions/job-settings" routerLinkActive="active"><span class="sidenav-icon">⏰</span> Job settings</a>
          </ng-container>
          <!-- Hotel group (Fourier: "add hotel management ... integrated with POS, Restaurant, Accounting"). Shown once the
               module is switched on (Settings > Modules, or Hotel > Settings). -->
          <button type="button" *ngIf="hotelApi.enabled() && showGroup('hotel')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('hotel')">
            <span class="sidenav-icon">🏨</span>
            <span class="sidenav-label">Hotel</span>
            <span class="sidenav-arrow">{{ isOpen('hotel') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('hotel') && hotelApi.enabled() && showGroup('hotel')">
            <ng-container *ngIf="hotelApi.enabled()">
              <a *ngIf="canSee('/admin/hotel/front-desk')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/front-desk" routerLinkActive="active"><span class="sidenav-icon">🛎️</span> Front desk</a>
              <a *ngIf="canSee('/admin/hotel/bookings')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/bookings" routerLinkActive="active"><span class="sidenav-icon">📖</span> Bookings</a>
              <a *ngIf="canSee('/admin/hotel/grid')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/grid" routerLinkActive="active"><span class="sidenav-icon">🗓️</span> Reservation chart</a>
              <a *ngIf="canSee('/admin/hotel/housekeeping')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/housekeeping" routerLinkActive="active"><span class="sidenav-icon">🧹</span> Housekeeping</a>
              <a *ngIf="canSee('/admin/hotel/maintenance')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/maintenance" routerLinkActive="active"><span class="sidenav-icon">🔧</span> Maintenance</a>
              <a *ngIf="canSee('/admin/hotel/guests')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/guests" routerLinkActive="active"><span class="sidenav-icon">🧳</span> Guests</a>
              <a *ngIf="canSee('/admin/hotel/rooms')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/rooms" routerLinkActive="active"><span class="sidenav-icon">🚪</span> Rooms</a>
              <a *ngIf="canSee('/admin/hotel/room-types')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/room-types" routerLinkActive="active"><span class="sidenav-icon">🛏️</span> Room types</a>
              <a *ngIf="canSee('/admin/hotel/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
            </ng-container>
            <a *ngIf="canSee('/admin/hotel/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
          </ng-container>
          <!-- Help Desk group (Fourier: "Add Help Desk module (with its menu) should manage
               customer and internal support requests from creation through resolution") — same
               top-level accordion group/admin-only treatment as every other group here, placed
               right after Subscriptions. One unified ticket list for both customer and internal
               requests (filter by type on the Tickets screen), plus the configurable Categories
               tickets route through. Guests submit/check a ticket with no login at all via the
               separate public /helpdesk page (see app.routes.ts), not through this admin menu. -->
          <button type="button" *ngIf="showGroup('helpdesk')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('helpdesk')">
            <span class="sidenav-icon">🎫</span>
            <span class="sidenav-label">Help Desk</span>
            <span class="sidenav-arrow">{{ isOpen('helpdesk') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('helpdesk') && showGroup('helpdesk')">
            <a *ngIf="canSee('/admin/helpdesk/tickets')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/tickets" routerLinkActive="active"><span class="sidenav-icon">🎫</span> Tickets</a>
            <a *ngIf="canSee('/admin/helpdesk/categories')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/categories" routerLinkActive="active"><span class="sidenav-icon">📋</span> Categories</a>
            <a *ngIf="canSee('/admin/helpdesk/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
            <a *ngIf="canSee('/admin/helpdesk/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
          </ng-container>
          <!-- Expenses group (Fourier: "I prefer to have the Expenses as module separate from
               Accounting (with it own Menu just before Accounting menu)") — split out of the
               Accounting group below into its own top-level sidenav group, same admin-only/
               accordion treatment, placed immediately above Accounting. Incomes stays put in
               Accounting for now; only Expenses and its Recurring-expenses templates moved. -->
          <button type="button" *ngIf="showGroup('expenses')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('expenses')">
            <span class="sidenav-icon">💸</span>
            <span class="sidenav-label">Expenses</span>
            <span class="sidenav-arrow">{{ isOpen('expenses') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('expenses') && showGroup('expenses')">
            <a *ngIf="canSee('/admin/accounting/expenses')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses" routerLinkActive="active"
              ><span class="sidenav-icon">💸</span> Expenses</a
            >
            <a *ngIf="canSee('/admin/accounting/recurring-expenses')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/recurring-expenses" routerLinkActive="active"
              ><span class="sidenav-icon">🔁</span> Recurring expenses</a
            >
          </ng-container>
          <!-- HR modules (Settings > Modules): Employees, Organizational Structure, Attendance & Leaves, Contracts, Payroll — placed just before Assets. -->
          <button type="button" *ngIf="showGroup('employees')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('employees')">
            <span class="sidenav-icon">👥</span>
            <span class="sidenav-label">Employees</span>
            <span class="sidenav-arrow">{{ isOpen('employees') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('employees') && showGroup('employees')">
            <a *ngIf="canSee('/admin/hr/employees')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/employees" routerLinkActive="active"
              ><span class="sidenav-icon">📋</span> Employees</a
            >
            <a *ngIf="canSee('/admin/hr/employees/new')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/employees/new" routerLinkActive="active"
              ><span class="sidenav-icon">➕</span> New employee</a
            >
            <a *ngIf="canSee('/admin/hr/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/reports" routerLinkActive="active"
              ><span class="sidenav-icon">📊</span> HR reports</a
            >
          </ng-container>
          <button type="button" *ngIf="showGroup('org')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('org')">
            <span class="sidenav-icon">🧭</span>
            <span class="sidenav-label">Organization</span>
            <span class="sidenav-arrow">{{ isOpen('org') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('org') && showGroup('org')">
            <a *ngIf="canSee('/admin/hr/org/setup')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/org/setup" routerLinkActive="active"
              ><span class="sidenav-icon">🏢</span> Departments & positions</a
            >
            <a *ngIf="canSee('/admin/hr/org/chart')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/org/chart" routerLinkActive="active"
              ><span class="sidenav-icon">🌳</span> Organization chart</a
            >
          </ng-container>
          <button type="button" *ngIf="showGroup('attendance')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('attendance')">
            <span class="sidenav-icon">🕒</span>
            <span class="sidenav-label">Attendance & Leaves</span>
            <span class="sidenav-arrow">{{ isOpen('attendance') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('attendance') && showGroup('attendance')">
            <a *ngIf="canSee('/admin/hr/attendance')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/attendance" routerLinkActive="active"
              ><span class="sidenav-icon">⏱️</span> Attendance</a
            >
            <a *ngIf="canSee('/admin/hr/leaves')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/leaves" routerLinkActive="active"
              ><span class="sidenav-icon">🏖️</span> Leave</a
            >
            <a *ngIf="canSee('/admin/hr/leave-settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/leave-settings" routerLinkActive="active"
              ><span class="sidenav-icon">⚙️</span> Settings</a
            >
          </ng-container>
          <button type="button" *ngIf="showGroup('contracts')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('contracts')">
            <span class="sidenav-icon">📄</span>
            <span class="sidenav-label">Contracts</span>
            <span class="sidenav-arrow">{{ isOpen('contracts') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('contracts') && showGroup('contracts')">
            <a *ngIf="canSee('/admin/hr/contracts')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/contracts" routerLinkActive="active"
              ><span class="sidenav-icon">📄</span> Contracts</a
            >
            <a *ngIf="canSee('/admin/hr/contracts/new')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/contracts/new" routerLinkActive="active"
              ><span class="sidenav-icon">➕</span> New contract</a
            >
          </ng-container>
          <button type="button" *ngIf="showGroup('payroll')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('payroll')">
            <span class="sidenav-icon">💰</span>
            <span class="sidenav-label">Payroll</span>
            <span class="sidenav-arrow">{{ isOpen('payroll') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('payroll') && showGroup('payroll')">
            <a *ngIf="canSee('/admin/hr/payroll/runs')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/payroll/runs" routerLinkActive="active"
              ><span class="sidenav-icon">🧾</span> Payroll runs</a
            >
            <a *ngIf="canSee('/admin/hr/payroll/rules')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/payroll/rules" routerLinkActive="active"
              ><span class="sidenav-icon">📐</span> Rules & tax bands</a
            >
          </ng-container>
          <!-- Installments group: optional module (Settings > Modules), placed just before Assets. -->
          <button type="button" *ngIf="showGroup('installments')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('installments')">
            <span class="sidenav-icon">🗓️</span>
            <span class="sidenav-label">Installments</span>
            <span class="sidenav-arrow">{{ isOpen('installments') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('installments') && showGroup('installments')">
            <a *ngIf="canSee('/admin/installments')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
              ><span class="sidenav-icon">📋</span> Agreements</a
            >
            <a *ngIf="canSee('/admin/installments/new')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments/new" routerLinkActive="active"
              ><span class="sidenav-icon">➕</span> New plan</a
            >
            <a *ngIf="canSee('/admin/installments/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments/reports" routerLinkActive="active"
              ><span class="sidenav-icon">📊</span> Reports</a
            >
            <a *ngIf="canSee('/admin/installments/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments/settings" routerLinkActive="active"
              ><span class="sidenav-icon">⚙️</span> Settings</a
            >
          </ng-container>
          <!-- Assets Management group: optional module (Settings > Modules), placed just before Accounting. -->
          <button type="button" *ngIf="showGroup('assets')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('assets')">
            <span class="sidenav-icon">🏗️</span>
            <span class="sidenav-label">Assets</span>
            <span class="sidenav-arrow">{{ isOpen('assets') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('assets') && showGroup('assets')">
            <a *ngIf="canSee('/admin/assets')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
              ><span class="sidenav-icon">📋</span> Asset register</a
            >
            <a *ngIf="canSee('/admin/assets/new')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/new" routerLinkActive="active"
              ><span class="sidenav-icon">➕</span> New asset</a
            >
            <a *ngIf="canSee('/admin/assets/from-purchase')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/from-purchase" routerLinkActive="active"
              ><span class="sidenav-icon">📥</span> From purchases</a
            >
            <a *ngIf="canSee('/admin/assets/categories')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/categories" routerLinkActive="active"
              ><span class="sidenav-icon">🗂️</span> Categories</a
            >
            <a *ngIf="canSee('/admin/assets/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/reports" routerLinkActive="active"
              ><span class="sidenav-icon">📊</span> Reports</a
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
          <button type="button" *ngIf="showGroup('accounting')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('accounting')">
            <span class="sidenav-icon">📒</span>
            <span class="sidenav-label">Accounting</span>
            <span class="sidenav-arrow">{{ isOpen('accounting') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('accounting') && showGroup('accounting')">
            <a *ngIf="canSee('/admin/accounting/accounts')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/accounts" routerLinkActive="active"
              ><span class="sidenav-icon">📋</span> Chart of accounts</a
            >
            <a *ngIf="canSee('/admin/accounting/cost-centers')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/cost-centers" routerLinkActive="active"
              ><span class="sidenav-icon">🏷️</span> Cost centers</a
            >
            <a *ngIf="canSee('/admin/accounting/journal-entries')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/journal-entries" routerLinkActive="active"
              ><span class="sidenav-icon">📝</span> Journal entries</a
            >
            <a *ngIf="canSee('/admin/accounting/incomes')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/incomes" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Incomes</a
            >
            <!-- Report Menu — single entry point for all 24 accounting reports (Accounting
                 Reports: Tax Report, Tax Declaration, Income Statement, Balance Sheet,
                 Profit & Loss, Financial Transactions, Cash Flow Report, Assets; plus Journals
                 Reports / Expenses Reports / Incomes Reports) — one link to
                 AccountingReportMenuComponent's own grouped picker screen. -->
            <a *ngIf="canSee('/admin/accounting/reports/menu')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/reports/menu" routerLinkActive="active"
              ><span class="sidenav-icon">📚</span> Reports</a
            >
          </ng-container>
          <!-- Users group (Task: "Move Staff menu to new Menu called Users") — same grouping treatment as Customers just above; "Staff" itself keeps its existing label and route. Fourier: "Move Users and Settings menus after Accounting Menu" — was directly after Inventory; both this and Settings below now sit after Accounting instead, order otherwise unchanged. -->
          <button type="button" *ngIf="showGroup('users')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('users')">
            <span class="sidenav-icon">🧑‍💼</span>
            <span class="sidenav-label">Users</span>
            <span class="sidenav-arrow">{{ isOpen('users') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('users') && showGroup('users')">
            <a *ngIf="canSee('/admin/staff')" class="sidenav-link sidenav-sublink" routerLink="/admin/staff" routerLinkActive="active"><span class="sidenav-icon">🧑‍💼</span> Staff</a>
            <a class="sidenav-link sidenav-sublink" routerLink="/admin/staff/roles" routerLinkActive="active"><span class="sidenav-icon">🛡️</span> Roles</a>
          </ng-container>
          <!-- Settings groups the "how the store is configured" screens (Task: "Move Payment Methods menu under new menu called Settings" / "Add taxes menu under Settings Menu"). -->
          <button type="button" *ngIf="showGroup('settings')" class="sidenav-link sidenav-toggle" (click)="toggleGroup('settings')">
            <span class="sidenav-icon">⚙️</span>
            <span class="sidenav-label">Settings</span>
            <span class="sidenav-arrow">{{ isOpen('settings') ? '▲' : '▼' }}</span>
          </button>
          <ng-container *ngIf="isOpen('settings') && showGroup('settings')">
            <a class="sidenav-link sidenav-sublink" routerLink="/settings/appearance" routerLinkActive="active"><span class="sidenav-icon">🎨</span> Appearance</a>
            <a *ngIf="canSee('/admin/settings/modules')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/modules" routerLinkActive="active"><span class="sidenav-icon">🧩</span> Modules</a>
            <a *ngIf="canSee('/admin/payment-methods')" class="sidenav-link sidenav-sublink" routerLink="/admin/payment-methods" routerLinkActive="active"
              ><span class="sidenav-icon">💳</span> Payment methods</a
            >
            <a *ngIf="canSee('/admin/settings/taxes')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/taxes" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Taxes</a
            >
            <!-- Task: "Under Settings menu add Business Details menu" -->
            <a *ngIf="canSee('/admin/settings/business')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/business" routerLinkActive="active"
              ><span class="sidenav-icon">🏢</span> Business details</a
            >
            <!-- Task: "Add System setting like currency, time zone, date format, etc" -->
            <a *ngIf="canSee('/admin/settings/system')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/system" routerLinkActive="active"
              ><span class="sidenav-icon">🌐</span> System settings</a
            >
            <!-- Fourier: "Configure smtp for sending email" -->
            <a *ngIf="canSee('/admin/settings/email')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/email" routerLinkActive="active"
              ><span class="sidenav-icon">✉️</span> Email settings</a
            >
            <!-- Emailed Excel/PDF reports on a schedule -->
            <a *ngIf="canSee('/admin/settings/report-schedules')" class="sidenav-link sidenav-sublink" routerLink="/admin/settings/report-schedules" routerLinkActive="active"
              ><span class="sidenav-icon">🗓️</span> Scheduled reports</a
            >
            <!-- Task: "This will work as a plugin (or apps) to be installed and configured" -->
            <a *ngIf="canSee('/admin/apps')" class="sidenav-link sidenav-sublink" routerLink="/admin/apps" routerLinkActive="active"
              ><span class="sidenav-icon">🧩</span> Apps</a
            >
            <!-- Task: "Implement an audit log of who did what and when" -->
            <a *ngIf="canSee('/admin/audit-log')" class="sidenav-link sidenav-sublink" routerLink="/admin/audit-log" routerLinkActive="active"
              ><span class="sidenav-icon">🕵️</span> Audit Log</a
            >
            <!-- Fourier: "no backup/restore or bulk import-export anywhere in the backend" — DB backup/restore, CSV import, and export/year-end archive, all on one screen. -->
            <a *ngIf="canSee('/admin/data')" class="sidenav-link sidenav-sublink" routerLink="/admin/data" routerLinkActive="active"
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
    private sidenavState: SidenavStateService,
    public hotelApi: HotelApiService,
    public modules: ModuleApiService
  ) {}

  ngOnInit(): void {
    // Hotel module: whether to show the Hotel group (a signal shared by every page, loaded once).
    this.hotelApi.ensureStateLoaded();
    // Settings > Modules: which optional modules are on.
    this.modules.ensureLoaded();
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

  /** Custom roles: may this user open the given path? (Admins always; others per their role's areas — see core/access.ts.) */
  canSee(path: string): boolean {
    return this.auth.canAccessPath(path);
  }

  /** The representative screens of each sidebar group — the group shows when at least one of them is reachable. */
  private static readonly GROUP_PATHS: Record<string, string[]> = {
    purchases: ['/admin/purchase-requisitions', '/admin/purchase-orders', '/admin/purchase-returns', '/admin/bills', '/admin/refunds', '/admin/purchases/reports'],
    suppliers: ['/admin/suppliers', '/admin/supplier-prices', '/admin/supplier-payments'],
    inventory: ['/admin/products', '/admin/categories', '/admin/brands', '/admin/stock-adjustments', '/admin/stock-counts', '/admin/reorder', '/admin/warehouses', '/admin/stock-transfers', '/admin/packaging/rules', '/admin/inventory/reports'],
    manufacturing: ['/admin/manufacturing/boms'],
    subscriptions: ['/admin/subscription-plans', '/admin/subscriptions'],
    helpdesk: ['/admin/helpdesk/tickets', '/admin/helpdesk/categories', '/admin/helpdesk/reports'],
    hotel: ['/admin/hotel/front-desk', '/admin/hotel/bookings', '/admin/hotel/rooms', '/admin/hotel/housekeeping'],
    expenses: ['/admin/accounting/expenses', '/admin/accounting/recurring-expenses'],
    assets: ['/admin/assets', '/admin/assets/categories', '/admin/assets/reports'],
    installments: ['/admin/installments', '/admin/installments/reports'],
    employees: ['/admin/hr/employees', '/admin/hr/reports'],
    org: ['/admin/hr/org/setup'],
    attendance: ['/admin/hr/attendance', '/admin/hr/leaves'],
    contracts: ['/admin/hr/contracts'],
    payroll: ['/admin/hr/payroll/runs'],
    accounting: ['/admin/accounting/accounts', '/admin/accounting/reports/menu'],
    users: [],
    settings: [],
  };

  /** Sidebar groups that belong to an optional module (Settings > Modules). */
  private static readonly GROUP_MODULES: Record<string, ModuleKey> = {
    manufacturing: 'MANUFACTURING',
    subscriptions: 'SUBSCRIPTIONS',
    helpdesk: 'HELPDESK',
    expenses: 'EXPENSES',
    assets: 'ASSETS',
    installments: 'INSTALLMENTS',
    employees: 'EMPLOYEES',
    org: 'ORG_STRUCTURE',
    attendance: 'ATTENDANCE',
    contracts: 'CONTRACTS',
    payroll: 'PAYROLL',
    hotel: 'HOTEL',
  };

  showGroup(group: string): boolean {
    const module = ShellComponent.GROUP_MODULES[group];
    if (module && !this.modules.on(module)) return false;
    if (this.auth.isAdmin()) return true;
    const paths = ShellComponent.GROUP_PATHS[group] ?? [];
    return paths.some((p) => this.auth.canAccessPath(p));
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
