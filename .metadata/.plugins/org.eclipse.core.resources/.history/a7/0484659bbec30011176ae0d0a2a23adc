package com.posapp.user.dto;

import com.posapp.user.AppUser;

import java.util.Set;
import java.util.stream.Collectors;

/** Never includes the password hash or PIN hash — hasPin just tells the admin UI whether one is set. */
public record UserDto(Long id, String username, String fullName, String email, String role, boolean active, boolean hasPin,
                       /** Fourier: "Build a full permissions system" — the additive ADMIN-tier powers this user holds; see Permission's own javadoc. */
                       Set<String> permissions,
                       /** Branches this user is limited to; empty = unrestricted. See AppUser.branchIds. */
                       Set<Long> branchIds) {
    public static UserDto from(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getPinHash() != null,
                u.getPermissions().stream().map(Enum::name).collect(Collectors.toSet()),
                new java.util.HashSet<>(u.getBranchIds()));
    }
}
