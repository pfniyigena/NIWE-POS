package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.WorkstationDto;
import com.posapp.manufacturing.dto.WorkstationRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/workstations")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class WorkstationController {

    private final WorkstationService workstationService;

    public WorkstationController(WorkstationService workstationService) {
        this.workstationService = workstationService;
    }

    @GetMapping
    public List<WorkstationDto> listAll() {
        return workstationService.listAll();
    }

    @GetMapping("/active")
    public List<WorkstationDto> listActive() {
        return workstationService.listActive();
    }

    @GetMapping("/{id}")
    public WorkstationDto getById(@PathVariable Long id) {
        return workstationService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public WorkstationDto create(@Valid @RequestBody WorkstationRequest request) {
        return workstationService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public WorkstationDto update(@PathVariable Long id, @Valid @RequestBody WorkstationRequest request) {
        return workstationService.update(id, request);
    }
}
