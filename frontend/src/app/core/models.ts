/** Fourier: "Build a full permissions system" — additive ADMIN-tier powers a non-admin can be individually granted. Mirrors the backend's Permission enum. */
export type Permission = 'CONFIRM_RESTAURANT_WASTAGE' | 'APPROVE_STOCK_ADJUSTMENT' | 'APPROVE_PURCHASE_REQUISITION' | 'OVERRIDE_DISCOUNT_CAP' | 'APPROVE_EXPENSE' | 'APPROVE_STOCK_TRANSFER';

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  fullName: string;
  role: 'ADMIN' | 'CASHIER';
  /** Fourier: "Build a full permissions system" — this user's additive grants, so the UI can gate without a separate call. */
  permissions: Permission[];
}

export interface Category {
  id: number;
  name: string;
  colorHex: string;
}

/** A product's manufacturer/brand — plain reference list, admin-manageable. See the backend's Brand entity javadoc. */
export interface Brand {
  id: number;
  name: string;
}

export interface Product {
  id: number;
  sku: string | null;
  barcode: string | null;
  /** Nullable, but unique when set — see the backend's Product.code javadoc. */
  code: string | null;
  /** A data: URL, or null. Same convention as StoreSettings.logoDataUrl. */
  iconDataUrl: string | null;
  name: string;
  /** Fourier: "On Invoice Line add product description text (by default it should pick the product description)" — see the backend's Product.description javadoc. */
  description: string | null;
  unitPrice: number;
  /** Not shown to customers — used only by the admin Profit reports. See the backend's Product.costPrice javadoc. */
  costPrice: number;
  stockQuantity: number;
  /** True (default) if this product's stockQuantity gates/is moved by selling it. When false it's like a service or made-to-order item — always sellable, never stock-checked. See the backend's Product.trackStock javadoc. */
  trackStock: boolean;
  active: boolean;
  categoryId: number | null;
  categoryName: string | null;
  categoryColor: string | null;
  /** Nullable — see the backend's Product.brand javadoc. */
  brandId: number | null;
  brandName: string | null;
  taxRateId: number | null;
  taxRateCode: string | null;
  taxRateName: string | null;
  taxRatePercent: number | null;
  /** True when the assigned tax rate's price is tax-inclusive (unitPrice already has tax folded in) — see TaxRateOption.inclusive. False (not just absent) when no tax rate is assigned. */
  taxRateInclusive: boolean;
  /** EFRIS commodity/tariff code ("unsp_code" on an EFRIS invoice line) — nullable, only fed to a real EFRIS submission. */
  unspCode: string | null;
  /** Goods (false, default) or service (true) — feeds EFRIS Chad's item_nature ("ITEM_NATURE_GOODS"/"ITEM_NATURE_SERVICE"). See the backend's Product.service javadoc. */
  service: boolean;
  /** Reorder point / order-up-to level — null = no reorder rule. See the backend's Product.reorderLevel. */
  reorderLevel: number | null;
  maxStockLevel: number | null;
  /** Variants: the template this product was generated from, its option values (e.g. "Red / L"), or — on a template — the option summary. */
  variantParentId?: number | null;
  variantLabel?: string | null;
  variantAttributes?: string | null;
  /** NONE (plain quantity), BATCH (lots with expiry) or SERIAL (one serial number per unit). */
  trackingMode?: 'NONE' | 'BATCH' | 'SERIAL';
}

/** Mirrors the backend's com.posapp.common.PageResponse — a plain page envelope, not Spring's Page<T> shape. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Task: "Implement an audit log of who did what and when" — mirrors the backend's AuditLogDto. See AuditLogService's javadoc for the fixed set of actions this app records. */
export interface AuditLogEntry {
  id: number;
  occurredAt: string;
  actorId: number | null;
  actorUsername: string;
  actorFullName: string | null;
  action: string;
  description: string;
  entityType: string | null;
  entityId: number | null;
}

export interface AppUser {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  role: 'ADMIN' | 'CASHIER';
  active: boolean;
  hasPin: boolean;
  /** Fourier: "Build a full permissions system" — this staff member's additive grants. */
  permissions: Permission[];
  /** Branches this account is limited to; empty = unrestricted (admins are never limited). */
  branchIds?: number[];
}

export interface DashboardSummary {
  todaySalesCount: number;
  todaySalesTotal: number;
  openSessionsCount: number;
  totalActiveProducts: number;
  totalCustomers: number;
  lowStockProducts: Product[];
}

export interface Customer {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  /** VAT/tax registration number (Task: "Add Tax ID on customer entity") — purely informational. */
  taxId: string | null;
  /** Task: "Enable credit for customer". */
  creditEnabled: boolean;
  /** Task: "Set customer credit limit" — always a number (defaults to 0 server-side), never null. */
  creditLimit: number;
  /** Task: "Optional due date/payment terms" — customer-level default; null means no default term. */
  defaultCreditTermDays: number | null;
  /** The customer the terminal pre-selects on a brand-new cart instead of leaving it with no customer at all — see Customer.walkIn's backend javadoc. At most one customer has this true in practice. */
  walkIn: boolean;
}

export interface RegisterSummary {
  id: number;
  name: string;
  location: string | null;
  /** Fourier: "the terminal will be registered on warehouse" — null means no warehouse assigned, which falls back to the primary warehouse at checkout. */
  warehouseId: number | null;
  warehouseName: string | null;
  /** Fourier: "if multi-location then sale start from location" — null means no override, which falls back to warehouseId's own default location. Must belong to warehouseId when set. */
  defaultLocationId: number | null;
  defaultLocationName: string | null;
  openSessionId: number | null;
}

export interface RegisterSession {
  id: number;
  registerId: number | null;
  registerName: string;
  cashierId: number | null;
  cashierName: string;
  openingFloat: number;
  openedAt: string;
  closedAt: string | null;
  countedCash: number | null;
  cashVariance: number | null;
  status: 'OPEN' | 'CLOSED';
  saleCount: number;
  grossSales: number;
}

/**
 * One cashier's own working session inside a shift (RegisterSession) — what lets more than one
 * person ring sales on the same terminal during the same till session. See CashierSessionApiService.
 */
export interface CashierSession {
  id: number;
  registerSessionId: number | null;
  registerName: string;
  /** Status of the *shift* (RegisterSession) this cashier session belongs to — separate from this session's own `status`. */
  shiftStatus: 'OPEN' | 'CLOSED' | null;
  cashierId: number | null;
  cashierName: string;
  startedAt: string;
  endedAt: string | null;
  status: 'OPEN' | 'CLOSED';
  /** Task: "Add customer-facing double screen" — which open tab is currently on screen at the terminal, for a second display to poll. Null when none is selected yet. */
  activeOrderId: number | null;
}

/**
 * One cashier's own line in the Z-report's per-cashier breakdown — Task: "The report for
 * register_session (Shift) should show all cashier_session (session report)." Ordinarily just the
 * one cashier the shift was opened for; more than one entry only when "Switch cashier" (or an
 * admin's "assign") brought a teammate onto the same till during this shift.
 */
export interface CashierSessionReport {
  cashierSessionId: number;
  cashierId: number | null;
  cashierName: string;
  startedAt: string;
  endedAt: string | null;
  status: 'OPEN' | 'CLOSED';
  saleCount: number;
  grossSales: number;
  taxCollected: number;
  cashIn: number;
  nonCashIn: number;
}

export interface SessionReport {
  session: RegisterSession;
  saleCount: number;
  grossSales: number;
  taxCollected: number;
  cashIn: number;
  nonCashIn: number;
  cashRefundsOut: number;
  expectedCash: number;
  /** Task: "The report for register_session (Shift) should show all cashier_session (session report)" — oldest first. */
  cashierSessions: CashierSessionReport[];
}

/**
 * A standalone report scoped to one cashier's own working session — deliberately *not* the same
 * thing as `SessionReport` (the whole shift's Z-report). Task: "We need to differentiate
 * CashierSession Z Report with RegisterSession Z Report." No cash-drawer reconciliation and no
 * refunds here — both are shift-wide, physical-till concepts; see CashierSessionZReportDto's own
 * javadoc for why.
 */
export interface CashierSessionZReport {
  cashierSessionId: number;
  registerSessionId: number | null;
  registerId: number | null;
  registerName: string;
  shiftStatus: 'OPEN' | 'CLOSED' | null;
  cashierId: number | null;
  cashierName: string;
  startedAt: string;
  endedAt: string | null;
  status: 'OPEN' | 'CLOSED';
  saleCount: number;
  grossSales: number;
  taxCollected: number;
  cashIn: number;
  nonCashIn: number;
}

export interface OrderLine {
  id: number;
  productId: number | null;
  description: string;
  unitPrice: number;
  quantity: number;
  discountPercent: number;
  lineTotal: number;
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  /** True when this line's tax was folded into unitPrice (inclusive) rather than added on top — snapshotted from the tax rate at add-line time. See TaxRateOption.inclusive. */
  taxInclusive: boolean;
  /** Serial numbers typed for this line (one per text line) and the product's tracking mode. */
  serialNumbers?: string | null;
  trackingMode?: 'NONE' | 'BATCH' | 'SERIAL';
}

export interface Payment {
  id: number;
  method: string;
  amount: number;
}

export interface Order {
  id: number;
  sessionId: number | null;
  cashierSessionId: number | null;
  customerId: number | null;
  customerName: string;
  /** Fourier: "sending ... receipts ... to the customer's email" — pre-fills the Receipts screen's "email this" prompt. Null for a walk-in/no-customer sale, or a customer with none on file. */
  customerEmail: string | null;
  status: 'OPEN' | 'COMPLETED' | 'VOIDED';
  note: string | null;
  invoiceNumber: string | null;
  createdAt: string;
  completedAt: string | null;
  lines: OrderLine[];
  payments: Payment[];
  totalQuantity: number;
  subtotal: number;
  taxTotal: number;
  total: number;
  /** Task: "add two logo (URA LOGO, EFRIS LOGO)" — lets the receipt fetch this order's own terminal's EFRIS logos. Null for an order with no session/register at all. */
  registerId: number | null;
  registerName: string;
  cashierName: string;
  /** How much of this sale was tendered on store credit — null when none. See backend SaleOrder.creditAmount's javadoc. */
  creditAmount: number | null;
  /** When the credit portion is due — null means no due date (never overdue). */
  dueDate: string | null;
  /** Task: "local queuing of sales that sync once connectivity returns" — when this sale was actually rung offline, not when it synced. Null for an ordinary online sale. */
  offlineQueuedAt: string | null;
  /** Set only when an offline-synced sale's final total didn't match the terminal's offline estimate — see backend SaleOrder.syncNote's javadoc. */
  syncNote: string | null;
  /**
   * EFRIS (Rwanda RRA e-invoicing) fiscal receipt fields — all null until a sale is actually
   * submitted to EFRIS (nothing does that yet — see the backend SaleOrder javadoc and the Apps
   * framework's own known-limitations note). Shown on the printed receipt once populated.
   */
  sdcId: string | null;
  sdcDate: string | null;
  sdcReceiptNumber: string | null;
  internalData: string | null;
  signatureData: string | null;
  cisNumber: string | null;
  cisDate: string | null;
  mrcData: string | null;
  /** When an EFRIS submission was last attempted (success or failure) — null means never attempted. See efrisError. */
  efrisSubmittedAt: string | null;
  /** Set only when an EFRIS submission was attempted and failed — see backend SaleOrder.efrisError's javadoc. The sale itself still completed either way. */
  efrisError: string | null;
  /** Branch this sale belongs to; null on sales older than branches (= head office). Drives the receipt's branch header. */
  branchId?: number | null;
}

/** One terminal's (Register's) EFRIS connection — Task: "EFRIS_setting for one terminal". Each till talks to its own local EFRIS device/URL, not one store-wide endpoint. */
export interface EfrisTerminal {
  registerId: number;
  registerName: string;
  location: string | null;
  efrisUrl: string | null;
  /** The JSON-RPC "db" argument EFRIS's middleware expects (e.g. "RRA_VSDC"). */
  efrisDb: string | null;
  /** The JSON-RPC "uid" argument — also sent as the invoice payload's own user_id. */
  efrisUid: number | null;
  /** The JSON-RPC "password" argument — stored/sent in plaintext, same as any third-party API key would be. */
  efrisPassword: string | null;
  /** Task: "add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the printout" — data: URLs, same convention as StoreSettings.logoDataUrl. */
  rraLogoDataUrl: string | null;
  efrisLogoDataUrl: string | null;
}

/**
 * Just the two logos for one terminal (not the rest of EfrisTerminal's sensitive connection
 * fields) — what ReceiptComponent fetches to print them, since any logged-in cashier can view a
 * receipt but only an admin can see a terminal's EFRIS URL/db/uid/password. See
 * EfrisSettingController.getLogos's javadoc.
 */
export interface EfrisLogos {
  rraLogoDataUrl: string | null;
  efrisLogoDataUrl: string | null;
}

/**
 * One terminal's (Register's) EFRIS Chad connection — Task: "Now I want to add new app (EFRIS
 * Chad) with will need the following settings: url, basic_auth_username, basic_password,
 * sdc_number, branch_code, mrc, integration_key. The integration with EFRIS Chad is via HTTP(S)
 * with basic authentication." Deliberately its own interface rather than reusing EfrisTerminal
 * (Rwanda's) — the two integrations' fields don't overlap (basic auth + SDC/branch/MRC vs.
 * JSON-RPC db/uid/password), and settings storage only for now: Fourier's own words, "I will
 * share the resulting parsing later."
 */
export interface EfrisChadTerminal {
  registerId: number;
  registerName: string;
  location: string | null;
  url: string | null;
  basicAuthUsername: string | null;
  basicPassword: string | null;
  sdcNumber: string | null;
  branchCode: string | null;
  mrc: string | null;
  integrationKey: string | null;
  /** Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — data: URLs, same convention as EfrisTerminal's own logo fields. */
  craLogoDataUrl: string | null;
  efrisLogoDataUrl: string | null;
}

/**
 * Just the two logos for one terminal's EFRIS Chad setting (not the rest of EfrisChadTerminal's
 * sensitive connection fields) — what ReceiptComponent/ReturnReceiptComponent fetch to print them,
 * mirroring EfrisLogos (Rwanda's equivalent). See EfrisChadSettingController.getLogos's javadoc.
 */
export interface EfrisChadLogos {
  craLogoDataUrl: string | null;
  efrisLogoDataUrl: string | null;
}

/** One returned line on a SalesReturn — new for "print out the return". */
export interface SalesReturnLine {
  orderLineId: number | null;
  description: string | null;
  quantity: number;
  /** Null for a return processed before this field existed. */
  refundAmount: number | null;
}

export interface SalesReturn {
  id: number;
  orderId: number | null;
  invoiceNumber: string | null;
  customerName: string;
  returnDate: string;
  refundTotal: number;
  refundMethod: string;
  reason: string | null;
  lines: SalesReturnLine[];
  /** Task: "The return will also integrate with EFRIS" — lets the return receipt fetch the original sale's terminal's EFRIS logos, same as the sale receipt does. Null for a return whose order has no session/register at all. */
  registerId: number | null;
  /** This return's own EFRIS erp_code — distinct from the original sale's invoiceNumber above, which becomes erp_code_ref instead. Null for a return processed before this feature existed. */
  returnNumber: string | null;
  /**
   * EFRIS (Rwanda RRA e-invoicing) fiscal receipt fields for this return — same shape as Order's
   * own EFRIS block, all null until the return is actually submitted to EFRIS with
   * transaction_type "R".
   */
  sdcId: string | null;
  sdcDate: string | null;
  sdcReceiptNumber: string | null;
  internalData: string | null;
  signatureData: string | null;
  cisNumber: string | null;
  cisDate: string | null;
  mrcData: string | null;
  /** When an EFRIS submission was last attempted for this return (success or failure) — null means never attempted. See efrisError. */
  efrisSubmittedAt: string | null;
  /** Set only when an EFRIS submission was attempted and failed — the return itself still completed either way. */
  efrisError: string | null;
  /** The raw EFRIS response body from the most recent attempt. */
  efrisResponse: string | null;
}

/** A payment/refund method's code (e.g. "CASH", "CARD") — was a fixed union, now admin-configurable. See PaymentMethodOption. */
export type PaymentMethod = string;

/** An admin-manageable payment/refund method (Admin > Settings > Payment methods). */
export interface PaymentMethodOption {
  id: number;
  code: string;
  name: string;
  active: boolean;
  cash: boolean;
  /** True if this method represents selling on store credit rather than an immediate tender. See backend PaymentMethodEntity.credit's javadoc. */
  credit: boolean;
  /** Task: "Make payment method order dynamic so I can set the first, second on checkout screen" — lower shows first; ties break by name. */
  sortOrder: number;
}

/** Task: "Customer balance on POS screen" — a customer's credit standing at a glance. */
export interface CreditSummary {
  customerId: number;
  creditEnabled: boolean;
  creditLimit: number;
  outstandingBalance: number;
  availableCredit: number;
  defaultCreditTermDays: number | null;
}

/**
 * Which kind of order/invoice an outstanding-order row or payment allocation targets — mirrors
 * the backend's OrderType enum, now three-way. POS_SALE is a register checkout. SALES_ORDER is
 * legacy only (a payment recorded directly against a back-office SalesOrder before Fourier's own
 * later request, "create invoice related to the Sale Order and record payment on invoice") — the
 * Record Payment screen no longer offers it as a new target. INVOICE is the replacement: an
 * Invoice, generated from a SalesOrder or created standalone (see the Invoice model below).
 */
export type OrderType = 'POS_SALE' | 'SALES_ORDER' | 'INVOICE';

/** One row of a customer's outstanding-orders list — backs the Record Payment screen's allocation table. */
export interface OutstandingOrder {
  orderId: number;
  orderType: OrderType;
  invoiceNumber: string | null;
  completedAt: string;
  dueDate: string | null;
  creditAmount: number;
  amountOutstanding: number;
}

/** One allocation of a customer payment onto a specific order — part of CustomerPayment. */
export interface PaymentAllocation {
  orderId: number;
  orderType: OrderType;
  invoiceNumber: string | null;
  amountApplied: number;
}

/**
 * 'SETTLEMENT' is an actual recorded payment (Record Payment screen) against something left
 * outstanding. 'POS_SALE' is a POS sale's own checkout tender, synthesized into this same row
 * shape so it shows up in Customer Payment history too (Fourier: "On Customer payment list no POS
 * payment for cash sale") — see backend PaymentSource's javadoc.
 */
export type PaymentSource = 'SETTLEMENT' | 'POS_SALE';

/** A recorded customer payment plus its allocations — backs the post-record response, payment history, and receipt reprints. */
export interface CustomerPayment {
  /** Null for a POS_SALE row — there is no backing CustomerPayment record, only the sale's own Payment. */
  id: number | null;
  customerId: number;
  customerName: string;
  amount: number;
  paidAt: string;
  /** See backend CustomerPayment.method's javadoc — null for a payment recorded before this field existed. */
  method: string | null;
  note: string | null;
  receiptNumber: string | null;
  /** Null for a POS_SALE row — nobody "recorded" a checkout tender. */
  recordedByName: string | null;
  allocations: PaymentAllocation[];
  customerBalanceAfter: number;
  source: PaymentSource;
}

/** Request body for POST /api/customers/{id}/payments. */
export interface RecordPaymentRequest {
  amount: number;
  /** Task: "To record Payment in Sale Order add payment mode (CASH, CHEQUE, ETC)" — an active PaymentMethodOption.code. */
  method: string;
  note?: string | null;
  allocations: { orderId: number; orderType: OrderType; amount: number }[];
}

/**
 * Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — request
 * body for POST /api/invoices/{id}/payments. No `allocations` list and no `orderId`/`orderType` —
 * the invoice is the one in the URL, and the whole amount always applies to it.
 */
export interface RecordInvoicePaymentRequest {
  amount: number;
  method: string;
  note?: string | null;
}

/**
 * An admin-manageable tax rate (Admin > Settings > Taxes) — was a single fixed `app.pos.tax-rate`
 * property. `ratePercent` is a percentage (e.g. 15 for 15%), not a fraction. Any number of rows
 * can be `active` at once (mirrors PaymentMethodOption) — each product picks which rate applies
 * to it (or none, for tax-exempt goods) via `Product.taxRateId`; see the backend TaxRate's
 * javadoc. `code` is a stable identifier set at creation and never changed afterward — it's what
 * gets snapshotted onto a sale's line items, not this row's id, so renaming/deactivating a rate
 * never rewrites an already-rung sale.
 *
 * `inclusive` (Task: "On Tax rate add inclusive attribute where if it is true the in the tax in
 * price is inclusive"): false (the default) means tax is added on top of a product's unitPrice at
 * checkout — the classic "+ tax" behavior. true means unitPrice already has this rate's tax folded
 * into it (the shelf price the customer sees is the final price), so checkout/receipts extract the
 * tax back out of the line total instead of adding it. Snapshotted onto OrderLine.taxInclusive at
 * add-line time, same as code/ratePercent.
 */
export interface TaxRateOption {
  id: number;
  code: string;
  name: string;
  ratePercent: number;
  active: boolean;
  inclusive: boolean;
}

/**
 * The single store-wide settings row (Admin > Settings > Business Details / System settings).
 * One GET backs both screens; each screen only ever saves the subset of fields it owns (see
 * StoreSettingsApiService).
 */
export interface StoreSettings {
  id: number;
  businessName: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  /** VAT/tax registration number printed on receipts/invoices — purely informational. */
  taxId: string | null;
  /** A data: URL straight out of FileReader.readAsDataURL — see BusinessDetailsComponent. */
  logoDataUrl: string | null;
  currencyCode: string;
  currencySymbol: string;
  /** An IANA zone id, e.g. "Africa/Kampala". */
  timeZone: string;
  /** A display pattern, e.g. "dd/MM/yyyy" — one of SystemSettingsComponent's fixed choices. */
  dateFormat: string;
  /**
   * Task 1: "In the system setting configure the max discount" — a store-wide ceiling (0-100) on
   * any single order line's discount, enforced backend-side by OrderService.validateDiscount for
   * both the online and offline-sync flows regardless of who's applying it. Defaults to 100
   * (effectively unrestricted) until an admin narrows it.
   */
  maxDiscountPercent: number;
  /**
   * Fourier: "Same for discount (but discount should be activate in the setting or
   * configuration) and it will be per line" — then "Except POS other module can have global
   * discount. But again give the user to configure both discount. Either on line or global" —
   * the master switch for how Quotation/Sale Order/Invoice/Purchase Order (Bill/Credit
   * Note/Refund inherit theirs) apply a discount: 'NONE' (no discount concept at all),
   * 'PER_LINE' (each line gets its own), or 'GLOBAL' (one percentage entered once for the whole
   * document) — mutually exclusive, never both at once. Defaults to 'NONE' — unlike
   * maxDiscountPercent above, which only caps something already possible, this is a brand-new
   * capability and off until an admin picks a mode. Independent of the POS terminal's own
   * role-gated discount, which this setting does not affect.
   */
  discountMode: 'NONE' | 'PER_LINE' | 'GLOBAL';
  /**
   * Task: "Inventory Setting (Example: allow negative stock)" — whether a checkout may sell more
   * of a product than its current stock shows, and whether a manual stock adjustment may push
   * stock below zero. Defaults to true (today's behavior — stock was never checked before this
   * setting existed) until an admin turns it off. See the backend StoreSettings.allowNegativeStock
   * javadoc.
   */
  allowNegativeStock: boolean;
  /**
   * Fourier: "Product Packaging & Breakdown / Unit Conversion ... configured as we did for chart
   * of accounts. If not activated ignore packaging" — the master switch for actually running a
   * PackagingRule breakdown (see PackagingBreakdownComponent). Configuring a rule itself is never
   * gated by this. Defaults to false — a brand-new capability, off until an admin turns it on in
   * Inventory settings. See the backend StoreSettings.productBreakdownEnabled javadoc.
   */
  productBreakdownEnabled: boolean;
  /**
   * Fourier: "Add allow multi-warehouse in inventory settings. By default multi-warehouse is
   * disabled ... Terminal linked to this warehouse(default) ... if yes ... To make sale there is
   * a need of creating another warehouse and locations and Terminal will be linked to those
   * warehouse." Follow-up: "When multi-warehouse is OFF ... I can still add location on the
   * default warehouse" — gates only creating a *second* warehouse and completing a sale against
   * the primary warehouse; adding locations to the existing warehouse and Stock Transfers are
   * unaffected either way. Defaults to false. See the backend StoreSettings.multiWarehouseEnabled
   * javadoc.
   */
  multiWarehouseEnabled: boolean;
  /** Multi-warehouse phase 4 — the one location Restaurant orders and wastage draw stock from; null = the primary warehouse's normal stock. See the backend StoreSettings.restaurantLocationId javadoc. */
  restaurantLocationId: number | null;
  /**
   * Fourier: "Configure smtp for sending email" — Admin > Settings > Email settings. The password
   * itself is never sent back to the frontend (see StoreSettingsApiService/EmailSettingsComponent);
   * smtpPasswordSet just tells the form whether one is already saved.
   */
  smtpEnabled: boolean;
  smtpHost: string | null;
  smtpPort: number;
  smtpUsername: string | null;
  smtpPasswordSet: boolean;
  smtpFromEmail: string | null;
  smtpFromName: string | null;
  smtpUseSsl: boolean;
  /**
   * Fourier's own opt-in escape hatch for a real certificate-hostname mismatch that no
   * connection-setup change on the backend could route around — still encrypted, but stops
   * verifying the certificate's name matches smtpHost. A real security tradeoff; see
   * EmailSettingsComponent's warning copy. Defaults to false.
   */
  smtpSkipCertCheck: boolean;
  /**
   * Fourier: "Wiring a handful of business events to that same mailer" — the one address every
   * passive alert (low stock, a purchase requisition awaiting approval, a quotation about to
   * expire, a customer nearing their credit limit) goes to. Null/blank means alerts simply don't
   * send yet — no separate on/off toggle, same convention smtpHost already uses for SMTP itself.
   */
  notificationsEmail: string | null;
  /** WEIGHTED_AVERAGE / LAST_PURCHASE / MANUAL — how a product's cost follows purchases. */
  costingMethod?: 'WEIGHTED_AVERAGE' | 'LAST_PURCHASE' | 'MANUAL';
}

// ---- Accounting (Admin > Accounting) ----

export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';

export interface Account {
  id: number;
  code: string;
  name: string;
  type: AccountType;
  description: string | null;
  active: boolean;
  /** A well-known account AccountingService posts to by hardcoded code — cannot be deactivated. See the backend Account.systemAccount javadoc. */
  systemAccount: boolean;
}

export interface AccountPayload {
  code: string;
  name: string;
  type: AccountType;
  description: string | null;
  active: boolean;
}

export interface JournalEntryLine {
  id: number;
  accountId: number;
  accountCode: string;
  accountName: string;
  debit: number;
  credit: number;
  description: string | null;
  /** Optional — see the backend JournalEntryLine.costCenter javadoc. */
  costCenterId: number | null;
  costCenterName: string | null;
}

export interface JournalEntry {
  id: number;
  entryNumber: string;
  entryDate: string;
  memo: string | null;
  /** Which module posted this automatically (e.g. "POS_SALE"), or "MANUAL" for one entered by hand on the Journal Entries screen. Null on very old rows only, shouldn't happen going forward. */
  sourceType: string | null;
  sourceId: number | null;
  lines: JournalEntryLine[];
  totalDebit: number;
  totalCredit: number;
}

export interface CreateJournalEntryLinePayload {
  accountId: number;
  debit: number;
  credit: number;
  description: string | null;
  costCenterId: number | null;
}

export interface CreateJournalEntryPayload {
  entryDate: string | null;
  memo: string | null;
  lines: CreateJournalEntryLinePayload[];
}

export interface LedgerLine {
  entryDate: string;
  journalEntryId: number;
  entryNumber: string;
  memo: string | null;
  description: string | null;
  debit: number;
  credit: number;
  runningBalance: number;
}

/**
 * One underlying transaction backing a summary report row — see the backend
 * ReportDetailLineDto javadoc for the debit/credit convention each report family uses.
 */
export interface ReportDetailLine {
  date: string | null;
  reference: string | null;
  description: string | null;
  debit: number | null;
  credit: number | null;
  amount: number;
}

/** One account's own section of the General Ledger report — every account back to back, as opposed to LedgerLine which is one account's own drill-down from Chart of Accounts. */
export interface GeneralLedgerAccount {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  openingBalance: number;
  closingBalance: number;
  lines: LedgerLine[];
}

export interface TrialBalanceRow {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  totalDebit: number;
  totalCredit: number;
  balance: number;
  details: ReportDetailLine[];
}

export interface AccountAmount {
  accountId: number;
  accountCode: string;
  accountName: string;
  amount: number;
  details: ReportDetailLine[];
}

export interface ProfitAndLoss {
  from: string;
  to: string;
  revenue: AccountAmount[];
  expenses: AccountAmount[];
  totalRevenue: number;
  totalExpenses: number;
  netIncome: number;
}

export interface BalanceSheet {
  asOf: string;
  assets: AccountAmount[];
  liabilities: AccountAmount[];
  equity: AccountAmount[];
  totalAssets: number;
  totalLiabilities: number;
  totalEquity: number;
  currentEarnings: number;
  balanced: boolean;
}

// ---- Accounting Reports expansion (Cost Centers, Expenses, Incomes, and the new report batch) ----

export interface CostCenter {
  id: number;
  code: string;
  name: string;
  active: boolean;
}

export interface CostCenterPayload {
  code: string;
  name: string;
  active: boolean;
}

/** Who the other side of an Expense/Income entry is — see the backend PayeeType javadoc. */
export type PayeeType = 'VENDOR' | 'STAFF' | 'CLIENT' | 'OTHER';

/** An Expense's approval lifecycle — see the backend ExpenseStatus javadoc. */
export type ExpenseStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'VOIDED';

export interface Expense {
  id: number;
  expenseDate: string;
  accountId: number;
  accountCode: string;
  accountName: string;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  paid: boolean;
  memo: string | null;
  costCenterId: number | null;
  costCenterName: string | null;
  status: ExpenseStatus;
  approvedAt: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  settledAt: string | null;
  receiptDataUrl: string | null;
  recurringTemplateId: number | null;
  /** Branch this expense belongs to; null on older rows (= head office). */
  branchId?: number | null;
}

export interface ExpensePayload {
  expenseDate: string;
  accountId: number;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  paid: boolean;
  memo: string | null;
  costCenterId: number | null;
  receiptDataUrl: string | null;
  /** Optional branch; omit/null = head office (or unchanged on edit). */
  branchId?: number | null;
}

/** Fourier: "Recurring expenses" — a saved template RecurringExpenseTemplateService's daily sweep turns into a new PENDING Expense. */
export type RecurrenceFrequency = 'WEEKLY' | 'MONTHLY';

export interface RecurringExpenseTemplate {
  id: number;
  name: string;
  accountId: number;
  accountCode: string;
  accountName: string;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  paid: boolean;
  memo: string | null;
  costCenterId: number | null;
  costCenterName: string | null;
  frequency: RecurrenceFrequency;
  nextRunDate: string;
  active: boolean;
  lastRunAt: string | null;
}

export interface RecurringExpenseTemplatePayload {
  name: string;
  accountId: number;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  paid: boolean;
  memo: string | null;
  costCenterId: number | null;
  frequency: RecurrenceFrequency;
  nextRunDate: string;
  active: boolean;
}

export interface Income {
  id: number;
  incomeDate: string;
  accountId: number;
  accountCode: string;
  accountName: string;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  received: boolean;
  memo: string | null;
  costCenterId: number | null;
  costCenterName: string | null;
  /** The branch this record belongs to; absent/null on rows older than branches (= head office). */
  branchId?: number | null;
}

export interface IncomePayload {
  incomeDate: string;
  accountId: number;
  payeeType: PayeeType;
  payeeId: number | null;
  payeeName: string;
  amount: number;
  received: boolean;
  memo: string | null;
  costCenterId: number | null;
  /** Optional branch; omitted = head office (or unchanged when editing a draft). */
  branchId?: number | null;
}

/** One row of a grouped Expenses/Incomes report — see the backend GroupedAmountDto javadoc. */
export interface GroupedAmount {
  label: string;
  amount: number;
  count: number;
  details: ReportDetailLine[];
}

/** One row of the "Financial Transactions" report. */
export interface FinancialTransaction {
  date: string;
  entryNumber: string;
  memo: string | null;
  accountCode: string | null;
  accountName: string | null;
  debit: number;
  credit: number;
  description: string | null;
}

/** One cost center's own totals for the "Cost Centers" journals report. */
export interface CostCenterActivity {
  costCenterId: number;
  costCenterCode: string;
  costCenterName: string;
  totalDebit: number;
  totalCredit: number;
  net: number;
  details: ReportDetailLine[];
}

export interface CashFlowLine {
  date: string;
  memo: string | null;
  amount: number;
}

export interface CashFlow {
  from: string;
  to: string;
  operating: CashFlowLine[];
  investing: CashFlowLine[];
  financing: CashFlowLine[];
  netOperating: number;
  netInvesting: number;
  netFinancing: number;
  netChangeInCash: number;
  openingCash: number;
  closingCash: number;
}

export interface TaxRateAmount {
  taxCode: string;
  amount: number;
  details: ReportDetailLine[];
}

/** Backs both the Tax Report and Tax Declaration screens — see the backend TaxSummaryDto javadoc. */
export interface TaxSummary {
  from: string;
  to: string;
  collectedByRate: TaxRateAmount[];
  totalCollected: number;
  taxPayableNetMovement: number;
  taxPayableDetails: ReportDetailLine[];
}

export interface AssetsReport {
  asOf: string;
  assets: AccountAmount[];
  totalAssets: number;
}

/** The eight groupBy values shared by the Expenses/Incomes segmented+periodic reports. */
export type ExpenseIncomeGroupBy = 'category' | 'vendor' | 'staff' | 'client' | 'daily' | 'weekly' | 'monthly' | 'yearly';

/**
 * One row of the Apps/plugins catalog (Admin > Settings > Apps — Task: "plugin (or apps) to be
 * installed and configured"), merged with this store's install/enable state. `key` is the
 * backend's `AppKey` enum name (e.g. "EFRIS_RW") — opaque here, just used to address the other
 * endpoints. See the backend's AppCatalog for why this list is fixed/code-defined, not something
 * you can add to from the UI.
 */
export interface AppSummary {
  key: string;
  name: string;
  description: string;
  category: string;
  installed: boolean;
  enabled: boolean;
}

/**
 * Task: "Inventory depth ... purchase orders and supplier records." Mirrors the backend's
 * SupplierDto. `active` suppliers show up in the purchase-order form's picker; an inactive one
 * keeps its name on old POs but can't be picked for a new one.
 */
export interface Supplier {
  id: number;
  name: string;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  /** VAT/tax registration number (Task: "add TAX ID on supplier") — purely informational, same treatment as Customer.taxId. */
  taxId: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
}

/** Mirrors the backend's PurchaseOrderStatus enum — see that class's javadoc for the DRAFT -> SENT -> (PARTIALLY_RECEIVED ->)* RECEIVED / CANCELLED lifecycle. */
export type PurchaseOrderStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';

/** One line of a PurchaseOrder — mirrors the backend's PurchaseOrderLineDto. */
export interface PurchaseOrderLine {
  id: number;
  productId: number | null;
  productName: string;
  quantityOrdered: number;
  quantityReceived: number;
  unitCost: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the discount percentage actually applied to this line: either its own entered value (PER_LINE mode) or the order's single header-level value applied uniformly (GLOBAL mode). Null/0 for a line with no discount either way. */
  discountPercent: number | null;
  discountAmount: number;
  lineTotal: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's PurchaseOrderLineDto. Null taxCode/taxRatePercent for a tax-exempt line. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  taxInclusive: boolean;
  trackingMode?: 'NONE' | 'BATCH' | 'SERIAL';
}

/** Task: "Inventory depth ... a stock-receiving flow that increments quantity with a paper trail." Mirrors the backend's PurchaseOrderDto. */
export interface PurchaseOrder {
  id: number;
  orderNumber: string;
  supplierId: number | null;
  supplierName: string | null;
  status: PurchaseOrderStatus;
  orderDate: string;
  expectedDate: string | null;
  notes: string | null;
  lines: PurchaseOrderLine[];
  /** Fourier: "I have realized that for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's PurchaseOrderDto (SupplierPaymentService.totalCost/netCost/taxCost). PurchaseOrder had no total concept at all before this. */
  totalAmount: number;
  subtotalAmount: number;
  taxAmount: number;
  /** Fourier: "Same for discount ... it will be per line" — mirrors the backend's SupplierPaymentService.discountCost. Sum of every line's discountAmount; purely informational, already folded into subtotalAmount/totalAmount. */
  discountAmount: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole order (GLOBAL mode only). Zero when discount was entered per line instead, or not at all. */
  discountPercent: number;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was actually entered. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. */
  discountFixedAmount: number | null;
  /** Task 5: "Add Record payment on purchase order view or list (if not paid)" — mirrors the backend's SupplierPaymentService.amountPaidForOrder/amountOwedForOrder. */
  amountPaid: number;
  amountOutstanding: number;
  /** Task 4: "Supplier Purchase Return" — mirrors the backend's SupplierPaymentService.returnedAmountForOrder/refundDueFromSupplierForOrder. */
  amountReturned: number;
  /** What the supplier now owes the store back — only nonzero once a return brings the net owed amount below what's already been paid. Informational only; nothing is collected automatically. */
  refundDue: number;
  /** Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ... equivalent with Invoices)" — null means no bill has been generated for this order yet; drives "Generate bill" vs "View bill" on the detail screen, same shape as a Sale Order's own invoice fields. */
  billId: number | null;
  billNumber: string | null;
  /** Multi-warehouse phase 2 — the warehouse this order receives into (RECEIVING location). Always filled in: the primary warehouse when none was chosen (older orders). */
  receivingWarehouseId: number;
  receivingWarehouseName: string;
  /** Branch of the receiving warehouse; null on older rows (= head office). */
  branchId?: number | null;
  /** Landed costs (freight, duty, ...) added to product cost while receiving this order. */
  landedCostTotal?: number;
}

/** Body for POST/PUT /api/purchase-orders — see the backend's PurchaseOrderRequest. */
export interface PurchaseOrderPayload {
  supplierId: number;
  expectedDate?: string | null;
  notes?: string | null;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage for the whole order (GLOBAL mode only). Mutually exclusive with each line's own discountPercent in practice. */
  discountPercent?: number | null;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — 'PERCENT' (default) or 'FIXED_AMOUNT'. */
  discountType?: 'PERCENT' | 'FIXED_AMOUNT' | null;
  /** The flat currency amount, used only when discountType is 'FIXED_AMOUNT'. */
  discountFixedAmount?: number | null;
  /** Multi-warehouse phase 2 — optional; omitted/null means the primary warehouse. Only sent when the store has more than one active warehouse. */
  receivingWarehouseId?: number | null;
  lines: { productId: number; quantityOrdered: number; unitCost: number | null; discountPercent?: number | null }[];
}

/** One line of a PurchaseReturn — mirrors the backend's PurchaseReturnLineDto. */
export interface PurchaseReturnLine {
  id: number;
  orderLineId: number | null;
  productName: string | null;
  quantity: number;
  refundAmount: number | null;
}

/**
 * Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
 * Module" — a return sent back to the supplier against a SENT/PARTIALLY_RECEIVED/RECEIVED
 * PurchaseOrder. Mirrors the backend's PurchaseReturnDto. Informational only, same as
 * SalesOrderReturn — `refundTotal` is what the supplier now owes the store back, not an
 * instruction to collect anything.
 */
export interface PurchaseReturn {
  id: number;
  purchaseOrderId: number | null;
  purchaseOrderNumber: string | null;
  supplierId: number | null;
  supplierName: string | null;
  returnNumber: string | null;
  returnDate: string;
  reason: string | null;
  refundTotal: number;
  lines: PurchaseReturnLine[];
}

/** Body for POST /api/purchase-returns — see the backend's ProcessPurchaseReturnRequest. */
export interface ProcessPurchaseReturnPayload {
  purchaseOrderId: number;
  lines: { lineId: number; quantity: number }[];
  reason?: string | null;
}

/**
 * Mirrors the backend's StockAdjustmentReason enum. INITIAL_STOCK (Fourier: "when creating product
 * if the stock quantity is set then it should impact inventory ... displayed into Stock Movement
 * History") is system-set only — StockAdjustmentFormComponent's own reason dropdown never offers
 * it, same as the backend enum's own javadoc explains.
 */
export type StockAdjustmentReason = 'DAMAGE' | 'THEFT' | 'EXPIRY' | 'RECOUNT' | 'OTHER' | 'INITIAL_STOCK';

/** Task: "Inventory depth ... stock adjustments/write-offs with a reason code." Mirrors the backend's StockAdjustmentDto. */
/** Fourier: "Build a full permissions system" — Stock Adjustments now mirror RestaurantWastageStatus's own PENDING/CONFIRMED/REJECTED lifecycle. */
export type StockAdjustmentStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';

export interface StockAdjustment {
  id: number;
  productId: number | null;
  productName: string;
  quantityDelta: number;
  /** A preview until CONFIRMED, the real value once CONFIRMED — see the backend's StockAdjustment class javadoc. */
  stockBefore: number;
  stockAfter: number;
  reason: StockAdjustmentReason;
  note: string | null;
  status: StockAdjustmentStatus;
  createdAt: string;
  /** Null until an admin (or a PERM_APPROVE_STOCK_ADJUSTMENT holder) confirms/rejects it. */
  confirmedAt: string | null;
  /** Multi-warehouse phase 2 — null for an adjustment against the product's plain primary-warehouse stock (all older rows); otherwise the location it targeted, with names snapshotted at logging time. */
  locationId: number | null;
  locationName: string | null;
  warehouseName: string | null;
}

/**
 * Fourier: "multi-location/multi-warehouse tracking ... start on the foundation phase" — Admin >
 * Inventory > Warehouses. Mirrors the backend's WarehouseDto. Foundation phase only: nothing else
 * in the app lets you pick a warehouse yet (every sale/adjustment/receipt still implicitly targets
 * whichever one is `primary`) — this screen only manages the list itself.
 */
export interface Warehouse {
  id: number;
  name: string;
  address: string | null;
  active: boolean;
  /** Exactly one warehouse ever has this set — seeded automatically, never chosen here. See WarehouseRequest's own note. */
  primary: boolean;
  /** Per-warehouse low-stock threshold; null = uses the store-wide default. Drives this warehouse's low-stock alert, dashboard list and the Stock On Hand report's status. */
  lowStockThreshold: number | null;
  /** Owning branch (see Branch). */
  branchId: number | null;
  branchName: string | null;
}

/** Deliberately has no `primary` field — see the backend's WarehouseRequest javadoc for why that flag isn't admin-settable. */
export interface WarehouseRequest {
  name: string;
  address: string | null;
  active: boolean;
  /** Optional (0 or more); null = use the store-wide default. */
  lowStockThreshold?: number | null;
  /** Owning branch; omit/null = head office on create, unchanged on update. */
  branchId?: number | null;
}

/** Fourier: "How business branches can be introduced?" — a trading location above warehouses; mirrors the backend's BranchDto. Exactly one is the head office (seeded, never deactivatable); records with no branch belong to it. */
export interface Branch {
  id: number;
  code: string;
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  receiptHeader: string | null;
  taxId: string | null;
  efrisBranchCode: string | null;
  documentPrefix: string | null;
  active: boolean;
  headquarters: boolean;
}

/** What the till prints for a branch's receipt header (readable by any signed-in user). Null fields mean "use the business-wide value". */
export interface BranchReceiptInfo {
  id: number;
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  receiptHeader: string | null;
  taxId: string | null;
}

/** Deliberately has no `headquarters` field — never admin-settable. */
export interface BranchRequest {
  code: string;
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  receiptHeader: string | null;
  taxId: string | null;
  efrisBranchCode: string | null;
  documentPrefix: string | null;
  active: boolean;
}

/** Fourier: "location type of RECEIVING,SALES,STORAGE,DAMAGED" — mirrors the backend's LocationType enum. Replaces the old isDefault flag entirely; see WarehouseLocation's own comment below. */
export type LocationType = 'RECEIVING' | 'SALES' | 'STORAGE' | 'DAMAGED';

/** Fourier: "Add warehouse location and allowing a warehouse to have a default 'Main Stock' location", later refined by "location type of RECEIVING,SALES,STORAGE,DAMAGED" — a sub-area within one Warehouse. Mirrors the backend's WarehouseLocationDto. */
export interface WarehouseLocation {
  id: number;
  warehouseId: number;
  name: string;
  active: boolean;
  /** Drives real behavior — see LocationType and the backend's own javadoc on it: exactly one RECEIVING per warehouse, at least one active SALES, DAMAGED excluded from sellable stock. May briefly be null only mid-migration, never for a location any screen actually renders. */
  type: LocationType | null;
}

export interface WarehouseLocationRequest {
  name: string;
  type: LocationType;
}

/** Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers" — mirrors the backend's StockTransferStatus enum. */
export type StockTransferStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'IN_TRANSIT' | 'RECEIVED';

/** Admin > Inventory > Stock Transfers — Fourier: "User can make stock transfer to any location." Mirrors the backend's StockTransferDto (location-to-location, same or different warehouse). */
export interface StockTransfer {
  id: number;
  productId: number | null;
  productName: string;
  fromLocationId: number | null;
  fromLocationName: string;
  fromWarehouseName: string;
  toLocationId: number | null;
  toLocationName: string;
  toWarehouseName: string;
  quantity: number;
  note: string | null;
  status: StockTransferStatus;
  createdAt: string;
  /** Null until an admin (or a PERM_APPROVE_STOCK_TRANSFER holder) confirms/rejects it. */
  confirmedAt: string | null;
  /** Branch of each end's warehouse (snapshot); null on transfers made before branches existed. */
  fromBranchId?: number | null;
  fromBranchName?: string | null;
  toBranchId?: number | null;
  toBranchName?: string | null;
  /** True when the two ends belong to different branches. */
  interBranch?: boolean;
  /** Inter-branch only: when it was dispatched / received and how many units arrived. */
  dispatchedAt?: string | null;
  receivedAt?: string | null;
  receivedQuantity?: number | null;
  receiveNote?: string | null;
}

/** Fourier: "Restaurants need a dedicated wastage module." Mirrors the backend's RestaurantWastageReason enum. */
export type RestaurantWastageReason = 'SPOILAGE' | 'BURNT_OR_OVERCOOKED' | 'DROPPED_OR_SPILLED' | 'EXPIRED' | 'PREP_TRIM' | 'CUSTOMER_COMPLAINT' | 'OTHER';

/** Fourier: "Restaurant wastage should be confirmed by admin..." Mirrors the backend's RestaurantWastageStatus enum. */
export type RestaurantWastageStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';

/** Mirrors the backend's RestaurantWastageDto. */
export interface RestaurantWastage {
  id: number;
  productId: number | null;
  productName: string;
  quantity: number;
  reason: RestaurantWastageReason;
  note: string | null;
  status: RestaurantWastageStatus;
  stockBefore: number | null;
  stockAfter: number | null;
  costImpact: number | null;
  createdAt: string;
  confirmedAt: string | null;
}

/** Fourier: "The Purchase order should flow same workflow as Sale order (with Bills ... equivalent with Invoices)" — mirrors the backend's PayableType enum. */
export type PayableType = 'PURCHASE_ORDER' | 'BILL';

/** Task: "Add Supplier payment" — one row of a supplier's outstanding-payables list, the accounts-payable mirror of OutstandingOrder. Mirrors the backend's OutstandingPurchaseOrderDto. */
export interface OutstandingPurchaseOrder {
  purchaseOrderId: number;
  /** Which kind of document `purchaseOrderId` actually targets — see PayableType's own javadoc. */
  payableType: PayableType;
  orderNumber: string | null;
  orderDate: string;
  expectedDate: string | null;
  totalCost: number;
  amountOutstanding: number;
}

/** One allocation of a supplier payment onto a specific purchase order or bill — part of SupplierPayment. Mirrors the backend's SupplierPaymentDto.AllocationDto. */
export interface SupplierPaymentAllocation {
  purchaseOrderId: number;
  payableType: PayableType;
  orderNumber: string | null;
  amountApplied: number;
}

/** A recorded supplier payment plus its allocations — the accounts-payable mirror of CustomerPayment. Mirrors the backend's SupplierPaymentDto. */
export interface SupplierPayment {
  id: number;
  supplierId: number;
  supplierName: string;
  amount: number;
  paidAt: string;
  /** See the backend SupplierPayment.method's javadoc — null unless recorded via BillRecordPaymentComponent. */
  method: string | null;
  note: string | null;
  receiptNumber: string | null;
  recordedByName: string;
  allocations: SupplierPaymentAllocation[];
  supplierBalanceAfter: number;
}

/** Request body for POST /api/suppliers/{id}/payments. Mirrors the backend's RecordSupplierPaymentRequest. */
export interface RecordSupplierPaymentRequest {
  amount: number;
  note?: string | null;
  allocations: { purchaseOrderId: number; payableType?: PayableType; amount: number }[];
}

/** Mirrors the backend's SalesOrderStatus enum — see that class's javadoc for the DRAFT -> CONFIRMED -> (PARTIALLY_FULFILLED ->)* FULFILLED / CANCELLED lifecycle. */
export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_FULFILLED' | 'FULFILLED' | 'CANCELLED';

/** One line of a SalesOrder — mirrors the backend's SalesOrderLineDto. */
export interface SalesOrderLine {
  trackingMode?: 'NONE' | 'BATCH' | 'SERIAL';
  id: number;
  productId: number | null;
  productName: string;
  quantityOrdered: number;
  quantityFulfilled: number;
  unitPrice: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the discount percentage actually applied to this line: either its own entered value (PER_LINE mode) or the order's single header-level value applied uniformly (GLOBAL mode). Null/0 for a line with no discount either way. */
  discountPercent: number | null;
  discountAmount: number;
  lineTotal: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's SalesOrderLineDto. Null taxCode/taxRatePercent for a tax-exempt line. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  taxInclusive: boolean;
}

/**
 * Task: "Add Sale order and Customer payment(So that we can have POS & Sales)" — a customer order
 * taken outside the register, entirely separate from a POS Order. Mirrors the backend's
 * SalesOrderDto. `totalAmount` is the sum of every line's `quantityOrdered * unitPrice` — what the
 * customer owes once this order is CONFIRMED/PARTIALLY_FULFILLED/FULFILLED (zero owed while DRAFT
 * or after CANCELLED).
 */
export interface SalesOrder {
  id: number;
  orderNumber: string;
  customerId: number | null;
  customerName: string | null;
  status: SalesOrderStatus;
  orderDate: string;
  expectedDate: string | null;
  dueDate: string | null;
  notes: string | null;
  totalAmount: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's SalesOrderDto. `totalAmount` above is now `subtotalAmount + taxAmount`. */
  subtotalAmount: number;
  taxAmount: number;
  /** Fourier: "Same for discount ... it will be per line" — sum of every line's discountAmount; purely informational, already folded into subtotalAmount/totalAmount. */
  discountAmount: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole order (GLOBAL mode only). Zero when discount was entered per line instead, or not at all. */
  discountPercent: number;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was actually entered. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. */
  discountFixedAmount: number | null;
  lines: SalesOrderLine[];
  /** Task 3: "Sale Order Return" — mirrors the backend's CustomerCreditService.amountPaidForSalesOrder/outstandingForSalesOrder/returnedAmountForSalesOrder/refundDueForSalesOrder. */
  amountPaid: number;
  amountOutstanding: number;
  amountReturned: number;
  /** What the store now owes this customer back — only nonzero once a return brings the net amount owed below what they already paid. Informational only; nothing is paid out automatically. */
  refundDue: number;
  /** Multi-warehouse: distinct "Warehouse / Location" labels this order was fulfilled from (empty if none recorded). */
  fulfilledFrom: string[];
  /** Branch this order is sold from; null on older rows (= head office). */
  branchId?: number | null;
}

/** Body for POST/PUT /api/sales-orders — see the backend's SalesOrderRequest. */
export interface SalesOrderPayload {
  customerId: number;
  expectedDate?: string | null;
  notes?: string | null;
  /** Optional branch; omit/null = head office (or unchanged on edit). */
  branchId?: number | null;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage for the whole order (GLOBAL mode only). Mutually exclusive with each line's own discountPercent in practice. */
  discountPercent?: number | null;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — 'PERCENT' (default) or 'FIXED_AMOUNT'. */
  discountType?: 'PERCENT' | 'FIXED_AMOUNT' | null;
  /** The flat currency amount, used only when discountType is 'FIXED_AMOUNT'. */
  discountFixedAmount?: number | null;
  lines: { productId: number; quantityOrdered: number; unitPrice: number | null; discountPercent?: number | null }[];
}

/** Body for POST /api/sales-orders/{id}/fulfill — see the backend's FulfillSalesOrderRequest. */
export interface FulfillSalesOrderRequest {
  /** Multi-warehouse phase 3 — the SALES location the goods leave from. Required (and not in the Main warehouse) once the store's multi-warehouse setting is on; optional otherwise. */
  locationId?: number | null;
  lines: { lineId: number; quantityFulfilled: number; serialNumbers?: string[] }[];
}

/** One line of a SalesOrderReturn — mirrors the backend's SalesOrderReturnLineDto. */
export interface SalesOrderReturnLine {
  id: number;
  orderLineId: number | null;
  productName: string | null;
  quantity: number;
  refundAmount: number | null;
}

/**
 * Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" — a
 * return processed against a confirmed/partially-fulfilled/fulfilled SalesOrder. Mirrors the
 * backend's SalesOrderReturnDto. Unlike a POS SalesReturn there is no `refundMethod` — this is
 * deliberately informational only (see the backend SalesOrderReturn's class javadoc): `refundTotal`
 * is a record of what's now owed back to the customer, not an instruction to pay anything out.
 */
export interface SalesOrderReturn {
  id: number;
  salesOrderId: number | null;
  salesOrderNumber: string | null;
  customerId: number | null;
  customerName: string | null;
  returnNumber: string | null;
  returnDate: string;
  reason: string | null;
  refundTotal: number;
  lines: SalesOrderReturnLine[];
  /** Multi-warehouse phase 3 — where the goods went back; null for older returns (primary warehouse stock). */
  locationId: number | null;
  locationName: string | null;
  warehouseName: string | null;
}

/** Body for POST /api/sales-order-returns — see the backend's ProcessSalesOrderReturnRequest. */
export interface ProcessSalesOrderReturnPayload {
  salesOrderId: number;
  /** Multi-warehouse phase 3 — optional; omitted = the location the order was last fulfilled from. */
  locationId?: number | null;
  lines: { lineId: number; quantity: number }[];
  reason?: string | null;
}

// ---- Manufacturing Module (Fourier: "Add Manufacturing Module") ----
// "1.Bill of Materials 2.Production plans 3.Manufacturing orders 4.Indirects Costs
// 5.Workstations 5.Settings (Production routings etc)" — "Am new on Manufacturing but I need
// full features". Mirrors the backend com.posapp.manufacturing package's DTOs exactly.

/** Where a routing operation happens on the shop floor. Mirrors the backend's WorkstationDto. */
export interface Workstation {
  id: number;
  code: string;
  name: string;
  description: string | null;
  hourlyRate: number;
  active: boolean;
}

/** Body for POST/PUT /api/manufacturing/workstations — see the backend's WorkstationRequest. */
export interface WorkstationPayload {
  code?: string | null;
  name: string;
  description?: string | null;
  hourlyRate: number;
  active: boolean;
}

/** Mirrors the backend's OverheadAllocationMethod enum — see that class's javadoc for how each turns into an amount charged against a production run. */
export type OverheadAllocationMethod = 'FLAT' | 'PER_UNIT' | 'PERCENT_OF_MATERIAL_COST';

/** A named "Indirect Costs" pool (Fourier's own term). Mirrors the backend's OverheadCostDto. */
export interface OverheadCost {
  id: number;
  code: string;
  name: string;
  allocationMethod: OverheadAllocationMethod;
  rate: number;
  active: boolean;
}

/** Body for POST/PUT /api/manufacturing/overhead-costs — see the backend's OverheadCostRequest. */
export interface OverheadCostPayload {
  code?: string | null;
  name: string;
  allocationMethod: OverheadAllocationMethod;
  rate: number;
  active: boolean;
}

/** One ordered step of a Routing — mirrors the backend's RoutingDto.OperationDto. */
export interface RoutingOperation {
  id: number;
  sequence: number;
  name: string;
  workstationId: number | null;
  workstationName: string | null;
  estimatedMinutesPerUnit: number;
}

/** "Settings (Production routings etc)" (Fourier's own naming). Mirrors the backend's RoutingDto. */
export interface Routing {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
  operations: RoutingOperation[];
}

/** Body for POST/PUT /api/manufacturing/routings — see the backend's RoutingRequest. */
export interface RoutingPayload {
  name: string;
  description?: string | null;
  active: boolean;
  operations: { sequence: number; name: string; workstationId: number | null; estimatedMinutesPerUnit: number }[];
}

/** One component line of a Bom — mirrors the backend's BomDto.LineDto. */
export interface BomLine {
  id: number;
  componentId: number;
  componentName: string;
  componentSku: string | null;
  quantityPerUnit: number;
  componentCostPrice: number;
}

/**
 * "Bill of Materials" — the recipe for one finished-good Product. Mirrors the backend's BomDto.
 * `estimatedUnitMaterialCost` is computed at TODAY's component cost prices — an estimate, not a
 * snapshot (an actual production run's own cost is locked in on its ManufacturingOrderRun instead).
 */
export interface Bom {
  id: number;
  productId: number;
  productName: string;
  code: string;
  name: string | null;
  routingId: number | null;
  routingName: string | null;
  active: boolean;
  estimatedUnitMaterialCost: number;
  lines: BomLine[];
}

/** Body for POST/PUT /api/manufacturing/boms — see the backend's BomRequest. */
export interface BomPayload {
  productId: number;
  name?: string | null;
  routingId?: number | null;
  active: boolean;
  lines: { componentId: number; quantityPerUnit: number }[];
}

/** Mirrors the backend's ManufacturingOrderStatus enum — see that class's javadoc for the DRAFT -> CONFIRMED -> (PARTIALLY_PRODUCED ->)* PRODUCED / CANCELLED lifecycle. */
export type ManufacturingOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_PRODUCED' | 'PRODUCED' | 'CANCELLED';

/** One overhead pool applied to a Manufacturing Order, snapshotted at add time. Mirrors the backend's ManufacturingOrderDto.OverheadLineDto. */
export interface ManufacturingOrderOverheadLine {
  overheadCostId: number | null;
  name: string;
  method: OverheadAllocationMethod;
  rate: number;
}

/** One component consumed by a production run, snapshotted at the time it ran. Mirrors the backend's ManufacturingOrderDto.ConsumptionDto. */
export interface ManufacturingOrderConsumption {
  componentId: number | null;
  componentName: string;
  quantityConsumed: number;
  unitCost: number;
  /** Null when the component wasn't stock-tracked at the time — see Product.trackStock. */
  stockBefore: number | null;
  stockAfter: number | null;
}

/** One partial (or full) production run against a Manufacturing Order. Mirrors the backend's ManufacturingOrderDto.RunDto. */
export interface ManufacturingOrderRun {
  id: number;
  producedAt: string;
  quantityProduced: number;
  materialCost: number;
  overheadCost: number;
  totalCost: number;
  /** Null when the finished good wasn't stock-tracked at the time — see Product.trackStock. */
  stockBefore: number | null;
  stockAfter: number | null;
  consumptions: ManufacturingOrderConsumption[];
}

/**
 * "Manufacturing orders" — mirrors the backend's ManufacturingOrderDto. `totalMaterialCost`/
 * `totalOverheadCost`/`totalCost` sum across every run; `unitCost` is `totalCost / quantityProduced`,
 * null while nothing has been produced yet.
 */
export interface ManufacturingOrder {
  id: number;
  orderNumber: string;
  productId: number;
  productName: string;
  bomId: number | null;
  bomCode: string | null;
  routingId: number | null;
  routingName: string | null;
  quantityPlanned: number;
  quantityProduced: number;
  status: ManufacturingOrderStatus;
  scheduledDate: string | null;
  completedAt: string | null;
  note: string | null;
  sourcePlanLineId: number | null;
  overheads: ManufacturingOrderOverheadLine[];
  runs: ManufacturingOrderRun[];
  totalMaterialCost: number;
  totalOverheadCost: number;
  totalCost: number;
  unitCost: number | null;
  /** Multi-warehouse phase 4 — null for an order on the primary warehouse's normal stock. */
  locationId: number | null;
  locationName: string | null;
  warehouseName: string | null;
}

/** Body for POST /api/manufacturing/orders — see the backend's CreateManufacturingOrderRequest. */
export interface CreateManufacturingOrderPayload {
  bomId: number;
  quantityPlanned: number;
  scheduledDate?: string | null;
  note?: string | null;
  overheadCostIds?: number[] | null;
  /** Multi-warehouse phase 4 — optional; where components are consumed from and finished goods land. */
  locationId?: number | null;
}

/** Body for POST /api/manufacturing/orders/{id}/produce — see the backend's ProduceManufacturingOrderRequest. */
export interface ProduceManufacturingOrderPayload {
  quantity: number;
}

/** How much of one component would be short if a plan line's suggestedQuantity were produced right now, against today's stock. Mirrors the backend's ComponentShortfallDto. */
export interface ComponentShortfall {
  componentId: number;
  componentName: string;
  trackStock: boolean;
  requiredQuantity: number;
  availableQuantity: number;
  shortfall: number;
}

/** One line of a Production Plan — mirrors the backend's ProductionPlanDto.LineDto. */
export interface ProductionPlanLine {
  id: number;
  productId: number;
  productName: string;
  bomId: number | null;
  bomCode: string | null;
  demandFromSales: number;
  demandFromLowStock: number;
  suggestedQuantity: number;
  generatedManufacturingOrderId: number | null;
  generatedManufacturingOrderNumber: string | null;
  /** Live, as of when this plan was read — see the backend ProductionPlanDto's class javadoc. */
  componentShortfalls: ComponentShortfall[];
}

/**
 * "Production plans" (full MRP-style, per Fourier's "I need full features" answer) — aggregates
 * demand from unfulfilled Sale Orders and low-stock finished goods. Mirrors the backend's
 * ProductionPlanDto.
 */
export interface ProductionPlan {
  id: number;
  planNumber: string;
  generatedAt: string;
  lowStockThreshold: number;
  note: string | null;
  lines: ProductionPlanLine[];
}

/** Body for POST /api/manufacturing/production-plans/generate — see the backend's GenerateProductionPlanRequest. */
export interface GenerateProductionPlanPayload {
  lowStockThreshold: number;
  note?: string | null;
}

/** Body for POST /api/manufacturing/production-plans/{planId}/lines/{lineId}/generate-order — see the backend's GenerateOrderFromPlanLineRequest. */
export interface GenerateOrderFromPlanLinePayload {
  quantity: number;
  scheduledDate?: string | null;
  overheadCostIds?: number[] | null;
}

// ── Quotation / Invoice / Purchase Requisition ──────────────────────────────
// Fourier: "Build Quotation, Invoice, and the purchase-requisition approval gate ... without
// touching warehousing." Mirrors the backend's own three new modules — see QuotationService,
// InvoiceService and PurchaseRequisitionService's class javadocs for how each actually differs
// and how they connect into the existing Sale/POS modules.

/** Mirrors the backend's QuotationStatus enum — DRAFT -> SENT -> ACCEPTED/REJECTED/EXPIRED, or ACCEPTED -> CONVERTED. */
export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CONVERTED';

/** One line of a Quotation — mirrors the backend's QuotationLineDto. */
export interface QuotationLine {
  id: number;
  productId: number | null;
  productName: string;
  quantity: number;
  unitPrice: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the discount percentage actually applied to this line: either its own entered value (PER_LINE mode) or the quotation's single header-level value applied uniformly (GLOBAL mode). Null/0 for a line with no discount either way. */
  discountPercent: number | null;
  discountAmount: number;
  lineTotal: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's QuotationLineDto. Null taxCode/taxRatePercent for a tax-exempt line. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  taxInclusive: boolean;
}

/**
 * A non-binding price offer to a customer (Fourier: "Add Quotation ... without touching
 * warehousing"). Mirrors the backend's QuotationDto. `totalAmount` is the sum of every line's
 * `quantity * unitPrice` — purely informational, since nothing is owed until this is accepted and
 * converted into a real SalesOrder.
 */
export interface Quotation {
  id: number;
  quoteNumber: string;
  customerId: number | null;
  customerName: string | null;
  /** Fourier: "sending ... quotations ... to the customer's email" — pre-fills the "email this" prompt. Null for no customer, or a customer with none on file. */
  customerEmail: string | null;
  status: QuotationStatus;
  quoteDate: string;
  validUntil: string | null;
  notes: string | null;
  totalAmount: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's QuotationDto. `totalAmount` above is now `subtotalAmount + taxAmount`. */
  subtotalAmount: number;
  taxAmount: number;
  /** Fourier: "Same for discount ... it will be per line" — sum of every line's discountAmount; purely informational, already folded into subtotalAmount/totalAmount. */
  discountAmount: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole quotation (GLOBAL mode only). Zero when discount was entered per line instead, or not at all. */
  discountPercent: number;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was actually entered. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. */
  discountFixedAmount: number | null;
  lines: QuotationLine[];
  /** Set once this quote is CONVERTED — see the backend Quotation's class javadoc for why this lives here rather than on SalesOrder itself. */
  convertedSalesOrderId: number | null;
  convertedSalesOrderNumber: string | null;
  /** The branch this record belongs to; absent/null on rows older than branches (= head office). */
  branchId?: number | null;
}

/** Body for POST/PUT /api/quotations — see the backend's QuotationRequest. */
export interface QuotationPayload {
  customerId: number;
  validUntil?: string | null;
  notes?: string | null;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage for the whole quotation (GLOBAL mode only). Mutually exclusive with each line's own discountPercent in practice. */
  discountPercent?: number | null;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — 'PERCENT' (default) or 'FIXED_AMOUNT'. */
  discountType?: 'PERCENT' | 'FIXED_AMOUNT' | null;
  /** The flat currency amount, used only when discountType is 'FIXED_AMOUNT'. */
  discountFixedAmount?: number | null;
  lines: { productId: number; quantity: number; unitPrice: number | null; discountPercent?: number | null }[];
  /** Optional branch; omitted = head office (or unchanged when editing a draft). */
  branchId?: number | null;
}

/** Mirrors the backend's InvoiceStatus enum — DRAFT (standalone, still editable) -> ISSUED -> VOID. A Sales-Order-derived invoice skips straight to ISSUED. */
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'VOID';

/** One line of an Invoice — mirrors the backend's InvoiceLineDto. */
export interface InvoiceLine {
  id: number;
  productId: number | null;
  productName: string;
  /** Fourier: "On Invoice Line add product description text (by default it should pick the product description)" — defaults from the product's own description at add-line time, editable per line afterward. */
  description: string | null;
  quantity: number;
  unitPrice: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the discount percentage actually applied to this line: either its own entered value (PER_LINE mode) or the invoice's single header-level value applied uniformly (GLOBAL mode), or carried verbatim from the Sales Order line it was generated from. Null/0 for a line with no discount either way. */
  discountPercent: number | null;
  discountAmount: number;
  lineTotal: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's InvoiceLineDto. Null taxCode/taxRatePercent for a tax-exempt line. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  taxInclusive: boolean;
}

/**
 * A formal, numbered invoice generated from one SalesOrder (Fourier: "Add Invoice ... without
 * touching warehousing"). Mirrors the backend's InvoiceDto. Exactly one is ever issued per Sales
 * Order — voiding it doesn't free the order up for a replacement (see the backend Invoice's class
 * javadoc). Voiding never changes what the customer owes on that Sales Order — that's still
 * tracked the same way it always was, independent of this document's own status.
 */
export interface Invoice {
  id: number;
  invoiceNumber: string;
  salesOrderId: number | null;
  salesOrderNumber: string | null;
  /** Fourier: "Subscriptions Module" — set only on an invoice auto-generated by the subscription billing sweep; see the backend Invoice.subscriptionId's javadoc. */
  subscriptionId: number | null;
  /** Fourier: "Add Recurring Invoice" — set only on an invoice auto-generated by a recurring invoice template's sweep or manual "run now"; see the backend Invoice.recurringInvoiceTemplateId's javadoc. */
  recurringInvoiceTemplateId: number | null;
  customerId: number | null;
  customerName: string | null;
  /** Fourier: "sending ... invoices ... to the customer's email" — pre-fills the "email this" prompt. Null for no customer, or a customer with none on file. */
  customerEmail: string | null;
  status: InvoiceStatus;
  invoiceDate: string;
  dueDate: string | null;
  /** Fourier: "create an invoice from the last end date" — the billing cycle this invoice covers; null for a non-subscription invoice. See the backend Invoice.periodStart's own javadoc. */
  periodStart: string | null;
  periodEnd: string | null;
  notes: string | null;
  totalAmount: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's InvoiceDto. `totalAmount` above is now `subtotalAmount + taxAmount`. */
  subtotalAmount: number;
  taxAmount: number;
  /** Fourier: "Same for discount ... it will be per line" — sum of every line's discountAmount; purely informational, already folded into subtotalAmount/totalAmount. */
  discountAmount: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole invoice (GLOBAL mode only, or carried verbatim from the originating Sales Order). Zero when discount was entered per line instead, or not at all. */
  discountPercent: number;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was actually entered. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  /** The flat currency amount actually typed, when discountType is FIXED_AMOUNT; null otherwise. */
  discountFixedAmount: number | null;
  lines: InvoiceLine[];
  voidedAt: string | null;
  voidReason: string | null;
  /** Fourier: "Add credit note Menu (Void) Where we can printout them" — null until this invoice is voided; see the backend Invoice.creditNoteNumber's javadoc. */
  creditNoteNumber: string | null;
  /** Fourier: "CANCEL/CREDIT PAID INVOICE ... Refund OR Customer Credit" — null unless cancelled via cancelPaidInvoice; see the backend Invoice.paymentDisposition's javadoc. */
  paymentDisposition: PaymentDisposition | null;
  /** Only set when paymentDisposition is REFUND. */
  refundMethod: string | null;
  /** Fourier: "record payment on invoice" — zero while DRAFT or once VOID; only an ISSUED invoice is ever payable. */
  amountPaid: number;
  amountOutstanding: number;
  /** The branch this record belongs to; absent/null on rows older than branches (= head office). */
  branchId?: number | null;
}

/** Mirrors the backend's PaymentDisposition enum — see CancelPaidInvoiceRequest's javadoc. */
export type PaymentDisposition = 'REFUND' | 'CUSTOMER_CREDIT';

/** Body for POST /api/invoices/{id}/cancel-paid — see the backend InvoiceService.cancelPaidInvoice's javadoc. refundMethod is only required when disposition is REFUND. */
export interface CancelPaidInvoiceRequest {
  disposition: PaymentDisposition;
  refundMethod?: string | null;
  reason?: string | null;
}

// ── Bill / Refund ────────────────────────────────────────────────────────────
// Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
// equivalent with Invoices and Credit note)." The accounts-payable mirror of Invoice/credit notes
// just above — see the backend Bill/BillStatus's own class javadocs for exactly how this differs
// from Invoice (every bill is generated from a Purchase Order; there is no standalone bill, and a
// paid one is cancelled via a Purchase Return rather than a cancel-paid path of its own).

/** Mirrors the backend's BillStatus enum — DRAFT (auto-generated once a purchase order is fully received) -> ISSUED -> VOID. A manually-generated bill skips straight to ISSUED. */
export type BillStatus = 'DRAFT' | 'ISSUED' | 'VOID';

/** One line of a Bill — mirrors the backend's BillLineDto. */
export interface BillLine {
  id: number;
  productId: number | null;
  productName: string;
  quantity: number;
  unitCost: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the effective discount percentage actually applied to this line (PER_LINE or GLOBAL, whichever applied on the originating Purchase Order), carried verbatim from the Purchase Order line it was generated from. Null/0 for a line with no discount either way. */
  discountPercent: number | null;
  discountAmount: number;
  lineTotal: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's BillLineDto. Null taxCode/taxRatePercent for a tax-exempt line. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxTotal: number;
  taxInclusive: boolean;
}

/**
 * A formal, numbered bill generated from one PurchaseOrder — mirrors the backend's BillDto.
 * Exactly one is ever generated per Purchase Order (see the backend Bill's class javadoc).
 * Voiding never changes what's owed on the underlying Purchase Order by itself — that figure
 * redirects through this bill's own paid/outstanding figures instead (see the backend
 * SupplierPaymentService.amountOwedForOrder's javadoc).
 */
export interface Bill {
  id: number;
  billNumber: string;
  purchaseOrderId: number | null;
  purchaseOrderNumber: string | null;
  supplierId: number | null;
  supplierName: string | null;
  status: BillStatus;
  billDate: string;
  notes: string | null;
  totalAmount: number;
  /** Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the backend's BillDto. `totalAmount` above is now `subtotalAmount + taxAmount`. */
  subtotalAmount: number;
  taxAmount: number;
  /** Fourier: "Same for discount ... it will be per line" — sum of every line's discountAmount; purely informational, already folded into subtotalAmount/totalAmount. */
  discountAmount: number;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage entered for the whole bill (carried verbatim from the originating Purchase Order). Zero when discount was entered per line instead, or not at all. */
  discountPercent: number;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — how discountPercent above was actually entered on the originating Purchase Order, carried verbatim. */
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  /** The flat currency amount actually typed on the originating Purchase Order, when discountType is FIXED_AMOUNT; null otherwise. */
  discountFixedAmount: number | null;
  lines: BillLine[];
  voidedAt: string | null;
  voidReason: string | null;
  /** Fourier: "Refunds equivalent with Credit note" — null until this bill is voided; see the backend Bill.refundNumber's javadoc. */
  refundNumber: string | null;
  /** Zero while DRAFT or once VOID; only an ISSUED bill is ever payable. */
  amountPaid: number;
  amountOutstanding: number;
  /** The branch this record belongs to; absent/null on rows older than branches (= head office). */
  branchId?: number | null;
}

/** Body for POST /api/bills/{id}/void — see the backend BillService.voidBill's javadoc. */
export interface VoidBillRequest {
  reason?: string | null;
}

/** Body for POST /api/bills/{id}/payments — the whole amount always applies to this one bill. Mirrors the backend's RecordBillPaymentRequest. */
export interface RecordBillPaymentRequest {
  amount: number;
  /** Fourier: "Add payment method when recording bill payment" — a required active PaymentMethodOption.code. */
  method: string;
  note?: string | null;
}

/** Fourier: "Subscriptions Module" — mirrors the backend's BillingInterval enum. */
export type BillingInterval = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

/** A plan a customer can subscribe to (Admin > Sales > Subscription plans) — see the backend SubscriptionPlan's javadoc. */
export interface SubscriptionPlan {
  id: number;
  name: string;
  description: string | null;
  price: number;
  billingInterval: BillingInterval;
  /** Fourier: "subscription plan should also have tax and discount on it" — null means tax-exempt, same convention as Product's own taxRateId. */
  taxRateId: number | null;
  taxRateCode: string | null;
  taxRateName: string | null;
  taxRatePercent: number | null;
  taxRateInclusive: boolean;
  /** This plan's own default discount — pre-fills "New subscription" but stays editable per enrollment. */
  discountPercent: number;
  active: boolean;
}

export interface SubscriptionPlanPayload {
  name: string;
  description: string | null;
  price: number;
  billingInterval: BillingInterval;
  taxRateId: number | null;
  discountPercent: number;
  active: boolean;
}

/** Mirrors the backend's SubscriptionStatus enum — see Subscription's javadoc. PAUSED is reversible; CANCELLED is terminal. */
export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';

/** One customer's own enrollment in a SubscriptionPlan (Admin > Sales > Subscriptions) — see the backend Subscription's javadoc. */
export interface Subscription {
  id: number;
  customerId: number;
  customerName: string;
  planId: number | null;
  planName: string | null;
  /** Snapshotted at creation from the plan — see the backend SubscriptionPlan's javadoc. */
  price: number;
  billingInterval: BillingInterval;
  /** Fourier: "subscription plan should also have tax and discount on it" — snapshotted at creation, same as price/billingInterval above. Null means tax-exempt. */
  taxCode: string | null;
  taxRatePercent: number | null;
  taxInclusive: boolean | null;
  discountPercent: number;
  /** Fourier: "may be some subscription will be paid after service other will be prepaid" — whether the very first nextBillingDate was startDate itself (true, prepaid) or startDate advanced by one billingInterval (false, paid after service). Always a plain boolean (true for a row predating this field). See the backend Subscription.billInAdvance's own javadoc. */
  billInAdvance: boolean;
  status: SubscriptionStatus;
  startDate: string;
  nextBillingDate: string;
  lastBilledAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  notes: string | null;
}

/**
 * Body for POST /api/subscriptions — see the backend SubscriptionService.create's javadoc.
 * startDate defaults to today when omitted. Fourier: "What if the One Subscription can be assign
 * to the customer with different price and different Billing interval" — price/billingInterval
 * are required here (the form pre-fills both from the chosen plan but leaves them editable), so
 * this one subscription can commit to different terms than the plan's own listed price/interval.
 */
export interface CreateSubscriptionPayload {
  customerId: number;
  planId: number;
  price: number;
  billingInterval: BillingInterval;
  /** Fourier: "subscription plan should also have tax and discount on it" — pre-filled from the chosen plan, left editable, same treatment as price/billingInterval above. */
  taxRateId?: number | null;
  discountPercent?: number | null;
  startDate?: string | null;
  /** Fourier: "may be some subscription will be paid after service other will be prepaid" — chosen fresh for this one subscription; omitted/null defaults to true (prepaid), today's unchanged behavior. */
  billInAdvance?: boolean | null;
  notes?: string | null;
}

/** Body for POST/PUT /api/invoices — creating/editing a standalone, DRAFT invoice (Fourier: "someone can create invoice (with invoice lines) without creating sales order"). Catalog products only, same convention as SalesOrderPayload. */
export interface InvoicePayload {
  customerId: number;
  dueDate?: string | null;
  notes?: string | null;
  /** Fourier: "Except POS other module can have global discount ... Either on line or global" — the single discount percentage for the whole invoice (GLOBAL mode only). Mutually exclusive with each line's own discountPercent in practice. */
  discountPercent?: number | null;
  /** Fourier: "Global discount should be a fixed amount or percentage with percentage as the default" — 'PERCENT' (default) or 'FIXED_AMOUNT'. */
  discountType?: 'PERCENT' | 'FIXED_AMOUNT' | null;
  /** The flat currency amount, used only when discountType is 'FIXED_AMOUNT'. */
  discountFixedAmount?: number | null;
  lines: { productId: number; quantity: number; unitPrice: number | null; description?: string | null; discountPercent?: number | null }[];
  /** Optional branch; omitted = head office (or unchanged when editing a draft). */
  branchId?: number | null;
}

// ── Recurring invoices ──────────────────────────────────────────────────────
// Fourier: "Add Recurring Invoice" — a standalone module (via AskUserQuestion), separate from
// Subscriptions: a saved template with real invoice line items (any catalog product, quantity,
// price) for one customer that auto-generates a brand-new, already-ISSUED Invoice on a schedule.

/** Mirrors the backend's com.posapp.recurringinvoice.RecurrenceFrequency enum — named distinctly from the existing (two-value) RecurrenceFrequency above, which belongs to Recurring expenses instead. */
export type InvoiceRecurrenceFrequency = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';

/** One line of a RecurringInvoiceTemplate — mirrors the backend's RecurringInvoiceTemplateLineDto. unitPrice is always set (the agreed recurring price), never re-derived from the product's live catalog price. */
export interface RecurringInvoiceTemplateLine {
  id: number;
  productId: number | null;
  productName: string | null;
  quantity: number;
  unitPrice: number;
  description: string | null;
  discountPercent: number | null;
}

/**
 * Admin > Sales > Recurring invoices — a saved template, not an invoice itself. Mirrors the
 * backend's RecurringInvoiceTemplateDto. Its own scheduled sweep (or a manual "Run now") turns a
 * due template into a brand-new, already-ISSUED standalone Invoice (see the backend
 * RecurringInvoiceTemplateService's own javadoc).
 */
export interface RecurringInvoiceTemplate {
  id: number;
  name: string;
  customerId: number;
  customerName: string;
  frequency: InvoiceRecurrenceFrequency;
  nextRunDate: string;
  /** Days after each generated invoice's own date it falls due — e.g. 30 for "net 30". Null means no due date is set on the generated invoice. */
  dueInDays: number | null;
  notes: string | null;
  discountPercent: number;
  discountType: 'PERCENT' | 'FIXED_AMOUNT';
  discountFixedAmount: number | null;
  active: boolean;
  lastRunAt: string | null;
  lines: RecurringInvoiceTemplateLine[];
  /** Sum of every line's own (quantity * unitPrice), before discount/tax — a quick preview figure only; see the backend RecurringInvoiceTemplateDto.estimatedAmount's own javadoc. */
  estimatedAmount: number;
  /** The branch generated invoices are issued from; absent/null on older templates (= head office). */
  branchId?: number | null;
}

/** Body for POST/PUT /api/recurring-invoices — see the backend RecurringInvoiceTemplateService.create/update's javadoc. */
export interface RecurringInvoiceTemplatePayload {
  name: string;
  customerId: number;
  frequency: InvoiceRecurrenceFrequency;
  nextRunDate: string;
  dueInDays?: number | null;
  notes?: string | null;
  discountPercent?: number | null;
  discountType?: 'PERCENT' | 'FIXED_AMOUNT' | null;
  discountFixedAmount?: number | null;
  active: boolean;
  lines: { productId: number; quantity: number; unitPrice: number; description?: string | null; discountPercent?: number | null }[];
  /** Optional branch generated invoices are issued from; omitted = head office (or unchanged when editing). */
  branchId?: number | null;
}

/** Mirrors the backend's PurchaseRequisitionStatus enum — DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED, or DRAFT/PENDING_APPROVAL -> CANCELLED, or APPROVED -> CONVERTED. */
export type PurchaseRequisitionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CONVERTED' | 'CANCELLED';

/** One line of a PurchaseRequisition — mirrors the backend's PurchaseRequisitionLineDto. Deliberately no cost field — see the backend PurchaseRequisition's class javadoc for why pricing only enters at conversion time. */
export interface PurchaseRequisitionLine {
  id: number;
  productId: number | null;
  productName: string;
  quantityRequested: number;
  note: string | null;
}

/**
 * The internal "can we buy this" approval gate placed in front of a PurchaseOrder (Fourier: "Add
 * ... the purchase-requisition approval gate ... without touching warehousing"). Mirrors the
 * backend's PurchaseRequisitionDto.
 */
export interface PurchaseRequisition {
  id: number;
  requisitionNumber: string;
  status: PurchaseRequisitionStatus;
  requestDate: string;
  requestedByFullName: string | null;
  notes: string | null;
  lines: PurchaseRequisitionLine[];
  approvedAt: string | null;
  approvedByFullName: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  convertedPurchaseOrderId: number | null;
  convertedPurchaseOrderNumber: string | null;
}

/** Body for POST/PUT /api/purchase-requisitions — see the backend's PurchaseRequisitionRequest. */
export interface PurchaseRequisitionPayload {
  notes?: string | null;
  lines: { productId: number; quantityRequested: number; note?: string | null }[];
}

/** Body for POST /api/purchase-requisitions/{id}/convert — see the backend's ConvertRequisitionRequest. This is the one moment a supplier/pricing is attached at all — see the backend PurchaseRequisition's class javadoc. */
export interface ConvertRequisitionPayload {
  supplierId: number;
  expectedDate?: string | null;
  lines: { lineId: number; unitCost: number }[];
}

// ---- Restaurant management (Fourier: "add Restaurant management in our ERP ... enabled on
// request as we did for Accounting module") ----

/** Admin > Restaurant > Tables — a physical dining table. See the backend's DiningTable class javadoc. */
export interface DiningTable {
  id: number;
  name: string;
  section: string | null;
  capacity: number;
  status: 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'OUT_OF_SERVICE';
  active: boolean;
  /** Null when no order is currently open at this table. */
  openOrderId: number | null;
}

/** Admin > Restaurant > Menu — a grouping of existing products (Starters, Mains, Drinks...). */
export interface MenuCategory {
  id: number;
  name: string;
  sortOrder: number;
  active: boolean;
}

/**
 * One existing catalog Product placed on the restaurant menu (Fourier chose "reuse existing
 * products" over a separate menu-item concept) — `product` is the same Product shape used
 * everywhere else in the app, never duplicated data of its own.
 */
export interface MenuItem {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  product: Product | null;
  sortOrder: number;
  active: boolean;
  /** Null means "use the product's own unitPrice" — see the backend's MenuItem.priceOverride javadoc. */
  priceOverride: number | null;
  effectivePrice: number;
  /** Whether this item's product has an active bill of materials — checkout consumes its ingredients instead of its own stock. Set up under Admin > Manufacturing > Bill of materials. */
  hasRecipe: boolean;
  /** Free-text kitchen prep area (Fourier: "Kitchen stations") — null means unassigned. */
  station: string | null;
}

/** One ordered item on a RestaurantOrder — see the backend's RestaurantOrderLine class javadoc for the snapshot convention. */
export interface RestaurantOrderLine {
  id: number;
  productId: number | null;
  description: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  taxRatePercent: number | null;
  lineTax: number;
  /** What this line contributes to RestaurantOrder.total, whatever its tax's inclusive/exclusive setting — summing it across a set of lines is exactly their fair share of the bill. See the backend's RestaurantOrderLineDto.lineGrandTotal javadoc. */
  lineGrandTotal: number;
  notes: string | null;
  kitchenStatus: 'PENDING' | 'SENT' | 'PREPARING' | 'READY' | 'SERVED';
  /** Snapshotted kitchen prep area (Fourier: "Kitchen stations") — null if the menu item had none assigned. */
  station: string | null;
}

/** One tender against a RestaurantOrder's bill — several of these is what makes split billing possible. */
export interface RestaurantPayment {
  id: number;
  method: string;
  amount: number;
}

/** A dine-in "tab" opened against one DiningTable — see the backend's RestaurantOrder class javadoc for why this is separate from the walk-up POS terminal's own SaleOrder. */
export interface RestaurantOrder {
  id: number;
  tableId: number | null;
  tableName: string | null;
  status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  /** Fourier: "Delivery & takeaway". */
  orderType: 'DINE_IN' | 'TAKEAWAY' | 'DELIVERY';
  /** What to show in place of a table name — the table's own name for DINE_IN, else "Takeaway"/"Delivery" plus the customer name. */
  displayLabel: string;
  guestCount: number;
  serverId: number | null;
  serverName: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerAddress: string | null;
  driverId: number | null;
  driverName: string | null;
  /** Only non-null for DELIVERY orders. */
  deliveryStatus: 'AWAITING_DRIVER' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | null;
  notes: string | null;
  openedAt: string;
  closedAt: string | null;
  ticketNumber: string | null;
  lines: RestaurantOrderLine[];
  payments: RestaurantPayment[];
  subtotal: number;
  taxTotal: number;
  total: number;
  paidTotal: number;
  balanceDue: number;
}

/** One row on the Kitchen Display — one order line, with just enough order/table context for the kitchen to know where it's going. */
export interface KitchenTicket {
  orderId: number;
  lineId: number;
  tableName: string;
  description: string;
  quantity: number;
  notes: string | null;
  kitchenStatus: 'PENDING' | 'SENT' | 'PREPARING' | 'READY' | 'SERVED';
  orderOpenedAt: string;
  /** Snapshotted kitchen prep area (Fourier: "Kitchen stations") — null if the menu item had none assigned; grouped as "Unassigned" in the station filter. */
  station: string | null;
}

/** A booking for a future dine-in visit (Fourier: "Reservations") — free-text guest details, not tied to an existing Customer. */
export interface Reservation {
  id: number;
  guestName: string;
  guestPhone: string | null;
  partySize: number;
  reservedFor: string;
  tableId: number | null;
  tableName: string | null;
  status: 'BOOKED' | 'SEATED' | 'CANCELLED' | 'NO_SHOW';
  notes: string | null;
  restaurantOrderId: number | null;
  /** The "reservation number" (Fourier: "To use the reserved table, you need to enter reservation number"). */
  confirmationCode: string | null;
}

/* ---------------------------------------------------------------------------------------------
 * Help Desk (Fourier: "Add Help Desk module (with its menu) should manage customer and internal
 * support requests from creation through resolution"). One unified ticket model for both
 * HelpDeskTicketType values, configurable HelpDeskCategory for routing, plain priority levels
 * with no SLA timers — see the backend HelpDeskTicketService's own javadoc for the full scope
 * decided via AskUserQuestion. Staff-created (admin screen, ADMIN-only) and guest-created (the
 * public, no-login /helpdesk page — Fourier: "supporting both authenticated and guest ticket
 * creation") share the same HelpDeskTicket model; only the public-facing DTOs below are
 * deliberately slimmer. */

export type HelpDeskTicketType = 'CUSTOMER' | 'INTERNAL';
export type HelpDeskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type HelpDeskStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/** Admin > Help Desk > Categories — a simple, admin-managed lookup list tickets route through, same shape as Product Category/Brand minus the color. */
export interface HelpDeskCategory {
  id: number;
  name: string;
  description: string | null;
  active: boolean;
}

export interface HelpDeskCategoryPayload {
  name: string;
  description?: string | null;
  active: boolean;
}

/** One Help Desk ticket — admin list row and detail payload both, same single-interface convention as Subscription/Invoice. */
export interface HelpDeskTicket {
  id: number;
  ticketNumber: string;
  type: HelpDeskTicketType;
  subject: string;
  description: string;
  categoryId: number | null;
  categoryName: string | null;
  priority: HelpDeskPriority;
  status: HelpDeskStatus;
  requesterName: string | null;
  requesterEmail: string | null;
  requesterPhone: string | null;
  customerId: number | null;
  customerName: string | null;
  createdByUserId: number | null;
  createdByUserFullName: string | null;
  assignedToUserId: number | null;
  assignedToUserFullName: string | null;
  resolutionNotes: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Body for POST/PUT /api/helpdesk/tickets — the staff "New ticket"/edit form. type is read on create, ignored on update (see backend HelpDeskTicketRequest's own javadoc). */
export interface HelpDeskTicketPayload {
  type: HelpDeskTicketType;
  subject: string;
  description: string;
  categoryId?: number | null;
  priority?: HelpDeskPriority | null;
  customerId?: number | null;
  requesterName?: string | null;
  requesterEmail?: string | null;
  requesterPhone?: string | null;
  assignedToUserId?: number | null;
}

/** One entry on a ticket's timeline. internal=true is a private staff note; internal=false is an update also shown on the public "check my ticket" lookup page. */
export interface HelpDeskComment {
  id: number;
  ticketId: number;
  authorUserId: number | null;
  authorUserFullName: string | null;
  body: string;
  internal: boolean;
  createdAt: string;
}

/** The public "Submit a ticket" form's own confirmation (Fourier: "supporting ... guest ticket creation") — deliberately slim, same shape as PublicReservationConfirmation. */
export interface PublicHelpDeskTicketConfirmation {
  ticketNumber: string;
  subject: string;
  status: HelpDeskStatus;
  createdAt: string;
}

/** The guest form's own category picker — id+name only. */
export interface PublicHelpDeskCategory {
  id: number;
  name: string;
}

/** One entry on the public "check my ticket" timeline — never an author name, just what was said and when. */
export interface PublicHelpDeskUpdate {
  body: string;
  createdAt: string;
}

/** The public lookup page's own result, by ticket number + the email given at creation — no internal fields. */
export interface PublicHelpDeskTicketStatus {
  ticketNumber: string;
  subject: string;
  status: HelpDeskStatus;
  categoryName: string | null;
  createdAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  resolutionNotes: string | null;
  updates: PublicHelpDeskUpdate[];
}

/**
 * Admin > Help Desk > Settings — Fourier: "this should be set to the helpdesk setting and the
 * email can be a list." Separate from the store-wide StoreSettings.notificationsEmail; see the
 * backend HelpDeskSettings entity's own javadoc for why.
 */
export interface HelpDeskSettings {
  id: number;
  notificationEmails: string[];
}

export interface HelpDeskSettingsPayload {
  notificationEmails: string[];
}

/** Fourier: "it is better to have a home page with look nice like a website" — the one sliver of StoreSettings the new public home page is allowed to see; see backend PublicBrandingDto's own javadoc for why it's this narrow. */
export interface PublicBranding {
  businessName: string | null;
  logoDataUrl: string | null;
}

/**
 * Admin > Inventory > Packaging — Fourier: "Product Packaging & Breakdown / Unit Conversion.
 * Whiskey Breakdown & Beer Breakdown (in the bar a bottle of whiskey can be splited into shot.
 * the same a caisse of beer can be sold in bottle)." The saved "recipe": any parentProduct breaks
 * down into unitsPerParent whole units of any childProduct — deliberately generic, not two
 * hardcoded whiskey/beer feature types (those are just the two examples given). Mirrors the
 * backend's PackagingRuleDto. Configuring a rule is never gated by StoreSettings.
 * productBreakdownEnabled — only actually running a breakdown is (see PackagingBreakdown below).
 */
export interface PackagingRule {
  id: number;
  parentProductId: number;
  parentProductName: string;
  parentProductSku: string | null;
  childProductId: number;
  childProductName: string;
  childProductSku: string | null;
  /** How many whole units of childProduct one unit of parentProduct breaks down into — e.g. 25 shots per bottle, 24 bottles per case. */
  unitsPerParent: number;
  notes: string | null;
  /** Toggle, don't delete — a paused rule keeps its own past breakdown history but can't be used to start a new one until resumed. */
  active: boolean;
}

/** Body for POST/PUT /api/packaging/rules — see the backend PackagingService.createRule/updateRule's javadoc. */
export interface PackagingRulePayload {
  parentProductId: number;
  childProductId: number;
  unitsPerParent: number;
  notes: string | null;
  active: boolean;
}

/**
 * One executed breakdown against a PackagingRule — mirrors the backend's PackagingBreakdownDto.
 * parentProductName/childProductName/unitsPerParent are snapshotted at the moment the breakdown
 * ran, so editing the rule afterward never rewrites this row's own history. The stockBefore/After
 * fields on either side are null when that side wasn't stock-tracked at the time. Deliberately
 * carries no accounting-posting fields — see the backend PackagingService.breakdown's own javadoc
 * for why a breakdown posts nothing to accounting (a value-neutral repackaging, not a cost/revenue
 * event). childUnitCost below is a catalog-costing field, not an accounting one — see it below.
 */
export interface PackagingBreakdown {
  id: number;
  ruleId: number | null;
  parentProductId: number | null;
  parentProductName: string;
  childProductId: number | null;
  childProductName: string;
  unitsPerParent: number;
  parentQuantity: number;
  childQuantityProduced: number;
  parentStockBefore: number | null;
  parentStockAfter: number | null;
  childStockBefore: number | null;
  childStockAfter: number | null;
  /**
   * Fourier: "the cost of the child should be equal to the cost of the parent divided by unit" —
   * parentProduct.costPrice / unitsPerParent at the moment this breakdown ran (2dp, half-up), the
   * same figure actually written onto childProduct.costPrice. Snapshotted here too, so this row
   * still shows what was applied even if the rule or either product's cost later changes.
   */
  childUnitCost: number | null;
  note: string | null;
  createdAt: string;
  /** Multi-warehouse phase 4 — null for a breakdown on the primary warehouse's normal stock. */
  locationId: number | null;
  locationName: string | null;
  warehouseName: string | null;
}

/** Body for POST /api/packaging/breakdowns — executes a breakdown immediately, no draft/confirm step. */
export interface PackagingBreakdownRequest {
  ruleId: number;
  parentQuantity: number;
  note: string | null;
  /** Multi-warehouse phase 4 — optional; the location the parent is taken from and the child put into. */
  locationId?: number | null;
}

/**
 * Admin > Sales > Settings — Fourier: "Every time invoice is ISSUED send try to send email to
 * customer and to configured emails. (Add invoices receiver emails in sales settings)." Mirrors
 * the backend's SalesSettingsDto. Everyone on invoiceReceiverEmails gets a copy of every invoice
 * email alongside the customer's own address, the moment an invoice becomes ISSUED — see the
 * backend InvoiceMailService's own javadoc for the best-effort "try to send" semantics.
 */
export interface SalesSettings {
  id: number;
  invoiceReceiverEmails: string[];
}

export interface SalesSettingsPayload {
  invoiceReceiverEmails: string[];
}

/** Multi-warehouse phase 1 — mirrors the backend's ProductStockByLocationDto. Read-only view of where one product's stock sits. */
export interface ProductStockByLocation {
  productId: number;
  /** Active, non-DAMAGED locations only — what could actually be sold. */
  sellableTotal: number;
  /** Everything recorded, including RECEIVING, STORAGE and DAMAGED stock. */
  onHandTotal: number;
  locations: ProductStockLocationRow[];
}

export interface ProductStockLocationRow {
  warehouseId: number;
  warehouseName: string;
  warehousePrimary: boolean;
  locationId: number;
  locationName: string;
  type: LocationType | null;
  active: boolean;
  sellable: boolean;
  quantity: number;
}

/** Multi-warehouse phase 1 — one low-stock product inside a single warehouse (the backend's WarehouseLowStockRow). */
export interface WarehouseLowStockRow {
  productId: number;
  sku: string | null;
  productName: string;
  quantity: number;
}

/** Physical stock count (stocktake) — mirrors the backend's StockCountDto / StockCountLineDto. */
export type StockCountStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'APPROVED' | 'CANCELLED';

export interface StockCountLine {
  id: number;
  productId: number;
  sku: string | null;
  productName: string;
  locationId: number;
  locationName: string;
  /** Quantity frozen when the count was started; later sales/receipts don't change it. */
  systemQuantity: number;
  /** Null until entered; uncounted lines are ignored on approval (never treated as zero). */
  countedQuantity: number | null;
  variance: number | null;
  unitCost: number | null;
  varianceValue: number | null;
  /** The RECOUNT stock adjustment posted for this line on approval. */
  adjustmentId: number | null;
}

export interface StockCount {
  id: number;
  countNumber: string;
  warehouseId: number;
  warehouseName: string;
  categoryId: number | null;
  categoryName: string | null;
  notes: string | null;
  status: StockCountStatus;
  createdAt: string;
  submittedAt: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  branchId: number | null;
  lineCount: number;
  countedCount: number;
  differenceCount: number;
  varianceValue: number;
  /** Empty on the list endpoint. */
  lines: StockCountLine[];
}

/** One product at or below its reorder point — mirrors the backend's ReorderSuggestionDto. */
export interface ReorderSuggestion {
  productId: number;
  sku: string | null;
  productName: string;
  categoryName: string | null;
  /** Sellable stock across all warehouses. */
  onHand: number;
  /** Outstanding quantity on open purchase orders. */
  onOrder: number;
  reorderLevel: number;
  maxStockLevel: number;
  suggestedQuantity: number;
}

/** What a supplier charges for a product — mirrors the backend's SupplierPriceDto. */
export interface SupplierPrice {
  id: number;
  supplierId: number;
  supplierName: string;
  productId: number;
  productName: string;
  sku: string | null;
  unitCost: number;
  validFrom: string | null;
  validTo: string | null;
  note: string | null;
}
