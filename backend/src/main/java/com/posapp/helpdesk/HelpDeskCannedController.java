package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskCannedDto;
import com.posapp.helpdesk.dto.HelpDeskCannedRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin > Help Desk > Saved replies. */
@RestController
@RequestMapping("/api/helpdesk/canned")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class HelpDeskCannedController {

    private final HelpDeskCannedService service;

    public HelpDeskCannedController(HelpDeskCannedService service) {
        this.service = service;
    }

    @GetMapping
    public List<HelpDeskCannedDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}/render")
    public Map<String, String> render(@PathVariable Long id, @RequestParam Long ticketId) {
        return Map.of("body", service.render(id, ticketId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public HelpDeskCannedDto create(@Valid @RequestBody HelpDeskCannedRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public HelpDeskCannedDto update(@PathVariable Long id, @Valid @RequestBody HelpDeskCannedRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
