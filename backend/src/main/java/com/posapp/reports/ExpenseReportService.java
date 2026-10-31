package com.posapp.reports;

import com.posapp.accounting.Account;
import com.posapp.accounting.Expense;
import com.posapp.accounting.ExpenseBudgetService;
import com.posapp.accounting.ExpensePayment;
import com.posapp.accounting.ExpensePaymentRepository;
import com.posapp.accounting.ExpenseRepository;
import com.posapp.accounting.ExpenseStatus;
import com.posapp.reports.dto.ExpenseReportRows.AgingRow;
import com.posapp.reports.dto.ExpenseReportRows.BudgetRow;
import com.posapp.reports.dto.ExpenseReportRows.PaymentAccountRow;
import com.posapp.reports.dto.ExpenseReportRows.RegisterRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Expenses reports: the register, unpaid bills by age, budget against actual, and what was paid from which account. */
@Service
public class ExpenseReportService {

    private final ExpenseRepository repository;
    private final ExpensePaymentRepository paymentRepository;
    private final ExpenseBudgetService budgetService;

    public ExpenseReportService(ExpenseRepository repository, ExpensePaymentRepository paymentRepository, ExpenseBudgetService budgetService) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.budgetService = budgetService;
    }

    private static BigDecimal tax(Expense e) {
        return e.getTaxAmount() != null ? e.getTaxAmount() : BigDecimal.ZERO;
    }

    /** Every expense dated in the range, optionally of one status (PENDING, APPROVED, REJECTED, VOIDED). */
    @Transactional(readOnly = true)
    public List<RegisterRow> register(LocalDate from, LocalDate to, String status) {
        String st = status == null || status.isBlank() || status.equalsIgnoreCase("ALL") ? null : status.trim().toUpperCase(Locale.ROOT);
        List<RegisterRow> rows = new ArrayList<>();
        for (Expense e : repository.findAllByOrderByExpenseDateDesc()) {
            if (e.getExpenseDate().isBefore(from) || e.getExpenseDate().isAfter(to)) continue;
            if (st != null && !e.getStatus().name().equals(st)) continue;
            BigDecimal paidSoFar = e.getAmountPaid() != null ? e.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal balance = !e.isPaid() && e.getStatus() == ExpenseStatus.APPROVED ? e.getAmount().subtract(paidSoFar).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            rows.add(new RegisterRow(e.getId(), e.getExpenseNumber(), e.getExpenseDate(), e.getPayeeName(), e.getAccount().getName(),
                    e.getCostCenter() != null ? e.getCostCenter().getName() : null, e.getReference(), e.getStatus().name(),
                    e.getAmount(), tax(e), e.getAmount().subtract(tax(e)), e.isPaid(),
                    e.getPaidFromAccount() != null ? e.getPaidFromAccount().getName() : (e.isPaid() ? "Cash" : null), e.getDueDate(), balance,
                    e.getCreatedByName(), e.getApprovedByName()));
        }
        rows.sort(Comparator.comparing(RegisterRow::date).thenComparing(RegisterRow::id));
        return rows;
    }

    /** Approved bills that still have a balance, oldest due date first, with how late each is today. */
    @Transactional(readOnly = true)
    public List<AgingRow> unpaidAging() {
        LocalDate today = LocalDate.now();
        List<AgingRow> rows = new ArrayList<>();
        for (Expense e : repository.findAllByOrderByExpenseDateDesc()) {
            if (e.getStatus() != ExpenseStatus.APPROVED || e.isPaid()) continue;
            BigDecimal paidSoFar = e.getAmountPaid() != null ? e.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal balance = e.getAmount().subtract(paidSoFar);
            if (balance.signum() <= 0) continue;
            LocalDate due = e.getDueDate() != null ? e.getDueDate() : e.getExpenseDate();
            long late = ChronoUnit.DAYS.between(due, today);
            String bucket = late <= 0 ? "Not yet due" : late <= 30 ? "1 to 30 days" : late <= 60 ? "31 to 60 days" : late <= 90 ? "61 to 90 days" : "Over 90 days";
            rows.add(new AgingRow(e.getId(), e.getExpenseNumber(), e.getPayeeName(), e.getReference(), e.getExpenseDate(), due, Math.max(late, 0), bucket, e.getAmount(), paidSoFar, balance));
        }
        rows.sort(Comparator.comparing(AgingRow::dueDate).thenComparing(AgingRow::id));
        return rows;
    }

    /** Budget against what was spent, one row per budget per calendar month the range touches. */
    @Transactional(readOnly = true)
    public List<BudgetRow> budgetVsActual(LocalDate from, LocalDate to) {
        List<BudgetRow> rows = new ArrayList<>();
        YearMonth ym = YearMonth.from(from);
        YearMonth last = YearMonth.from(to);
        int guard = 0;
        while (!ym.isAfter(last) && guard++ < 36) {
            for (var r : budgetService.status(ym.getYear(), ym.getMonthValue())) {
                rows.add(new BudgetRow(ym.toString(), r.label(), r.kind(), r.budget(), r.spent(), r.pending(), r.remaining(), r.percentUsed(), r.state()));
            }
            ym = ym.plusMonths(1);
        }
        return rows;
    }

    /** Money paid out in the range, by the account it came from: expenses paid up front (by expense date) and payments on bills (by payment date). */
    @Transactional(readOnly = true)
    public List<PaymentAccountRow> byPaymentAccount(LocalDate from, LocalDate to) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
        for (Expense e : repository.findByStatusAndExpenseDateBetweenOrderByExpenseDateAsc(ExpenseStatus.APPROVED, from, to)) {
            if (!e.isPaid()) continue;
            String name = e.getPaidFromAccount() != null ? e.getPaidFromAccount().getName() : "Cash";
            counts.computeIfAbsent(name, k -> new long[2])[0]++;
            BigDecimal[] arr0 = amounts.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            arr0[0] = arr0[0].add(e.getAmount());
        }
        for (ExpensePayment p : paymentRepository.findBetween(from, to)) {
            Account a = p.getPaidFromAccount();
            String name = a != null ? a.getName() : "Cash";
            counts.computeIfAbsent(name, k -> new long[2])[1]++;
            BigDecimal[] arr = amounts.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            arr[1] = arr[1].add(p.getAmount());
        }
        List<PaymentAccountRow> rows = new ArrayList<>();
        counts.forEach((name, c) -> {
            BigDecimal[] a = amounts.get(name);
            rows.add(new PaymentAccountRow(name, c[0], c[1], a[0], a[1], a[0].add(a[1])));
        });
        rows.sort(Comparator.comparing(PaymentAccountRow::total).reversed());
        return rows;
    }
}
