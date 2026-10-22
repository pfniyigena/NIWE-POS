package com.posapp.hr;

import com.posapp.common.ListPaging;
import com.posapp.hr.dto.HrDtos.ContractDto;
import com.posapp.hr.dto.HrRequests.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Employment contracts. Salary data, so it needs the Payroll area (not just HR). */
@RestController
@RequestMapping("/api/hr/contracts")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
public class ContractController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_MANAGE')";

    private final ContractService service;
    private final EmployeeRepository employeeRepository;

    public ContractController(ContractService service, EmployeeRepository employeeRepository) {
        this.service = service;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public ResponseEntity<List<ContractDto>> list(@RequestParam(required = false) ContractStatus status, @RequestParam(required = false) Long employeeId,
                                                  @RequestParam(required = false) ContractType type, @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long branchId,
                                                  @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Map<Long, Long> branches = new HashMap<>();
        if (branchId != null) {
            employeeRepository.findAll().forEach(e -> branches.put(e.getId(), e.getBranchId()));
        }
        List<ContractDto> rows = service.list(status, employeeId).stream()
                .filter(c -> type == null || c.type() == type)
                .filter(c -> ListPaging.has(q, c.contractNumber(), c.employeeName(), c.employeeNumber(), c.jobTitle()))
                .filter(c -> branchId == null || branchId.equals(branches.get(c.employeeId())))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    @GetMapping("/expiring")
    public List<ContractDto> expiring(@RequestParam(defaultValue = "60") int days) {
        return service.expiring(days);
    }

    @GetMapping("/{id}")
    public ContractDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public ContractDto create(@Valid @RequestBody ContractRequest request) {
        return service.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public ContractDto update(@PathVariable Long id, @Valid @RequestBody ContractRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/activate")
    public ContractDto activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/terminate")
    public ContractDto terminate(@PathVariable Long id, @Valid @RequestBody ContractTerminateRequest request) {
        return service.terminate(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/renew")
    public ContractDto renew(@PathVariable Long id, @Valid @RequestBody RenewRequest request) {
        return service.renew(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
