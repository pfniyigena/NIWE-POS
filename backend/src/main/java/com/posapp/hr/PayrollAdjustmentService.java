package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.PayrollExtrasDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

/**
 * One-off amounts for one employee in one month: a bonus, back pay (arrears), an allowance, or a deduction (a fine, a uniform...).
 * The next payroll worked out for that month includes them; once that payroll is approved they are locked.
 */
@Service
public class PayrollAdjustmentService {

    private final PayrollAdjustmentRepository repository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository runRepository;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;

    public PayrollAdjustmentService(PayrollAdjustmentRepository repository, EmployeeRepository employeeRepository, PayrollRunRepository runRepository,
                                    AuditLogService auditLogService, BranchAccessService branchAccessService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.runRepository = runRepository;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<AdjustmentDto> list(Integer year, Integer month, Long employeeId, AdjustmentKind kind, String q) {
        assertCompanyWide();
        String needle = q == null ? "" : q.trim().toLowerCase();
        Map<String, PayrollRun> runs = new HashMap<>();
        for (PayrollRun r : runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc()) runs.put(r.getPeriodYear() + "-" + r.getPeriodMonth(), r);
        List<AdjustmentDto> out = new ArrayList<>();
        for (PayrollAdjustment a : repository.findAllByOrderByPeriodYearDescPeriodMonthDescIdDesc()) {
            if (year != null && a.getPeriodYear() != year) continue;
            if (month != null && a.getPeriodMonth() != month) continue;
            if (employeeId != null && !a.getEmployee().getId().equals(employeeId)) continue;
            if (kind != null && a.getKind() != kind) continue;
            if (!needle.isEmpty() && !((a.getName() + " " + a.getEmployee().fullName() + " " + nz(a.getEmployee().getEmployeeNumber()) + " " + nz(a.getNote())).toLowerCase().contains(needle))) continue;
            out.add(dto(a, runs.get(a.getPeriodYear() + "-" + a.getPeriodMonth())));
        }
        return out;
    }

    @Transactional
    public AdjustmentDto create(AdjustmentRequest req) {
        assertCompanyWide();
        PayrollAdjustment a = new PayrollAdjustment();
        a.setCreatedByName(HrSupport.currentUserName());
        apply(a, req);
        a = repository.save(a);
        auditLogService.record("PAYROLL_ADJUSTMENT_ADDED", describe(a), "PayrollAdjustment", a.getId());
        return dto(a, runRepository.findByPeriodYearAndPeriodMonth(a.getPeriodYear(), a.getPeriodMonth()).orElse(null));
    }

    @Transactional
    public AdjustmentDto update(Long id, AdjustmentRequest req) {
        assertCompanyWide();
        PayrollAdjustment a = adjustment(id);
        assertOpen(a.getPeriodYear(), a.getPeriodMonth());
        apply(a, req);
        a = repository.save(a);
        auditLogService.record("PAYROLL_ADJUSTMENT_CHANGED", describe(a), "PayrollAdjustment", id);
        return dto(a, runRepository.findByPeriodYearAndPeriodMonth(a.getPeriodYear(), a.getPeriodMonth()).orElse(null));
    }

    @Transactional
    public void delete(Long id) {
        assertCompanyWide();
        PayrollAdjustment a = adjustment(id);
        assertOpen(a.getPeriodYear(), a.getPeriodMonth());
        repository.delete(a);
        auditLogService.record("PAYROLL_ADJUSTMENT_REMOVED", describe(a), "PayrollAdjustment", id);
    }

    /** Pending adjustments of a month, by employee (what the payroll being worked out has to include). */
    @Transactional(readOnly = true)
    public Map<Long, List<PayrollAdjustment>> forMonth(int year, int month) {
        Map<Long, List<PayrollAdjustment>> out = new HashMap<>();
        for (PayrollAdjustment a : repository.findByPeriodYearAndPeriodMonthOrderByIdAsc(year, month)) {
            out.computeIfAbsent(a.getEmployee().getId(), k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    private void apply(PayrollAdjustment a, AdjustmentRequest req) {
        if (req == null) throw ApiException.badRequest("Fill in the adjustment.");
        Employee e = a.getId() != null ? a.getEmployee() : null;
        if (e == null) {
            if (req.employeeId() == null) throw ApiException.badRequest("Choose the employee.");
            e = employeeRepository.findById(req.employeeId()).orElseThrow(() -> ApiException.notFound("Employee not found"));
        }
        if (req.kind() == null) throw ApiException.badRequest("Choose whether it adds to or takes from the pay.");
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Give the adjustment a name, for example 'Year-end bonus'.");
        if (req.amount() == null || req.amount().signum() <= 0) throw ApiException.badRequest("Enter an amount above zero.");
        if (req.year() == null || req.month() == null || req.month() < 1 || req.month() > 12 || req.year() < 2000 || req.year() > 2200) throw ApiException.badRequest("Choose the payroll month.");
        YearMonth ym = YearMonth.of(req.year(), req.month());
        assertOpen(req.year(), req.month());
        if (e.getHireDate().isAfter(ym.atEndOfMonth()) || (e.getTerminationDate() != null && e.getTerminationDate().isBefore(ym.atDay(1)))) {
            throw ApiException.badRequest(e.fullName() + " was not employed in " + ym + ".");
        }
        a.setEmployee(e);
        a.setKind(req.kind());
        a.setName(req.name().trim());
        a.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        a.setTaxable(req.kind() == AdjustmentKind.EARNING && (req.taxable() == null || req.taxable()));
        a.setPeriodYear(req.year());
        a.setPeriodMonth(req.month());
        a.setNote(HrSupport.blankToNull(req.note()));
    }

    private void assertOpen(int year, int month) {
        runRepository.findByPeriodYearAndPeriodMonth(year, month).ifPresent(r -> {
            if (r.getStatus() != PayrollStatus.DRAFT) {
                throw ApiException.conflict("Payroll for " + YearMonth.of(year, month) + " is already " + r.getStatus().name().toLowerCase() + ", so it can't take changes. Put the adjustment in a later month.");
            }
        });
    }

    private AdjustmentDto dto(PayrollAdjustment a, PayrollRun run) {
        Employee e = a.getEmployee();
        boolean applied = run != null && run.getStatus() != PayrollStatus.DRAFT;
        return new AdjustmentDto(a.getId(), e.getId(), e.getEmployeeNumber(), e.fullName(), a.getKind(), a.getName(), a.getAmount(), a.isTaxable(), a.getPeriodYear(), a.getPeriodMonth(),
                a.getNote(), a.getCreatedByName(), applied ? "APPLIED" : "PENDING", run != null && !applied ? run.getId() : null, a.getCreatedAt());
    }

    private String describe(PayrollAdjustment a) {
        return (a.getKind() == AdjustmentKind.EARNING ? "Added " : "Deducted ") + a.getAmount() + " (" + a.getName() + ") for " + a.getEmployee().fullName() + " in " + YearMonth.of(a.getPeriodYear(), a.getPeriodMonth());
    }

    private PayrollAdjustment adjustment(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Adjustment not found"));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll covers all branches — it's only available to users who aren't limited to certain branches.");
        }
    }
}
