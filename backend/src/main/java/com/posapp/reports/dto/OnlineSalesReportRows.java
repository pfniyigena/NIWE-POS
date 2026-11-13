package com.posapp.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Rows of the online sales reports. A sale counts on the day the online order was completed. */
public final class OnlineSalesReportRows {

    private OnlineSalesReportRows() {
    }

    /** One day: orders completed that day, what they were invoiced, and the returns refunded that day. */
    public record DayRow(LocalDate date, int orders, int delivery, int pickup, BigDecimal goods, BigDecimal discounts, BigDecimal deliveryFees,
                         BigDecimal invoiced, int returns, BigDecimal refunded, BigDecimal net) {
    }

    /** One completed order with the invoice it produced. */
    public record OrderRow(LocalDate date, String time, String order, String customer, String delivery, String payment, String coupon, BigDecimal goods,
                           BigDecimal discount, BigDecimal deliveryFee, BigDecimal orderTotal, String invoice, BigDecimal invoiced, BigDecimal paid, BigDecimal refunded) {
    }

    /** How the invoices of the completed orders were paid. */
    public record PaymentRow(String method, int payments, BigDecimal received) {
    }

    /** Orders placed in the period that are not (yet) sales: still open, waiting for payment, never paid, cancelled or rejected. */
    public record StatusRow(String group, int orders, BigDecimal value, BigDecimal received, BigDecimal refunded) {
    }

    public record CouponRow(String coupon, int orders, BigDecimal discounts, BigDecimal deliveryWaived, BigDecimal invoiced) {
    }

    public record ProductRow(String product, BigDecimal sold, BigDecimal returned, BigDecimal netQuantity, BigDecimal revenue, BigDecimal refunded, BigDecimal net) {
    }
}
