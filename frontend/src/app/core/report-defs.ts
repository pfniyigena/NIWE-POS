/**
 * Config-driven definitions for the POS Reports (Task: "Add POS Report where it will show the
 * list of the following reports ... by click on each report it will open report with different
 * filters and export as EXCEL,PDF" — grew well past the original seven once the Credit reports,
 * and later Task 1's two Inventory reports, were added). One generic ReportViewerComponent
 * renders every report from this table rather than a near-identical component per report — each
 * entry says which backend endpoint to call, which columns to render (and how to format each
 * one), and which filters apply, mirroring the backend's ReportController exactly (see its class
 * javadoc).
 */
export type ReportColumnFormat = 'text' | 'int' | 'money' | 'percent' | 'date' | 'hash';

export interface ReportColumn {
  key: string;
  label: string;
  format: ReportColumnFormat;
}

export interface ReportDef {
  key: string;
  title: string;
  section: 'POS Sales-Transactions' | 'POS Profit' | 'POS Credit' | 'Inventory' | 'Sales' | 'Restaurant' | 'Help Desk';
  /** Path segment under /api/reports/ — see ReportController. */
  endpoint: string;
  columns: ReportColumn[];
  /** Whether this report's filters include a Category picker, alongside the always-present date range + register. */
  categoryFilter: boolean;
  /** Fourier: "can we have Stock Movement History for a specific product" — whether this report's filters include a searchable Product picker (own filter, independent of categoryFilter above). Only Stock Movement History sets this. */
  productFilter?: boolean;
  /** Multi-warehouse phase 1 — whether this report's filters include a Warehouse picker (only shown once the store has more than one warehouse). Only Stock On Hand & Valuation sets this. */
  warehouseFilter?: boolean;
  /** Fourier: "How business branches can be introduced?" — whether this report's filters include a Branch picker (only shown once the store has more than one active branch). The POS sales/profit reports and Stock Movement History set this. */
  branchFilter?: boolean;
  /**
   * How this report's filter bar renders (Task family: "Outstanding credit report" / "Overdue
   * credit report" / "Daily credit collection report" — three of the four new credit reports
   * don't fit the seven original reports' always-date-range-plus-register shape). Undefined
   * (every original report) means today's behavior: date range + register picker. 'dateOnly'
   * keeps the date range but drops the register picker (credit sales aren't tied to a register).
   * 'none' drops the whole date-range block — these two reports are snapshots of the current
   * outstanding/overdue state, not scoped to a period at all.
   */
  filterMode?: 'dateRegister' | 'dateOnly' | 'none';
}

/**
 * Task: "all link to back report should go to the parent not to the POS reports" — ReportViewerComponent
 * is one shared screen for every report regardless of which picker linked to it (ReportListComponent at
 * /admin/reports, InventoryReportListComponent at /admin/inventory/reports, or SalesReportListComponent
 * at /admin/sales/reports), but its "back" link used to be hardcoded to /admin/reports no matter which
 * picker actually owns the report. This maps a ReportDef's own `section` to that owning picker's route so
 * the back link (and the picker's own display name) can be derived instead of assumed.
 */
export function reportListRouteForSection(section: ReportDef['section']): string {
  switch (section) {
    case 'Inventory':
      return '/admin/inventory/reports';
    case 'Sales':
      return '/admin/sales/reports';
    case 'Restaurant':
      return '/admin/restaurant/reports';
    case 'Help Desk':
      return '/admin/helpdesk/reports';
    default:
      return '/admin/reports';
  }
}

export const REPORT_DEFS: ReportDef[] = [
  {
    key: 'category-sales',
    title: 'Total POS Categories Sales',
    section: 'POS Sales-Transactions',
    endpoint: 'category-sales',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  // Fourier: "Add also report related to brand" — mirrors category-sales exactly, one level over.
  {
    key: 'brand-sales',
    title: 'Total POS Brands Sales',
    section: 'POS Sales-Transactions',
    endpoint: 'brand-sales',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'brandName', label: 'Brand', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'product-sales',
    title: 'Total POS Products Sales',
    section: 'POS Sales-Transactions',
    endpoint: 'product-sales',
    branchFilter: true,
    categoryFilter: true,
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'shift-sales',
    title: 'POS Shifts Sales',
    section: 'POS Sales-Transactions',
    endpoint: 'shift-sales',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'shiftId', label: 'Shift', format: 'hash' },
      { key: 'registerName', label: 'Register', format: 'text' },
      { key: 'openedByName', label: 'Opened by', format: 'text' },
      { key: 'openedAt', label: 'Opened at', format: 'date' },
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'saleCount', label: 'Sales', format: 'int' },
      { key: 'subtotal', label: 'Subtotal', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'shift-transactions',
    title: 'Detailed POS Shifts Transactions',
    section: 'POS Sales-Transactions',
    endpoint: 'shift-transactions',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'invoiceNumber', label: 'Invoice', format: 'text' },
      { key: 'shiftId', label: 'Shift', format: 'hash' },
      { key: 'registerName', label: 'Register', format: 'text' },
      { key: 'cashierName', label: 'Cashier', format: 'text' },
      { key: 'customerName', label: 'Customer', format: 'text' },
      { key: 'completedAt', label: 'Completed at', format: 'date' },
      { key: 'itemCount', label: 'Items', format: 'int' },
      { key: 'subtotal', label: 'Subtotal', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
      { key: 'paymentMethods', label: 'Payment', format: 'text' },
    ],
  },
  {
    key: 'shift-profit',
    title: 'POS Shifts Profit',
    section: 'POS Profit',
    endpoint: 'shift-profit',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'shiftId', label: 'Shift', format: 'hash' },
      { key: 'registerName', label: 'Register', format: 'text' },
      { key: 'openedByName', label: 'Opened by', format: 'text' },
      { key: 'openedAt', label: 'Opened at', format: 'date' },
      { key: 'saleCount', label: 'Sales', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  {
    key: 'category-profit',
    title: 'Total POS Categories Profit',
    section: 'POS Profit',
    endpoint: 'category-profit',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  // Fourier: "Add also report related to brand" — mirrors category-profit exactly, one level over.
  {
    key: 'brand-profit',
    title: 'Total POS Brands Profit',
    section: 'POS Profit',
    endpoint: 'brand-profit',
    branchFilter: true,
    categoryFilter: false,
    columns: [
      { key: 'brandName', label: 'Brand', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  {
    key: 'product-profit',
    title: 'Total POS Products Profit',
    section: 'POS Profit',
    endpoint: 'product-profit',
    branchFilter: true,
    categoryFilter: true,
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  {
    key: 'credit-sales',
    title: 'Credit Sale Report',
    section: 'POS Credit',
    endpoint: 'credit-sales',
    branchFilter: true,
    categoryFilter: false,
    filterMode: 'dateOnly',
    columns: [
      { key: 'invoiceNumber', label: 'Invoice', format: 'text' },
      { key: 'completedAt', label: 'Date', format: 'date' },
      { key: 'customerName', label: 'Customer', format: 'text' },
      { key: 'creditAmount', label: 'Credit amount', format: 'money' },
      { key: 'amountOutstanding', label: 'Outstanding', format: 'money' },
      { key: 'dueDate', label: 'Due date', format: 'text' },
    ],
  },
  {
    key: 'daily-credit-collection',
    title: 'Daily Credit Collection Report',
    section: 'POS Credit',
    endpoint: 'daily-credit-collection',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'date', label: 'Date', format: 'text' },
      { key: 'paymentCount', label: 'Payments', format: 'int' },
      { key: 'totalCollected', label: 'Total collected', format: 'money' },
    ],
  },
  {
    key: 'outstanding-credit',
    title: 'Outstanding Credit Report',
    section: 'POS Credit',
    endpoint: 'outstanding-credit',
    categoryFilter: false,
    filterMode: 'none',
    branchFilter: true,
    columns: [
      { key: 'customerName', label: 'Customer', format: 'text' },
      { key: 'phone', label: 'Phone', format: 'text' },
      { key: 'creditLimit', label: 'Credit limit', format: 'money' },
      { key: 'outstandingBalance', label: 'Outstanding', format: 'money' },
      { key: 'availableCredit', label: 'Available credit', format: 'money' },
    ],
  },
  {
    key: 'overdue-credit',
    title: 'Overdue Credit Report',
    section: 'POS Credit',
    endpoint: 'overdue-credit',
    categoryFilter: false,
    filterMode: 'none',
    branchFilter: true,
    columns: [
      { key: 'invoiceNumber', label: 'Invoice', format: 'text' },
      { key: 'customerName', label: 'Customer', format: 'text' },
      { key: 'phone', label: 'Phone', format: 'text' },
      { key: 'dueDate', label: 'Due date', format: 'text' },
      { key: 'daysOverdue', label: 'Days overdue', format: 'int' },
      { key: 'creditAmount', label: 'Credit amount', format: 'money' },
      { key: 'amountOutstanding', label: 'Outstanding', format: 'money' },
    ],
  },
  // Task 1: "Inventory Report" — the user's "Both" answer to what it should show. Reuses this same
  // REPORT_DEFS/ReportViewerComponent framework rather than bespoke components: both new reports
  // are flat per-row tables that fit the existing generic table exactly (see ReportController's
  // javadoc for the backend side).
  {
    key: 'stock-valuation',
    title: 'Stock On Hand & Valuation',
    section: 'Inventory',
    endpoint: 'stock-valuation',
    branchFilter: true,
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'stockQuantity', label: 'Stock qty', format: 'int' },
      { key: 'reserved', label: 'Reserved', format: 'int' },
      { key: 'inTransit', label: 'In transit', format: 'int' },
      { key: 'unitCost', label: 'Unit cost', format: 'money' },
      { key: 'stockValue', label: 'Stock value', format: 'money' },
      { key: 'status', label: 'Status', format: 'text' },
    ],
  },
  {
    key: 'stock-aging',
    title: 'Stock Aging',
    section: 'Inventory',
    endpoint: 'stock-aging',
    branchFilter: true,
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantity', label: 'Qty', format: 'int' },
      { key: 'unitCost', label: 'Unit cost', format: 'money' },
      { key: 'stockValue', label: 'Stock value', format: 'money' },
      { key: 'lastReceived', label: 'Last received', format: 'text' },
      { key: 'lastSold', label: 'Last sold', format: 'text' },
      { key: 'daysSinceLastSale', label: 'Days since sale', format: 'text' },
      { key: 'ageBucket', label: 'Age', format: 'text' },
    ],
  },
  {
    key: 'dead-stock',
    title: 'Dead Stock (no sales in 90 days)',
    section: 'Inventory',
    endpoint: 'dead-stock',
    branchFilter: true,
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'quantity', label: 'Qty', format: 'int' },
      { key: 'unitCost', label: 'Unit cost', format: 'money' },
      { key: 'stockValue', label: 'Tied-up value', format: 'money' },
      { key: 'lastReceived', label: 'Last received', format: 'text' },
      { key: 'lastSold', label: 'Last sold', format: 'text' },
      { key: 'daysSinceLastSale', label: 'Days since sale', format: 'text' },
    ],
  },
  {
    key: 'batch-stock',
    title: 'Batch Stock (lots & expiry)',
    section: 'Inventory',
    endpoint: 'batch-stock',
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'warehouseName', label: 'Warehouse', format: 'text' },
      { key: 'locationName', label: 'Location', format: 'text' },
      { key: 'batchNumber', label: 'Batch', format: 'text' },
      { key: 'expiryDate', label: 'Expiry', format: 'text' },
      { key: 'daysToExpiry', label: 'Days left', format: 'text' },
      { key: 'quantity', label: 'Qty', format: 'int' },
      { key: 'unitCost', label: 'Unit cost', format: 'money' },
      { key: 'stockValue', label: 'Value', format: 'money' },
      { key: 'status', label: 'Status', format: 'text' },
    ],
  },
  {
    key: 'expiring-stock',
    title: 'Expiring Stock (60 days)',
    section: 'Inventory',
    endpoint: 'expiring-stock',
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'warehouseName', label: 'Warehouse', format: 'text' },
      { key: 'locationName', label: 'Location', format: 'text' },
      { key: 'batchNumber', label: 'Batch', format: 'text' },
      { key: 'expiryDate', label: 'Expiry', format: 'text' },
      { key: 'daysToExpiry', label: 'Days left', format: 'text' },
      { key: 'quantity', label: 'Qty', format: 'int' },
      { key: 'stockValue', label: 'Value at risk', format: 'money' },
      { key: 'status', label: 'Status', format: 'text' },
    ],
  },
  {
    key: 'serial-register',
    title: 'Serial Number Register',
    section: 'Inventory',
    endpoint: 'serial-register',
    categoryFilter: true,
    warehouseFilter: true,
    filterMode: 'none',
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'serialNumber', label: 'Serial', format: 'text' },
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'warehouseName', label: 'Warehouse', format: 'text' },
      { key: 'locationName', label: 'Location', format: 'text' },
      { key: 'receivedOn', label: 'Received', format: 'text' },
      { key: 'source', label: 'Source', format: 'text' },
      { key: 'leftOn', label: 'Left stock', format: 'text' },
      { key: 'leftRef', label: 'Document', format: 'text' },
    ],
  },
  {
    key: 'stock-movement',
    title: 'Stock Movement History',
    section: 'Inventory',
    endpoint: 'stock-movement',
    branchFilter: true,
    categoryFilter: true,
    productFilter: true,
    // Warehouse filter on Stock Movement History: keeps only that warehouse's movements.
    warehouseFilter: true,
    filterMode: 'dateOnly',
    columns: [
      { key: 'occurredAt', label: 'Date', format: 'date' },
      { key: 'type', label: 'Type', format: 'text' },
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'warehouseName', label: 'Warehouse', format: 'text' },
      { key: 'quantityDelta', label: 'Qty change', format: 'int' },
      // Task: "On Stock Movement History ... add the previous quantity and current quantity after
      // transaction" — the product's stock immediately before/after this one event.
      { key: 'stockBefore', label: 'Before', format: 'int' },
      { key: 'stockAfter', label: 'After', format: 'int' },
      { key: 'reference', label: 'Reference', format: 'text' },
      { key: 'note', label: 'Note', format: 'text' },
    ],
  },
  // Task 4: "Add Purchase order reports" — every PO placed in the date range, any status/supplier.
  // Supplier Statement (Task 5) is scoped to one supplier instead and has its own dedicated
  // screen (SupplierStatementComponent), same "hand-built, not this generic framework" reasoning
  // as Customer Statement — see that component's own doc comment.
  {
    key: 'purchase-orders',
    title: 'Purchase Orders',
    section: 'Inventory',
    endpoint: 'purchase-orders',
    branchFilter: true,
    categoryFilter: false,
    filterMode: 'dateOnly',
    columns: [
      { key: 'orderNumber', label: 'Order #', format: 'text' },
      { key: 'supplierName', label: 'Supplier', format: 'text' },
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'orderDate', label: 'Order date', format: 'date' },
      { key: 'expectedDate', label: 'Expected', format: 'date' },
      { key: 'lineCount', label: 'Lines', format: 'int' },
      { key: 'quantityOrdered', label: 'Qty ordered', format: 'int' },
      { key: 'quantityReceived', label: 'Qty received', format: 'int' },
      { key: 'totalCost', label: 'Total cost', format: 'money' },
      { key: 'amountPaid', label: 'Paid', format: 'money' },
      { key: 'amountOutstanding', label: 'Outstanding', format: 'money' },
    ],
  },
  // Task: "Add Supplier payment" — the accounts-payable mirror of Outstanding Credit Report above.
  {
    key: 'outstanding-payables',
    title: 'Outstanding Payables Report',
    section: 'Inventory',
    endpoint: 'outstanding-payables',
    categoryFilter: false,
    filterMode: 'none',
    columns: [
      { key: 'supplierName', label: 'Supplier', format: 'text' },
      { key: 'phone', label: 'Phone', format: 'text' },
      { key: 'outstandingBalance', label: 'Outstanding', format: 'money' },
    ],
  },
  // Task: "Add Sale order... Then let's have Sale as Menu" — every Sales Order placed in the date
  // range, any status/customer. Reached from the new Sales sidenav group's Reports link, not the
  // Inventory Reports picker — see shell.component.ts's sidenav reorg.
  {
    key: 'sales-orders',
    title: 'Sales Orders',
    section: 'Sales',
    endpoint: 'sales-orders',
    branchFilter: true,
    categoryFilter: false,
    filterMode: 'dateOnly',
    columns: [
      { key: 'orderNumber', label: 'Order #', format: 'text' },
      { key: 'customerName', label: 'Customer', format: 'text' },
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'orderDate', label: 'Order date', format: 'date' },
      { key: 'expectedDate', label: 'Expected', format: 'date' },
      { key: 'dueDate', label: 'Due date', format: 'text' },
      { key: 'lineCount', label: 'Lines', format: 'int' },
      { key: 'quantityOrdered', label: 'Qty ordered', format: 'int' },
      { key: 'quantityFulfilled', label: 'Qty fulfilled', format: 'int' },
      { key: 'totalAmount', label: 'Total amount', format: 'money' },
      { key: 'amountOutstanding', label: 'Outstanding', format: 'money' },
    ],
  },
  // Fourier: "Add all reports related to Restaurants Including all type of orders etc." — a
  // fourth report family, own 'Restaurant' section/picker (RestaurantReportListComponent at
  // /admin/restaurant/reports), reusing this same REPORT_DEFS/ReportViewerComponent framework.
  // None of these take a register (a restaurant order isn't tied to one) or a category picker (no
  // report here needs one — the two per-item/per-category reports don't offer a *filter*, only a
  // column, same as brand-sales/category-sales above), so every one of these is `filterMode:
  // 'dateOnly'`. Every DINE_IN/TAKEAWAY/DELIVERY order is included in each report.
  {
    key: 'restaurant-order-type-sales',
    title: 'Restaurant Sales by Order Type',
    section: 'Restaurant',
    endpoint: 'restaurant-order-type-sales',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'orderType', label: 'Order type', format: 'text' },
      { key: 'orderCount', label: 'Orders', format: 'int' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'restaurant-order-transactions',
    title: 'Restaurant Orders (all types)',
    section: 'Restaurant',
    endpoint: 'restaurant-order-transactions',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'ticketNumber', label: 'Ticket', format: 'text' },
      { key: 'orderType', label: 'Order type', format: 'text' },
      { key: 'tableOrCustomer', label: 'Table / customer', format: 'text' },
      { key: 'serverName', label: 'Server', format: 'text' },
      { key: 'closedAt', label: 'Closed at', format: 'date' },
      { key: 'itemCount', label: 'Items', format: 'int' },
      { key: 'subtotal', label: 'Subtotal', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
      { key: 'paymentMethods', label: 'Payment', format: 'text' },
    ],
  },
  {
    key: 'restaurant-menu-item-sales',
    title: 'Restaurant Menu Item Sales',
    section: 'Restaurant',
    endpoint: 'restaurant-menu-item-sales',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'itemName', label: 'Item', format: 'text' },
      { key: 'categoryName', label: 'Menu category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'restaurant-menu-category-sales',
    title: 'Restaurant Menu Category Sales',
    section: 'Restaurant',
    endpoint: 'restaurant-menu-category-sales',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'categoryName', label: 'Menu category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'salesTotal', label: 'Sales total', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'restaurant-menu-item-profit',
    title: 'Restaurant Menu Item Profit',
    section: 'Restaurant',
    endpoint: 'restaurant-menu-item-profit',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'itemName', label: 'Item', format: 'text' },
      { key: 'categoryName', label: 'Menu category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  {
    key: 'restaurant-menu-category-profit',
    title: 'Restaurant Menu Category Profit',
    section: 'Restaurant',
    endpoint: 'restaurant-menu-category-profit',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'categoryName', label: 'Menu category', format: 'text' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'revenue', label: 'Revenue', format: 'money' },
      { key: 'cost', label: 'Cost', format: 'money' },
      { key: 'profit', label: 'Profit', format: 'money' },
      { key: 'marginPercent', label: 'Margin', format: 'percent' },
    ],
  },
  {
    key: 'restaurant-server-sales',
    title: 'Restaurant Sales by Server',
    section: 'Restaurant',
    endpoint: 'restaurant-server-sales',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'serverName', label: 'Server', format: 'text' },
      { key: 'orderCount', label: 'Orders', format: 'int' },
      { key: 'quantitySold', label: 'Qty sold', format: 'int' },
      { key: 'subtotal', label: 'Subtotal', format: 'money' },
      { key: 'taxTotal', label: 'Tax', format: 'money' },
      { key: 'grandTotal', label: 'Grand total', format: 'money' },
    ],
  },
  {
    key: 'restaurant-cancelled-orders',
    title: 'Restaurant Cancelled Orders',
    section: 'Restaurant',
    endpoint: 'restaurant-cancelled-orders',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'orderType', label: 'Order type', format: 'text' },
      { key: 'tableOrCustomer', label: 'Table / customer', format: 'text' },
      { key: 'serverName', label: 'Server', format: 'text' },
      { key: 'openedAt', label: 'Opened at', format: 'date' },
      { key: 'cancelledAt', label: 'Cancelled at', format: 'date' },
      { key: 'itemCount', label: 'Items', format: 'int' },
      { key: 'wouldBeTotal', label: 'Would-be total', format: 'money' },
    ],
  },
  // Fourier: "Restaurants need a dedicated wastage module" — the reporting half; the operational
  // "record a loss"/"view log" screens are RestaurantWastageFormComponent/RestaurantWastageListComponent,
  // reached from the Restaurant sidenav group directly, not through this reports picker.
  {
    key: 'restaurant-wastage',
    title: 'Restaurant Wastage',
    section: 'Restaurant',
    endpoint: 'restaurant-wastage',
    categoryFilter: false,
    filterMode: 'dateOnly',
    branchFilter: true,
    columns: [
      { key: 'recordedAt', label: 'Date', format: 'date' },
      { key: 'sku', label: 'SKU', format: 'text' },
      { key: 'productName', label: 'Product', format: 'text' },
      { key: 'quantity', label: 'Qty wasted', format: 'int' },
      { key: 'reason', label: 'Reason', format: 'text' },
      // Fourier: "Restaurant wastage should be confirmed by admin once confirmed inventory and
      // account will be impacted" — PENDING/REJECTED rows always show "Before"/"After"/"Cost
      // impact" as blank, since only a CONFIRMED entry ever actually moved stock/posted to accounting.
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'note', label: 'Note', format: 'text' },
      { key: 'stockBefore', label: 'Before', format: 'int' },
      { key: 'stockAfter', label: 'After', format: 'int' },
      { key: 'costImpact', label: 'Cost impact', format: 'money' },
    ],
  },
  // Fourier: "Add Helpdesk reports(XLS,PDF)" — every ticket created in the chosen date range, same
  // "dateOnly" shape as the simpler reports above (a ticket isn't tied to a register, and the
  // existing Tickets screen already covers filtering by status/priority/category/assignee for
  // day-to-day work — this is the period export).
  {
    key: 'helpdesk-tickets',
    title: 'Help Desk Tickets',
    section: 'Help Desk',
    endpoint: 'helpdesk-tickets',
    categoryFilter: false,
    filterMode: 'dateOnly',
    columns: [
      { key: 'ticketNumber', label: 'Ticket #', format: 'text' },
      { key: 'type', label: 'Type', format: 'text' },
      { key: 'subject', label: 'Subject', format: 'text' },
      { key: 'categoryName', label: 'Category', format: 'text' },
      { key: 'priority', label: 'Priority', format: 'text' },
      { key: 'status', label: 'Status', format: 'text' },
      { key: 'requesterName', label: 'Requester', format: 'text' },
      { key: 'assignedToUserFullName', label: 'Assigned to', format: 'text' },
      { key: 'createdAt', label: 'Created', format: 'date' },
      { key: 'resolvedAt', label: 'Resolved', format: 'date' },
      { key: 'closedAt', label: 'Closed', format: 'date' },
    ],
  },
];
