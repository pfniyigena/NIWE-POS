package com.posapp.reports;

import com.posapp.common.ExportFiles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** MF-5: Manufacturing reports with the usual date range and Excel / PDF / CSV downloads; each can be limited to one finished product. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class ManufacturingReportController {

    private final ManufacturingReportService service;
    private final ReportExportService exportService;

    public ManufacturingReportController(ManufacturingReportService service, ReportExportService exportService) {
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
        return v == null ? "—" : v.toPlainString();
    }

    private static String s(Object o) {
        return o == null ? "—" : String.valueOf(o);
    }

    private static String pct(BigDecimal v) {
        return v == null ? "—" : v.toPlainString() + "%";
    }

    @GetMapping("/manufacturing-runs")
    public ResponseEntity<?> runs(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                  @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.productionRuns(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) {
            body.add(List.of(s(r.date()), r.orderNumber(), r.productName(), s(r.quantityProduced()), s(r.quantityScrapped()), pct(r.yieldPercent()), m(r.materialCost()), m(r.labourCost()),
                    m(r.machineCost()), m(r.overheadCost()), m(r.byProductCredit()), m(r.totalCost()), m(r.unitCost())));
        }
        return ExportFiles.file(exportService, "Production Runs", "production-runs", List.of("Date", "Order", "Product", "Good units", "Scrapped", "Yield", "Material", "Labour", "Machine",
                "Overhead", "By-product credit", "Total cost", "Unit cost"), body, format);
    }

    @GetMapping("/manufacturing-cost-per-product")
    public ResponseEntity<?> costPerProduct(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.costPerProduct(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) {
            body.add(List.of(r.productName(), s(r.runs()), s(r.quantityProduced()), s(r.quantityScrapped()), pct(r.yieldPercent()), m(r.materialCost()), m(r.labourCost()), m(r.machineCost()),
                    m(r.overheadCost()), m(r.byProductCredit()), m(r.totalCost()), m(r.unitCost())));
        }
        return ExportFiles.file(exportService, "Manufacturing Cost per Product", "cost-per-product", List.of("Product", "Runs", "Good units", "Scrapped", "Yield", "Material", "Labour", "Machine",
                "Overhead", "By-product credit", "Total cost", "Unit cost"), body, format);
    }

    @GetMapping("/manufacturing-component-use")
    public ResponseEntity<?> componentUse(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.componentUse(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) {
            body.add(List.of(r.componentName(), s(r.runs()), s(r.plannedQuantity()), s(r.usedQuantity()), s(r.varianceQuantity()), pct(r.variancePercent()), m(r.totalCost()), m(r.varianceCost())));
        }
        return ExportFiles.file(exportService, "Component Use against Recipe", "component-use", List.of("Component", "Runs", "Recipe quantity", "Used", "Difference", "Difference %", "Cost of use",
                "Cost of difference"), body, format);
    }

    @GetMapping("/manufacturing-workstations")
    public ResponseEntity<?> workstations(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                          @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.workstations(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) {
            body.add(List.of(r.workstation(), s(r.runs()), m(r.plannedMinutes()), m(r.actualMinutes()), m(r.varianceMinutes()), m(r.machineCost()), m(r.labourCost()), m(r.totalCost())));
        }
        return ExportFiles.file(exportService, "Workstation Time and Cost", "workstation-time", List.of("Workstation", "Steps run", "Planned minutes", "Actual minutes", "Difference", "Machine cost",
                "Labour cost", "Total"), body, format);
    }

    @GetMapping("/manufacturing-scrap")
    public ResponseEntity<?> scrap(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                   @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.scrap(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) body.add(List.of(r.productName(), r.reason(), s(r.runs()), s(r.scrappedUnits()), m(r.scrapCost())));
        return ExportFiles.file(exportService, "Scrap and Rejects", "scrap", List.of("Product", "Reason", "Runs", "Units scrapped", "Cost of scrap"), body, format);
    }

    @GetMapping("/manufacturing-order-plan")
    public ResponseEntity<?> orderPlan(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) Long productId, @RequestParam(defaultValue = "json") String format) {
        var rows = service.orderPlan(start(from), end(to), productId);
        if (!download(format)) return ResponseEntity.ok(rows);
        List<List<String>> body = new ArrayList<>();
        for (var r : rows) {
            body.add(List.of(r.orderNumber(), r.productName(), r.status().replace('_', ' '), s(r.scheduledDate()), s(r.quantityPlanned()), s(r.quantityProduced()), s(r.quantityRemaining()),
                    m(r.plannedCost()), m(r.plannedCostOfUnitsMade()), m(r.actualCost()), m(r.costVariance())));
        }
        return ExportFiles.file(exportService, "Manufacturing Orders: Plan against Actual", "order-plan", List.of("Order", "Product", "Status", "Scheduled", "Planned qty", "Made", "Remaining",
                "Planned cost", "Planned cost of units made", "Actual cost", "Difference"), body, format);
    }
}
