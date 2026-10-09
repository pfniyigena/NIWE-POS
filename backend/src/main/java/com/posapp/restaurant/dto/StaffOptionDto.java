package com.posapp.restaurant.dto;

import com.posapp.user.AppUser;

/**
 * Minimal staff option for pickers open to any logged-in user (e.g. deliveries-board driver
 * assignment) — deliberately never exposes {@code AppUser}'s own sensitive fields
 * (passwordHash/pinHash/email), unlike {@code UserController}'s ADMIN-only {@code /api/users},
 * which a non-admin cashier can't call.
 */
public record StaffOptionDto(Long id, String fullName) {
    public static StaffOptionDto from(AppUser user) {
        return new StaffOptionDto(user.getId(), user.getFullName());
    }
}
