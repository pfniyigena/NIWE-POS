package com.posapp.manufacturing;

import com.posapp.manufacturing.dto.ComponentAvailabilityDto;
import com.posapp.manufacturing.dto.CreateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.UpdateManufacturingOrderRequest;
import com.posapp.manufacturing.dto.ManufacturingOrderDto;
import com.posapp.manufacturing.dto.ProduceManufacturingOrderRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/orders")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW')")
public class ManufacturingOrderController {

    private final ManufacturingOrderService manufacturingOrderService;

    private final ManufacturingPrintService printService;

    public ManufacturingOrderController(ManufacturingOrderService manufacturingOrderService, ManufacturingPrintService printService) {
        this.manufacturingOrderService = manufacturingOrderService;
        this.printService = printService;
    }

    @GetMapping
    public org.springframework.http.ResponseEntity<List<ManufacturingOrderDto>> listAll(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from, @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return com.posapp.common.ListPaging.of(manufacturingOrderService.filtered(q, status, productId, from, to), page, size);
    }

    @GetMapping("/{id}")
    public ManufacturingOrderDto getById(@PathVariable Long id) {
        return manufacturingOrderService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping
    public ManufacturingOrderDto create(@Valid @RequestBody CreateManufacturingOrderRequest request) {
        return manufacturingOrderService.create(request);
    }

    @GetMapping("/{id}/job-card")
    public org.springframework.http.ResponseEntity<byte[]> jobCard(@PathVariable Long id) {
        ManufacturingPrintService.Printed p = printService.jobCard(id);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(p.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(p.content());
    }

    @GetMapping("/{id}/availability")
    public ComponentAvailabilityDto availability(@PathVariable Long id) {
        return manufacturingOrderService.availability(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PutMapping("/{id}")
    public ManufacturingOrderDto update(@PathVariable Long id, @Valid @RequestBody UpdateManufacturingOrderRequest request) {
        return manufacturingOrderService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/copy")
    public ManufacturingOrderDto copy(@PathVariable Long id) {
        return manufacturingOrderService.copy(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/confirm")
    public ManufacturingOrderDto confirm(@PathVariable Long id) {
        return manufacturingOrderService.confirm(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/produce")
    public ManufacturingOrderDto produce(@PathVariable Long id, @Valid @RequestBody ProduceManufacturingOrderRequest request) {
        return manufacturingOrderService.produce(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')")
    @PostMapping("/{id}/cancel")
    public ManufacturingOrderDto cancel(@PathVariable Long id) {
        return manufacturingOrderService.cancel(id);
    }
}
