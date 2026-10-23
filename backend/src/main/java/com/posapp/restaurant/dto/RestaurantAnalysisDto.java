package com.posapp.restaurant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything on the Sales analysis screen for one date range. */
public record RestaurantAnalysisDto(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<Hour> hours,
        List<Weekday> weekdays,
        List<Day> days,
        List<Type> types,
        List<Payment> payments,
        List<Item> items,
        List<Category> categories,
        List<ServerRow> servers,
        List<TableRow> tables,
        List<Control> controls
) {
    public record Totals(int checks, int parties, int covers, BigDecimal itemsSold, BigDecimal gross, BigDecimal discounts, BigDecimal netSales,
                         BigDecimal serviceCharge, BigDecimal tax, BigDecimal rounding, BigDecimal billed, BigDecimal tips,
                         BigDecimal averageCheck, BigDecimal averagePerCover, BigDecimal cost, BigDecimal profit, BigDecimal marginPercent,
                         int averageMinutesAtTable) {
    }

    public record Hour(int hour, int checks, int covers, BigDecimal netSales) {
    }

    public record Weekday(int day, String name, int days, int checks, int covers, BigDecimal netSales, BigDecimal averageNetPerDay) {
    }

    public record Day(LocalDate date, int checks, int covers, BigDecimal netSales, BigDecimal billed) {
    }

    public record Type(String type, int checks, int covers, BigDecimal netSales, BigDecimal billed, BigDecimal averageCheck) {
    }

    public record Payment(String method, int checks, BigDecimal amount, BigDecimal percent) {
    }

    /** One dish. {@code popularity} is its share of all dishes sold; {@code classification} is STAR, PLOWHORSE, PUZZLE or DOG. */
    public record Item(Long productId, String name, String category, BigDecimal quantity, BigDecimal netSales, BigDecimal salesPercent, BigDecimal cost,
                       BigDecimal profit, BigDecimal marginPercent, BigDecimal unitProfit, BigDecimal popularity, String classification) {
    }

    public record Category(Long categoryId, String name, BigDecimal quantity, BigDecimal netSales, BigDecimal salesPercent, BigDecimal cost,
                           BigDecimal profit, BigDecimal marginPercent) {
    }

    public record ServerRow(Long serverId, String name, int checks, int covers, BigDecimal netSales, BigDecimal averageCheck, BigDecimal serviceCharge,
                            BigDecimal tips, BigDecimal discounts, int voids, BigDecimal voidAmount, int comps, BigDecimal compAmount) {
    }

    public record TableRow(Long tableId, String name, String section, int seats, int parties, int covers, BigDecimal turnsPerDay,
                           int averageMinutes, BigDecimal netSales, BigDecimal salesPerCover) {
    }

    /** Voids, comps, cancelled orders and receipt copies in the range. */
    public record Control(String type, int count, BigDecimal amount) {
    }
}
