package com.posapp.user;

import com.posapp.security.AppUserPrincipal;
import com.posapp.user.dto.ChangePasswordRequest;
import com.posapp.user.dto.SetPinRequest;
import com.posapp.user.dto.UpdateMyProfileRequest;
import com.posapp.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service "My Profile" (Task: "Add my profile, change my password") — deliberately a
 * separate controller from {@link UserController}, which is {@code ADMIN}-only end to end:
 * every endpoint here is open to *any* logged-in user acting on their own account only (there's
 * no id in the URL to act on someone else's — the caller's own id always comes from the JWT via
 * {@code AppUserPrincipal}, the same "no id in the path, principal decides who" shape
 * {@code CashierSessionController}'s {@code mine}/{@code close} already use for the same reason).
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserDto get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.getOwnProfile(principal.getId());
    }

    @PutMapping
    public UserDto update(@Valid @RequestBody UpdateMyProfileRequest request,
                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.updateOwnProfile(principal.getId(), request);
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                @AuthenticationPrincipal AppUserPrincipal principal) {
        userService.changeOwnPassword(principal.getId(), request);
    }

    /**
     * Self-service "change my PIN" (Task: "Add change PIN inside my profile") — reuses
     * {@link UserService#setPin} as-is (same 4-6-digit validation, same uniqueness-among-active-
     * users check CashierSessionService relies on), just aimed at the caller's own id instead of
     * one an admin picked. Deliberately doesn't require the caller's *current* PIN first, unlike
     * {@link #changePassword}: the PIN is a secondary quick-identify code (not the login
     * credential that got them into My Profile in the first place), and requiring the old one
     * would strand anyone who forgot it with no self-service way to reset it.
     */
    @PostMapping("/pin")
    public UserDto changePin(@Valid @RequestBody SetPinRequest request,
                              @AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.setPin(principal.getId(), request);
    }
}
