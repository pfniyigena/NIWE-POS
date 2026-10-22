package com.posapp.propertymgmt;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.JournalEntry;
import com.posapp.accounting.JournalEntryLine;
import com.posapp.accounting.JournalEntryRepository;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Books owner statements to the general ledger. Rent is the owner's money, not the agency's: rent invoices are booked as sales revenue when they are issued, so when a statement
 * is agreed the rent collected is taken back out of revenue, the agency keeps its management fee as income, and what is left is owed to the owner (Owners' Funds Payable). When
 * the owner is paid, the debt is settled from cash. Reopening a statement or undoing its payment posts the opposite entry. Done inside the caller's transaction, so a statement
 * is never agreed or paid without its entries.
 *
 * <pre>
 * Agreed:  Dr Sales revenue (rent collected)
 *            Cr Management fee income (fee, less any manual additions, plus any manual deductions)
 *            Cr Tax payable (VAT on the fee, when charged)
 *            Cr Repairs and maintenance expense (repairs recharged to the owner)
 *            Cr Owners' funds payable (net to the owner; a debit when the net is negative)
 * Paid:    Dr Owners' funds payable / Cr Cash   (net, when positive)
 * </pre>
 */
@Service
public class PmLedgerService {

    public static final String OWNER_FUNDS_PAYABLE = "OWNER_FUNDS_PAYABLE";
    public static final String MANAGEMENT_FEE_INCOME = "MANAGEMENT_FEE_INCOME";
    public static final String TENANT_DEPOSITS_HELD = "TENANT_DEPOSITS_HELD";
    public static final String TENANT_CHARGES_RECOVERED = "TENANT_CHARGES_RECOVERED";

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final JournalEntryRepository journalRepository;
    private final AuditLogService auditLogService;
    private final com.posapp.controls.AccountingLockService lockService;

    public PmLedgerService(AccountRepository accountRepository, AccountService accountService, JournalEntryRepository journalRepository, AuditLogService auditLogService,
                           com.posapp.controls.AccountingLockService lockService) {
        this.lockService = lockService;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.journalRepository = journalRepository;
        this.auditLogService = auditLogService;
    }

    private record Spec(Account account, BigDecimal amount, boolean debit, String description) {
    }

    /** The account for a well-known key; the two accounts this module owns are created on first use. */
    private Account account(String key) {
        if (accountRepository.count() == 0) accountService.seedDefaultChartIfNeeded();
        Account existing = accountRepository.findByWellKnownKey(key).orElse(null);
        if (existing != null) return existing;
        if (OWNER_FUNDS_PAYABLE.equals(key)) return create(key, 2880, "Owners' Funds Payable", AccountType.LIABILITY, "Property Management: rent collected for owners and not yet paid out to them. Owed back to the owners.");
        if (MANAGEMENT_FEE_INCOME.equals(key)) return create(key, 4300, "Management Fee Income", AccountType.REVENUE, "Property Management: the agency's fee on the rent it collects for owners, and other charges taken on owner statements.");
        if (TENANT_DEPOSITS_HELD.equals(key)) return create(key, 2890, "Tenant Security Deposits", AccountType.LIABILITY, "Property Management: security deposits held for tenants. Owed back to the tenants when their leases end, less what is kept.");
        if (TENANT_CHARGES_RECOVERED.equals(key)) return create(key, 4310, "Tenant Charges Recovered", AccountType.REVENUE, "Property Management: damage, cleaning, utility and other charges kept from tenants' security deposits.");
        throw ApiException.conflict("The chart of accounts has no account for " + key + ". Open Accounting > Chart of accounts, then try again.");
    }

    private Account create(String key, int firstCode, String name, AccountType type, String description) {
        int code = firstCode;
        while (accountRepository.existsByCodeIgnoreCase(String.valueOf(code))) code++;
        Account a = new Account();
        a.setCode(String.valueOf(code));
        a.setName(name);
        a.setType(type);
        a.setDescription(description);
        a.setSystemAccount(true);
        a.setWellKnownKey(key);
        return accountRepository.save(a);
    }

    private void add(List<Spec> lines, String key, BigDecimal amount, boolean normallyDebit, boolean reverse, String description) {
        if (amount == null || amount.signum() == 0) return;
        boolean debit = amount.signum() > 0 ? normallyDebit : !normallyDebit;
        if (reverse) debit = !debit;
        lines.add(new Spec(account(key), amount.abs(), debit, description));
    }

    private void book(String sourceType, Long sourceId, String memo, java.time.Instant when, List<Spec> specs) {
        if (specs.isEmpty()) return;
        lockService.assertOpen(when);
        JournalEntry entry = new JournalEntry();
        entry.setEntryNumber("JE-" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet());
        entry.setEntryDate(when);
        entry.setMemo(memo);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        for (Spec s : specs) {
            JournalEntryLine line = new JournalEntryLine();
            line.setEntry(entry);
            line.setAccount(s.account());
            line.setDebit(s.debit() ? s.amount() : BigDecimal.ZERO);
            line.setCredit(s.debit() ? BigDecimal.ZERO : s.amount());
            line.setDescription(s.description());
            entry.getLines().add(line);
        }
        JournalEntry saved = journalRepository.save(entry);
        auditLogService.record("JOURNAL_ENTRY_POSTED", memo, "JournalEntry", saved.getId());
    }

    /** The statement was agreed ({@code reverse} = it was reopened). */
    @Transactional
    public void agreement(PmOwnerStatement st, String ownerName, boolean reverse) {
        BigDecimal rent = st.getRentCollected(), fee = st.getManagementFee(), vat = st.getVatAmount(), repairs = st.getRepairs(), adj = st.getAdjustments(), net = st.getNetPayable();
        List<Spec> lines = new ArrayList<>();
        String who = " — " + ownerName + " — " + st.getPeriodFrom() + " to " + st.getPeriodTo();
        add(lines, "SALES_REVENUE", rent, true, reverse, "Rent collected for the owner");
        add(lines, MANAGEMENT_FEE_INCOME, fee.subtract(adj), false, reverse, "Management fee");
        add(lines, "TAX_PAYABLE", vat, false, reverse, "VAT on the management fee");
        add(lines, "REPAIRS_MAINTENANCE_EXPENSE", repairs, false, reverse, "Repairs recharged to the owner");
        add(lines, OWNER_FUNDS_PAYABLE, net, false, reverse, "Owed to the owner");
        book(reverse ? "PM_STATEMENT_REVERSAL" : "PM_STATEMENT", st.getId(), (reverse ? "Reopened owner statement " : "Owner statement ") + st.getCode() + who, java.time.Instant.now(), lines);
    }

    /** The owner was paid ({@code reverse} = the payment was undone). Only a positive net moves money. */
    @Transactional
    public void payout(PmOwnerStatement st, String ownerName, LocalDate date, boolean reverse) {
        if (st.getNetPayable().signum() <= 0) return;
        List<Spec> lines = new ArrayList<>();
        add(lines, OWNER_FUNDS_PAYABLE, st.getNetPayable(), true, reverse, "Paid to the owner");
        add(lines, "CASH", st.getNetPayable(), false, reverse, "Paid to the owner");
        java.time.Instant when = date == null ? java.time.Instant.now() : date.atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant();
        book(reverse ? "PM_STATEMENT_PAYOUT_REVERSAL" : "PM_STATEMENT_PAYOUT", st.getId(),
                (reverse ? "Undid payment of owner statement " : "Paid owner statement ") + st.getCode() + " — " + ownerName, reverse ? java.time.Instant.now() : when, lines);
    }

    /**
     * A movement of a security deposit ({@code reverse} = it was voided). Transfers between two leases of the same tenant move nothing in the books.
     *
     * <pre>
     * Received:  Dr Cash / Cr Tenant security deposits
     * Refunded:  Dr Tenant security deposits / Cr Cash
     * Kept for unpaid rent: the rent invoice was paid through the normal payment (Dr Cash / Cr Receivables), so here Dr Tenant security deposits / Cr Cash
     * Kept for anything else: Dr Tenant security deposits / Cr Tenant charges recovered
     * </pre>
     */
    @Transactional
    public void deposit(PmDepositTxn t, String leaseCode, String tenantName, boolean reverse) {
        List<Spec> lines = new ArrayList<>();
        BigDecimal amount = t.getAmount();
        String what = leaseCode + " — " + tenantName;
        switch (t.getKind()) {
            case RECEIVED -> {
                add(lines, "CASH", amount, true, reverse, "Deposit received");
                add(lines, TENANT_DEPOSITS_HELD, amount, false, reverse, "Deposit received");
            }
            case REFUND -> {
                add(lines, TENANT_DEPOSITS_HELD, amount, true, reverse, "Deposit refunded");
                add(lines, "CASH", amount, false, reverse, "Deposit refunded");
            }
            case DEDUCTION -> {
                add(lines, TENANT_DEPOSITS_HELD, amount, true, reverse, "Deposit kept");
                if (t.getReason() == PmDepositReason.UNPAID_RENT) add(lines, "CASH", amount, false, reverse, "Applied to rent invoice");
                else add(lines, TENANT_CHARGES_RECOVERED, amount, false, reverse, "Kept for " + t.getReason().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
            }
            default -> {
                return;
            }
        }
        java.time.Instant when = reverse || t.getTxnDate() == null ? java.time.Instant.now() : t.getTxnDate().atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant();
        String verb = switch (t.getKind()) {
            case RECEIVED -> "Security deposit received";
            case REFUND -> "Security deposit refunded";
            default -> "Security deposit kept";
        };
        book(reverse ? "PM_DEPOSIT_REVERSAL" : "PM_DEPOSIT", t.getId(), (reverse ? "Voided: " : "") + verb + " — " + what, when, lines);
    }
}
