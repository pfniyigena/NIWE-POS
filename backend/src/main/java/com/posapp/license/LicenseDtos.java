package com.posapp.license;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LicenseDtos {

    private LicenseDtos() {
    }

    public record HistoryRow(String licenseId, long serial, String customer, LocalDate issuedAt, LocalDate expiresAt, Instant installedAt, String installedBy,
                             boolean active, int modules) {
    }

    /** What the Settings > Modules screen shows about the license. {@code problem} is set when a license is installed but cannot be believed. */
    public record LicenseStatus(String installationId, boolean keyConfigured, boolean hasLicense, boolean valid, String problem, String customer, String licenseId,
                                Long serial, LocalDate issuedAt, LocalDate expiresAt, boolean expired, Long daysLeft, boolean expiringSoon, List<String> modules,
                                boolean devAllModules, List<HistoryRow> history) {
    }

    public record InstallRequest(String license) {
    }

    public record RequestModuleRequest(String module, String note) {
    }

    public record ModuleRequestDto(Long id, String module, String moduleName, String note, String status, String requestedBy, Instant requestedAt, Instant closedAt,
                                   String closedNote, boolean emailed) {
    }

    /** {@code text} is what to send to NIWE by hand when {@code emailSent} is false. */
    public record RequestResult(ModuleRequestDto request, boolean emailSent, String emailNote, String text) {
    }
}
