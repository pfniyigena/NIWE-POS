package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateDealDtos.CommissionRow;
import com.posapp.realestate.dto.RealEstateDealDtos.DealRow;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateDtos.PropertyRow;
import com.posapp.realestate.dto.RealEstateDtos.ViewingDto;
import com.posapp.realestate.dto.RealEstateDocDtos.DocumentDto;
import com.posapp.realestate.dto.RealEstateDocDtos.ValuationDto;
import com.posapp.realestate.dto.RealEstateProjectDtos.ProjectRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitSaleRow;
import com.posapp.reports.ReportExportService;
import com.posapp.security.AppUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Real Estate reports: the listings, the leads and the viewings. Admin or the Reports area; {@code format} is json, xlsx or pdf. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_REPORTS_VIEW')")
public class ReReportController {

    private final RePropertyService propertyService;
    private final ReLeadService leadService;
    private final ReViewingService viewingService;
    private final ReDealService dealService;
    private final ReProjectService projectService;
    private final ReUnitSaleService unitSaleService;
    private final ReDocumentService documentService;
    private final ReValuationService valuationService;
    private final ReportExportService exportService;

    public ReReportController(RePropertyService propertyService, ReLeadService leadService, ReViewingService viewingService, ReDealService dealService, ReProjectService projectService,
                              ReUnitSaleService unitSaleService, ReDocumentService documentService, ReValuationService valuationService,
                              ReportExportService exportService) {
        this.propertyService = propertyService;
        this.leadService = leadService;
        this.viewingService = viewingService;
        this.dealService = dealService;
        this.projectService = projectService;
        this.unitSaleService = unitSaleService;
        this.documentService = documentService;
        this.valuationService = valuationService;
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

    @GetMapping("/re-listings")
    public ResponseEntity<?> listings(@RequestParam(required = false) PropertyStatus status, @RequestParam(defaultValue = "json") String format) {
        List<PropertyRow> rows = propertyService.list(null, status, null, null, null, null, null, null, null);
        if (isDownload(format)) {
            List<String> headers = List.of("Code", "Title", "Type", "For", "Status", "Area", "Bedrooms", "Size (m²)", "Sale price", "Rent / month", "Agent", "Listed", "Days listed", "Open leads");
            List<List<String>> body = new ArrayList<>();
            for (PropertyRow r : rows) {
                body.add(List.of(r.code(), r.title(), r.type().name(), r.purpose().name(), r.status().name(), dash(r.area()), dash(r.bedrooms()), dash(r.sizeSqm()), dash(r.salePrice()),
                        dash(r.rentPrice()), dash(r.agentName()), dash(r.listedDate()), s(r.daysListed()), s(r.openLeads())));
            }
            return export("Real Estate Listings", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-leads")
    public ResponseEntity<?> leads(@RequestParam(required = false) LeadStatus status, @RequestParam(defaultValue = "false") boolean openOnly,
                                   @RequestParam(defaultValue = "json") String format) {
        List<LeadRow> rows = leadService.list(null, status, null, null, null, false, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Name", "Phone", "Looking to", "Status", "Source", "Budget from", "Budget to", "Area", "Property", "Assigned to", "Next follow-up", "Overdue", "Last contact");
            List<List<String>> body = new ArrayList<>();
            for (LeadRow r : rows) {
                body.add(List.of(r.name(), dash(r.phone()), r.interest().name(), r.status().name(), r.source().name(), dash(r.budgetMin()), dash(r.budgetMax()), dash(r.preferredArea()),
                        dash(r.propertyCode()), dash(r.assignedName()), dash(r.nextFollowUp()), r.followUpOverdue() ? "Yes" : "", dash(r.lastContactAt())));
            }
            return export("Real Estate Leads", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-viewings")
    public ResponseEntity<?> viewings(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                      @RequestParam(defaultValue = "json") String format) {
        // Viewings are mostly ahead of us: by default from the start of this month to a month from now.
        LocalDate start = from != null ? from : LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC).plusDays(30);
        List<ViewingDto> rows = viewingService.list(RealEstateController.actor(me), start, end, null, null);
        if (isDownload(format)) {
            List<String> headers = List.of("Start", "End", "Property", "Area", "Lead", "Phone", "Agent", "Status", "Feedback");
            List<List<String>> body = new ArrayList<>();
            for (ViewingDto r : rows) {
                body.add(List.of(s(r.startAt()), s(r.endAt()), r.propertyCode() + " " + s(r.propertyTitle()), dash(r.propertyArea()), s(r.leadName()), dash(r.leadPhone()),
                        dash(r.agentName()), r.status().name(), dash(r.feedback())));
            }
            return export("Real Estate Viewings", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-deals")
    public ResponseEntity<?> deals(@RequestParam(required = false) DealStage stage, @RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        List<DealRow> rows = dealService.list(null, stage, null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Deal", "Property", "Buyer", "Agent", "Stage", "Asking price", "Offer", "Agreed price", "Paid", "Outstanding", "Commission", "Offer date", "Expected completion", "Completed");
            List<List<String>> body = new ArrayList<>();
            for (DealRow r : rows) {
                body.add(List.of(r.code(), s(r.propertyCode()) + " " + s(r.propertyTitle()), r.buyerName(), dash(r.agentName()), r.stage().name(), dash(r.listPrice()), s(r.offerAmount()),
                        dash(r.agreedPrice()), s(r.paid()), dash(r.outstanding()), s(r.commissionTotal()), dash(r.offerDate()), dash(r.expectedCompletion()), dash(r.completedDate())));
            }
            return export("Real Estate Deals", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-commission")
    public ResponseEntity<?> commission(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long agentId,
                                        @RequestParam(defaultValue = "json") String format) {
        // Sales completed in the period; by default from the start of this month to today.
        LocalDate start = from != null ? from : LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        List<CommissionRow> rows = dealService.commissionRows(start, end, agentId);
        if (isDownload(format)) {
            List<String> headers = List.of("Deal", "Property", "Completed", "Sale price", "Role", "Paid to", "Commission", "State", "Expense");
            List<List<String>> body = new ArrayList<>();
            for (CommissionRow r : rows) {
                String state = r.status() == CommissionStatus.POSTED ? (r.expensePaid() ? "Paid" : "Posted") : r.due() ? "To post" : "Planned";
                body.add(List.of(r.dealCode(), s(r.propertyCode()) + " " + s(r.propertyTitle()), dash(r.completedDate()), s(r.price()), r.role(), r.payeeName(), s(r.amount()), state,
                        dash(r.expenseStatus())));
            }
            return export("Real Estate Commission", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
    @GetMapping("/re-projects")
    public ResponseEntity<?> projects(@RequestParam(required = false) ProjectStatus status, @RequestParam(defaultValue = "json") String format) {
        List<ProjectRow> rows = projectService.list(null, status);
        if (isDownload(format)) {
            List<String> headers = List.of("Code", "Project", "Area", "Developer", "Status", "Launch", "Handover", "Units", "Available", "Reserved", "Sold", "Withheld", "Stock value", "Sold value");
            List<List<String>> body = new ArrayList<>();
            for (ProjectRow r : rows) {
                body.add(List.of(r.code(), r.name(), dash(r.area()), dash(r.developerName()), r.status().name(), dash(r.launchDate()), dash(r.handoverDate()), s(r.totalUnits()),
                        s(r.available()), s(r.reserved()), s(r.sold()), s(r.withheld()), s(r.stockValue()), s(r.soldValue())));
            }
            return export("Real Estate Projects", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-price-list")
    public ResponseEntity<?> priceList(@RequestParam(required = false) Long projectId, @RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        // openOnly = only units that are still available
        List<UnitRow> rows = projectService.units(null, projectId, null, null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Project", "Block", "Unit", "Type", "Floor", "Bedrooms", "Size (m²)", "List price", "Price per m²", "Status", "Buyer");
            List<List<String>> body = new ArrayList<>();
            for (UnitRow r : rows) {
                body.add(List.of(s(r.projectName()), dash(r.block()), r.unitNo(), r.kind().name(), dash(r.floor()), dash(r.bedrooms()), dash(r.sizeSqm()), s(r.listPrice()), dash(r.pricePerSqm()),
                        r.status().name(), dash(r.buyerName())));
            }
            return export("Real Estate Price List", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-unit-sales")
    public ResponseEntity<?> unitSales(@RequestParam(required = false) UnitSaleStage stage, @RequestParam(required = false) Long projectId, @RequestParam(defaultValue = "false") boolean openOnly,
                                       @RequestParam(defaultValue = "json") String format) {
        List<UnitSaleRow> rows = unitSaleService.list(null, stage, projectId, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Sale", "Project", "Unit", "Buyer", "Agent", "Stage", "List price", "Agreed price", "Paid", "Outstanding", "Reserved", "Agreement", "Completed", "Invoice", "Late");
            List<List<String>> body = new ArrayList<>();
            for (UnitSaleRow r : rows) {
                body.add(List.of(r.code(), s(r.projectName()), s(r.unitNo()), r.buyerName(), dash(r.agentName()), r.stage().name(), s(r.listPrice()), s(r.agreedPrice()), s(r.paid()),
                        s(r.outstanding()), dash(r.reservedOn()), dash(r.agreementDate()), dash(r.completedDate()), dash(r.invoiceNumber()), r.overdue() ? "Yes" : ""));
            }
            return export("Real Estate Unit Sales", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
    @GetMapping("/re-valuations")
    public ResponseEntity<?> valuations(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        // openOnly = only the current (latest) valuation of each property
        List<ValuationDto> rows = valuationService.list(null, openOnly, null);
        if (isDownload(format)) {
            List<String> headers = List.of("Property", "Title", "Valuation date", "Valuer", "Firm", "Purpose", "Method", "Market value", "Forced-sale value", "Monthly rent", "Asking price", "vs asking %", "Valid until", "Expired", "Current");
            List<List<String>> body = new ArrayList<>();
            for (ValuationDto r : rows) {
                body.add(List.of(s(r.propertyCode()), s(r.propertyTitle()), s(r.valuationDate()), r.valuerName(), dash(r.valuerFirm()), r.purpose().name(), r.method().name(), s(r.marketValue()),
                        dash(r.forcedSaleValue()), dash(r.monthlyRent()), dash(r.askingPrice()), dash(r.vsAskingPercent()), dash(r.validUntil()), r.expired() ? "Yes" : "", r.current() ? "Yes" : ""));
            }
            return export("Real Estate Valuations", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/re-documents")
    public ResponseEntity<?> documents(@RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "json") String format) {
        // openOnly = only documents that have an expiry date, soonest first
        List<DocumentDto> rows = documentService.list(null, null, null, null, openOnly);
        if (isDownload(format)) {
            List<String> headers = List.of("Document", "Attached to", "Category", "File", "Expires", "State", "Days left", "Added by", "Added");
            List<List<String>> body = new ArrayList<>();
            for (DocumentDto r : rows) {
                body.add(List.of(r.name(), dash(r.subjectLabel()), r.category().name(), r.fileName(), dash(r.expiryDate()), r.expiryState(), dash(r.daysToExpiry()), dash(r.uploadedByName()), s(r.createdAt())));
            }
            return export("Real Estate Documents", headers, body, format);
        }
        return ResponseEntity.ok(rows);
    }
}
