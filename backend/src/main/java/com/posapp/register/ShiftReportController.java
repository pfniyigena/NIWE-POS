package com.posapp.register;

import com.posapp.common.ExportFiles;
import com.posapp.register.dto.ShiftReportDto;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** X report (the shift so far) and Z report (the closed shift, numbered and frozen), to read, print or download. */
@RestController
@RequestMapping("/api/sessions")
public class ShiftReportController {

    private final ShiftReportService service;
    private final ReportExportService exportService;

    public ShiftReportController(ShiftReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping("/{id}/x-report")
    public ShiftReportDto x(@PathVariable Long id) {
        return service.xReport(id);
    }

    @GetMapping("/{id}/z-report")
    public ShiftReportDto z(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean print) {
        return service.zReport(id, print);
    }

    /** Excel, PDF or CSV of the X report (open shift) or Z report (closed shift). */
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_SALES_VIEW') or hasAuthority('AREA_SALES_MANAGE')")
    @GetMapping("/{id}/shift-report/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format) {
        ShiftReportDto r = "X".equals(service.reportType(id)) ? service.xReport(id) : service.zReport(id, false);
        List<List<String>> rows = new ArrayList<>();
        String head = r.type().equals("Z") ? "Z report " + (r.zNumber() == null ? "" : "no. " + r.zNumber()) : "X report";
        rows.add(List.of("Shift", "Register", r.registerName(), ""));
        rows.add(List.of("Shift", "Cashier", r.cashierName(), ""));
        rows.add(List.of("Shift", "Opened", when(r.openedAt()), ""));
        if (r.closedAt() != null) rows.add(List.of("Shift", "Closed", when(r.closedAt()), ""));
        var s = r.sales();
        rows.add(List.of("Sales", "Number of sales", String.valueOf(s.count()), ""));
        rows.add(List.of("Sales", "Items sold", String.valueOf(s.items()), ""));
        rows.add(List.of("Sales", "Sales at shelf price", "", n(s.shelfValue())));
        rows.add(List.of("Sales", "Discounts and promotions", "", n(s.discounts())));
        rows.add(List.of("Sales", "Net sales (before tax)", "", n(s.netSales())));
        rows.add(List.of("Sales", "Tax", "", n(s.tax())));
        rows.add(List.of("Sales", "Total sales", "", n(s.totalSales())));
        rows.add(List.of("Sales", "Average sale", "", n(s.averageSale())));
        for (var p : r.payments()) rows.add(List.of("Payments", p.name() + (p.cash() ? " (cash)" : ""), String.valueOf(p.count()), n(p.amount())));
        for (var t : r.taxes()) rows.add(List.of("Tax", t.code() + (t.ratePercent() == null ? "" : " " + t.ratePercent().stripTrailingZeros().toPlainString() + "%"), "net " + n(t.net()), n(t.tax())));
        rows.add(List.of("Refunds", "Returns made", String.valueOf(r.refunds().count()), n(r.refunds().total())));
        for (var p : r.refunds().byMethod()) rows.add(List.of("Refunds", p.name(), String.valueOf(p.count()), n(p.amount())));
        var d = r.drawer();
        rows.add(List.of("Drawer", "Opening float", "", n(d.openingFloat())));
        rows.add(List.of("Drawer", "Cash sales", "", n(d.cashSales())));
        rows.add(List.of("Drawer", "Cash refunds", "", n(d.cashRefunds())));
        rows.add(List.of("Drawer", "Paid in", "", n(d.paidIn())));
        rows.add(List.of("Drawer", "Paid out", "", n(d.paidOut())));
        rows.add(List.of("Drawer", "Dropped into the safe", "", n(d.drops())));
        rows.add(List.of("Drawer", "Expected cash", "", n(d.expectedCash())));
        if (d.countedCash() != null) rows.add(List.of("Drawer", "Counted cash", "", n(d.countedCash())));
        if (d.variance() != null) rows.add(List.of("Drawer", "Difference", "", n(d.variance())));
        var c = r.controls();
        rows.add(List.of("Controls", "Sales with a discount", String.valueOf(c.discountedSales()), ""));
        rows.add(List.of("Controls", "Carts dropped with items", String.valueOf(c.abandonedCarts()), n(c.abandonedValue())));
        rows.add(List.of("Controls", "Sales rung offline", String.valueOf(c.offlineSales()), ""));
        rows.add(List.of("Controls", "Sold on credit", "", n(c.creditSales())));
        rows.add(List.of("Controls", "Charged to rooms", "", n(c.roomCharges())));
        rows.add(List.of("Controls", "Gift cards sold", "", n(c.giftCardsSold())));
        for (var k : r.cashiers()) rows.add(List.of("Cashiers", k.name(), String.valueOf(k.sales()), n(k.total())));
        return ExportFiles.file(exportService, head + " — " + r.registerName(), r.type().equals("Z") ? "z-report" : "x-report", List.of("Section", "Item", "Count", "Amount"), rows, format);
    }

    private static String n(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String when(java.time.Instant t) {
        return t == null ? "" : t.atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString().replace('T', ' ').substring(0, 16);
    }
}
