package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.PublicHelpDeskCategoryDto;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketDto;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketRequest;
import com.posapp.helpdesk.dto.PublicHelpDeskTicketStatusDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fourier: "Add Help Desk module ... Customer login is not necessary to create a Help Desk
 * ticket. In fact, supporting both authenticated and guest ticket creation." Read (via
 * AskUserQuestion) as "staff-logged-in vs. public form," since this app has no customer-facing
 * login system at all — "authenticated" creation is {@code HelpDeskTicketController}'s own
 * ADMIN-only POST, from the admin screen; this controller is the "guest" half, the second of only
 * two unauthenticated namespaces in the whole app (the other being {@code
 * PublicReservationController}) — see {@code SecurityConfig}'s own {@code /api/public/**}
 * permitAll comment. Every endpoint here is deliberately narrow: {@link #categories} leaks only
 * id+name of active categories, {@link #create} always makes a {@code CUSTOMER}-type ticket with
 * no priority control, and {@link #lookup} requires the exact email given at creation (not just a
 * ticket number) so this can't be used to browse or enumerate anyone else's tickets.
 */
@RestController
@RequestMapping("/api/public/helpdesk")
public class PublicHelpDeskController {

    private final HelpDeskTicketService ticketService;
    private final HelpDeskCategoryService categoryService;

    public PublicHelpDeskController(HelpDeskTicketService ticketService, HelpDeskCategoryService categoryService) {
        this.ticketService = ticketService;
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<PublicHelpDeskCategoryDto> categories() {
        return categoryService.listActive().stream()
                .map(c -> new PublicHelpDeskCategoryDto(c.id(), c.name()))
                .toList();
    }

    @PostMapping("/tickets")
    public PublicHelpDeskTicketDto create(@Valid @RequestBody PublicHelpDeskTicketRequest request) {
        return ticketService.createByGuest(request);
    }

    @GetMapping("/tickets/lookup")
    public PublicHelpDeskTicketStatusDto lookup(@RequestParam String ticketNumber, @RequestParam String email) {
        return ticketService.publicLookup(ticketNumber, email);
    }
}
