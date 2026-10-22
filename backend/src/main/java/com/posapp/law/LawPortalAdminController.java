package com.posapp.law;

import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawPortalDtos.AdminDto;
import com.posapp.law.dto.LawPortalDtos.InviteResultDto;
import com.posapp.law.dto.LawPortalRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/** The firm's side of the client portal, per matter. Who may do what follows the matter's access rules (see {@link LawPortalAdminService}). */
@RestController
@RequestMapping("/api/law")
@PreAuthorize("isAuthenticated()")
public class LawPortalAdminController {

    private final LawPortalAdminService service;

    public LawPortalAdminController(LawPortalAdminService service) {
        this.service = service;
    }

    private static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    @GetMapping("/matters/{id}/portal")
    public AdminDto view(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.view(actor(me), id);
    }

    @PutMapping("/matters/{id}/portal")
    public AdminDto settings(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody SettingsRequest r) {
        return service.saveSettings(actor(me), id, r);
    }

    @PutMapping("/matters/{id}/portal/update")
    public AdminDto update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody UpdateRequest r) {
        return service.postUpdate(actor(me), id, r);
    }

    @PutMapping("/deadlines/{id}/portal")
    public AdminDto deadline(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody VisibleRequest r) {
        return service.setDeadlineVisible(actor(me), id, r);
    }

    @PostMapping("/matters/{id}/portal/accounts")
    public InviteResultDto invite(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody InviteRequest r) {
        return service.invite(actor(me), id, r);
    }

    @PostMapping("/matters/{id}/portal/accounts/{accountId}/invite")
    public InviteResultDto reinvite(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long accountId) {
        return service.reinvite(actor(me), id, accountId);
    }

    @PutMapping("/matters/{id}/portal/accounts/{accountId}/active")
    public AdminDto active(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long accountId, @RequestBody ActiveRequest r) {
        return service.setActive(actor(me), id, accountId, r);
    }

    @PostMapping("/matters/{id}/portal/messages")
    public AdminDto reply(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody MessageRequest r) {
        return service.reply(actor(me), id, r);
    }

    @PostMapping("/matters/{id}/portal/messages/read")
    public AdminDto read(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.markRead(actor(me), id);
    }
}
