package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.*;
import com.posapp.hr.dto.MyHrDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Employee self-service: a signed-in user whose login is linked to an employee record sees and does their own HR things —
 * payslips, leave, attendance, contact details — without any HR or Payroll access. Everything here is looked up from the
 * login, never from an id sent by the browser, so nobody can reach another person's data through these calls.
 */
@Service
public class MyHrService {

    private static final Set<PayrollStatus> VISIBLE = Set.of(PayrollStatus.APPROVED, PayrollStatus.PAID);

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollPaperService paperService;
    private final LeaveService leaveService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceService attendanceService;
    private final AttendanceRecordRepository attendanceRepository;
    private final AuditLogService auditLogService;
    private final EmployeeDocumentService documentService;

    public MyHrService(EmployeeService employeeService, EmployeeRepository employeeRepository, PayrollLineRepository lineRepository,
                       PayrollPaperService paperService, LeaveService leaveService, LeaveTypeRepository leaveTypeRepository,
                       AttendanceService attendanceService, AttendanceRecordRepository attendanceRepository, AuditLogService auditLogService,
                       EmployeeDocumentService documentService) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.lineRepository = lineRepository;
        this.paperService = paperService;
        this.leaveService = leaveService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.attendanceService = attendanceService;
        this.attendanceRepository = attendanceRepository;
        this.auditLogService = auditLogService;
        this.documentService = documentService;
    }

    private Employee me() {
        Employee me = employeeService.currentEmployee();
        if (me == null) {
            throw ApiException.badRequest("Your login isn't linked to an employee record. Ask HR to link it on your employee page.");
        }
        return me;
    }

    private static MyProfileDto profile(Employee e) {
        return new MyProfileDto(e.getId(), e.getEmployeeNumber(), e.fullName(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getName(), e.getManager() == null ? null : e.getManager().fullName(),
                e.getHireDate(), e.getStatus(), e.getPhone(), e.getEmail(), e.getAddress(), e.getEmergencyContactName(), e.getEmergencyContactPhone());
    }

    @Transactional
    public MyOverviewDto overview() {
        Employee me = employeeService.currentEmployee();
        int year = LocalDate.now().getYear();
        if (me == null) {
            return new MyOverviewDto(false, null, year, List.of(), 0, 0, new TodayDto(false, null, null, null));
        }
        int pending = (int) leaveService.requestsOfEmployee(me.getId()).stream().filter(r -> r.status() == LeaveStatus.PENDING).count();
        int payslips = lineRepository.findByEmployeeIdAndRunStatusInOrderByRunPeriodYearDescRunPeriodMonthDesc(me.getId(), VISIBLE).size();
        return new MyOverviewDto(true, profile(me), year, leaveService.balancesOfEmployee(me, year), pending, payslips, attendanceService.today());
    }

    @Transactional
    public MyProfileDto updateContact(MyContactRequest r) {
        Employee me = me();
        String email = HrSupport.blankToNull(r.email());
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("That e-mail address doesn't look right.");
        }
        me.setPhone(HrSupport.blankToNull(r.phone()));
        me.setEmail(email);
        me.setAddress(HrSupport.blankToNull(r.address()));
        me.setEmergencyContactName(HrSupport.blankToNull(r.emergencyContactName()));
        me.setEmergencyContactPhone(HrSupport.blankToNull(r.emergencyContactPhone()));
        employeeRepository.save(me);
        auditLogService.record("EMPLOYEE_SELF_UPDATED", me.fullName() + " updated their contact details", "Employee", me.getId());
        return profile(me);
    }

    // ---- payslips ----

    @Transactional(readOnly = true)
    public List<MyPayslipRowDto> payslips(Integer year) {
        Employee me = me();
        return lineRepository.findByEmployeeIdAndRunStatusInOrderByRunPeriodYearDescRunPeriodMonthDesc(me.getId(), VISIBLE).stream()
                .filter(l -> year == null || l.getRun().getPeriodYear() == year)
                .map(l -> new MyPayslipRowDto(l.getRun().getId(), l.getRun().getPeriodYear(), l.getRun().getPeriodMonth(), l.getRun().getStatus(),
                        l.getRun().getPaidDate(), l.getGrossPay(), l.getPayeTax(), l.getEmployeeContributions(), l.getOtherDeductions(), l.getNetPay()))
                .toList();
    }

    private PayrollLine line(Employee me, Long runId) {
        return lineRepository.findByRunIdAndEmployeeIdAndRunStatusIn(runId, me.getId(), VISIBLE)
                .orElseThrow(() -> ApiException.notFound("Payslip not found"));
    }

    @Transactional(readOnly = true)
    public MyPayslipDto payslip(Long runId) {
        PayrollLine l = line(me(), runId);
        PayrollRun run = l.getRun();
        return new MyPayslipDto(run.getId(), run.getPeriodYear(), run.getPeriodMonth(), run.getStatus(), run.getPaidDate(), PayrollLineDto.from(l),
                l.getItems().stream().sorted(Comparator.comparingInt(PayrollItem::getSortOrder)).map(PayrollItemDto::from).toList());
    }

    @Transactional(readOnly = true)
    public PayrollPaperService.Pdf payslipPdf(Long runId) {
        Employee me = me();
        PayrollLine l = line(me, runId);
        return paperService.payslipFile(l.getRun(), l, me);
    }

    @Transactional
    public PayrollPaperService.Pdf certificate(int year) {
        return paperService.ownCertificate(me(), year);
    }

    // ---- leave ----

    @Transactional(readOnly = true)
    public List<LeaveTypeDto> leaveTypes() {
        return leaveTypeRepository.findAllByOrderByNameAsc().stream().filter(LeaveType::isActive).map(LeaveTypeDto::from).toList();
    }

    @Transactional
    public List<LeaveBalanceDto> balances(int year) {
        return leaveService.balancesOfEmployee(me(), year);
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal workingDays(LocalDate from, LocalDate to, boolean startHalf, boolean endHalf) {
        me();
        return leaveService.leaveDays(from, to, startHalf, endHalf);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> leaves() {
        return leaveService.requestsOfEmployee(me().getId());
    }

    @Transactional
    public LeaveRequestDto requestLeave(LeaveRequestRequest r) {
        Employee me = me();
        // Always for myself, whatever employee id the browser sent.
        return leaveService.createOwn(me, new LeaveRequestRequest(me.getId(), r.leaveTypeId(), r.startDate(), r.endDate(), r.reason(), r.startHalfDay(), r.endHalfDay()));
    }

    @Transactional
    public LeaveRequestDto cancelLeave(Long id, DecisionRequest d) {
        return leaveService.cancelOwn(me(), id, d);
    }

    // ---- documents ----

    /** Documents HR has chosen to share with the employee (contract copy, certificates...). */
    @Transactional(readOnly = true)
    public List<com.posapp.hr.dto.HrDocDtos.EmployeeDocumentDto> documents() {
        return documentService.sharedWith(me());
    }

    @Transactional(readOnly = true)
    public EmployeeDocumentService.FileData documentFile(Long id) {
        return documentService.sharedFile(me(), id);
    }

    // ---- attendance ----

    @Transactional(readOnly = true)
    public List<AttendanceDto> attendance(LocalDate from, LocalDate to) {
        Employee me = me();
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The end date can't be before the start date.");
        }
        if (from.plusDays(400).isBefore(to)) {
            throw ApiException.badRequest("Choose a period of at most a year.");
        }
        return attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(me.getId(), from, to).stream().map(AttendanceDto::from).toList();
    }
}
