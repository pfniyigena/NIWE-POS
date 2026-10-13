package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantOrder;
import com.posapp.restaurant.RestaurantOrderCalculator;
import com.posapp.restaurant.RestaurantPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RestaurantOrderDto(
        Long id,
        Long tableId,
        String tableName,
        String status,
        /** Fourier: "Delivery & takeaway" — DINE_IN/TAKEAWAY/DELIVERY. */
        String orderType,
        /** What the UI should display in place of a table name — the table's own name for DINE_IN, else "Takeaway"/"Delivery" plus the customer name. See RestaurantOrder.displayLabel's javadoc. */
        String displayLabel,
        int guestCount,
        Long serverId,
        String serverName,
        String customerName,
        String customerPhone,
        String customerAddress,
        Long driverId,
        String driverName,
        /** Only non-null for DELIVERY orders. */
        String deliveryStatus,
        String notes,
        Instant openedAt,
        Instant closedAt,
        String ticketNumber,
        List<RestaurantOrderLineDto> lines,
        List<RestaurantPaymentDto> payments,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total,
        BigDecimal paidTotal,
        BigDecimal balanceDue,
        /** Hotel module: room-service / charged-to-room booking, else null. */
        Long roomBookingId
) {
    public static RestaurantOrderDto from(RestaurantOrder order) {
        var table = order.getTable();
        var server = order.getServer();
        var driver = order.getDriver();
        BigDecimal subtotal = RestaurantOrderCalculator.subtotal(order);
        BigDecimal taxTotal = RestaurantOrderCalculator.taxTotal(order);
        BigDecimal total = subtotal.add(taxTotal);
        BigDecimal paidTotal = order.getPayments().stream().map(RestaurantPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RestaurantOrderDto(
                order.getId(),
                table != null ? table.getId() : null,
                table != null ? table.getName() : null,
                order.getStatus().name(),
                order.getOrderType().name(),
                order.displayLabel(),
                order.getGuestCount(),
                server != null ? server.getId() : null,
                server != null ? server.getFullName() : null,
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getCustomerAddress(),
                driver != null ? driver.getId() : null,
                driver != null ? driver.getFullName() : null,
                order.getDeliveryStatus() != null ? order.getDeliveryStatus().name() : null,
                order.getNotes(),
                order.getOpenedAt(),
                order.getClosedAt(),
                order.getTicketNumber(),
                order.getLines().stream().map(RestaurantOrderLineDto::from).toList(),
                order.getPayments().stream().map(RestaurantPaymentDto::from).toList(),
                subtotal,
                taxTotal,
                total,
                paidTotal,
                total.subtract(paidTotal),
                order.getRoomBookingId()
        );
    }
}
