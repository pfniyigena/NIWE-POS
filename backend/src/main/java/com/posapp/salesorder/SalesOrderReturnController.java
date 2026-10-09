package com.posapp.salesorder;

import com.posapp.salesorder.dto.ProcessSalesOrderReturnRequest;
import com.posapp.salesorder.dto.SalesOrderReturnDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-only, same tier as Sales Orders themselves — this is a back-office flow, not a terminal one. */
@RestController
@RequestMapping("/api/sales-order-returns")
@PreAuthorize("hasRole('ADMIN')")
public class SalesOrderReturnController {

    private final SalesOrderReturnService salesOrderReturnService;

    public SalesOrderReturnController(SalesOrderReturnService salesOrderReturnService) {
        this.salesOrderReturnService = salesOrderReturnService;
    }

    /** Store-wide Sales Order Returns list screen. */
    @GetMapping
    public List<SalesOrderReturnDto> listAll() {
        return salesOrderReturnService.listAll();
    }

    @GetMapping("/{id}")
    public SalesOrderReturnDto getById(@PathVariable Long id) {
        return salesOrderReturnService.getById(id);
    }

    /** The Sale Order detail screen's own return history section. */
    @GetMapping("/order/{salesOrderId}")
    public List<SalesOrderReturnDto> listForOrder(@PathVariable Long salesOrderId) {
        return salesOrderReturnService.listForOrder(salesOrderId);
    }

    /** Keyed by SalesOrderLine id; a line with nothing returned against it is absent, not present with a zero — backs the "process a return" panel's remaining-quantity column. */
    @GetMapping("/order/{salesOrderId}/quantities")
    public Map<Long, Integer> returnedQuantitiesForOrder(@PathVariable Long salesOrderId) {
        return salesOrderReturnService.returnedQuantitiesForOrder(salesOrderId);
    }

    @PostMapping
    public SalesOrderReturnDto processReturn(@Valid @RequestBody ProcessSalesOrderReturnRequest request) {
        return salesOrderReturnService.processReturn(request);
    }
}
