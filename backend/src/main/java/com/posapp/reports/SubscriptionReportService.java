package com.posapp.reports;

import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.reports.dto.SubscriptionReportRows.ChurnRow;
import com.posapp.reports.dto.SubscriptionReportRows.PlanRow;
import com.posapp.reports.dto.SubscriptionReportRows.UnpaidRow;
import com.posapp.subscription.Subscription;
import com.posapp.subscription.SubscriptionRepository;
import com.posapp.subscription.SubscriptionService;
import com.posapp.subscription.SubscriptionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subscription reports: recurring revenue by plan, churn in a period, unpaid subscription invoices. */
@Service
public class SubscriptionReportService {

    private final SubscriptionRepository repository;
    private final InvoiceService invoiceService;

    public SubscriptionReportService(SubscriptionRepository repository, InvoiceService invoiceService) {
        this.repository = repository;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public List<PlanRow> byPlan() {
        LocalDate today = LocalDate.now();
        Map<String, int[]> counts = new LinkedHashMap<>();   // active, trial, paused, gone, seats
        Map<String, BigDecimal> mrr = new LinkedHashMap<>();
        for (Subscription s : repository.findAllByOrderByIdDesc()) {
            String plan = s.getPlan() != null ? s.getPlan().getName() : "(no plan)";
            int[] c = counts.computeIfAbsent(plan, k -> new int[5]);
            mrr.putIfAbsent(plan, BigDecimal.ZERO);
            switch (s.getStatus()) {
                case PAUSED -> c[2]++;
                case CANCELLED, ENDED -> c[3]++;
                case ACTIVE -> {
                    if (s.getTrialEndDate() != null && s.getTrialEndDate().isAfter(today)) {
                        c[1]++;
                    } else {
                        c[0]++;
                        c[4] += s.getQuantity();
                        mrr.merge(plan, SubscriptionService.monthly(SubscriptionService.cycleNet(s), s.getBillingInterval()), BigDecimal::add);
                    }
                }
            }
        }
        List<PlanRow> rows = new ArrayList<>();
        for (var e : counts.entrySet()) {
            int[] c = e.getValue();
            BigDecimal m = mrr.get(e.getKey());
            rows.add(new PlanRow(e.getKey(), c[0], c[1], c[2], c[3], c[4], m, m.multiply(BigDecimal.valueOf(12)),
                    c[0] == 0 ? BigDecimal.ZERO : m.divide(BigDecimal.valueOf(c[0]), 2, java.math.RoundingMode.HALF_UP)));
        }
        rows.sort(Comparator.comparing(PlanRow::mrr).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ChurnRow> churn(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        List<ChurnRow> rows = new ArrayList<>();
        for (Subscription s : repository.findAllByOrderByIdDesc()) {
            if (s.getStatus() != SubscriptionStatus.CANCELLED && s.getStatus() != SubscriptionStatus.ENDED) continue;
            var at = s.getStatus() == SubscriptionStatus.CANCELLED ? s.getCancelledAt() : s.getEndedAt();
            if (at == null) continue;
            LocalDate left = at.atZone(zone).toLocalDate();
            if (left.isBefore(from) || left.isAfter(to)) continue;
            rows.add(new ChurnRow(s.getCustomerName(), s.getPlan() != null ? s.getPlan().getName() : null, s.getStatus().name(), s.getStartDate(), left,
                    ChronoUnit.MONTHS.between(s.getStartDate(), left), SubscriptionService.monthly(SubscriptionService.cycleNet(s), s.getBillingInterval()),
                    s.getCyclesBilled(), s.getCancelReason()));
        }
        rows.sort(Comparator.comparing(ChurnRow::leftOn));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<UnpaidRow> unpaid() {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        List<UnpaidRow> rows = new ArrayList<>();
        for (InvoiceDto inv : invoiceService.unpaidSubscriptionInvoices()) {
            Subscription s = inv.subscriptionId() == null ? null : repository.findById(inv.subscriptionId()).orElse(null);
            LocalDate base = inv.dueDate() != null ? inv.dueDate() : (inv.invoiceDate() != null ? inv.invoiceDate().atZone(zone).toLocalDate() : today);
            rows.add(new UnpaidRow(inv.customerName(), s != null && s.getPlan() != null ? s.getPlan().getName() : null, inv.invoiceNumber(), base,
                    Math.max(0, ChronoUnit.DAYS.between(base, today)), inv.totalAmount(), inv.amountOutstanding(),
                    s != null && inv.id().equals(s.getDunningInvoiceId()) ? s.getDunningCount() : 0, s != null ? s.getStatus().name() : null));
        }
        rows.sort(Comparator.comparingLong(UnpaidRow::daysLate).reversed());
        return rows;
    }
}
