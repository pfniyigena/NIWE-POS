package com.posapp.register;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the till↔device binding to the calls that actually operate a till: opening a shift,
 * entering the terminal, starting or changing a sale, moving cash. Reading reports, receipts and
 * history, closing a shift, and the admin screens are deliberately NOT restricted, so a manager can
 * still look after a till from the back office.
 */
@Component
public class RegisterDeviceInterceptor implements HandlerInterceptor {

    private static final Pattern OPEN_SHIFT = Pattern.compile("^/api/registers/(\\d+)/sessions$");
    private static final Pattern CASH_MOVE = Pattern.compile("^/api/sessions/(\\d+)/cash-movements$");
    private static final Pattern ENTER = Pattern.compile("^/api/sessions/(\\d+)/cashier-sessions(/mine)?$");
    private static final Pattern CS_ORDERS = Pattern.compile("^/api/cashier-sessions/(\\d+)/(orders|active-order)$");
    private static final Pattern ORDER_OP = Pattern.compile("^/api/orders/(\\d+)/(lines|discount|customer|coupons|park|discard|checkout)(/.*)?$");

    private final DeviceBindingService service;

    public RegisterDeviceInterceptor(DeviceBindingService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String method = req.getMethod();
        if ("OPTIONS".equals(method)) return true;
        String path = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) path = path.substring(ctx.length());
        Matcher m;
        if ("POST".equals(method) && (m = OPEN_SHIFT.matcher(path)).matches()) {
            service.assertRegister(Long.valueOf(m.group(1)));
        } else if ("POST".equals(method) && (m = CASH_MOVE.matcher(path)).matches()) {
            service.assertSession(Long.valueOf(m.group(1)));
        } else if ((m = ENTER.matcher(path)).matches()
                && (("POST".equals(method) && m.group(2) == null) || ("GET".equals(method) && m.group(2) != null))) {
            service.assertSession(Long.valueOf(m.group(1)));
        } else if ((m = CS_ORDERS.matcher(path)).matches()) {
            service.assertCashierSession(Long.valueOf(m.group(1)));
        } else if (!"GET".equals(method) && !"HEAD".equals(method) && (m = ORDER_OP.matcher(path)).matches()) {
            service.assertOrder(Long.valueOf(m.group(1)));
        }
        return true;
    }
}
