package com.posapp.restaurant;

import com.posapp.restaurant.dto.DiningTableDto;
import com.posapp.restaurant.dto.TableRequest;
import com.posapp.restaurant.dto.TableStatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin > Restaurant > Tables — reads open to any logged-in user (the floor/order-taking screens need them), writes admin-only, same shape as CatalogController's categories/brands. */
@RestController
@RequestMapping("/api/restaurant/tables")
public class DiningTableController {

    private final DiningTableService tableService;

    public DiningTableController(DiningTableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public List<DiningTableDto> listAll() {
        return tableService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public DiningTableDto create(@Valid @RequestBody TableRequest request) {
        return tableService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    public DiningTableDto update(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return tableService.update(id, request);
    }

    /** Manual status override (e.g. marking a table OUT_OF_SERVICE) — open to any logged-in staff, not admin-only, since front-of-house staff are the ones who'd notice a broken table. */
    @PostMapping("/{id}/status")
    public DiningTableDto setStatus(@PathVariable Long id, @Valid @RequestBody TableStatusRequest request) {
        return tableService.setStatus(id, request);
    }
}
