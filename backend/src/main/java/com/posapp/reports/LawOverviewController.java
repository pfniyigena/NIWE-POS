package com.posapp.reports;

import com.posapp.law.LawAccess.Actor;
import com.posapp.reports.dto.LawOverviewDto.Overview;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

/** {@code GET /api/law/overview}: the partner's first page. Anyone who can open the Law Firm module may call it; they only see their own matters. */
@RestController
@RequestMapping("/api/law/overview")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')")
public class LawOverviewController {

    private final LawOverviewService service;

    public LawOverviewController(LawOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public Overview overview(@AuthenticationPrincipal AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return service.overview(new Actor(me.getUser(), admin, viewer, manager));
    }
}
