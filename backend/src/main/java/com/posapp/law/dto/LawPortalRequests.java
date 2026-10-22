package com.posapp.law.dto;

/** What the portal screens send. */
public final class LawPortalRequests {

    private LawPortalRequests() {
    }

    public record LoginRequest(String email, String password) {
    }

    public record AcceptRequest(String token, String password) {
    }

    public record PasswordRequest(String currentPassword, String newPassword) {
    }

    public record MessageRequest(String body) {
    }

    public record SettingsRequest(Boolean enabled, Boolean showDeadlines, Boolean showDocuments, Boolean showInvoices, Boolean showTrust, Boolean allowUpload, Boolean allowMessages) {
    }

    public record UpdateRequest(String text) {
    }

    public record InviteRequest(String name, String email) {
    }

    public record ActiveRequest(Boolean active) {
    }

    public record VisibleRequest(Boolean visible) {
    }
}
