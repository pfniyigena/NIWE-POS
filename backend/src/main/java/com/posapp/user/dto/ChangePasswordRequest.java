package com.posapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service password change (Task: "change my password") — unlike
 * {@link ResetPasswordRequest} (an admin resetting *someone else's* password, no proof of the old
 * one needed, since the admin's own authority is the proof), this requires the caller's current
 * password so that changing a password proves the caller actually is that user, not just that
 * they're logged in as someone with a still-valid JWT.
 */
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
