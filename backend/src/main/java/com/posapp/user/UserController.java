package com.posapp.user;

import com.posapp.security.AppUserPrincipal;
import com.posapp.user.dto.CreateUserRequest;
import com.posapp.user.dto.ResetPasswordRequest;
import com.posapp.user.dto.SetPinRequest;
import com.posapp.user.dto.UpdateUserRequest;
import com.posapp.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff/user account management — every endpoint here is ADMIN-only. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.listUsers();
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id,
                           @Valid @RequestBody UpdateUserRequest request,
                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.updateUser(id, request, principal.getId());
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
    }

    /** Sets/changes this staff member's PIN, used to identify them at the terminal (see CashierSessionService). */
    @PostMapping("/{id}/pin")
    public UserDto setPin(@PathVariable Long id, @Valid @RequestBody SetPinRequest request) {
        return userService.setPin(id, request);
    }
}
