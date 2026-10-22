package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.OwnerRow;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.TenantRow;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /api/propertymgmt/export}: the owners, tenants and leases lists as Excel, PDF or CSV, filtered exactly like the lists on screen and always complete (never one page).
 * Operations viewers and managers and admins.
 */
@RestController
@RequestMapping("/api/propertymgmt/export")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class PmListExportController {

    private final PmOwnerService ownerService;
    private final PmTenantService tenantService;
    private final PmLeaseService leaseService;
    private final ReportExportService exportService;

    public PmListExportController(PmOwnerService ownerService, PmTenantService tenantService, PmLeaseService leaseService, ReportExportService exportService) {
        this.ownerService = ownerService;
        this.tenantService = tenantService;
        this.leaseService = leaseService;
        this.exportService = exportService;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @GetMapping("/owners")
    public ResponseEntity<byte[]> owners(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activeOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Owner", "Name", "Phone", "E-mail", "Properties", "Units", "Occupied", "Rent per month", "Active");
        List<List<String>> body = new ArrayList<>();
        for (OwnerRow r : ownerService.list(q, activeOnly)) {
            body.add(List.of(s(r.code()), s(r.name()), s(r.phone()), s(r.email()), s(r.properties()), s(r.units()), s(r.occupied()), s(r.monthlyRent()), r.active() ? "Yes" : "No"));
        }
        return file("Owners", headers, body, format);
    }

    @GetMapping("/tenants")
    public ResponseEntity<byte[]> tenants(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activeOnly, @RequestParam(required = false) Boolean withLeaseOnly,
                                          @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Tenant", "Kind", "Name", "Phone", "E-mail", "Current leases", "Units", "Active");
        List<List<String>> body = new ArrayList<>();
        for (TenantRow r : tenantService.list(q, activeOnly, withLeaseOnly)) {
            body.add(List.of(s(r.code()), s(r.kind()), s(r.name()), s(r.phone()), s(r.email()), s(r.currentLeases()), s(r.currentUnits()), r.active() ? "Yes" : "No"));
        }
        return file("Tenants", headers, body, format);
    }

    @GetMapping("/leases")
    public ResponseEntity<byte[]> leases(@RequestParam(required = false) String q, @RequestParam(required = false) LeaseStatus status, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) Long unitId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long propertyId,
                                         @RequestParam(required = false) Long ownerId, @RequestParam(required = false) Boolean expiringOnly, @RequestParam(defaultValue = "xlsx") String format) {
        List<String> headers = List.of("Lease", "Property", "Unit", "Owner", "Tenant", "Start", "End", "Days left", "Rent", "Frequency", "Per month", "Due day", "Deposit", "Status", "State");
        List<List<String>> body = new ArrayList<>();
        for (LeaseRow r : leaseService.list(q, status, state, unitId, tenantId, propertyId, ownerId, expiringOnly)) {
            body.add(List.of(s(r.code()), s(r.propertyName()), s(r.unitNo()), s(r.ownerName()), s(r.tenantName()), s(r.startDate()), s(r.endDate()), s(r.daysToEnd()), s(r.rent()),
                    s(r.frequency()), s(r.monthlyRent()), s(r.dueDay()), s(r.deposit()), s(r.status()), s(r.state())));
        }
        return file("Leases", headers, body, format);
    }

    private ResponseEntity<byte[]> file(String name, List<String> headers, List<List<String>> body, String format) {
        String title = "Property Management " + name;
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
