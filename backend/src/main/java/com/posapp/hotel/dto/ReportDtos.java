package com.posapp.hotel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The manager's reports (HO-7): the day, revenue by booking source, the forecast with pick-up, and the night audit reconciled to Accounting. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record RevenueLineDto(String type, long lines, BigDecimal net, BigDecimal tax, BigDecimal gross) {
    }

    /** Money taken in one payment method on a day. {@code net} = deposits + payments - refunds. */
    public record MoneyLineDto(String method, BigDecimal deposits, BigDecimal payments, BigDecimal refunds, BigDecimal net) {
    }

    public record PeriodDto(String label, LocalDate from, LocalDate to, long roomNightsSold, long roomNightsAvailable, BigDecimal occupancyPercent,
                            BigDecimal roomRevenue, BigDecimal adr, BigDecimal revPar) {
    }

    public record DailyReportDto(LocalDate date, boolean today, int totalRooms, int outOfOrder, int sellable, int roomsOccupied, BigDecimal occupancyPercent,
                                 BigDecimal roomRevenue, BigDecimal expectedRoomRevenue, BigDecimal adr, BigDecimal revPar,
                                 int arrivals, int departures, int stayOvers, int arrivalsTomorrow, int departuresTomorrow,
                                 int inHouseNow, BigDecimal inHouseBalance, List<RevenueLineDto> revenue, BigDecimal revenueTotal,
                                 List<MoneyLineDto> money, BigDecimal moneyTotal, PeriodDto monthToDate, PeriodDto yearToDate) {
    }

    public record SourceRowDto(String source, long bookings, long stays, long cancelled, long noShows, long roomNights, BigDecimal roomRevenue,
                               BigDecimal otherRevenue, BigDecimal adr, BigDecimal averageStay, BigDecimal averageLeadDays, BigDecimal cancelPercent) {
    }

    public record SourceReportDto(LocalDate from, LocalDate to, List<SourceRowDto> rows, SourceRowDto total) {
    }

    public record ForecastDayDto(LocalDate date, String weekday, int capacity, int booked, int held, BigDecimal occupancyPercent, BigDecimal revenue,
                                 int arrivals, int departures, int pickup, BigDecimal pickupRevenue) {
    }

    public record ForecastTotalsDto(long roomNightsBooked, long capacityNights, BigDecimal occupancyPercent, BigDecimal revenue, BigDecimal adr,
                                    long pickup, BigDecimal pickupRevenue) {
    }

    public record ForecastDto(LocalDate from, LocalDate to, int pickupDays, List<ForecastDayDto> days, ForecastTotalsDto totals) {
    }

    /** One comparison between what the hotel's records say and what Accounting holds. */
    public record AuditCheckDto(String code, String label, BigDecimal hotel, BigDecimal accounting, BigDecimal difference, boolean ok, String note) {
    }

    public record AccountMovementDto(String account, BigDecimal debit, BigDecimal credit) {
    }

    public record NightAuditReportDto(LocalDate date, LocalDate lastAuditRun, String lastAuditSummary, String postingMode, boolean auditRanForDate,
                                      int inHouseStays, int arrivals, int departures, long roomNightsCharged, BigDecimal roomRevenue,
                                      List<RevenueLineDto> charged, BigDecimal chargedTotal, List<MoneyLineDto> money, BigDecimal moneyTotal,
                                      List<AccountMovementDto> postings, List<AuditCheckDto> checks, boolean balanced) {
    }
}
