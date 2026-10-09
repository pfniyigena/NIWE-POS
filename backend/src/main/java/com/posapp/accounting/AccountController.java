package com.posapp.accounting;

import com.posapp.accounting.dto.AccountDto;
import com.posapp.accounting.dto.AccountRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDto> listAll() {
        return accountService.listAll();
    }

    @GetMapping("/active")
    public List<AccountDto> listActive() {
        return accountService.listActive();
    }

    /** Admin &gt; Accounting &gt; Expenses/Incomes &gt; New's own category picker — active accounts of one type only. */
    @GetMapping("/active/by-type/{type}")
    public List<AccountDto> listActiveByType(@PathVariable AccountType type) {
        return accountService.listActiveByType(type);
    }

    @GetMapping("/{id}")
    public AccountDto getById(@PathVariable Long id) {
        return accountService.getById(id);
    }

    @PostMapping
    public AccountDto create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request);
    }

    @PutMapping("/{id}")
    public AccountDto update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }
}
