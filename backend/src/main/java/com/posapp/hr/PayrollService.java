package com.posapp.hr;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The monthly payroll. Generating a run works out every employee's pay from their contract(s), unpaid leave, attendance
 * (for employees flagged as tracked), the payroll rules and the income-tax bands; approving posts it to Accounting;
 * paying settles the salaries owed.
 */
@Service
public class PayrollService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PayrollRunRepository runRepository;
    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final PayrollRuleRepository ruleRepository;
    private final TaxBandRepository bandRepository;
    private final PayrollRuleService ruleService;
    private final HrSettingsService settingsService;
    private final LeaveService leaveService;
    private final AccountingService accountingService;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public PayrollService(PayrollRunRepository runRepository, EmployeeRepository employeeRepository, ContractRepository contractRepository,
                          AttendanceRecordRepository attendanceRepository, PayrollRuleRepository ruleRepository, TaxBandRepository bandRepository,
                          PayrollRuleService ruleService, HrSettingsService settingsService, LeaveService leaveService,
                          AccountingService accountingService, BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.runRepository = runRepository;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.attendanceRepository = attendanceRepository;
        this.ruleRepository = ruleRepository;
        this.bandRepository = bandRepository;
        this.ruleService = ruleService;
        this.settingsService = settingsService;
        this.leaveService = leaveService;
        this.accountingService = accountingService;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<PayrollRunDto> list() {
        assertCompanyWide();
        return runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc().stream().map(PayrollRunDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PayrollRunDetailDto get(Long id) {
        assertCompanyWide();
        PayrollRun run = run(id);
        return detail(run, List.of());
    }

    @Transactional(readOnly = true)
    public PayslipDto payslip(Long runId, Long lineId) {
        assertCompanyWide();
        PayrollRun run = run(runId);
        PayrollLine line = run.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Payslip not found"));
        return new PayslipDto(PayrollRunDto.from(run), PayrollLineDto.from(line), line.getBankName(), line.getBankAccount(),
                line.getItems().stream().sorted(Comparator.comparingInt(PayrollItem::getSortOrder)).map(PayrollItemDto::from).toList());
    }

    private PayrollRunDetailDto detail(PayrollRun run, List<String> warnings) {
        List<PayrollLineDto> lines = run.getLines().stream()
                .sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(PayrollLineDto::from).toList();
        return new PayrollRunDetailDto(PayrollRunDto.from(run), lines, warnings);
    }

    // ---- generating ----

    @Transactional
    public PayrollRunDetailDto generate(int year, int month) {
        assertCompanyWide();
        if (month < 1 || month > 12 || year < 2000 || year > 2200) {
            throw ApiException.badRequest("Choose a valid month.");
        }
        ruleService.ensureDefaults();
        PayrollRun run = runRepository.findByPeriodYearAndPeriodMonth(year, month).orElse(null);
        if (run != null && run.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.conflict("Payroll for " + YearMonth.of(year, month) + " is already " + run.getStatus().name().toLowerCase() + ".");
        }
        if (run == null) {
            run = new PayrollRun();
            run.setPeriodYear(year);
            run.setPeriodMonth(month);
            run.setCreatedByName(HrSupport.currentUserName());
        } else {
            run.getLines().clear();
            runRepository.saveAndFlush(run);
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        int daysInMonth = ym.lengthOfMonth();
        HrSettings settings = settingsService.entity();
        Set<DayOfWeek> weekdays = settingsService.workingDays();
        Set<LocalDate> holidays = leaveService.holidaySet(monthStart, monthEnd);
        int workingDaysInMonth = LeaveService.countWorkingDays(monthStart, monthEnd, weekdays, holidays);
        List<PayrollRule> rules = ruleRepository.findByActiveTrueOrderBySortOrderAscIdAsc();
        List<TaxBand> bands = bandRepository.findAllByOrderBySortOrderAscFromAmountAsc();

        List<String> warnings = new ArrayList<>();
        if (rules.isEmpty()) {
            warnings.add("No payroll rules are active — no social-security contributions were calculated.");
        }
        if (bands.isEmpty()) {
            warnings.add("No income-tax bands are set — income tax (PAYE) is 0 for everyone. Enter the bands under Payroll > Rules.");
        }

        List<Contract> allContracts = contractRepository.findAllByOrderByStartDateDescIdDesc();
        Map<Long, List<Contract>> contractsByEmployee = new HashMap<>();
        for (Contract c : allContracts) {
            if (c.getStatus() == ContractStatus.DRAFT) {
                continue;
            }
            contractsByEmployee.computeIfAbsent(c.getEmployee().getId(), k -> new ArrayList<>()).add(c);
        }

        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if (e.getHireDate().isAfter(monthEnd) || (e.getTerminationDate() != null && e.getTerminationDate().isBefore(monthStart))) {
                continue;
            }
            if (e.getStatus() == EmployeeStatus.INACTIVE) {
                warnings.add(e.fullName() + " is marked inactive and was not paid.");
                continue;
            }
            PayrollLine line = buildLine(run, e, contractsByEmployee.getOrDefault(e.getId(), List.of()), monthStart, monthEnd, daysInMonth,
                    workingDaysInMonth, settings, rules, bands, warnings);
            if (line != null) {
                run.getLines().add(line);
            }
        }
        if (run.getLines().isEmpty()) {
            warnings.add("Nobody had a contract covering " + ym + " — add or activate contracts first.");
        }
        totals(run);
        PayrollRun saved = runRepository.save(run);
        auditLogService.record("PAYROLL_GENERATED", "Generated payroll for " + ym + " (" + saved.getLines().size() + " employees, net " + saved.getTotalNet() + ")", "PayrollRun", saved.getId());
        return detail(saved, warnings);
    }

    private PayrollLine buildLine(PayrollRun run, Employee e, List<Contract> contracts, LocalDate monthStart, LocalDate monthEnd,
                                  int daysInMonth, int workingDaysInMonth, HrSettings settings, List<PayrollRule> rules,
                                  List<TaxBand> bands, List<String> warnings) {
        LocalDate empStart = e.getHireDate().isAfter(monthStart) ? e.getHireDate() : monthStart;
        LocalDate empEnd = e.getTerminationDate() != null && e.getTerminationDate().isBefore(monthEnd) ? e.getTerminationDate() : monthEnd;

        BigDecimal baseTotal = BigDecimal.ZERO;
        int payableDays = 0;
        Contract latest = null;
        LocalDate firstDay = null, lastDay = null;
        Map<String, BigDecimal[]> earnings = new LinkedHashMap<>(); // key -> {amount, taxable(1/0)}
        Map<String, BigDecimal> fixedDeductions = new LinkedHashMap<>();
        for (Contract c : contracts) {
            LocalDate cEnd = ContractService.effectiveEnd(c);
            LocalDate s = max(max(c.getStartDate(), empStart), monthStart);
            LocalDate en = min(min(cEnd != null ? cEnd : monthEnd, empEnd), monthEnd);
            if (en.isBefore(s)) {
                continue;
            }
            int days = (int) ChronoUnit.DAYS.between(s, en) + 1;
            BigDecimal frac = days >= daysInMonth ? BigDecimal.ONE : BigDecimal.valueOf(days).divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP);
            payableDays += days;
            baseTotal = baseTotal.add(c.getBaseSalary().multiply(frac));
            if (latest == null || c.getStartDate().isAfter(latest.getStartDate())) {
                latest = c;
            }
            firstDay = firstDay == null || s.isBefore(firstDay) ? s : firstDay;
            lastDay = lastDay == null || en.isAfter(lastDay) ? en : lastDay;
            for (ContractComponent comp : c.getComponents()) {
                BigDecimal amount = comp.getAmount().multiply(frac);
                if (comp.getKind() == ComponentKind.EARNING) {
                    earnings.merge(comp.getName() + "|" + comp.isTaxable(), new BigDecimal[]{amount, comp.isTaxable() ? BigDecimal.ONE : BigDecimal.ZERO},
                            (a, b) -> new BigDecimal[]{a[0].add(b[0]), a[1]});
                } else {
                    fixedDeductions.merge(comp.getName(), amount, BigDecimal::add);
                }
            }
        }
        if (latest == null) {
            if (e.getStatus() == EmployeeStatus.ACTIVE) {
                warnings.add(e.fullName() + " has no contract covering this month and was not paid.");
            }
            return null;
        }

        PayrollLine line = new PayrollLine();
        line.setRun(run);
        line.setEmployeeId(e.getId());
        line.setEmployeeNumber(e.getEmployeeNumber());
        line.setEmployeeName(e.fullName());
        line.setDepartmentName(e.getDepartment() != null ? e.getDepartment().getName() : null);
        line.setBankName(e.getBankName());
        line.setBankAccount(e.getBankAccount());
        line.setBranchId(e.getBranchId());
        line.setContractId(latest.getId());
        line.setContractNumber(latest.getContractNumber());
        line.setBaseSalary(latest.getBaseSalary());
        line.setDaysInMonth(daysInMonth);
        line.setPayableDays(payableDays);

        int order = 0;
        List<PayrollItem> items = new ArrayList<>();
        BigDecimal basic = money(baseTotal);
        String basicName = payableDays >= daysInMonth ? "Basic salary" : "Basic salary (" + payableDays + " of " + daysInMonth + " days)";
        items.add(item(line, ItemKind.EARNING, basicName, basic, true, null, ++order));
        BigDecimal gross = basic;
        BigDecimal taxable = basic;
        for (Map.Entry<String, BigDecimal[]> en : earnings.entrySet()) {
            String name = en.getKey().substring(0, en.getKey().lastIndexOf('|'));
            BigDecimal amount = money(en.getValue()[0]);
            boolean tx = en.getValue()[1].signum() > 0;
            items.add(item(line, ItemKind.EARNING, name, amount, tx, null, ++order));
            gross = gross.add(amount);
            if (tx) {
                taxable = taxable.add(amount);
            }
        }

        // Unpaid leave, and (for tracked employees) absences and overtime.
        BigDecimal dailyRate = workingDaysInMonth > 0 ? latest.getBaseSalary().divide(BigDecimal.valueOf(workingDaysInMonth), 10, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal unpaidDays = leaveService.approvedLeaveDays(e.getId(), firstDay, lastDay, false);
        BigDecimal absentDays = BigDecimal.ZERO;
        BigDecimal overtimeHours = BigDecimal.ZERO;
        if (e.isAttendanceTracked()) {
            List<LeaveRequest> approved = leaveService.approvedRequests(e.getId());
            Set<LocalDate> holidays = leaveService.holidaySet(firstDay, lastDay);
            Set<DayOfWeek> weekdays = settingsService.workingDays();
            for (AttendanceRecord a : attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(e.getId(), firstDay, lastDay)) {
                overtimeHours = overtimeHours.add(a.getOvertimeHours());
                if (a.getStatus() == AttendanceStatus.ABSENT && weekdays.contains(a.getWorkDate().getDayOfWeek()) && !holidays.contains(a.getWorkDate())
                        && approved.stream().noneMatch(r -> !a.getWorkDate().isBefore(r.getStartDate()) && !a.getWorkDate().isAfter(r.getEndDate()))) {
                    absentDays = absentDays.add(BigDecimal.ONE);
                }
            }
        }
        BigDecimal basicLeft = basic;
        if (unpaidDays.signum() > 0 && dailyRate.signum() > 0) {
            BigDecimal cut = money(dailyRate.multiply(unpaidDays)).min(basicLeft);
            items.add(item(line, ItemKind.EARNING, "Unpaid leave (" + unpaidDays.stripTrailingZeros().toPlainString() + " day(s))", cut.negate(), true, null, ++order));
            gross = gross.subtract(cut);
            taxable = taxable.subtract(cut);
            basicLeft = basicLeft.subtract(cut);
        }
        if (absentDays.signum() > 0 && dailyRate.signum() > 0) {
            BigDecimal cut = money(dailyRate.multiply(absentDays)).min(basicLeft);
            items.add(item(line, ItemKind.EARNING, "Absence (" + absentDays.stripTrailingZeros().toPlainString() + " day(s))", cut.negate(), true, null, ++order));
            gross = gross.subtract(cut);
            taxable = taxable.subtract(cut);
            basicLeft = basicLeft.subtract(cut);
        }
        if (overtimeHours.signum() > 0 && dailyRate.signum() > 0 && settings.getStandardDailyHours().signum() > 0) {
            BigDecimal hourly = dailyRate.divide(settings.getStandardDailyHours(), 10, RoundingMode.HALF_UP);
            BigDecimal pay = money(hourly.multiply(overtimeHours).multiply(settings.getOvertimeMultiplier()));
            items.add(item(line, ItemKind.EARNING, "Overtime (" + overtimeHours.stripTrailingZeros().toPlainString() + " h)", pay, true, null, ++order));
            gross = gross.add(pay);
            taxable = taxable.add(pay);
        }
        line.setUnpaidLeaveDays(unpaidDays);
        line.setAbsentDays(absentDays);
        line.setOvertimeHours(overtimeHours);

        // Statutory contributions that don't depend on the net pay.
        BigDecimal employeeContrib = BigDecimal.ZERO;
        BigDecimal employerContrib = BigDecimal.ZERO;
        List<PayrollRule> netRules = new ArrayList<>();
        for (PayrollRule rule : rules) {
            if (rule.getBase() == RuleBase.NET) {
                netRules.add(rule);
                continue;
            }
            BigDecimal amount = ruleAmount(rule, rule.getBase() == RuleBase.BASIC ? basicLeft : gross);
            if (amount.signum() == 0) {
                continue;
            }
            if (rule.getKind() == RuleKind.EMPLOYEE_DEDUCTION) {
                items.add(item(line, ItemKind.DEDUCTION, rule.getName(), amount, false, rule.getTarget(), ++order));
                employeeContrib = employeeContrib.add(amount);
                if (rule.isDeductibleFromTax()) {
                    taxable = taxable.subtract(amount);
                }
            } else {
                items.add(item(line, ItemKind.EMPLOYER, rule.getName(), amount, false, rule.getTarget(), ++order));
                employerContrib = employerContrib.add(amount);
            }
        }

        // Income tax.
        BigDecimal taxableForTax = taxable.max(BigDecimal.ZERO);
        BigDecimal paye = settings.isTaxBandsAnnual()
                ? money(progressive(taxableForTax.multiply(BigDecimal.valueOf(12)), bands).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP))
                : money(progressive(taxableForTax, bands));
        if (paye.signum() > 0) {
            items.add(item(line, ItemKind.TAX, "Income tax (PAYE)", paye, false, null, ++order));
        }

        // Fixed deductions from the contract (loan repayments...) come after tax.
        BigDecimal otherDeductions = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> d : fixedDeductions.entrySet()) {
            BigDecimal amount = money(d.getValue());
            if (amount.signum() > 0) {
                items.add(item(line, ItemKind.DEDUCTION, d.getKey(), amount, false, PayableTarget.OTHER, ++order));
                otherDeductions = otherDeductions.add(amount);
            }
        }

        // Rules on the net pay (e.g. CBHI) come last.
        BigDecimal netBefore = gross.subtract(paye).subtract(employeeContrib).subtract(otherDeductions);
        for (PayrollRule rule : netRules) {
            BigDecimal amount = ruleAmount(rule, netBefore.max(BigDecimal.ZERO));
            if (amount.signum() == 0) {
                continue;
            }
            if (rule.getKind() == RuleKind.EMPLOYEE_DEDUCTION) {
                items.add(item(line, ItemKind.DEDUCTION, rule.getName(), amount, false, rule.getTarget(), ++order));
                employeeContrib = employeeContrib.add(amount);
            } else {
                items.add(item(line, ItemKind.EMPLOYER, rule.getName(), amount, false, rule.getTarget(), ++order));
                employerContrib = employerContrib.add(amount);
            }
        }

        BigDecimal net = gross.subtract(paye).subtract(employeeContrib).subtract(otherDeductions);
        if (net.signum() < 0) {
            warnings.add(e.fullName() + "'s deductions are more than their pay — net pay is negative. Check their contract deductions.");
        }
        line.setGrossPay(gross);
        line.setTaxablePay(taxableForTax);
        line.setPayeTax(paye);
        line.setEmployeeContributions(employeeContrib);
        line.setOtherDeductions(otherDeductions);
        line.setNetPay(net);
        line.setEmployerContributions(employerContrib);
        line.getItems().addAll(items);
        return line;
    }

    private BigDecimal ruleAmount(PayrollRule rule, BigDecimal base) {
        BigDecimal counted = rule.getCapAmount() != null ? base.min(rule.getCapAmount()) : base;
        if (counted.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return money(counted.multiply(rule.getRatePercent()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /** Progressive tax: each band taxes the part of the income inside it. */
    static BigDecimal progressive(BigDecimal income, List<TaxBand> bands) {
        BigDecimal tax = BigDecimal.ZERO;
        for (TaxBand b : bands) {
            BigDecimal upper = b.getToAmount() != null ? income.min(b.getToAmount()) : income;
            BigDecimal inBand = upper.subtract(b.getFromAmount());
            if (inBand.signum() > 0) {
                tax = tax.add(inBand.multiply(b.getRatePercent()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            }
        }
        return tax;
    }

    private static PayrollItem item(PayrollLine line, ItemKind kind, String name, BigDecimal amount, boolean taxable, PayableTarget target, int order) {
        PayrollItem i = new PayrollItem();
        i.setLine(line);
        i.setKind(kind);
        i.setName(name);
        i.setAmount(amount);
        i.setTaxable(taxable);
        i.setTarget(target);
        i.setSortOrder(order);
        return i;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private void totals(PayrollRun run) {
        BigDecimal gross = BigDecimal.ZERO, paye = BigDecimal.ZERO, ee = BigDecimal.ZERO, other = BigDecimal.ZERO, net = BigDecimal.ZERO, er = BigDecimal.ZERO;
        for (PayrollLine l : run.getLines()) {
            gross = gross.add(l.getGrossPay());
            paye = paye.add(l.getPayeTax());
            ee = ee.add(l.getEmployeeContributions());
            other = other.add(l.getOtherDeductions());
            net = net.add(l.getNetPay());
            er = er.add(l.getEmployerContributions());
        }
        run.setTotalGross(gross);
        run.setTotalPaye(paye);
        run.setTotalEmployeeContributions(ee);
        run.setTotalOtherDeductions(other);
        run.setTotalNet(net);
        run.setTotalEmployerContributions(er);
    }

    // ---- approve / pay / delete ----

    @Transactional
    public PayrollRunDto approve(Long id) {
        assertCompanyWide();
        PayrollRun run = run(id);
        if (run.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.conflict("Only a draft payroll can be approved.");
        }
        if (run.getLines().isEmpty()) {
            throw ApiException.badRequest("This payroll has nobody in it.");
        }
        String memo = "Payroll " + YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        for (Map.Entry<Long, List<PayrollLine>> g : byBranch(run).entrySet()) {
            BigDecimal gross = BigDecimal.ZERO, paye = BigDecimal.ZERO, ss = BigDecimal.ZERO, other = BigDecimal.ZERO, net = BigDecimal.ZERO, er = BigDecimal.ZERO;
            for (PayrollLine l : g.getValue()) {
                gross = gross.add(l.getGrossPay());
                paye = paye.add(l.getPayeTax());
                net = net.add(l.getNetPay());
                er = er.add(l.getEmployerContributions());
                for (PayrollItem i : l.getItems()) {
                    if (i.getKind() == ItemKind.DEDUCTION || i.getKind() == ItemKind.EMPLOYER) {
                        if (i.getTarget() == PayableTarget.SOCIAL_SECURITY) {
                            ss = ss.add(i.getAmount());
                        } else {
                            other = other.add(i.getAmount());
                        }
                    }
                }
            }
            accountingService.postPayrollApproval(run.getId(), memo, gross, er, paye, ss, other, net, g.getKey());
        }
        run.setStatus(PayrollStatus.APPROVED);
        run.setApprovedByName(HrSupport.currentUserName());
        run.setApprovedAt(Instant.now());
        runRepository.save(run);
        auditLogService.record("PAYROLL_APPROVED", "Approved " + memo + " (net " + run.getTotalNet() + ")", "PayrollRun", id);
        return PayrollRunDto.from(run);
    }

    @Transactional
    public PayrollRunDto pay(Long id, LocalDate paidDate) {
        assertCompanyWide();
        PayrollRun run = run(id);
        if (run.getStatus() != PayrollStatus.APPROVED) {
            throw ApiException.conflict(run.getStatus() == PayrollStatus.PAID ? "This payroll is already paid." : "Approve the payroll before paying it.");
        }
        String memo = "Salaries paid " + YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        for (Map.Entry<Long, List<PayrollLine>> g : byBranch(run).entrySet()) {
            BigDecimal net = g.getValue().stream().map(PayrollLine::getNetPay).reduce(BigDecimal.ZERO, BigDecimal::add);
            accountingService.postPayrollPayment(run.getId(), memo, net, g.getKey());
        }
        run.setStatus(PayrollStatus.PAID);
        run.setPaidDate(paidDate != null ? paidDate : LocalDate.now());
        runRepository.save(run);
        auditLogService.record("PAYROLL_PAID", "Paid " + memo + " (" + run.getTotalNet() + ")", "PayrollRun", id);
        return PayrollRunDto.from(run);
    }

    @Transactional
    public void delete(Long id) {
        assertCompanyWide();
        PayrollRun run = run(id);
        if (run.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.conflict("Only a draft payroll can be deleted.");
        }
        runRepository.delete(run);
        auditLogService.record("PAYROLL_DELETED", "Deleted draft payroll " + YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()), "PayrollRun", id);
    }

    private Map<Long, List<PayrollLine>> byBranch(PayrollRun run) {
        Map<Long, List<PayrollLine>> groups = new LinkedHashMap<>();
        for (PayrollLine l : run.getLines()) {
            groups.computeIfAbsent(l.getBranchId(), k -> new ArrayList<>()).add(l);
        }
        return groups;
    }

    PayrollRun run(Long id) {
        return runRepository.findById(id).orElseThrow(() -> ApiException.notFound("Payroll run not found"));
    }

    /** Payroll covers the whole company, so staff limited to some branches can't run or read it. */
    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll covers all branches — it's only available to users who aren't limited to certain branches.");
        }
    }
}
