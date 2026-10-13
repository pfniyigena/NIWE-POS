package com.posapp.hr;

import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import com.posapp.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    public HrController(EmployeeService employeeService, OrgService orgService, LeaveService leaveService, AttendanceService attendanceService,
                        HrSettingsService settingsService, UserRepository userRepository) {
        this.employeeService = employeeService;
        this.orgService = orgService;
        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
    }

    // ---- employees ----

    @GetMapping("/employees")
    public List<EmployeeDto> employees(@RequestParam(required = false) EmployeeStatus status, @RequestParam(required = false) Long departmentId,
                                       @RequestParam(required = false) Long branchId, @RequestParam(required = false) String q) {
        return employeeService.list(status, departmentId, branchId, q);
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
    public List<LeaveRequestDto> leaves(@RequestParam(required = false) LeaveStatus status, @RequestParam(required = false) Long employeeId,
                                        @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return leaveService.listRequests(status, employeeId, from, to);
    }

    /** How many working days a period would use (weekends and public holidays excluded) — for the request form. */
    @GetMapping("/leaves/working-days")
    public int workingDays(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return leaveService.workingDays(from, to);
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
    public List<AttendanceDto> attendance(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) Long departmentId) {
        return attendanceService.list(from, to, employeeId, departmentId);
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
