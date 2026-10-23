package com.posapp.restaurant;
import com.posapp.common.Qty;

import com.posapp.common.ExportFiles;
import com.posapp.reports.ReportExportService;
import com.posapp.restaurant.dto.*;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Excel / PDF / CSV downloads of the Restaurant lists. Each takes the filters of its screen and returns every matching row, not one
 * page. The rows come from the same services as the screens. Reading the lists is open to every signed-in user, but a file of the
 * whole day's takings is for the Operations area, so these need the Operations view right (or Admin). The paths sit under
 * /api/restaurant, so the module licence gate covers them.
 */
@RestController
@RequestMapping("/api/restaurant")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class RestaurantExportController {

    private final RestaurantOrderService orderService;
    private final ReservationService reservationService;
    private final RestaurantWastageService wastageService;
    private final MenuService menuService;
    private final DiningTableService tableService;
    private final ReportExportService exportService;
    private final RestaurantVoidService voidService;

    public RestaurantExportController(RestaurantOrderService orderService, ReservationService reservationService, RestaurantWastageService wastageService,
                                      MenuService menuService, DiningTableService tableService, ReportExportService exportService,
                                      RestaurantVoidService voidService) {
        this.voidService = voidService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.wastageService = wastageService;
        this.menuService = menuService;
        this.tableService = tableService;
        this.exportService = exportService;
    }

    static String s(Object o) {
        return o == null ? "" : o instanceof BigDecimal b ? Qty.str(b) : String.valueOf(o);
    }

    static String num(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String when(java.time.Instant t) {
        return t == null ? "" : t.atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T', ' ').substring(0, 16);
    }

    static String pretty(String code) {
        if (code == null || code.isEmpty()) return "";
        String t = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
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

    static String pick(String label, String v) {
        return v == null || v.isBlank() ? null : label + " " + pretty(v.trim());
    }

    @GetMapping("/orders/closed/export")
    public ResponseEntity<byte[]> orders(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String type, @RequestParam(required = false) String method,
                                         @RequestParam(required = false) Long serverId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (RestaurantOrderDto o : orderService.closedFiltered(q, status, type, method, serverId, from, to, false)) {
            body.add(List.of(when(o.closedAt()), s(o.ticketNumber()), pretty(o.orderType()), s(o.displayLabel()), s(o.serverName()), s(o.guestCount()),
                    s(o.lines().stream().map(l -> l.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add)), num(o.subtotal()), num(o.taxTotal()), num(o.total()), num(o.paidTotal()),
                    o.payments().stream().map(p -> p.method()).distinct().collect(Collectors.joining(" + ")), pretty(o.status())));
        }
        return ExportFiles.file(exportService, title("Restaurant receipts", pick("status", status), pick("type", type), method == null || method.isBlank() ? null : "paid by " + method,
                range("closed", from, to), search(q)), "restaurant-receipts",
                List.of("Closed", "Ticket", "Type", "Table / customer", "Server", "Guests", "Items", "Subtotal", "Tax", "Total", "Paid", "Payment", "Status"), body, format);
    }

    @GetMapping("/voids/export")
    public ResponseEntity<byte[]> voids(@RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String user,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (RestaurantVoidLogDto v : voidService.filtered(q, type != null && !type.isBlank() ? type : status, user, from, to)) {
            body.add(List.of(when(v.createdAt()), pretty(v.type()), s(v.orderLabel()), s(v.description()), s(v.quantity()), num(v.amount()), s(v.reason()), s(v.byUser()), s(v.approvedBy())));
        }
        return ExportFiles.file(exportService, title("Restaurant voids and comps", pick("type", type != null && !type.isBlank() ? type : status), range("on", from, to), search(q)), "restaurant-voids",
                List.of("When", "Type", "Table / customer", "Item", "Qty", "Value", "Reason", "By", "Approved by"), body, format);
    }

    @GetMapping("/wastage/export")
    public ResponseEntity<byte[]> wastage(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String reason,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (RestaurantWastageDto w : wastageService.filtered(q, status, reason, from, to)) {
            body.add(List.of(when(w.createdAt()), s(w.productName()), s(w.quantity()), pretty(w.reason()), pretty(w.status()), num(w.costImpact()), s(w.note())));
        }
        return ExportFiles.file(exportService, title("Restaurant wastage", pick("status", status), pick("reason", reason), range("logged", from, to), search(q)), "restaurant-wastage",
                List.of("Logged", "Item", "Quantity", "Reason", "Status", "Cost", "Note"), body, format);
    }

    @GetMapping("/reservations/export")
    public ResponseEntity<byte[]> reservations(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (ReservationDto r : reservationService.filtered(q, status, from, to)) {
            body.add(List.of(when(r.reservedFor()), s(r.guestName()), s(r.guestPhone()), s(r.partySize()), s(r.tableName()), pretty(r.status()), s(r.confirmationCode()), s(r.notes()), s(r.durationMinutes()), pretty(r.depositStatus()), s(r.depositAmount()), s(r.guestNoShows())));
        }
        return ExportFiles.file(exportService, title("Reservations", pick("status", status), range("for", from, to), search(q)), "reservations",
                List.of("When", "Guest", "Phone", "Party", "Table", "Status", "Code", "Notes", "Minutes", "Deposit", "Deposit amount", "Earlier no-shows"), body, format);
    }

    @GetMapping("/menu/items/export")
    public ResponseEntity<byte[]> menuItems(@RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (MenuItemDto i : menuService.listItems(categoryId)) {
            body.add(List.of(s(i.categoryName()), i.product() != null ? s(i.product().name()) : "", num(i.effectivePrice()), s(i.station()), i.hasRecipe() ? "Yes" : "No", i.active() ? "Yes" : "No"));
        }
        return ExportFiles.file(exportService, "Restaurant menu", "restaurant-menu", List.of("Category", "Item", "Price", "Station", "Recipe", "Active"), body, format);
    }

    @GetMapping("/tables/export")
    public ResponseEntity<byte[]> tables(@RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (DiningTableDto t : tableService.listAll()) {
            body.add(List.of(s(t.name()), s(t.section()), s(t.capacity()), pretty(t.status()), t.active() ? "Yes" : "No", t.openOrderId() != null ? "Yes" : "No"));
        }
        return ExportFiles.file(exportService, "Restaurant tables", "restaurant-tables", List.of("Table", "Section", "Seats", "Status", "Active", "Open order"), body, format);
    }
}
