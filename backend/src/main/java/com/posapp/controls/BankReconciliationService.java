package com.posapp.controls;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.JournalEntry;
import com.posapp.accounting.JournalEntryLine;
import com.posapp.accounting.JournalEntryRepository;
import com.posapp.accounting.JournalEntryService;
import com.posapp.accounting.dto.CreateJournalEntryRequest;
import com.posapp.accounting.dto.JournalEntryDto;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.controls.ControlsDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matching a bank (or mobile money) statement to the ledger. Pick an asset account and give the statement date and ending balance; tick the ledger lines that appear on the
 * statement; when what is ticked plus what earlier reconciliations already matched equals the statement balance, finish it. A line belongs to at most one reconciliation.
 * Bank charges and interest that are on the statement but not yet in the books are posted from here and ticked in one step.
 */
@Service
public class BankReconciliationService {

    private static final int MAX_ROWS = 2000;

    private final BankReconciliationRepository repository;
    private final BankReconciliationLineRepository tickRepository;
    private final LedgerLineRepository ledger;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryService journalEntryService;
    private final AuditLogService auditLogService;

    public BankReconciliationService(BankReconciliationRepository repository, BankReconciliationLineRepository tickRepository, LedgerLineRepository ledger,
                                     AccountRepository accountRepository, JournalEntryRepository journalEntryRepository, JournalEntryService journalEntryService,
                                     AuditLogService auditLogService) {
        this.repository = repository;
        this.tickRepository = tickRepository;
        this.ledger = ledger;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalEntryService = journalEntryService;
        this.auditLogService = auditLogService;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant startOf(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static LocalDate dayOf(Instant i) {
        return i.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private BankReconciliation get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Reconciliation not found."));
    }

    private BankReconciliation getDraft(Long id) {
        BankReconciliation r = get(id);
        if (!"DRAFT".equals(r.getStatus())) throw ApiException.conflict("This reconciliation is already finished.");
        return r;
    }

    private Account account(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    private BigDecimal differenceOf(BankReconciliation r) {
        BigDecimal cleared = money(ledger.clearedBefore(r.getAccountId(), r.getStatementDate(), r.getId())).add(money(ledger.tickedDebit(r.getId()))).subtract(money(ledger.tickedCredit(r.getId())));
        return money(r.getStatementBalance()).subtract(cleared);
    }

    private ReconRow row(BankReconciliation r) {
        Account a = account(r.getAccountId());
        boolean canUndo = "DONE".equals(r.getStatus()) && repository.findFirstByAccountIdAndStatusOrderByStatementDateDescIdDesc(r.getAccountId(), "DONE")
                .map(last -> last.getId().equals(r.getId())).orElse(false);
        BigDecimal diff = differenceOf(r);
        return new ReconRow(r.getId(), "REC-" + String.format("%05d", r.getId()), a.getId(), a.getCode(), a.getName(), r.getStatementDate(), money(r.getStatementBalance()), r.getStatus(),
                (int) tickRepository.countByReconciliationId(r.getId()), diff, r.getNote(), r.getCreatedBy(), r.getCompletedBy(), r.getCompletedAt(), canUndo);
    }

    /** The accounts that can be reconciled: active cash, bank and mobile money accounts. */
    @Transactional(readOnly = true)
    public List<MoneyAccount> moneyAccounts() {
        return accountRepository.findByActiveTrueAndTypeOrderByCodeAsc(AccountType.ASSET).stream()
                .filter(a -> a.getWellKnownKey() == null || AccountService.CASH.equals(a.getWellKnownKey()) || AccountService.TRUST_BANK.equals(a.getWellKnownKey()))
                .map(a -> new MoneyAccount(a.getId(), a.getCode(), a.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<ReconRow> list() {
        return repository.findAllByOrderByStatementDateDescIdDesc().stream().map(this::row).toList();
    }

    @Transactional
    public ReconDetail start(ReconStart request, String by) {
        if (request.accountId() == null) throw ApiException.badRequest("Choose the bank account.");
        Account a = account(request.accountId());
        boolean moneyAccount = a.getWellKnownKey() == null || AccountService.CASH.equals(a.getWellKnownKey()) || AccountService.TRUST_BANK.equals(a.getWellKnownKey());
        if (a.getType() != AccountType.ASSET || !a.isActive() || !moneyAccount) {
            throw ApiException.badRequest("Only an active cash, bank or mobile money account can be reconciled. Add your bank as an asset account in the chart of accounts.");
        }
        if (request.statementDate() == null) throw ApiException.badRequest("The statement date is required.");
        if (request.statementDate().isAfter(LocalDate.now(ZoneOffset.UTC))) throw ApiException.badRequest("The statement date cannot be in the future.");
        if (request.statementBalance() == null) throw ApiException.badRequest("The statement ending balance is required.");
        if (repository.findFirstByAccountIdAndStatus(a.getId(), "DRAFT").isPresent()) {
            throw ApiException.conflict("There is already an unfinished reconciliation for " + a.getName() + ". Finish or cancel it first.");
        }
        repository.findFirstByAccountIdAndStatusOrderByStatementDateDescIdDesc(a.getId(), "DONE").ifPresent(last -> {
            if (!request.statementDate().isAfter(last.getStatementDate())) {
                throw ApiException.badRequest("The last finished statement for " + a.getName() + " was " + last.getStatementDate() + ". This one must be later.");
            }
        });
        BankReconciliation r = new BankReconciliation();
        r.setAccountId(a.getId());
        r.setStatementDate(request.statementDate());
        r.setStatementBalance(money(request.statementBalance()));
        r.setNote(trim(request.note()));
        r.setCreatedBy(by);
        BankReconciliation saved = repository.save(r);
        auditLogService.record("BANK_RECONCILIATION_STARTED", "Started reconciling " + a.getName() + " to the statement of " + saved.getStatementDate(), "BankReconciliation", saved.getId());
        return detail(saved.getId(), null);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional
    public ReconDetail update(Long id, ReconUpdate request) {
        BankReconciliation r = getDraft(id);
        if (request.statementDate() != null) {
            if (request.statementDate().isAfter(LocalDate.now(ZoneOffset.UTC))) throw ApiException.badRequest("The statement date cannot be in the future.");
            repository.findFirstByAccountIdAndStatusOrderByStatementDateDescIdDesc(r.getAccountId(), "DONE").ifPresent(last -> {
                if (!request.statementDate().isAfter(last.getStatementDate())) throw ApiException.badRequest("The last finished statement was " + last.getStatementDate() + ". This one must be later.");
            });
            // Lines already ticked must still be on or before the new date.
            List<Long> ticked = ledger.tickedIds(r.getId());
            if (!ticked.isEmpty() && ledger.withEntries(ticked).stream().anyMatch(l -> dayOf(l.getEntry().getEntryDate()).isAfter(request.statementDate()))) {
                throw ApiException.badRequest("Some ticked lines are dated after that date. Untick them first.");
            }
            r.setStatementDate(request.statementDate());
        }
        if (request.statementBalance() != null) r.setStatementBalance(money(request.statementBalance()));
        if (request.note() != null) r.setNote(trim(request.note()));
        repository.save(r);
        return detail(id, null);
    }

    @Transactional(readOnly = true)
    public ReconDetail detail(Long id, LocalDate from) {
        BankReconciliation r = get(id);
        ReconRow header = row(r);
        BigDecimal before = money(ledger.clearedBefore(r.getAccountId(), r.getStatementDate(), r.getId()));
        BigDecimal dep = money(ledger.tickedDebit(r.getId()));
        BigDecimal wd = money(ledger.tickedCredit(r.getId()));
        BigDecimal cleared = before.add(dep).subtract(wd);
        List<LineRow> rows = new ArrayList<>();
        boolean truncated = false;
        BigDecimal ledgerBalance = null;
        if ("DRAFT".equals(r.getStatus())) {
            Set<Long> ticked = new HashSet<>(ledger.tickedIds(r.getId()));
            Instant before2 = startOf(r.getStatementDate().plusDays(1));
            Instant lower = from == null ? Instant.EPOCH : startOf(from);
            List<JournalEntryLine> lines = ledger.candidates(r.getAccountId(), lower, before2, r.getId(), PageRequest.of(0, MAX_ROWS + 1));
            if (lines.size() > MAX_ROWS) {
                truncated = true;
                lines = lines.subList(0, MAX_ROWS);
            }
            for (JournalEntryLine l : lines) rows.add(lineRow(l, ticked.contains(l.getId())));
        } else {
            List<Long> ids = ledger.tickedIds(r.getId());
            if (!ids.isEmpty()) {
                List<JournalEntryLine> lines = new ArrayList<>(ledger.withEntries(ids));
                lines.sort((a, b) -> a.getEntry().getEntryDate().compareTo(b.getEntry().getEntryDate()));
                for (JournalEntryLine l : lines) rows.add(lineRow(l, true));
            }
        }
        BigDecimal diff = money(r.getStatementBalance()).subtract(cleared);
        return new ReconDetail(header, before, dep, wd, cleared, diff, ledgerBalance, rows, truncated);
    }

    private LineRow lineRow(JournalEntryLine l, boolean ticked) {
        JournalEntry e = l.getEntry();
        return new LineRow(l.getId(), dayOf(e.getEntryDate()), e.getEntryNumber(), e.getMemo(), l.getDescription(), money(l.getDebit()), money(l.getCredit()), ticked);
    }

    /** Ticks or unticks the given ledger lines. */
    @Transactional
    public ReconDetail tick(Long id, TickRequest request) {
        BankReconciliation r = getDraft(id);
        List<Long> ids = request.lineIds() == null ? List.of() : request.lineIds().stream().distinct().toList();
        if (ids.isEmpty()) return detail(id, null);
        if (!request.ticked()) {
            tickRepository.deleteSome(r.getId(), ids);
            return detail(id, null);
        }
        List<JournalEntryLine> lines = ledger.withEntries(ids);
        if (lines.size() != ids.size()) throw ApiException.notFound("One of those ledger lines does not exist.");
        Set<Long> already = new HashSet<>(ledger.tickedIds(r.getId()));
        Set<Long> takenElsewhere = new HashSet<>();
        tickRepository.findByJournalLineIdIn(ids).forEach(t -> { if (!t.getReconciliationId().equals(r.getId())) takenElsewhere.add(t.getJournalLineId()); });
        for (JournalEntryLine l : lines) {
            if (!l.getAccount().getId().equals(r.getAccountId())) throw ApiException.badRequest("That line belongs to a different account.");
            if (dayOf(l.getEntry().getEntryDate()).isAfter(r.getStatementDate())) throw ApiException.badRequest("That line is dated after the statement date (" + r.getStatementDate() + ").");
            if (takenElsewhere.contains(l.getId())) throw ApiException.conflict("That line was already matched in an earlier reconciliation.");
            if (already.contains(l.getId())) continue;
            BankReconciliationLine t = new BankReconciliationLine();
            t.setReconciliationId(r.getId());
            t.setJournalLineId(l.getId());
            tickRepository.save(t);
        }
        return detail(id, null);
    }

    /** Ticks or unticks every candidate line between two dates (default: all). */
    @Transactional
    public ReconDetail tickAll(Long id, TickAllRequest request) {
        BankReconciliation r = getDraft(id);
        Instant lower = request.from() == null ? Instant.EPOCH : startOf(request.from());
        LocalDate upTo = request.to() == null || request.to().isAfter(r.getStatementDate()) ? r.getStatementDate() : request.to();
        List<JournalEntryLine> lines = ledger.candidates(r.getAccountId(), lower, startOf(upTo.plusDays(1)), r.getId(), PageRequest.of(0, 100000));
        List<Long> ids = lines.stream().map(JournalEntryLine::getId).toList();
        return tick(id, new TickRequest(ids, request.ticked()));
    }

    /** Posts a bank charge, interest or other item found on the statement but missing from the books, and ticks it. */
    @Transactional
    public ReconDetail adjustment(Long id, AdjustmentRequest request) {
        BankReconciliation r = getDraft(id);
        if (request.otherAccountId() == null) throw ApiException.badRequest("Choose the account for the other side (for example Bank charges or Interest income).");
        Account bank = account(r.getAccountId());
        Account other = account(request.otherAccountId());
        if (other.getId().equals(bank.getId())) throw ApiException.badRequest("The other side must be a different account.");
        if (!other.isActive()) throw ApiException.badRequest("That account is not active.");
        if (request.amount() == null || request.amount().signum() <= 0) throw ApiException.badRequest("The amount must be more than zero.");
        if (request.amount().stripTrailingZeros().scale() > 2) throw ApiException.badRequest("The amount can have at most two decimals.");
        boolean out = "OUT".equalsIgnoreCase(request.direction());
        if (!out && !"IN".equalsIgnoreCase(request.direction())) throw ApiException.badRequest("Direction must be OUT (money left the account, such as a bank charge) or IN (such as interest).");
        LocalDate date = request.date() != null ? request.date() : r.getStatementDate();
        if (date.isAfter(r.getStatementDate())) throw ApiException.badRequest("The date cannot be after the statement date.");
        BigDecimal amount = money(request.amount());
        CreateJournalEntryRequest je = new CreateJournalEntryRequest();
        je.setEntryDate(date);
        je.setMemo(trim(request.memo()) != null ? request.memo().trim() : (out ? "Bank charge" : "Bank interest") + " per statement of " + r.getStatementDate());
        CreateJournalEntryRequest.LineEntry bankLine = new CreateJournalEntryRequest.LineEntry();
        bankLine.setAccountId(bank.getId());
        CreateJournalEntryRequest.LineEntry otherLine = new CreateJournalEntryRequest.LineEntry();
        otherLine.setAccountId(other.getId());
        if (out) {
            bankLine.setCredit(amount);
            otherLine.setDebit(amount);
        } else {
            bankLine.setDebit(amount);
            otherLine.setCredit(amount);
        }
        je.setLines(List.of(bankLine, otherLine));
        JournalEntryDto created = journalEntryService.create(je, "BANK_ADJUSTMENT");
        Long bankLineId = created.lines().stream().filter(l -> bank.getId().equals(l.accountId())).map(JournalEntryDto.LineDto::id).findFirst().orElseThrow();
        BankReconciliationLine t = new BankReconciliationLine();
        t.setReconciliationId(r.getId());
        t.setJournalLineId(bankLineId);
        tickRepository.save(t);
        auditLogService.record("BANK_RECONCILIATION_ADJUSTMENT", (out ? "Charge " : "Interest ") + amount + " posted against " + other.getName() + " while reconciling " + bank.getName(),
                "BankReconciliation", r.getId());
        return detail(id, null);
    }

    @Transactional
    public ReconDetail finish(Long id, String by) {
        BankReconciliation r = getDraft(id);
        BigDecimal diff = differenceOf(r);
        if (diff.signum() != 0) {
            throw ApiException.badRequest("The difference is " + diff.toPlainString() + ", not zero. Tick the missing lines or post what is on the statement but not in the books.");
        }
        r.setStatus("DONE");
        r.setCompletedBy(by);
        r.setCompletedAt(Instant.now());
        repository.save(r);
        auditLogService.record("BANK_RECONCILIATION_FINISHED", "Finished reconciling " + account(r.getAccountId()).getName() + " to the statement of " + r.getStatementDate()
                + " (" + tickRepository.countByReconciliationId(r.getId()) + " lines)", "BankReconciliation", r.getId());
        return detail(id, null);
    }

    /** Cancels an unfinished reconciliation; the lines go back to unmatched. */
    @Transactional
    public void cancel(Long id) {
        BankReconciliation r = getDraft(id);
        tickRepository.deleteAllFor(r.getId());
        repository.delete(r);
        auditLogService.record("BANK_RECONCILIATION_CANCELLED", "Cancelled the unfinished reconciliation of the statement dated " + r.getStatementDate(), "BankReconciliation", id);
    }

    /** Takes back the most recent finished reconciliation of an account (its lines become unmatched). */
    @Transactional
    public void undo(Long id) {
        BankReconciliation r = get(id);
        if (!"DONE".equals(r.getStatus())) throw ApiException.conflict("Only a finished reconciliation can be undone. Use Cancel for an unfinished one.");
        BankReconciliation last = repository.findFirstByAccountIdAndStatusOrderByStatementDateDescIdDesc(r.getAccountId(), "DONE").orElse(null);
        if (last == null || !last.getId().equals(r.getId())) throw ApiException.conflict("Only the latest finished reconciliation of an account can be undone. Undo the later one first.");
        tickRepository.deleteAllFor(r.getId());
        repository.delete(r);
        auditLogService.record("BANK_RECONCILIATION_UNDONE", "Undid the reconciliation of " + account(r.getAccountId()).getName() + " to the statement of " + r.getStatementDate(), "BankReconciliation", id);
    }
}
