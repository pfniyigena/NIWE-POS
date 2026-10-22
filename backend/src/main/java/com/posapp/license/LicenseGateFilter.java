package com.posapp.license;

import com.posapp.modules.ModuleKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Refuses calls to the API of a module that is not licensed (HTTP 403, the usual {status, error, message, timestamp} body). It looks only at the
 * start of the path, so it covers the endpoints that belong to exactly one module; shared endpoints (reports, the dashboard, products,
 * customers, ...) stay open and the menus of a locked module are hidden by the frontend. It runs after Spring Security, so only signed-in callers get here.
 * Public pages (/api/public/**) are not touched: they answer 404 through {@code ModuleService.requireEnabled}.
 */
@Component
public class LicenseGateFilter extends OncePerRequestFilter {

    private record Rule(String prefix, ModuleKey key) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("/api/registers", ModuleKey.POS),
            new Rule("/api/sessions", ModuleKey.POS),
            new Rule("/api/cashier-sessions", ModuleKey.POS),
            new Rule("/api/returns", ModuleKey.POS),
            new Rule("/api/hotel", ModuleKey.HOTEL),
            new Rule("/api/restaurant", ModuleKey.RESTAURANT),
            new Rule("/api/manufacturing", ModuleKey.MANUFACTURING),
            new Rule("/api/subscription-plans", ModuleKey.SUBSCRIPTIONS),
            new Rule("/api/subscriptions", ModuleKey.SUBSCRIPTIONS),
            new Rule("/api/helpdesk", ModuleKey.HELPDESK),
            new Rule("/api/accounting/expenses", ModuleKey.EXPENSES),
            new Rule("/api/accounting/recurring-expenses", ModuleKey.EXPENSES),
            new Rule("/api/assets", ModuleKey.ASSETS),
            new Rule("/api/installments", ModuleKey.INSTALLMENTS),
            new Rule("/api/time", ModuleKey.TIME_TRACKING),
            new Rule("/api/work-orders", ModuleKey.WORK_ORDERS),
            new Rule("/api/booking", ModuleKey.BOOKING),
            new Rule("/api/law", ModuleKey.LAW_FIRM),
            new Rule("/api/portal", ModuleKey.LAW_FIRM),
            new Rule("/api/realestate", ModuleKey.REAL_ESTATE),
            new Rule("/api/propertymgmt", ModuleKey.PROPERTY_MANAGEMENT),
            new Rule("/api/hr/employees", ModuleKey.EMPLOYEES),
            new Rule("/api/hr/staff-logins", ModuleKey.EMPLOYEES),
            new Rule("/api/hr/departments", ModuleKey.ORG_STRUCTURE),
            new Rule("/api/hr/positions", ModuleKey.ORG_STRUCTURE),
            new Rule("/api/hr/org-chart", ModuleKey.ORG_STRUCTURE),
            new Rule("/api/hr/attendance", ModuleKey.ATTENDANCE),
            new Rule("/api/hr/leave-types", ModuleKey.ATTENDANCE),
            new Rule("/api/hr/leaves", ModuleKey.ATTENDANCE),
            new Rule("/api/hr/leave-balances", ModuleKey.ATTENDANCE),
            new Rule("/api/hr/holidays", ModuleKey.ATTENDANCE),
            new Rule("/api/hr/contracts", ModuleKey.CONTRACTS),
            new Rule("/api/hr/payroll", ModuleKey.PAYROLL));

    /** Reading the hotel settings is always allowed (the frontend reads the hotel switch from here for everybody); changing them is not, because that could switch the room-charge payment method on. */
    private static final String HOTEL_SETTINGS = "/api/hotel/settings";

    private final LicenseService licenseService;

    public LicenseGateFilter(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod())) {
            ModuleKey key = moduleFor(request.getMethod(), request.getRequestURI());
            if (key != null && !licenseService.isLicensed(key)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                String message = key.label() + " is not part of your license. An admin can request it in Settings > Modules.";
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"" + message + "\",\"timestamp\":\"" + Instant.now() + "\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static ModuleKey moduleFor(String method, String path) {
        if (path == null || ("GET".equalsIgnoreCase(method) && HOTEL_SETTINGS.equals(path))) return null;
        for (Rule r : RULES) {
            if (path.startsWith(r.prefix()) && (path.length() == r.prefix().length() || path.charAt(r.prefix().length()) == '/')) {
                return r.key();
            }
        }
        return null;
    }
}
