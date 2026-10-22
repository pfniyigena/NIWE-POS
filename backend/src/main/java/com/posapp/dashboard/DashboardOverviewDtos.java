package com.posapp.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the dashboard shows beyond the basic tiles: things to act on, how sales compare, who owes, what to pay, what sells. */
public final class DashboardOverviewDtos {

    private DashboardOverviewDtos() {
    }

    /** One thing that needs attention. {@code key} says what it is (the screen maps it to a list); {@code amount} is null when money does not apply. */
    public record AttentionItem(String key, String label, long count, BigDecimal amount) {
    }

    /** One day (UTC) of selling: till sales and invoices issued, kept apart so they are never mixed up. */
    public record DayPoint(LocalDate date, int posCount, BigDecimal pos, int invoiceCount, BigDecimal invoiced) {
    }

    /** Sales (till + invoices) compared with the same weekday last week, and the last 7 days with the 7 before. */
    public record Comparison(BigDecimal today, BigDecimal sameDayLastWeek, BigDecimal last7, BigDecimal previous7, int todayCount, int sameDayLastWeekCount) {
    }

    public record Owing(Long customerId, String name, BigDecimal outstanding, BigDecimal overdue, long oldestDaysOverdue) {
    }

    public record OwingBlock(BigDecimal totalOutstanding, BigDecimal totalOverdue, int overdueItems, int customersOwing, int customersOverdue, List<Owing> top) {
    }

    public record UnpaidBill(Long billId, String billNumber, String supplierName, BigDecimal outstanding, long ageDays) {
    }

    public record BillsBlock(BigDecimal totalUnpaid, int unpaidBills, List<UnpaidBill> oldest) {
    }

    public record TopSeller(Long productId, String name, String sku, long quantity, BigDecimal revenue) {
    }

    public record Overview(LocalDate today, List<AttentionItem> attention, List<DayPoint> days, Comparison comparison, OwingBlock owing, BillsBlock bills, List<TopSeller> topSellers,
                           int topSellersDays) {
    }
}
