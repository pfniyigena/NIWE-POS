import { Routes } from '@angular/router';
import { authGuard, adminGuard, moduleGuard, publicModuleGuard } from './core/auth.guard';
import { ModuleKey } from './core/api/module-api.service';
import { PurchaseReportListComponent } from './admin/reports/purchase-report-list.component';
import { ModulesComponent } from './admin/modules.component';
import { PublicHomeComponent } from './home/public-home.component';
import { LoginComponent } from './login/login.component';
import { ForgotPasswordComponent } from './login/forgot-password.component';
import { PublicReservationComponent } from './restaurant/public-reservation.component';
import { PublicHelpDeskComponent } from './helpdesk/public-helpdesk.component';
import { ResetPasswordComponent } from './login/reset-password.component';
import { MyProfileComponent } from './account/my-profile.component';
import { AppearanceComponent } from './account/appearance.component';
import { ChartCountrySetupComponent } from './setup/chart-country-setup.component';
import { RegisterListComponent } from './registers/register-list.component';
import { RegisterFormComponent } from './registers/register-form.component';
import { SessionOpenComponent } from './registers/session-open.component';
import { SessionListComponent } from './registers/session-list.component';
import { SessionCloseComponent } from './registers/session-close.component';
import { SessionReportComponent } from './registers/session-report.component';
import { CashierSessionOrdersComponent } from './registers/cashier-session-orders.component';
import { CashierSessionReportComponent } from './registers/cashier-session-report.component';
import { TerminalComponent } from './pos/terminal.component';
import { CustomerDisplayComponent } from './pos/customer-display.component';
import { CheckoutComponent } from './checkout/checkout.component';
import { ReceiptComponent } from './checkout/receipt.component';
import { ReturnListComponent } from './returns/return-list.component';
import { ReturnFormComponent } from './returns/return-form.component';
import { ReturnReceiptComponent } from './returns/return-receipt.component';
import { ReceiptListComponent } from './receipts/receipt-list.component';
import { AdminDashboardComponent } from './admin/dashboard.component';
import { ProductListComponent } from './admin/product-list.component';
import { ProductDetailComponent } from './admin/product-detail.component';
import { ProductFormComponent } from './admin/product-form.component';
import { ProductLabelsComponent } from './admin/product-labels.component';
import { CategoryListComponent } from './admin/category-list.component';
import { CategoryFormComponent } from './admin/category-form.component';
import { BrandListComponent } from './admin/brand-list.component';
import { BrandFormComponent } from './admin/brand-form.component';
import { BranchListComponent } from './admin/branch-list.component';
import { BranchFormComponent } from './admin/branch-form.component';
import { WarehouseListComponent } from './admin/warehouse-list.component';
import { WarehouseFormComponent } from './admin/warehouse-form.component';
import { CustomerListComponent } from './admin/customer-list.component';
import { CustomerFormComponent } from './admin/customer-form.component';
import { CustomerDetailComponent } from './admin/customer-detail.component';
import { CustomerStatementComponent } from './admin/customer-statement.component';
import { RecordPaymentComponent } from './admin/record-payment.component';
import { CustomerPaymentListComponent } from './admin/customer-payment-list.component';
import { CustomerPaymentDetailComponent } from './admin/customer-payment-detail.component';
import { UserListComponent } from './admin/user-list.component';
import { UserFormComponent } from './admin/user-form.component';
import { RoleListComponent } from './admin/role-list.component';
import { RoleFormComponent } from './admin/role-form.component';
import { ShiftListComponent } from './admin/shift-list.component';
import { PaymentMethodListComponent } from './admin/payment-method-list.component';
import { PaymentMethodFormComponent } from './admin/payment-method-form.component';
import { TaxRateListComponent } from './admin/tax-rate-list.component';
import { TaxRateFormComponent } from './admin/tax-rate-form.component';
import { ReportListComponent } from './admin/reports/report-list.component';
import { ReportViewerComponent } from './admin/reports/report-viewer.component';
import { InventoryReportListComponent } from './admin/reports/inventory-report-list.component';
import { BusinessDetailsComponent } from './admin/business-details.component';
import { SystemSettingsComponent } from './admin/system-settings.component';
import { EmailSettingsComponent } from './admin/email-settings.component';
import { AppListComponent } from './admin/apps/app-list.component';
import { AppSettingsComponent } from './admin/apps/app-settings.component';
import { AuditLogListComponent } from './admin/audit-log-list.component';
import { DataManagementComponent } from './admin/data-management.component';
import { SupplierListComponent } from './admin/supplier-list.component';
import { SupplierFormComponent } from './admin/supplier-form.component';
import { SupplierStatementComponent } from './admin/supplier-statement.component';
import { RecordSupplierPaymentComponent } from './admin/record-supplier-payment.component';
import { SupplierPaymentListComponent } from './admin/supplier-payment-list.component';
import { SupplierPaymentDetailComponent } from './admin/supplier-payment-detail.component';
import { PurchaseOrderListComponent } from './admin/purchase-order-list.component';
import { PurchaseOrderFormComponent } from './admin/purchase-order-form.component';
import { PurchaseOrderDetailComponent } from './admin/purchase-order-detail.component';
import { PurchaseOrderPrintComponent } from './admin/purchase-order-print.component';
import { PurchaseReturnListComponent } from './admin/purchase-return-list.component';
import { BillListComponent } from './admin/bill-list.component';
import { BillDetailComponent } from './admin/bill-detail.component';
import { BillRecordPaymentComponent } from './admin/bill-record-payment.component';
import { RefundListComponent } from './admin/refund-list.component';
import { StockAdjustmentListComponent } from './admin/stock-adjustment-list.component';
import { StockAdjustmentFormComponent } from './admin/stock-adjustment-form.component';
import { ReorderListComponent } from './admin/reorder-list.component';
import { SupplierPriceListComponent } from './admin/supplier-price-list.component';
import { ProductVariantsComponent } from './admin/product-variants.component';
import { StockCountListComponent } from './admin/stock-count-list.component';
import { StockCountFormComponent } from './admin/stock-count-form.component';
import { StockCountDetailComponent } from './admin/stock-count-detail.component';
import { StockTransferListComponent } from './admin/stock-transfer-list.component';
import { StockTransferFormComponent } from './admin/stock-transfer-form.component';
import { InventorySettingsComponent } from './admin/inventory-settings.component';
import { ReportScheduleListComponent } from './admin/report-schedule-list.component';
import { PackagingRuleListComponent } from './admin/packaging-rule-list.component';
import { PackagingRuleFormComponent } from './admin/packaging-rule-form.component';
import { PackagingBreakdownComponent } from './admin/packaging-breakdown.component';
import { SalesOrderListComponent } from './admin/sales-order-list.component';
import { SalesOrderFormComponent } from './admin/sales-order-form.component';
import { SalesOrderDetailComponent } from './admin/sales-order-detail.component';
import { SalesOrderReturnListComponent } from './admin/sales-order-return-list.component';
import { QuotationListComponent } from './admin/quotation-list.component';
import { QuotationFormComponent } from './admin/quotation-form.component';
import { QuotationDetailComponent } from './admin/quotation-detail.component';
import { InvoiceListComponent } from './admin/invoice-list.component';
import { SubscriptionPlanListComponent } from './admin/subscription-plan-list.component';
import { SubscriptionPlanFormComponent } from './admin/subscription-plan-form.component';
import { SubscriptionListComponent } from './admin/subscription-list.component';
import { SubscriptionFormComponent } from './admin/subscription-form.component';
import { SubscriptionDetailComponent } from './admin/subscription-detail.component';
import { SubscriptionJobSettingsComponent } from './admin/subscription-job-settings.component';
import { HelpDeskCategoryListComponent } from './admin/helpdesk-category-list.component';
import { HelpDeskCategoryFormComponent } from './admin/helpdesk-category-form.component';
import { HelpDeskTicketListComponent } from './admin/helpdesk-ticket-list.component';
import { HelpDeskTicketFormComponent } from './admin/helpdesk-ticket-form.component';
import { HelpDeskTicketDetailComponent } from './admin/helpdesk-ticket-detail.component';
import { HelpDeskSettingsComponent } from './admin/helpdesk-settings.component';
import { HelpDeskReportListComponent } from './admin/helpdesk-report-list.component';
import { CreditNoteListComponent } from './admin/credit-note-list.component';
import { InvoiceDetailComponent } from './admin/invoice-detail.component';
import { InvoiceFormComponent } from './admin/invoice-form.component';
import { InvoiceRecordPaymentComponent } from './admin/invoice-record-payment.component';
import { RecurringInvoiceListComponent } from './admin/recurring-invoice-list.component';
import { RecurringInvoiceFormComponent } from './admin/recurring-invoice-form.component';
import { RecurringInvoiceDetailComponent } from './admin/recurring-invoice-detail.component';
import { PurchaseRequisitionListComponent } from './admin/purchase-requisition-list.component';
import { PurchaseRequisitionFormComponent } from './admin/purchase-requisition-form.component';
import { PurchaseRequisitionDetailComponent } from './admin/purchase-requisition-detail.component';
import { SalesReportListComponent } from './admin/reports/sales-report-list.component';
import { SalesSettingsComponent } from './admin/sales-settings.component';
import { WorkstationListComponent } from './admin/manufacturing/workstation-list.component';
import { WorkstationFormComponent } from './admin/manufacturing/workstation-form.component';
import { OverheadCostListComponent } from './admin/manufacturing/overhead-cost-list.component';
import { OverheadCostFormComponent } from './admin/manufacturing/overhead-cost-form.component';
import { RoutingListComponent } from './admin/manufacturing/routing-list.component';
import { RoutingFormComponent } from './admin/manufacturing/routing-form.component';
import { BomListComponent } from './admin/manufacturing/bom-list.component';
import { BomFormComponent } from './admin/manufacturing/bom-form.component';
import { ManufacturingOrderListComponent } from './admin/manufacturing/manufacturing-order-list.component';
import { ManufacturingOrderFormComponent } from './admin/manufacturing/manufacturing-order-form.component';
import { ManufacturingOrderDetailComponent } from './admin/manufacturing/manufacturing-order-detail.component';
import { ProductionPlanListComponent } from './admin/manufacturing/production-plan-list.component';
import { ProductionPlanDetailComponent } from './admin/manufacturing/production-plan-detail.component';
import { InstallmentPlanListComponent } from './admin/installments/plan-list.component';
import { InstallmentPlanFormComponent } from './admin/installments/plan-form.component';
import { InstallmentPlanDetailComponent } from './admin/installments/plan-detail.component';
import { InstallmentSettingsComponent } from './admin/installments/installment-settings.component';
import { InstallmentReportListComponent } from './admin/installments/installment-report-list.component';
import { AssetListComponent } from './admin/assets/asset-list.component';
import { AssetFormComponent } from './admin/assets/asset-form.component';
import { AssetFromPurchaseComponent } from './admin/assets/asset-from-purchase.component';
import { AssetDetailComponent } from './admin/assets/asset-detail.component';
import { AssetCategoryListComponent } from './admin/assets/asset-category-list.component';
import { EmployeeListComponent } from './admin/hr/employee-list.component';
import { EmployeeFormComponent } from './admin/hr/employee-form.component';
import { EmployeeDetailComponent } from './admin/hr/employee-detail.component';
import { OrgSetupComponent } from './admin/hr/org-setup.component';
import { OrgChartComponent } from './admin/hr/org-chart.component';
import { AttendanceComponent } from './admin/hr/attendance.component';
import { LeavesComponent } from './admin/hr/leaves.component';
import { LeaveSettingsComponent } from './admin/hr/leave-settings.component';
import { ContractListComponent } from './admin/hr/contract-list.component';
import { ContractFormComponent } from './admin/hr/contract-form.component';
import { PayrollRulesComponent } from './admin/hr/payroll-rules.component';
import { PayrollRunsComponent } from './admin/hr/payroll-runs.component';
import { PayrollRunComponent } from './admin/hr/payroll-run.component';
import { PayslipComponent } from './admin/hr/payslip.component';
import { HrReportListComponent } from './admin/hr/hr-report-list.component';
import { AssetReportListComponent } from './admin/reports/asset-report-list.component';
import { AccountListComponent } from './admin/accounting/account-list.component';
import { AccountFormComponent } from './admin/accounting/account-form.component';
import { AccountLedgerComponent } from './admin/accounting/account-ledger.component';
import { JournalEntryListComponent } from './admin/accounting/journal-entry-list.component';
import { JournalEntryDetailComponent } from './admin/accounting/journal-entry-detail.component';
import { JournalEntryFormComponent } from './admin/accounting/journal-entry-form.component';
import { GeneralLedgerComponent } from './admin/accounting/general-ledger.component';
import { TrialBalanceComponent } from './admin/accounting/trial-balance.component';
import { ProfitAndLossComponent } from './admin/accounting/profit-and-loss.component';
import { BalanceSheetComponent } from './admin/accounting/balance-sheet.component';
import { CostCenterListComponent } from './admin/accounting/cost-center-list.component';
import { CostCenterFormComponent } from './admin/accounting/cost-center-form.component';
import { ExpenseListComponent } from './admin/accounting/expense-list.component';
import { ExpenseFormComponent } from './admin/accounting/expense-form.component';
import { ExpenseDetailComponent } from './admin/accounting/expense-detail.component';
import { RecurringExpenseListComponent } from './admin/accounting/recurring-expense-list.component';
import { RecurringExpenseFormComponent } from './admin/accounting/recurring-expense-form.component';
import { IncomeListComponent } from './admin/accounting/income-list.component';
import { IncomeFormComponent } from './admin/accounting/income-form.component';
import { CashFlowComponent } from './admin/accounting/cash-flow.component';
import { TaxReportComponent } from './admin/accounting/tax-report.component';
import { AssetsReportComponent } from './admin/accounting/assets-report.component';
import { FinancialTransactionsComponent } from './admin/accounting/financial-transactions.component';
import { JournalsReportComponent } from './admin/accounting/journals-report.component';
import { CostCenterActivityComponent } from './admin/accounting/cost-center-activity.component';
import { ExpenseIncomeReportComponent } from './admin/accounting/expense-income-report.component';
import { AccountingReportMenuComponent } from './admin/accounting/accounting-report-menu.component';
import { TableListComponent } from './restaurant/table-list.component';
import { HotelSettingsComponent } from './hotel/hotel-settings.component';
import { RoomTypeListComponent } from './hotel/room-type-list.component';
import { RoomListComponent } from './hotel/room-list.component';
import { GuestListComponent } from './hotel/guest-list.component';
import { BookingListComponent } from './hotel/booking-list.component';
import { BookingFormComponent } from './hotel/booking-form.component';
import { BookingDetailComponent } from './hotel/booking-detail.component';
import { HotelInvoiceComponent } from './hotel/hotel-invoice.component';
import { FrontDeskComponent } from './hotel/front-desk.component';
import { ReservationGridComponent } from './hotel/reservation-grid.component';
import { HousekeepingComponent } from './hotel/housekeeping.component';
import { MaintenanceComponent } from './hotel/maintenance.component';
import { HotelReportComponent } from './hotel/hotel-report.component';
import { MenuAdminComponent } from './restaurant/menu-admin.component';
import { FloorPlanComponent } from './restaurant/floor-plan.component';
import { TableOrderComponent } from './restaurant/table-order.component';
import { KitchenDisplayComponent } from './restaurant/kitchen-display.component';
import { ReservationListComponent } from './restaurant/reservation-list.component';
import { OrderIntakeComponent } from './restaurant/order-intake.component';
import { DeliveriesBoardComponent } from './restaurant/deliveries-board.component';
import { RestaurantReceiptListComponent } from './restaurant/restaurant-receipt-list.component';
import { RestaurantReceiptComponent } from './restaurant/restaurant-receipt.component';
import { RestaurantOrdersComponent } from './restaurant/restaurant-orders.component';
import { RestaurantReportListComponent } from './restaurant/restaurant-report-list.component';
import { RestaurantWastageListComponent } from './restaurant/restaurant-wastage-list.component';
import { RestaurantWastageFormComponent } from './restaurant/restaurant-wastage-form.component';

const baseRoutes: Routes = [
  { path: 'login', component: LoginComponent },
  // Fourier: "Add forget password feature where user will input the email and he/she can reset
  // the password" — both public, no auth needed (same as 'login' above); AuthController's
  // matching backend endpoints are under the already-public /api/auth/** path.
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  // Fourier: "Public be able to make reservation" — the one page in the app meant for a visitor
  // who is not a staff member at all, so it's public/no auth needed just like 'login' above, but
  // unlike 'login' it's not part of the staff auth flow — it posts to PublicReservationController
  // (/api/public/reservations) and never touches AuthService/the JWT.
  { path: 'reserve', component: PublicReservationComponent, canActivate: [publicModuleGuard('RESTAURANT')] },
  // Fourier: "Add Help Desk module ... supporting both authenticated and guest ticket creation" —
  // the guest half, same public/no-auth treatment as 'reserve' above; posts to
  // PublicHelpDeskController (/api/public/helpdesk/**), never touches AuthService/the JWT.
  { path: 'helpdesk', component: PublicHelpDeskComponent, canActivate: [publicModuleGuard('HELPDESK')] },
  // Fourier: "it is better to have a home page with look nice like a website" — the root route is
  // now this public landing page, not a straight redirect to 'registers'. PublicHomeComponent
  // itself sends an already-logged-in visitor on to their usual landing (same split LoginComponent
  // uses), so a staff member hitting '/' still ends up where they always did.
  { path: '', pathMatch: 'full', component: PublicHomeComponent },

  // Self-service "My Profile" (Task: "Add my profile, change my password") — any logged-in user,
  // not admin-only; there's no id in the path, the backend always resolves "you" from the JWT.
  { path: 'profile', component: MyProfileComponent, canActivate: [authGuard] },
  // Settings > Appearance: every logged-in user picks their own colour theme.
  { path: 'settings/appearance', component: AppearanceComponent, canActivate: [authGuard] },

  // Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add
  // this on the screen then at first time a user select the country?" — the first-run chart-of-
  // accounts setup wizard. adminGuard itself redirects here from every other admin route while
  // the chart still needs setting up (see auth.guard.ts) — this route exists so there's somewhere
  // for that redirect to land, and it carries the same two guards as everything else (a cashier
  // has no reason to see this; see ChartSetupController, which is ADMIN-only end to end).
  { path: 'setup/chart-country', component: ChartCountrySetupComponent, canActivate: [authGuard, adminGuard] },

  // ADMIN-only (Task: "Remove Registers & Terminal menu from Cashier side so he can not create a
  // session himself") — this list's "Go to terminal" link had no per-row ownership check, so a
  // cashier browsing it could open the terminal for a shift nobody assigned them, silently
  // self-creating a cashier session on it via the terminal's mine() call. A cashier still reaches
  // their own assigned shift's terminal via the "Terminal" link on their own row on /sessions.
  { path: 'registers', component: RegisterListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'registers/new', component: RegisterFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'registers/:id/edit', component: RegisterFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'registers/:id/open-session', component: SessionOpenComponent, canActivate: [authGuard, adminGuard] },

  { path: 'sessions', component: SessionListComponent, canActivate: [authGuard] },
  { path: 'sessions/:id/close', component: SessionCloseComponent, canActivate: [authGuard, adminGuard] },
  { path: 'sessions/:id/report', component: SessionReportComponent, canActivate: [authGuard] },
  { path: 'cashier-sessions/:id/orders', component: CashierSessionOrdersComponent, canActivate: [authGuard] },
  // Task: "We need to differentiate CashierSession Z Report with RegisterSession Z Report" — this
  // cashier's own report, separate from the whole shift's at 'sessions/:id/report' above.
  { path: 'cashier-sessions/:id/report', component: CashierSessionReportComponent, canActivate: [authGuard] },

  { path: 'pos/:sessionId', component: TerminalComponent, canActivate: [authGuard] },

  // Task: "Add customer-facing double screen" — a second, read-only screen the terminal opens
  // (a new window/second monitor, or the same URL on separate hardware). authGuard only, same as
  // the terminal itself: ownership of the cashier session it polls is enforced server-side, not
  // by an adminGuard here.
  { path: 'customer-display/:cashierSessionId', component: CustomerDisplayComponent, canActivate: [authGuard] },

  { path: 'checkout/:orderId', component: CheckoutComponent, canActivate: [authGuard] },
  { path: 'receipt/:orderId', component: ReceiptComponent, canActivate: [authGuard] },

  { path: 'returns', component: ReturnListComponent, canActivate: [authGuard] },
  { path: 'returns/new', component: ReturnFormComponent, canActivate: [authGuard] },
  // Task: "print out the return" — a printable receipt for one already-processed return.
  { path: 'returns/:id/receipt', component: ReturnReceiptComponent, canActivate: [authGuard] },

  { path: 'receipts', component: ReceiptListComponent, canActivate: [authGuard] },

  // ---- admin-only management screens ----
  { path: 'admin/dashboard', component: AdminDashboardComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/products', component: ProductListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/products/new', component: ProductFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/products/:id/edit', component: ProductFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/products/:id/variants', component: ProductVariantsComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "barcode/label generation for products that don't already carry a manufacturer
  // barcode" — printable, no app-shell chrome, same convention as the purchase order/receipt print
  // routes below. Placed after ":id/edit" only for readability — "labels" can never collide with
  // ":id" since product ids are numeric.
  { path: 'admin/products/labels', component: ProductLabelsComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Add view on product where we can show current quantity on that product, Stock
  // movement, etc" — a read-only companion screen to the edit form above, at the bare ":id"
  // (no "/edit" or other trailing segment). MUST come after "new" and "labels" above: those are
  // also two literal path segments under admin/products, and Angular's router matches route array
  // entries in order — if this ":id" route came first, a visit to "new" or "labels" would match it
  // instead (with id="new"/"labels") rather than falling through to its own dedicated route below.
  { path: 'admin/products/:id', component: ProductDetailComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/categories', component: CategoryListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/categories/new', component: CategoryFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/categories/:id/edit', component: CategoryFormComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add product brands" — same tier/shape as Categories just above.
  { path: 'admin/brands', component: BrandListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/brands/new', component: BrandFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/brands/:id/edit', component: BrandFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/branches', component: BranchListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/branches/new', component: BranchFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/branches/:id/edit', component: BranchFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/warehouses', component: WarehouseListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/warehouses/new', component: WarehouseFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/warehouses/:id/edit', component: WarehouseFormComponent, canActivate: [authGuard, adminGuard] },

  // Task: "Inventory depth ... purchase orders and supplier records, a stock-receiving flow ...
  // and stock adjustments/write-offs with a reason code" — all admin-only, same tier as Products.
  { path: 'admin/suppliers', component: SupplierListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/suppliers/new', component: SupplierFormComponent, canActivate: [authGuard, adminGuard] },
  // Task 5: "Add Supplier Statement" — placed before ":id/edit" below, same convention as
  // admin/customers/statement above it.
  { path: 'admin/suppliers/statement', component: SupplierStatementComponent, canActivate: [authGuard, adminGuard] },
  // Task: "Add Supplier payment" — store-wide list placed before ":id/edit" below, same "statement before :id" convention just above.
  { path: 'admin/supplier-payments', component: SupplierPaymentListComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Add view details on supplier payments list" — a specific payment's own id, placed
  // after the 'supplier-payments' list route above, same "literal path before :id" convention as
  // 'admin/customer-payments/:id'.
  { path: 'admin/supplier-payments/:id', component: SupplierPaymentDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/suppliers/:id/edit', component: SupplierFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/suppliers/:id/record-payment', component: RecordSupplierPaymentComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add ... the purchase-requisition approval gate ... without touching warehousing" —
  // the internal approval step placed in front of Purchase Orders below; converting an APPROVED
  // one creates a real purchase order.
  { path: 'admin/purchase-requisitions', component: PurchaseRequisitionListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-requisitions/new', component: PurchaseRequisitionFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-requisitions/:id/edit', component: PurchaseRequisitionFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-requisitions/:id', component: PurchaseRequisitionDetailComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/purchase-orders', component: PurchaseOrderListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-orders/new', component: PurchaseOrderFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-orders/:id/edit', component: PurchaseOrderFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/purchase-orders/:id', component: PurchaseOrderDetailComponent, canActivate: [authGuard, adminGuard] },
  // Task 3: "Print out purchase order" — printable, no app-shell chrome, same as /returns/:id/receipt.
  { path: 'admin/purchase-orders/:id/print', component: PurchaseOrderPrintComponent, canActivate: [authGuard, adminGuard] },

  // Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
  // Module" — the store-wide ledger; processing itself happens from the order's own detail page above.
  { path: 'admin/purchase-returns', component: PurchaseReturnListComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
  // equivalent with Invoices and Credit note)" — one is generated from a Purchase Order above (see
  // that order's own detail page's "Generate bill" button), same "record-payment before :id" order
  // as Invoices.
  { path: 'admin/bills', component: BillListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/bills/:id/record-payment', component: BillRecordPaymentComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/bills/:id', component: BillDetailComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Refunds equivalent with Credit note" — own top-level path segment ("refunds", not
  // "bills/..."), so it never collides with the routes above, same convention as /admin/credit-notes.
  { path: 'admin/refunds', component: RefundListComponent, canActivate: [authGuard, adminGuard] },

  // Back to ADMIN-only for listing/logging (see StockAdjustmentController's own javadoc) — only
  // confirm/reject below carry the ADMIN/PERM_APPROVE_STOCK_ADJUSTMENT additive gate.
  { path: 'admin/stock-adjustments', component: StockAdjustmentListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/stock-adjustments/new', component: StockAdjustmentFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/reorder', component: ReorderListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/supplier-prices', component: SupplierPriceListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/stock-counts', component: StockCountListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/stock-counts/new', component: StockCountFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/stock-counts/:id', component: StockCountDetailComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "multi-location/multi-warehouse tracking ... Stock Transfers" — same shape as
  // Stock adjustments just above (list/create ADMIN-only, confirm/reject carry the ADMIN/
  // PERM_APPROVE_STOCK_TRANSFER additive gate), no separate edit screen either.
  { path: 'admin/stock-transfers', component: StockTransferListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/stock-transfers/new', component: StockTransferFormComponent, canActivate: [authGuard, adminGuard] },

  // Task 2 (this pass): "Move Inventory Report as submenu to Inventory Menu" — same
  // ReportViewerComponent at 'admin/reports/:key' below still renders each one; this is just a
  // second, Inventory-scoped picker (see InventoryReportListComponent).
  { path: 'admin/inventory/reports', component: InventoryReportListComponent, canActivate: [authGuard, adminGuard] },

  // Task 2: "Inventory Setting (Example: allow negative stock)" — its own screen under Admin >
  // Inventory, not folded into Settings > System settings (see InventorySettingsComponent's javadoc).
  { path: 'admin/inventory/settings', component: InventorySettingsComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Product Packaging & Breakdown / Unit Conversion ... configured as we did for chart
  // of accounts. If not activated ignore packaging" — rule CRUD is always reachable here;
  // StoreSettings.productBreakdownEnabled only gates PackagingBreakdownComponent's "Break down
  // now" button, not these routes (see PackagingService's own javadoc for the toggle reasoning).
  { path: 'admin/packaging/rules', component: PackagingRuleListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/packaging/rules/new', component: PackagingRuleFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/packaging/rules/:id/edit', component: PackagingRuleFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/packaging/breakdown', component: PackagingBreakdownComponent, canActivate: [authGuard, adminGuard] },

  // Task: "Cashier can add or modify customer" / "Give Cashier Customer menu" — list/new/edit are
  // now authGuard only (any logged-in user), not adminGuard. "Statement" stays ADMIN-only right
  // below — it's a financial report, not customer add/modify, and the credit fields on the edit
  // form itself stay ADMIN-only too (see CustomerFormComponent's own isAdmin gating).
  { path: 'admin/customers', component: CustomerListComponent, canActivate: [authGuard] },
  { path: 'admin/customers/new', component: CustomerFormComponent, canActivate: [authGuard] },
  // Placed before the ":id/edit" route below — otherwise the router would try to match "statement" as an :id.
  { path: 'admin/customers/statement', component: CustomerStatementComponent, canActivate: [authGuard, adminGuard] },
  // Task: "Add Sale order and Customer payment... Let have Sale as Menu" — store-wide list, same "before :id" convention as statement above.
  { path: 'admin/customer-payments', component: CustomerPaymentListComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Add view details on Customer payments" — placed right after the literal
  // 'customer-payments' list route above, same "literal path before :id" convention as every
  // other list/detail pair in this file.
  { path: 'admin/customer-payments/:id', component: CustomerPaymentDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/customers/:id/edit', component: CustomerFormComponent, canActivate: [authGuard] },
  // Fourier: "Better add one button (View) on customer then put all others buttons on view form"
  // — placed after the literal 'new'/'statement' routes above (otherwise the router would try to
  // match those as an :id), and after ':id/edit' only for readability — a different segment count
  // means it can never collide with it either way.
  { path: 'admin/customers/:id', component: CustomerDetailComponent, canActivate: [authGuard] },
  // Task family: "Customer payment later" / "Partial settlement" / "Payment allocation to orders".
  { path: 'admin/customers/:id/record-payment', component: RecordPaymentComponent, canActivate: [authGuard, adminGuard] },

  // Task: "Add Sale order and Customer payment(So that we can have POS & Sales) ... Let have Sale
  // as Menu" — a back-office order-taking flow entirely separate from POS checkout. ADMIN-only,
  // same tier as Purchase Orders.
  { path: 'admin/sales-orders', component: SalesOrderListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/sales-orders/new', component: SalesOrderFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/sales-orders/:id/edit', component: SalesOrderFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/sales-orders/:id', component: SalesOrderDetailComponent, canActivate: [authGuard, adminGuard] },

  // Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" — the
  // store-wide ledger; processing a return itself happens from the order's own detail page above.
  { path: 'admin/sales-order-returns', component: SalesOrderReturnListComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add Quotation ... without touching warehousing" — a non-binding price offer that
  // precedes a Sale Order; converting an ACCEPTED one creates a real sale order above.
  { path: 'admin/quotations', component: QuotationListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/quotations/new', component: QuotationFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/quotations/:id/edit', component: QuotationFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/quotations/:id', component: QuotationDetailComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add Invoice ... without touching warehousing" — a formal, numbered document
  // generated from a Sale Order above (see the Sale Order detail page's own "Generate invoice"
  // button). Later, Fourier: "someone can create invoice (with invoice lines) without creating
  // sales order" — a standalone invoice can also be created blank, starting as DRAFT; the
  // "new"/"edit" routes below must come before the "/:id" route or Angular would try to match
  // "new"/"edit" as an id instead.
  { path: 'admin/invoices', component: InvoiceListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/invoices/new', component: InvoiceFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/invoices/:id/edit', component: InvoiceFormComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Let record payment per invoice not per customer (all outstanding invoices)" — a
  // different segment count than ":id" below, so it can never collide with it either way.
  { path: 'admin/invoices/:id/record-payment', component: InvoiceRecordPaymentComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/invoices/:id', component: InvoiceDetailComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add Recurring Invoice" — a saved template with real invoice line items (any
  // catalog product, quantity, price) for one customer, auto-generating a brand-new, already-
  // ISSUED invoice on a schedule (see backend RecurringInvoiceTemplateService). Deliberately a
  // separate module from Subscriptions just below (Fourier, via AskUserQuestion). "new" must
  // come before the "/:id" route for the same reason as Invoices above.
  { path: 'admin/recurring-invoices', component: RecurringInvoiceListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/recurring-invoices/new', component: RecurringInvoiceFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/recurring-invoices/:id/edit', component: RecurringInvoiceFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/recurring-invoices/:id', component: RecurringInvoiceDetailComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Subscriptions Module" — customer subscriptions to a fixed, admin-defined plan;
  // each billing date auto-generates a standalone ISSUED invoice (see backend SubscriptionService).
  // "new" must come before the "/:id" route for the same reason as Invoices above.
  { path: 'admin/subscription-plans', component: SubscriptionPlanListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/subscription-plans/new', component: SubscriptionPlanFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/subscription-plans/:id/edit', component: SubscriptionPlanFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/subscriptions', component: SubscriptionListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/subscriptions/new', component: SubscriptionFormComponent, canActivate: [authGuard, adminGuard] },
  // Daily job settings (billing, reminders, unpaid handling, digest) — before '/:id'.
  { path: 'admin/subscriptions/job-settings', component: SubscriptionJobSettingsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/subscriptions/:id', component: SubscriptionDetailComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add Help Desk module (with its menu) should manage customer and internal support
  // requests from creation through resolution" — ADMIN-only admin side, same tier as Subscriptions
  // just above; the no-login guest side is the public 'helpdesk' route near the top of this file.
  { path: 'admin/helpdesk/categories', component: HelpDeskCategoryListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/categories/new', component: HelpDeskCategoryFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/categories/:id/edit', component: HelpDeskCategoryFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/tickets', component: HelpDeskTicketListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/tickets/new', component: HelpDeskTicketFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/tickets/:id/edit', component: HelpDeskTicketFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/tickets/:id', component: HelpDeskTicketDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/helpdesk/settings', component: HelpDeskSettingsComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Add Helpdesk reports(XLS,PDF)" — the picker; ReportViewerComponent itself is already
  // registered at 'admin/reports/:key' further up (shared by every report in the app).
  { path: 'admin/helpdesk/reports', component: HelpDeskReportListComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Add credit note Menu (Void) Where we can printout them" — a distinct top-level
  // segment ("credit-notes", not "invoices/..."), so it never collides with the routes above.
  { path: 'admin/credit-notes', component: CreditNoteListComponent, canActivate: [authGuard, adminGuard] },

  // Sales > Reports — mirrors admin/inventory/reports's own second-picker pattern.
  { path: 'admin/sales/reports', component: SalesReportListComponent, canActivate: [authGuard, adminGuard] },
  // Purchases > Reports — same picker pattern.
  { path: 'admin/purchases/reports', component: PurchaseReportListComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "Every time invoice is ISSUED send try to send email to customer and to configured
  // emails. (Add invoices receiver emails in sales settings)" — its own small settings screen,
  // same tier/placement convention as admin/helpdesk/settings.
  { path: 'admin/sales/settings', component: SalesSettingsComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/staff', component: UserListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/staff/new', component: UserFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/staff/:id/edit', component: UserFormComponent, canActivate: [authGuard, adminGuard] },
  // Custom roles (Staff > Roles) — admin-only; 'roles' is literal so it never clashes with ':id/edit' above (different segment count).
  { path: 'admin/staff/roles', component: RoleListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/staff/roles/new', component: RoleFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/staff/roles/:id/edit', component: RoleFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/shifts', component: ShiftListComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/payment-methods', component: PaymentMethodListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/payment-methods/new', component: PaymentMethodFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/payment-methods/:id/edit', component: PaymentMethodFormComponent, canActivate: [authGuard, adminGuard] },

  // Settings > Taxes — admin-manageable tax rates, replacing the old fixed app.pos.tax-rate property.
  // Settings > Modules: activate optional modules (Hotel, Restaurant, ...); activation is permanent.
  { path: 'admin/settings/modules', component: ModulesComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/settings/taxes', component: TaxRateListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/settings/taxes/new', component: TaxRateFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/settings/taxes/:id/edit', component: TaxRateFormComponent, canActivate: [authGuard, adminGuard] },

  // Settings > Business Details / System settings (Task family: "Under Settings menu add Business
  // Details menu" / "Add System setting like currency, time zone, date format").
  { path: 'admin/settings/business', component: BusinessDetailsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/settings/system', component: SystemSettingsComponent, canActivate: [authGuard, adminGuard] },
  // Settings > Email settings (Fourier: "Configure smtp for sending email").
  { path: 'admin/settings/email', component: EmailSettingsComponent, canActivate: [authGuard, adminGuard] },
  // Settings > Scheduled reports (emailed Excel/PDF reports on a daily/weekly/monthly schedule).
  { path: 'admin/settings/report-schedules', component: ReportScheduleListComponent, canActivate: [authGuard, adminGuard] },

  // Settings > Apps (Task: "This will work as a plugin (or apps) to be installed and configured")
  // — a fixed, code-defined catalog of installable built-in integrations (starting with EFRIS
  // Rwanda e-Invoicing). ":key" must come after the bare "admin/apps" route.
  { path: 'admin/apps', component: AppListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/apps/:key', component: AppSettingsComponent, canActivate: [authGuard, adminGuard] },

  // POS Reports (Task: "Add POS Report..."). ADMIN-only, same as every other reporting/oversight screen.
  { path: 'admin/reports', component: ReportListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/reports/:key', component: ReportViewerComponent, canActivate: [authGuard, adminGuard] },

  // Task: "Implement an audit log of who did what and when" — ADMIN-only, same tier as Reports/Apps.
  { path: 'admin/audit-log', component: AuditLogListComponent, canActivate: [authGuard, adminGuard] },

  // Fourier: "no backup/restore or bulk import-export anywhere in the backend" — ADMIN-only, same tier as Audit Log/Reports.
  { path: 'admin/data', component: DataManagementComponent, canActivate: [authGuard, adminGuard] },

  // Manufacturing Module (Fourier: "We are now good with POS and Sale Module ... Add Manufacturing
  // Module" — scoped via his own list "1.Bill of Materials 2.Production plans 3.Manufacturing
  // orders 4.Indirects Costs 5.Workstations 5.Settings (Production routings etc)" and "Am new on
  // Manufacturing but I need full features"). ADMIN-only, same tier as every other management module.
  { path: 'admin/manufacturing/boms', component: BomListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/boms/new', component: BomFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/boms/:id/edit', component: BomFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/manufacturing/production-plans', component: ProductionPlanListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/production-plans/:id', component: ProductionPlanDetailComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/manufacturing/orders', component: ManufacturingOrderListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/orders/new', component: ManufacturingOrderFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/orders/:id', component: ManufacturingOrderDetailComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/manufacturing/workstations', component: WorkstationListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/workstations/new', component: WorkstationFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/workstations/:id/edit', component: WorkstationFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/manufacturing/routings', component: RoutingListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/routings/new', component: RoutingFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/routings/:id/edit', component: RoutingFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/manufacturing/overhead-costs', component: OverheadCostListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/overhead-costs/new', component: OverheadCostFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/manufacturing/overhead-costs/:id/edit', component: OverheadCostFormComponent, canActivate: [authGuard, adminGuard] },

  // Accounting Module (Fourier: "We need to add Accounting module. Then all POS, Sale, Inventory
  // and Manufacturing module will be integrated when accounting is enabled") — Chart of Accounts,
  // Cost Centers, Journal Entries (automatic + manual), Expenses/Incomes, General Ledger, and the
  // full Accounting/Journals/Expenses/Incomes report batch (Fourier's later, larger follow-up
  // request — see AccountingReportService's own per-method javadoc for each report). ADMIN-only,
  // same tier as every other management module.
  // Assets Management module (optional; activated in Settings > Modules)
  { path: 'admin/installments', component: InstallmentPlanListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/installments/new', component: InstallmentPlanFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/installments/settings', component: InstallmentSettingsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/installments/reports', component: InstallmentReportListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/installments/:id', component: InstallmentPlanDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets', component: AssetListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/new', component: AssetFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/from-purchase', component: AssetFromPurchaseComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/categories', component: AssetCategoryListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/reports', component: AssetReportListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/:id', component: AssetDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/assets/:id/edit', component: AssetFormComponent, canActivate: [authGuard, adminGuard] },
  // HR modules (optional; activated in Settings > Modules): Employees, Organizational Structure, Attendance & Leaves, Contracts, Payroll
  { path: 'admin/hr/employees', component: EmployeeListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/employees/new', component: EmployeeFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/employees/:id', component: EmployeeDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/employees/:id/edit', component: EmployeeFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/org/setup', component: OrgSetupComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/org/chart', component: OrgChartComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/attendance', component: AttendanceComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/leaves', component: LeavesComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/leave-settings', component: LeaveSettingsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/contracts', component: ContractListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/contracts/new', component: ContractFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/contracts/:id/edit', component: ContractFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/payroll/rules', component: PayrollRulesComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/payroll/runs', component: PayrollRunsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/payroll/runs/:id', component: PayrollRunComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/payroll/runs/:id/payslip/:lineId', component: PayslipComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hr/reports', component: HrReportListComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/accounts', component: AccountListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/accounts/new', component: AccountFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/accounts/:id/edit', component: AccountFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/accounts/:id/ledger', component: AccountLedgerComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/cost-centers', component: CostCenterListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/cost-centers/new', component: CostCenterFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/cost-centers/:id/edit', component: CostCenterFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/expenses', component: ExpenseListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/expenses/new', component: ExpenseFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/expenses/:id', component: ExpenseDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/expenses/:id/edit', component: ExpenseFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/recurring-expenses', component: RecurringExpenseListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/recurring-expenses/new', component: RecurringExpenseFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/recurring-expenses/:id/edit', component: RecurringExpenseFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/incomes', component: IncomeListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/incomes/new', component: IncomeFormComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/accounting/journal-entries', component: JournalEntryListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/journal-entries/new', component: JournalEntryFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/journal-entries/:id', component: JournalEntryDetailComponent, canActivate: [authGuard, adminGuard] },

  // Report Menu — the single picker screen for all 24 accounting reports, one sidenav link
  // opening AccountingReportMenuComponent rather than a sidenav entry per report (Fourier: "Do
  // the same as we did for POS Report"). This was originally two picker screens/links
  // ("Accounting Report" + "Report Menu"); Fourier then asked to fold "Accounting Report" into
  // "Report Menu" too, so its own route/component were retired and this is now the only entry
  // point. Placed before the individual report routes below only for readability — Angular
  // matches full paths, so order doesn't matter here.
  { path: 'admin/accounting/reports/menu', component: AccountingReportMenuComponent, canActivate: [authGuard, adminGuard] },

  // Accounting Reports.
  { path: 'admin/accounting/reports/tax-report', component: TaxReportComponent, canActivate: [authGuard, adminGuard] },
  // "Tax Declaration" opens the exact same screen as "Tax Report" — see TaxReportComponent/TaxSummaryDto's own javadoc.
  { path: 'admin/accounting/reports/tax-declaration', component: TaxReportComponent, canActivate: [authGuard, adminGuard] },
  // "Income Statement" is the standard alternate name for Profit & Loss — same screen, no separate component.
  { path: 'admin/accounting/reports/income-statement', component: ProfitAndLossComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/balance-sheet', component: BalanceSheetComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/profit-and-loss', component: ProfitAndLossComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/financial-transactions', component: FinancialTransactionsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/cash-flow', component: CashFlowComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/assets', component: AssetsReportComponent, canActivate: [authGuard, adminGuard] },

  // Journals Reports. "Ledger" (singular, one account) and Chart of Accounts Directory need no
  // route of their own — they're exactly the existing Chart of Accounts screen (pick an account
  // there for its own Ledger link). "General Ledger" below is the all-accounts report Fourier
  // asked to add ("General ledger ... add them"), reached from the report menu rather than Chart
  // of Accounts. Trial Balance / Trial Nets / Trial Balance & Nets all read the same
  // TrialBalanceComponent with a different `data.mode` — see that component's own javadoc.
  { path: 'admin/accounting/reports/general-ledger', component: GeneralLedgerComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/trial-balance', component: TrialBalanceComponent, data: { mode: 'totals', title: 'Trial balance' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/trial-nets', component: TrialBalanceComponent, data: { mode: 'nets', title: 'Trial nets' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/trial-balance-and-nets', component: TrialBalanceComponent, data: { mode: 'full', title: 'Trial balance & nets' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/journals', component: JournalsReportComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/cost-centers', component: CostCenterActivityComponent, canActivate: [authGuard, adminGuard] },

  // Expenses Segmented + Periodic Reports (8 menu items, one shared component — see
  // ExpenseIncomeReportComponent's own javadoc).
  { path: 'admin/accounting/reports/expenses-by-category', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'category', title: 'Expenses by category' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-by-vendor', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'vendor', title: 'Expenses by vendor' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-by-staff', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'staff', title: 'Expenses by staff' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-by-client', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'client', title: 'Expenses by client' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-daily', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'daily', title: 'Daily expenses' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-weekly', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'weekly', title: 'Weekly expenses' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-monthly', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'monthly', title: 'Monthly expenses' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/expenses-yearly', component: ExpenseIncomeReportComponent, data: { direction: 'expense', groupBy: 'yearly', title: 'Yearly expenses' }, canActivate: [authGuard, adminGuard] },

  // Incomes Segmented + Periodic Reports (8 menu items, same shared component).
  { path: 'admin/accounting/reports/incomes-by-category', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'category', title: 'Incomes by category' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-by-vendor', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'vendor', title: 'Incomes by vendor' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-by-staff', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'staff', title: 'Incomes by staff' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-by-client', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'client', title: 'Incomes by client' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-daily', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'daily', title: 'Daily incomes' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-weekly', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'weekly', title: 'Weekly incomes' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-monthly', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'monthly', title: 'Monthly incomes' }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/accounting/reports/incomes-yearly', component: ExpenseIncomeReportComponent, data: { direction: 'income', groupBy: 'yearly', title: 'Yearly incomes' }, canActivate: [authGuard, adminGuard] },

  // Restaurant Management (Fourier: "I want now to add Restaurant management in our ERP").
  // Table/menu setup are ADMIN-only admin config, same tier as every other module's own settings;
  // the day-to-day floor plan/order-taking/kitchen/reservation screens are authGuard only (any
  // logged-in staff, cashier or admin) — same tier as the walk-up POS terminal itself. Always
  // available — no enable/disable switch (Fourier: "make all modules available without enabling
  // them").
  { path: 'admin/restaurant/tables', component: TableListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/restaurant/menu', component: MenuAdminComponent, canActivate: [authGuard, adminGuard] },

  { path: 'admin/restaurant/tables/floor-plan', component: FloorPlanComponent, canActivate: [authGuard] },
  // Fourier: "Delivery & takeaway" — 'orders/new' must be registered before 'orders/:id' so the
  // literal segment wins (Angular matches route order, not specificity, unlike the backend's own
  // Spring routing).
  // Fourier: "There is no screen for Restaurant orders" — every currently open order (dine-in,
  // takeaway, delivery) in one list, reusing the existing GET /orders/open feed. Registered before
  // 'orders/new'/'orders/:id' only for readability here; it has no further path segment so it
  // can't collide with either.
  { path: 'admin/restaurant/orders', component: RestaurantOrdersComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/orders/new', component: OrderIntakeComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/orders/:id', component: TableOrderComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/deliveries', component: DeliveriesBoardComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/kitchen', component: KitchenDisplayComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/reservations', component: ReservationListComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/receipts', component: RestaurantReceiptListComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/receipts/:orderId', component: RestaurantReceiptComponent, canActivate: [authGuard] },
  // Fourier: "Add all reports related to Restaurants Including all type of orders etc" — ADMIN-only,
  // same tier as admin/reports/admin/inventory/reports/admin/sales/reports (reports surface
  // revenue/cost figures no cashier role needs). ReportViewerComponent at 'admin/reports/:key'
  // already renders each one; this is just a third, Restaurant-scoped picker.
  { path: 'admin/restaurant/reports', component: RestaurantReportListComponent, canActivate: [authGuard, adminGuard] },
  // Fourier: "Restaurants need a dedicated wastage module" — same tier as orders/kitchen/receipts
  // above (any logged-in staff, no adminGuard): logging a dropped plate is front-of-house/kitchen
  // work, not back-office admin, unlike the reports picker just above or the general Inventory >
  // Stock Adjustments screen. 'wastage/new' registered before this comment matters not at all here
  // (no ':id' segment exists to collide with), kept for consistency with the orders block above.
  { path: 'admin/restaurant/wastage', component: RestaurantWastageListComponent, canActivate: [authGuard] },
  { path: 'admin/restaurant/wastage/new', component: RestaurantWastageFormComponent, canActivate: [authGuard] },

  // Hotel management (Fourier: "add hotel management ... integrated with POS, Restaurant, Accounting"). Access comes from the
  // Hotel area in core/access.ts (settings are admin-only); 'new' / 'walk-in' need Manage. Literal segments are registered before ':id'.
  { path: 'admin/hotel/settings', component: HotelSettingsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/front-desk', component: FrontDeskComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/bookings', component: BookingListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/bookings/new', component: BookingFormComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/walk-in', component: BookingFormComponent, data: { walkIn: true }, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/bookings/:id', component: BookingDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/bookings/:id/invoice', component: HotelInvoiceComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/grid', component: ReservationGridComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/housekeeping', component: HousekeepingComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/maintenance', component: MaintenanceComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/guests', component: GuestListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/rooms', component: RoomListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/room-types', component: RoomTypeListComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/hotel/reports', component: HotelReportComponent, canActivate: [authGuard, adminGuard] },

  { path: '**', redirectTo: 'registers' },
];

/**
 * Settings > Modules: every screen of an optional module also needs that module switched on. Added here, by path prefix,
 * rather than on each route one by one. Hotel > Settings stays reachable so the module can still be configured.
 */
const MODULE_PREFIXES: { prefix: string; key: ModuleKey; except?: string[] }[] = [
  // POS: registers & terminal, sessions, checkout, returns, receipts, shifts, POS reports.
  { prefix: 'registers', key: 'POS' },
  { prefix: 'sessions', key: 'POS' },
  { prefix: 'pos/', key: 'POS' },
  { prefix: 'checkout', key: 'POS' },
  { prefix: 'returns', key: 'POS' },
  { prefix: 'receipts', key: 'POS' },
  { prefix: 'admin/shifts', key: 'POS' },
  // admin/reports/:key is the viewer shared by every module's reports, so it is not blocked here (it checks itself).
  { prefix: 'admin/reports', key: 'POS', except: ['admin/reports/:key'] },
  { prefix: 'admin/restaurant', key: 'RESTAURANT' },
  { prefix: 'admin/manufacturing', key: 'MANUFACTURING' },
  { prefix: 'admin/subscription', key: 'SUBSCRIPTIONS' },
  { prefix: 'admin/helpdesk', key: 'HELPDESK' },
  { prefix: 'admin/accounting/expenses', key: 'EXPENSES' },
  { prefix: 'admin/accounting/recurring-expenses', key: 'EXPENSES' },
  { prefix: 'admin/accounting/reports/expenses-', key: 'EXPENSES' },
  { prefix: 'admin/hotel', key: 'HOTEL', except: ['admin/hotel/settings'] },
  { prefix: 'admin/assets', key: 'ASSETS' },
  { prefix: 'admin/installments', key: 'INSTALLMENTS' },
  { prefix: 'admin/hr/employees', key: 'EMPLOYEES' },
  { prefix: 'admin/hr/reports', key: 'EMPLOYEES' },
  { prefix: 'admin/hr/org', key: 'ORG_STRUCTURE' },
  { prefix: 'admin/hr/attendance', key: 'ATTENDANCE' },
  { prefix: 'admin/hr/leave', key: 'ATTENDANCE' },
  { prefix: 'admin/hr/contracts', key: 'CONTRACTS' },
  { prefix: 'admin/hr/payroll', key: 'PAYROLL' },
];

export const routes: Routes = baseRoutes.map((route) => {
  const path = route.path ?? '';
  const rule = MODULE_PREFIXES.find((r) => path.startsWith(r.prefix) && !(r.except ?? []).includes(path));
  return rule ? { ...route, canActivate: [...(route.canActivate ?? []), moduleGuard(rule.key)] } : route;
});
