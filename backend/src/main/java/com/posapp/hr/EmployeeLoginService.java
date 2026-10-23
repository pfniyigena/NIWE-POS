package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.user.AppUser;
import com.posapp.user.CustomRole;
import com.posapp.user.CustomRoleRepository;
import com.posapp.user.UserRepository;
import com.posapp.user.UserService;
import com.posapp.user.dto.CreateUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gives employees a sign-in so each one can open "My HR" and see their own record, contract, leave, attendance and payslips.
 * The login is an ordinary staff login with no extra access; it is linked to the employee record. The first password is shown
 * once to whoever creates it (or can be typed in) and should be changed at the first sign-in (Profile).
 */
@Service
public class EmployeeLoginService {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** Must match the name the sign-in redirect looks for (core/access.ts). */
    public static final String ROLE_NAME = "Employee";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final CustomRoleRepository customRoleRepository;
    private final AuditLogService auditLogService;

    public EmployeeLoginService(EmployeeRepository employeeRepository, UserRepository userRepository, UserService userService,
                                CustomRoleRepository customRoleRepository, AuditLogService auditLogService) {
        this.customRoleRepository = customRoleRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    public record Created(Long employeeId, String employeeNumber, String employeeName, String username, String password) {
    }

    public record BulkResult(List<Created> created, List<String> skipped) {
    }

    @Transactional
    public Created create(Long employeeId, String username, String password) {
        Employee e = employeeRepository.findById(employeeId).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (e.getUserId() != null) {
            throw ApiException.conflict(e.fullName() + " already has a login.");
        }
        if (e.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(e.fullName() + " has left the company.");
        }
        String name = username == null || username.isBlank() ? freeUsername(e) : username.trim();
        if (userRepository.existsByUsernameIgnoreCase(name)) {
            throw ApiException.conflict("Username '" + name + "' is already taken.");
        }
        String pwd = password == null || password.isBlank() ? generatePassword() : password;
        if (pwd.length() < 6) {
            throw ApiException.badRequest("The password must be at least 6 characters.");
        }
        CreateUserRequest r = new CreateUserRequest();
        r.setUsername(name);
        r.setPassword(pwd);
        r.setFullName(e.fullName());
        String email = e.getEmail() == null || e.getEmail().isBlank() ? null : e.getEmail().trim();
        r.setEmail(email != null && !userRepository.existsByEmailIgnoreCase(email) ? email : null);
        r.setRole("CASHIER");
        r.setCustomRoleId(employeeRole().getId());
        AppUser user = userRepository.findById(userService.createUser(r).id()).orElseThrow();
        e.setUserId(user.getId());
        employeeRepository.save(e);
        auditLogService.record("EMPLOYEE_LOGIN_CREATED", "Gave " + e.fullName() + " (" + e.getEmployeeNumber() + ") a login: " + name, "Employee", e.getId());
        return new Created(e.getId(), e.getEmployeeNumber(), e.fullName(), name, pwd);
    }

    /** A login for every active or suspended employee who has none yet. */
    @Transactional
    public BulkResult createForAll() {
        List<Created> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByOrderByFirstNameAscLastNameAsc()) {
            if (e.getUserId() != null || e.getStatus() == EmployeeStatus.TERMINATED) {
                continue;
            }
            try {
                created.add(create(e.getId(), null, null));
            } catch (ApiException ex) {
                skipped.add(e.fullName() + ": " + ex.getMessage());
            }
        }
        return new BulkResult(created, skipped);
    }

    /** A new password for an employee's login (shown once). */
    @Transactional
    public Created resetPassword(Long employeeId) {
        Employee e = employeeRepository.findById(employeeId).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (e.getUserId() == null) {
            throw ApiException.badRequest(e.fullName() + " has no login yet.");
        }
        AppUser user = userRepository.findById(e.getUserId()).orElseThrow(() -> ApiException.notFound("Login not found"));
        String pwd = generatePassword();
        com.posapp.user.dto.ResetPasswordRequest r = new com.posapp.user.dto.ResetPasswordRequest();
        r.setNewPassword(pwd);
        userService.resetPassword(user.getId(), r);
        auditLogService.record("EMPLOYEE_LOGIN_RESET", "Reset the password of " + e.fullName() + " (" + user.getUsername() + ")", "Employee", e.getId());
        return new Created(e.getId(), e.getEmployeeNumber(), e.fullName(), user.getUsername(), pwd);
    }

    /** The role these logins get. It grants no screens of its own: it only marks the login as "employee, here for My HR" so it lands there after sign-in. */
    private CustomRole employeeRole() {
        return customRoleRepository.findAllByOrderByNameAsc().stream().filter(r -> ROLE_NAME.equalsIgnoreCase(r.getName())).findFirst().orElseGet(() -> {
            CustomRole role = new CustomRole();
            role.setName(ROLE_NAME);
            role.setDescription("Employees who sign in only to see their own HR (My HR). Grants no other screens.");
            return customRoleRepository.save(role);
        });
    }

    private String freeUsername(Employee e) {
        String base = e.getEmployeeNumber().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.isEmpty()) {
            base = "emp" + e.getId();
        }
        String candidate = base;
        int n = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = base + (++n);
        }
        return candidate;
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
