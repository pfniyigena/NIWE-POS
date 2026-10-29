package com.posapp.logging;

import java.time.Instant;

public record LoginEventDto(Long id, Instant at, String username, boolean success, String reason, String ip, String deviceId, String userAgent) {
    public static LoginEventDto from(LoginEvent e) {
        return new LoginEventDto(e.getId(), e.getCreatedAt(), e.getUsername(), e.isSuccess(), e.getReason(), e.getIp(), e.getDeviceId(), e.getUserAgent());
    }
}
