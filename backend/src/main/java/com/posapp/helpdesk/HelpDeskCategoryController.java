package com.posapp.helpdesk;

import com.posapp.helpdesk.dto.HelpDeskCategoryDto;
import com.posapp.helpdesk.dto.HelpDeskCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Help Desk > Categories — ADMIN-only, same tier as every other back-office module. */
@RestController
@RequestMapping("/api/helpdesk/categories")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class HelpDeskCategoryController {

    private final HelpDeskCategoryService service;

    public HelpDeskCategoryController(HelpDeskCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<HelpDeskCategoryDto> listAll() {
        return service.listAll();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public HelpDeskCategoryDto create(@Valid @RequestBody HelpDeskCategoryRequest request) {
        return service.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public HelpDeskCategoryDto update(@PathVariable Long id, @Valid @RequestBody HelpDeskCategoryRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
