package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
        int total = 0;
        for (LeaveRequest r : requestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId)) {
            if (r.getStatus() != LeaveStatus.APPROVED || (paid != null && r.getLeaveType().isPaid() != paid)) {
                continue;
            }
            LocalDate s = r.getStartDate().isBefore(from) ? from : r.getStartDate();
            LocalDate e = r.getEndDate().isAfter(to) ? to : r.getEndDate();
            if (!e.isBefore(s)) {
                total += countWorkingDays(s, e, weekdays, holidays);
            }
        }
        return BigDecimal.valueOf(total);
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
        int days = workingDays(r.startDate(), r.endDate());
        if (days <= 0) {
            throw ApiException.badRequest("That period has no working days (only weekends or public holidays).");
        }
        checkOverlap(employee.getId(), r.startDate(), r.endDate(), null);
        checkBalance(employee, type, r.startDate().getYear(), BigDecimal.valueOf(days), null);

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(type);
        lr.setStartDate(r.startDate());
        lr.setEndDate(r.endDate());
        lr.setDays(BigDecimal.valueOf(days));
        lr.setReason(HrSupport.blankToNull(r.reason()));
        LeaveRequest saved = requestRepository.save(lr);
        auditLogService.record("LEAVE_REQUESTED", "Leave request for " + employee.fullName() + ": " + type.getName() + " " + r.startDate() + " to " + r.endDate() + " (" + days + " days)", "LeaveRequest", saved.getId());
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
        checkBalance(lr.getEmployee(), lr.getLeaveType(), lr.getStartDate().getYear(), lr.getDays(), lr.getId());
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

    private void checkBalance(Employee employee, LeaveType type, int year, BigDecimal days, Long ignoreId) {
        if (type.getDaysPerYear() <= 0) {
            return;
        }
        BigDecimal used = BigDecimal.ZERO;
        for (LeaveRequest o : requestRepository.findByEmployeeIdOrderByStartDateDesc(employee.getId())) {
            if (o.getLeaveType().getId().equals(type.getId()) && o.getStartDate().getYear() == year
                    && (o.getStatus() == LeaveStatus.APPROVED || o.getStatus() == LeaveStatus.PENDING)
                    && (ignoreId == null || !o.getId().equals(ignoreId))) {
                used = used.add(o.getDays());
            }
        }
        BigDecimal remaining = BigDecimal.valueOf(type.getDaysPerYear()).subtract(used);
        if (days.compareTo(remaining) > 0) {
            throw ApiException.conflict(employee.fullName() + " has only " + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + " day(s) of " + type.getName() + " left in " + year + " (needs " + days.stripTrailingZeros().toPlainString() + ").");
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
        List<LeaveBalanceDto> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if (e.getStatus() == EmployeeStatus.TERMINATED && e.getTerminationDate() != null && e.getTerminationDate().getYear() < year) {
                continue;
            }
            if ((employeeId != null && !e.getId().equals(employeeId)) || !branchAccessService.canAccess(e.getBranchId())) {
                continue;
            }
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
                BigDecimal remaining = unlimited ? BigDecimal.ZERO : entitled.subtract(used).subtract(pending);
                out.add(new LeaveBalanceDto(e.getId(), e.getEmployeeNumber(), e.fullName(), t.getId(), t.getName(), unlimited, entitled, used, pending, remaining));
            }
        }
        return out;
    }
}
