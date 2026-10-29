package com.posapp.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Once the security filters have run, tells the request log (and every log line of this call) who is calling. */
@Component
public class RequestUserInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && a.getName() != null && !"anonymousUser".equals(a.getName())) {
            req.setAttribute(RequestLogFilter.ATTR_USER, a.getName());
            MDC.put(RequestLogFilter.MDC_USER, a.getName());
        }
        return true;
    }
}
