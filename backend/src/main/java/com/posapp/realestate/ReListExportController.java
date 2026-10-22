package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateDealDtos.DealRow;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateDtos.PropertyRow;
import com.posapp.realestate.dto.RealEstateDtos.ViewingDto;
import com.posapp.realestate.dto.RealEstateProjectDtos.ProjectRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitSaleRow;
import com.posapp.reports.ReportExportService;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/realestate/export}: the list screens (properties, leads, deals, projects, units, unit sales, viewings) as Excel, PDF or CSV, filtered exactly like the lists on
 * screen and always complete (never one page). Operations viewers and managers and admins.
 */
@RestController
@RequestMapping("/api/realestate/export")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class ReListExportController {

    private final RePropertyService propertyService;
    private final ReLeadService leadService;
    private final ReViewingService viewingService;
    private final ReDealService dealService;
    private final ReProjectService projectService;
    private final ReUnitSaleService unitSaleService;
    private final ReCollectionService collectionService;
    private final ReCommissionService commissionService;
    private final ReportExportService exportService;

    public ReListExportController(RePropertyService propertyService, ReLeadService leadService, ReViewingService viewingService, ReDealService dealService,
                                  ReProjectService projectService, ReUnitSaleService unitSaleService, ReCollectionService collectionService, ReCommissionService commissionService, ReportExportService exportService) {
        this.propertyService = propertyService;
        this.leadService = leadService;
        this.viewingService = viewingService;
        this.dealService = dealService;
        this.projectService = projectService;
        this.unitSaleService = unitSaleService;
        this.collectionService = collectionService;
        this.commissionService = commissionService;
        this.exportService = exportService;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String yes(boolean b) {
        return b ? "Yes" : "";
    }

    @GetMapping("/properties")
    public ResponseEntity<byte[]> properties(@RequestParam(required = false) String q, @RequestParam(required = false) PropertyStatus status, @RequestParam(required = false) PropertyType type,
                                             @RequestParam(required = false) ListingPurpose purpose, @RequestParam(required = false) Long agentId, @RequestParam(required = false) String area,
                                             @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Integer bedrooms,
                                             @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Code", "Title", "Type", "For", "Status", "Area", "Bedrooms", "Size (m²)", "Sale price", "Rent / month", "Agent", "Listed", "Days listed", "Open leads",
                "Upcoming viewings", "On website");
        List<List<String>> body = new ArrayList<>();
        for (PropertyRow r : propertyService.list(q, status, type, purpose, agentId, area, minPrice, maxPrice, bedrooms)) {
            body.add(List.of(s(r.code()), s(r.title()), s(r.type()), s(r.purpose()), s(r.status()), s(r.area()), s(r.bedrooms()), s(r.sizeSqm()), s(r.salePrice()), s(r.rentPrice()),
                    s(r.agentName()), s(r.listedDate()), s(r.listedDate() == null ? null : r.daysListed()), s(r.openLeads()), s(r.upcomingViewings()), yes(r.publicListing())));
        }
        return file("Properties", headers, body, format);
    }

    @GetMapping("/leads")
    public ResponseEntity<byte[]> leads(@RequestParam(required = false) String q, @RequestParam(required = false) LeadStatus status, @RequestParam(required = false) LeadInterest interest,
                                        @RequestParam(required = false) Long assignedId, @RequestParam(required = false) Long propertyId, @RequestParam(required = false) Boolean dueOnly,
                                        @RequestParam(required = false) Boolean openOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Name", "Phone", "E-mail", "Looking to", "Status", "Source", "Budget from", "Budget to", "Area", "Property", "Assigned to", "Next follow-up", "Overdue",
                "Last contact", "Viewings");
        List<List<String>> body = new ArrayList<>();
        for (LeadRow r : leadService.list(q, status, interest, assignedId, propertyId, dueOnly, openOnly)) {
            body.add(List.of(s(r.name()), s(r.phone()), s(r.email()), s(r.interest()), s(r.status()), s(r.source()), s(r.budgetMin()), s(r.budgetMax()), s(r.preferredArea()),
                    s(r.propertyCode()), s(r.assignedName()), s(r.nextFollowUp()), yes(r.followUpOverdue()), s(r.lastContactAt()), s(r.viewings())));
        }
        return file("Leads", headers, body, format);
    }

    @GetMapping("/deals")
    public ResponseEntity<byte[]> deals(@RequestParam(required = false) String q, @RequestParam(required = false) DealStage stage, @RequestParam(required = false) Long propertyId,
                                        @RequestParam(required = false) Long agentId, @RequestParam(required = false) Boolean openOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Deal", "Property", "Buyer", "Agent", "Stage", "Asking price", "Offer", "Agreed price", "Paid", "Outstanding", "Commission", "Offer date",
                "Expected completion", "Completed", "Late");
        List<List<String>> body = new ArrayList<>();
        for (DealRow r : dealService.list(q, stage, propertyId, agentId, openOnly)) {
            body.add(List.of(s(r.code()), (s(r.propertyCode()) + " " + s(r.propertyTitle())).trim(), s(r.buyerName()), s(r.agentName()), s(r.stage()), s(r.listPrice()), s(r.offerAmount()),
                    s(r.agreedPrice()), s(r.paid()), s(r.outstanding()), s(r.commissionTotal()), s(r.offerDate()), s(r.expectedCompletion()), s(r.completedDate()), yes(r.overdue())));
        }
        return file("Deals", headers, body, format);
    }

    @GetMapping("/projects")
    public ResponseEntity<byte[]> projects(@RequestParam(required = false) String q, @RequestParam(required = false) ProjectStatus status, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Code", "Project", "Area", "Developer", "Status", "Launch", "Handover", "Units", "Available", "Reserved", "Sold", "Withheld", "Stock value", "Sold value");
        List<List<String>> body = new ArrayList<>();
        for (ProjectRow r : projectService.list(q, status)) {
            body.add(List.of(s(r.code()), s(r.name()), s(r.area()), s(r.developerName()), s(r.status()), s(r.launchDate()), s(r.handoverDate()), s(r.totalUnits()), s(r.available()),
                    s(r.reserved()), s(r.sold()), s(r.withheld()), s(r.stockValue()), s(r.soldValue())));
        }
        return file("Projects", headers, body, format);
    }

    @GetMapping("/units")
    public ResponseEntity<byte[]> units(@RequestParam(required = false) String q, @RequestParam(required = false) Long projectId, @RequestParam(required = false) UnitStatus status,
                                        @RequestParam(required = false) UnitKind kind, @RequestParam(required = false) String block, @RequestParam(required = false) Boolean availableOnly,
                                        @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Project", "Block", "Unit", "Type", "Floor", "Bedrooms", "Size (m²)", "List price", "Price per m²", "Status", "Buyer");
        List<List<String>> body = new ArrayList<>();
        for (UnitRow r : projectService.units(q, projectId, status, kind, block, availableOnly)) {
            body.add(List.of(s(r.projectName()), s(r.block()), s(r.unitNo()), s(r.kind()), s(r.floor()), s(r.bedrooms()), s(r.sizeSqm()), s(r.listPrice()), s(r.pricePerSqm()), s(r.status()),
                    s(r.buyerName())));
        }
        return file("Units", headers, body, format);
    }

    @GetMapping("/unit-sales")
    public ResponseEntity<byte[]> unitSales(@RequestParam(required = false) String q, @RequestParam(required = false) UnitSaleStage stage, @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) Long unitId, @RequestParam(required = false) Boolean openOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Sale", "Project", "Unit", "Buyer", "Agent", "Stage", "List price", "Agreed price", "Paid", "Outstanding", "Reserved", "Agreement", "Completed", "Invoice",
                "Late");
        List<List<String>> body = new ArrayList<>();
        for (UnitSaleRow r : unitSaleService.list(q, stage, projectId, unitId, openOnly)) {
            body.add(List.of(s(r.code()), s(r.projectName()), s(r.unitNo()), s(r.buyerName()), s(r.agentName()), s(r.stage()), s(r.listPrice()), s(r.agreedPrice()), s(r.paid()),
                    s(r.outstanding()), s(r.reservedOn()), s(r.agreementDate()), s(r.completedDate()), s(r.invoiceNumber()), yes(r.overdue())));
        }
        return file("Unit Sales", headers, body, format);
    }

    @GetMapping("/collections")
    public ResponseEntity<byte[]> collections(@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long agentId, @RequestParam(required = false) Integer days,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("List", "Sale", "Project", "Unit", "Buyer", "Phone", "E-mail", "Agent", "Agreed price", "Paid", "Outstanding", "Overdue", "Days overdue", "Next due",
                "Next amount", "Promised for", "Promise broken", "Last note");
        List<List<String>> body = new ArrayList<>();
        var board = collectionService.board(projectId, agentId, days);
        for (var part : List.of(Map.entry("Overdue", board.overdue()), Map.entry("Due soon", board.dueSoon()), Map.entry("No schedule", board.unscheduled()))) {
            for (var r : part.getValue()) {
                body.add(List.of(part.getKey(), s(r.saleCode()), s(r.projectName()), s(r.unitNo()), s(r.buyerName()), s(r.buyerPhone()), s(r.buyerEmail()), s(r.agentName()), s(r.agreedPrice()),
                        s(r.paid()), s(r.outstanding()), r.overdueAmount().signum() > 0 ? s(r.overdueAmount()) : "", r.oldestDaysOverdue() > 0 ? s(r.oldestDaysOverdue()) : "", s(r.nextDueDate()),
                        s(r.nextDueAmount()), s(r.promiseDate()), yes(r.promiseBroken()), s(r.lastNoteText())));
            }
        }
        return file("Collections", headers, body, format);
    }

    @GetMapping("/commissions")
    public ResponseEntity<byte[]> commissions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String payee,
                                              @RequestParam(defaultValue = "false") boolean openOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Paid to", "Deal", "Property", "Completed", "Sale price", "Role", "Commission", "Status", "Paid on");
        List<List<String>> body = new ArrayList<>();
        for (var st : commissionService.board(from, to, payee, openOnly).payees()) {
            for (var l : st.lines()) {
                body.add(List.of(s(st.payeeName()), s(l.dealCode()), (s(l.propertyCode()) + " " + s(l.propertyTitle())).trim(), s(l.completedDate()), s(l.salePrice()), s(l.role()), s(l.amount()),
                        switch (l.state()) {
                            case PLANNED -> "Not earned yet";
                            case TO_POST -> "Earned, to post";
                            case AWAITING_APPROVAL -> "Awaiting approval";
                            case TO_PAY -> "Approved, to pay";
                            case PAID -> "Paid";
                            case REJECTED -> "Rejected";
                        }, s(l.paidOn())));
            }
        }
        return file("Commission", headers, body, format);
    }

    @GetMapping("/viewings")
    public ResponseEntity<byte[]> viewings(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Long agentId,
                                           @RequestParam(required = false) ViewingStatus status, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Start", "End", "Property", "Area", "Client", "Phone", "Agent", "Status", "Feedback");
        List<List<String>> body = new ArrayList<>();
        for (ViewingDto r : viewingService.list(RealEstateController.actor(me), from, to, agentId, status)) {
            body.add(List.of(s(r.startAt()), s(r.endAt()), (s(r.propertyCode()) + " " + s(r.propertyTitle())).trim(), s(r.propertyArea()), s(r.leadName()), s(r.leadPhone()), s(r.agentName()),
                    s(r.status()), s(r.feedback())));
        }
        return file("Viewings", headers, body, format);
    }

    private ResponseEntity<byte[]> file(String name, List<String> headers, List<List<String>> body, String format) {
        String title = "Real Estate " + name;
        String base = title.toLowerCase().replace(' ', '-');
        byte[] content;
        MediaType type;
        String ext;
        if ("csv".equalsIgnoreCase(format)) {
            content = csv(headers, body);
            type = MediaType.parseMediaType("text/csv;charset=UTF-8");
            ext = ".csv";
        } else if ("pdf".equalsIgnoreCase(format)) {
            content = exportService.toPdf(title, headers, body);
            type = MediaType.APPLICATION_PDF;
            ext = ".pdf";
        } else {
            content = exportService.toXlsx(title, headers, body);
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ext = ".xlsx";
        }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(base + ext).build().toString())
                .body(content);
    }

    /** Comma separated, every value in quotes, with a byte-order mark so Excel opens the accents right. A value that would be read as a formula is made harmless. */
    static byte[] csv(List<String> headers, List<List<String>> body) {
        StringBuilder b = new StringBuilder("﻿");
        b.append(line(headers));
        for (List<String> row : body) b.append(line(row));
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String line(List<String> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) b.append(',');
            String v = cells.get(i) == null ? "" : cells.get(i);
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9][0-9.,]*")) v = "'" + v;
            b.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return b.append("\r\n").toString();
    }
}
