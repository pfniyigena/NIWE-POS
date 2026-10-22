package com.posapp.reports;

import com.posapp.common.ExportFiles;
import com.posapp.reports.dto.PosOpsRows.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** The POS operations reports: the same date range, register and branch filters and Excel / PDF / CSV downloads as the other POS reports. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class PosOpsReportController {

    private final PosOpsReportService service;
    private final ReportExportService exportService;

    public PosOpsReportController(PosOpsReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    private static Instant start(LocalDate from) {
        return (from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30)).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant end(LocalDate to) {
        return (to != null ? to : LocalDate.now(ZoneOffset.UTC)).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }

    private static boolean download(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format) || "csv".equalsIgnoreCase(format);
    }

    private static String m(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private ResponseEntity<?> out(String title, String base, List<String> headers, List<List<String>> body, String format) {
        return ExportFiles.file(exportService, title, base, headers, body, format);
    }

    @GetMapping("/pos-by-cashier")
    public ResponseEntity<?> byCashier(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long registerId,
                                       @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        List<CashierRow> rows = service.byCashier(start(from), end(to), registerId, branchId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (CashierRow r : rows) {
            body.add(List.of(r.cashierName(), s(r.shifts()), s(r.saleCount()), s(r.itemCount()), m(r.grandTotal()), m(r.discounts()), s(r.refundCount()), m(r.refundTotal()), s(r.voidCount()), m(r.netSales()), m(r.averageSale())));
        }
        return out("POS Sales by Cashier", "pos-sales-by-cashier", List.of("Cashier", "Shifts", "Sales", "Items", "Sales total", "Discounts", "Returns", "Refunded", "Voided sales", "Net sales", "Average sale"), body, format);
    }

    @GetMapping("/pos-by-hour")
    public ResponseEntity<?> byHour(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long registerId,
                                    @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        List<HourRow> rows = service.byHour(start(from), end(to), registerId, branchId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (HourRow r : rows) body.add(List.of(r.hour(), s(r.saleCount()), s(r.itemCount()), m(r.grandTotal()), m(r.averageSale()), m(r.sharePercent()) + "%"));
        return out("POS Sales by Hour", "pos-sales-by-hour", List.of("Hour", "Sales", "Items", "Sales total", "Average sale", "Share of sales"), body, format);
    }

    @GetMapping("/pos-by-payment")
    public ResponseEntity<?> byPayment(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long registerId,
                                       @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        List<PaymentRow> rows = service.byPayment(start(from), end(to), registerId, branchId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (PaymentRow r : rows) body.add(List.of(r.method(), s(r.paymentCount()), m(r.amount()), s(r.refundCount()), m(r.refundAmount()), m(r.netAmount()), m(r.sharePercent()) + "%"));
        return out("POS Sales by Payment Method", "pos-sales-by-payment", List.of("Payment method", "Payments", "Received", "Refunds", "Refunded", "Net", "Share of takings"), body, format);
    }

    @GetMapping("/pos-exceptions")
    public ResponseEntity<?> exceptions(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long registerId,
                                        @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        LocalDate f = from != null ? from : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        List<ExceptionRow> rows = service.exceptions(f, t, registerId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (ExceptionRow r : rows) {
            body.add(List.of(r.at() == null ? "" : r.at().atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T', ' ').substring(0, 16), r.type(), s(r.reference()), s(r.customer()),
                    s(r.registerName()), s(r.by()), m(r.amount()), s(r.reason()), s(r.approvedBy())));
        }
        return out("POS Exceptions", "pos-exceptions", List.of("When", "Type", "Reference", "Customer", "Register", "By", "Amount", "Reason", "Approved by"), body, format);
    }

    @GetMapping("/pos-shift-comparison")
    public ResponseEntity<?> shiftComparison(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long registerId,
                                             @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "json") String format) {
        List<ShiftCompareRow> rows = service.shiftComparison(start(from), end(to), registerId, branchId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (ShiftCompareRow r : rows) {
            body.add(List.of("#" + r.shiftId(), r.zNumber() == null ? "" : "Z" + r.zNumber(), r.registerName(), r.cashierName(), s(r.openedAt()), s(r.closedAt()), s(r.saleCount()), m(r.grandTotal()), m(r.averageSale()),
                    m(r.itemsPerSale()), m(r.discounts()), m(r.refundTotal()), s(r.voidCount()), m(r.expectedCash()), m(r.countedCash()), m(r.variance()), r.salesVsAveragePercent() == null ? "" : m(r.salesVsAveragePercent()) + "%"));
        }
        return out("POS Shift Comparison", "pos-shift-comparison", List.of("Shift", "Z no.", "Register", "Cashier", "Opened", "Closed", "Sales", "Sales total", "Average sale", "Items per sale", "Discounts", "Refunds", "Voided", "Expected cash",
                "Counted cash", "Cash difference", "Versus average shift"), body, format);
    }
}
