package com.posapp.hr;

import com.posapp.hr.dto.HrDtos.ContractDto;
import com.posapp.hr.dto.HrRequests.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Employment contracts. Salary data, so it needs the Payroll area (not just HR). */
@RestController
@RequestMapping("/api/hr/contracts")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_VIEW')")
public class ContractController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_PAYROLL_MANAGE')";

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContractDto> list(@RequestParam(required = false) ContractStatus status, @RequestParam(required = false) Long employeeId) {
        return service.list(status, employeeId);
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
