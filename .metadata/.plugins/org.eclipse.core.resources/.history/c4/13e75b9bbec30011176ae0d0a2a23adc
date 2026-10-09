package com.posapp.order.dto;

import com.posapp.order.OrderCalculator;
import com.posapp.order.SaleOrder;
import com.posapp.register.CashierSession;
import com.posapp.register.RegisterSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrderDto(
        Long id,
        Long sessionId,
        Long cashierSessionId,
        Long customerId,
        String customerName,
        /** Fourier: "sending ... receipts ... to the customer's email" — for the Receipts screen's "email this" prompt to pre-fill. Null for a walk-in/no-customer sale, or a customer with none on file. */
        String customerEmail,
        String status,
        String note,
        String invoiceNumber,
        Instant createdAt,
        Instant completedAt,
        List<OrderLineDto> lines,
        List<PaymentDto> payments,
        int totalQuantity,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total,
        /** Task: "add two logo (URA LOGO, EFRIS LOGO)" — lets the receipt look up this order's own terminal's EfrisSetting (registerId, not sessionId) to fetch its logos. Null for an order with no session/register at all. */
        Long registerId,
        String registerName,
        String cashierName,
        BigDecimal creditAmount,
        LocalDate dueDate,
        /** Task: "local queuing of sales that sync once connectivity returns" — null for an ordinary online sale. */
        Instant offlineQueuedAt,
        /** Set only when an offline-synced sale's final total didn't match the terminal's offline estimate — see SaleOrder.syncNote's javadoc. */
        String syncNote,
        /** EFRIS (Rwanda RRA e-invoicing) fiscal receipt fields — all null until a sale is actually submitted to EFRIS. See SaleOrder's javadoc. */
        String sdcId,
        String sdcDate,
        String sdcReceiptNumber,
        String internalData,
        String signatureData,
        String cisNumber,
        String cisDate,
        String mrcData,
        /** When an EFRIS submission was last attempted (success or failure) — null if never attempted. */
        Instant efrisSubmittedAt,
        /** Set only when an attempted EFRIS submission failed — see SaleOrder.efrisError's javadoc. */
        String efrisError,
        /** The raw EFRIS response body from the most recent attempt — see SaleOrder.efrisResponse's javadoc. */
        String efrisResponse,
        /** The branch this sale belongs to (null on sales older than branches = head office) — the receipt uses it to print the branch header. */
        Long branchId
) {
    public static OrderDto from(SaleOrder order) {
        var customer = order.getCustomer();
        RegisterSession session = order.getSession();
        CashierSession cashierSession = order.getCashierSession();
        var register = session != null ? session.getRegister() : null;
        // Prefer the cashier who actually rang up this order (their own cashier session, when
        // more than one person shares a shift); fall back to the shift's opener for orders
        // created before this feature existed, which have no cashierSession at all.
        var cashier = cashierSession != null ? cashierSession.getCashier()
                : (session != null ? session.getCashier() : null);
        return new OrderDto(
                order.getId(),
                session != null ? session.getId() : null,
                cashierSession != null ? cashierSession.getId() : null,
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : "Walk-in Customer",
                customer != null ? customer.getEmail() : null,
                order.getStatus().name(),
                order.getNote(),
                order.getInvoiceNumber(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getLines().stream().map(OrderLineDto::from).toList(),
                order.getPayments().stream().map(PaymentDto::from).toList(),
                OrderCalculator.totalQuantity(order),
                OrderCalculator.subtotal(order),
                OrderCalculator.taxTotal(order),
                OrderCalculator.total(order),
                register != null ? register.getId() : null,
                register != null ? register.getName() : "—",
                cashier != null ? cashier.getFullName() : "—",
                order.getCreditAmount(),
                order.getDueDate(),
                order.getOfflineQueuedAt(),
                order.getSyncNote(),
                order.getSdcId(),
                order.getSdcDate(),
                order.getSdcReceiptNumber(),
                order.getInternalData(),
                order.getSignatureData(),
                order.getCisNumber(),
                order.getCisDate(),
                order.getMrcData(),
                order.getEfrisSubmittedAt(),
                order.getEfrisError(),
                order.getEfrisResponse(),
                order.getBranchId()
        );
    }
}
