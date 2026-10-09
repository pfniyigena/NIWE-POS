package com.posapp.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Self-service profile edit (Task: "Add my profile") — deliberately narrower than
 * {@link UpdateUserRequest}, which an admin uses to edit *someone else's* account. A user editing
 * their own profile can change their display name, but not their own role or active flag (that
 * would let a cashier promote themselves to ADMIN, or let anyone lock themselves out) and not
 * their username (kept stable, same reasoning as every other immutable-code field in this app).
 * Can set/change their own email though (Fourier: "Add forget password feature ... input the
 * email") — a user setting up forgot-password for their own account is exactly the kind of thing
 * "self-service" is for, same tier as changing your own display name, not the role/active
 * restriction above.
 */
public class UpdateMyProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String email;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
