package com.posapp.order;

import com.posapp.common.ExportFiles;
import com.posapp.order.dto.OrderDto;
import com.posapp.register.CashControlService;
import com.posapp.register.PosExceptionService;
import com.posapp.register.RegisterService;
import com.posapp.register.dto.PosExceptionDto;
import com.posapp.register.dto.CashMovementDto;
import com.posapp.register.dto.SessionDto;
import com.posapp.reports.ReportExportService;
import com.posapp.returns.ReturnsService;
import com.posapp.returns.dto.SalesReturnDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Excel / PDF / CSV downloads of the POS lists (sales, shifts, returns). Each takes the filters of its screen and returns every matching row, not one page.
 * Reading the lists on screen is open to every signed-in user (a cashier looks a receipt up), but a file of the day's takings is for the Sales area.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_SALES_MANAGE')")
public class PosExportController {

    private final OrderService orderService;
    private final RegisterService registerService;
    private final ReturnsService returnsService;
    private final ReportExportService exportService;
    private final CashControlService cashControlService;
    private final PosExceptionService exceptionService;

    public PosExportController(OrderService orderService, RegisterService registerService, ReturnsService returnsService, ReportExportService exportService, CashControlService cashControlService, PosExceptionService exceptionService) {
        this.exceptionService = exceptionService;
        this.cashControlService = cashControlService;
        this.orderService = orderService;
        this.registerService = registerService;
        this.returnsService = returnsService;
        this.exportService = exportService;
    }

    static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String num(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String when(Instant t) {
        return t == null ? "" : t.atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T', ' ').substring(0, 16);
    }

    static String title(String name, String... filters) {
        List<String> bits = new ArrayList<>();
        for (String f : filters) {
            if (f != null && !f.isBlank()) bits.add(f);
        }
        return bits.isEmpty() ? name : name + " (" + String.join(", ", bits) + ")";
    }

    static String range(String label, LocalDate from, LocalDate to) {
        return from == null && to == null ? null : label + " " + (from == null ? "…" : from.toString()) + " to " + (to == null ? "…" : to.toString());
    }

    static String search(String q) {
        return q == null || q.isBlank() ? null : "search \"" + q.trim() + "\"";
    }

    static String pick(String label, String v) {
        return v == null || v.isBlank() ? null : label + " " + v.trim();
    }

    @GetMapping("/orders/completed/export")
    public ResponseEntity<byte[]> sales(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) String method,
                                        @RequestParam(required = false) Long registerId, @RequestParam(required = false) Long cashierId, @RequestParam(required = false) Long customerId,
                                        @RequestParam(required = false) Long sessionId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (OrderDto o : orderService.completedFiltered(q, status, method, registerId, cashierId, customerId, sessionId, from, to)) {
            body.add(List.of(when(o.completedAt()), s(o.invoiceNumber()), s(o.customerName()), s(o.cashierName()), s(o.registerName()), s(o.totalQuantity()),
                    num(o.subtotal()), num(o.taxTotal()), num(o.total()), num(o.promoSavings()),
                    o.payments().stream().map(p -> p.method()).distinct().collect(Collectors.joining(" + ")),
                    o.offlineQueuedAt() != null ? "Offline" : "", s(o.syncNote()), s(o.efrisError())));
        }
        return ExportFiles.file(exportService, title("POS sales", pick("filter", status), pick("paid by", method), range("completed", from, to), search(q)), "pos-sales",
                List.of("Completed", "Invoice", "Customer", "Cashier", "Register", "Items", "Subtotal", "Tax", "Total", "Promo savings", "Payment", "Offline", "Sync note", "EFRIS error"), body, format);
    }

    @GetMapping("/sessions/export")
    public ResponseEntity<byte[]> shifts(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long registerId,
                                         @RequestParam(required = false) Long cashierId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (SessionDto d : registerService.filteredSessions(q, status, registerId, cashierId, from, to)) {
            body.add(List.of(when(d.openedAt()), when(d.closedAt()), s(d.registerName()), s(d.zNumber()), s(d.cashierName()), s(d.status()), num(d.openingFloat()), s(d.saleCount()),
                    num(d.grossSales()), num(d.expectedCash()), num(d.countedCash()), num(d.cashVariance()), s(d.varianceNote())));
        }
        return ExportFiles.file(exportService, title("POS shifts", pick("status", status), range("opened", from, to), search(q)), "pos-shifts",
                List.of("Opened", "Closed", "Register", "Z no.", "Cashier", "Status", "Opening float", "Sales", "Gross sales", "Expected cash", "Counted cash", "Variance", "Note"), body, format);
    }

    @GetMapping("/returns/export")
    public ResponseEntity<byte[]> returns(@RequestParam(required = false) String q, @RequestParam(required = false) String method, @RequestParam(required = false) Long registerId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (SalesReturnDto r : returnsService.filteredReturns(q, method, registerId, from, to)) {
            body.add(List.of(when(r.returnDate()), s(r.returnNumber()), s(r.invoiceNumber()), s(r.customerName()), num(r.refundTotal()), s(r.refundMethod()), s(r.reason())));
        }
        return ExportFiles.file(exportService, title("POS returns", pick("refunded by", method), range("returned", from, to), search(q)), "pos-returns",
                List.of("Returned", "Return no.", "Invoice", "Customer", "Refund", "Method", "Reason"), body, format);
    }

    @GetMapping("/cash-movements/export")
    public ResponseEntity<byte[]> cash(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long registerId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (CashMovementDto m : cashControlService.filtered(q, status, registerId, from, to)) {
            String kind = switch (m.kind()) { case "PAID_IN" -> "Paid in"; case "PAID_OUT" -> "Paid out"; case "DROP" -> "Drop"; default -> "Count"; };
            body.add(List.of(when(m.createdAt()), s(m.registerName()), kind, s(m.reason()), num(m.amount()), num(m.expectedCash()), num(m.variance()), s(m.note()), s(m.byUser()),
                    s(m.approvedBy()), m.voided() ? "Voided: " + s(m.voidReason()) : ""));
        }
        return ExportFiles.file(exportService, title("Cash movements", pick("filter", status), range("moved", from, to), search(q)), "pos-cash-movements",
                List.of("When", "Register", "Kind", "Reason", "Amount", "Expected (counts)", "Difference (counts)", "Note", "By", "Approved by", "Voided"), body, format);
    }

    @GetMapping("/pos/exceptions/export")
    public ResponseEntity<byte[]> exceptions(@RequestParam(required = false) String q, @RequestParam(required = false) String type, @RequestParam(required = false) Long registerId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (PosExceptionDto e : exceptionService.filtered(q, type, registerId, from, to)) {
            String kind = switch (e.type()) { case "VOID" -> "Voided sale"; case "RETURN" -> "Return"; case "DISCOUNT" -> "Discount"; case "PRICE_OVERRIDE" -> "Price changed"; case "REPRINT" -> "Receipt reprint"; default -> "Dropped cart"; };
            body.add(List.of(when(e.at()), kind, s(e.ref()), s(e.customer()), s(e.registerName()), s(e.by()), num(e.amount()), s(e.reason()), s(e.approvedBy())));
        }
        return ExportFiles.file(exportService, title("Voids and returns", pick("type", type), range("on", from, to), search(q)), "pos-voids-returns",
                List.of("When", "Type", "Reference", "Customer", "Register", "By", "Amount", "Reason", "Approved by"), body, format);
    }
}
