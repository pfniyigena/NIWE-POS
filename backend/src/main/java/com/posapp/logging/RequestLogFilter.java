package com.posapp.logging;

import com.posapp.register.ClientDevice;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * v1.14.0: gives every API call a short reference (shown in the response header {@code X-Request-Id} and in error bodies, and printed on every log
 * line made while the call runs), and writes one line to the access log for each call that changed something (not GET), failed (4xx / 5xx) or was
 * slow. Runs before the security filters, so it also sees calls they refuse (401 / 403). Never logs the query string (it can hold a reset token), the body or the Authorization header.
 */
@Component
@Order(org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER - 10)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String MDC_REQ = "req";
    public static final String MDC_USER = "user";
    /** Request attribute set by {@link RequestUserInterceptor} once the caller is known (the security filters run after this one). */
    public static final String ATTR_USER = "posapp.request.user";
    private static final Logger access = LoggerFactory.getLogger("com.posapp.access");

    private final long slowMs;
    private final Set<String> trustedProxies;

    public RequestLogFilter(@Value("${app.logging.slow-request-ms:2000}") long slowMs,
                            @Value("${app.device-binding.trusted-proxies:127.0.0.1,::1}") String trusted) {
        this.slowMs = slowMs;
        this.trustedProxies = ClientDevice.parseList(trusted);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p == null || !p.startsWith("/api/") || p.startsWith("/api/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put(MDC_REQ, id);
        res.setHeader("X-Request-Id", id);
        long t0 = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(req, res);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            Object attr = req.getAttribute(ATTR_USER);
            String user = attr == null ? "-" : attr.toString();
            MDC.put(MDC_USER, user);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            int status = failure != null ? 500 : res.getStatus();
            boolean write = !"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod());
            if (write || status >= 400 || ms >= slowMs) {
                String line = req.getMethod() + " " + req.getRequestURI() + " " + status + " " + ms + "ms ip=" + ClientDevice.ip(req, trustedProxies)
                        + " dev=" + shortDevice(ClientDevice.deviceId(req));
                if (status >= 500 || failure != null) access.warn(line);
                else if (ms >= slowMs) access.warn(line + " SLOW");
                else access.info(line);
            }
            MDC.remove(MDC_REQ);
            MDC.remove(MDC_USER);
        }
    }

    private static String shortDevice(String id) {
        return id == null ? "-" : id.length() <= 8 ? id : id.substring(0, 8);
    }
}
