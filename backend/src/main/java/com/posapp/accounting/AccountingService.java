package com.posapp.accounting;

import com.posapp.audit.AuditLogService;
import com.posapp.manufacturing.ManufacturingOrder;
import com.posapp.manufacturing.ManufacturingOrderRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounting Module (Fourier: "We need to add Accounting module. Then all POS, Sale, Inventory
 * and Manufacturing module will be integrated") — the one class every other module calls into.
 * Every {@code postXxx} method here always posts (the module is always on — there is no
 * enable/disable switch; see Fourier's own "make all modules available without enabling them").
 * Callers (see the list below) call their {@code postXxx} method
 * right after the side effect they're recording has already happened and been saved — accounting
 * never blocks or rolls back a real transaction (a checkout, a stock receipt, a production run):
 * if a posting were ever to throw, that would undo something that already genuinely happened, so
 * every posting instead defends itself with a "plug to Suspense and log a warning" fallback (see
 * {@link #post}) rather than ever raising an exception a caller would have to handle.
 *
 * <p><b>Callers, one per module:</b> {@code OrderService.completeCheckout} → {@link #postPosSale};
 * {@code ReturnsService.processReturn} → {@link #postPosReturn}; {@code
 * SalesOrderService.fulfillLines} → {@link #postSalesOrderFulfillment}; {@code
 * SalesOrderReturnService.processReturn} → {@link #postSalesOrderReturn}; {@code
 * PurchaseOrderService.receiveLines} → {@link #postPurchaseReceipt}; {@code
 * PurchaseReturnService.processReturn} → {@link #postPurchaseReturn}; {@code
 * CustomerCreditService.recordPayment} → {@link #postCustomerPayment}; {@code
 * SupplierPaymentService.recordPayment} → {@link #postSupplierPayment}; {@code
 * StockAdjustmentService.create} → {@link #postStockAdjustment}; {@code
 * ManufacturingOrderService.produce} → {@link #postManufacturingRun}; {@code
 * ExpenseService.create} → {@link #postExpense}; {@code IncomeService.create} → {@link
 * #postIncome}; {@code RestaurantOrderService.checkout} → {@link #postRestaurantSale}; {@code
 * InvoiceService.issue} → {@link #postInvoiceIssued}/{@link #postInvoiceVoid} (standalone
 * invoices only — see those methods' own javadoc); {@code InvoiceService.cancelPaidInvoice} →
 * {@link #postInvoiceCancelPaid} (the separate path for a standalone invoice that already has a
 * payment recorded against it — see {@code PaymentDisposition}'s own javadoc).
 *
 * <p><b>Deliberate simplifications</b> (all documented here rather than scattered across ten call
 * sites): every non-store-credit tender (cash, card, mobile money, whatever an admin has set up as
 * a {@code PaymentMethodEntity}) posts to the one {@code CASH} account — this app has no per-method
 * bank/clearing sub-accounts, so a card payment is treated the same as cash for GL purposes rather
 * than modeling card-network settlement delay. Inventory is one pooled asset account, matching
 * {@code Product}'s own single {@code stockQuantity}/{@code costPrice} pair — there's no separate
 * raw-material/WIP/finished-goods split, so a Manufacturing Order's material consumption and
 * finished-goods output are a wash within that one account (see {@link #postManufacturingRun}) and
 * only the overhead actually absorbed shows up as a net movement. Cost of Goods Sold and the
 * matching Inventory relief are only ever posted for a line whose product is stock-tracked (see
 * {@code Product.trackStock}) — a non-tracked (service/made-to-order) line contributes revenue only,
 * even if it happens to carry a nonzero {@code costPrice} elsewhere (e.g. on the unrelated Profit
 * report), since nothing was ever actually held as inventory for it. Every automatic posting is
 * built from figures the calling service already computed for its own domain purpose (a receipt's
 * {@code unitCost}, a return's {@code refundTotal}, a run's {@code materialCost}/{@code
 * overheadCost}) rather than re-deriving them — the accounting entries are a mirror of what already
 * happened, never a second source of truth for it. {@link #postExpense}/{@link #postIncome} are
 * the one pair where the caller (an admin, via {@code ExpenseService}/{@code IncomeService})
 * chooses the offset side itself — a {@code paid}/{@code received} flag on the record picks
 * {@code CASH} vs. {@code ACCOUNTS_PAYABLE}/{@code ACCOUNTS_RECEIVABLE} — rather than that being
 * derived from a real payment the way every other posting's cash side is.
 */
@Service
public class AccountingService {

    private static final Logger log = LoggerFactory.getLogger(AccountingService.class);

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final CostCenterRepository costCenterRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;

    public AccountingService(AccountRepository accountRepository, JournalEntryRepository journalEntryRepository,
                              CostCenterRepository costCenterRepository, AccountService accountService,
                              AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.costCenterRepository = costCenterRepository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    // ---- POS (com.posapp.order / com.posapp.returns) ----

    /** Called from OrderService.completeCheckout, for both the online and offline-sync paths (see that method's own javadoc — both funnel through it, so this covers every completed sale exactly once). */
    @Transactional
    public void postPosSale(com.posapp.order.SaleOrder order) {
        // SaleOrder.creditAmount is already the authoritative snapshot of how much of this sale was
        // tendered on store credit (see its own javadoc) — so "cash" here is simply whatever of the
        // total tender wasn't that, robust even to the rare case (an offline-synced sale replayed
        // through the shared completeCheckout path) where the two figures might not line up exactly
        // to the penny; see post()'s Suspense-account fallback for what happens if they don't.
        BigDecimal creditAmount = order.getCreditAmount() != null ? order.getCreditAmount() : BigDecimal.ZERO;
        BigDecimal tendered = order.getPayments().stream().map(com.posapp.order.Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashAmount = tendered.subtract(creditAmount);
        BigDecimal subtotal = com.posapp.order.OrderCalculator.subtotal(order);
        BigDecimal taxTotal = com.posapp.order.OrderCalculator.taxTotal(order);
        BigDecimal cogs = trackedLineCost(order);

        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.CASH, cashAmount, null, true);
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, creditAmount, null, true);
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotal, null, false);
        addIfPositive(lines, AccountService.TAX_PAYABLE, taxTotal, null, false);
        addIfPositive(lines, AccountService.COST_OF_GOODS_SOLD, cogs, "Cost of goods sold", true);
        addIfPositive(lines, AccountService.INVENTORY, cogs, "Inventory relief", false);

        post("POS_SALE", order.getId(), "POS sale " + safe(order.getInvoiceNumber()), null, lines, order.getBranchId());
    }

    /** Called from ReturnsService.processReturn. cogsReversal/refundMethod are computed by the caller, which already knows exactly which lines were restocked (trackStock) and at what quantity — see AccountingService's class javadoc on why postings never re-derive figures the caller already has. */
    @Transactional
    public void postPosReturn(com.posapp.returns.SalesReturn salesReturn, String refundMethod, boolean refundMethodIsCredit,
                               BigDecimal subtotalRefund, BigDecimal taxRefund, BigDecimal cogsReversal) {
        BigDecimal totalRefund = subtotalRefund.add(taxRefund);
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotalRefund, "Revenue reversal", true);
        addIfPositive(lines, AccountService.TAX_PAYABLE, taxRefund, "Tax reversal", true);
        if (refundMethodIsCredit) {
            addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, totalRefund, null, false);
        } else {
            addIfPositive(lines, AccountService.CASH, totalRefund, null, false);
        }
        addIfPositive(lines, AccountService.INVENTORY, cogsReversal, "Inventory restocked", true);
        addIfPositive(lines, AccountService.COST_OF_GOODS_SOLD, cogsReversal, "Cost of goods sold reversal", false);

        post("POS_RETURN", salesReturn.getId(), "POS return " + safe(salesReturn.getReturnNumber()) + " (" + refundMethod + ")", null, lines, salesReturn.getBranchId());
    }

    // ---- Sale Orders (com.posapp.salesorder) ----

    /**
     * Called from SalesOrderService.fulfillLines once per fulfill call, for exactly the net
     * (tax-excluded) revenue and tax accrued this call — a partial fulfillment posts a partial
     * entry, mirroring how the order's own quantityFulfilled advances incrementally.
     *
     * <p>Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
     * {@code netRevenueAmount}/{@code taxAmount} replace the old single {@code revenueAmount}
     * (which posted the whole amount to Sales Revenue with no tax split at all); same
     * revenue/tax-collected split {@link #postPosSale} already does for POS.
     */
    @Transactional
    public void postSalesOrderFulfillment(com.posapp.salesorder.SalesOrder salesOrder, BigDecimal netRevenueAmount, BigDecimal taxAmount, BigDecimal cogsAmount) {
        BigDecimal total = netRevenueAmount.add(taxAmount);
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, total, null, true);
        addIfPositive(lines, AccountService.SALES_REVENUE, netRevenueAmount, null, false);
        addIfPositive(lines, AccountService.TAX_PAYABLE, taxAmount, null, false);
        addIfPositive(lines, AccountService.COST_OF_GOODS_SOLD, cogsAmount, "Cost of goods sold", true);
        addIfPositive(lines, AccountService.INVENTORY, cogsAmount, "Inventory relief", false);

        post("SALES_ORDER_FULFILLMENT", salesOrder.getId(), "Fulfilled sales order " + salesOrder.getOrderNumber(), null, lines, salesOrder.getBranchId());
    }

    /** Called from SalesOrderReturnService.processReturn. cogsReversal is computed by the caller for exactly the trackStock lines it actually restocked. */
    @Transactional
    public void postSalesOrderReturn(com.posapp.salesorder.SalesOrderReturn salesOrderReturn, BigDecimal refundTotal, BigDecimal cogsReversal) {
        if (refundTotal.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.SALES_REVENUE, refundTotal, "Revenue reversal", true);
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, refundTotal, null, false);
        addIfPositive(lines, AccountService.INVENTORY, cogsReversal, "Inventory restocked", true);
        addIfPositive(lines, AccountService.COST_OF_GOODS_SOLD, cogsReversal, "Cost of goods sold reversal", false);

        post("SALES_ORDER_RETURN", salesOrderReturn.getId(), "Sales order return " + safe(salesOrderReturn.getReturnNumber()), null, lines,
                salesOrderReturn.getSalesOrder() != null ? salesOrderReturn.getSalesOrder().getBranchId() : null);
    }

    // ---- Invoices (com.posapp.invoice) ----

    /**
     * Called from InvoiceService.issue, but only for a *standalone* invoice (no Sales Order
     * behind it) — Fourier: "someone can create invoice (with invoice lines) without creating
     * sales order." A Sales-Order-derived invoice's revenue/receivable was already posted when
     * that order was fulfilled (see {@link #postSalesOrderFulfillment}); posting it again here
     * would double it. A standalone invoice never touches inventory (see {@code Invoice}'s own
     * class javadoc), so this is revenue/receivable only — no COGS/Inventory lines, unlike that
     * method.
     */
    @Transactional
    public void postInvoiceIssued(com.posapp.invoice.Invoice invoice) {
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) return;
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — the
        // credit side now splits into revenue (subtotal) and tax collected, same as postPosSale;
        // the debit (what the customer owes) is still the invoice's full tax-inclusive total.
        BigDecimal subtotal = com.posapp.invoice.InvoiceService.subtotalAmount(invoice);
        BigDecimal tax = com.posapp.invoice.InvoiceService.taxAmount(invoice);
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, invoice.getTotalAmount(), null, true);
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotal, null, false);
        addIfPositive(lines, AccountService.TAX_PAYABLE, tax, null, false);

        post("INVOICE_ISSUED", invoice.getId(), "Issued invoice " + invoice.getInvoiceNumber() + " for " + safe(invoice.getCustomerName()), null, lines, invoice.getBranchId());
    }

    /**
     * Reverses {@link #postInvoiceIssued}'s entry — same "never erase a real posted event,
     * reverse it instead" stance as {@link #postExpenseVoid}. Only ever called for a standalone
     * invoice (see {@code InvoiceService.voidInvoice}, which refuses to void one that already has
     * a payment recorded against it, so this never has to account for that) — a Sales-Order-
     * derived invoice never posted anything here to reverse.
     */
    @Transactional
    public void postInvoiceVoid(com.posapp.invoice.Invoice invoice) {
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) return;
        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
        // reverses postInvoiceIssued's own revenue/tax split instead of debiting the whole total
        // to Sales Revenue alone.
        BigDecimal subtotal = com.posapp.invoice.InvoiceService.subtotalAmount(invoice);
        BigDecimal tax = com.posapp.invoice.InvoiceService.taxAmount(invoice);
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotal, "Invoice void", true);
        addIfPositive(lines, AccountService.TAX_PAYABLE, tax, "Invoice void", true);
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, invoice.getTotalAmount(), null, false);

        post("INVOICE_VOID", invoice.getId(), "Voided invoice " + invoice.getInvoiceNumber(), null, lines, invoice.getBranchId());
    }

    /**
     * Called from InvoiceService.cancelPaidInvoice — the paid-invoice counterpart of {@link
     * #postInvoiceVoid}: Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the
     * payment → Refund OR → Customer Credit." Revenue is reversed in full either way (the sale
     * never stood), but unlike a plain void (where nothing has been paid yet, so the whole
     * reversal lands on Accounts Receivable), only whatever's still outstanding can land there —
     * the part already collected has to leave some other way: through Cash, if the admin chose to
     * refund it (same direction as {@code postPosReturn}'s own cash-refund branch), or through the
     * new {@code CUSTOMER_CREDIT_PAYABLE} liability if they chose to credit the customer instead
     * (the store keeps the cash, but now owes it back). Either way the entry still balances: the
     * Sales Revenue debit is exactly {@code stillOutstanding + amountPaid}, i.e. the invoice's full
     * total.
     */
    @Transactional
    public void postInvoiceCancelPaid(com.posapp.invoice.Invoice invoice, BigDecimal amountPaid, com.posapp.invoice.PaymentDisposition disposition) {
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal stillOutstanding = total.subtract(amountPaid);
        if (stillOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            stillOutstanding = BigDecimal.ZERO;
        }

        // Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
        // same revenue/tax split as postInvoiceIssued/postInvoiceVoid for the reversed side;
        // stillOutstanding/amountPaid below are unaffected (they're the raw tax-inclusive total
        // either way, not revenue).
        BigDecimal subtotal = com.posapp.invoice.InvoiceService.subtotalAmount(invoice);
        BigDecimal tax = com.posapp.invoice.InvoiceService.taxAmount(invoice);
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotal, "Invoice cancelled", true);
        addIfPositive(lines, AccountService.TAX_PAYABLE, tax, "Invoice cancelled", true);
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, stillOutstanding, null, false);
        if (disposition == com.posapp.invoice.PaymentDisposition.REFUND) {
            addIfPositive(lines, AccountService.CASH, amountPaid, "Refunded to customer", false);
        } else {
            addIfPositive(lines, AccountService.CUSTOMER_CREDIT_PAYABLE, amountPaid, "Converted to customer credit", false);
        }

        post("INVOICE_CANCELLED_PAID", invoice.getId(), "Cancelled paid invoice " + invoice.getInvoiceNumber(), null, lines, invoice.getBranchId());
    }

    // ---- Purchasing (com.posapp.inventory) ----

    /**
     * Called from PurchaseOrderService.receiveLines once per receiving call, for exactly the net
     * (tax-excluded) value and tax value received this call.
     *
     * <p>Fourier: "for purchases, sales, invoices, bills the tax are not being captured" —
     * {@code netValue} now lands in {@link AccountService#INVENTORY} on its own (previously the
     * full tax-inclusive amount was booked as inventory cost, overstating stock value by whatever
     * tax was paid on it) and {@code taxValue} lands in {@link AccountService#INPUT_VAT_RECOVERABLE}
     * instead — this app's answer to "all" (capture the tax and track it as reclaimable, not just
     * fold it into inventory): see that account's own javadoc for the chart-of-accounts side.
     * {@code Accounts Payable} is still credited the full amount owed either way.
     */
    @Transactional
    public void postPurchaseReceipt(com.posapp.inventory.PurchaseOrder purchaseOrder, BigDecimal netValue, BigDecimal taxValue) {
        BigDecimal total = netValue.add(taxValue);
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.INVENTORY, netValue, null, true);
        addIfPositive(lines, AccountService.INPUT_VAT_RECOVERABLE, taxValue, null, true);
        addIfPositive(lines, AccountService.ACCOUNTS_PAYABLE, total, null, false);

        post("PURCHASE_RECEIPT", purchaseOrder.getId(), "Received against purchase order " + purchaseOrder.getOrderNumber(), null, lines, purchaseOrder.getBranchId());
    }

    /**
     * Landed cost (freight, duty, clearing) entered while receiving a purchase order: capitalised into
     * Inventory. {@code paid} picks CASH, otherwise ACCOUNTS_PAYABLE — the same convention as an expense.
     * Record the freight this way instead of as a separate expense, or it would be counted twice.
     */
    @Transactional
    public void postLandedCost(com.posapp.inventory.PurchaseOrder purchaseOrder, BigDecimal amount, boolean paid, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.INVENTORY, amount, null, true);
        addIfPositive(lines, paid ? AccountService.CASH : AccountService.ACCOUNTS_PAYABLE, amount, null, false);
        post("PURCHASE_LANDED_COST", purchaseOrder.getId(),
                "Landed cost on purchase order " + purchaseOrder.getOrderNumber() + (note != null && !note.isBlank() ? " — " + note : ""),
                null, lines, purchaseOrder.getBranchId());
    }

    /** An inter-branch delivery that arrived short: the missing units are a stock loss at cost, booked to the sending branch. */
    @Transactional
    public void postTransferShortage(com.posapp.warehouse.StockTransfer transfer, int shortageQuantity) {
        com.posapp.catalog.Product product = transfer.getProduct();
        if (product == null || product.getCostPrice() == null || shortageQuantity <= 0) return;
        BigDecimal amount = product.getCostPrice().multiply(BigDecimal.valueOf(shortageQuantity)).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.INVENTORY_SHRINKAGE_EXPENSE, amount, null, true);
        addIfPositive(lines, AccountService.INVENTORY, amount, null, false);
        post("STOCK_TRANSFER_SHORTAGE", transfer.getId(),
                "Short delivery — " + shortageQuantity + " x " + product.getName() + " lost in transit to " + transfer.getToBranchName(),
                null, lines, transfer.getFromBranchId());
    }

    /** Called from PurchaseReturnService.processReturn. */
    @Transactional
    public void postPurchaseReturn(com.posapp.inventory.PurchaseReturn purchaseReturn, BigDecimal refundTotal) {
        if (refundTotal.compareTo(BigDecimal.ZERO) <= 0) return;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.ACCOUNTS_PAYABLE, refundTotal, null, true);
        addIfPositive(lines, AccountService.INVENTORY, refundTotal, null, false);

        post("PURCHASE_RETURN", purchaseReturn.getId(), "Purchase return " + safe(purchaseReturn.getReturnNumber()), null, lines,
                purchaseReturn.getPurchaseOrder() != null ? purchaseReturn.getPurchaseOrder().getBranchId() : null);
    }

    /** Called from SupplierPaymentService.recordPayment. */
    @Transactional
    public void postSupplierPayment(com.posapp.inventory.SupplierPayment payment) {
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.ACCOUNTS_PAYABLE, payment.getAmount(), null, true);
        addIfPositive(lines, AccountService.CASH, payment.getAmount(), null, false);

        post("SUPPLIER_PAYMENT", payment.getId(), "Paid " + payment.getSupplier().getName(), null, lines, payment.getBranchId());
    }

    /** Called from StockAdjustmentService.create — a positive quantityDelta (recount found extra) is a "gain" (debit Inventory, credit the same expense account as a contra); a negative one (damage/theft/expiry/other write-off) is a real loss. */
    @Transactional
    public void postStockAdjustment(com.posapp.inventory.StockAdjustment adjustment) {
        com.posapp.catalog.Product product = adjustment.getProduct();
        if (product == null || product.getCostPrice() == null) return;
        BigDecimal amount = product.getCostPrice().multiply(BigDecimal.valueOf(Math.abs(adjustment.getQuantityDelta())))
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        List<LineSpec> lines = new ArrayList<>();
        if (adjustment.getQuantityDelta() < 0) {
            addIfPositive(lines, AccountService.INVENTORY_SHRINKAGE_EXPENSE, amount, null, true);
            addIfPositive(lines, AccountService.INVENTORY, amount, null, false);
        } else {
            addIfPositive(lines, AccountService.INVENTORY, amount, null, true);
            addIfPositive(lines, AccountService.INVENTORY_SHRINKAGE_EXPENSE, amount, "Recount gain", false);
        }
        post("STOCK_ADJUSTMENT", adjustment.getId(), "Stock adjustment — " + product.getName() + " (" + adjustment.getReason() + ")", null, lines);
    }

    /**
     * Called from RestaurantWastageService.create (Fourier: "Restaurants need a dedicated
     * wastage module"). Always a real loss — unlike {@link #postStockAdjustment}'s two-way
     * branch, wastage is never a "gain" — so this is just that method's negative-delta half:
     * debit Inventory Shrinkage Expense, credit Inventory, for {@code wastage.getCostImpact()}
     * (already snapshotted by the caller; zero and skipped here the same way a null/zero cost
     * price skips {@code postStockAdjustment}).
     */
    @Transactional
    public void postRestaurantWastage(com.posapp.restaurant.RestaurantWastage wastage) {
        BigDecimal amount = wastage.getCostImpact();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.INVENTORY_SHRINKAGE_EXPENSE, amount, null, true);
        addIfPositive(lines, AccountService.INVENTORY, amount, null, false);
        post("RESTAURANT_WASTAGE", wastage.getId(), "Restaurant wastage — " + wastage.getProductName() + " (" + wastage.getReason() + ")", null, lines, wastage.getBranchId());
    }

    // ---- Manufacturing (com.posapp.manufacturing) ----

    /**
     * Called from ManufacturingOrderService.produce, once per run. Only the overhead actually
     * absorbed into this run creates a net journal entry — see the class javadoc's "pooled
     * Inventory account" explanation for why material consumption and finished-goods output are a
     * wash within that one account and don't need their own lines. A run with zero overhead posts
     * nothing at all (there is genuinely nothing for the GL to record).
     */
    @Transactional
    public void postManufacturingRun(ManufacturingOrder manufacturingOrder, ManufacturingOrderRun run) {
        BigDecimal overheadCost = run.getOverheadCost() != null ? run.getOverheadCost() : BigDecimal.ZERO;
        if (overheadCost.compareTo(BigDecimal.ZERO) <= 0) return;

        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.INVENTORY, overheadCost, "Overhead absorbed into finished goods", true);
        addIfPositive(lines, AccountService.ACCRUED_MANUFACTURING_OVERHEAD, overheadCost, null, false);

        post("MANUFACTURING_RUN", run.getId(), "Production run against " + manufacturingOrder.getOrderNumber(), null, lines);
    }

    // ---- Customer / Supplier payments (com.posapp.credit / com.posapp.inventory) ----

    /** Called from CustomerCreditService.recordPayment — every settlement method (cash, card, whatever) lands in the one CASH account, same simplification as postPosSale (see class javadoc). */
    @Transactional
    public void postCustomerPayment(com.posapp.credit.CustomerPayment payment) {
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.CASH, payment.getAmount(), null, true);
        addIfPositive(lines, AccountService.ACCOUNTS_RECEIVABLE, payment.getAmount(), null, false);

        post("CUSTOMER_PAYMENT", payment.getId(), "Payment received from " + payment.getCustomer().getName(), null, lines, payment.getBranchId());
    }

    // ---- Restaurant (com.posapp.restaurant) ----

    /**
     * Called from {@code RestaurantOrderService.checkout}, once per checked-out table order —
     * the restaurant module's own mirror of {@link #postPosSale}, built from primitive figures
     * the caller already computed (see this class's own javadoc on why postings never re-derive
     * figures a caller already has) rather than taking a {@code RestaurantOrder} entity directly,
     * since that type lives in a separate module this one has no reason to depend on. Restaurant
     * sales have no store-credit tender in this first version (Fourier's own request covered
     * split/merge billing, not tabs on account), so the whole tendered amount is treated as cash.
     */
    @Transactional
    public void postRestaurantSale(Long orderId, String ticketNumber, BigDecimal cashAmount,
                                    BigDecimal subtotal, BigDecimal taxTotal, BigDecimal cogs) {
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.CASH, cashAmount, null, true);
        addIfPositive(lines, AccountService.SALES_REVENUE, subtotal, null, false);
        addIfPositive(lines, AccountService.TAX_PAYABLE, taxTotal, null, false);
        addIfPositive(lines, AccountService.COST_OF_GOODS_SOLD, cogs, "Cost of goods sold", true);
        addIfPositive(lines, AccountService.INVENTORY, cogs, "Inventory relief", false);

        post("RESTAURANT_SALE", orderId, "Restaurant sale " + safe(ticketNumber), null, lines);
    }

    // ---- Expenses / Incomes (com.posapp.accounting itself) ----

    /**
     * Called from ExpenseService.approve (Fourier: "Approval workflow before posting" — moved
     * here from the old call-at-create-time behavior; see ExpenseStatus's own javadoc). The cost
     * center (if any) is tagged only on the expense side of the entry (the category line), never
     * on the Cash/Accounts Payable offset — see JournalEntryLine.costCenter's own javadoc.
     */
    @Transactional
    public void postExpense(Expense expense) {
        Long costCenterId = expense.getCostCenter() != null ? expense.getCostCenter().getId() : null;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, expense.getAccount().getCode(), expense.getAmount(), expense.getMemo(), true, costCenterId);
        addIfPositive(lines, expense.isPaid() ? AccountService.CASH : AccountService.ACCOUNTS_PAYABLE, expense.getAmount(), null, false);

        post("EXPENSE", expense.getId(), "Expense — " + safe(expense.getPayeeName()), toInstant(expense.getExpenseDate()), lines, expense.getBranchId());
    }

    /**
     * Called from ExpenseService.voidExpense — reverses an already-APPROVED (and so already
     * posted) expense's entry with an equal-and-opposite one, same "never erase a real posted
     * event, reverse it instead" stance as {@code postPosReturn}/{@code postSalesOrderReturn}/
     * {@code postPurchaseReturn}. What {@code postExpense} debited (the expense-category account)
     * is credited here, and what it credited (Cash or Accounts Payable) is debited here.
     */
    @Transactional
    public void postExpenseVoid(Expense expense) {
        Long costCenterId = expense.getCostCenter() != null ? expense.getCostCenter().getId() : null;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, expense.isPaid() ? AccountService.CASH : AccountService.ACCOUNTS_PAYABLE, expense.getAmount(), "Expense void", true);
        addIfPositive(lines, expense.getAccount().getCode(), expense.getAmount(), "Expense void — " + safe(expense.getMemo()), false, costCenterId);

        post("EXPENSE_VOID", expense.getId(), "Void of expense — " + safe(expense.getPayeeName()), null, lines, expense.getBranchId());
    }

    /**
     * Called from ExpenseService.settle — the moment a bill that posted unpaid (paid == false,
     * credited to Accounts Payable at approval time via postExpense) is actually paid off. Only
     * moves cash against the payable; never touches the expense-category side again, since that
     * was already recognized in full at approval time, not deferred until settlement.
     */
    @Transactional
    public void postExpenseSettlement(Expense expense, java.time.LocalDate settledDate) {
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, AccountService.ACCOUNTS_PAYABLE, expense.getAmount(), "Bill settled", true);
        addIfPositive(lines, AccountService.CASH, expense.getAmount(), null, false);

        post("EXPENSE_SETTLEMENT", expense.getId(), "Settled bill — " + safe(expense.getPayeeName()), toInstant(settledDate), lines, expense.getBranchId());
    }

    /** Called from IncomeService.create — see postExpense's javadoc on cost-center tagging (the same rule applies to the revenue-side line here). */
    @Transactional
    public void postIncome(Income income) {
        Long costCenterId = income.getCostCenter() != null ? income.getCostCenter().getId() : null;
        List<LineSpec> lines = new ArrayList<>();
        addIfPositive(lines, income.isReceived() ? AccountService.CASH : AccountService.ACCOUNTS_RECEIVABLE, income.getAmount(), null, true);
        addIfPositive(lines, income.getAccount().getCode(), income.getAmount(), income.getMemo(), false, costCenterId);

        post("INCOME", income.getId(), "Income — " + safe(income.getPayeeName()), toInstant(income.getIncomeDate()), lines, income.getBranchId());
    }

    private java.time.Instant toInstant(java.time.LocalDate date) {
        return date != null ? date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : null;
    }

    // ---- Core posting helper ----

    private BigDecimal trackedLineCost(com.posapp.order.SaleOrder order) {
        BigDecimal total = BigDecimal.ZERO;
        for (com.posapp.order.OrderLine line : order.getLines()) {
            if (line.getProduct() != null && line.getProduct().isTrackStock()) {
                total = total.add(com.posapp.order.OrderCalculator.lineCost(line));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void addIfPositive(List<LineSpec> lines, String wellKnownKey, BigDecimal amount, String description, boolean debit) {
        addIfPositive(lines, wellKnownKey, amount, description, debit, null);
    }

    /** See LineSpec/JournalEntryLine.costCenter's own javadoc — costCenterId is normally null; only Expense/Income postings ever pass one. */
    private void addIfPositive(List<LineSpec> lines, String wellKnownKey, BigDecimal amount, String description, boolean debit, Long costCenterId) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new LineSpec(wellKnownKey, amount, description, debit, costCenterId));
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Builds and saves one balanced {@link JournalEntry} from a list of well-known-account lines.
     * If the lines as given don't balance to the penny — which should never happen from any of the
     * postXxx methods above, since each one is constructed from figures that are mathematically
     * equal on both sides, but could in principle drift from an edge case like an offline-synced
     * sale's price/tax estimate not exactly matching the server's recomputed figures (see {@code
     * SaleOrder.syncNote}'s own javadoc) — the difference is plugged into the {@code SUSPENSE}
     * account and a warning is logged, rather than throwing and rolling back whatever real-world
     * event this posting was trying to record (see class javadoc).
     */
    private void post(String sourceType, Long sourceId, String memo, java.time.Instant entryDate, List<LineSpec> lineSpecs) {
        post(sourceType, sourceId, memo, entryDate, lineSpecs, null);
    }

    /** Same, tagging the entry with the branch of the originating document (null = head office / unattributed) — see {@link JournalEntry#getBranchId}. */
    private void post(String sourceType, Long sourceId, String memo, java.time.Instant entryDate, List<LineSpec> lineSpecs, Long branchId) {
        if (accountRepository.count() == 0) {
            accountService.seedDefaultChartIfNeeded();
        }
        if (lineSpecs.isEmpty()) {
            return;
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LineSpec spec : lineSpecs) {
            if (spec.debit) totalDebit = totalDebit.add(spec.amount);
            else totalCredit = totalCredit.add(spec.amount);
        }
        BigDecimal diff = totalDebit.subtract(totalCredit);
        if (diff.abs().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Journal entry for {}#{} did not balance (debits {} vs credits {}) — plugging {} into Suspense",
                    sourceType, sourceId, totalDebit, totalCredit, diff.abs());
            lineSpecs.add(new LineSpec(AccountService.SUSPENSE, diff.abs(), "Balancing plug", diff.compareTo(BigDecimal.ZERO) < 0, null));
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntryNumber("JE-" + System.currentTimeMillis());
        entry.setEntryDate(entryDate != null ? entryDate : java.time.Instant.now());
        entry.setMemo(memo);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setBranchId(branchId);
        for (LineSpec spec : lineSpecs) {
            Account account = accountService.getByWellKnownKeyOrThrow(spec.wellKnownKey());
            JournalEntryLine line = new JournalEntryLine();
            line.setEntry(entry);
            line.setAccount(account);
            line.setDebit(spec.debit ? spec.amount : BigDecimal.ZERO);
            line.setCredit(spec.debit ? BigDecimal.ZERO : spec.amount);
            line.setDescription(spec.description);
            if (spec.costCenterId() != null) {
                line.setCostCenter(costCenterRepository.findById(spec.costCenterId()).orElse(null));
            }
            entry.getLines().add(line);
        }
        JournalEntry saved = journalEntryRepository.save(entry);
        auditLogService.record("JOURNAL_ENTRY_POSTED", memo, "JournalEntry", saved.getId());
    }

    private record LineSpec(String wellKnownKey, BigDecimal amount, String description, boolean debit, Long costCenterId) {
    }
}
