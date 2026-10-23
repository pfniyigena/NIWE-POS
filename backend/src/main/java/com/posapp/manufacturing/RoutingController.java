package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.RoutingDto;
import com.posapp.manufacturing.dto.RoutingRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/routings")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<RoutingDto>> listAll(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(routingService.filtered(q, status), page, size);
    }

    @GetMapping("/active")
    public List<RoutingDto> listActive() {
        return routingService.listActive();
    }

    @GetMapping("/{id}")
    public RoutingDto getById(@PathVariable Long id) {
        return routingService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public RoutingDto create(@Valid @RequestBody RoutingRequest request) {
        return routingService.create(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public RoutingDto update(@PathVariable Long id, @Valid @RequestBody RoutingRequest request) {
        return routingService.update(id, request);
    }
}
