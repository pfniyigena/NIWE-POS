package com.posapp.accounting;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.accounting.dto.CreateJournalEntryRequest;
import com.posapp.accounting.dto.JournalEntryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Listing + {@link #createManual}, an admin's own hand-entered {@link JournalEntry} (adjustments,
 * opening balances, recording an actual expense like rent or electricity — see {@code
 * AccountService}'s well-known {@code OPERATING_EXPENSES}/{@code OWNERS_EQUITY} starter accounts,
 * which exist specifically for entries like these). Every *automatic* posting from POS/Sale/
 * Inventory/Manufacturing goes through {@link AccountingService#post} instead, never through this
 * class — see that class's own javadoc.
 */
@Service
public class JournalEntryService {

    private final JournalEntryRepository repository;
    private final AccountRepository accountRepository;
    private final CostCenterRepository costCenterRepository;
    private final AuditLogService auditLogService;

    public JournalEntryService(JournalEntryRepository repository, AccountRepository accountRepository,
                                CostCenterRepository costCenterRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.costCenterRepository = costCenterRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<JournalEntryDto> listAll() {
        return repository.findAllByOrderByEntryDateDesc().stream().map(JournalEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public JournalEntryDto getById(Long id) {
        return JournalEntryDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public JournalEntryDto createManual(CreateJournalEntryRequest request) {
        if (request.getLines().size() < 2) {
            throw ApiException.badRequest("A journal entry needs at least two lines");
        }
        JournalEntry entry = new JournalEntry();
        entry.setEntryNumber("JE-" + System.currentTimeMillis());
        entry.setEntryDate(request.getEntryDate() != null
                ? request.getEntryDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                : java.time.Instant.now());
        entry.setMemo(request.getMemo());
        entry.setSourceType("MANUAL");

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (CreateJournalEntryRequest.LineEntry lineEntry : request.getLines()) {
            BigDecimal debit = lineEntry.getDebit() != null ? lineEntry.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = lineEntry.getCredit() != null ? lineEntry.getCredit() : BigDecimal.ZERO;
            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                throw ApiException.badRequest("A single line can't have both a debit and a credit — split it into two lines");
            }
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                throw ApiException.badRequest("Every line needs either a debit or a credit amount");
            }
            Account account = accountRepository.findById(lineEntry.getAccountId())
                    .orElseThrow(() -> ApiException.notFound("Account not found: " + lineEntry.getAccountId()));
            JournalEntryLine line = new JournalEntryLine();
            line.setEntry(entry);
            line.setAccount(account);
            line.setDebit(debit);
            line.setCredit(credit);
            line.setDescription(lineEntry.getDescription());
            if (lineEntry.getCostCenterId() != null) {
                CostCenter costCenter = costCenterRepository.findById(lineEntry.getCostCenterId())
                        .orElseThrow(() -> ApiException.notFound("Cost center not found: " + lineEntry.getCostCenterId()));
                line.setCostCenter(costCenter);
            }
            entry.getLines().add(line);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw ApiException.badRequest("This entry doesn't balance — total debits (" + totalDebit
                    + ") must equal total credits (" + totalCredit + ")");
        }

        JournalEntry saved = repository.save(entry);
        auditLogService.record("JOURNAL_ENTRY_CREATED",
                "Created manual journal entry " + saved.getEntryNumber() + " (" + saved.getLines().size() + " lines, " + totalDebit + ")",
                "JournalEntry", saved.getId());
        return JournalEntryDto.from(saved);
    }

    private JournalEntry getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Journal entry not found: " + id));
    }
}
