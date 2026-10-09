package com.posapp.reports.dto;

import java.time.Instant;

/**
 * "Restaurant Cancelled Orders" — every order cancelled (not checked out) in the date range, any
 * order type, table freed with nothing ever sold ({@code RestaurantOrderService#cancel} posts no
 * accounting entry). {@code wouldBeTotal} is what the bill would have come to had it been checked
 * out instead — the lines/prices as they stood at the moment of cancellation — shown as a sense
 * of scale for "how much business walked away," never posted or counted as revenue anywhere.
 * {@code ticketNumber} is always blank: a ticket number is only assigned at checkout (see {@code
 * RestaurantOrder.ticketNumber}'s own javadoc), and a cancelled order never reaches one.
 */
public record RestaurantCancelledOrderRow(
        Long orderId,
        String orderType,
        String tableOrCustomer,
        String serverName,
        Instant openedAt,
        Instant cancelledAt,
        int itemCount,
        java.math.BigDecimal wouldBeTotal
) {
}
