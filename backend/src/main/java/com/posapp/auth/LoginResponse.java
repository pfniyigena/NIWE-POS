package com.posapp.auth;

import java.util.Set;

public class LoginResponse {

    private final String token;
    private final Long userId;
    private final String username;
    private final String fullName;
    private final String role;
    /** Fourier: "Build a full permissions system" — lets the frontend gate UI without a separate call. */
    private final Set<String> permissions;
    /** Effective access-area grants from the user's custom role (e.g. INVENTORY_MANAGE, INVENTORY_VIEW); empty for none. */
    private final Set<String> areas;
    private final String customRoleName;

    public LoginResponse(String token, Long userId, String username, String fullName, String role, Set<String> permissions,
                         Set<String> areas, String customRoleName) {
        this.areas = areas;
        this.customRoleName = customRoleName;
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.permissions = permissions;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public Set<String> getPermissions() { return permissions; }
    public Set<String> getAreas() { return areas; }
    public String getCustomRoleName() { return customRoleName; }
}
