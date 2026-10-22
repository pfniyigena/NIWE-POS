package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.LawPaperService.Kind;
import com.posapp.law.dto.LawPrintDtos.MailedClient;
import com.posapp.law.dto.LawPrintDtos.PaperChecks;
import com.posapp.law.dto.LawPrintDtos.PaperRequest;
import com.posapp.law.dto.LawPrintDtos.Printed;
import com.posapp.law.dto.LawPrintDtos.SavedPaper;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/** The matter papers: cover sheet, engagement letter and closing letter (print, keep in the documents, e-mail). Who may do what follows the matter's access rules. */
@RestController
@RequestMapping("/api/law/matters/{id}/papers")
@PreAuthorize("isAuthenticated()")
public class LawPaperController {

    private final LawPaperService papers;

    public LawPaperController(LawPaperService papers) {
        this.papers = papers;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    private static ResponseEntity<byte[]> pdf(Printed p) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(p.fileName(), StandardCharsets.UTF_8).build().toString()).body(p.content());
    }

    @GetMapping("/checks")
    public PaperChecks checks(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return papers.checks(actor(me), id);
    }

    @PostMapping("/{kind}")
    public ResponseEntity<byte[]> print(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable String kind, @RequestBody(required = false) PaperRequest request) {
        return pdf(papers.print(actor(me), id, Kind.of(kind), request));
    }

    @PostMapping("/{kind}/save")
    public SavedPaper save(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable String kind, @RequestBody(required = false) PaperRequest request) {
        return papers.save(actor(me), id, Kind.of(kind), request);
    }

    @PostMapping("/{kind}/email")
    public MailedClient email(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable String kind, @RequestBody(required = false) PaperRequest request) {
        return papers.email(actor(me), id, Kind.of(kind), request);
    }
}
