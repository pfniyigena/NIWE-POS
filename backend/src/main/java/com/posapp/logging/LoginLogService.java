package com.posapp.logging;

import com.posapp.common.PageResponse;
import com.posapp.register.ClientDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * v1.14.0: the sign-in log. Recording never makes a sign-in fail. A name that does not look like a user name (spaces, very long) is stored as
 * "(not a user name)" because people sometimes type their password into the name box.
 */
@Service
public class LoginLogService {
    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._@+-]{1,60}");
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59Z");

    private final LoginEventRepository repository;
    private final Set<String> trustedProxies;

    public LoginLogService(LoginEventRepository repository, @Value("${app.device-binding.trusted-proxies:127.0.0.1,::1}") String trusted) {
        this.repository = repository;
        this.trustedProxies = ClientDevice.parseList(trusted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(HttpServletRequest req, String attemptedName, Long userId, boolean success, String reason) {
        try {
            String name = attemptedName == null ? null : attemptedName.trim();
            if (name != null && !NAME.matcher(name).matches()) name = "(not a user name)";
            LoginEvent e = new LoginEvent();
            e.setUsername(name);
            e.setUserId(userId);
            e.setSuccess(success);
            e.setReason(reason);
            e.setIp(ClientDevice.ip(req, trustedProxies));
            e.setDeviceId(ClientDevice.deviceId(req));
            String ua = req == null ? null : req.getHeader("User-Agent");
            e.setUserAgent(ua == null ? null : ua.length() > 255 ? ua.substring(0, 255) : ua);
            repository.save(e);
            if (success) log.info("Sign-in: {} from {}", name, e.getIp());
            else log.warn("Failed sign-in: {} from {} ({})", name, e.getIp(), reason);
        } catch (RuntimeException ex) {
            log.warn("Could not record a sign-in: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginEventDto> search(String q, String outcome, Instant from, Instant to, int page, int size) {
        String oc = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!oc.equals("OK") && !oc.equals("FAILED")) oc = "";
        return PageResponse.from(repository.search(q == null ? "" : q.trim(), oc, from == null ? EARLIEST : from, to == null ? LATEST : to,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)))).map(LoginEventDto::from));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return Map.of("okLast24h", repository.countBySuccessAndCreatedAtGreaterThanEqual(true, since),
                "failedLast24h", repository.countBySuccessAndCreatedAtGreaterThanEqual(false, since),
                "failingAddressesLast24h", repository.countFailingAddressesSince(since));
    }
}
