package com.posapp.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public class CustomRoleRequest {

    @NotBlank
    private String name;
    private String description;
    private boolean active = true;
    /** Grant codes such as INVENTORY_MANAGE or REPORTS_VIEW. */
    private Set<String> grants;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Set<String> getGrants() { return grants; }
    public void setGrants(Set<String> grants) { this.grants = grants; }
}
