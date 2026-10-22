package com.posapp.controls;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountService;
import com.posapp.accounting.JournalEntry;
import com.posapp.accounting.JournalEntryLine;
import com.posapp.accounting.JournalEntryRepository;
import com.posapp.accounting.JournalEntryService;
import com.posapp.accounting.dto.CreateJournalEntryRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.controls.ControlsDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The ledger as it stood on the day the business started using the system: cash and bank, equipment, loans, owner's equity and so on, posted as ONE balanced entry
 * (source OPENING_BALANCES). Receivables and payables are refused here, because they are the sum of customer and supplier balances and come in through those imports (so each
 * customer and supplier has their own opening balance). Only one such entry can stand at a time; to correct it, reverse it (an equal and opposite entry on the same date,
 * source OPENING_BALANCES_REVERSAL) and record it again.
 */
@Service
public class LedgerOpeningService {

    public static final String SOURCE = "OPENING_BALANCES";
    public static final String REVERSAL = "OPENING_BALANCES_REVERSAL";

    private final AccountRepository accountRepository;
    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final OpeningEntryRepository entries;
    private final AuditLogService auditLogService;

    public LedgerOpeningService(AccountRepository accountRepository, JournalEntryService journalEntryService, JournalEntryRepository journalEntryRepository,
                                OpeningEntryRepository entries, AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.journalEntryService = journalEntryService;
        this.journalEntryRepository = journalEntryRepository;
        this.entries = entries;
        this.auditLogService = auditLogService;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private JournalEntry standing() {
        for (JournalEntry e : entries.findBySourceTypeOrderByIdDesc(SOURCE)) {
            if (entries.findFirstBySourceTypeAndSourceId(REVERSAL, e.getId()).isEmpty()) return e;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public OpeningState state() {
        JournalEntry e = standing();
        if (e == null) return new OpeningState(false, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        List<OpeningLineView> lines = new ArrayList<>();
        BigDecimal d = BigDecimal.ZERO, c = BigDecimal.ZERO;
        for (JournalEntryLine l : e.getLines()) {
            lines.add(new OpeningLineView(l.getAccount().getId(), l.getAccount().getCode(), l.getAccount().getName(), money(l.getDebit()), money(l.getCredit())));
            d = d.add(l.getDebit());
            c = c.add(l.getCredit());
        }
        return new OpeningState(true, e.getId(), e.getEntryNumber(), e.getEntryDate().atZone(ZoneOffset.UTC).toLocalDate(), money(d), money(c), lines);
    }

    /** Checks the request and returns the lines as they would be posted (including the Owner's Equity balancing line when asked for). Throws on the first problem. */
    private CreateJournalEntryRequest build(OpeningRequest request) {
        if (request.asOf() == null) throw ApiException.badRequest("The opening date is required (the day you started using the system).");
        if (request.asOf().isAfter(LocalDate.now(ZoneOffset.UTC))) throw ApiException.badRequest("The opening date cannot be in the future.");
        if (request.lines() == null || request.lines().isEmpty()) throw ApiException.badRequest("Add at least one account.");
        if (standing() != null) throw ApiException.conflict("Opening balances are already recorded. Reverse them first if they were wrong.");
        Set<Long> seen = new HashSet<>();
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        List<CreateJournalEntryRequest.LineEntry> out = new ArrayList<>();
        for (OpeningLine l : request.lines()) {
            if (l.accountId() == null) throw ApiException.badRequest("Every line needs an account.");
            Account a = accountRepository.findById(l.accountId()).orElseThrow(() -> ApiException.notFound("Account not found: " + l.accountId()));
            if (!a.isActive()) throw ApiException.badRequest(a.getCode() + " " + a.getName() + " is not active.");
            if (AccountService.ACCOUNTS_RECEIVABLE.equals(a.getWellKnownKey()) || AccountService.ACCOUNTS_PAYABLE.equals(a.getWellKnownKey())) {
                throw ApiException.badRequest(a.getName() + " comes from customer and supplier balances. Import those on the Data screen (What customers owe you / What you owe suppliers) instead.");
            }
            if (!seen.add(a.getId())) throw ApiException.badRequest(a.getCode() + " " + a.getName() + " appears twice. Put it on one line.");
            BigDecimal d = l.debit() == null ? BigDecimal.ZERO : l.debit();
            BigDecimal c = l.credit() == null ? BigDecimal.ZERO : l.credit();
            if (d.signum() < 0 || c.signum() < 0) throw ApiException.badRequest("Amounts cannot be negative (" + a.getName() + ").");
            if (d.signum() > 0 && c.signum() > 0) throw ApiException.badRequest(a.getName() + " has both a debit and a credit. Use one of them.");
            if (d.signum() == 0 && c.signum() == 0) throw ApiException.badRequest(a.getName() + " has no amount.");
            if (d.stripTrailingZeros().scale() > 2 || c.stripTrailingZeros().scale() > 2) throw ApiException.badRequest(a.getName() + ": at most two decimals.");
            CreateJournalEntryRequest.LineEntry le = new CreateJournalEntryRequest.LineEntry();
            le.setAccountId(a.getId());
            le.setDebit(money(d));
            le.setCredit(money(c));
            le.setDescription("Opening balance");
            out.add(le);
            debit = debit.add(d);
            credit = credit.add(c);
        }
        BigDecimal diff = debit.subtract(credit);
        if (diff.signum() != 0) {
            if (!request.balanceToEquity()) {
                throw ApiException.badRequest("Debits (" + money(debit).toPlainString() + ") and credits (" + money(credit).toPlainString() + ") differ by " + money(diff.abs()).toPlainString()
                        + ". Fix the amounts, or choose to put the difference in Owner's Equity.");
            }
            Account equity = accountRepository.findByWellKnownKey(AccountService.OWNERS_EQUITY).orElseThrow(() -> ApiException.conflict("The chart has no Owner's Equity account."));
            if (seen.contains(equity.getId())) throw ApiException.badRequest("Owner's Equity is already on a line, so it cannot also take the difference.");
            CreateJournalEntryRequest.LineEntry le = new CreateJournalEntryRequest.LineEntry();
            le.setAccountId(equity.getId());
            le.setDescription("Opening balance - balancing figure");
            if (diff.signum() > 0) le.setCredit(money(diff)); else le.setDebit(money(diff.abs()));
            out.add(le);
        }
        CreateJournalEntryRequest je = new CreateJournalEntryRequest();
        je.setEntryDate(request.asOf());
        je.setMemo(request.memo() == null || request.memo().isBlank() ? "Opening balances" : request.memo().trim());
        je.setLines(out);
        return je;
    }

    /** Checks only: throws with the reason, or returns normally. Used by the CSV import, so a refusal must not mark the import's transaction rollback-only. */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public CreateJournalEntryRequest validate(OpeningRequest request) {
        return build(request);
    }

    @Transactional
    public OpeningState record(OpeningRequest request) {
        CreateJournalEntryRequest je = build(request);
        var created = journalEntryService.create(je, SOURCE);
        auditLogService.record("OPENING_BALANCES_RECORDED", "Recorded opening balances as of " + request.asOf() + " (" + created.lines().size() + " lines)", "JournalEntry", created.id());
        return state();
    }

    /** Posts the equal and opposite entry on the same date so opening balances can be entered again. */
    @Transactional
    public OpeningState reverse() {
        JournalEntry e = standing();
        if (e == null) throw ApiException.conflict("There are no opening balances to reverse.");
        CreateJournalEntryRequest je = new CreateJournalEntryRequest();
        je.setEntryDate(e.getEntryDate().atZone(ZoneOffset.UTC).toLocalDate());
        je.setMemo("Reversal of opening balances " + e.getEntryNumber());
        List<CreateJournalEntryRequest.LineEntry> lines = new ArrayList<>();
        for (JournalEntryLine l : e.getLines()) {
            CreateJournalEntryRequest.LineEntry le = new CreateJournalEntryRequest.LineEntry();
            le.setAccountId(l.getAccount().getId());
            le.setDebit(l.getCredit());
            le.setCredit(l.getDebit());
            le.setDescription("Reversal of opening balance");
            lines.add(le);
        }
        je.setLines(lines);
        var created = journalEntryService.create(je, REVERSAL);
        JournalEntry reversal = journalEntryRepository.findById(created.id()).orElseThrow();
        reversal.setSourceId(e.getId());
        journalEntryRepository.save(reversal);
        auditLogService.record("OPENING_BALANCES_REVERSED", "Reversed opening balances " + e.getEntryNumber(), "JournalEntry", created.id());
        return state();
    }
}
