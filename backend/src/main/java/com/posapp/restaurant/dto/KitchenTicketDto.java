package com.posapp.restaurant.dto;


import java.math.BigDecimal;import com.posapp.restaurant.RestaurantOrder;
import com.posapp.restaurant.RestaurantOrderLine;

import java.time.Instant;

/**
 * One row on the Kitchen Display (Fourier: "Kitchen Order Tickets / Kitchen Display") — one order
 * line, with just enough order/table context for the kitchen to know where it's going.
 * {@code tableName} is really a display label now (Fourier: "Delivery & takeaway") — the table's
 * own name for a dine-in order, else "Takeaway"/"Delivery" plus the customer name; kept under its
 * original name so existing frontend code and this field's meaning to the kitchen ("where does
 * this go") both still read naturally.
 */
public record KitchenTicketDto(
        Long orderId,
        Long lineId,
        String tableName,
        String description,
        BigDecimal quantity,
        String notes,
        String kitchenStatus,
        Instant orderOpenedAt,
        /** Snapshotted kitchen prep area (Fourier: "Kitchen stations") — null if the menu item had none assigned; the Kitchen Display groups those under "Unassigned". */
        String station,
        int course,
        /** The picked options, for example "Large", "No ice". */
        java.util.List<String> options,
        String allergens,
        /** Allergens the guests told us about, for the whole order. */
        String guestAllergens,
        /** Allergens of this item that a guest cannot have, else null. */
        String allergyConflict,
        Instant sentAt,
        Instant readyAt,
        /** Minutes this ticket should take. */
        int targetMinutes,
        /** Minutes since it reached the kitchen (until it was ready, once it is). */
        int elapsedMinutes,
        /** OK, WARN (amber) or LATE (red). */
        String urgency,
        int remakeCount,
        Integer seat,
        int guestCount
) {
    public static KitchenTicketDto from(RestaurantOrder order, RestaurantOrderLine line) {
        return from(order, line, Instant.now(), 15, 75);
    }

    public static KitchenTicketDto from(RestaurantOrder order, RestaurantOrderLine line, Instant now, int defaultPrepMinutes, int warnPercent) {
        var hits = com.posapp.restaurant.RestaurantAllergens.overlap(line.getAllergens(), order.getGuestAllergens());
        String conflict = hits.isEmpty() ? null : String.join(",", hits);
        return new KitchenTicketDto(
                order.getId(),
                line.getId(),
                order.displayLabel(),
                line.getDescription(),
                line.getQuantity(),
                line.getNotes(),
                line.getKitchenStatus().name(),
                order.getOpenedAt(),
                line.getStation(),
                line.getCourse(),
                line.getOptions().stream().map(com.posapp.restaurant.RestaurantOrderLineOption::getOptionName).toList(),
                line.getAllergens(),
                order.getGuestAllergens(),
                conflict,
                line.getSentAt(),
                line.getReadyAt(),
                com.posapp.restaurant.KitchenTiming.target(line, defaultPrepMinutes),
                (int) (com.posapp.restaurant.KitchenTiming.elapsedSeconds(line, now) / 60),
                com.posapp.restaurant.KitchenTiming.urgency(line, now, defaultPrepMinutes, warnPercent),
                line.getRemakeCount(),
                line.getSeat(),
                order.getGuestCount()
        );
    }
}
