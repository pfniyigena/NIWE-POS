package com.posapp.manufacturing;

import com.posapp.common.Qty;
import com.posapp.common.ExportFiles;
import com.posapp.manufacturing.dto.*;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Excel / PDF / CSV downloads of the Manufacturing lists. Each takes the filters of its screen
 * and returns every matching row, not one page.
 */
@RestController
@RequestMapping("/api/manufacturing")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class ManufacturingExportController {

    private final ManufacturingOrderService orderService;
    private final BomService bomService;
    private final RoutingService routingService;
    private final WorkstationService workstationService;
    private final OverheadCostService overheadCostService;
    private final ProductionPlanService planService;
    private final ReportExportService exportService;

    public ManufacturingExportController(ManufacturingOrderService orderService, BomService bomService, RoutingService routingService,
                                         WorkstationService workstationService, OverheadCostService overheadCostService,
                                         ProductionPlanService planService, ReportExportService exportService) {
        this.orderService = orderService;
        this.bomService = bomService;
        this.routingService = routingService;
        this.workstationService = workstationService;
        this.overheadCostService = overheadCostService;
        this.planService = planService;
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

    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> orders(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (ManufacturingOrderDto o : orderService.filtered(q, status, productId, from, to)) {
            body.add(List.of(s(o.orderNumber()), s(o.productName()), s(o.bomCode()), s(o.status()), s(o.quantityPlanned()), s(o.quantityProduced()),
                    s(o.scheduledDate()), when(o.completedAt()), num(o.totalMaterialCost()), num(o.totalOverheadCost()), num(o.totalCost()), num(o.unitCost()),
                    s(o.warehouseName()), s(o.locationName()), s(o.note())));
        }
        return ExportFiles.file(exportService, title("Manufacturing orders", pick("status", status), range("scheduled", from, to), search(q)), "manufacturing-orders",
                List.of("Order", "Product", "Recipe", "Status", "Planned", "Produced", "Scheduled", "Completed", "Material cost", "Overhead cost", "Total cost", "Unit cost", "Warehouse", "Location", "Note"), body, format);
    }

    @GetMapping("/boms/export")
    public ResponseEntity<byte[]> boms(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) Long productId,
                                       @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (BomDto b : bomService.filtered(q, status, productId)) {
            body.add(List.of(s(b.code()), s(b.name()), s(b.productName()), s(b.routingName()), b.active() ? "Active" : "Inactive", s(b.lines().size()), num(b.estimatedUnitMaterialCost()),
                    b.lines().stream().map(l -> l.componentName() + " x" + Qty.str(l.quantityPerUnit())).collect(Collectors.joining("; "))));
        }
        return ExportFiles.file(exportService, title("Bills of materials", pick("status", status), search(q)), "manufacturing-boms",
                List.of("Code", "Name", "Product", "Routing", "Status", "Components", "Material cost per unit", "Component list"), body, format);
    }

    @GetMapping("/routings/export")
    public ResponseEntity<byte[]> routings(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (RoutingDto r : routingService.filtered(q, status)) {
            body.add(List.of(s(r.name()), s(r.description()), r.active() ? "Active" : "Inactive", s(r.operations().size()),
                    r.operations().stream().map(o -> o.sequence() + ". " + o.name() + (o.workstationName() == null ? "" : " @ " + o.workstationName())).collect(Collectors.joining("; ")),
                    num(r.operations().stream().map(RoutingDto.OperationDto::estimatedMinutesPerUnit).filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add))));
        }
        return ExportFiles.file(exportService, title("Routings", pick("status", status), search(q)), "manufacturing-routings",
                List.of("Name", "Description", "Status", "Operations", "Steps", "Minutes per unit"), body, format);
    }

    @GetMapping("/workstations/export")
    public ResponseEntity<byte[]> workstations(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (WorkstationDto w : workstationService.filtered(q, status)) {
            body.add(List.of(s(w.code()), s(w.name()), s(w.description()), num(w.hourlyRate()), w.active() ? "Active" : "Inactive"));
        }
        return ExportFiles.file(exportService, title("Workstations", pick("status", status), search(q)), "manufacturing-workstations",
                List.of("Code", "Name", "Description", "Hourly rate", "Status"), body, format);
    }

    @GetMapping("/overhead-costs/export")
    public ResponseEntity<byte[]> overheads(@RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) String method,
                                            @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (OverheadCostDto o : overheadCostService.filtered(q, status, method)) {
            body.add(List.of(s(o.code()), s(o.name()), s(o.allocationMethod()), num(o.rate()), o.active() ? "Active" : "Inactive"));
        }
        return ExportFiles.file(exportService, title("Overhead costs", pick("status", status), pick("method", method), search(q)), "manufacturing-overheads",
                List.of("Code", "Name", "Method", "Rate", "Status"), body, format);
    }

    @GetMapping("/production-plans/export")
    public ResponseEntity<byte[]> plans(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        List<List<String>> body = new ArrayList<>();
        for (ProductionPlanDto p : planService.filtered(q, from, to)) {
            for (ProductionPlanDto.LineDto l : p.lines()) {
                body.add(List.of(s(p.planNumber()), when(p.generatedAt()), s(p.lowStockThreshold()), s(l.productName()), s(l.bomCode()), s(l.demandFromSales()),
                        s(l.demandFromLowStock()), s(l.suggestedQuantity()), s(l.generatedManufacturingOrderNumber()),
                        l.componentShortfalls() == null ? "" : l.componentShortfalls().size() + ""));
            }
            if (p.lines().isEmpty()) body.add(List.of(s(p.planNumber()), when(p.generatedAt()), s(p.lowStockThreshold()), "", "", "", "", "", "", ""));
        }
        return ExportFiles.file(exportService, title("Production plans", range("made", from, to), search(q)), "manufacturing-plans",
                List.of("Plan", "Made", "Low-stock level", "Product", "Recipe", "From sales", "From low stock", "Suggested", "Order made", "Short components"), body, format);
    }
}
