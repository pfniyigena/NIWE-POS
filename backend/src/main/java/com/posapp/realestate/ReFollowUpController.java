package com.posapp.realestate;

import com.posapp.realestate.dto.RealEstateDtos.LeadDto;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.Board;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.DigestResult;
import com.posapp.realestate.dto.RealEstateFollowUpDtos.FollowUpRequest;
import com.posapp.realestate.dto.RealEstateReminderDtos.RemindOne;
import com.posapp.realestate.dto.RealEstateReminderDtos.ReminderResult;
import com.posapp.security.AppUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/realestate}: the follow-up board, moving a lead's next call, and the morning e-mail (preview or send now: admins). */
@RestController
@RequestMapping("/api/realestate")
public class ReFollowUpController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReFollowUpService service;
    private final ReLeadService leadService;
    private final ReViewingReminderService reminderService;

    public ReFollowUpController(ReFollowUpService service, ReLeadService leadService, ReViewingReminderService reminderService) {
        this.service = service;
        this.leadService = leadService;
        this.reminderService = reminderService;
    }

    @GetMapping("/follow-ups")
    @PreAuthorize(VIEW)
    public Board board(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) Long agentId, @RequestParam(defaultValue = "false") boolean all,
                       @RequestParam(required = false) Integer days) {
        return service.board(RealEstateController.actor(me), agentId, all, days);
    }

    /** Moves the next call of a lead to another day (today or later), or clears it. Does not count as contact. */
    @PutMapping("/leads/{id}/follow-up")
    @PreAuthorize(MANAGE)
    public LeadDto setFollowUp(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody FollowUpRequest r) {
        return leadService.setFollowUp(RealEstateController.actor(me), id, r.nextFollowUp());
    }

    @PostMapping("/follow-ups/send")
    @PreAuthorize("hasRole('ADMIN')")
    public DigestResult send(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean preview) {
        return service.run(RealEstateAccess.name(me.getUser()), preview);
    }

    /** Sends the lead's reminder for one viewing now (manager). */
    @PostMapping("/viewings/{id}/remind")
    @PreAuthorize(MANAGE)
    public RemindOne remind(@PathVariable Long id) {
        return reminderService.remindClient(id);
    }

    /** The viewing reminder run: preview it or send it at once (admin). */
    @PostMapping("/viewings/reminders/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ReminderResult sendReminders(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(defaultValue = "false") boolean preview) {
        return reminderService.run(RealEstateAccess.name(me.getUser()), preview);
    }
}
