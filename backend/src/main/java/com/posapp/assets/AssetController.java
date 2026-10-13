package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.*;
import com.posapp.assets.dto.AssetRequests.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything under /api/assets (the Assets Management module). Reading needs the Accounting area; changing things needs manage. */
@RestController
@RequestMapping("/api/assets")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class AssetController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')";

    private final FixedAssetService service;
    private final AssetDepreciationService depreciationService;
    private final AssetPurchaseService purchaseService;

    public AssetController(FixedAssetService service, AssetDepreciationService depreciationService, AssetPurchaseService purchaseService) {
        this.service = service;
        this.purchaseService = purchaseService;
        this.depreciationService = depreciationService;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return service.listCategories();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @GetMapping("/summary")
    public SummaryDto summary(@RequestParam(required = false) Long branchId) {
        return service.summary(branchId);
    }

    @GetMapping
    public List<AssetDto> list(@RequestParam(required = false) AssetStatus status, @RequestParam(required = false) Long branchId) {
        return service.list(status, branchId);
    }

    /** Received purchase order lines that can be turned into assets. */
    @GetMapping("/purchase-lines")
    public List<PurchaseLineDto> purchaseLines() {
        return purchaseService.purchaseLines();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/from-purchase")
    public List<AssetDto> fromPurchase(@Valid @RequestBody FromPurchaseRequest request) {
        return purchaseService.register(request);
    }

    @GetMapping("/{id}")
    public AssetDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/depreciation")
    public List<DepreciationEntryDto> depreciation(@PathVariable Long id) {
        return service.depreciationOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public AssetDto create(@Valid @RequestBody AssetRequest request) {
        return service.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public AssetDto update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/transfers")
    public List<TransferDto> transfers(@PathVariable Long id) {
        return service.transfersOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/transfer")
    public AssetDto transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return service.transfer(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/dispose")
    public AssetDto dispose(@PathVariable Long id, @Valid @RequestBody DisposeRequest request) {
        return service.dispose(id, request);
    }

    /** Charges the monthly depreciation of every active asset up to the date (default: end of last month). */
    @PreAuthorize(MANAGE)
    @PostMapping("/depreciation/run")
    public DepreciationRunResult runDepreciation(@RequestBody(required = false) RunDepreciationRequest request) {
        return depreciationService.run(request != null ? request.upTo() : null);
    }
}
