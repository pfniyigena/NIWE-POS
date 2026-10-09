package com.posapp.user;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.user.dto.ChangePasswordRequest;
import com.posapp.user.dto.CreateUserRequest;
import com.posapp.user.dto.ResetPasswordRequest;
import com.posapp.user.dto.SetPinRequest;
import com.posapp.user.dto.UpdateMyProfileRequest;
import com.posapp.user.dto.UpdateUserRequest;
import com.posapp.user.dto.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    /** Validates the branch ids assigned to a user. */
    private final BranchService branchService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditLogService auditLogService, BranchService branchService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
    }

    /** Each id must be an existing branch; null/empty is a perfectly normal "unrestricted". */
    private Set<Long> parseBranchIds(Set<Long> raw) {
        Set<Long> result = new HashSet<>();
        if (raw != null) {
            for (Long id : raw) {
                result.add(branchService.getEntityOrThrow(id).getId());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    /** Task: "Implement an audit log of who did what and when" — user management is a curated action, see AuditLogService's javadoc. */
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw ApiException.conflict("Username '" + request.getUsername() + "' is already taken");
        }
        assertEmailAvailable(request.getEmail(), null);
        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setRole(parseRole(request.getRole()));
        user.setActive(true);
        user.setPermissions(parsePermissions(request.getPermissions()));
        user.setBranchIds(parseBranchIds(request.getBranchIds()));
        AppUser saved = userRepository.save(user);
        auditLogService.record("USER_CREATED",
                "Created user " + saved.getUsername() + " (" + saved.getFullName() + ", role " + saved.getRole() + ")"
                        + (saved.getPermissions().isEmpty() ? "" : ", permissions " + saved.getPermissions()),
                "AppUser", saved.getId());
        return UserDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — logs the role/active before-and-after, since those are the two fields that actually change what a user can do. */
    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request, Long currentUserId) {
        AppUser user = getUserOrThrow(id);
        if (id.equals(currentUserId) && !request.isActive()) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        assertEmailAvailable(request.getEmail(), id);
        Role oldRole = user.getRole();
        boolean wasActive = user.isActive();
        Set<Permission> oldPermissions = new HashSet<>(user.getPermissions());
        user.setFullName(request.getFullName());
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setRole(parseRole(request.getRole()));
        user.setActive(request.isActive());
        // Fourier: "Build a full permissions system" — the admin form always sends the full desired
        // set; null (e.g. a non-permissions-aware caller) leaves the existing grants untouched
        // rather than silently wiping them.
        if (request.getPermissions() != null) {
            user.setPermissions(parsePermissions(request.getPermissions()));
        }
        Set<Long> oldBranchIds = new HashSet<>(user.getBranchIds());
        if (request.getBranchIds() != null) {
            user.setBranchIds(parseBranchIds(request.getBranchIds()));
        }
        AppUser saved = userRepository.save(user);
        StringBuilder description = new StringBuilder("Updated user ").append(saved.getUsername());
        if (oldRole != saved.getRole()) {
            description.append(": role ").append(oldRole).append(" -> ").append(saved.getRole());
        }
        if (wasActive != saved.isActive()) {
            description.append(saved.isActive() ? " — reactivated" : " — deactivated");
        }
        if (!oldPermissions.equals(saved.getPermissions())) {
            description.append(", permissions -> ").append(saved.getPermissions());
        }
        if (!oldBranchIds.equals(saved.getBranchIds())) {
            description.append(", branch access -> ").append(saved.getBranchIds().isEmpty() ? "all branches" : saved.getBranchIds());
        }
        auditLogService.record("USER_UPDATED", description.toString(), "AppUser", saved.getId());
        return UserDto.from(saved);
    }

    /** Task: "Implement an audit log of who did what and when" — records that a reset happened, never the new password itself. */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = getUserOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditLogService.record("USER_PASSWORD_RESET", "Reset password for user " + user.getUsername(), "AppUser", user.getId());
    }

    /**
     * Fourier: "Add forget password feature where user will input the email and he/she can reset
     * the password" — the actual password-setting step of that flow, called by
     * {@code com.posapp.auth.PasswordResetService} once it's verified the reset token is real,
     * unused, and unexpired. Deliberately takes the already-resolved {@link AppUser} rather than
     * an id: the token lookup that got the caller here already found the user by email (see
     * {@code PasswordResetToken}), so there's no reason to look it up a second time.
     */
    @Transactional
    public void setPasswordViaResetToken(AppUser user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditLogService.record("USER_PASSWORD_RESET_VIA_EMAIL",
                user.getUsername() + " reset their own password via the emailed reset link", "AppUser", user.getId());
    }

    /**
     * Sets/changes a staff member's PIN. Enforced unique among *active* users here — not with a
     * unique DB column, since PINs are stored hashed (each hash is unique even for the same raw
     * PIN) — by checking the new raw PIN against every other active user's existing hash. This is
     * what lets {@code CashierSessionService} resolve "who just typed this PIN" from the PIN
     * alone, with no username needed. An inactive user's old PIN is never checked, so it can be
     * reused once they're deactivated.
     */
    @Transactional
    public UserDto setPin(Long id, SetPinRequest request) {
        AppUser user = getUserOrThrow(id);
        String rawPin = request.getPin();
        boolean collision = userRepository.findByActiveTrue().stream()
                .anyMatch(other -> !other.getId().equals(id)
                        && other.getPinHash() != null
                        && passwordEncoder.matches(rawPin, other.getPinHash()));
        if (collision) {
            throw ApiException.conflict("This PIN is already in use by another active staff member — choose a different one");
        }
        user.setPinHash(passwordEncoder.encode(rawPin));
        UserDto saved = UserDto.from(userRepository.save(user));
        // Task: "Implement an audit log of who did what and when" — records that a PIN was set, never the PIN itself.
        auditLogService.record("USER_PIN_SET", "Set PIN for user " + user.getUsername(), "AppUser", user.getId());
        return saved;
    }

    /** Self-service "My Profile" (Task: "Add my profile") — any logged-in user reading their own account, not just an admin. */
    @Transactional(readOnly = true)
    public UserDto getOwnProfile(Long userId) {
        return UserDto.from(getUserOrThrow(userId));
    }

    /**
     * Self-service profile edit — see {@link UpdateMyProfileRequest}'s javadoc for why this only
     * ever touches {@code fullName}, never {@code role}/{@code active} the way an admin's
     * {@link #updateUser} can.
     */
    @Transactional
    public UserDto updateOwnProfile(Long userId, UpdateMyProfileRequest request) {
        AppUser user = getUserOrThrow(userId);
        assertEmailAvailable(request.getEmail(), userId);
        user.setFullName(request.getFullName());
        user.setEmail(normalizeEmail(request.getEmail()));
        return UserDto.from(userRepository.save(user));
    }

    /**
     * Self-service password change (Task: "change my password") — requires the caller's current
     * password (unlike an admin's {@link #resetPassword}, which needs none) so that changing a
     * password proves the caller actually is that user. Wrong current password: 400, same style
     * every other user-facing validation failure in this codebase uses.
     */
    @Transactional
    public void changeOwnPassword(Long userId, ChangePasswordRequest request) {
        AppUser user = getUserOrThrow(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // Task: "Implement an audit log of who did what and when" — a self-service password change is
        // still a security-relevant event worth a trail, same as an admin's resetPassword above.
        auditLogService.record("USER_PASSWORD_CHANGED", user.getFullName() + " changed their own password", "AppUser", user.getId());
    }

    private AppUser getUserOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found: " + id));
    }

    /** Blank/null email is always fine (see AppUser.email's javadoc — it's optional); a non-blank one must not already belong to a *different* account, so PasswordResetService.findByEmail always resolves unambiguously. */
    private void assertEmailAvailable(String email, Long excludingUserId) {
        String normalized = normalizeEmail(email);
        if (normalized == null) return;
        userRepository.findByEmailIgnoreCase(normalized).ifPresent(existing -> {
            if (excludingUserId == null || !existing.getId().equals(excludingUserId)) {
                throw ApiException.conflict("Email '" + normalized + "' is already used by another account");
            }
        });
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Unknown role: " + raw);
        }
    }

    /** Fourier: "Build a full permissions system" — null/empty is a perfectly normal "no extra grants", not an error. */
    private Set<Permission> parsePermissions(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> parsed = new HashSet<>();
        for (String name : raw) {
            try {
                parsed.add(Permission.valueOf(name));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Unknown permission: " + name);
            }
        }
        return parsed;
    }
}
