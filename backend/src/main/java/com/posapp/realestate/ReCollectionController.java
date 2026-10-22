package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateCollectionDtos.CollectionBoard;
import com.posapp.realestate.dto.RealEstateCollectionDtos.MailedBuyer;
import com.posapp.realestate.dto.RealEstateCollectionDtos.SaleNoteDto;
import com.posapp.realestate.dto.RealEstateCollectionDtos.SaleNoteRequest;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code /api/realestate}: collecting what buyers owe on sold units. Operations viewers read; managers and admins write notes and e-mail buyers. */
@RestController
@RequestMapping("/api/realestate")
public class ReCollectionController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReCollectionService service;

    public ReCollectionController(ReCollectionService service) {
        this.service = service;
    }

    @GetMapping("/collections")
    @PreAuthorize(VIEW)
    public CollectionBoard board(@RequestParam(required = false) Long projectId, @RequestParam(required = false) Long agentId, @RequestParam(required = false) Integer days) {
        return service.board(projectId, agentId, days);
    }

    @GetMapping("/unit-sales/{id}/collection-notes")
    @PreAuthorize(VIEW)
    public List<SaleNoteDto> notes(@PathVariable Long id) {
        return service.notes(id);
    }

    @PostMapping("/unit-sales/{id}/collection-notes")
    @PreAuthorize(MANAGE)
    public SaleNoteDto addNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody SaleNoteRequest r) {
        return service.addNote(RealEstateController.actor(me), id, r);
    }

    @PostMapping("/unit-sales/{id}/remind")
    @PreAuthorize(MANAGE)
    public MailedBuyer remind(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.remind(RealEstateController.actor(me), id);
    }

    @PostMapping("/unit-sales/{id}/statement/email")
    @PreAuthorize(MANAGE)
    public MailedBuyer emailStatement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return service.emailStatement(RealEstateController.actor(me), id);
    }
}
