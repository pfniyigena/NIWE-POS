package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.CustomerRepository;
import com.posapp.law.dto.LawPortalDtos.InviteInfoDto;
import com.posapp.law.dto.LawPortalDtos.SessionDto;
import com.posapp.law.dto.LawPortalRequests.AcceptRequest;
import com.posapp.law.dto.LawPortalRequests.LoginRequest;
import com.posapp.law.dto.LawPortalRequests.PasswordRequest;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.security.JwtService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Signing in to the client portal. A portal login is its own kind of account (see {@link LawPortalAccount}): its token carries typ=portal, is
 * accepted only by the portal's own endpoints, and never refers to the firm's user table. Passwords are set through a one-time invitation
 * link; five wrong passwords lock the account for fifteen minutes; every failure gives the same answer.
 */
@Service
public class LawPortalAuthService {

    public static final int SESSION_MINUTES = 120;
    public static final long INVITE_DAYS = 7;
    static final int MAX_FAILS = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC = "The e-mail or password is not correct, or the account is temporarily locked.";

    private final LawPortalAccountRepository accounts;
    private final LawSettingsRepository settingsRepository;
    private final ModuleService moduleService;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final AuditLogService audit;
    private final String dummyHash;

    public LawPortalAuthService(LawPortalAccountRepository accounts, LawSettingsRepository settingsRepository, ModuleService moduleService, PasswordEncoder encoder,
                                JwtService jwt, CustomerRepository customerRepository, StoreSettingsRepository storeSettingsRepository, AuditLogService audit) {
        this.accounts = accounts;
        this.settingsRepository = settingsRepository;
        this.moduleService = moduleService;
        this.encoder = encoder;
        this.jwt = jwt;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.audit = audit;
        this.dummyHash = encoder.encode("not-a-real-password-" + RANDOM.nextLong());
    }

    // ------------------------------------------------------------------ tokens

    /** A fresh random one-time token (URL safe). */
    static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    boolean portalOpen() {
        if (!moduleService.isEnabled(ModuleKey.LAW_FIRM)) return false;
        return settingsRepository.findAll().stream().findFirst().map(LawSettings::isPortalEnabled).orElse(false);
    }

    private static void checkPassword(String password, String email) {
        if (password == null || password.length() < 10) throw ApiException.badRequest("The password must be at least 10 characters.");
        if (password.length() > 100) throw ApiException.badRequest("The password is too long.");
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) throw ApiException.badRequest("The password must contain letters and numbers.");
        if (email != null && password.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT))) throw ApiException.badRequest("The password must not contain your e-mail address.");
    }

    private SessionDto session(LawPortalAccount a) {
        return new SessionDto(jwt.generatePortalToken(a.getId(), SESSION_MINUTES), SESSION_MINUTES, a.getName(), a.getEmail());
    }

    // ------------------------------------------------------------------ sign in

    @Transactional(noRollbackFor = ApiException.class)
    public SessionDto login(LoginRequest r) {
        if (r == null || r.email() == null || r.password() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        if (!portalOpen()) throw new ApiException(HttpStatus.UNAUTHORIZED, "The client portal is not available.");
        LawPortalAccount a = accounts.findByEmail(normalise(r.email())).orElse(null);
        if (a == null || a.getPasswordHash() == null || !a.isActive()) {
            encoder.matches(r.password(), dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        }
        Instant now = Instant.now();
        if (a.getLockedUntil() != null && a.getLockedUntil().isAfter(now)) {
            encoder.matches(r.password(), dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        }
        if (!encoder.matches(r.password(), a.getPasswordHash())) {
            a.setFailedAttempts(a.getFailedAttempts() + 1);
            if (a.getFailedAttempts() >= MAX_FAILS) {
                a.setLockedUntil(now.plus(LOCK));
                a.setFailedAttempts(0);
                audit.record("LAW_PORTAL_LOCKED", "Portal account " + a.getEmail() + " locked for 15 minutes after wrong passwords", "LawPortalAccount", a.getId());
            }
            accounts.save(a);
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        }
        a.setFailedAttempts(0);
        a.setLockedUntil(null);
        a.setLastLoginAt(now);
        accounts.save(a);
        audit.record("LAW_PORTAL_LOGIN", "Client portal sign-in: " + a.getEmail(), "LawPortalAccount", a.getId());
        return session(a);
    }

    // ------------------------------------------------------------------ invitation

    private LawPortalAccount byInvite(String token) {
        if (token == null || token.isBlank() || token.length() > 100) throw ApiException.badRequest("This link is not valid.");
        LawPortalAccount a = accounts.findByInviteTokenHash(hash(token.trim())).orElseThrow(() -> ApiException.badRequest("This link is not valid or has already been used."));
        if (a.getInviteExpiresAt() == null || a.getInviteExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("This link has expired. Ask the firm to send you a new one.");
        if (!a.isActive()) throw ApiException.badRequest("This account is switched off. Contact the firm.");
        return a;
    }

    @Transactional(readOnly = true)
    public InviteInfoDto inviteInfo(String token) {
        if (!portalOpen()) throw ApiException.badRequest("The client portal is not available.");
        LawPortalAccount a = byInvite(token);
        StoreSettings s = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        return new InviteInfoDto(a.getName(), a.getEmail(), s != null ? s.getBusinessName() : null);
    }

    @Transactional
    public SessionDto accept(AcceptRequest r) {
        if (!portalOpen()) throw ApiException.badRequest("The client portal is not available.");
        LawPortalAccount a = byInvite(r == null ? null : r.token());
        checkPassword(r.password(), a.getEmail());
        a.setPasswordHash(encoder.encode(r.password()));
        a.setInviteTokenHash(null);
        a.setInviteExpiresAt(null);
        a.setFailedAttempts(0);
        a.setLockedUntil(null);
        a.setSessionsValidFrom(Instant.now());
        a.setLastLoginAt(Instant.now());
        accounts.save(a);
        audit.record("LAW_PORTAL_ACCEPTED", "Portal account " + a.getEmail() + " set a password", "LawPortalAccount", a.getId());
        return session(a);
    }

    @Transactional
    public void changePassword(LawPortalPrincipal p, PasswordRequest r) {
        LawPortalAccount a = accounts.findById(p.accountId()).orElseThrow(() -> ApiException.forbidden("Sign in again."));
        if (r == null || r.currentPassword() == null || !encoder.matches(r.currentPassword(), a.getPasswordHash())) throw ApiException.badRequest("The current password is not correct.");
        checkPassword(r.newPassword(), a.getEmail());
        a.setPasswordHash(encoder.encode(r.newPassword()));
        a.setSessionsValidFrom(Instant.now());
        accounts.save(a);
        audit.record("LAW_PORTAL_PASSWORD", "Portal account " + a.getEmail() + " changed its password", "LawPortalAccount", a.getId());
    }

    // ------------------------------------------------------------------ per request

    /** Called for every portal request carrying a portal token. Returns null (so the request is refused) unless the account is still valid. */
    public Authentication authenticate(Claims claims) {
        try {
            String sub = claims.getSubject();
            if (sub == null || !sub.startsWith("portal:")) return null;
            if (!portalOpen()) return null;
            LawPortalAccount a = accounts.findById(Long.parseLong(sub.substring(7))).orElse(null);
            if (a == null || !a.isActive() || a.getPasswordHash() == null) return null;
            if (a.getSessionsValidFrom() != null) {
                Instant iat = claims.getIssuedAt() == null ? Instant.EPOCH : claims.getIssuedAt().toInstant();
                if (iat.isBefore(a.getSessionsValidFrom().truncatedTo(ChronoUnit.SECONDS))) return null;
            }
            LawPortalPrincipal principal = new LawPortalPrincipal(a.getId(), a.getCustomerId(), a.getName(), a.getEmail());
            return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_PORTAL_CLIENT")));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
