package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.ContractDto;
import com.posapp.hr.dto.HrRequests.*;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Employment contracts: draft, activate, edit, renew, terminate, and the daily expiry check. */
@Service
public class ContractService {

    private final ContractRepository repository;
    private final EmployeeRepository employeeRepository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;
    private final ModuleService moduleService;

    public ContractService(ContractRepository repository, EmployeeRepository employeeRepository, BranchAccessService branchAccessService,
                           AuditLogService auditLogService, ModuleService moduleService) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
        this.moduleService = moduleService;
    }

    @Transactional(readOnly = true)
    public List<ContractDto> list(ContractStatus status, Long employeeId) {
        return repository.findAllByOrderByStartDateDescIdDesc().stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> employeeId == null || c.getEmployee().getId().equals(employeeId))
                .filter(c -> branchAccessService.canAccess(c.getEmployee().getBranchId()))
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ContractDto get(Long id) {
        Contract c = entity(id);
        branchAccessService.assertCanAccess(c.getEmployee().getBranchId());
        return toDto(c);
    }

    /** Active contracts ending within the next {@code days} days (soonest first). */
    @Transactional(readOnly = true)
    public List<ContractDto> expiring(int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return repository.findByStatus(ContractStatus.ACTIVE).stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isAfter(limit))
                .filter(c -> branchAccessService.canAccess(c.getEmployee().getBranchId()))
                .sorted((a, b) -> a.getEndDate().compareTo(b.getEndDate()))
                .map(this::toDto).toList();
    }

    @Transactional
    public ContractDto create(ContractRequest r) {
        Employee employee = employeeRepository.findById(r.employeeId()).orElseThrow(() -> ApiException.notFound("Employee not found"));
        branchAccessService.assertCanAccess(employee.getBranchId());
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(employee.fullName() + " has left the company.");
        }
        Contract c = new Contract();
        c.setContractNumber(nextNumber());
        c.setEmployee(employee);
        apply(c, r);
        Contract saved = repository.save(c);
        auditLogService.record("CONTRACT_CREATED", "Created contract " + saved.getContractNumber() + " for " + employee.fullName(), "Contract", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public ContractDto update(Long id, ContractRequest r) {
        Contract c = entity(id);
        branchAccessService.assertCanAccess(c.getEmployee().getBranchId());
        if (c.getStatus() != ContractStatus.DRAFT && c.getStatus() != ContractStatus.ACTIVE) {
            throw ApiException.conflict("A " + c.getStatus().name().toLowerCase() + " contract can't be edited.");
        }
        if (!c.getEmployee().getId().equals(r.employeeId())) {
            throw ApiException.badRequest("The employee of a contract can't be changed.");
        }
        apply(c, r);
        if (c.getStatus() == ContractStatus.ACTIVE) {
            checkOverlap(c);
        }
        Contract saved = repository.save(c);
        auditLogService.record("CONTRACT_UPDATED", "Updated contract " + saved.getContractNumber(), "Contract", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public ContractDto activate(Long id) {
        Contract c = entity(id);
        branchAccessService.assertCanAccess(c.getEmployee().getBranchId());
        if (c.getStatus() != ContractStatus.DRAFT) {
            throw ApiException.conflict("Only a draft contract can be activated.");
        }
        if (c.getEmployee().getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(c.getEmployee().fullName() + " has left the company.");
        }
        if (c.getEndDate() != null && c.getEndDate().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("This contract has already ended.");
        }
        checkOverlap(c);
        c.setStatus(ContractStatus.ACTIVE);
        repository.save(c);
        auditLogService.record("CONTRACT_ACTIVATED", "Activated contract " + c.getContractNumber() + " for " + c.getEmployee().fullName(), "Contract", id);
        return toDto(c);
    }

    @Transactional
    public ContractDto terminate(Long id, ContractTerminateRequest r) {
        Contract c = entity(id);
        branchAccessService.assertCanAccess(c.getEmployee().getBranchId());
        if (c.getStatus() != ContractStatus.ACTIVE) {
            throw ApiException.conflict("Only an active contract can be terminated.");
        }
        if (r.date().isBefore(c.getStartDate())) {
            throw ApiException.badRequest("The termination date can't be before the contract starts.");
        }
        c.setStatus(ContractStatus.TERMINATED);
        c.setTerminationDate(r.date());
        c.setTerminationReason(HrSupport.blankToNull(r.reason()));
        repository.save(c);
        auditLogService.record("CONTRACT_TERMINATED", "Terminated contract " + c.getContractNumber() + " on " + r.date(), "Contract", id);
        return toDto(c);
    }

    /** Creates a draft that continues this contract (same terms, new dates); activate it once it's agreed. */
    @Transactional
    public ContractDto renew(Long id, RenewRequest r) {
        Contract old = entity(id);
        branchAccessService.assertCanAccess(old.getEmployee().getBranchId());
        if (old.getStatus() != ContractStatus.ACTIVE && old.getStatus() != ContractStatus.EXPIRED) {
            throw ApiException.conflict("Only an active or expired contract can be renewed.");
        }
        if (old.getEmployee().getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(old.getEmployee().fullName() + " has left the company.");
        }
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("The end date can't be before the start date.");
        }
        Contract c = new Contract();
        c.setContractNumber(nextNumber());
        c.setEmployee(old.getEmployee());
        c.setType(old.getType() == ContractType.PROBATION ? ContractType.FIXED_TERM : old.getType());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setJobTitle(old.getJobTitle());
        c.setBaseSalary(r.baseSalary() != null ? r.baseSalary() : old.getBaseSalary());
        c.setRenewsContractNumber(old.getContractNumber());
        c.setNotes(old.getNotes());
        for (ContractComponent oc : old.getComponents()) {
            ContractComponent nc = new ContractComponent();
            nc.setContract(c);
            nc.setName(oc.getName());
            nc.setKind(oc.getKind());
            nc.setAmount(oc.getAmount());
            nc.setTaxable(oc.isTaxable());
            c.getComponents().add(nc);
        }
        Contract saved = repository.save(c);
        auditLogService.record("CONTRACT_RENEWED", "Draft contract " + saved.getContractNumber() + " renews " + old.getContractNumber(), "Contract", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Contract c = entity(id);
        branchAccessService.assertCanAccess(c.getEmployee().getBranchId());
        if (c.getStatus() != ContractStatus.DRAFT) {
            throw ApiException.conflict("Only a draft contract can be deleted. Terminate an active one instead.");
        }
        repository.delete(c);
        auditLogService.record("CONTRACT_DELETED", "Deleted draft contract " + c.getContractNumber(), "Contract", id);
    }

    /** Marks active contracts whose end date has passed as expired. Returns how many. */
    @Transactional
    public int expireOverdue() {
        int n = 0;
        for (Contract c : repository.findByStatus(ContractStatus.ACTIVE)) {
            if (c.getEndDate() != null && c.getEndDate().isBefore(LocalDate.now())) {
                c.setStatus(ContractStatus.EXPIRED);
                repository.save(c);
                n++;
            }
        }
        if (n > 0) {
            auditLogService.record("CONTRACTS_EXPIRED", n + " contract(s) reached their end date and were marked expired");
        }
        return n;
    }

    @Scheduled(cron = "0 15 5 * * *")
    public void dailyExpiry() {
        if (moduleService.isEnabled(ModuleKey.CONTRACTS)) {
            expireOverdue();
        }
    }

    /** The last day a contract covers: its end date or its early termination date, whichever is first (null = open-ended). */
    public static LocalDate effectiveEnd(Contract c) {
        LocalDate end = c.getEndDate();
        if (c.getTerminationDate() != null && (end == null || c.getTerminationDate().isBefore(end))) {
            end = c.getTerminationDate();
        }
        return end;
    }

    private void checkOverlap(Contract c) {
        LocalDate end = effectiveEnd(c);
        for (Contract o : repository.findByEmployeeIdOrderByStartDateDesc(c.getEmployee().getId())) {
            if (o.getId().equals(c.getId()) || o.getStatus() != ContractStatus.ACTIVE) {
                continue;
            }
            LocalDate oEnd = effectiveEnd(o);
            boolean overlaps = (end == null || !o.getStartDate().isAfter(end)) && (oEnd == null || !oEnd.isBefore(c.getStartDate()));
            if (overlaps) {
                throw ApiException.conflict(c.getEmployee().fullName() + " already has an active contract (" + o.getContractNumber()
                        + ") covering these dates. Terminate it or change the dates first.");
            }
        }
    }

    private void apply(Contract c, ContractRequest r) {
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("The end date can't be before the start date.");
        }
        if (r.baseSalary().signum() < 0) {
            throw ApiException.badRequest("The salary can't be negative.");
        }
        ContractType type = r.type() != null ? r.type() : ContractType.PERMANENT;
        if (type != ContractType.PERMANENT && r.endDate() == null) {
            throw ApiException.badRequest("A " + type.name().toLowerCase().replace('_', ' ') + " contract needs an end date.");
        }
        c.setType(type);
        c.setStartDate(r.startDate());
        c.setEndDate(type == ContractType.PERMANENT ? null : r.endDate());
        c.setJobTitle(HrSupport.blankToNull(r.jobTitle()) != null ? r.jobTitle().trim() : c.getEmployee().getJobTitle());
        c.setBaseSalary(r.baseSalary());
        c.setNotes(HrSupport.blankToNull(r.notes()));
        c.getComponents().clear();
        if (r.components() != null) {
            for (ComponentRequest cr : r.components()) {
                if (cr.amount().signum() < 0) {
                    throw ApiException.badRequest(cr.name() + ": the amount can't be negative.");
                }
                ContractComponent comp = new ContractComponent();
                comp.setContract(c);
                comp.setName(cr.name().trim());
                comp.setKind(cr.kind() != null ? cr.kind() : ComponentKind.EARNING);
                comp.setAmount(cr.amount());
                comp.setTaxable(cr.taxable() == null || cr.taxable());
                c.getComponents().add(comp);
            }
        }
    }

    Contract entity(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Contract not found"));
    }

    private ContractDto toDto(Contract c) {
        Integer days = c.getStatus() == ContractStatus.ACTIVE && c.getEndDate() != null
                ? (int) ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate()) : null;
        return ContractDto.from(c, days);
    }

    private String nextNumber() {
        long n = repository.count() + 1;
        String number = String.format("CT-%04d", n);
        while (repository.existsByContractNumber(number)) {
            n++;
            number = String.format("CT-%04d", n);
        }
        return number;
    }
}
