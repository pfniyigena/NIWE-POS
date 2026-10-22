package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.law.dto.LawTrustDtos.*;
import com.posapp.law.dto.LawTrustRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The client money (trust) account. Each matter's ledger follows the matter's own access rules (see {@link LawAccess}); the overview needs
 * Operations (view); the reconciliation needs Billing (manage). Who may approve a payout is decided by the service (lead lawyer or admin,
 * never the person who asked).
 */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawTrustController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final LawTrustService service;
    private final LawTrustPaperService papers;

    public LawTrustController(LawTrustService service, LawTrustPaperService papers) {
        this.service = service;
        this.papers = papers;
    }

    private static ResponseEntity<byte[]> pdf(Printed p) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    private static Set<String> auth(AppUserPrincipal me) {
        return me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    private static boolean canBill(AppUserPrincipal me) {
        Set<String> auth = auth(me);
        return auth.contains("ROLE_ADMIN") || auth.contains("AREA_BILLING_MANAGE");
    }

    @PreAuthorize(VIEW)
    @GetMapping("/trust/overview")
    public TrustOverviewDto overview(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.overview(actor(me), canBill(me));
    }

    @GetMapping("/matters/{id}/trust")
    public MatterTrustDto matter(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.matter(actor(me), canBill(me), id);
    }

    @PostMapping("/matters/{id}/trust/deposit")
    public MatterTrustDto deposit(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TrustDepositRequest request) {
        return service.deposit(actor(me), canBill(me), id, request);
    }

    @PostMapping("/matters/{id}/trust/payout")
    public MatterTrustDto payout(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TrustPayoutRequest request) {
        return service.requestPayout(actor(me), canBill(me), id, request);
    }

    @PostMapping("/matters/{id}/trust/fee-transfer")
    public MatterTrustDto feeTransfer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody TrustFeeTransferRequest request) {
        return service.requestFeeTransfer(actor(me), canBill(me), id, request);
    }

    @PostMapping("/trust/{txId}/approve")
    public MatterTrustDto approve(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txId, @RequestBody(required = false) TrustNoteRequest request) {
        return service.approve(actor(me), canBill(me), txId, request);
    }

    @PostMapping("/trust/{txId}/reject")
    public MatterTrustDto reject(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txId, @RequestBody TrustNoteRequest request) {
        return service.reject(actor(me), canBill(me), txId, request);
    }

    @PostMapping("/trust/{txId}/cancel")
    public MatterTrustDto cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txId) {
        return service.cancel(actor(me), canBill(me), txId);
    }

    @PostMapping("/trust/{txId}/reverse")
    public MatterTrustDto reverse(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txId, @RequestBody TrustReverseRequest request) {
        return service.reverseDeposit(actor(me), canBill(me), txId, request);
    }

    @GetMapping("/trust/reconciliation")
    public ReconciliationView reconciliation(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.reconciliation(canBill(me));
    }

    @PostMapping("/trust/reconciliation")
    public ReconciliationView reconcile(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody TrustReconcileRequest request) {
        return service.reconcile(actor(me), canBill(me), request);
    }

    // ---- papers

    /** The receipt for money received, or the payment voucher for money paid out or taken as fees. Only posted entries. */
    @GetMapping("/trust/{txId}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long txId) {
        return pdf(papers.receipt(actor(me), txId));
    }

    /** The client's trust ledger for the matter, for a period (whole history when no dates are given). */
    @GetMapping("/matters/{id}/trust/statement")
    public ResponseEntity<byte[]> statement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return pdf(papers.statement(actor(me), id, from, to));
    }

    @PostMapping("/matters/{id}/trust/statement/email")
    public MailedClient emailStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return papers.emailStatement(actor(me), canBill(me), id);
    }

    @GetMapping("/trust/reconciliation/{id}/print")
    public ResponseEntity<byte[]> reconciliationSheet(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return pdf(papers.reconciliation(canBill(me), id));
    }
}
