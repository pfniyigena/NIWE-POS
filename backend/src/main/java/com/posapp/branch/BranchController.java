package com.posapp.branch;

import com.posapp.branch.dto.BranchDto;
import com.posapp.branch.dto.BranchRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin &gt; Branches — ADMIN-only, same tier as Warehouses. See Branch's own javadoc. */
@RestController
@RequestMapping("/api/branches")
@PreAuthorize("hasRole('ADMIN')")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ANY')")
    @GetMapping
    public List<BranchDto> listAll() {
        return branchService.listAll();
    }

    /** Receipt header details for the till — open to any signed-in user, unlike the rest of this controller. */
    @GetMapping("/{id}/receipt-info")
    @PreAuthorize("isAuthenticated()")
    public com.posapp.branch.dto.BranchReceiptInfo receiptInfo(@PathVariable Long id) {
        return branchService.receiptInfo(id);
    }

    @PostMapping
    public BranchDto create(@Valid @RequestBody BranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    public BranchDto update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }

    @PostMapping("/{id}/active")
    public BranchDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return branchService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }
}
