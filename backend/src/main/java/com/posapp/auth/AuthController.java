package com.posapp.auth;

import com.posapp.auth.dto.ForgotPasswordRequest;
import com.posapp.auth.dto.ResetPasswordRequest;
import com.posapp.security.AppUserPrincipal;
import com.posapp.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here is public (`/api/auth/**` — see SecurityConfig), by design: {@code login}
 * always was, and Fourier's forgot/reset-password pair below has to be too — a user who's locked
 * out has no JWT to authenticate with in the first place. See PasswordResetService's own javadoc
 * for how each stays safe without one (a fixed-shape response from forgotPassword regardless of
 * whether the email exists; a single-use, time-limited, unguessable token for resetPassword).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var authToken = new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
        var authentication = authenticationManager.authenticate(authToken);
        if (!(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var user = principal.getUser();
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name(), user.getId());
        var permissionNames = user.getPermissions().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getFullName(), user.getRole().name(), permissionNames);
    }

    /** Fourier: "user will input the email and he/she can reset the password" — always returns 200 with no body, whether or not that email is actually registered (see PasswordResetService.requestReset's javadoc). */
    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
    }

    /** The link from that email lands here — see PasswordResetService.resetPassword for what makes a token valid. */
    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
    }
}
