package com.posapp.user;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.user.dto.AccessAreaDto;
import com.posapp.user.dto.CustomRoleDto;
import com.posapp.user.dto.CustomRoleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Admin-defined roles (Admin > Staff > Roles). See {@link CustomRole}. */
@Service
public class CustomRoleService {

    private final CustomRoleRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public CustomRoleService(CustomRoleRepository repository, UserRepository userRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    /** The areas a role can be given, in display order. REPORTS is view-only. */
    public List<AccessAreaDto> areas() {
        List<AccessAreaDto> result = new ArrayList<>();
        for (AccessArea a : AccessArea.values()) {
            result.add(new AccessAreaDto(a.name(), a.getLabel(), a.getDescription(), a == AccessArea.REPORTS));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CustomRoleDto> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(r -> CustomRoleDto.from(r, userRepository.countByCustomRoleId(r.getId()))).toList();
    }

    @Transactional
    public CustomRoleDto create(CustomRoleRequest request) {
        String name = request.getName().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A role named '" + name + "' already exists");
        }
        CustomRole role = new CustomRole();
        apply(role, request, name);
        CustomRole saved = repository.save(role);
        auditLogService.record("ROLE_CREATED", "Created role '" + saved.getName() + "' with " + describe(saved.getGrants()), "CustomRole", saved.getId());
        return CustomRoleDto.from(saved, 0);
    }

    @Transactional
    public CustomRoleDto update(Long id, CustomRoleRequest request) {
        CustomRole role = getOrThrow(id);
        String name = request.getName().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A role named '" + name + "' already exists");
        }
        apply(role, request, name);
        CustomRole saved = repository.save(role);
        auditLogService.record("ROLE_UPDATED", "Updated role '" + saved.getName() + "': " + describe(saved.getGrants())
                + (saved.isActive() ? "" : " (inactive)"), "CustomRole", saved.getId());
        return CustomRoleDto.from(saved, userRepository.countByCustomRoleId(id));
    }

    @Transactional
    public void delete(Long id) {
        CustomRole role = getOrThrow(id);
        long users = userRepository.countByCustomRoleId(id);
        if (users > 0) {
            throw ApiException.conflict("'" + role.getName() + "' is still assigned to " + users + " user(s) — move them to another role first");
        }
        repository.delete(role);
        auditLogService.record("ROLE_DELETED", "Deleted role '" + role.getName() + "'", "CustomRole", id);
    }

    /** Used by UserService when assigning a role to a user. */
    @Transactional(readOnly = true)
    public CustomRole getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Role not found: " + id));
    }

    private void apply(CustomRole role, CustomRoleRequest request, String name) {
        Set<String> grants = AccessGrants.normalize(request.getGrants());
        // Reports are view-only: a MANAGE grant there is meaningless, so it is stored as VIEW.
        if (grants.remove(AccessGrants.code(AccessArea.REPORTS, AccessLevel.MANAGE))) {
            grants.add(AccessGrants.code(AccessArea.REPORTS, AccessLevel.VIEW));
        }
        role.setName(name);
        role.setDescription(request.getDescription() == null || request.getDescription().isBlank() ? null : request.getDescription().trim());
        role.setActive(request.isActive());
        role.getGrants().clear();
        role.getGrants().addAll(grants);
    }

    private static String describe(Set<String> grants) {
        return grants.isEmpty() ? "no access areas" : String.join(", ", grants.stream().sorted().toList());
    }
}
