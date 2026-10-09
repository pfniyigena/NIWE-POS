package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantOrder;
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
        int quantity,
        String notes,
        String kitchenStatus,
        Instant orderOpenedAt,
        /** Snapshotted kitchen prep area (Fourier: "Kitchen stations") — null if the menu item had none assigned; the Kitchen Display groups those under "Unassigned". */
        String station
) {
    public static KitchenTicketDto from(RestaurantOrder order, RestaurantOrderLine line) {
        return new KitchenTicketDto(
                order.getId(),
                line.getId(),
                order.displayLabel(),
                line.getDescription(),
                line.getQuantity(),
                line.getNotes(),
                line.getKitchenStatus().name(),
                order.getOpenedAt(),
                line.getStation()
        );
    }
}
