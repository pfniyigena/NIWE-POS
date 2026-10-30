package com.posapp.helpdesk;

import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
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

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    private final ModuleService moduleService;
    private final HelpDeskSettingsService settingsService;

    public PublicHelpDeskController(HelpDeskTicketService ticketService, HelpDeskCategoryService categoryService, ModuleService moduleService,
                                    HelpDeskSettingsService settingsService) {
        this.settingsService = settingsService;
        this.moduleService = moduleService;
        this.ticketService = ticketService;
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<PublicHelpDeskCategoryDto> categories() {
        open();
        return categoryService.listActive().stream()
                .map(c -> new PublicHelpDeskCategoryDto(c.id(), c.name()))
                .toList();
    }

    @PostMapping("/tickets")
    public PublicHelpDeskTicketDto create(@Valid @RequestBody PublicHelpDeskTicketRequest request) {
        open();
        return ticketService.createByGuest(request);
    }

    @GetMapping("/tickets/lookup")
    public PublicHelpDeskTicketStatusDto lookup(@RequestParam String ticketNumber, @RequestParam String email) {
        open();
        return ticketService.publicLookup(ticketNumber, email);
    }

    /** The requester answers; it reopens a resolved ticket and puts a waiting one back to work. */
    @PostMapping("/tickets/reply")
    public PublicHelpDeskTicketStatusDto reply(@RequestBody Map<String, String> request) {
        open();
        return ticketService.publicReply(request.get("ticketNumber"), request.get("email"), request.get("body"));
    }

    @PostMapping("/tickets/rate")
    public PublicHelpDeskTicketStatusDto rate(@RequestBody Map<String, Object> request) {
        open();
        Object r = request.get("rating");
        int rating = r instanceof Number n ? n.intValue() : -1;
        Object c = request.get("comment");
        return ticketService.publicRate(String.valueOf(request.get("ticketNumber")), String.valueOf(request.get("email")), rating, c == null ? null : c.toString());
    }

    @PostMapping(value = "/tickets/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PublicHelpDeskTicketStatusDto addFile(@RequestParam String ticketNumber, @RequestParam String email, @RequestParam("file") MultipartFile file) throws IOException {
        open();
        ticketService.publicAddFile(ticketNumber, email, file);
        return ticketService.publicLookup(ticketNumber, email);
    }

    @GetMapping("/tickets/attachments/{id}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @RequestParam String ticketNumber, @RequestParam String email) {
        open();
        HelpDeskAttachmentService.FileData f = ticketService.publicFile(ticketNumber, email, id);
        boolean inline = f.contentType().startsWith("image/") || f.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.data());
    }

    /** The module must be on and "Allow public website" switched on in the Help Desk settings; otherwise the page answers "not found". */
    private void open() {
        moduleService.requireEnabled(ModuleKey.HELPDESK);
        if (!settingsService.isPublicOpen()) {
            throw com.posapp.common.ApiException.notFound("The Help Desk website is not available.");
        }
    }
}
