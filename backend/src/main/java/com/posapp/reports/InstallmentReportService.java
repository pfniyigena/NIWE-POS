package com.posapp.reports;

import com.posapp.installment.InstallmentPlanStatus;
import com.posapp.installment.InstallmentService;
import com.posapp.installment.InstallmentState;
import com.posapp.installment.dto.InstallmentDtos.InstallmentLine;
import com.posapp.installment.dto.InstallmentDtos.PlanSummaryDto;
import com.posapp.reports.dto.InstallmentReportRows.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** The instalment reports: agreements, what is due, what is overdue, and what was collected. */
@Service
public class InstallmentReportService {

    private final InstallmentService installmentService;

    public InstallmentReportService(InstallmentService installmentService) {
        this.installmentService = installmentService;
    }

    public List<AgreementRow> agreements(Long branchId) {
        return installmentService.list(null, null).stream()
                .filter(p -> branchId == null || branchId.equals(p.branchId()))
                .map(p -> new AgreementRow(p.planNumber(), p.customerName(), p.invoiceNumber(), p.status().name(), p.numberOfInstallments(),
                        p.paidInstallments(), p.financedAmount(), p.totalInterest(), p.totalPayable(), p.paid(),
                        p.status() == InstallmentPlanStatus.ACTIVE ? p.outstanding() : java.math.BigDecimal.ZERO, p.overdueAmount(), p.nextDueDate())).toList();
    }

    /** Unpaid instalments falling due from today through the next {@code days} days. */
    public List<DueRow> due(int days, Long branchId) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(days);
        return installmentService.lines(true, branchId).stream()
                .filter(l -> l.balance().signum() > 0 && !l.dueDate().isBefore(today) && !l.dueDate().isAfter(limit))
                .sorted(Comparator.comparing(InstallmentLine::dueDate).thenComparing(InstallmentLine::planNumber))
                .map(InstallmentReportService::row).toList();
    }

    /** Unpaid instalments past their due date, longest overdue first. */
    public List<DueRow> overdue(Long branchId) {
        return installmentService.lines(true, branchId).stream()
                .filter(l -> l.state() == InstallmentState.OVERDUE)
                .sorted(Comparator.comparingLong(InstallmentLine::daysOverdue).reversed())
                .map(InstallmentReportService::row).toList();
    }

    public List<CollectionRow> collections(LocalDate from, LocalDate to, Long branchId) {
        ZoneId zone = ZoneId.systemDefault();
        return installmentService.paymentsBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(), branchId).stream()
                .map(x -> new CollectionRow(x.getPaidAt(), x.getReceiptNumber(), x.getPlan().getPlanNumber(), x.getPlan().getCustomerName(),
                        x.getPrincipalPart(), x.getChargesPart(), x.getAmount(), x.getMethod(), x.getRecordedByName())).toList();
    }

    private static DueRow row(InstallmentLine l) {
        return new DueRow(l.planNumber(), l.customerName(), l.invoiceNumber(), l.seq() + " of " + l.of(), l.dueDate(), l.totalDue(), l.paid(),
                l.balance(), l.state().name(), l.daysOverdue());
    }
}
