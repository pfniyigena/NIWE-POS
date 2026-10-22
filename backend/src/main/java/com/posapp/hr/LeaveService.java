package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Leave types, public holidays, leave requests (with approval) and leave balances. */
@Service
public class LeaveService {

    private final LeaveTypeRepository typeRepository;
    private final LeaveRequestRepository requestRepository;
    private final PublicHolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final HrSettingsService settingsService;
    private final HrSettingsRepository settingsRepository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public LeaveService(LeaveTypeRepository typeRepository, LeaveRequestRepository requestRepository, PublicHolidayRepository holidayRepository,
                        EmployeeRepository employeeRepository, HrSettingsService settingsService, HrSettingsRepository settingsRepository,
                        BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.typeRepository = typeRepository;
        this.requestRepository = requestRepository;
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
        this.settingsService = settingsService;
        this.settingsRepository = settingsRepository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    // ---- leave types ----

    /** Creates the ready-made leave types the first time they are needed (editable afterwards, never brought back). */
    @Transactional
    public void ensureDefaults() {
        HrSettings settings = settingsService.entity();
        if (settings.isLeaveDefaultsLoaded()) {
            return;
        }
        if (typeRepository.count() == 0) {
            addType("Annual leave", 18, true);
            addType("Sick leave", 30, true);
            addType("Maternity leave", 98, true);
            addType("Paternity leave", 4, true);
            addType("Unpaid leave", 0, false);
        }
        settings.setLeaveDefaultsLoaded(true);
        settingsRepository.save(settings);
    }

    private void addType(String name, int days, boolean paid) {
        LeaveType t = new LeaveType();
        t.setName(name);
        t.setDaysPerYear(days);
        t.setPaid(paid);
        t.setPayoutOnExit(name.equals("Annual leave"));
        typeRepository.save(t);
    }

    @Transactional
    public List<LeaveTypeDto> listTypes() {
        ensureDefaults();
        return typeRepository.findAllByOrderByNameAsc().stream().map(LeaveTypeDto::from).toList();
    }

    @Transactional
    public LeaveTypeDto createType(LeaveTypeRequest r) {
        String name = r.name().trim();
        if (typeRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already a leave type called " + name + ".");
        }
        LeaveType t = new LeaveType();
        applyType(t, r, name);
        LeaveType saved = typeRepository.save(t);
        auditLogService.record("LEAVE_TYPE_CREATED", "Created leave type " + saved.getName(), "LeaveType", saved.getId());
        return LeaveTypeDto.from(saved);
    }

    @Transactional
    public LeaveTypeDto updateType(Long id, LeaveTypeRequest r) {
        LeaveType t = type(id);
        String name = r.name().trim();
        if (!t.getName().equalsIgnoreCase(name) && typeRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already a leave type called " + name + ".");
        }
        applyType(t, r, name);
        LeaveType saved = typeRepository.save(t);
        auditLogService.record("LEAVE_TYPE_UPDATED", "Updated leave type " + saved.getName(), "LeaveType", saved.getId());
        return LeaveTypeDto.from(saved);
    }

    @Transactional
    public void deleteType(Long id) {
        LeaveType t = type(id);
        if (requestRepository.existsByLeaveTypeId(id)) {
            throw ApiException.conflict("Leave type " + t.getName() + " has requests. Mark it inactive instead.");
        }
        typeRepository.delete(t);
        auditLogService.record("LEAVE_TYPE_DELETED", "Deleted leave type " + t.getName(), "LeaveType", id);
    }

    private void applyType(LeaveType t, LeaveTypeRequest r, String name) {
        if (r.daysPerYear() != null && r.daysPerYear() < 0) {
            throw ApiException.badRequest("Days per year can't be negative.");
        }
        t.setName(name);
        if (r.daysPerYear() != null) {
            t.setDaysPerYear(r.daysPerYear());
        }
        if (r.paid() != null) {
            t.setPaid(r.paid());
        }
        if (r.active() != null) {
            t.setActive(r.active());
        }
        if (r.accrualMode() != null) {
            t.setAccrualMode(r.accrualMode());
        }
        if (r.carryOverMaxDays() != null) {
            if (r.carryOverMaxDays() < 0 || r.carryOverMaxDays() > 365) {
                throw ApiException.badRequest("Days that carry over must be between 0 and 365.");
            }
            t.setCarryOverMaxDays(r.carryOverMaxDays());
        }
        if (r.payoutOnExit() != null) {
            t.setPayoutOnExit(r.payoutOnExit());
        }
    }

    /** Ids of the active leave types whose unused days are paid out when an employee leaves. */
    @Transactional(readOnly = true)
    public Set<Long> payoutTypeIds() {
        ensureDefaults();
        Set<Long> ids = new HashSet<>();
        for (LeaveType t : typeRepository.findAllByOrderByNameAsc()) {
            if (t.isActive() && t.isPayoutOnExit()) ids.add(t.getId());
        }
        return ids;
    }

    private LeaveType type(Long id) {
        return typeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leave type not found"));
    }

    // ---- public holidays ----

    @Transactional(readOnly = true)
    public List<HolidayDto> listHolidays() {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream().map(HolidayDto::from).toList();
    }

    @Transactional
    public HolidayDto createHoliday(HolidayRequest r) {
        if (holidayRepository.existsByHolidayDate(r.date())) {
            throw ApiException.conflict("There is already a holiday on " + r.date() + ".");
        }
        PublicHoliday h = new PublicHoliday();
        h.setHolidayDate(r.date());
        h.setName(r.name().trim());
        PublicHoliday saved = holidayRepository.save(h);
        auditLogService.record("HOLIDAY_CREATED", "Added public holiday " + saved.getName() + " (" + saved.getHolidayDate() + ")", "PublicHoliday", saved.getId());
        return HolidayDto.from(saved);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        PublicHoliday h = holidayRepository.findById(id).orElseThrow(() -> ApiException.notFound("Holiday not found"));
        holidayRepository.delete(h);
        auditLogService.record("HOLIDAY_DELETED", "Removed public holiday " + h.getName(), "PublicHoliday", id);
    }

    Set<LocalDate> holidaySet(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetween(from, to).stream().map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());
    }

    /** Working days from..to inclusive: the configured working weekdays that aren't public holidays. */
    @Transactional
    public int workingDays(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return countWorkingDays(from, to, settingsService.workingDays(), holidaySet(from, to));
    }

    static int countWorkingDays(LocalDate from, LocalDate to, Set<DayOfWeek> weekdays, Set<LocalDate> holidays) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (weekdays.contains(d.getDayOfWeek()) && !holidays.contains(d)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Working days of the employee's APPROVED leave inside from..to (clipped to the range). {@code paid} null = all
     * types, true = only paid types, false = only unpaid types.
     */
    @Transactional
    public BigDecimal approvedLeaveDays(Long employeeId, LocalDate from, LocalDate to, Boolean paid) {
        Set<DayOfWeek> weekdays = settingsService.workingDays();
        Set<LocalDate> holidays = holidaySet(from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveRequest r : requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId)) {
            if (r.getStatus() != LeaveStatus.APPROVED || (paid != null && r.getLeaveType().isPaid() != paid)) {
                continue;
            }
            total = total.add(daysOf(r.getStartDate(), r.getEndDate(), from, to, r.isStartHalf(), r.isEndHalf(), weekdays, holidays));
        }
        return total;
    }

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static boolean isWorking(LocalDate d, Set<DayOfWeek> weekdays, Set<LocalDate> holidays) {
        return weekdays.contains(d.getDayOfWeek()) && !holidays.contains(d);
    }

    /** Working days of a leave (reqStart..reqEnd) that fall inside from..to; a half day at the start or end counts 0.5, and a single half day is 0.5. */
    static BigDecimal daysOf(LocalDate reqStart, LocalDate reqEnd, LocalDate from, LocalDate to, boolean startHalf, boolean endHalf, Set<DayOfWeek> weekdays, Set<LocalDate> holidays) {
        LocalDate s = reqStart.isBefore(from) ? from : reqStart;
        LocalDate e = reqEnd.isAfter(to) ? to : reqEnd;
        if (e.isBefore(s)) {
            return BigDecimal.ZERO;
        }
        int n = countWorkingDays(s, e, weekdays, holidays);
        BigDecimal days = BigDecimal.valueOf(n);
        if (reqStart.equals(reqEnd)) {
            if ((startHalf || endHalf) && n > 0) {
                days = HALF;
            }
        } else {
            if (startHalf && s.equals(reqStart) && isWorking(s, weekdays, holidays)) {
                days = days.subtract(HALF);
            }
            if (endHalf && e.equals(reqEnd) && isWorking(e, weekdays, holidays)) {
                days = days.subtract(HALF);
            }
        }
        return days;
    }

    /** What a leave from..to would use, counting a half day at the start and/or end as 0.5. */
    @Transactional
    public BigDecimal leaveDays(LocalDate from, LocalDate to, boolean startHalf, boolean endHalf) {
        if (to.isBefore(from)) {
            return BigDecimal.ZERO;
        }
        return daysOf(from, to, from, to, startHalf, endHalf, settingsService.workingDays(), holidaySet(from, to));
    }

    /** The employee's approved leave requests (used by payroll to avoid counting an absence on a day of approved leave). */
    @Transactional(readOnly = true)
    List<LeaveRequest> approvedRequests(Long employeeId) {
        return requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream().filter(r -> r.getStatus() == LeaveStatus.APPROVED).toList();
    }

    // ---- requests ----

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> listRequests(LeaveStatus status, Long employeeId, LocalDate from, LocalDate to) {
        List<LeaveRequest> all = from != null && to != null
                ? requestRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(to, from)
                : requestRepository.findAllByOrderByStartDateDescIdDesc();
        return all.stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> employeeId == null || r.getEmployee().getId().equals(employeeId))
                .filter(r -> branchAccessService.canAccess(r.getEmployee().getBranchId()))
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .map(LeaveRequestDto::from).toList();
    }

    @Transactional
    public LeaveRequestDto create(LeaveRequestRequest r, Employee currentEmployee) {
        Employee employee;
        if (r.employeeId() != null) {
            employee = employeeRepository.findById(r.employeeId()).orElseThrow(() -> ApiException.notFound("Employee not found"));
        } else if (currentEmployee != null) {
            employee = currentEmployee;
        } else {
            throw ApiException.badRequest("Choose the employee.");
        }
        branchAccessService.assertCanAccess(employee.getBranchId());
        return createFor(employee, r);
    }

    /** The signed-in employee asks for their own leave (no branch rule: it is their own record). */
    @Transactional
    public LeaveRequestDto createOwn(Employee me, LeaveRequestRequest r) {
        return createFor(me, r);
    }

    private LeaveRequestDto createFor(Employee employee, LeaveRequestRequest r) {
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(employee.fullName() + " has left the company.");
        }
        LeaveType type = type(r.leaveTypeId());
        if (!type.isActive()) {
            throw ApiException.badRequest("Leave type " + type.getName() + " is inactive.");
        }
        if (r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("The end date can't be before the start date.");
        }
        if (r.startDate().getYear() != r.endDate().getYear()) {
            throw ApiException.badRequest("A leave request can't span two years — please split it into two requests.");
        }
        if (workingDays(r.startDate(), r.endDate()) <= 0) {
            throw ApiException.badRequest("That period has no working days (only weekends or public holidays).");
        }
        boolean startHalf = Boolean.TRUE.equals(r.startHalfDay());
        boolean endHalf = Boolean.TRUE.equals(r.endHalfDay());
        BigDecimal days = leaveDays(r.startDate(), r.endDate(), startHalf, endHalf);
        checkOverlap(employee.getId(), r.startDate(), r.endDate(), null);
        checkBalance(employee, type, r.startDate().getYear(), days, null, r.startDate().getMonthValue());

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(type);
        lr.setStartDate(r.startDate());
        lr.setEndDate(r.endDate());
        lr.setDays(days);
        lr.setStartHalf(startHalf);
        lr.setEndHalf(endHalf);
        lr.setReason(HrSupport.blankToNull(r.reason()));
        LeaveRequest saved = requestRepository.save(lr);
        auditLogService.record("LEAVE_REQUESTED", "Leave request for " + employee.fullName() + ": " + type.getName() + " " + r.startDate() + " to " + r.endDate() + " (" + days.stripTrailingZeros().toPlainString() + " days)", "LeaveRequest", saved.getId());
        return LeaveRequestDto.from(saved);
    }

    @Transactional
    public LeaveRequestDto approve(Long id, DecisionRequest d) {
        LeaveRequest lr = request(id);
        branchAccessService.assertCanAccess(lr.getEmployee().getBranchId());
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Only a pending request can be approved.");
        }
        checkOverlap(lr.getEmployee().getId(), lr.getStartDate(), lr.getEndDate(), lr.getId());
        checkBalance(lr.getEmployee(), lr.getLeaveType(), lr.getStartDate().getYear(), lr.getDays(), lr.getId(), lr.getStartDate().getMonthValue());
        decide(lr, LeaveStatus.APPROVED, d);
        auditLogService.record("LEAVE_APPROVED", "Approved " + lr.getLeaveType().getName() + " for " + lr.getEmployee().fullName() + " (" + lr.getStartDate() + " to " + lr.getEndDate() + ")", "LeaveRequest", id);
        return LeaveRequestDto.from(lr);
    }

    @Transactional
    public LeaveRequestDto reject(Long id, DecisionRequest d) {
        LeaveRequest lr = request(id);
        branchAccessService.assertCanAccess(lr.getEmployee().getBranchId());
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("Only a pending request can be rejected.");
        }
        decide(lr, LeaveStatus.REJECTED, d);
        auditLogService.record("LEAVE_REJECTED", "Rejected " + lr.getLeaveType().getName() + " for " + lr.getEmployee().fullName(), "LeaveRequest", id);
        return LeaveRequestDto.from(lr);
    }

    @Transactional
    public LeaveRequestDto cancel(Long id, DecisionRequest d) {
        LeaveRequest lr = request(id);
        branchAccessService.assertCanAccess(lr.getEmployee().getBranchId());
        if (lr.getStatus() != LeaveStatus.PENDING && lr.getStatus() != LeaveStatus.APPROVED) {
            throw ApiException.conflict("This request is already " + lr.getStatus().name().toLowerCase() + ".");
        }
        decide(lr, LeaveStatus.CANCELLED, d);
        auditLogService.record("LEAVE_CANCELLED", "Cancelled " + lr.getLeaveType().getName() + " for " + lr.getEmployee().fullName(), "LeaveRequest", id);
        return LeaveRequestDto.from(lr);
    }

    /** An employee withdraws their own request while it is still waiting for a decision. */
    @Transactional
    public LeaveRequestDto cancelOwn(Employee me, Long id, DecisionRequest d) {
        LeaveRequest lr = request(id);
        if (!lr.getEmployee().getId().equals(me.getId())) {
            throw ApiException.notFound("Leave request not found");
        }
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict(lr.getStatus() == LeaveStatus.APPROVED
                    ? "This leave is already approved. Ask HR to cancel it."
                    : "This request is already " + lr.getStatus().name().toLowerCase() + ".");
        }
        decide(lr, LeaveStatus.CANCELLED, d);
        auditLogService.record("LEAVE_CANCELLED", "Withdrew " + lr.getLeaveType().getName() + " (" + lr.getStartDate() + " to " + lr.getEndDate() + ")", "LeaveRequest", id);
        return LeaveRequestDto.from(lr);
    }

    /** One employee's own requests, newest first (no branch rule). */
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> requestsOfEmployee(Long employeeId) {
        return requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream().map(LeaveRequestDto::from).toList();
    }

    private void decide(LeaveRequest lr, LeaveStatus status, DecisionRequest d) {
        lr.setStatus(status);
        lr.setDecidedBy(HrSupport.currentUserName());
        lr.setDecidedAt(Instant.now());
        lr.setDecisionNote(d != null ? HrSupport.blankToNull(d.note()) : null);
        requestRepository.save(lr);
    }

    private void checkOverlap(Long employeeId, LocalDate from, LocalDate to, Long ignoreId) {
        for (LeaveRequest o : requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId)) {
            if ((o.getStatus() == LeaveStatus.PENDING || o.getStatus() == LeaveStatus.APPROVED)
                    && (ignoreId == null || !o.getId().equals(ignoreId))
                    && !o.getStartDate().isAfter(to) && !o.getEndDate().isBefore(from)) {
                throw ApiException.conflict("That overlaps another leave request (" + o.getStartDate() + " to " + o.getEndDate() + ").");
            }
        }
    }

    /** What an employee may take of a leave type in a year, counting days earned so far (monthly accrual) up to {@code uptoMonth}. */
    BigDecimal entitlement(LeaveType t, Employee e, int year, int uptoMonth) {
        BigDecimal full = BigDecimal.valueOf(t.getDaysPerYear());
        if (t.getAccrualMode() != LeaveAccrual.MONTHLY) {
            return full;
        }
        int hireYear = e.getHireDate().getYear();
        if (hireYear > year) {
            return BigDecimal.ZERO;
        }
        int first = hireYear == year ? e.getHireDate().getMonthValue() : 1;
        int months = Math.max(0, Math.min(12, uptoMonth) - first + 1);
        return full.multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    /** Unused days of earlier years that carry into {@code year} (at most the type's carry-over limit; only approved leave counts as used). */
    BigDecimal carriedOver(LeaveType t, Employee e, int year, List<LeaveRequest> requests) {
        int max = t.getCarryOverMaxDays();
        if (max <= 0 || t.getDaysPerYear() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal carry = BigDecimal.ZERO;
        for (int y = Math.max(e.getHireDate().getYear(), year - 30); y < year; y++) {
            BigDecimal used = BigDecimal.ZERO;
            for (LeaveRequest r : requests) {
                if (r.getLeaveType().getId().equals(t.getId()) && r.getStartDate().getYear() == y && r.getStatus() == LeaveStatus.APPROVED) {
                    used = used.add(r.getDays());
                }
            }
            BigDecimal unused = entitlement(t, e, y, 12).add(carry).subtract(used).max(BigDecimal.ZERO);
            carry = unused.min(BigDecimal.valueOf(max));
        }
        return carry;
    }

    private void checkBalance(Employee employee, LeaveType type, int year, BigDecimal days, Long ignoreId, int startMonth) {
        if (type.getDaysPerYear() <= 0) {
            return;
        }
        List<LeaveRequest> requests = requestRepository.findByEmployeeIdOrderByStartDateDesc(employee.getId());
        BigDecimal used = BigDecimal.ZERO;
        for (LeaveRequest o : requests) {
            if (o.getLeaveType().getId().equals(type.getId()) && o.getStartDate().getYear() == year
                    && (o.getStatus() == LeaveStatus.APPROVED || o.getStatus() == LeaveStatus.PENDING)
                    && (ignoreId == null || !o.getId().equals(ignoreId))) {
                used = used.add(o.getDays());
            }
        }
        BigDecimal available = entitlement(type, employee, year, startMonth).add(carriedOver(type, employee, year, requests));
        BigDecimal remaining = available.subtract(used);
        if (days.compareTo(remaining) > 0) {
            throw ApiException.conflict(employee.fullName() + " has only " + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + " day(s) of " + type.getName() + " left in " + year + (type.getAccrualMode() == LeaveAccrual.MONTHLY ? " by then (earned monthly)" : "")
                    + " (needs " + days.stripTrailingZeros().toPlainString() + ").");
        }
    }

    private LeaveRequest request(Long id) {
        return requestRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leave request not found"));
    }

    // ---- balances ----

    @Transactional
    public List<LeaveBalanceDto> balances(int year, Long employeeId) {
        ensureDefaults();
        List<LeaveType> types = typeRepository.findAllByOrderByNameAsc().stream().filter(LeaveType::isActive).toList();
        LocalDate today = LocalDate.now();
        int accrualMonth = year < today.getYear() ? 12 : year == today.getYear() ? today.getMonthValue() : 0;
        List<LeaveBalanceDto> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if (e.getStatus() == EmployeeStatus.TERMINATED && e.getTerminationDate() != null && e.getTerminationDate().getYear() < year) {
                continue;
            }
            if ((employeeId != null && !e.getId().equals(employeeId)) || !branchAccessService.canAccess(e.getBranchId())) {
                continue;
            }
            out.addAll(balancesOf(e, types, year, accrualMonth));
        }
        return out;
    }

    private List<LeaveBalanceDto> balancesOf(Employee e, List<LeaveType> types, int year, int accrualMonth) {
        List<LeaveBalanceDto> out = new ArrayList<>();
        List<LeaveRequest> requests = requestRepository.findByEmployeeIdOrderByStartDateDesc(e.getId());
        for (LeaveType t : types) {
            BigDecimal used = BigDecimal.ZERO;
            BigDecimal pending = BigDecimal.ZERO;
            for (LeaveRequest r : requests) {
                if (r.getLeaveType().getId().equals(t.getId()) && r.getStartDate().getYear() == year) {
                    if (r.getStatus() == LeaveStatus.APPROVED) {
                        used = used.add(r.getDays());
                    } else if (r.getStatus() == LeaveStatus.PENDING) {
                        pending = pending.add(r.getDays());
                    }
                }
            }
            boolean unlimited = t.getDaysPerYear() <= 0;
            BigDecimal entitled = BigDecimal.valueOf(t.getDaysPerYear());
            BigDecimal accrued = unlimited ? BigDecimal.ZERO : entitlement(t, e, year, accrualMonth);
            BigDecimal carried = unlimited ? BigDecimal.ZERO : carriedOver(t, e, year, requests);
            BigDecimal remaining = unlimited ? BigDecimal.ZERO : accrued.add(carried).subtract(used).subtract(pending);
            out.add(new LeaveBalanceDto(e.getId(), e.getEmployeeNumber(), e.fullName(), t.getId(), t.getName(), unlimited, entitled, used, pending, remaining, accrued, carried, t.getAccrualMode()));
        }
        return out;
    }

    /** The signed-in employee's own balances: no branch filter, since it is their own record. */
    @Transactional
    public List<LeaveBalanceDto> balancesOfEmployee(Employee e, int year) {
        ensureDefaults();
        List<LeaveType> types = typeRepository.findAllByOrderByNameAsc().stream().filter(LeaveType::isActive).toList();
        LocalDate today = LocalDate.now();
        int accrualMonth = year < today.getYear() ? 12 : year == today.getYear() ? today.getMonthValue() : 0;
        return balancesOf(e, types, year, accrualMonth);
    }


    // ---- team calendar ----

    /** Who is away on which days of a month (approved and pending leave), for the team calendar. Employees with no leave in the month are left out. */
    @Transactional(readOnly = true)
    public LeaveCalendarDto calendar(int year, int month, Long departmentId, Long branchId) {
        if (month < 1 || month > 12 || year < 2000 || year > 2200) {
            throw ApiException.badRequest("Choose a valid month.");
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Set<DayOfWeek> weekdays = settingsService.workingDays();
        Map<LocalDate, String> holidayNames = new LinkedHashMap<>();
        for (PublicHoliday h : holidayRepository.findByHolidayDateBetween(from, to)) {
            holidayNames.put(h.getHolidayDate(), h.getName());
        }
        List<CalendarDayDto> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(new CalendarDayDto(d, !weekdays.contains(d.getDayOfWeek()), holidayNames.get(d)));
        }
        Map<Long, List<CalendarEntryDto>> entries = new LinkedHashMap<>();
        Map<Long, Employee> people = new LinkedHashMap<>();
        for (LeaveRequest r : requestRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(to, from)) {
            if (r.getStatus() != LeaveStatus.APPROVED && r.getStatus() != LeaveStatus.PENDING) {
                continue;
            }
            Employee e = r.getEmployee();
            if (!branchAccessService.canAccess(e.getBranchId()) || (branchId != null && !branchId.equals(e.getBranchId()))
                    || (departmentId != null && (e.getDepartment() == null || !departmentId.equals(e.getDepartment().getId())))) {
                continue;
            }
            boolean single = r.getStartDate().equals(r.getEndDate());
            for (LocalDate d = r.getStartDate().isBefore(from) ? from : r.getStartDate(); !d.isAfter(r.getEndDate()) && !d.isAfter(to); d = d.plusDays(1)) {
                if (!isWorking(d, weekdays, holidayNames.keySet())) {
                    continue;
                }
                boolean half = single ? (r.isStartHalf() || r.isEndHalf()) : (d.equals(r.getStartDate()) && r.isStartHalf()) || (d.equals(r.getEndDate()) && r.isEndHalf());
                entries.computeIfAbsent(e.getId(), k -> new ArrayList<>()).add(new CalendarEntryDto(d, r.getId(), r.getLeaveType().getName(), r.getStatus(), half));
                people.put(e.getId(), e);
            }
        }
        List<CalendarRowDto> rows = new ArrayList<>();
        for (Map.Entry<Long, List<CalendarEntryDto>> en : entries.entrySet()) {
            Employee e = people.get(en.getKey());
            en.getValue().sort(Comparator.comparing(CalendarEntryDto::date));
            rows.add(new CalendarRowDto(e.getId(), e.getEmployeeNumber(), e.fullName(), e.getDepartment() != null ? e.getDepartment().getName() : null, en.getValue()));
        }
        rows.sort(Comparator.comparing(CalendarRowDto::employeeName, String.CASE_INSENSITIVE_ORDER));
        return new LeaveCalendarDto(year, month, days, rows);
    }
}
