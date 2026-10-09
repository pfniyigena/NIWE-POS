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
@PreAuthorize("hasRole('ADMIN')")
public class ReorderController {

    private final ReorderService service;

    public ReorderController(ReorderService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReorderSuggestionDto> suggestions() {
        return service.suggestions();
    }

    @PostMapping("/requisition")
    public PurchaseRequisitionDto createRequisition(@Valid @RequestBody ReorderRequisitionRequest request) {
        return service.createRequisition(request);
    }
}
