package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.RoutingDto;
import com.posapp.manufacturing.dto.RoutingRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/routings")
@PreAuthorize("hasRole('ADMIN')")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping
    public List<RoutingDto> listAll() {
        return routingService.listAll();
    }

    @GetMapping("/active")
    public List<RoutingDto> listActive() {
        return routingService.listActive();
    }

    @GetMapping("/{id}")
    public RoutingDto getById(@PathVariable Long id) {
        return routingService.getById(id);
    }

    @PostMapping
    public RoutingDto create(@Valid @RequestBody RoutingRequest request) {
        return routingService.create(request);
    }

    @PutMapping("/{id}")
    public RoutingDto update(@PathVariable Long id, @Valid @RequestBody RoutingRequest request) {
        return routingService.update(id, request);
    }
}
