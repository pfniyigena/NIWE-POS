package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.Attention;
import com.posapp.accounting.dto.ExpenseExtras.MonthAmount;
import com.posapp.accounting.dto.ExpenseExtras.NamedAmount;
import com.posapp.accounting.dto.ExpenseExtras.SummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Expenses overview: this month against last, where the money goes, what waits for approval, and which bills are unpaid or late. */
@Service
public class ExpenseOverviewService {

    private final ExpenseRepository repository;
    private final ExpensePaymentRepository paymentRepository;
    private final ExpenseSettingsService settingsService;
    private final ExpenseBudgetService budgetService;

    public ExpenseOverviewService(ExpenseRepository repository, ExpensePaymentRepository paymentRepository, ExpenseSettingsService settingsService,
                                  ExpenseBudgetService budgetService) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.settingsService = settingsService;
        this.budgetService = budgetService;
    }

    private static BigDecimal tax(Expense e) {
        return e.getTaxAmount() != null ? e.getTaxAmount() : BigDecimal.ZERO;
    }

    private static BigDecimal net(Expense e) {
        return e.getAmount().subtract(tax(e));
    }

    @Transactional(readOnly = true)
    public SummaryDto summary() {
        LocalDate today = LocalDate.now();
        YearMonth thisM = YearMonth.from(today);
        YearMonth lastM = thisM.minusMonths(1);
        int dueSoonDays = settingsService.entity().getDueSoonDays();
        List<Expense> all = repository.findAllByOrderByExpenseDateDesc();

        BigDecimal thisMonth = BigDecimal.ZERO, lastMonth = BigDecimal.ZERO, ytd = BigDecimal.ZERO, vat = BigDecimal.ZERO;
        int pendingCount = 0;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        int unpaidCount = 0, overdueCount = 0, dueSoonCount = 0;
        BigDecimal unpaidBal = BigDecimal.ZERO, overdueBal = BigDecimal.ZERO, dueSoonBal = BigDecimal.ZERO;
        Map<String, BigDecimal> cat = new HashMap<>();
        Map<String, Integer> catN = new HashMap<>();
        Map<String, BigDecimal> ven = new HashMap<>();
        Map<String, Integer> venN = new HashMap<>();
        Map<String, BigDecimal> byAccount = new HashMap<>();
        Map<String, Integer> byAccountN = new HashMap<>();
        Map<YearMonth, BigDecimal> trend = new HashMap<>();
        List<Attention> attention = new ArrayList<>();

        for (Expense e : all) {
            if (e.getStatus() == ExpenseStatus.PENDING) {
                pendingCount++;
                pendingAmount = pendingAmount.add(e.getAmount());
            }
            if (e.getStatus() != ExpenseStatus.APPROVED) continue;
            YearMonth m = YearMonth.from(e.getExpenseDate());
            BigDecimal n = net(e);
            trend.merge(m, n, BigDecimal::add);
            if (m.equals(thisM)) {
                thisMonth = thisMonth.add(n);
                vat = vat.add(tax(e));
                cat.merge(e.getAccount().getName(), n, BigDecimal::add);
                catN.merge(e.getAccount().getName(), 1, Integer::sum);
                String v = e.getPayeeName() == null ? "—" : e.getPayeeName();
                ven.merge(v, n, BigDecimal::add);
                venN.merge(v, 1, Integer::sum);
                if (e.isPaid()) {
                    String acc = e.getPaidFromAccount() != null ? e.getPaidFromAccount().getName() : "Cash";
                    byAccount.merge(acc, e.getAmount(), BigDecimal::add);
                    byAccountN.merge(acc, 1, Integer::sum);
                }
            }
            if (m.equals(lastM)) lastMonth = lastMonth.add(n);
            if (e.getExpenseDate().getYear() == today.getYear()) ytd = ytd.add(n);
            if (!e.isPaid()) {
                BigDecimal bal = e.getAmount().subtract(e.getAmountPaid() != null ? e.getAmountPaid() : BigDecimal.ZERO);
                if (bal.signum() > 0 && e.getSettledAt() == null) {
                    unpaidCount++;
                    unpaidBal = unpaidBal.add(bal);
                    LocalDate due = e.getDueDate();
                    if (due != null && due.isBefore(today)) {
                        overdueCount++;
                        overdueBal = overdueBal.add(bal);
                        attention.add(new Attention(e.getId(), e.getExpenseNumber(), e.getPayeeName(), e.getAmount(), bal, due, "OVERDUE"));
                    } else if (due != null && !due.isAfter(today.plusDays(dueSoonDays))) {
                        dueSoonCount++;
                        dueSoonBal = dueSoonBal.add(bal);
                        attention.add(new Attention(e.getId(), e.getExpenseNumber(), e.getPayeeName(), e.getAmount(), bal, due, "DUE_SOON"));
                    }
                }
            }
        }
        for (ExpensePayment p : paymentRepository.findBetween(thisM.atDay(1), thisM.atEndOfMonth())) {
            String acc = p.getPaidFromAccount() != null ? p.getPaidFromAccount().getName() : "Cash";
            byAccount.merge(acc, p.getAmount(), BigDecimal::add);
            byAccountN.merge(acc, 1, Integer::sum);
        }
        attention.sort(Comparator.comparing(Attention::dueDate));
        List<MonthAmount> six = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = thisM.minusMonths(i);
            six.add(new MonthAmount(m.toString(), trend.getOrDefault(m, BigDecimal.ZERO)));
        }
        return new SummaryDto(thisMonth, lastMonth, ytd, pendingCount, pendingAmount, unpaidCount, unpaidBal, overdueCount, overdueBal, dueSoonCount, dueSoonBal, vat,
                top(cat, catN, 5), top(ven, venN, 5), top(byAccount, byAccountN, 10), six,
                budgetService.alerts(today.getYear(), today.getMonthValue()), attention.size() > 10 ? attention.subList(0, 10) : attention);
    }

    private static List<NamedAmount> top(Map<String, BigDecimal> amounts, Map<String, Integer> counts, int limit) {
        return amounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(en -> new NamedAmount(en.getKey(), en.getValue(), counts.getOrDefault(en.getKey(), 0)))
                .toList();
    }
}
