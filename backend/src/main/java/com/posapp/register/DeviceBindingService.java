package com.posapp.register;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.order.OrderRepository;
import com.posapp.order.SaleOrder;
import com.posapp.register.dto.BindDeviceRequest;
import com.posapp.register.dto.DeviceStatusDto;
import com.posapp.register.dto.RegisterDto;
import com.posapp.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Set;

/**
 * Fourier: "map POS Terminal (till) with specific Device means the Terminal can not be opened to
 * more than one device and it is known."
 *
 * A till is either unbound (any device, as always) or bound to one device: the id its browser
 * keeps (X-Device-Id) and, unless the admin chose otherwise, the address that PC had when the
 * admin pressed "Bind to this device". Every till operation then has to come from that device and
 * address or the server refuses it. An admin lifts the binding by unbinding (there is no bypass).
 *
 * This is an operational control, not hard security: the id lives in the browser, so someone who
 * can copy that browser's storage can pose as the till PC. It stops the everyday case (the same
 * till opened on a second PC, a phone, another tab elsewhere) and records who bound what.
 */
@Service
public class DeviceBindingService {
    private static final Logger log = LoggerFactory.getLogger(DeviceBindingService.class);

    private final RegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final CashierSessionRepository cashierSessionRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;
    private final Set<String> trustedProxies;

    public DeviceBindingService(RegisterRepository registerRepository, RegisterSessionRepository sessionRepository,
                                CashierSessionRepository cashierSessionRepository, OrderRepository orderRepository,
                                AuditLogService auditLogService,
                                @Value("${app.device-binding.trusted-proxies:127.0.0.1,::1}") String trusted) {
        this.registerRepository = registerRepository;
        this.sessionRepository = sessionRepository;
        this.cashierSessionRepository = cashierSessionRepository;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
        this.trustedProxies = ClientDevice.parseList(trusted);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes a = RequestContextHolder.getRequestAttributes();
        return a instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    private Register register(Long id) {
        return registerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Register not found: " + id));
    }

    // ---- admin actions ----

    @Transactional
    public RegisterDto bind(Long registerId, BindDeviceRequest body, AppUser admin) {
        HttpServletRequest req = currentRequest();
        String deviceId = ClientDevice.deviceId(req);
        if (deviceId == null) throw ApiException.badRequest("This browser did not identify itself. Reload the page and try again.");
        Register r = register(registerId);
        if (r.getDeviceId() != null && !r.getDeviceId().equals(deviceId)) {
            throw ApiException.conflict("'" + r.getName() + "' is already tied to " + label(r) + ". Unbind it first, then bind it to this device.");
        }
        registerRepository.findAll().stream()
                .filter(o -> deviceId.equals(o.getDeviceId()) && !o.getId().equals(registerId)).findFirst()
                .ifPresent(o -> {
                    throw ApiException.conflict("This device is already the till for '" + o.getName() + "'. Unbind that register first.");
                });
        boolean checkIp = body == null || body.getCheckIp() == null || body.getCheckIp();
        String ip = ClientDevice.ip(req, trustedProxies);
        String name = body != null && body.getDeviceName() != null && !body.getDeviceName().isBlank() ? body.getDeviceName().trim() : null;
        if (name != null && name.length() > 120) throw ApiException.badRequest("The device name is too long (120 characters at most)");
        if (name == null) name = r.getDeviceName() != null ? r.getDeviceName() : "Device " + deviceId.substring(0, Math.min(6, deviceId.length()));
        r.setDeviceId(deviceId);
        r.setDeviceName(name);
        r.setDeviceIp(checkIp ? ip : null);
        r.setDeviceBoundAt(Instant.now());
        r.setDeviceBoundBy(admin == null ? null : admin.getUsername());
        Register saved = registerRepository.save(r);
        auditLogService.record("REGISTER_DEVICE_BOUND", "Tied terminal " + saved.getName() + " to " + name + (checkIp ? " (" + ip + ")" : " (address not checked)"), "Register", saved.getId());
        return dto(saved);
    }

    @Transactional
    public RegisterDto unbind(Long registerId) {
        Register r = register(registerId);
        if (r.getDeviceId() == null) return dto(r);
        String was = label(r);
        r.setDeviceId(null);
        r.setDeviceName(null);
        r.setDeviceIp(null);
        r.setDeviceBoundAt(null);
        r.setDeviceBoundBy(null);
        Register saved = registerRepository.save(r);
        auditLogService.record("REGISTER_DEVICE_UNBOUND", "Released terminal " + saved.getName() + " from " + was, "Register", saved.getId());
        return dto(saved);
    }

    private RegisterDto dto(Register r) {
        Long open = sessionRepository.findByRegisterIdAndStatus(r.getId(), SessionStatus.OPEN).map(RegisterSession::getId).orElse(null);
        return RegisterDto.from(r, open);
    }

    private static String label(Register r) {
        return "'" + (r.getDeviceName() == null ? "another device" : r.getDeviceName()) + "'";
    }

    // ---- what the screen asks, and what the server enforces ----

    @Transactional(readOnly = true)
    public DeviceStatusDto status(Long registerId) {
        return evaluate(register(registerId));
    }

    DeviceStatusDto evaluate(Register r) {
        HttpServletRequest req = currentRequest();
        String deviceId = ClientDevice.deviceId(req);
        String ip = ClientDevice.ip(req, trustedProxies);
        if (r.getDeviceId() == null) {
            return new DeviceStatusDto(r.getId(), r.getName(), false, true, false, false, true, null, null, null, null, ip, null);
        }
        boolean sameDevice = r.getDeviceId().equals(deviceId);
        boolean ipChecked = r.getDeviceIp() != null;
        boolean ipOk = !ipChecked || r.getDeviceIp().equals(ip);
        boolean allowed = sameDevice && ipOk;
        String msg = null;
        if (!sameDevice) {
            msg = "The terminal '" + r.getName() + "' is tied to " + label(r) + ". It cannot be used on this device. An administrator can unbind it in Registers.";
        } else if (!ipOk) {
            msg = "The terminal '" + r.getName() + "' is tied to " + label(r) + " at " + r.getDeviceIp() + ", but this device is now at " + ip
                    + ". An administrator can unbind and bind it again in Registers.";
        }
        return new DeviceStatusDto(r.getId(), r.getName(), true, allowed, sameDevice, ipChecked, ipOk, r.getDeviceName(), r.getDeviceIp(),
                r.getDeviceBoundAt(), r.getDeviceBoundBy(), ip, msg);
    }

    private void enforce(Register r) {
        if (r == null || r.getDeviceId() == null) return;
        DeviceStatusDto s = evaluate(r);
        if (!s.allowed()) {
            log.warn("Till '{}' refused for device {} at {}", r.getName(), ClientDevice.deviceId(currentRequest()), s.yourIp());
            throw ApiException.forbidden(s.message());
        }
    }

    // ---- guards used by the interceptor and the offline sync ----

    @Transactional(readOnly = true)
    public void assertRegister(Long registerId) {
        registerRepository.findById(registerId).ifPresent(this::enforce);
    }

    @Transactional(readOnly = true)
    public void assertSession(Long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(s -> enforce(s.getRegister()));
    }

    @Transactional(readOnly = true)
    public void assertCashierSession(Long cashierSessionId) {
        cashierSessionRepository.findById(cashierSessionId)
                .ifPresent(cs -> enforce(cs.getRegisterSession() == null ? null : cs.getRegisterSession().getRegister()));
    }

    @Transactional(readOnly = true)
    public void assertOrder(Long orderId) {
        orderRepository.findById(orderId).map(SaleOrder::getCashierSession).ifPresent(cs ->
                enforce(cs.getRegisterSession() == null ? null : cs.getRegisterSession().getRegister()));
    }
}
