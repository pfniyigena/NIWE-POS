package com.posapp.realestate;

import com.posapp.realestate.RePrintService.Printed;
import com.posapp.realestate.dto.RealEstateCommissionDtos.MailedPayee;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PayoutBoard;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PostDueResult;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** {@code /api/realestate/commissions}: what each agent has earned and been paid. Operations viewers read and print; managers and admins post, and e-mail statements. */
@RestController
@RequestMapping("/api/realestate/commissions")
public class ReCommissionController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReCommissionService service;

    public ReCommissionController(ReCommissionService service) {
        this.service = service;
    }

    @GetMapping("/payouts")
    @PreAuthorize(VIEW)
    public PayoutBoard payouts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String payee,
                               @RequestParam(defaultValue = "false") boolean openOnly) {
        return service.board(from, to, payee, openOnly);
    }

    @PostMapping("/post-due")
    @PreAuthorize(MANAGE)
    public PostDueResult postDue(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String payee) {
        return service.postDue(RealEstateController.actor(me), from, to, payee);
    }

    @GetMapping("/statement")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> statement(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String payee) {
        Printed p = service.statement(from, to, payee);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    @PostMapping("/statement/email")
    @PreAuthorize(MANAGE)
    public MailedPayee emailStatement(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) String payee) {
        return service.emailStatement(RealEstateController.actor(me), from, to, payee);
    }
}
