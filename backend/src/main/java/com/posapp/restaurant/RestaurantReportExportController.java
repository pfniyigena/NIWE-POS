package com.posapp.restaurant;

import com.posapp.common.ApiException;
import com.posapp.common.ExportFiles;
import com.posapp.reports.ReportExportService;
import com.posapp.restaurant.dto.DayCloseRowDto;
import com.posapp.restaurant.dto.EndOfDayDto;
import com.posapp.restaurant.dto.RestaurantAnalysisDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Excel / PDF / CSV downloads of the sales analysis tables, the Z-report and the list of closed days. */
@RestController
@RequestMapping("/api/restaurant/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class RestaurantReportExportController {

    private final RestaurantReportService service;
    private final ReportExportService exportService;

    public RestaurantReportExportController(RestaurantReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    private static String n(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static List<String> row(String... cells) {
        return List.of(cells);
    }

    @GetMapping("/analysis/export")
    public ResponseEntity<byte[]> analysis(@RequestParam String table,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        RestaurantAnalysisDto a = service.analysis(from, to);
        String when = a.from().equals(a.to()) ? a.from().toString() : a.from() + " to " + a.to();
        List<String> head;
        List<List<String>> body = new ArrayList<>();
        String name;
        switch (table) {
            case "hours" -> {
                name = "Sales by hour";
                head = List.of("Hour", "Checks", "Covers", "Net sales");
                a.hours().stream().filter(h -> h.checks() > 0).forEach(h -> body.add(row(String.format("%02d:00", h.hour()), s(h.checks()), s(h.covers()), n(h.netSales()))));
            }
            case "weekdays" -> {
                name = "Sales by weekday";
                head = List.of("Weekday", "Days", "Checks", "Covers", "Net sales", "Average per day");
                a.weekdays().forEach(w -> body.add(row(w.name(), s(w.days()), s(w.checks()), s(w.covers()), n(w.netSales()), n(w.averageNetPerDay()))));
            }
            case "days" -> {
                name = "Sales by day";
                head = List.of("Date", "Checks", "Covers", "Net sales", "Billed");
                a.days().forEach(d -> body.add(row(d.date().toString(), s(d.checks()), s(d.covers()), n(d.netSales()), n(d.billed()))));
            }
            case "types" -> {
                name = "Sales by order type";
                head = List.of("Type", "Checks", "Covers", "Net sales", "Billed", "Average check");
                a.types().forEach(t -> body.add(row(RestaurantExportController.pretty(t.type()), s(t.checks()), s(t.covers()), n(t.netSales()), n(t.billed()), n(t.averageCheck()))));
            }
            case "payments" -> {
                name = "Payments";
                head = List.of("Method", "Checks", "Amount", "Share %");
                a.payments().forEach(p -> body.add(row(p.method(), s(p.checks()), n(p.amount()), s(p.percent()))));
            }
            case "items" -> {
                name = "Menu mix";
                head = List.of("Item", "Category", "Sold", "Net sales", "Share of sales %", "Cost", "Profit", "Margin %", "Profit each", "Share of dishes %", "Class");
                a.items().forEach(i -> body.add(row(i.name(), i.category(), s(i.quantity()), n(i.netSales()), s(i.salesPercent()), n(i.cost()), n(i.profit()), s(i.marginPercent()),
                        n(i.unitProfit()), s(i.popularity()), RestaurantExportController.pretty(i.classification()))));
            }
            case "categories" -> {
                name = "Sales by category";
                head = List.of("Category", "Sold", "Net sales", "Share %", "Cost", "Profit", "Margin %");
                a.categories().forEach(c -> body.add(row(c.name(), s(c.quantity()), n(c.netSales()), s(c.salesPercent()), n(c.cost()), n(c.profit()), s(c.marginPercent()))));
            }
            case "servers" -> {
                name = "Sales by server";
                head = List.of("Server", "Checks", "Covers", "Net sales", "Average check", "Service charge", "Tips", "Discounts", "Voids", "Void value", "Comps", "Comp value");
                a.servers().forEach(v -> body.add(row(v.name(), s(v.checks()), s(v.covers()), n(v.netSales()), n(v.averageCheck()), n(v.serviceCharge()), n(v.tips()), n(v.discounts()),
                        s(v.voids()), n(v.voidAmount()), s(v.comps()), n(v.compAmount()))));
            }
            case "tables" -> {
                name = "Table turns";
                head = List.of("Table", "Section", "Seats", "Parties", "Covers", "Turns per day", "Average minutes", "Net sales", "Per cover");
                a.tables().forEach(t -> body.add(row(t.name(), s(t.section()), s(t.seats()), s(t.parties()), s(t.covers()), s(t.turnsPerDay()), s(t.averageMinutes()), n(t.netSales()), n(t.salesPerCover()))));
            }
            case "controls" -> {
                name = "Voids, comps and cancelled orders";
                head = List.of("Type", "Count", "Value");
                a.controls().forEach(c -> body.add(row(RestaurantExportController.pretty(c.type()), s(c.count()), n(c.amount()))));
            }
            default -> throw ApiException.badRequest("Unknown table: " + table);
        }
        return ExportFiles.file(exportService, "Restaurant " + name.toLowerCase() + " (" + when + ")", "restaurant-" + table, head, body, format);
    }

    @GetMapping("/end-of-day/export")
    public ResponseEntity<byte[]> endOfDay(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) BigDecimal openingFloat,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        EndOfDayDto r = service.endOfDay(date, openingFloat);
        List<List<String>> b = new ArrayList<>();
        var sa = r.sales();
        b.add(row("Sales", "Checks", s(sa.checks()), ""));
        b.add(row("Sales", "Covers", s(sa.covers()), ""));
        b.add(row("Sales", "Gross sales", "", n(sa.gross())));
        b.add(row("Sales", "Discounts", "", n(sa.discounts())));
        b.add(row("Sales", "Net sales", "", n(sa.netSales())));
        b.add(row("Sales", "Service charge", "", n(sa.serviceCharge())));
        b.add(row("Sales", "Tax", "", n(sa.tax())));
        b.add(row("Sales", "Rounding", "", n(sa.rounding())));
        b.add(row("Sales", "Total billed", "", n(sa.billed())));
        b.add(row("Sales", "Tips", "", n(sa.tips())));
        r.types().forEach(t -> b.add(row("Order type", RestaurantExportController.pretty(t.type()), s(t.checks()), n(t.netSales()))));
        r.taxes().forEach(t -> b.add(row("Tax", t.label(), n(t.base()), n(t.tax()))));
        r.payments().forEach(p -> b.add(row("Payments", p.method(), s(p.checks()), n(p.amount()))));
        r.tips().forEach(t -> b.add(row("Tips by server", t.server(), s(t.checks()), n(t.tips()))));
        r.deposits().forEach(d -> b.add(row("Deposits", RestaurantCashService.label(d.kind()), s(d.count()), n(d.amount()))));
        r.controls().forEach(c -> b.add(row("Controls", RestaurantExportController.pretty(c.type()), s(c.count()), n(c.amount()))));
        var c = r.cash();
        b.add(row("Cash", "Opening float", "", n(c.openingFloat())));
        b.add(row("Cash", "Cash sales", "", n(c.cashSales())));
        b.add(row("Cash", "Deposits taken in cash", "", n(c.depositsIn())));
        b.add(row("Cash", "Deposits refunded in cash", "", n(c.depositsOut())));
        b.add(row("Cash", "Paid in", "", n(c.paidIn())));
        b.add(row("Cash", "Paid out", "", n(c.paidOut())));
        b.add(row("Cash", "Tips paid out", "", n(c.tipsPaid())));
        b.add(row("Cash", "Expected in drawer", "", n(c.expected())));
        if (r.closed()) {
            b.add(row("Cash", "Counted", "", n(c.counted())));
            b.add(row("Cash", "Variance", "", n(c.variance())));
            b.add(row("Cash", "Left in drawer", "", n(c.floatKept())));
            b.add(row("Cash", "Banked", "", n(c.banked())));
        }
        r.cashEvents().forEach(e -> b.add(row("Cash entries", RestaurantCashService.label(e.kind()) + (e.reason() == null ? "" : ": " + e.reason()), s(e.by()), n(e.amount()))));
        String title = "Restaurant end of day " + r.date() + (r.closed() ? " (Z-" + String.format("%04d", r.zNumber()) + ")" : " (not closed yet)");
        return ExportFiles.file(exportService, title, "restaurant-end-of-day-" + r.date(), List.of("Section", "Line", "Count / base", "Amount"), b, format);
    }

    @GetMapping("/end-of-day/history/export")
    public ResponseEntity<byte[]> history(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (DayCloseRowDto c : service.history(status, from, to)) {
            body.add(row("Z-" + String.format("%04d", c.zNumber()), c.date().toString(), RestaurantExportController.pretty(c.status()), s(c.checks()), n(c.netSales()), n(c.totalBilled()), n(c.tips()),
                    n(c.cashExpected()), n(c.cashCounted()), n(c.variance()), n(c.banked()), s(c.closedBy()), s(c.notes())));
        }
        return ExportFiles.file(exportService, RestaurantExportController.title("Restaurant closed days", RestaurantExportController.pick("status", status), RestaurantExportController.range("for", from, to)),
                "restaurant-closed-days", List.of("Z", "Date", "Status", "Checks", "Net sales", "Billed", "Tips", "Expected cash", "Counted", "Variance", "Banked", "Closed by", "Notes"), body, format);
    }
}
