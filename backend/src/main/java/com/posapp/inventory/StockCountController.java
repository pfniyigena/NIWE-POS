package com.posapp.inventory;

import com.posapp.inventory.dto.StockCountAddLineRequest;
import com.posapp.inventory.dto.StockCountDto;
import com.posapp.inventory.dto.StockCountRequest;
import com.posapp.inventory.dto.StockCountSaveRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Physical stock counts. Starting, counting and submitting are admin-only; sign-off (which posts the
 * differences as stock adjustments) is ADMIN or whoever holds {@code Permission.APPROVE_STOCK_ADJUSTMENT},
 * the same maker-checker split a single adjustment already has.
 */
@RestController
@RequestMapping("/api/stock-counts")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_VIEW')")
public class StockCountController {

    private final StockCountService service;

    public StockCountController(StockCountService service) {
        this.service = service;
    }

    @GetMapping
    public List<StockCountDto> listAll() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    public StockCountDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping
    public StockCountDto start(@Valid @RequestBody StockCountRequest request) {
        return service.start(request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PutMapping("/{id}/counts")
    public StockCountDto saveCounts(@PathVariable Long id, @RequestBody StockCountSaveRequest request) {
        return service.saveCounts(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/lines")
    public StockCountDto addLine(@PathVariable Long id, @Valid @RequestBody StockCountAddLineRequest request) {
        return service.addLine(id, request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @DeleteMapping("/{id}/lines/{lineId}")
    public StockCountDto removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.removeLine(id, lineId);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/submit")
    public StockCountDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/reopen")
    public StockCountDto reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_APPROVE_STOCK_ADJUSTMENT')")
    @PostMapping("/{id}/approve")
    public StockCountDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_INVENTORY_MANAGE')")
    @PostMapping("/{id}/cancel")
    public StockCountDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
