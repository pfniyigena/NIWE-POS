package com.posapp.credit.dto;

import com.posapp.credit.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of a customer's outstanding-orders list — backs the Record Payment screen's allocation
 * table (Task: "Payment allocation to orders", scope decided as "manual, oldest-first suggested"):
 * the backend returns these oldest-first (see {@code OrderRepository.findCreditOrdersForCustomer}'s
 * ordering), and the frontend builds its default oldest-first allocation suggestion from this
 * list — the backend doesn't need to know a payment amount to return it, so the suggestion can be
 * recomputed instantly as the admin edits the amount, with no extra round-trip.
 *
 * <p>{@code orderType} distinguishes a POS credit sale from a confirmed/fulfilled back-office
 * Sales Order (Task: "Add Sale order and Customer payment... so it shows up alongside register
 * sales in one place") — both kinds are merged into one oldest-first list by {@code
 * CustomerCreditService.listOutstandingOrders}. {@code invoiceNumber} carries whichever order's
 * own identifier applies ({@code SaleOrder.invoiceNumber} or {@code SalesOrder.orderNumber}).
 */
public record OutstandingOrderDto(
        Long orderId,
        OrderType orderType,
        String invoiceNumber,
        Instant completedAt,
        LocalDate dueDate,
        BigDecimal creditAmount,
        BigDecimal amountOutstanding
) {
}
