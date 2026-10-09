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
@PreAuthorize("hasRole('ADMIN')")
public class HelpDeskCategoryController {

    private final HelpDeskCategoryService service;

    public HelpDeskCategoryController(HelpDeskCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<HelpDeskCategoryDto> listAll() {
        return service.listAll();
    }

    @PostMapping
    public HelpDeskCategoryDto create(@Valid @RequestBody HelpDeskCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public HelpDeskCategoryDto update(@PathVariable Long id, @Valid @RequestBody HelpDeskCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
