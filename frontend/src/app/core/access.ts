/**
 * Custom roles: which "access area" each admin-guarded screen belongs to. One table, used by the route guard
 * (adminGuard) and by the sidebar (ShellComponent.canSee), so the menu and the URL can never disagree. It mirrors
 * the @PreAuthorize rules on the backend controllers — the backend is still the real lock; this only decides what
 * to show and where to redirect.
 *
 * An ADMIN passes everything. For anyone else a path is allowed when their custom role grants at least VIEW on one
 * of the listed areas; paths that create or change something ("new", "edit", "record-payment", "open-session")
 * need MANAGE. A path matching no rule stays admin-only.
 */
export type AreaCode = 'SALES' | 'BILLING' | 'INVENTORY' | 'OPERATIONS' | 'ACCOUNTING' | 'HOTEL' | 'HR' | 'PAYROLL' | 'REPORTS';

export type AreaRequirement = AreaCode[] | 'ADMIN';

interface AccessRule {
  prefix?: string;
  pattern?: RegExp;
  areas: AreaRequirement;
}

const RULES: AccessRule[] = [
  // Admin-only settings screens that live under an otherwise shared prefix — listed first so they win.
  { prefix: 'admin/inventory/settings', areas: 'ADMIN' },
  { prefix: 'admin/helpdesk/settings', areas: 'ADMIN' },
  { prefix: 'admin/subscriptions/job-settings', areas: 'ADMIN' },
  { prefix: 'admin/installments/settings', areas: 'ADMIN' },
  { prefix: 'admin/sales/settings', areas: 'ADMIN' },
  { prefix: 'admin/hotel/settings', areas: 'ADMIN' },

  // Recording a payment is billing work even when it starts from a customer or supplier page.
  { pattern: /^admin\/suppliers\/[^/]+\/record-payment$/, areas: ['BILLING'] },
  { pattern: /^admin\/customers\/[^/]+\/record-payment$/, areas: ['SALES', 'BILLING'] },

  // Reports and dashboard.
  { prefix: 'admin/accounting/reports', areas: ['REPORTS', 'ACCOUNTING'] },
  { prefix: 'admin/helpdesk/reports', areas: ['REPORTS', 'OPERATIONS'] },
  { prefix: 'admin/dashboard', areas: ['REPORTS'] },
  { prefix: 'admin/reports', areas: ['REPORTS'] },
  { prefix: 'admin/sales/reports', areas: ['REPORTS'] },
  { prefix: 'admin/purchases/reports', areas: ['REPORTS'] },
  { prefix: 'admin/assets/reports', areas: ['REPORTS', 'ACCOUNTING'] },
  { prefix: 'admin/installments/reports', areas: ['REPORTS', 'BILLING'] },
  { prefix: 'admin/inventory/reports', areas: ['REPORTS'] },
  { prefix: 'admin/restaurant/reports', areas: ['REPORTS'] },
  { prefix: 'admin/hotel/reports', areas: ['REPORTS', 'HOTEL'] },

  // The first-run chart-of-accounts wizard: it writes, so it needs MANAGE (see WRITE_SEGMENTS).
  { prefix: 'setup/chart-country', areas: ['ACCOUNTING'] },

  // Accounting (recurring expenses are billing documents).
  { prefix: 'admin/accounting/recurring-expenses', areas: ['BILLING'] },
  { prefix: 'admin/accounting', areas: ['ACCOUNTING'] },

  // Billing and payments.
  { prefix: 'admin/bills', areas: ['BILLING'] },
  { prefix: 'admin/invoices', areas: ['BILLING'] },
  { prefix: 'admin/recurring-invoices', areas: ['BILLING'] },
  { prefix: 'admin/refunds', areas: ['BILLING'] },
  { prefix: 'admin/credit-notes', areas: ['BILLING'] },
  { prefix: 'admin/installments', areas: ['BILLING'] },
  { prefix: 'admin/supplier-payments', areas: ['BILLING'] },
  { prefix: 'admin/customer-payments', areas: ['SALES', 'BILLING'] },

  // Sales and customers.
  { prefix: 'admin/customers', areas: ['SALES'] },
  { prefix: 'admin/sales-orders', areas: ['SALES'] },
  { prefix: 'admin/sales-order-returns', areas: ['SALES'] },
  { prefix: 'admin/quotations', areas: ['SALES'] },
  { prefix: 'admin/shifts', areas: ['SALES'] },
  { prefix: 'registers', areas: ['SALES'] },

  // Inventory and purchasing.
  { prefix: 'admin/products', areas: ['INVENTORY'] },
  { prefix: 'admin/categories', areas: ['INVENTORY'] },
  { prefix: 'admin/brands', areas: ['INVENTORY'] },
  { prefix: 'admin/warehouses', areas: ['INVENTORY'] },
  { prefix: 'admin/suppliers', areas: ['INVENTORY'] },
  { prefix: 'admin/supplier-prices', areas: ['INVENTORY'] },
  { prefix: 'admin/purchase-requisitions', areas: ['INVENTORY'] },
  { prefix: 'admin/purchase-orders', areas: ['INVENTORY'] },
  { prefix: 'admin/purchase-returns', areas: ['INVENTORY'] },
  { prefix: 'admin/stock-adjustments', areas: ['INVENTORY'] },
  { prefix: 'admin/stock-counts', areas: ['INVENTORY'] },
  { prefix: 'admin/stock-transfers', areas: ['INVENTORY'] },
  { prefix: 'admin/reorder', areas: ['INVENTORY'] },
  { prefix: 'admin/packaging', areas: ['INVENTORY'] },

  // Assets Management (fixed assets post to Accounting).
  { prefix: 'admin/assets', areas: ['ACCOUNTING'] },

  // HR modules. Contracts and payroll carry salary data, so they have their own area; reports are open to the HR and Reports areas
  // (the salary reports are locked down again on the backend).
  { prefix: 'admin/hr/reports', areas: ['REPORTS', 'HR', 'PAYROLL'] },
  { prefix: 'admin/hr/contracts', areas: ['PAYROLL'] },
  { prefix: 'admin/hr/payroll', areas: ['PAYROLL'] },
  { prefix: 'admin/hr', areas: ['HR'] },

  // Operations modules.
  { prefix: 'admin/manufacturing', areas: ['OPERATIONS'] },
  { prefix: 'admin/restaurant', areas: ['OPERATIONS'] },
  { prefix: 'admin/hotel', areas: ['HOTEL'] },
  { prefix: 'admin/subscription-plans', areas: ['OPERATIONS'] },
  { prefix: 'admin/subscriptions', areas: ['OPERATIONS'] },
  { prefix: 'admin/helpdesk', areas: ['OPERATIONS'] },
];

const WRITE_SEGMENTS = new Set(['new', 'edit', 'record-payment', 'open-session', 'chart-country', 'walk-in', 'from-purchase']);

function normalise(path: string): string {
  const noQuery = path.split('?')[0].split('#')[0];
  return noQuery.replace(/^\/+/, '').replace(/\/+$/, '');
}

/** The areas a path needs (any one of them is enough), or 'ADMIN' when only an admin may open it. */
export function requiredAreas(path: string): AreaRequirement {
  const p = normalise(path);
  for (const rule of RULES) {
    if (rule.pattern ? rule.pattern.test(p) : p === rule.prefix || p.startsWith(rule.prefix + '/')) {
      return rule.areas;
    }
  }
  return 'ADMIN';
}

/** True when opening this path changes data, so VIEW access is not enough. */
export function needsManage(path: string): boolean {
  return normalise(path).split('/').some((segment) => WRITE_SEGMENTS.has(segment));
}

/** Where a signed-in non-admin with these areas should land after login (first match wins). */
export function landingFor(areas: string[], posOn = true): string {
  const manages = (a: AreaCode) => areas.includes(a + '_MANAGE');
  const views = (a: AreaCode) => areas.includes(a + '_VIEW') || manages(a);
  if (manages('SALES')) return posOn ? '/registers' : '/admin/sales-orders';
  if (views('REPORTS')) return '/admin/dashboard';
  if (views('ACCOUNTING')) return '/admin/accounting/accounts';
  if (views('INVENTORY')) return '/admin/products';
  if (views('BILLING')) return '/admin/invoices';
  if (views('HOTEL')) return '/admin/hotel/front-desk';
  if (views('HR')) return '/admin/hr/employees';
  if (views('PAYROLL')) return '/admin/hr/payroll/runs';
  if (views('OPERATIONS')) return '/admin/helpdesk/tickets';
  if (views('SALES')) return '/admin/customers';
  return posOn ? '/sessions' : '/settings/appearance';
}
