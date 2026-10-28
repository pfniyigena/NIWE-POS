package com.posapp.register;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Who is calling: the id the browser sends in X-Device-Id and the address the request really came from. */
public final class ClientDevice {
    public static final String HEADER = "X-Device-Id";

    private ClientDevice() {
    }

    public static String deviceId(HttpServletRequest req) {
        if (req == null) return null;
        String v = req.getHeader(HEADER);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() || v.length() > 64 ? null : v;
    }

    /**
     * The caller's address. X-Forwarded-For is believed only when the request itself arrived from
     * one of {@code trustedProxies} (a proxy or web server on the same machine); otherwise a client
     * could simply claim any address.
     */
    public static String ip(HttpServletRequest req, Set<String> trustedProxies) {
        if (req == null) return null;
        String remote = normalise(req.getRemoteAddr());
        if (trustedProxies.contains(remote)) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",")[0].trim();
                if (!first.isEmpty()) return normalise(first);
            }
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return normalise(real.trim());
        }
        return remote;
    }

    public static String normalise(String ip) {
        if (ip == null) return null;
        String v = ip.trim();
        if (v.startsWith("::ffff:")) v = v.substring(7);
        // the same machine can appear as 127.0.0.1 or ::1 depending on how the browser reached the server: treat them as one address
        if (v.equals("0:0:0:0:0:0:0:1") || v.equals("::1")) v = "127.0.0.1";
        return v;
    }

    public static Set<String> parseList(String csv) {
        if (csv == null) return Set.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(ClientDevice::normalise).collect(Collectors.toSet());
    }
}
