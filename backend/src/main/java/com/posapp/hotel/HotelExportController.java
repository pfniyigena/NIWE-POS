package com.posapp.hotel;

import com.posapp.common.ExportFiles;
import com.posapp.hotel.dto.HotelDtos.*;
import com.posapp.reports.ReportExportService;
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

/**
 * Excel / PDF / CSV downloads of the Hotel lists. Each takes the filters of its screen and returns every matching row, not one page.
 * The rows come from the same services as the screens, so the filters and the access rule are the screens' own. The paths sit under
 * /api/hotel, so the module licence gate covers them.
 */
@RestController
@RequestMapping("/api/hotel")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HOTEL_VIEW')")
public class HotelExportController {

    private final HotelController hotel;
    private final BookingService bookingService;
    private final GuestService guestService;
    private final FrontDeskService frontDeskService;
    private final RoomService roomService;
    private final HotelReportService reportService;
    private final ReportExportService exportService;

    public HotelExportController(HotelController hotel, BookingService bookingService, GuestService guestService, FrontDeskService frontDeskService,
                                 RoomService roomService, HotelReportService reportService, ReportExportService exportService) {
        this.hotel = hotel;
        this.bookingService = bookingService;
        this.guestService = guestService;
        this.frontDeskService = frontDeskService;
        this.roomService = roomService;
        this.reportService = reportService;
        this.exportService = exportService;
    }

    static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String num(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String title(String name, String... filters) {
        List<String> bits = new ArrayList<>();
        for (String f : filters) {
            if (f != null && !f.isBlank()) bits.add(f);
        }
        return bits.isEmpty() ? name : name + " (" + String.join(", ", bits) + ")";
    }

    static String search(String q) {
        return q == null || q.isBlank() ? null : "search \"" + q.trim() + "\"";
    }

    static String range(String label, LocalDate from, LocalDate to) {
        return from == null && to == null ? null : label + " " + (from == null ? "…" : from.toString()) + " to " + (to == null ? "…" : to.toString());
    }

    private static String pretty(String code) {
        if (code == null) return "";
        String t = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static List<String> bookingHeaders() {
        return List.of("Booking", "Guest", "Phone", "Room", "Room type", "Arrival", "Departure", "Nights", "Adults", "Children", "Rate / night",
                "Status", "Source", "Total", "Paid", "Balance");
    }

    private static List<String> bookingRow(BookingDto b) {
        return List.of(s(b.bookingNumber()), s(b.guestName()), s(b.guestPhone()), s(b.roomNumber()), s(b.roomTypeName()), s(b.checkInDate()),
                s(b.checkOutDate()), s(b.nights()), s(b.adults()), s(b.children()), num(b.nightlyTotal()), pretty(b.status()), s(b.source()),
                num(b.chargesTotal()), num(b.paidTotal()), num(b.balance()));
    }

    @GetMapping("/bookings/export")
    public ResponseEntity<byte[]> bookings(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) String source,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (BookingDto b : bookingService.page(status, q, from, to, roomTypeId, source, 0, 0).getContent()) body.add(bookingRow(b));
        return ExportFiles.file(exportService, title("Bookings", status == null || status.isBlank() ? null : "status " + pretty(status),
                range("arriving", from, to), search(q)), "bookings", bookingHeaders(), body, format);
    }

    @GetMapping("/guests/export")
    public ResponseEntity<byte[]> guests(@RequestParam(required = false) String q, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (GuestDto g : guestService.page(q, 0, 0).getContent()) {
            body.add(List.of(s(g.fullName()), s(g.phone()), s(g.email()), s(g.nationality()), s(g.idType()), s(g.idNumber()), s(g.address()), s(g.stays()), s(g.notes())));
        }
        return ExportFiles.file(exportService, title("Guests", search(q)), "guests",
                List.of("Name", "Phone", "E-mail", "Nationality", "ID type", "ID number", "Address", "Stays", "Notes"), body, format);
    }

    @GetMapping("/rooms/export")
    public ResponseEntity<byte[]> rooms(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) String floor,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        List<RoomDto> rows = hotel.rooms(q, status, roomTypeId, floor, null, null).getBody();
        List<List<String>> body = new ArrayList<>();
        for (RoomDto r : rows == null ? List.<RoomDto>of() : rows) {
            body.add(List.of(s(r.number()), s(r.floor()), s(r.roomTypeName()), num(r.rate()), pretty(r.housekeepingStatus()), s(r.housekeeper()),
                    r.outOfOrder() ? "Out of order" : r.occupied() ? "Occupied" : "Vacant", s(r.occupantGuest()), s(r.occupantCheckOut()),
                    r.active() ? "Yes" : "No", s(r.notes())));
        }
        return ExportFiles.file(exportService, title("Rooms", status == null || status.isBlank() ? null : "status " + pretty(status), search(q)), "rooms",
                List.of("Room", "Floor", "Room type", "Rate / night", "Housekeeping", "Housekeeper", "State", "Guest", "Leaves", "Active", "Notes"), body, format);
    }

    @GetMapping("/room-types/export")
    public ResponseEntity<byte[]> roomTypes(@RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (RoomTypeDto t : roomService.listTypes()) {
            body.add(List.of(s(t.name()), num(t.baseRate()), s(t.baseOccupancy()), s(t.maxOccupancy()), num(t.extraPersonRate()), s(t.roomCount()),
                    t.active() ? "Yes" : "No", s(t.amenities()), s(t.description())));
        }
        return ExportFiles.file(exportService, "Room types", "room-types",
                List.of("Name", "Rate / night", "Base guests", "Max guests", "Extra guest rate", "Rooms", "Active", "Amenities", "Description"), body, format);
    }

    @GetMapping("/maintenance/export")
    public ResponseEntity<byte[]> maintenance(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String priority, @RequestParam(required = false) Long roomId,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        List<MaintenanceDto> rows = hotel.maintenance(q, status, priority, roomId, null, null).getBody();
        List<List<String>> body = new ArrayList<>();
        for (MaintenanceDto m : rows == null ? List.<MaintenanceDto>of() : rows) {
            body.add(List.of(s(m.roomNumber()), s(m.title()), s(m.description()), pretty(m.priority()), pretty(m.status()), m.blocksRoom() ? "Yes" : "No",
                    s(m.reportedBy()), s(m.createdAt()), s(m.resolvedAt()), s(m.resolutionNote())));
        }
        return ExportFiles.file(exportService, title("Maintenance", status == null || status.isBlank() ? null : "status " + pretty(status), search(q)), "maintenance",
                List.of("Room", "Fault", "Details", "Priority", "Status", "Blocks room", "Reported by", "Reported", "Resolved", "Resolution"), body, format);
    }

    @GetMapping("/front-desk/{list}/export")
    public ResponseEntity<byte[]> frontDesk(@org.springframework.web.bind.annotation.PathVariable String list, @RequestParam(defaultValue = "xlsx") String format) {
        List<BookingDto> rows;
        String name;
        switch (list) {
            case "arrivals" -> { rows = frontDeskService.arrivals(); name = "Arrivals"; }
            case "departures" -> { rows = frontDeskService.departures(); name = "Departures"; }
            case "in-house" -> { rows = frontDeskService.inHouse(); name = "In-house guests"; }
            default -> throw com.posapp.common.ApiException.notFound("Unknown list: " + list);
        }
        List<List<String>> body = new ArrayList<>();
        for (BookingDto b : rows) body.add(bookingRow(b));
        return ExportFiles.file(exportService, name, list, bookingHeaders(), body, format);
    }

    @GetMapping("/reports/occupancy/export")
    public ResponseEntity<byte[]> occupancy(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "xlsx") String format) {
        OccupancyReportDto r = reportService.occupancy(from, to);
        List<List<String>> body = new ArrayList<>();
        for (RevenueByType t : r.byType()) body.add(List.of(s(t.roomTypeName()), s(t.roomNights()), num(t.revenue())));
        body.add(List.of("Total (" + r.roomCount() + " rooms, " + r.roomNightsAvailable() + " nights available)", s(r.roomNightsSold()), num(r.roomRevenue())));
        body.add(List.of("Occupancy %", num(r.occupancyPercent()), ""));
        body.add(List.of("ADR (average daily rate)", num(r.adr()), ""));
        body.add(List.of("RevPAR (revenue per available room)", num(r.revPar()), ""));
        return ExportFiles.file(exportService, "Occupancy " + from + " to " + to, "occupancy", List.of("Room type", "Room nights sold", "Room revenue"), body, format);
    }
}
