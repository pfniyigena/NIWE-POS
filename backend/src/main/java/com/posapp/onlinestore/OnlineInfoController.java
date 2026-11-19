package com.posapp.onlinestore;

import com.posapp.common.ListPaging;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.onlinestore.OnlineStoreDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The shop's information pages and contact form (v1.51.0): what the public reads and sends, and what staff edit and answer. */
@RestController
public class OnlineInfoController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final OnlineInfoService info;
    private final OnlineContactService contact;
    private final ModuleService moduleService;

    public OnlineInfoController(OnlineInfoService info, OnlineContactService contact, ModuleService moduleService) {
        this.info = info;
        this.contact = contact;
        this.moduleService = moduleService;
    }

    // ---------------------------------------------------------------- public (no login)

    /** The published information pages, for the footer. */
    @GetMapping("/api/public/store/info")
    public List<InfoLink> links(@RequestParam(defaultValue = "en") String lang) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return info.links(lang);
    }

    @GetMapping("/api/public/store/info/{slug}")
    public InfoPageDto page(@PathVariable String slug, @RequestParam(defaultValue = "en") String lang) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        return info.page(slug, lang);
    }

    @PostMapping("/api/public/store/contact")
    public Map<String, Object> send(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        moduleService.requireEnabled(ModuleKey.ONLINE_STORE);
        contact.send(request, http.getRemoteAddr());
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------- staff

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/info-pages")
    public List<InfoPageAdminDto> adminPages(@RequestParam(defaultValue = "en") String lang) {
        return info.adminPages(lang);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/api/online-store/info-pages/{slug}")
    public InfoPageAdminDto savePage(@PathVariable String slug, @Valid @RequestBody InfoPageRequest request, @RequestParam(defaultValue = "en") String lang) {
        return info.save(slug, request, lang);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/info-pages/{slug}/restore")
    public InfoPageAdminDto restorePage(@PathVariable String slug, @RequestParam(defaultValue = "en") String lang) {
        return info.restore(slug, lang);
    }

    @PreAuthorize(VIEW)
    @GetMapping("/api/online-store/messages")
    public ResponseEntity<List<ContactMessageDto>> messages(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(contact.list(status, q), page, size);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/messages/{id}/answered")
    public ContactMessageDto answered(@PathVariable Long id, @Valid @RequestBody(required = false) ContactNoteRequest request) {
        return contact.answered(id, request == null ? null : request.note());
    }

    /** Sends the written answer to the customer's e-mail address and marks the message answered. */
    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/messages/{id}/reply")
    public ContactMessageDto reply(@PathVariable Long id, @Valid @RequestBody ContactReplyRequest request) {
        return contact.reply(id, request.text());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/api/online-store/messages/{id}/reopen")
    public ContactMessageDto reopen(@PathVariable Long id) {
        return contact.reopen(id);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/api/online-store/messages/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contact.delete(id);
        return ResponseEntity.noContent().build();
    }
}
