package com.posapp.user.dto;

import com.posapp.user.CustomRole;

import java.util.List;

public record CustomRoleDto(Long id, String name, String description, boolean active, List<String> grants, long userCount) {
    public static CustomRoleDto from(CustomRole r, long userCount) {
        return new CustomRoleDto(r.getId(), r.getName(), r.getDescription(), r.isActive(),
                r.getGrants().stream().sorted().toList(), userCount);
    }
}
