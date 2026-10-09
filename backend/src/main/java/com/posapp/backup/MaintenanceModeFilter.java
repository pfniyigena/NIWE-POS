package com.posapp.backup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fourier: "true maintenance-mode lockout" — while {@link MaintenanceModeService} says a restore
 * is running, every request except the ones explicitly allowed below gets a flat 503 right here,
 * before CORS preflight even matters, before JwtAuthFilter parses a token, before Spring Security's
 * own authorization check, and before any controller or repository touches the database that's
 * mid DROP/CREATE. Registered in SecurityConfig ahead of JwtAuthFilter, so it's the very first thing
 * that runs on every request while the backend is up.
 * <p>
 * Allowed through regardless of maintenance mode: CORS preflight ({@code OPTIONS}, which never
 * reaches a controller anyway) and {@code /api/backups/**} itself — so the admin who kicked off the
 * restore can keep seeing its own backup list/progress rather than being locked out of the one
 * screen they're actually using. {@code /api/auth/**} (login) is deliberately NOT exempted: the
 * users table is one of the ones being dropped and rebuilt, so letting a login attempt through
 * during that brief window could surface a raw SQL error instead of this filter's clean
 * "under maintenance" response — for a restore that normally takes seconds, blocking login too is
 * the simpler, safer choice.
 * <p>
 * Every blocked request gets the same small JSON body ({@code {"maintenance":true,"message":...}})
 * regardless of what it was trying to do, so the frontend's maintenanceInterceptor can recognize it
 * with one check and show a single full-screen notice rather than whatever error handling that one
 * screen happened to have.
 */
@Component
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final String MAINTENANCE_BODY =
            "{\"maintenance\":true,\"message\":\"The system is being restored from a backup. Please wait a moment and try again.\"}";

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceModeFilter(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean exempt = "OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().startsWith("/api/backups");
        if (!exempt && maintenanceModeService.isActive()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(MAINTENANCE_BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
