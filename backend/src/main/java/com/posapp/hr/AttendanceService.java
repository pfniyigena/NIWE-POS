package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.AttendanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Daily attendance: manual entries and the clock in / clock out of the signed-in employee. */
@Service
public class AttendanceService {

    private final AttendanceRecordRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final LeaveService leaveService;
    private final HrSettingsService settingsService;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public AttendanceService(AttendanceRecordRepository repository, EmployeeRepository employeeRepository, EmployeeService employeeService,
                             LeaveService leaveService, HrSettingsService settingsService, BranchAccessService branchAccessService,
                             AuditLogService auditLogService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.leaveService = leaveService;
        this.settingsService = settingsService;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> list(LocalDate from, LocalDate to, Long employeeId, Long departmentId) {
        return repository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(from, to).stream()
                .filter(a -> employeeId == null || a.getEmployee().getId().equals(employeeId))
                .filter(a -> departmentId == null || (a.getEmployee().getDepartment() != null && a.getEmployee().getDepartment().getId().equals(departmentId)))
                .filter(a -> branchAccessService.canAccess(a.getEmployee().getBranchId()))
                .map(AttendanceDto::from).toList();
    }

    /** Creates or replaces the record of one employee for one day. */
    @Transactional
    public AttendanceDto save(AttendanceRequest r) {
        Employee employee = employeeRepository.findById(r.employeeId()).orElseThrow(() -> ApiException.notFound("Employee not found"));
        branchAccessService.assertCanAccess(employee.getBranchId());
        if (r.workDate().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Attendance can't be recorded for a future date.");
        }
        if (r.clockIn() != null && r.clockOut() != null && !r.clockOut().isAfter(r.clockIn())) {
            throw ApiException.badRequest("Clock-out must be after clock-in.");
        }
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(employee.getId(), r.workDate()).orElseGet(AttendanceRecord::new);
        rec.setEmployee(employee);
        rec.setWorkDate(r.workDate());
        rec.setClockIn(r.status() == AttendanceStatus.ABSENT ? null : r.clockIn());
        rec.setClockOut(r.status() == AttendanceStatus.ABSENT ? null : r.clockOut());
        rec.setNote(HrSupport.blankToNull(r.note()));
        recalc(rec, r.status());
        AttendanceRecord saved = repository.save(rec);
        auditLogService.record("ATTENDANCE_SAVED", "Attendance " + employee.fullName() + " " + r.workDate() + " " + saved.getStatus(), "AttendanceRecord", saved.getId());
        return AttendanceDto.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        AttendanceRecord rec = repository.findById(id).orElseThrow(() -> ApiException.notFound("Attendance record not found"));
        branchAccessService.assertCanAccess(rec.getEmployee().getBranchId());
        repository.delete(rec);
        auditLogService.record("ATTENDANCE_DELETED", "Deleted attendance " + rec.getEmployee().fullName() + " " + rec.getWorkDate(), "AttendanceRecord", id);
    }

    @Transactional(readOnly = true)
    public TodayDto today() {
        Employee me = employeeService.currentEmployee();
        if (me == null) {
            return new TodayDto(false, null, null, null);
        }
        AttendanceDto rec = repository.findByEmployeeIdAndWorkDate(me.getId(), LocalDate.now()).map(AttendanceDto::from).orElse(null);
        return new TodayDto(true, me.getId(), me.fullName(), rec);
    }

    @Transactional
    public AttendanceDto clockIn() {
        Employee me = requireMe();
        LocalDate today = LocalDate.now();
        if (repository.findByEmployeeIdAndWorkDate(me.getId(), today).map(a -> a.getClockIn() != null).orElse(false)) {
            throw ApiException.conflict("You already clocked in today.");
        }
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(me.getId(), today).orElseGet(AttendanceRecord::new);
        rec.setEmployee(me);
        rec.setWorkDate(today);
        rec.setClockIn(LocalTime.now().withSecond(0).withNano(0));
        rec.setClockOut(null);
        recalc(rec, null);
        return AttendanceDto.from(repository.save(rec));
    }

    @Transactional
    public AttendanceDto clockOut() {
        Employee me = requireMe();
        AttendanceRecord rec = repository.findByEmployeeIdAndWorkDate(me.getId(), LocalDate.now())
                .filter(a -> a.getClockIn() != null).orElseThrow(() -> ApiException.conflict("You haven't clocked in today."));
        if (rec.getClockOut() != null) {
            throw ApiException.conflict("You already clocked out today.");
        }
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        if (!now.isAfter(rec.getClockIn())) {
            throw ApiException.conflict("Clock-out must be after clock-in.");
        }
        rec.setClockOut(now);
        recalc(rec, rec.getStatus());
        return AttendanceDto.from(repository.save(rec));
    }

    private Employee requireMe() {
        Employee me = employeeService.currentEmployee();
        if (me == null) {
            throw ApiException.badRequest("Your login isn't linked to an employee record. Ask HR to link it on your employee page.");
        }
        if (me.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest("Your employment has ended.");
        }
        return me;
    }

    /** Works out status, hours worked and overtime from the clock times and the HR settings. */
    private void recalc(AttendanceRecord rec, AttendanceStatus requested) {
        HrSettings s = settingsService.entity();
        if (requested == AttendanceStatus.ABSENT || rec.getClockIn() == null) {
            rec.setStatus(AttendanceStatus.ABSENT);
            rec.setHoursWorked(BigDecimal.ZERO);
            rec.setOvertimeHours(BigDecimal.ZERO);
            return;
        }
        boolean late = rec.getClockIn().isAfter(s.getWorkStart().plusMinutes(s.getGraceMinutes()));
        rec.setStatus(requested != null && requested != AttendanceStatus.ABSENT ? requested : (late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT));
        if (rec.getClockOut() != null) {
            BigDecimal hours = BigDecimal.valueOf(Duration.between(rec.getClockIn(), rec.getClockOut()).toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            rec.setHoursWorked(hours);
            boolean workingDay = settingsService.workingDays().contains(rec.getWorkDate().getDayOfWeek());
            // On a day off every hour is overtime; on a working day only the hours beyond the standard day.
            BigDecimal overtime = workingDay ? hours.subtract(s.getStandardDailyHours()).max(BigDecimal.ZERO) : hours;
            rec.setOvertimeHours(overtime);
        } else {
            rec.setHoursWorked(BigDecimal.ZERO);
            rec.setOvertimeHours(BigDecimal.ZERO);
        }
    }

    @Transactional
    public List<AttendanceSummaryDto> summary(LocalDate from, LocalDate to, Long employeeId, Long departmentId) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The end date can't be before the start date.");
        }
        Set<DayOfWeek> weekdays = settingsService.workingDays();
        Set<LocalDate> holidays = leaveService.holidaySet(from, to);
        List<AttendanceRecord> records = repository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(from, to);
        List<AttendanceSummaryDto> out = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if ((employeeId != null && !e.getId().equals(employeeId))
                    || (departmentId != null && (e.getDepartment() == null || !e.getDepartment().getId().equals(departmentId)))
                    || !branchAccessService.canAccess(e.getBranchId())) {
                continue;
            }
            LocalDate start = e.getHireDate().isAfter(from) ? e.getHireDate() : from;
            LocalDate end = e.getTerminationDate() != null && e.getTerminationDate().isBefore(to) ? e.getTerminationDate() : to;
            if (end.isBefore(start)) {
                continue;
            }
            int expected = LeaveService.countWorkingDays(start, end, weekdays, holidays);
            int present = 0, late = 0, absent = 0;
            BigDecimal hours = BigDecimal.ZERO, overtime = BigDecimal.ZERO;
            for (AttendanceRecord a : records) {
                if (!a.getEmployee().getId().equals(e.getId())) {
                    continue;
                }
                switch (a.getStatus()) {
                    case PRESENT -> present++;
                    case LATE -> late++;
                    case ABSENT -> absent++;
                }
                hours = hours.add(a.getHoursWorked());
                overtime = overtime.add(a.getOvertimeHours());
            }
            BigDecimal leave = leaveService.approvedLeaveDays(e.getId(), start, end, null);
            if (expected == 0 && present + late + absent == 0 && leave.signum() == 0) {
                continue;
            }
            out.add(new AttendanceSummaryDto(e.getId(), e.getEmployeeNumber(), e.fullName(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null, expected, present, late, absent, leave, hours, overtime));
        }
        return out;
    }
}
