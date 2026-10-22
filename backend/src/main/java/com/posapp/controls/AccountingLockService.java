package com.posapp.controls;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * "The books are closed up to" date. Once set, no ledger entry dated on or before it can be posted, by any document type or by hand, because
 * {@code AccountingService.post} and {@code JournalEntryService} both ask {@link #assertOpen} first. Voids and corrections still work: they post today, after the date.
 * Only an admin changes the date, it must be before today, and every change is logged with a reason.
 */
@Service
public class AccountingLockService {

    private final AccountingLockRepository repository;
    private final AccountingLockLogRepository logRepository;
    private final AuditLogService auditLogService;

    public AccountingLockService(AccountingLockRepository repository, AccountingLockLogRepository logRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.logRepository = logRepository;
        this.auditLogService = auditLogService;
    }

    public record LogLine(LocalDate previousDate, LocalDate newDate, String changedBy, String reason, Instant at) {}

    public record LockState(LocalDate lockDate, String updatedBy, Instant updatedAt, List<LogLine> history) {}

    @Transactional(readOnly = true)
    public LocalDate lockDate() {
        return repository.findAll().stream().findFirst().map(AccountingLock::getLockDate).orElse(null);
    }

    /** Refuses a posting dated on or before the closing date. A null date means "now". */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public void assertOpen(Instant entryDate) {
        LocalDate lock = lockDate();
        if (lock == null) return;
        LocalDate day = (entryDate != null ? entryDate : Instant.now()).atZone(ZoneOffset.UTC).toLocalDate();
        if (!day.isAfter(lock)) {
            throw ApiException.badRequest("The books are closed up to " + lock + ", so nothing can be recorded on " + day
                    + ". Use a later date, or ask an admin to reopen the books (Accounting > Controls).");
        }
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public void assertOpen(LocalDate date) {
        assertOpen(date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Transactional(readOnly = true)
    public LockState state() {
        AccountingLock row = repository.findAll().stream().findFirst().orElse(null);
        List<LogLine> history = logRepository.findTop20ByOrderByIdDesc().stream()
                .map(l -> new LogLine(l.getPreviousDate(), l.getNewDate(), l.getChangedBy(), l.getReason(), l.getCreatedAt())).toList();
        return new LockState(row == null ? null : row.getLockDate(), row == null ? null : row.getUpdatedBy(), row == null ? null : row.getUpdatedAt(), history);
    }

    /** Sets, moves or clears (null) the closing date. */
    @Transactional
    public LockState set(LocalDate newDate, String reason, String by) {
        if (newDate != null && !newDate.isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw ApiException.badRequest("The books can only be closed up to a day before today, so today's work is never blocked.");
        }
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("Say why (for example: March accounts approved).");
        AccountingLock row = repository.findAll().stream().findFirst().orElseGet(AccountingLock::new);
        LocalDate previous = row.getLockDate();
        if (java.util.Objects.equals(previous, newDate)) throw ApiException.badRequest("The books are already " + (newDate == null ? "open." : "closed up to " + newDate + "."));
        row.setLockDate(newDate);
        row.setUpdatedBy(by);
        repository.save(row);
        AccountingLockLog log = new AccountingLockLog();
        log.setPreviousDate(previous);
        log.setNewDate(newDate);
        log.setChangedBy(by);
        log.setReason(reason.trim());
        logRepository.save(log);
        auditLogService.record("ACCOUNTING_LOCK_CHANGED",
                (newDate == null ? "Reopened the books" : "Closed the books up to " + newDate) + (previous == null ? "" : " (was " + previous + ")") + ": " + reason.trim());
        return state();
    }
}
