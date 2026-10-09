import { Injectable, signal } from '@angular/core';

/** Which admin group a sidenav route belongs to — see SidenavStateService.defaultGroupForUrl. */
const GROUP_ROUTE_PREFIXES: { group: string; prefixes: string[] }[] = [
  { group: 'pos', prefixes: ['/registers', '/sessions', '/returns', '/receipts', '/admin/shifts', '/admin/reports'] },
  // 'customers' renamed to 'sales' (Task: "Then let have Sale as Menu and Move Customers into
  // Sales") — '/admin/sales' covers /admin/sales-orders and /admin/sales/reports in one prefix,
  // alongside the customer-facing screens that moved into this group.
  { group: 'sales', prefixes: ['/admin/customers', '/admin/customer-payments', '/admin/sales'] },
  {
    group: 'inventory',
    // '/admin/inventory' covers both /admin/inventory/reports (Task 2, this pass) and
    // /admin/inventory/settings (Task 2, previous pass) in one prefix.
    prefixes: ['/admin/products', '/admin/categories', '/admin/suppliers', '/admin/purchase-orders', '/admin/supplier-payments', '/admin/stock-adjustments', '/admin/inventory'],
  },
  // Expenses split out of Accounting into its own top-level group (Fourier: "I prefer to have
  // the Expenses as module separate from Accounting").
  { group: 'expenses', prefixes: ['/admin/accounting/expenses', '/admin/accounting/recurring-expenses'] },
  { group: 'users', prefixes: ['/admin/staff'] },
  {
    group: 'settings',
    prefixes: ['/admin/payment-methods', '/admin/settings', '/admin/apps', '/admin/audit-log'],
  },
];

/**
 * Bug: "When I click on Inventory => Products, after loading product, POS Menu open its submenu
 * instead of keeping Inventory submenu opened." Root cause: ShellComponent isn't a persistent
 * layout wrapping a `<router-outlet>` — every routed page embeds its own `<app-shell>...
 * </app-shell>` in its own template (see AppComponent, which is just `<router-outlet>`), so
 * navigating to a *different* route destroys the old page (and its embedded ShellComponent
 * instance) and constructs a brand new one. `openGroup` used to live as a plain field on
 * ShellComponent itself, so every single navigation — not just Inventory → Products, any group to
 * any other page — silently reset the accordion back to its hardcoded 'pos' default instead of
 * remembering what the person had open. Moving that one piece of UI state into this
 * `providedIn: 'root'` service (a true singleton, constructed once for the app's lifetime, not
 * once per ShellComponent instance) is what makes it survive the page component underneath it
 * being torn down and rebuilt on every navigation.
 */
@Injectable({ providedIn: 'root' })
export class SidenavStateService {
  /** At most one group open at a time (Task: "Once one menu is open other should be closed") — defaults to whichever group the very first page of the session belongs to, 'pos' if that page isn't in any group (e.g. a fresh login always lands outside any group). */
  readonly openGroup = signal<string | null>(this.defaultGroupForUrl(typeof window !== 'undefined' ? window.location.pathname : ''));

  isOpen(group: string): boolean {
    return this.openGroup() === group;
  }

  toggle(group: string): void {
    this.openGroup.set(this.openGroup() === group ? null : group);
  }

  /**
   * Only used once, at service-construction time (see the `openGroup` initializer above) — a
   * best-effort "open the right group already" for the very first page the session lands on
   * (e.g. a bookmark straight into /admin/suppliers, or a hard refresh). Every navigation after
   * that is driven entirely by what the person clicks (toggle above), same as before this fix;
   * this deliberately doesn't re-run per navigation, since a person expanding Settings to peek at
   * something while still conceptually "in" Inventory shouldn't get silently snapped back the
   * moment they click a Settings link.
   */
  private defaultGroupForUrl(pathname: string): string {
    const match = GROUP_ROUTE_PREFIXES.find((entry) => entry.prefixes.some((prefix) => pathname.startsWith(prefix)));
    return match?.group ?? 'pos';
  }
}
