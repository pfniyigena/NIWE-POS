package com.posapp.credit.dto;

import com.posapp.credit.CustomerPayment;
import com.posapp.credit.CustomerPaymentAllocation;
import com.posapp.credit.OrderType;
import com.posapp.credit.PaymentSource;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A recorded customer payment plus how it was allocated (Task: "Payment receipt") — the same
 * shape backs the immediate response right after recording a payment, one row of payment history
 * (Task: "Payment history"), and a reprinted receipt by id. {@code customerBalanceAfter} is
 * always the customer's *current* outstanding balance at the moment this DTO is built, not a
 * historical snapshot frozen at the time this particular payment was made — for the immediate
 * post-record response the two are the same thing, but for an older payment reprinted later, it
 * reflects today's balance (which may include sales/payments made since). Labelled "Customer
 * balance" rather than "Balance after this payment" on screen for that reason.
 *
 * <p>Fourier's own follow-up: "On Customer payment list no POS payment for cash sale." This row
 * shape now also represents a POS sale's own tendered payment (see {@link #fromPosPayment}), not
 * just an actual {@link CustomerPayment} — {@link #source} says which. A POS-sourced row has no
 * backing {@code CustomerPayment} row at all (see {@code CustomerPayment}'s own class javadoc for
 * why a checkout tender was never modeled as one), so {@code id} is null, {@code recordedByName}
 * is null (nobody "recorded" it — it was tendered at checkout), and {@code allocations} holds a
 * single synthetic entry pointing back at the sale itself, so the "Applied to" column a reader is
 * already used to reads sensibly for this kind of row too, rather than looking oddly empty.
 */
public record CustomerPaymentDto(
        Long id,
        Long customerId,
        String customerName,
        BigDecimal amount,
        Instant paidAt,
        /** See CustomerPayment.method's javadoc — null for a payment recorded before this field existed. */
        String method,
        String note,
        /** The settlement's own receipt number, or — for a POS-sourced row — the sale's invoice number, since that already is that payment's own receipt. */
        String receiptNumber,
        String recordedByName,
        List<AllocationDto> allocations,
        BigDecimal customerBalanceAfter,
        PaymentSource source
) {
    /** {@code orderType} distinguishes a POS credit sale from a Sales Order allocation — see CustomerPaymentAllocation's "exclusive arc" javadoc. */
    public record AllocationDto(Long orderId, OrderType orderType, String invoiceNumber, BigDecimal amountApplied) {
    }

    public static CustomerPaymentDto from(CustomerPayment payment, BigDecimal customerBalanceAfter) {
        return new CustomerPaymentDto(
                payment.getId(),
                payment.getCustomer().getId(),
                payment.getCustomer().getName(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getMethod(),
                payment.getNote(),
                payment.getReceiptNumber(),
                payment.getRecordedBy() != null ? payment.getRecordedBy().getFullName() : "—",
                payment.getAllocations().stream()
                        .map(CustomerPaymentDto::allocationFrom)
                        .toList(),
                customerBalanceAfter,
                PaymentSource.SETTLEMENT
        );
    }

    /**
     * A POS sale's own tendered payment, merged into the same history — see this record's own
     * javadoc. {@code order.getCustomer()} must be non-null (the caller only calls this for a
     * customer-attached sale; a walk-in/no-customer sale has nowhere to merge into).
     */
    public static CustomerPaymentDto fromPosPayment(Payment payment, SaleOrder order, BigDecimal customerBalanceAfter) {
        var customer = order.getCustomer();
        return new CustomerPaymentDto(
                null,
                customer.getId(),
                customer.getName(),
                payment.getAmount(),
                order.getCompletedAt(),
                payment.getMethod(),
                null,
                order.getInvoiceNumber(),
                null,
                List.of(new AllocationDto(order.getId(), OrderType.POS_SALE, order.getInvoiceNumber(), payment.getAmount())),
                customerBalanceAfter,
                PaymentSource.POS_SALE
        );
    }

    /** Branches on which side of the three-way "exclusive arc" is set — see CustomerPaymentAllocation's javadoc. */
    private static AllocationDto allocationFrom(CustomerPaymentAllocation allocation) {
        if (allocation.getInvoice() != null) {
            return new AllocationDto(
                    allocation.getInvoice().getId(),
                    OrderType.INVOICE,
                    allocation.getInvoice().getInvoiceNumber(),
                    allocation.getAmountApplied()
            );
        }
        if (allocation.getSalesOrder() != null) {
            return new AllocationDto(
                    allocation.getSalesOrder().getId(),
                    OrderType.SALES_ORDER,
                    allocation.getSalesOrder().getOrderNumber(),
                    allocation.getAmountApplied()
            );
        }
        return new AllocationDto(
                allocation.getOrder().getId(),
                OrderType.POS_SALE,
                allocation.getOrder().getInvoiceNumber(),
                allocation.getAmountApplied()
        );
    }
}
