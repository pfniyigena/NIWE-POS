package com.posapp.propertymgmt;

import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EmailAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.EmailStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.GenerateAllStatementsRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.GenerateStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.PayStatementRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.StatementAdjustmentRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtRequests.StatementNotesRequest;
import com.posapp.propertymgmt.dto.PropertyMgmtStatementDtos.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** {@code /api/propertymgmt/statements}: owner statements. Operations viewers read and print; Operations managers and admins make, agree and pay them. */
@RestController
@RequestMapping("/api/propertymgmt/statements")
public class PmStatementController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final PmStatementService service;
    private final PmStatementFiles files;
    private final PmStatementMailService mail;
    private final PmStatementAuto auto;

    public PmStatementController(PmStatementService service, PmStatementFiles files, PmStatementMailService mail, PmStatementAuto auto) {
        this.auto = auto;
        this.service = service;
        this.files = files;
        this.mail = mail;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<List<StatementRow>> list(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) StatementStatus status,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "false") boolean unpaidOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PmPaging.of(service.list(ownerId, status, from, to, unpaidOnly), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public StatementDto get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.get(PropertyManagementController.actor(me), id);
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public StatementDto generate(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody GenerateStatementRequest r) {
        return service.generate(PropertyManagementController.actor(me), r);
    }

    @PostMapping("/generate-all")
    @PreAuthorize(MANAGE)
    public GenerateAllResult generateAll(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody GenerateAllStatementsRequest r) {
        return service.generateAll(PropertyManagementController.actor(me), r);
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize(MANAGE)
    public StatementDto refresh(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.refresh(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(MANAGE)
    public StatementDto addLine(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody StatementAdjustmentRequest r) {
        return service.addAdjustment(PropertyManagementController.actor(me), id, r);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize(MANAGE)
    public StatementDto removeLine(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long lineId) {
        return service.removeAdjustment(PropertyManagementController.actor(me), id, lineId);
    }

    @PutMapping("/{id}/notes")
    @PreAuthorize(MANAGE)
    public StatementDto notes(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody StatementNotesRequest r) {
        return service.notes(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize(MANAGE)
    public StatementDto finalizeStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.finalizeStatement(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(MANAGE)
    public StatementDto reopen(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.reopen(PropertyManagementController.actor(me), id);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize(MANAGE)
    public StatementDto pay(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PayStatementRequest r) {
        return service.pay(PropertyManagementController.actor(me), id, r);
    }

    @PostMapping("/{id}/unpay")
    @PreAuthorize(MANAGE)
    public StatementDto unpay(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.unpay(PropertyManagementController.actor(me), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** The statement as a PDF or Excel file to send to the owner. */
    @GetMapping("/{id}/export")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestParam(defaultValue = "pdf") String format) {
        StatementDto d = service.get(PropertyManagementController.actor(me), id);
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        byte[] content = files.build(d, xlsx);
        return ResponseEntity.ok()
                .contentType(xlsx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder("attachment").filename(files.filename(d.statement(), xlsx)).build().toString())
                .body(content);
    }

    /** Runs the monthly statement run now: with {@code force} whatever the schedule says, otherwise only if it is due. Administrators only. */
    @PostMapping("/auto-run")
    @PreAuthorize("hasRole('ADMIN')")
    public AutoRunResult autoRun(@RequestParam(defaultValue = "true") boolean force) {
        return auto.run(java.time.LocalDate.now(), force);
    }

    /** E-mails every agreed statement that has not been sent yet. */
    @PostMapping("/email-all")
    @PreAuthorize(MANAGE)
    public EmailAllResult emailAll(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody(required = false) EmailAllStatementsRequest r) {
        return mail.emailAll(PropertyManagementController.actor(me), r);
    }

    /** E-mails the PDF to the owner (or to the address given). Agreed or paid statements only. */
    @PostMapping("/{id}/email")
    @PreAuthorize(MANAGE)
    public StatementDto email(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) EmailStatementRequest r) {
        return mail.email(PropertyManagementController.actor(me), id, r);
    }
}
