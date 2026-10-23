package com.posapp.hr;

import com.posapp.common.ListPaging;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import com.posapp.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Employees, organisation, leave, attendance and the HR settings (everything under /api/hr except contracts and payroll). */
@RestController
@RequestMapping("/api/hr")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_HR_VIEW')")
public class HrController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_HR_MANAGE')";

    private final EmployeeService employeeService;
    private final OrgService orgService;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;
    private final HrSettingsService settingsService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final HrPrintService printService;
    private final EmployeeLoginService loginService;

    public HrController(EmployeeService employeeService, OrgService orgService, LeaveService leaveService, AttendanceService attendanceService,
                        HrSettingsService settingsService, UserRepository userRepository, EmployeeRepository employeeRepository,
                        HrPrintService printService, EmployeeLoginService loginService) {
        this.printService = printService;
        this.loginService = loginService;
        this.employeeService = employeeService;
        this.orgService = orgService;
        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Branch of each employee, for the lists whose rows don't carry it. */
    Map<Long, Long> employeeBranches() {
        Map<Long, Long> m = new HashMap<>();
        for (Employee e : employeeRepository.findAll()) {
            m.put(e.getId(), e.getBranchId());
        }
        return m;
    }

    // ---- employees ----

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeDto>> employees(@RequestParam(required = false) EmployeeStatus status, @RequestParam(required = false) Long departmentId,
                                                       @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q,
                                                       @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ListPaging.of(employeeService.list(status, departmentId, branchId, q), page, size);
    }

    @GetMapping("/employees/{id}")
    public EmployeeDto employee(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/employees")
    public EmployeeDto createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return employeeService.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/employees/{id}")
    public EmployeeDto updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/employees/{id}/terminate")
    public EmployeeDto terminate(@PathVariable Long id, @Valid @RequestBody TerminateRequest request) {
        return employeeService.terminate(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/employees/{id}/status")
    public EmployeeDto setStatus(@PathVariable Long id, @RequestParam EmployeeStatus status) {
        return employeeService.setStatus(id, status);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/employees/{id}")
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.delete(id);
    }

    /** The employee's Service Card (staff card) as a PDF to print. */
    @GetMapping("/employees/{id}/service-card")
    public org.springframework.http.ResponseEntity<byte[]> serviceCard(@PathVariable Long id) {
        HrPrintService.Printed p = printService.serviceCard(id);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(p.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(p.content());
    }

    public record LoginRequest(String username, String password) {
    }

    /** Gives one employee a login (the first password is returned once). */
    @PreAuthorize(MANAGE)
    @PostMapping("/employees/{id}/login")
    public EmployeeLoginService.Created createLogin(@PathVariable Long id, @RequestBody(required = false) LoginRequest request) {
        return loginService.create(id, request == null ? null : request.username(), request == null ? null : request.password());
    }

    /** Gives every employee who has no login one. */
    @PreAuthorize(MANAGE)
    @PostMapping("/employees/logins")
    public EmployeeLoginService.BulkResult createLogins() {
        return loginService.createForAll();
    }

    /** A new password for an employee's login (returned once). */
    @PreAuthorize(MANAGE)
    @PostMapping("/employees/{id}/login/reset-password")
    public EmployeeLoginService.Created resetLoginPassword(@PathVariable Long id) {
        return loginService.resetPassword(id);
    }

    /** Staff logins that can be linked to an employee. */
    @PreAuthorize(MANAGE)
    @GetMapping("/staff-logins")
    public List<StaffLoginDto> staffLogins() {
        return userRepository.findByActiveTrue().stream()
                .map(u -> new StaffLoginDto(u.getId(), u.getUsername(), u.getFullName())).toList();
    }

    public record StaffLoginDto(Long id, String username, String fullName) {
    }

    // ---- organisation ----

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return orgService.listDepartments();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/departments")
    public DepartmentDto createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return orgService.createDepartment(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/departments/{id}")
    public DepartmentDto updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return orgService.updateDepartment(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/departments/{id}")
    public void deleteDepartment(@PathVariable Long id) {
        orgService.deleteDepartment(id);
    }

    @GetMapping("/positions")
    public List<PositionDto> positions() {
        return orgService.listPositions();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/positions")
    public PositionDto createPosition(@Valid @RequestBody PositionRequest request) {
        return orgService.createPosition(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/positions/{id}")
    public PositionDto updatePosition(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        return orgService.updatePosition(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/positions/{id}")
    public void deletePosition(@PathVariable Long id) {
        orgService.deletePosition(id);
    }

    @GetMapping("/org-chart")
    public OrgChartDto orgChart() {
        return orgService.chart();
    }

    // ---- leave ----

    @GetMapping("/leave-types")
    public List<LeaveTypeDto> leaveTypes() {
        return leaveService.listTypes();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/leave-types")
    public LeaveTypeDto createLeaveType(@Valid @RequestBody LeaveTypeRequest request) {
        return leaveService.createType(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/leave-types/{id}")
    public LeaveTypeDto updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
        return leaveService.updateType(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/leave-types/{id}")
    public void deleteLeaveType(@PathVariable Long id) {
        leaveService.deleteType(id);
    }

    @GetMapping("/holidays")
    public List<HolidayDto> holidays() {
        return leaveService.listHolidays();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/holidays")
    public HolidayDto createHoliday(@Valid @RequestBody HolidayRequest request) {
        return leaveService.createHoliday(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/holidays/{id}")
    public void deleteHoliday(@PathVariable Long id) {
        leaveService.deleteHoliday(id);
    }

    @GetMapping("/leaves")
    public ResponseEntity<List<LeaveRequestDto>> leaves(@RequestParam(required = false) LeaveStatus status, @RequestParam(required = false) Long employeeId,
                                                        @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                        @RequestParam(required = false) String q, @RequestParam(required = false) Long branchId,
                                                        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Map<Long, Long> branches = branchId == null ? Map.of() : employeeBranches();
        List<LeaveRequestDto> rows = leaveService.listRequests(status, employeeId, from, to).stream()
                .filter(r -> ListPaging.has(q, r.employeeName(), r.employeeNumber(), r.leaveTypeName(), r.reason()))
                .filter(r -> branchId == null || branchId.equals(branches.get(r.employeeId())))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    /** How many working days a period would use (weekends and public holidays excluded) — for the request form. */
    @GetMapping("/leaves/working-days")
    public BigDecimal workingDays(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(defaultValue = "false") boolean startHalf, @RequestParam(defaultValue = "false") boolean endHalf) {
        return leaveService.leaveDays(from, to, startHalf, endHalf);
    }

    /** Who is away on which days of a month, for the team leave calendar. */
    @GetMapping("/leaves/calendar")
    public LeaveCalendarDto leaveCalendar(@RequestParam int year, @RequestParam int month, @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Long branchId) {
        return leaveService.calendar(year, month, departmentId, branchId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/leaves")
    public LeaveRequestDto requestLeave(@Valid @RequestBody LeaveRequestRequest request) {
        return leaveService.create(request, employeeService.currentEmployee());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/leaves/{id}/approve")
    public LeaveRequestDto approveLeave(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return leaveService.approve(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/leaves/{id}/reject")
    public LeaveRequestDto rejectLeave(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return leaveService.reject(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/leaves/{id}/cancel")
    public LeaveRequestDto cancelLeave(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return leaveService.cancel(id, request);
    }

    @GetMapping("/leave-balances")
    public List<LeaveBalanceDto> balances(@RequestParam(required = false) Integer year, @RequestParam(required = false) Long employeeId) {
        return leaveService.balances(year != null ? year : LocalDate.now().getYear(), employeeId);
    }

    // ---- attendance ----

    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceDto>> attendance(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long employeeId,
                                                          @RequestParam(required = false) Long departmentId, @RequestParam(required = false) AttendanceStatus status,
                                                          @RequestParam(required = false) String q, @RequestParam(required = false) Long branchId,
                                                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Map<Long, Long> branches = branchId == null ? Map.of() : employeeBranches();
        List<AttendanceDto> rows = attendanceService.list(from, to, employeeId, departmentId).stream()
                .filter(a -> status == null || a.status() == status)
                .filter(a -> ListPaging.has(q, a.employeeName(), a.employeeNumber(), a.note()))
                .filter(a -> branchId == null || branchId.equals(branches.get(a.employeeId())))
                .toList();
        return ListPaging.of(rows, page, size);
    }

    @GetMapping("/attendance/summary")
    public List<AttendanceSummaryDto> attendanceSummary(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                        @RequestParam(required = false) Long employeeId, @RequestParam(required = false) Long departmentId) {
        return attendanceService.summary(from, to, employeeId, departmentId);
    }

    @GetMapping("/attendance/today")
    public TodayDto today() {
        return attendanceService.today();
    }

    @PostMapping("/attendance/clock-in")
    public AttendanceDto clockIn() {
        return attendanceService.clockIn();
    }

    @PostMapping("/attendance/clock-out")
    public AttendanceDto clockOut() {
        return attendanceService.clockOut();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/attendance")
    public AttendanceDto saveAttendance(@Valid @RequestBody AttendanceRequest request) {
        return attendanceService.save(request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/attendance/{id}")
    public void deleteAttendance(@PathVariable Long id) {
        attendanceService.delete(id);
    }

    // ---- settings ----

    @GetMapping("/settings")
    public SettingsDto settings() {
        return settingsService.get();
    }

    /** The tax-band mode is a payroll setting (see the payroll controller), so it is ignored here. */
    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return settingsService.update(new SettingsRequest(request.workStart(), request.graceMinutes(), request.standardDailyHours(),
                request.workingDays(), request.overtimeMultiplier(), null));
    }
}
