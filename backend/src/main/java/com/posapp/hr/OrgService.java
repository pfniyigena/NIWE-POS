package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDtos.*;
import com.posapp.hr.dto.HrRequests.DepartmentRequest;
import com.posapp.hr.dto.HrRequests.PositionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Departments, positions and the organisation chart. */
@Service
public class OrgService {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public OrgService(DepartmentRepository departmentRepository, PositionRepository positionRepository,
                      EmployeeRepository employeeRepository, AuditLogService auditLogService) {
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    // ---- departments ----

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments() {
        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(d -> DepartmentDto.from(d, employeeRepository.countByDepartmentId(d.getId()))).toList();
    }

    @Transactional
    public DepartmentDto createDepartment(DepartmentRequest r) {
        String name = r.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already a department called " + name + ".");
        }
        Department d = new Department();
        apply(d, r, name);
        Department saved = departmentRepository.save(d);
        auditLogService.record("DEPARTMENT_CREATED", "Created department " + saved.getName(), "Department", saved.getId());
        return DepartmentDto.from(saved, 0);
    }

    @Transactional
    public DepartmentDto updateDepartment(Long id, DepartmentRequest r) {
        Department d = department(id);
        String name = r.name().trim();
        if (!d.getName().equalsIgnoreCase(name) && departmentRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("There is already a department called " + name + ".");
        }
        apply(d, r, name);
        Department saved = departmentRepository.save(d);
        auditLogService.record("DEPARTMENT_UPDATED", "Updated department " + saved.getName(), "Department", saved.getId());
        return DepartmentDto.from(saved, employeeRepository.countByDepartmentId(id));
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department d = department(id);
        if (employeeRepository.existsByDepartmentId(id) || departmentRepository.existsByParentId(id) || positionRepository.existsByDepartmentId(id)) {
            throw ApiException.conflict("Department " + d.getName() + " still has employees, positions or sub-departments. Move them first or mark it inactive.");
        }
        departmentRepository.delete(d);
        auditLogService.record("DEPARTMENT_DELETED", "Deleted department " + d.getName(), "Department", id);
    }

    private void apply(Department d, DepartmentRequest r, String name) {
        d.setName(name);
        d.setCode(HrSupport.blankToNull(r.code()));
        if (r.parentId() != null) {
            Department parent = department(r.parentId());
            if (d.getId() != null) {
                for (Department up = parent; up != null; up = up.getParent()) {
                    if (up.getId().equals(d.getId())) {
                        throw ApiException.badRequest("A department can't be placed under itself or one of its sub-departments.");
                    }
                }
            }
            d.setParent(parent);
        } else {
            d.setParent(null);
        }
        d.setManager(r.managerId() != null ? employee(r.managerId()) : null);
        if (r.active() != null) {
            d.setActive(r.active());
        }
    }

    Department department(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Department not found"));
    }

    private Employee employee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
    }

    // ---- positions ----

    @Transactional(readOnly = true)
    public List<PositionDto> listPositions() {
        return positionRepository.findAllByOrderByTitleAsc().stream()
                .map(p -> PositionDto.from(p, employeeRepository.countByPositionIdAndStatus(p.getId(), EmployeeStatus.ACTIVE))).toList();
    }

    @Transactional
    public PositionDto createPosition(PositionRequest r) {
        Position p = new Position();
        applyPosition(p, r);
        Position saved = positionRepository.save(p);
        auditLogService.record("POSITION_CREATED", "Created position " + saved.getTitle(), "Position", saved.getId());
        return PositionDto.from(saved, 0);
    }

    @Transactional
    public PositionDto updatePosition(Long id, PositionRequest r) {
        Position p = position(id);
        applyPosition(p, r);
        Position saved = positionRepository.save(p);
        auditLogService.record("POSITION_UPDATED", "Updated position " + saved.getTitle(), "Position", saved.getId());
        return PositionDto.from(saved, employeeRepository.countByPositionIdAndStatus(id, EmployeeStatus.ACTIVE));
    }

    @Transactional
    public void deletePosition(Long id) {
        Position p = position(id);
        if (employeeRepository.existsByPositionId(id)) {
            throw ApiException.conflict("Position " + p.getTitle() + " is held by employees. Move them first or mark it inactive.");
        }
        positionRepository.delete(p);
        auditLogService.record("POSITION_DELETED", "Deleted position " + p.getTitle(), "Position", id);
    }

    private void applyPosition(Position p, PositionRequest r) {
        p.setTitle(r.title().trim());
        p.setDepartment(r.departmentId() != null ? department(r.departmentId()) : null);
        p.setDescription(HrSupport.blankToNull(r.description()));
        if (r.plannedHeadcount() != null) {
            if (r.plannedHeadcount() < 0) {
                throw ApiException.badRequest("Planned headcount can't be negative.");
            }
            p.setPlannedHeadcount(r.plannedHeadcount());
        }
        if (r.active() != null) {
            p.setActive(r.active());
        }
    }

    Position position(Long id) {
        return positionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Position not found"));
    }

    // ---- org chart ----

    @Transactional(readOnly = true)
    public OrgChartDto chart() {
        List<Department> departments = departmentRepository.findAllByOrderByNameAsc().stream().filter(Department::isActive).toList();
        List<Employee> employees = employeeRepository.findAllByOrderByFirstNameAscLastNameAsc().stream()
                .filter(e -> e.getStatus() != EmployeeStatus.TERMINATED).toList();

        Map<Long, Long> counts = new HashMap<>();
        for (Employee e : employees) {
            if (e.getDepartment() != null) {
                counts.merge(e.getDepartment().getId(), 1L, Long::sum);
            }
        }
        Map<Long, List<Department>> byParent = new HashMap<>();
        List<Department> roots = new ArrayList<>();
        for (Department d : departments) {
            Long parentId = d.getParent() != null ? d.getParent().getId() : null;
            boolean parentListed = parentId != null && departments.stream().anyMatch(x -> x.getId().equals(parentId));
            if (parentListed) {
                byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(d);
            } else {
                roots.add(d);
            }
        }
        List<DeptNode> deptTree = roots.stream().map(d -> deptNode(d, byParent, counts)).toList();

        Map<Long, List<Employee>> reports = new HashMap<>();
        List<Employee> top = new ArrayList<>();
        for (Employee e : employees) {
            Long managerId = e.getManager() != null ? e.getManager().getId() : null;
            boolean managerListed = managerId != null && employees.stream().anyMatch(x -> x.getId().equals(managerId));
            if (managerListed) {
                reports.computeIfAbsent(managerId, k -> new ArrayList<>()).add(e);
            } else {
                top.add(e);
            }
        }
        List<PersonNode> people = top.stream().map(e -> personNode(e, reports, new java.util.HashSet<>())).toList();
        return new OrgChartDto(deptTree, people);
    }

    private DeptNode deptNode(Department d, Map<Long, List<Department>> byParent, Map<Long, Long> counts) {
        List<DeptNode> children = byParent.getOrDefault(d.getId(), List.of()).stream().map(c -> deptNode(c, byParent, counts)).toList();
        return new DeptNode(d.getId(), d.getName(), d.getManager() != null ? d.getManager().fullName() : null,
                counts.getOrDefault(d.getId(), 0L), children);
    }

    private PersonNode personNode(Employee e, Map<Long, List<Employee>> reports, java.util.Set<Long> seen) {
        seen.add(e.getId());
        List<PersonNode> children = new ArrayList<>();
        for (Employee r : reports.getOrDefault(e.getId(), List.of())) {
            if (!seen.contains(r.getId())) { // guards against a reporting loop
                children.add(personNode(r, reports, seen));
            }
        }
        return new PersonNode(e.getId(), e.fullName(), e.getJobTitle(), e.getDepartment() != null ? e.getDepartment().getName() : null, children);
    }
}
