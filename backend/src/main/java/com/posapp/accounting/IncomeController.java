package com.posapp.accounting;

import com.posapp.accounting.dto.IncomeDto;
import com.posapp.accounting.dto.IncomeRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/incomes")
@PreAuthorize("hasRole('ADMIN')")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public List<IncomeDto> listAll() {
        return incomeService.listAll();
    }

    @GetMapping("/{id}")
    public IncomeDto getById(@PathVariable Long id) {
        return incomeService.getById(id);
    }

    @PostMapping
    public IncomeDto create(@Valid @RequestBody IncomeRequest request) {
        return incomeService.create(request);
    }
}
