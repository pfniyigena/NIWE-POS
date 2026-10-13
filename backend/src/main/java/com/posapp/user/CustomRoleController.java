package com.posapp.user;

import com.posapp.user.dto.AccessAreaDto;
import com.posapp.user.dto.CustomRoleDto;
import com.posapp.user.dto.CustomRoleRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin > Staff > Roles — ADMIN only. */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
public class CustomRoleController {

    private final CustomRoleService service;

    public CustomRoleController(CustomRoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomRoleDto> list() {
        return service.list();
    }

    /** The area catalog the form builds its grid from. */
    @GetMapping("/areas")
    public List<AccessAreaDto> areas() {
        return service.areas();
    }

    @PostMapping
    public CustomRoleDto create(@Valid @RequestBody CustomRoleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CustomRoleDto update(@PathVariable Long id, @Valid @RequestBody CustomRoleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
