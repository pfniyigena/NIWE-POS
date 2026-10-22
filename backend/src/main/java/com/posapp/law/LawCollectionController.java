package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawCollectionDtos.CollectionsDto;
import com.posapp.law.dto.LawCollectionDtos.RemindRequest;
import com.posapp.law.dto.LawCollectionDtos.RemindResult;
import com.posapp.law.dto.LawCollectionDtos.ReminderItem;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Fee collections: unpaid invoices, a client's fee statement and the reminder e-mails. Seeing follows the Operations area; sending needs billing, a manager or an admin. */
@RestController
@RequestMapping("/api/law/collections")
public class LawCollectionController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final LawCollectionService service;

    public LawCollectionController(LawCollectionService service) {
        this.service = service;
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
    @GetMapping
    public CollectionsDto list(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) String q,
                               @RequestParam(required = false) Long clientId) {
        return service.list(actor(me), canBill(me), overdueOnly, q, clientId);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/clients/{clientId}/statement")
    public ResponseEntity<byte[]> statement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long clientId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Printed p = service.statement(actor(me), clientId, from, to);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    @PreAuthorize(VIEW)
    @PostMapping("/clients/{clientId}/statement/email")
    public MailedClient emailStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long clientId) {
        return service.emailStatement(actor(me), canBill(me), clientId);
    }

    @PreAuthorize(VIEW)
    @PostMapping("/remind")
    public RemindResult remind(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody RemindRequest request) {
        return service.remind(actor(me), canBill(me), request);
    }

    /** Who the automatic run would remind now. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/reminders")
    public List<ReminderItem> preview() {
        return service.preview();
    }

    /** The automatic run, started by hand. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reminders/run")
    public RemindResult run(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.runNow(actor(me), canBill(me));
    }
}
