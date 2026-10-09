package com.posapp.accounting;

import com.posapp.accounting.dto.AccountAmountDto;
import com.posapp.accounting.dto.AssetsReportDto;
import com.posapp.accounting.dto.BalanceSheetDto;
import com.posapp.accounting.dto.CashFlowDto;
import com.posapp.accounting.dto.CashFlowLineDto;
import com.posapp.accounting.dto.CostCenterActivityDto;
import com.posapp.accounting.dto.FinancialTransactionDto;
import com.posapp.accounting.dto.GeneralLedgerAccountDto;
import com.posapp.accounting.dto.GroupedAmountDto;
import com.posapp.accounting.dto.LedgerLineDto;
import com.posapp.accounting.dto.ProfitAndLossDto;
import com.posapp.accounting.dto.ReportDetailLineDto;
import com.posapp.accounting.dto.TaxRateAmountDto;
import com.posapp.accounting.dto.TaxSummaryDto;
import com.posapp.accounting.dto.TrialBalanceRowDto;
import com.posapp.common.ApiException;
import com.posapp.order.OrderCalculator;
import com.posapp.order.OrderLine;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.SaleOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four standard reports every double-entry ledger needs, all built from {@link
 * JournalEntryLine} rows — General Ledger ({@link #generalLedger}, every account's own ledger
 * back to back; {@link #ledger} is the same math for one account only, reached from Chart of
 * Accounts rather than the Reports menu), Trial Balance ({@link #trialBalance}), Profit &amp; Loss
 * ({@link #profitAndLoss}, shown under both that name and "Income Statement") and Balance Sheet
 * ({@link #balanceSheet}) — plus the later Accounting/Journals/Expenses/Incomes report batch
 * below. Kept separate from {@code com.posapp.reports.ReportService} (the existing
 * POS/Inventory/Sales report engine) deliberately — these only ever read this module's own {@link
 * Account}/{@link JournalEntry} data, so folding them into that unrelated package would add a
 * dependency neither side needs.
 * <p>
 * Every report row that summarizes more than one underlying transaction also carries a {@code
 * details} list of {@link ReportDetailLineDto} (Fourier: "The report show the summary, Add all
 * details") — the individual journal-entry lines, sale-order lines, or Expense/Income entries
 * that rolled into that row, in date order. {@link #toDetails} is the one shared helper every
 * journal-line-backed report reuses for this; {@link #taxSummary} and {@link #groupAmounts} build
 * their own detail lists directly since their source rows aren't {@link JournalEntryLine}s.
 */
@Service
public class AccountingReportService {

    private static final int SCALE = 2;

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final CostCenterRepository costCenterRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final OrderRepository orderRepository;
    /** Branch P&L — resolves which branch a legacy (null) entry belongs to. */
    private final com.posapp.branch.BranchService branchService;

    public AccountingReportService(AccountRepository accountRepository, JournalEntryRepository journalEntryRepository,
                                    CostCenterRepository costCenterRepository, ExpenseRepository expenseRepository,
                                    IncomeRepository incomeRepository, OrderRepository orderRepository,
                                    com.posapp.branch.BranchService branchService) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.costCenterRepository = costCenterRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.orderRepository = orderRepository;
        this.branchService = branchService;
    }

    /** True for a debit-normal account (a debit increases it) — ASSET/EXPENSE; false for a credit-normal one (LIABILITY/EQUITY/REVENUE). */
    private boolean isDebitNormal(AccountType type) {
        return type == AccountType.ASSET || type == AccountType.EXPENSE;
    }

    /** One account's own dated, running-balance ledger — Admin > Accounting > Chart of Accounts > (an account) > Ledger. */
    @Transactional(readOnly = true)
    public List<LedgerLineDto> ledger(Long accountId, Instant from, Instant to) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account not found: " + accountId));
        boolean debitNormal = isDebitNormal(account.getType());
        // Opening balance is everything strictly before `from`, so the running balance shown for
        // the first line in range is correct rather than starting from zero every time the date
        // range narrows — same reasoning a real ledger's own "brought forward" line serves.
        BigDecimal openingBalance = BigDecimal.ZERO;
        for (JournalEntryLine line : journalEntryRepository.findLinesForAccountUpTo(accountId, from.minusMillis(1))) {
            openingBalance = applyLine(openingBalance, line, debitNormal);
        }

        List<LedgerLineDto> rows = new ArrayList<>();
        BigDecimal running = openingBalance;
        for (JournalEntryLine line : journalEntryRepository.findLinesForAccountInRange(accountId, from, to)) {
            running = applyLine(running, line, debitNormal);
            JournalEntry entry = line.getEntry();
            rows.add(new LedgerLineDto(entry.getEntryDate(), entry.getId(), entry.getEntryNumber(), entry.getMemo(),
                    line.getDescription(), line.getDebit(), line.getCredit(), running.setScale(SCALE, RoundingMode.HALF_UP)));
        }
        return rows;
    }

    private BigDecimal applyLine(BigDecimal balance, JournalEntryLine line, boolean debitNormal) {
        BigDecimal delta = debitNormal ? line.getDebit().subtract(line.getCredit()) : line.getCredit().subtract(line.getDebit());
        return balance.add(delta);
    }

    /**
     * Every account's own ledger, back to back — the traditional "general ledger" book (Fourier:
     * "General ledger ... add them"), reached from Admin &gt; Accounting &gt; Reports rather than
     * {@link #ledger}'s single-account drill-down from Chart of Accounts. Built the same
     * one-query-for-the-whole-range-then-group way {@link #trialBalance}/{@link #balanceSheet}
     * are, rather than calling {@link #ledger} once per account (which would re-query per account
     * instead of once for everything). Accounts with no activity in the range are left out, same
     * as trialBalance.
     */
    @Transactional(readOnly = true)
    public List<GeneralLedgerAccountDto> generalLedger(Instant from, Instant to) {
        Map<Long, List<JournalEntryLine>> openingLines = groupLinesByAccount(journalEntryRepository.findAllLinesUpTo(from.minusMillis(1)));
        Map<Long, List<JournalEntryLine>> rangeLines = groupLinesByAccount(journalEntryRepository.findAllLinesInRange(from, to));

        List<GeneralLedgerAccountDto> result = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            List<JournalEntryLine> inRange = rangeLines.get(account.getId());
            if (inRange == null || inRange.isEmpty()) {
                continue;
            }
            boolean debitNormal = isDebitNormal(account.getType());

            BigDecimal opening = BigDecimal.ZERO;
            for (JournalEntryLine line : openingLines.getOrDefault(account.getId(), List.of())) {
                opening = applyLine(opening, line, debitNormal);
            }

            List<JournalEntryLine> sorted = new ArrayList<>(inRange);
            sorted.sort(Comparator.comparing(l -> l.getEntry().getEntryDate()));

            List<LedgerLineDto> rows = new ArrayList<>();
            BigDecimal running = opening;
            for (JournalEntryLine line : sorted) {
                running = applyLine(running, line, debitNormal);
                JournalEntry entry = line.getEntry();
                rows.add(new LedgerLineDto(entry.getEntryDate(), entry.getId(), entry.getEntryNumber(), entry.getMemo(),
                        line.getDescription(), line.getDebit(), line.getCredit(), running.setScale(SCALE, RoundingMode.HALF_UP)));
            }
            result.add(new GeneralLedgerAccountDto(account.getId(), account.getCode(), account.getName(), account.getType(),
                    opening.setScale(SCALE, RoundingMode.HALF_UP), running.setScale(SCALE, RoundingMode.HALF_UP), rows));
        }
        return result;
    }

    /** Every active account's own debit/credit totals and net balance in range — should always sum to equal totals on both sides, a structural sanity check on the whole ledger. */
    @Transactional(readOnly = true)
    public List<TrialBalanceRowDto> trialBalance(Instant from, Instant to) {
        Map<Long, List<JournalEntryLine>> linesByAccount = groupLinesByAccount(journalEntryRepository.findAllLinesInRange(from, to));
        List<TrialBalanceRowDto> rows = new ArrayList<>();
        for (Account account : accountRepository.findAllByOrderByCodeAsc()) {
            List<JournalEntryLine> lines = linesByAccount.get(account.getId());
            if (lines == null) {
                continue;
            }
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (JournalEntryLine line : lines) {
                totalDebit = totalDebit.add(line.getDebit());
                totalCredit = totalCredit.add(line.getCredit());
            }
            boolean debitNormal = isDebitNormal(account.getType());
            BigDecimal balance = debitNormal ? totalDebit.subtract(totalCredit) : totalCredit.subtract(totalDebit);
            rows.add(new TrialBalanceRowDto(account.getId(), account.getCode(), account.getName(), account.getType(),
                    totalDebit.setScale(SCALE, RoundingMode.HALF_UP), totalCredit.setScale(SCALE, RoundingMode.HALF_UP),
                    balance.setScale(SCALE, RoundingMode.HALF_UP), toDetails(lines)));
        }
        return rows;
    }

    /** Revenue and Expense accounts only, net of debits/credits within the range — see ProfitAndLossDto's own javadoc. */
    @Transactional(readOnly = true)
    public ProfitAndLossDto profitAndLoss(Instant from, Instant to) {
        return profitAndLoss(from, to, null);
    }

    /**
     * Branch P&amp;L: {@code branchId} (null = every branch) keeps only entries posted for that
     * branch. Entries with no branch (older, or from sources that carry none such as customer and
     * supplier payments) count as head office's.
     */
    @Transactional(readOnly = true)
    public ProfitAndLossDto profitAndLoss(Instant from, Instant to, Long branchId) {
        List<JournalEntryLine> allLines = journalEntryRepository.findAllLinesInRange(from, to);
        if (branchId != null) {
            Long headquartersId = branchService.headquartersId();
            allLines = allLines.stream()
                    .filter(l -> (l.getEntry().getBranchId() != null ? l.getEntry().getBranchId() : headquartersId).equals(branchId))
                    .toList();
        }
        Map<Long, List<JournalEntryLine>> linesByAccount = groupLinesByAccount(allLines);
        List<AccountAmountDto> revenue = accountAmounts(linesByAccount, AccountType.REVENUE);
        List<AccountAmountDto> expenses = accountAmounts(linesByAccount, AccountType.EXPENSE);
        BigDecimal totalRevenue = sum(revenue);
        BigDecimal totalExpenses = sum(expenses);
        return new ProfitAndLossDto(from, to, revenue, expenses, totalRevenue, totalExpenses,
                totalRevenue.subtract(totalExpenses).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** As-of-date snapshot of every Asset/Liability/(real) Equity account, plus computed current earnings — see BalanceSheetDto's own javadoc. */
    @Transactional(readOnly = true)
    public BalanceSheetDto balanceSheet(Instant asOf) {
        Map<Long, List<JournalEntryLine>> linesByAccount = groupLinesByAccount(journalEntryRepository.findAllLinesUpTo(asOf));
        List<AccountAmountDto> assets = accountAmounts(linesByAccount, AccountType.ASSET);
        List<AccountAmountDto> liabilities = accountAmounts(linesByAccount, AccountType.LIABILITY);
        List<AccountAmountDto> equity = accountAmounts(linesByAccount, AccountType.EQUITY);
        BigDecimal totalAssets = sum(assets);
        BigDecimal totalLiabilities = sum(liabilities);
        BigDecimal totalEquity = sum(equity);
        BigDecimal totalRevenue = sum(accountAmounts(linesByAccount, AccountType.REVENUE));
        BigDecimal totalExpenses = sum(accountAmounts(linesByAccount, AccountType.EXPENSE));
        BigDecimal currentEarnings = totalRevenue.subtract(totalExpenses).setScale(SCALE, RoundingMode.HALF_UP);
        boolean balanced = totalAssets.subtract(totalLiabilities).subtract(totalEquity).subtract(currentEarnings)
                .abs().compareTo(BigDecimal.valueOf(0.01)) < 0;
        return new BalanceSheetDto(asOf, assets, liabilities, equity, totalAssets, totalLiabilities, totalEquity, currentEarnings, balanced);
    }

    /** Every line in a set, grouped by its own account id — shared by trialBalance/profitAndLoss/balanceSheet, replacing what used to be a plain debit/credit-totals map so each account's own contributing lines survive for {@code details}. */
    private Map<Long, List<JournalEntryLine>> groupLinesByAccount(List<JournalEntryLine> lines) {
        Map<Long, List<JournalEntryLine>> map = new LinkedHashMap<>();
        for (JournalEntryLine line : lines) {
            map.computeIfAbsent(line.getAccount().getId(), id -> new ArrayList<>()).add(line);
        }
        return map;
    }

    private List<AccountAmountDto> accountAmounts(Map<Long, List<JournalEntryLine>> linesByAccount, AccountType type) {
        List<AccountAmountDto> result = new ArrayList<>();
        boolean debitNormal = isDebitNormal(type);
        for (Account account : accountRepository.findByTypeOrderByCodeAsc(type)) {
            List<JournalEntryLine> lines = linesByAccount.get(account.getId());
            if (lines == null) {
                continue;
            }
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (JournalEntryLine line : lines) {
                totalDebit = totalDebit.add(line.getDebit());
                totalCredit = totalCredit.add(line.getCredit());
            }
            BigDecimal net = totalDebit.subtract(totalCredit);
            if (net.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal amount = debitNormal ? net : net.negate();
            result.add(new AccountAmountDto(account.getId(), account.getCode(), account.getName(),
                    amount.setScale(SCALE, RoundingMode.HALF_UP), toDetails(lines)));
        }
        return result;
    }

    private BigDecimal sum(List<AccountAmountDto> rows) {
        return rows.stream().map(AccountAmountDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Every line in {@code lines}, turned into a {@code ReportDetailLineDto} in date order — the raw debit/credit exactly as posted, not normalized for the account's own type; shared by every journal-line-backed report's own {@code details}. */
    private List<ReportDetailLineDto> toDetails(List<JournalEntryLine> lines) {
        List<JournalEntryLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(l -> l.getEntry().getEntryDate()));
        List<ReportDetailLineDto> details = new ArrayList<>();
        for (JournalEntryLine line : sorted) {
            JournalEntry entry = line.getEntry();
            String description = line.getDescription() != null && !line.getDescription().isBlank() ? line.getDescription() : entry.getMemo();
            BigDecimal debit = line.getDebit().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal credit = line.getCredit().setScale(SCALE, RoundingMode.HALF_UP);
            details.add(new ReportDetailLineDto(entry.getEntryDate(), entry.getEntryNumber(), description, debit, credit,
                    debit.subtract(credit)));
        }
        return details;
    }

    // ---- Journals Reports: Financial Transactions / Journals / Cost Centers / Chart of Accounts Directory ----

    /** Every journal entry line across every account in range, flattened and dated — the "Financial Transactions" report. Chart of Accounts Directory and the plain Journals list need no method here: they're exactly AccountService.listAll()/JournalEntryService.listAll() (optionally date-filtered client-side), reusing those existing endpoints as-is rather than duplicating them. Each row here already IS one transaction, so unlike every report above it needs no separate {@code details} list. */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDto> financialTransactions(Instant from, Instant to) {
        List<FinancialTransactionDto> rows = new ArrayList<>();
        for (JournalEntryLine line : journalEntryRepository.findAllLinesInRange(from, to)) {
            JournalEntry entry = line.getEntry();
            Account account = line.getAccount();
            rows.add(new FinancialTransactionDto(entry.getEntryDate(), entry.getEntryNumber(), entry.getMemo(),
                    account != null ? account.getCode() : null, account != null ? account.getName() : null,
                    line.getDebit(), line.getCredit(), line.getDescription()));
        }
        rows.sort(Comparator.comparing(FinancialTransactionDto::date));
        return rows;
    }

    /** Every journal entry in range — same rows JournalEntryService.listAll() would show, just date-scoped for the "Journals Report" menu item rather than reusing the unscoped admin list. JournalEntryDto already carries its own {@code lines}, so it needs no separate {@code details} field either — the frontend expands that list directly. */
    @Transactional(readOnly = true)
    public List<com.posapp.accounting.dto.JournalEntryDto> journals(Instant from, Instant to) {
        return journalEntryRepository.findByEntryDateBetweenOrderByEntryDateAsc(from, to).stream()
                .map(com.posapp.accounting.dto.JournalEntryDto::from).toList();
    }

    /** Each cost center's own total debit/credit/net activity in range — the "Cost Centers" journals report (distinct from the plain CRUD list under Admin > Accounting > Cost Centers). */
    @Transactional(readOnly = true)
    public List<CostCenterActivityDto> costCenterActivity(Instant from, Instant to) {
        Map<Long, List<JournalEntryLine>> linesByCostCenter = new LinkedHashMap<>();
        for (JournalEntryLine line : journalEntryRepository.findAllLinesWithCostCenterInRange(from, to)) {
            linesByCostCenter.computeIfAbsent(line.getCostCenter().getId(), id -> new ArrayList<>()).add(line);
        }
        List<CostCenterActivityDto> rows = new ArrayList<>();
        for (CostCenter cc : costCenterRepository.findAllByOrderByCodeAsc()) {
            List<JournalEntryLine> lines = linesByCostCenter.get(cc.getId());
            if (lines == null) {
                continue;
            }
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (JournalEntryLine line : lines) {
                totalDebit = totalDebit.add(line.getDebit());
                totalCredit = totalCredit.add(line.getCredit());
            }
            rows.add(new CostCenterActivityDto(cc.getId(), cc.getCode(), cc.getName(),
                    totalDebit.setScale(SCALE, RoundingMode.HALF_UP), totalCredit.setScale(SCALE, RoundingMode.HALF_UP),
                    totalDebit.subtract(totalCredit).setScale(SCALE, RoundingMode.HALF_UP), toDetails(lines)));
        }
        return rows;
    }

    // ---- Accounting Reports: Cash Flow / Tax Summary / Assets ----

    /**
     * Direct-method cash flow statement (Fourier's own scoping choice over the indirect method,
     * which would need non-cash adjustments like depreciation this app doesn't model) — every
     * movement of the {@code CASH} account in range is classified Operating by default, or
     * Financing when it's the cash side of a {@code MANUAL} journal entry whose other line hits
     * {@code OWNERS_EQUITY} (an owner contribution/draw). Investing is always empty — see
     * CashFlowDto's own javadoc. Each {@code CashFlowLineDto} already IS one cash movement, so
     * this report needs no separate {@code details} list either.
     */
    @Transactional(readOnly = true)
    public CashFlowDto cashFlow(Instant from, Instant to) {
        Account cash = accountRepository.findByWellKnownKey(AccountService.CASH)
                .orElseThrow(() -> new IllegalStateException("Missing well-known account '" + AccountService.CASH + "'"));

        BigDecimal opening = BigDecimal.ZERO;
        for (JournalEntryLine line : journalEntryRepository.findLinesForAccountUpTo(cash.getId(), from.minusMillis(1))) {
            opening = opening.add(line.getDebit().subtract(line.getCredit()));
        }

        List<CashFlowLineDto> operating = new ArrayList<>();
        List<CashFlowLineDto> financing = new ArrayList<>();
        BigDecimal netOperating = BigDecimal.ZERO;
        BigDecimal netFinancing = BigDecimal.ZERO;
        for (JournalEntryLine line : journalEntryRepository.findLinesForAccountInRange(cash.getId(), from, to)) {
            BigDecimal amount = line.getDebit().subtract(line.getCredit());
            JournalEntry entry = line.getEntry();
            boolean isFinancing = "MANUAL".equals(entry.getSourceType()) && entry.getLines().stream()
                    .anyMatch(l -> l != line && l.getAccount() != null && AccountService.OWNERS_EQUITY.equals(l.getAccount().getWellKnownKey()));
            CashFlowLineDto dto = new CashFlowLineDto(entry.getEntryDate(), entry.getMemo(), amount.setScale(SCALE, RoundingMode.HALF_UP));
            if (isFinancing) {
                financing.add(dto);
                netFinancing = netFinancing.add(amount);
            } else {
                operating.add(dto);
                netOperating = netOperating.add(amount);
            }
        }
        BigDecimal netChange = netOperating.add(netFinancing);
        BigDecimal closing = opening.add(netChange);
        return new CashFlowDto(from, to, operating, List.of(), financing,
                netOperating.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP),
                netFinancing.setScale(SCALE, RoundingMode.HALF_UP), netChange.setScale(SCALE, RoundingMode.HALF_UP),
                opening.setScale(SCALE, RoundingMode.HALF_UP), closing.setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * General tax summary shared by the Tax Report and Tax Declaration screens — see
     * TaxSummaryDto's own javadoc for why "tax paid" isn't a separate figure here.
     * {@code collectedByRate} is computed straight from completed POS sale lines (not from the
     * ledger, which only ever sees one lumped {@code TAX_PAYABLE} movement) so it can actually
     * break tax collected down by {@code OrderLine.taxCode} — each rate's own {@code details} is
     * every sale order line that contributed to it, with the collected tax shown on the credit
     * side (collecting tax credits Tax Payable). {@code taxPayableDetails} is the same TAX_PAYABLE
     * ledger lines {@code taxPayableNetMovement} itself is summed from.
     */
    @Transactional(readOnly = true)
    public TaxSummaryDto taxSummary(Instant from, Instant to) {
        Map<String, BigDecimal> byRate = new LinkedHashMap<>();
        Map<String, List<ReportDetailLineDto>> detailsByRate = new LinkedHashMap<>();
        for (SaleOrder order : orderRepository.findCompletedForReport(OrderStatus.COMPLETED, from, to, null)) {
            for (OrderLine line : order.getLines()) {
                BigDecimal tax = OrderCalculator.lineTax(line);
                if (tax.compareTo(BigDecimal.ZERO) <= 0) continue;
                String code = line.getTaxCode() != null && !line.getTaxCode().isBlank() ? line.getTaxCode() : "No tax rate";
                byRate.merge(code, tax, BigDecimal::add);
                BigDecimal scaledTax = tax.setScale(SCALE, RoundingMode.HALF_UP);
                String reference = order.getInvoiceNumber() != null && !order.getInvoiceNumber().isBlank()
                        ? order.getInvoiceNumber() : ("Order #" + order.getId());
                detailsByRate.computeIfAbsent(code, k -> new ArrayList<>())
                        .add(new ReportDetailLineDto(order.getCompletedAt(), reference, line.getDescription(), null, scaledTax, scaledTax));
            }
        }
        List<TaxRateAmountDto> collectedByRate = new ArrayList<>();
        BigDecimal totalCollected = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : byRate.entrySet()) {
            BigDecimal amount = e.getValue().setScale(SCALE, RoundingMode.HALF_UP);
            List<ReportDetailLineDto> details = new ArrayList<>(detailsByRate.getOrDefault(e.getKey(), List.of()));
            details.sort(Comparator.comparing(d -> d.date() == null ? Instant.EPOCH : d.date()));
            collectedByRate.add(new TaxRateAmountDto(e.getKey(), amount, details));
            totalCollected = totalCollected.add(amount);
        }
        collectedByRate.sort(Comparator.comparing(TaxRateAmountDto::taxCode));

        BigDecimal netMovement = BigDecimal.ZERO;
        List<ReportDetailLineDto> taxPayableDetails = List.of();
        Account taxPayable = accountRepository.findByWellKnownKey(AccountService.TAX_PAYABLE).orElse(null);
        if (taxPayable != null) {
            List<JournalEntryLine> lines = journalEntryRepository.findLinesForAccountInRange(taxPayable.getId(), from, to);
            for (JournalEntryLine line : lines) {
                netMovement = netMovement.add(line.getCredit().subtract(line.getDebit()));
            }
            taxPayableDetails = toDetails(lines);
        }
        return new TaxSummaryDto(from, to, collectedByRate, totalCollected.setScale(SCALE, RoundingMode.HALF_UP),
                netMovement.setScale(SCALE, RoundingMode.HALF_UP), taxPayableDetails);
    }

    /** A simple listing of Asset-type accounts and their as-of-date balances — reuses balanceSheet's own computation (Fourier's own scoping choice over a full fixed-asset register with depreciation). Inherits its own per-account {@code details} from AccountAmountDto for free. */
    @Transactional(readOnly = true)
    public AssetsReportDto assets(Instant asOf) {
        BalanceSheetDto bs = balanceSheet(asOf);
        return new AssetsReportDto(bs.asOf(), bs.assets(), bs.totalAssets());
    }

    // ---- Expenses / Incomes segmented + periodic reports ----

    /** category/vendor/staff/client/daily/weekly/monthly/yearly — one method backs all eight "Expenses ..." report menu items; see GroupedAmountDto's own javadoc. */
    @Transactional(readOnly = true)
    public List<GroupedAmountDto> expensesGrouped(LocalDate from, LocalDate to, String groupBy) {
        List<GroupRow> rows = expenseRepository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus.APPROVED, from, to).stream()
                .map(e -> new GroupRow(e.getAccount() != null ? e.getAccount().getCode() + " — " + e.getAccount().getName() : "Unknown",
                        e.getPayeeType(), e.getPayeeName(), e.getAmount(), e.getExpenseDate(), e.getMemo()))
                .toList();
        return groupAmounts(rows, groupBy, true);
    }

    /** Incomes' mirror of expensesGrouped — same eight groupBy values. */
    @Transactional(readOnly = true)
    public List<GroupedAmountDto> incomesGrouped(LocalDate from, LocalDate to, String groupBy) {
        List<GroupRow> rows = incomeRepository.findByIncomeDateBetweenOrderByIncomeDateAsc(from, to).stream()
                .map(i -> new GroupRow(i.getAccount() != null ? i.getAccount().getCode() + " — " + i.getAccount().getName() : "Unknown",
                        i.getPayeeType(), i.getPayeeName(), i.getAmount(), i.getIncomeDate(), i.getMemo()))
                .toList();
        return groupAmounts(rows, groupBy, false);
    }

    private record GroupRow(String categoryLabel, PayeeType payeeType, String payeeLabel, BigDecimal amount, LocalDate date, String memo) {
    }

    /** {@code expense} decides which side of ReportDetailLineDto each row's own amount lands on — debit for an Expense (the same side a real posting of it would hit), credit for an Income — see ReportDetailLineDto's own javadoc. */
    private List<GroupedAmountDto> groupAmounts(List<GroupRow> rows, String groupBy, boolean expense) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, List<ReportDetailLineDto>> detailsByKey = new LinkedHashMap<>();
        for (GroupRow r : rows) {
            String key = groupKey(r, groupBy);
            if (key == null) continue;
            totals.merge(key, r.amount(), BigDecimal::add);
            counts.merge(key, 1L, Long::sum);
            BigDecimal scaledAmount = r.amount().setScale(SCALE, RoundingMode.HALF_UP);
            Instant date = r.date().atStartOfDay(ZoneOffset.UTC).toInstant();
            String memo = r.memo() == null ? "" : r.memo();
            ReportDetailLineDto detail = expense
                    ? new ReportDetailLineDto(date, r.payeeLabel(), memo, scaledAmount, null, scaledAmount)
                    : new ReportDetailLineDto(date, r.payeeLabel(), memo, null, scaledAmount, scaledAmount);
            detailsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail);
        }
        List<GroupedAmountDto> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            out.add(new GroupedAmountDto(e.getKey(), e.getValue().setScale(SCALE, RoundingMode.HALF_UP), counts.get(e.getKey()),
                    detailsByKey.getOrDefault(e.getKey(), List.of())));
        }
        out.sort(Comparator.comparing(GroupedAmountDto::label));
        return out;
    }

    private String groupKey(GroupRow r, String groupBy) {
        return switch (groupBy) {
            case "category" -> r.categoryLabel();
            case "vendor" -> r.payeeType() == PayeeType.VENDOR ? r.payeeLabel() : null;
            case "staff" -> r.payeeType() == PayeeType.STAFF ? r.payeeLabel() : null;
            case "client" -> r.payeeType() == PayeeType.CLIENT ? r.payeeLabel() : null;
            case "daily" -> r.date().toString();
            case "weekly" -> weekLabel(r.date());
            case "monthly" -> YearMonth.from(r.date()).toString();
            case "yearly" -> String.valueOf(r.date().getYear());
            default -> throw ApiException.badRequest("Unknown groupBy: " + groupBy);
        };
    }

    private String weekLabel(LocalDate date) {
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = date.get(WeekFields.ISO.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }
}
