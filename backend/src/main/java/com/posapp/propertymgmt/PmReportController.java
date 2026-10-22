package com.posapp.propertymgmt;

import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.dto.PropertyMgmtDepositDtos.DepositRow;
import com.posapp.propertymgmt.dto.PropertyMgmtFeeInvoiceDtos.FeeInvoiceRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.OwnerRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.UnitRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDocumentDtos.DocumentDto;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.ContractorRow;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.RentIncreaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtInsightDtos.TrendRow;
import com.posapp.propertymgmt.dto.PropertyMgmtMaintenanceDtos.MaintenanceCostRow;
import com.posapp.propertymgmt.dto.PropertyMgmtMaintenanceDtos.MaintenanceRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.CollectionRow;
import com.posapp.propertymgmt.dto.PropertyMgmtChargeDtos.ChargeReportRow;
import com.posapp.propertymgmt.dto.PropertyMgmtRentDtos.RentChargeRow;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.StatementRow;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Property Management reports: the rent roll, vacancies, lease expiry and the owners' portfolios. Admin or the Reports area; {@code format} is json, xlsx or pdf. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class PmReportController {

    private final PmLeaseService leaseService;
    private final PmPropertyService propertyService;
    private final PmOwnerService ownerService;
    private final PmViews views;
    private final PmRentService rentService;
    private final PmMaintenanceService maintenanceService;
    private final PmStatementService statementService;
    private final PropertyManagementAccess access;
    private final ReportExportService exportService;
    private final PmInsightService insight;
    private final PmDocumentService documentService;
    private final PmDepositService depositService;
    private final PmFeeInvoiceService feeInvoiceService;
    private final PmChargeService chargeService;

    public PmReportController(PmLeaseService leaseService, PmPropertyService propertyService, PmOwnerService ownerService, PmViews views, PmRentService rentService,
                              PmMaintenanceService maintenanceService, PmStatementService statementService, PropertyManagementAccess access, ReportExportService exportService, PmInsightService insight, PmDocumentService documentService, PmDepositService depositService, PmFeeInvoiceService feeInvoiceService, PmChargeService chargeService) {
        this.feeInvoiceService = feeInvoiceService;
        this.chargeService = chargeService;
        this.depositService = depositService;
        this.insight = insight;
        this.documentService = documentService;
        this.statementService = statementService;
        this.rentService = rentService;
        this.maintenanceService = maintenanceService;
        this.leaseService = leaseService;
        this.propertyService = propertyService;
        this.ownerService = ownerService;
        this.views = views;
        this.access = access;
        this.exportService = exportService;
    }

    private static boolean isDownload(String format) {
        return "xlsx".equalsIgnoreCase(format) || "pdf".equalsIgnoreCase(format);
    }

    private ResponseEntity<byte[]> export(String title, List<String> headers, List<List<String>> rows, String format) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] content = xlsx ? exportService.toXlsx(title, headers, rows) : exportService.toPdf(title, headers, rows);
        String filename = title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + (xlsx ? ".xlsx" : ".pdf");
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(filename).build().toString())
                .body(content);
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String dash(Object o) {
        return o == null || String.valueOf(o).isBlank() ? "—" : String.valueOf(o);
    }

    /** The leases in force today with what they bring in. openOnly = leave out leases that have not started yet. */
    @GetMapping("/pm-rent-roll")
    public ResponseEntity<?> rentRoll(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<LeaseRow> rows = leaseService.list(null, LeaseStatus.ACTIVE, null, null, null, null, null, null).stream()
                .filter(r -> !"RENEWED".equals(r.state()) && (!openOnly || !"UPCOMING".equals(r.state())))
                .sorted(Comparator.comparing((LeaseRow r) -> s(r.propertyName()).toLowerCase()).thenComparing(r -> s(r.unitNo()))).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Property", "Unit", "Owner", "Tenant", "Lease", "Start", "End", "Days left", "Rent", "Frequency", "Per month", "Due day", "Deposit", "State");
            List<List<String>> body = new ArrayList<>();
            for (LeaseRow r : rows) {
                body.add(List.of(s(r.propertyName()), s(r.unitNo()), dash(r.ownerName()), s(r.tenantName()), r.code(), s(r.startDate()), s(r.endDate()), dash(r.daysToEnd()), s(r.rent()),
                        r.frequency().name(), s(r.monthlyRent()), s(r.dueDay()), dash(r.deposit()), r.state()));
            }
            return export("Property Management Rent Roll", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Units with nobody in them. openOnly = only units that can be let now (leave out those off the market). */
    @GetMapping("/pm-vacancy")
    @Transactional(readOnly = true)
    public ResponseEntity<?> vacancy(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        access.on();
        Snapshot snap = views.snapshot();
        List<UnitRow> rows = propertyService.units(null, null, null, null).stream()
                .filter(r -> "VACANT".equals(r.occupancy()) || !openOnly && "OFF_MARKET".equals(r.occupancy()) || !openOnly && "UPCOMING".equals(r.occupancy()))
                .filter(r -> {
                    PmProperty p = snap.properties.get(r.propertyId());
                    return p != null && p.isActive();
                })
                .sorted(Comparator.comparing((UnitRow r) -> r.vacantSince() == null ? snap.today : r.vacantSince())).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Property", "Unit", "Type", "Floor", "Bedrooms", "Size m²", "Asking rent", "Status", "Vacant since", "Next tenant", "Next lease starts");
            List<List<String>> body = new ArrayList<>();
            for (UnitRow r : rows) {
                body.add(List.of(s(r.propertyName()), r.unitNo(), r.kind().name(), dash(r.floor()), dash(r.bedrooms()), dash(r.sizeSqm()), dash(r.marketRent()), r.occupancy(),
                        dash(r.vacantSince()), "UPCOMING".equals(r.occupancy()) ? dash(r.tenantName()) : "—", "UPCOMING".equals(r.occupancy()) ? dash(r.leaseStart()) : "—"));
            }
            return export("Property Management Vacancy", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Leases in force by end date. openOnly = only those expiring soon or already past their end date. */
    @GetMapping("/pm-lease-expiry")
    public ResponseEntity<?> expiry(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<LeaseRow> rows = leaseService.list(null, LeaseStatus.ACTIVE, null, null, null, null, null, openOnly).stream().filter(r -> !"RENEWED".equals(r.state())).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Lease", "Property", "Unit", "Tenant", "Start", "End", "Days left", "Rent", "Frequency", "State", "Renewal");
            List<List<String>> body = new ArrayList<>();
            for (LeaseRow r : rows) {
                body.add(List.of(r.code(), s(r.propertyName()), s(r.unitNo()), s(r.tenantName()), s(r.startDate()), s(r.endDate()), dash(r.daysToEnd()), s(r.rent()), r.frequency().name(),
                        r.state(), dash(r.renewalCode())));
            }
            return export("Property Management Lease Expiry", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Each owner's portfolio. openOnly = leave out inactive owners. */
    @GetMapping("/pm-owners")
    public ResponseEntity<?> owners(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<OwnerRow> rows = ownerService.list(null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Owner", "Name", "Phone", "E-mail", "Properties", "Units", "Occupied", "Rent per month", "Active");
            List<List<String>> body = new ArrayList<>();
            for (OwnerRow r : rows) {
                body.add(List.of(r.code(), r.name(), dash(r.phone()), dash(r.email()), s(r.properties()), s(r.units()), s(r.occupied()), s(r.monthlyRent()), r.active() ? "Yes" : "No"));
            }
            return export("Property Management Owners", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every overdue rent invoice, oldest first, with how many days late it is. openOnly = leave out the ones 30 days late or less. */
    @GetMapping("/pm-arrears")
    @Transactional
    public ResponseEntity<?> arrears(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        access.on();
        List<RentChargeRow> rows = rentService.arrears(null, null, null).rows().stream().flatMap(t -> t.items().stream())
                .filter(r -> !openOnly || r.daysOverdue() > 30).sorted(Comparator.comparing(RentChargeRow::dueDate).thenComparing(RentChargeRow::id)).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Tenant", "Property", "Unit", "Owner", "Lease", "Period", "Due", "Days late", "Invoice", "Invoiced", "Paid", "Owed");
            List<List<String>> body = new ArrayList<>();
            for (RentChargeRow r : rows) {
                body.add(List.of(s(r.tenantName()), s(r.propertyName()), s(r.unitNo()), dash(r.ownerName()), s(r.leaseCode()), r.periodStart() + " to " + r.periodEnd(), s(r.dueDate()),
                        s(r.daysOverdue()), dash(r.invoiceNumber()), s(r.billed()), s(r.paid()), s(r.outstanding())));
            }
            return export("Property Management Arrears", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Rent by the month it fell due: invoiced, collected, still owed, not yet invoiced. openOnly = only months with something still owed or not yet invoiced. */
    @GetMapping("/pm-rent-collection")
    @Transactional
    public ResponseEntity<?> collection(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<CollectionRow> rows = rentService.collection(null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Month", "Charges", "Invoiced", "Collected", "Still owed", "Not yet invoiced", "Collected %");
            List<List<String>> body = new ArrayList<>();
            for (CollectionRow r : rows) {
                body.add(List.of(r.month(), s(r.charges()), s(r.invoiced()), s(r.collected()), s(r.outstanding()), s(r.notYetInvoiced()), s(r.collectedPercent())));
            }
            return export("Property Management Rent Collection", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every maintenance request with its cost and who pays. openOnly = only requests still open. */
    @GetMapping("/pm-maintenance")
    public ResponseEntity<?> maintenance(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<MaintenanceRow> rows = maintenanceService.list(null, null, openOnly, null, null, null, null, null, null, null);
        if (isDownload(format)) {
            List<String> headers = List.of("Request", "Property", "Unit", "Tenant", "Job", "Category", "Priority", "Status", "Reported", "Due", "Completed", "Days", "Contractor", "Estimate", "Cost",
                    "Paid by", "Work order", "Invoice");
            List<List<String>> body = new ArrayList<>();
            for (MaintenanceRow r : rows) {
                body.add(List.of(r.code(), s(r.propertyName()), s(r.unitNo()), dash(r.tenantName()), r.title(), r.category().name(), r.priority().name(), r.status().name(), s(r.reportedDate()),
                        dash(r.dueDate()), dash(r.completedDate()), dash(r.daysOpen()), dash(r.contractorName()), dash(r.estimatedCost()), dash(r.actualCost()), r.costBearer().name(),
                        dash(r.workOrderNumber()), dash(r.invoiceNumber())));
            }
            return export("Property Management Maintenance", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Repair cost of finished jobs per property, by who pays. openOnly = only properties with a cost. */
    @GetMapping("/pm-maintenance-cost")
    public ResponseEntity<?> maintenanceCost(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<MaintenanceCostRow> rows = maintenanceService.costByProperty(openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Property", "Owner", "Requests", "Done", "Owner pays", "Tenant pays", "Agency pays", "Total");
            List<List<String>> body = new ArrayList<>();
            for (MaintenanceCostRow r : rows) {
                body.add(List.of(s(r.propertyName()), dash(r.ownerName()), s(r.requests()), s(r.completed()), s(r.owner()), s(r.tenant()), s(r.agency()), s(r.total())));
            }
            return export("Property Management Maintenance Cost", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every owner statement with what the owner is owed. openOnly = leave out the ones already paid. */
    @GetMapping("/pm-owner-statements")
    public ResponseEntity<?> ownerStatements(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<StatementRow> rows = statementService.list(null, null, null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Statement", "Owner", "From", "To", "Status", "Rent collected", "Management fee", "VAT on fee", "Repairs", "Adjustments", "Net payable", "Paid on");
            List<List<String>> body = new ArrayList<>();
            for (StatementRow r : rows) {
                body.add(List.of(r.code(), s(r.ownerName()), s(r.periodFrom()), s(r.periodTo()), r.status().name(), s(r.rentCollected()), s(r.managementFee()), s(r.vat()), s(r.repairs()), s(r.adjustments()),
                        s(r.netPayable()), dash(r.paidDate())));
            }
            return export("Property Management Owner Statements", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Security deposits: what was agreed, what is in, what was kept or refunded and what is still held. openOnly = only those that need action (to collect, or to settle now the lease is over). */
    @GetMapping("/pm-deposits")
    public ResponseEntity<?> deposits(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<DepositRow> rows = depositService.list(null, null, null, null, null).stream()
                .filter(r -> !openOnly || "REFUND_DUE".equals(r.state()) || "DUE".equals(r.state()) || "PART_PAID".equals(r.state()))
                .sorted(Comparator.comparing((DepositRow r) -> "REFUND_DUE".equals(r.state()) ? 0 : 1).thenComparing(r -> s(r.propertyName()).toLowerCase()).thenComparing(r -> s(r.unitNo()))).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Property", "Unit", "Owner", "Tenant", "Lease", "End", "Days left", "Agreed", "Paid in", "Kept", "Refunded", "Held", "State");
            List<List<String>> body = new ArrayList<>();
            for (DepositRow r : rows) {
                body.add(List.of(s(r.propertyName()), s(r.unitNo()), dash(r.ownerName()), s(r.tenantName()), r.leaseCode(), s(r.endDate()), dash(r.daysToEnd()), s(r.agreed()), s(r.paidIn()),
                        s(r.deducted()), s(r.refunded()), s(r.held()), r.state()));
            }
            return export("Property Management Security Deposits", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** The invoices to owners for the management fee, with the VAT on them (for the VAT return). from/to = issue date. openOnly = leave out voided invoices. */
    @GetMapping("/pm-fee-invoices")
    public ResponseEntity<?> feeInvoices(@RequestParam(required = false) java.time.LocalDate from, @RequestParam(required = false) java.time.LocalDate to,
                                         @RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<FeeInvoiceRow> rows = feeInvoiceService.list(null, null, from, to, null).stream().filter(r -> !openOnly || r.status() == PmFeeInvoiceStatus.ISSUED).toList();
        if (isDownload(format)) {
            List<String> headers = List.of("Invoice", "Date", "Owner", "Owner tax ID", "Statement", "Period from", "Period to", "Fee", "VAT %", "VAT", "Total", "Status");
            List<List<String>> body = new ArrayList<>();
            for (FeeInvoiceRow r : rows) {
                body.add(List.of(r.number(), s(r.issueDate()), r.ownerName(), dash(r.ownerTaxNumber()), dash(r.statementCode()), s(r.periodFrom()), s(r.periodTo()), s(r.netAmount()), dash(r.vatPercent()),
                        s(r.vatAmount()), s(r.totalAmount()), r.status().name()));
            }
            return export("Property Management Fee Invoices", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** The utilities and service charges that rent invoices took, by the date the rent fell due. */
    @GetMapping("/pm-charges")
    public ResponseEntity<?> charges(@RequestParam(required = false) java.time.LocalDate from, @RequestParam(required = false) java.time.LocalDate to, @RequestParam(defaultValue = "json") String format) {
        List<ChargeReportRow> rows = chargeService.report(from, to);
        if (isDownload(format)) {
            List<String> headers = List.of("Rent due", "Property", "Unit", "Owner", "Tenant", "Lease", "Charge", "Kind", "Details", "Invoice", "Amount");
            List<List<String>> body = new ArrayList<>();
            for (ChargeReportRow r : rows) {
                body.add(List.of(s(r.dueDate()), dash(r.propertyName()), dash(r.unitNo()), dash(r.ownerName()), dash(r.tenantName()), dash(r.leaseCode()), r.name(), r.source().name(), r.description(), dash(r.invoiceNumber()), s(r.amount())));
            }
            return export("Property Management Utilities and Service Charges", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Leases with a yearly increase and what the rent becomes at renewal, soonest first. openOnly = only those ending within 120 days or already past their end. */
    @GetMapping("/pm-rent-increases")
    public ResponseEntity<?> rentIncreases(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<RentIncreaseRow> rows = insight.rentIncreases(openOnly, 120);
        if (isDownload(format)) {
            List<String> headers = List.of("Lease", "Property", "Unit", "Owner", "Tenant", "Ends", "Days left", "Rent now", "Increase %", "Rent after", "Extra", "Frequency", "State", "Renewal");
            List<List<String>> body = new ArrayList<>();
            for (RentIncreaseRow r : rows) {
                body.add(List.of(r.code(), s(r.propertyName()), s(r.unitNo()), dash(r.ownerName()), s(r.tenantName()), s(r.endDate()), dash(r.daysToEnd()), s(r.rent()), s(r.escalationPercent()),
                        s(r.newRent()), s(r.increase()), r.frequency(), r.state(), dash(r.renewalCode())));
            }
            return export("Property Management Rent Increases Due", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Month by month for the last twelve: units let, rent invoiced and collected. openOnly = only months with rent still owed or with units empty. */
    @GetMapping("/pm-trend")
    public ResponseEntity<?> trend(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "12") int months, @RequestParam(defaultValue = "json") String format) {
        List<TrendRow> rows = insight.trend(months, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Month", "Units", "Let", "Occupancy %", "Charges", "Invoiced", "Collected", "Still owed", "Collected %");
            List<List<String>> body = new ArrayList<>();
            for (TrendRow r : rows) {
                body.add(List.of(r.month(), s(r.units()), s(r.occupied()), s(r.occupancyPercent()), s(r.charges()), s(r.invoiced()), s(r.collected()), s(r.outstanding()), s(r.collectedPercent())));
            }
            return export("Property Management Monthly Trend", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Repairs by contractor. openOnly = only contractors who have been paid something. */
    @GetMapping("/pm-contractors")
    public ResponseEntity<?> contractors(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<ContractorRow> rows = insight.contractors(openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Contractor", "Jobs", "Done", "Open", "Late", "Total cost", "Average cost", "Average days");
            List<List<String>> body = new ArrayList<>();
            for (ContractorRow r : rows) {
                body.add(List.of(r.contractorName(), s(r.jobs()), s(r.completed()), s(r.open()), s(r.late()), s(r.totalCost()), dash(r.averageCost()), dash(r.averageDays())));
            }
            return export("Property Management Repairs By Contractor", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    /** Every document on file with what it belongs to and when it lapses. openOnly = only documents that have an expiry date (soonest first), so the ones already lapsed or about to are at the top. */
    @GetMapping("/pm-documents")
    public ResponseEntity<?> documents(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<DocumentDto> rows = documentService.list(null, null, null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Document", "Attached to", "Kind", "File", "Expires", "State", "Days left", "Added by", "Added on");
            List<List<String>> body = new ArrayList<>();
            for (DocumentDto r : rows) {
                body.add(List.of(r.name(), dash(r.subjectLabel()), r.category().name(), r.fileName(), dash(r.expiryDate()), r.expiryState(), dash(r.daysToExpiry()), dash(r.uploadedByName()),
                        s(r.createdAt() == null ? null : r.createdAt().toString().substring(0, 10))));
            }
            return export("Property Management Documents", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
}
