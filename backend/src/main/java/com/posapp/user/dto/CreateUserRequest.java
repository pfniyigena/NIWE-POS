package com.posapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    /** Optional (Fourier: "Add forget password feature ... input the email") — see AppUser.email's javadoc for why this isn't required. */
    private String email;

    @NotNull(message = "Role is required")
    private String role;

    /** Fourier: "Build a full permissions system" — optional; null/omitted means no extra grants. */
    private Set<String> permissions;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
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
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    /** Optional admin-defined role (see CustomRole); ignored for an ADMIN. */
    private Long customRoleId;

    public Long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(Long customRoleId) { this.customRoleId = customRoleId; }
}
