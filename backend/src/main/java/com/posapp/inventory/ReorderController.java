package com.posapp.inventory;

import com.posapp.inventory.dto.PurchaseRequisitionDto;
import com.posapp.inventory.dto.ReorderRequisitionRequest;
import com.posapp.inventory.dto.ReorderSuggestionDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reorder")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class ReorderController {

    private final ReorderService service;

    public ReorderController(ReorderService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReorderSuggestionDto> suggestions() {
        return service.suggestions();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/requisition")
    public PurchaseRequisitionDto createRequisition(@Valid @RequestBody ReorderRequisitionRequest request) {
        return service.createRequisition(request);
    }
}
