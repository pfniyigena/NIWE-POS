package com.posapp.accounting;

import com.posapp.accounting.dto.CreateJournalEntryRequest;
import com.posapp.accounting.dto.JournalEntryDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting/journal-entries")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    public JournalEntryController(JournalEntryService journalEntryService) {
        this.journalEntryService = journalEntryService;
    }

    @GetMapping
    public List<JournalEntryDto> listAll() {
        return journalEntryService.listAll();
    }

    @GetMapping("/{id}")
    public JournalEntryDto getById(@PathVariable Long id) {
        return journalEntryService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')")
    @PostMapping
    public JournalEntryDto create(@Valid @RequestBody CreateJournalEntryRequest request) {
        return journalEntryService.createManual(request);
    }
}
