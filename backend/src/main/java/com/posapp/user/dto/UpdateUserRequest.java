package com.posapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    /** Optional (Fourier: "Add forget password feature ... input the email") — see AppUser.email's javadoc for why this isn't required. */
    private String email;

    @NotNull(message = "Role is required")
    private String role;

    private boolean active = true;

    /** Fourier: "Build a full permissions system" — the admin form always sends the full desired set; null/omitted is treated as "no change" (see UserService.updateUser). */
    private Set<String> permissions;

    /** Branches this user is limited to; empty = unrestricted. On update, null/omitted leaves the existing assignment untouched. */
    private Set<Long> branchIds;

    public Set<Long> getBranchIds() { return branchIds; }
    public void setBranchIds(Set<Long> branchIds) { this.branchIds = branchIds; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    /** Optional admin-defined role (see CustomRole); ignored for an ADMIN. */
    private Long customRoleId;
    /** True when the form is sending customRoleId on purpose (null then means "no custom role"); false/omitted leaves it unchanged. */
    private boolean customRoleProvided;

    public boolean isCustomRoleProvided() { return customRoleProvided; }
    public void setCustomRoleProvided(boolean customRoleProvided) { this.customRoleProvided = customRoleProvided; }

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }
}
