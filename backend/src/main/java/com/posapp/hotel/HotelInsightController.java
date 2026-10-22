package com.posapp.hotel;

import com.posapp.common.ExportFiles;
import com.posapp.hotel.dto.ReportDtos.*;
import com.posapp.reports.ReportExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.posapp.hotel.HotelExportController.num;
import static com.posapp.hotel.HotelExportController.s;

/** The manager's reports: the day report, revenue by source, forecast and pick-up, and the night-audit report, each with its download. */
@RestController
@RequestMapping("/api/hotel/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HotelInsightController {

    private static final List<String> SECTION_HEADERS = List.of("Section", "Item", "Amount", "Note");

    private final HotelInsightService service;
    private final ReportExportService exportService;

    public HotelInsightController(HotelInsightService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    // ---- day report ----

    @GetMapping("/daily")
    public DailyReportDto daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date);
    }

    @GetMapping("/daily/export")
    public ResponseEntity<byte[]> dailyExport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        DailyReportDto r = service.daily(date);
        List<List<String>> b = new ArrayList<>();
        b.add(List.of("Rooms", "Rooms in the hotel", s(r.totalRooms()), ""));
        b.add(List.of("Rooms", "Out of order", s(r.outOfOrder()), ""));
        b.add(List.of("Rooms", "Rooms occupied", s(r.roomsOccupied()), "of " + r.sellable() + " sellable"));
        b.add(List.of("Rooms", "Occupancy %", num(r.occupancyPercent()), ""));
        b.add(List.of("Rooms", "Arrivals", s(r.arrivals()), ""));
        b.add(List.of("Rooms", "Departures", s(r.departures()), ""));
        b.add(List.of("Rooms", "Stay-overs", s(r.stayOvers()), ""));
        b.add(List.of("Rooms", "Arrivals tomorrow", s(r.arrivalsTomorrow()), ""));
        b.add(List.of("Rooms", "Departures tomorrow", s(r.departuresTomorrow()), ""));
        b.add(List.of("Rooms", "In house now", s(r.inHouseNow()), "balance " + num(r.inHouseBalance())));
        b.add(List.of("Rate", "Room revenue", num(r.roomRevenue()), ""));
        b.add(List.of("Rate", "ADR", num(r.adr()), ""));
        b.add(List.of("Rate", "RevPAR", num(r.revPar()), ""));
        if (r.expectedRoomRevenue() != null) b.add(List.of("Rate", "Expected room revenue (in house)", num(r.expectedRoomRevenue()), ""));
        for (RevenueLineDto l : r.revenue()) b.add(List.of("Revenue earned", l.type(), num(l.net()), l.lines() + " lines, tax " + num(l.tax())));
        b.add(List.of("Revenue earned", "Total (before tax)", num(r.revenueTotal()), ""));
        for (MoneyLineDto m : r.money()) b.add(List.of("Money taken", m.method(), num(m.net()),
                "deposits " + num(m.deposits()) + ", payments " + num(m.payments()) + ", refunds " + num(m.refunds())));
        b.add(List.of("Money taken", "Total", num(r.moneyTotal()), ""));
        for (PeriodDto p : List.of(r.monthToDate(), r.yearToDate())) {
            b.add(List.of(p.label(), "Occupancy %", num(p.occupancyPercent()), p.roomNightsSold() + " of " + p.roomNightsAvailable() + " room nights"));
            b.add(List.of(p.label(), "Room revenue", num(p.roomRevenue()), "ADR " + num(p.adr()) + ", RevPAR " + num(p.revPar())));
        }
        return ExportFiles.file(exportService, "Day report " + r.date(), "day-report", SECTION_HEADERS, b, format);
    }

    // ---- by source ----

    @GetMapping("/sources")
    public SourceReportDto sources(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.sources(from, to);
    }

    @GetMapping("/sources/export")
    public ResponseEntity<byte[]> sourcesExport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(defaultValue = "xlsx") String format) {
        SourceReportDto r = service.sources(from, to);
        List<List<String>> b = new ArrayList<>();
        for (SourceRowDto x : r.rows()) b.add(sourceRow(x));
        b.add(sourceRow(r.total()));
        return ExportFiles.file(exportService, "Revenue by source " + from + " to " + to, "revenue-by-source",
                List.of("Source", "Bookings", "Stays", "Cancelled", "No-shows", "Room nights", "Room revenue", "Other revenue", "ADR", "Average stay", "Average lead (days)", "Lost %"),
                b, format);
    }

    private static List<String> sourceRow(SourceRowDto x) {
        return List.of(s(x.source()), s(x.bookings()), s(x.stays()), s(x.cancelled()), s(x.noShows()), s(x.roomNights()), num(x.roomRevenue()),
                num(x.otherRevenue()), num(x.adr()), num(x.averageStay()), num(x.averageLeadDays()), num(x.cancelPercent()));
    }

    // ---- forecast ----

    @GetMapping("/forecast")
    public ForecastDto forecast(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) Integer days, @RequestParam(required = false) Integer pickupDays) {
        return service.forecast(from, days, pickupDays);
    }

    @GetMapping("/forecast/export")
    public ResponseEntity<byte[]> forecastExport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) Integer days, @RequestParam(required = false) Integer pickupDays,
                                                 @RequestParam(defaultValue = "xlsx") String format) {
        ForecastDto r = service.forecast(from, days, pickupDays);
        List<List<String>> b = new ArrayList<>();
        for (ForecastDayDto d : r.days()) {
            b.add(List.of(s(d.date()), s(d.weekday()), s(d.capacity()), s(d.booked()), s(d.held()), num(d.occupancyPercent()), num(d.revenue()),
                    s(d.arrivals()), s(d.departures()), s(d.pickup()), num(d.pickupRevenue())));
        }
        ForecastTotalsDto t = r.totals();
        b.add(List.of("Total", "", s(t.capacityNights()), s(t.roomNightsBooked()), "", num(t.occupancyPercent()), num(t.revenue()), "", "", s(t.pickup()), num(t.pickupRevenue())));
        return ExportFiles.file(exportService, "Forecast " + r.from() + " to " + r.to() + " (pick-up " + r.pickupDays() + " days)", "forecast",
                List.of("Date", "Day", "Rooms", "Booked", "Held", "Occupancy %", "Revenue", "Arrivals", "Departures", "Pick-up nights", "Pick-up revenue"), b, format);
    }

    // ---- night audit ----

    @GetMapping("/night-audit")
    public NightAuditReportDto nightAudit(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.nightAudit(date);
    }

    @GetMapping("/night-audit/export")
    public ResponseEntity<byte[]> nightAuditExport(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                   @RequestParam(defaultValue = "xlsx") String format) {
        NightAuditReportDto r = service.nightAudit(date);
        List<List<String>> b = new ArrayList<>();
        b.add(List.of("Summary", "Night audit run for this day", r.auditRanForDate() ? "Yes" : "No", r.lastAuditRun() == null ? "never run" : "last run " + r.lastAuditRun()));
        b.add(List.of("Summary", "Posting mode", r.postingMode(), ""));
        b.add(List.of("Summary", "In house", s(r.inHouseStays()), ""));
        b.add(List.of("Summary", "Arrivals", s(r.arrivals()), ""));
        b.add(List.of("Summary", "Departures", s(r.departures()), ""));
        b.add(List.of("Summary", "Room nights charged", s(r.roomNightsCharged()), "room revenue " + num(r.roomRevenue())));
        for (RevenueLineDto l : r.charged()) b.add(List.of("Charged", l.type(), num(l.net()), l.lines() + " lines, tax " + num(l.tax())));
        b.add(List.of("Charged", "Total (before tax)", num(r.chargedTotal()), ""));
        for (MoneyLineDto m : r.money()) b.add(List.of("Money taken", m.method(), num(m.net()), "deposits " + num(m.deposits()) + ", payments " + num(m.payments()) + ", refunds " + num(m.refunds())));
        b.add(List.of("Money taken", "Total", num(r.moneyTotal()), ""));
        for (AccountMovementDto a : r.postings()) b.add(List.of("Posted to Accounting", a.account(), "", "debit " + num(a.debit()) + ", credit " + num(a.credit())));
        for (AuditCheckDto c : r.checks()) {
            b.add(List.of("Check: " + c.label(), c.ok() ? "OK" : "DIFFERENCE", num(c.difference()), "hotel " + num(c.hotel()) + ", accounting " + num(c.accounting())));
        }
        b.add(List.of("Result", r.balanced() ? "Hotel and Accounting agree" : "Hotel and Accounting differ", "", ""));
        return ExportFiles.file(exportService, "Night audit " + r.date(), "night-audit", SECTION_HEADERS, b, format);
    }
}
