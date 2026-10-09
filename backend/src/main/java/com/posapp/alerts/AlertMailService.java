package com.posapp.alerts;

import com.posapp.accounting.Expense;
import com.posapp.catalog.Product;
import com.posapp.customer.Customer;
import com.posapp.email.MailService;
import com.posapp.inventory.PurchaseRequisition;
import com.posapp.quotation.Quotation;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fourier: "Wiring a handful of business events to that same mailer would close this cheaply" —
 * the one place every passive business-event alert (low stock, a purchase requisition awaiting
 * approval, a quotation about to expire, a customer nearing their credit limit) turns into an
 * actual email, all sent to the single {@link StoreSettings#getNotificationsEmail()} address
 * rather than to a customer/supplier (see that field's own javadoc).
 *
 * <p>Every public method here is {@code @Async} (needs {@code @EnableAsync} on
 * {@code PosApplication} — now added) and swallows its own exceptions: an alert email must never
 * fail, slow down, or roll back the real business transaction that triggered it (stock being
 * adjusted, a requisition being submitted, a checkout accepting a credit sale), the same
 * "best-effort, never block the caller" treatment {@code QuotationService.send}'s own auto-email
 * to a customer already gets, just applied to every alert here rather than one hand-rolled
 * try/catch. No-ops silently — not even a log line, so a store that hasn't opted into this yet
 * doesn't get its logs spammed on every low-stock sale — whenever {@code notificationsEmail} is
 * blank or {@link MailService#isConfigured()} is false; both are perfectly normal states for a
 * store that hasn't set this up.
 */
@Service
public class AlertMailService {

    private static final Logger log = LoggerFactory.getLogger(AlertMailService.class);

    private final MailService mailService;
    private final StoreSettingsService storeSettingsService;

    public AlertMailService(MailService mailService, StoreSettingsService storeSettingsService) {
        this.mailService = mailService;
        this.storeSettingsService = storeSettingsService;
    }

    /** Fired from {@code CatalogService.adjustStock} the moment a tracked product's stock crosses from at/above the low-stock threshold to below it. */
    @Async
    public void alertLowStock(Product product) {
        send("Low stock: " + product.getName(),
                "<p>" + escapeHtml(product.getName()) + " has crossed below its low-stock threshold.</p>"
                        + "<p>Current stock on hand: " + product.getStockQuantity() + "</p>");
    }

    /** Per-warehouse variant, fired from {@code CatalogService.adjustStock(Product, int, WarehouseLocation)} when a non-primary warehouse's total for a tracked product crosses below the same threshold. */
    @Async
    public void alertLowStock(Product product, String warehouseName, int warehouseQuantity) {
        send("Low stock at " + warehouseName + ": " + product.getName(),
                "<p>" + escapeHtml(product.getName()) + " has crossed below its low-stock threshold at warehouse "
                        + escapeHtml(warehouseName) + ".</p>"
                        + "<p>Current stock at " + escapeHtml(warehouseName) + ": " + warehouseQuantity + "</p>");
    }

    /** Fired from {@code PurchaseRequisitionService.submit} on the DRAFT -&gt; PENDING_APPROVAL transition. */
    @Async
    public void alertRequisitionAwaitingApproval(PurchaseRequisition requisition) {
        send("Purchase requisition awaiting approval: " + requisition.getRequisitionNumber(),
                "<p>Purchase requisition " + escapeHtml(requisition.getRequisitionNumber())
                        + " has been submitted and is now awaiting approval.</p>"
                        + "<p>Requested by: " + escapeHtml(requisition.getRequestedByFullName()) + "</p>");
    }

    /** Fired from {@code QuotationService}'s daily scheduled sweep for a SENT quote whose {@code validUntil} is within the configured warning window. */
    @Async
    public void alertQuotationExpiringSoon(Quotation quotation) {
        String customerName = quotation.getCustomer() != null ? quotation.getCustomer().getName() : null;
        send("Quotation expiring soon: " + quotation.getQuoteNumber(),
                "<p>Quotation " + escapeHtml(quotation.getQuoteNumber())
                        + (customerName != null ? " for " + escapeHtml(customerName) : "")
                        + " is valid until " + quotation.getValidUntil() + " and has not yet been accepted or rejected.</p>");
    }

    /** Fired from {@code CustomerCreditService.assertWithinLimit} once a successful credit sale leaves the customer's outstanding balance crossing into the configured warning zone of their credit limit. */
    @Async
    public void alertCustomerNearingCreditLimit(Customer customer, BigDecimal outstanding) {
        send("Customer nearing credit limit: " + customer.getName(),
                "<p>" + escapeHtml(customer.getName()) + " is approaching their credit limit.</p>"
                        + "<p>Outstanding balance: " + outstanding + " of a " + customer.getCreditLimit() + " limit.</p>");
    }

    /**
     * Fired from {@code ExpenseService.create} the moment a new expense is logged as PENDING —
     * same "a document just entered the approval queue" trigger as {@code
     * alertRequisitionAwaitingApproval}. Fires for every new expense regardless of who entered
     * it (including one a self-approving admin entered for themselves), same as that method does.
     */
    @Async
    public void alertExpenseAwaitingApproval(Expense expense) {
        send("Expense awaiting approval: " + expense.getPayeeName(),
                "<p>An expense of " + expense.getAmount() + " to " + escapeHtml(expense.getPayeeName())
                        + " has been logged and is now awaiting approval.</p>"
                        + "<p>Category: " + escapeHtml(expense.getAccount() != null ? expense.getAccount().getName() : null) + "</p>");
    }

    private void send(String subject, String html) {
        try {
            StoreSettings settings = storeSettingsService.getSettingsEntity();
            String to = settings.getNotificationsEmail();
            if (to == null || to.isBlank() || !mailService.isConfigured()) {
                return;
            }
            mailService.send(to.trim(), subject, html, List.of());
        } catch (Exception ex) {
            log.warn("Failed to send alert email ('{}'): {}", subject, ex.getMessage());
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
