package com.posapp.collections;

import com.posapp.collections.CollectionDtos.CustomersPage;
import com.posapp.collections.CollectionDtos.ItemsPage;
import com.posapp.collections.CollectionDtos.LogRow;
import com.posapp.collections.CollectionDtos.MailedStatement;
import com.posapp.collections.CollectionDtos.Printed;
import com.posapp.collections.CollectionDtos.RemindRequest;
import com.posapp.collections.CollectionDtos.RemindResult;
import com.posapp.collections.CollectionDtos.ReminderItem;
import com.posapp.collections.CollectionDtos.SettingsDto;
import com.posapp.collections.CollectionDtos.SettingsRequest;
import com.posapp.security.AppUserPrincipal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /api/collections}: what customers owe, how late it is, their statements and the reminder e-mails. Seeing needs the Billing or Sales area (or admin);
 * sending e-mail needs Billing or Sales management (or admin); the automatic-run settings are admin only.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_VIEW') or hasAuthority('AREA_SALES_VIEW')";
    private static final String SEND = "hasRole('ADMIN') or hasAuthority('AREA_BILLING_MANAGE') or hasAuthority('AREA_SALES_MANAGE')";

    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    private static boolean canSend(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return auth.contains("ROLE_ADMIN") || auth.contains("AREA_BILLING_MANAGE") || auth.contains("AREA_SALES_MANAGE");
    }

    private static String by(AppUserPrincipal me) {
        return me.getUsername();
    }

    /** The unpaid items (overdue ones by default), worst first. */
    @PreAuthorize(VIEW)
    @GetMapping
    public ItemsPage items(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) String bucket,
                           @RequestParam(required = false) Long customerId, @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q,
                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.items(canSend(me), overdueOnly, bucket, customerId, branchId, q, page, size);
    }

    /** The same debts, one row per customer. */
    @PreAuthorize(VIEW)
    @GetMapping("/customers")
    public CustomersPage customers(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "true") boolean overdueOnly, @RequestParam(required = false) Long branchId,
                                   @RequestParam(required = false) String q, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.customers(canSend(me), overdueOnly, branchId, q, page, size);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/customers/{customerId}/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long customerId) {
        Printed p = service.statement(customerId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    @PreAuthorize(SEND)
    @PostMapping("/customers/{customerId}/statement/email")
    public MailedStatement emailStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long customerId) {
        return service.emailStatement(customerId, by(me));
    }

    @PreAuthorize(SEND)
    @PostMapping("/remind")
    public RemindResult remind(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody RemindRequest request) {
        return service.remind(request == null ? null : request.customerIds(), by(me));
    }

    /** Everything mailed so far, newest first; {@code customerId} narrows it to one customer. */
    @PreAuthorize(VIEW)
    @GetMapping("/log")
    public List<LogRow> log(@RequestParam(required = false) Long customerId) {
        return service.history(customerId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return service.updateSettings(request);
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
        return service.runNow(by(me));
    }
}
