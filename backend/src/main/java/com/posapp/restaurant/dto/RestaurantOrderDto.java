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
        Long roomBookingId,
        /** Everything taken off the bill by discounts (line discounts and the order discount). */
        BigDecimal discountTotal,
        BigDecimal orderDiscountPercent,
        BigDecimal orderDiscountAmount,
        String discountReason,
        String discountBy,
        BigDecimal serviceChargePercent,
        /** The service charge in money (0 when waived or not charged). */
        BigDecimal serviceCharge,
        boolean serviceChargeWaived,
        /** What rounding added to (or took from) the bill. */
        BigDecimal roundingAdjustment,
        /** The tip given at checkout. */
        BigDecimal tipAmount,
        /** The change handed back at checkout. */
        BigDecimal changeGiven,
        /** The highest course released to the kitchen so far. */
        int firedCourse,
        /** Allergens the guests told us about (comma separated). */
        String guestAllergens,
        /** "Check 2" once the bill has been split, else null. */
        String checkLabel,
        Long parentOrderId,
        /** How many times the receipt has been printed. */
        int printCount,
        String cancelReason,
        /** A reservation deposit already paid and taken off this bill. */
        BigDecimal depositApplied,
        /** STAFF, QR or WEB: where the order came from. */
        String source,
        String onlineOrderCode
) {
    public static RestaurantOrderDto from(RestaurantOrder order) {
        var table = order.getTable();
        var server = order.getServer();
        var driver = order.getDriver();
        BigDecimal subtotal = RestaurantOrderCalculator.subtotal(order);
        BigDecimal taxTotal = RestaurantOrderCalculator.taxTotal(order);
        BigDecimal total = RestaurantOrderCalculator.total(order);
        BigDecimal tip = order.getTipAmount() == null ? BigDecimal.ZERO : order.getTipAmount();
        BigDecimal deposit = order.getDepositApplied() == null ? BigDecimal.ZERO : order.getDepositApplied();
        BigDecimal depositUsed = deposit.min(total.max(BigDecimal.ZERO));
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
                total.add(tip).subtract(paidTotal).subtract(depositUsed),
                order.getRoomBookingId(),
                RestaurantOrderCalculator.discountTotal(order),
                order.getDiscountPercent(),
                order.getDiscountAmount(),
                order.getDiscountReason(),
                order.getDiscountBy(),
                order.getServiceChargePercent(),
                RestaurantOrderCalculator.serviceCharge(order),
                order.isServiceChargeWaived(),
                RestaurantOrderCalculator.roundingAdjustment(order),
                tip,
                order.getChangeGiven(),
                order.getFiredCourse(),
                order.getGuestAllergens(),
                order.getCheckLabel(),
                order.getParentOrderId(),
                order.getPrintCount(),
                order.getCancelReason(),
                deposit,
                order.getSource() == null ? "STAFF" : order.getSource(),
                order.getOnlineOrderCode()
        );
    }
}
