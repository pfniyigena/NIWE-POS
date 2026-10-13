package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.EmployeeDto;
import com.posapp.hr.dto.HrRequests.EmployeeRequest;
import com.posapp.hr.dto.HrRequests.TerminateRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Employee records. Salary is not here — it lives on the contract. */
@Service
public class EmployeeService {

    private final EmployeeRepository repository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final ContractRepository contractRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final UserRepository userRepository;
    private final BranchService branchService;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;
    private final HrSupport support;

    public EmployeeService(EmployeeRepository repository, DepartmentRepository departmentRepository, PositionRepository positionRepository,
                           ContractRepository contractRepository, LeaveRequestRepository leaveRequestRepository,
                           AttendanceRecordRepository attendanceRepository, UserRepository userRepository, BranchService branchService,
                           BranchAccessService branchAccessService, AuditLogService auditLogService, HrSupport support) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.contractRepository = contractRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> list(EmployeeStatus status, Long departmentId, Long branchId, String q) {
        Map<Long, String> branches = support.branchNames();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return repository.findAllByOrderByFirstNameAscLastNameAsc().stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> departmentId == null || (e.getDepartment() != null && e.getDepartment().getId().equals(departmentId)))
                .filter(e -> branchId == null || branchId.equals(e.getBranchId()))
                .filter(e -> branchAccessService.canAccess(e.getBranchId()))
                .filter(e -> needle == null || matches(e, needle))
                .map(e -> toDto(e, branches)).toList();
    }

    private boolean matches(Employee e, String needle) {
        return (e.fullName() + " " + e.getEmployeeNumber() + " " + nz(e.getJobTitle()) + " " + nz(e.getPhone()) + " " + nz(e.getEmail()))
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Transactional(readOnly = true)
    public EmployeeDto get(Long id) {
        Employee e = entity(id);
        branchAccessService.assertCanAccess(e.getBranchId());
        return toDto(e, support.branchNames());
    }

    /** The employee linked to the signed-in user's login, if any. */
    @Transactional(readOnly = true)
    public Employee currentEmployee() {
        AppUser user = HrSupport.currentUser();
        return user == null ? null : repository.findFirstByUserId(user.getId()).orElse(null);
    }

    @Transactional
    public EmployeeDto create(EmployeeRequest r) {
        Employee e = new Employee();
        String number = HrSupport.blankToNull(r.employeeNumber());
        if (number == null) {
            number = nextNumber();
        } else if (repository.existsByEmployeeNumber(number)) {
            throw ApiException.conflict("There is already an employee with the number " + number + ".");
        }
        e.setEmployeeNumber(number);
        apply(e, r);
        Employee saved = repository.save(e);
        auditLogService.record("EMPLOYEE_CREATED", "Added employee " + saved.getEmployeeNumber() + " " + saved.fullName(), "Employee", saved.getId());
        return toDto(saved, support.branchNames());
    }

    @Transactional
    public EmployeeDto update(Long id, EmployeeRequest r) {
        Employee e = entity(id);
        branchAccessService.assertCanAccess(e.getBranchId());
        String number = HrSupport.blankToNull(r.employeeNumber());
        if (number != null && !number.equals(e.getEmployeeNumber())) {
            if (repository.existsByEmployeeNumber(number)) {
                throw ApiException.conflict("There is already an employee with the number " + number + ".");
            }
            e.setEmployeeNumber(number);
        }
        apply(e, r);
        Employee saved = repository.save(e);
        auditLogService.record("EMPLOYEE_UPDATED", "Updated employee " + saved.getEmployeeNumber() + " " + saved.fullName(), "Employee", saved.getId());
        return toDto(saved, support.branchNames());
    }

    /** Employee leaves the company: marks them terminated and ends their active contract. */
    @Transactional
    public EmployeeDto terminate(Long id, TerminateRequest r) {
        Employee e = entity(id);
        branchAccessService.assertCanAccess(e.getBranchId());
        if (e.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.conflict(e.fullName() + " has already left.");
        }
        if (r.date().isBefore(e.getHireDate())) {
            throw ApiException.badRequest("The leaving date can't be before the hire date.");
        }
        e.setStatus(EmployeeStatus.TERMINATED);
        e.setTerminationDate(r.date());
        e.setTerminationReason(HrSupport.blankToNull(r.reason()));
        for (Contract c : contractRepository.findByEmployeeIdOrderByStartDateDesc(id)) {
            if (c.getStatus() == ContractStatus.ACTIVE) {
                c.setStatus(ContractStatus.TERMINATED);
                c.setTerminationDate(r.date());
                c.setTerminationReason("Employee left" + (r.reason() != null && !r.reason().isBlank() ? ": " + r.reason().trim() : ""));
                contractRepository.save(c);
            }
        }
        repository.save(e);
        auditLogService.record("EMPLOYEE_TERMINATED", "Employee " + e.getEmployeeNumber() + " " + e.fullName() + " left on " + r.date(), "Employee", id);
        return toDto(e, support.branchNames());
    }

    /** Brings back an inactive or terminated employee. */
    @Transactional
    public EmployeeDto setStatus(Long id, EmployeeStatus status) {
        Employee e = entity(id);
        branchAccessService.assertCanAccess(e.getBranchId());
        if (status == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest("Use \"Employee left\" to terminate an employee.");
        }
        e.setStatus(status);
        if (status == EmployeeStatus.ACTIVE) {
            e.setTerminationDate(null);
            e.setTerminationReason(null);
        }
        repository.save(e);
        auditLogService.record("EMPLOYEE_STATUS", "Employee " + e.getEmployeeNumber() + " " + e.fullName() + " set to " + status, "Employee", id);
        return toDto(e, support.branchNames());
    }

    @Transactional
    public void delete(Long id) {
        Employee e = entity(id);
        branchAccessService.assertCanAccess(e.getBranchId());
        boolean hasHistory = !contractRepository.findByEmployeeIdOrderByStartDateDesc(id).isEmpty()
                || !leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(id).isEmpty()
                || !attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(id, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31)).isEmpty();
        if (hasHistory) {
            throw ApiException.conflict(e.fullName() + " has contracts, leave or attendance records. Mark the employee as left instead of deleting.");
        }
        if (repository.existsByManagerId(id) || departmentRepository.existsByManagerId(id)) {
            throw ApiException.conflict(e.fullName() + " is a manager of other employees or departments. Change that first.");
        }
        repository.delete(e);
        auditLogService.record("EMPLOYEE_DELETED", "Deleted employee " + e.getEmployeeNumber() + " " + e.fullName(), "Employee", id);
    }

    private void apply(Employee e, EmployeeRequest r) {
        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setDateOfBirth(r.dateOfBirth());
        if (r.dateOfBirth() != null && !r.dateOfBirth().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("The date of birth must be in the past.");
        }
        e.setNationalId(HrSupport.blankToNull(r.nationalId()));
        e.setPhone(HrSupport.blankToNull(r.phone()));
        e.setEmail(HrSupport.blankToNull(r.email()));
        e.setAddress(HrSupport.blankToNull(r.address()));
        e.setEmergencyContactName(HrSupport.blankToNull(r.emergencyContactName()));
        e.setEmergencyContactPhone(HrSupport.blankToNull(r.emergencyContactPhone()));
        e.setHireDate(r.hireDate());
        if (r.employmentType() != null) {
            e.setEmploymentType(r.employmentType());
        }
        e.setJobTitle(HrSupport.blankToNull(r.jobTitle()));
        e.setDepartment(r.departmentId() != null
                ? departmentRepository.findById(r.departmentId()).orElseThrow(() -> ApiException.notFound("Department not found")) : null);
        Position position = r.positionId() != null
                ? positionRepository.findById(r.positionId()).orElseThrow(() -> ApiException.notFound("Position not found")) : null;
        e.setPosition(position);
        if (position != null && e.getJobTitle() == null) {
            e.setJobTitle(position.getTitle());
        }
        if (r.managerId() != null) {
            Employee manager = entity(r.managerId());
            if (e.getId() != null) {
                for (Employee up = manager; up != null; up = up.getManager()) {
                    if (up.getId().equals(e.getId())) {
                        throw ApiException.badRequest("That would make the reporting line loop back on itself.");
                    }
                }
            }
            e.setManager(manager);
        } else {
            e.setManager(null);
        }
        Long branch = branchService.resolveForDocument(r.branchId());
        branchAccessService.assertCanAccess(branch);
        e.setBranchId(branch);
        if (r.userId() != null) {
            userRepository.findById(r.userId()).orElseThrow(() -> ApiException.notFound("Staff login not found"));
            repository.findFirstByUserId(r.userId()).ifPresent(other -> {
                if (e.getId() == null || !other.getId().equals(e.getId())) {
                    throw ApiException.conflict("That staff login is already linked to " + other.fullName() + ".");
                }
            });
            e.setUserId(r.userId());
        } else {
            e.setUserId(null);
        }
        e.setBankName(HrSupport.blankToNull(r.bankName()));
        e.setBankAccount(HrSupport.blankToNull(r.bankAccount()));
        e.setTaxNumber(HrSupport.blankToNull(r.taxNumber()));
        e.setSocialSecurityNumber(HrSupport.blankToNull(r.socialSecurityNumber()));
        if (r.attendanceTracked() != null) {
            e.setAttendanceTracked(r.attendanceTracked());
        }
        e.setNotes(HrSupport.blankToNull(r.notes()));
    }

    Employee entity(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
    }

    EmployeeDto toDto(Employee e, Map<Long, String> branches) {
        String userLabel = null;
        if (e.getUserId() != null) {
            userLabel = userRepository.findById(e.getUserId()).map(u -> u.getUsername() + (u.getFullName() != null ? " — " + u.getFullName() : "")).orElse(null);
        }
        return EmployeeDto.from(e, e.getBranchId() != null ? branches.get(e.getBranchId()) : null, userLabel);
    }

    private String nextNumber() {
        long n = repository.count() + 1;
        String number = String.format("EMP-%04d", n);
        while (repository.existsByEmployeeNumber(number)) {
            n++;
            number = String.format("EMP-%04d", n);
        }
        return number;
    }
}
