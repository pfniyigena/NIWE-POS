package com.posapp.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads "Authorization: Bearer <jwt>", validates it, and populates the SecurityContext for this request. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final com.posapp.law.LawPortalAuthService portalAuthService;

    public JwtAuthFilter(JwtService jwtService, AppUserDetailsService userDetailsService, com.posapp.law.LawPortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            io.jsonwebtoken.Claims claims = jwtService.parseClaims(token);
            if ("portal".equals(claims.get("typ", String.class))) {
                // A client portal token: only the portal's own endpoints take it, and it never touches the firm's user table.
                if (request.getRequestURI().startsWith("/api/portal/") && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var portalAuth = portalAuthService.authenticate(claims);
                    if (portalAuth != null) SecurityContextHolder.getContext().setAuthentication(portalAuth);
                }
                filterChain.doFilter(request, response);
                return;
            }
            String username = claims.getSubject();
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid/expired token: leave the context unauthenticated, let the security chain 401 it.
        }

        filterChain.doFilter(request, response);
    }
}
