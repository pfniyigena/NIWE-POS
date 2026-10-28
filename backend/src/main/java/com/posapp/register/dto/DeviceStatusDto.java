package com.posapp.register.dto;

import java.time.Instant;

/**
 * What the till screen needs to know about the device it is running on: is this till tied to a
 * device, and if so is it this one. {@code allowed} is the answer the server also enforces.
 */
public record DeviceStatusDto(Long registerId, String registerName, boolean bound, boolean allowed, boolean thisDevice,
                              boolean ipChecked, boolean ipMatches, String deviceName, String boundIp, Instant boundAt,
                              String boundBy, String yourIp, String message) {
}
