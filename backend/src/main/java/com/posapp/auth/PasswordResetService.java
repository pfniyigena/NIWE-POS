package com.posapp.auth;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.MailService;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import com.posapp.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Fourier: "Add forget password feature where user will input the email and he/she can reset the
 * password" — the whole flow lives here: {@link #requestReset} emails a one-time link,
 * {@link #resetPassword} consumes it. See {@link PasswordResetToken}'s own class javadoc for why
 * each request gets its own token row instead of one column on {@code AppUser}.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 UserService userService,
                                 MailService mailService,
                                 AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    /**
     * Deliberately never reveals whether {@code email} actually belongs to an account: a request
     * for an email that isn't registered returns normally (nothing sent, nothing thrown) exactly
     * like one that is, so a caller probing for valid emails learns nothing either way. The one
     * thing this *does* surface as a real error is email not being configured at all — that's a
     * setup problem for the administrator to fix, not information about any particular account,
     * so it's checked before the user lookup even happens (see the top-level `if` below) rather
     * than only once a matching user is found.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("Email is required");
        }
        if (!mailService.isConfigured()) {
            throw ApiException.badRequest("Email isn't configured on this system yet — ask your administrator to set up SMTP in Admin > Settings > Email");
        }
        Optional<AppUser> userOpt = userRepository.findByEmailIgnoreCase(email.trim());
        if (userOpt.isEmpty()) {
            return;
        }
        AppUser user = userOpt.get();
        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokenRepository.save(resetToken);

        String link = frontendUrl + "/reset-password?token=" + token;
        String html = "<p>Hello " + escapeHtml(user.getFullName()) + ",</p>"
                + "<p>We received a request to reset your password. Click the link below to choose a new one — it expires in 30 minutes.</p>"
                + "<p><a href=\"" + link + "\">Reset your password</a></p>"
                + "<p>If you didn't request this, you can safely ignore this email — your password won't change.</p>";
        mailService.send(user.getEmail(), "Reset your password", html, List.of());
        auditLogService.record("PASSWORD_RESET_REQUESTED",
                user.getUsername() + " requested a password reset link", "AppUser", user.getId());
    }

    /** Single-use and time-limited — see the class javadoc and {@link PasswordResetToken}'s own fields for what's actually checked here. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid"));
        if (resetToken.isUsed()) {
            throw ApiException.badRequest("This reset link has already been used — request a new one");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("This reset link has expired — request a new one");
        }
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        userService.setPasswordViaResetToken(resetToken.getUser(), newPassword);
    }

    /** 32 random bytes, URL-safe base64 — long and unguessable enough to be looked up as-is (see PasswordResetToken.token's javadoc), with no padding characters that would need extra URL-encoding in the emailed link. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
