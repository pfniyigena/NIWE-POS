package com.posapp.law.dto;

import com.posapp.law.DeadlineStatus;
import com.posapp.law.DeadlineType;
import com.posapp.law.DocCategory;
import com.posapp.law.DocReview;
import com.posapp.law.MatterStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** What the client portal and the staff's portal screens send back. The client side never carries internal notes, rates, team notes or other clients' data. */
public final class LawPortalDtos {

    private LawPortalDtos() {
    }

    // ---- client side

    public record SessionDto(String token, int expiresInMinutes, String name, String email) {
    }

    public record InviteInfoDto(String name, String email, String firmName) {
    }

    public record MatterRowDto(Long id, String number, String title, MatterStatus status, String practiceArea, String leadName, LocalDate openedDate, long unreadMessages,
                               LocalDate nextDeadline) {
    }

    public record MeDto(String name, String email, String clientName, String firmName, String firmPhone, String firmEmail, List<MatterRowDto> matters) {
    }

    public record FeaturesDto(boolean deadlines, boolean documents, boolean invoices, boolean trust, boolean upload, boolean messages) {
    }

    public record DeadlineDto(String title, DeadlineType type, LocalDate dueDate, LocalTime dueTime, DeadlineStatus status) {
    }

    public record DocumentDto(Long id, String name, DocCategory category, String fileName, long sizeBytes, int version, Instant updatedAt) {
    }

    public record UploadDto(Long id, String name, String fileName, long sizeBytes, DocReview reviewStatus, String reviewNote, Instant createdAt) {
    }

    public record InvoiceDto(String number, Instant date, LocalDate dueDate, BigDecimal total, BigDecimal paid, BigDecimal outstanding) {
    }

    public record TrustEntryDto(LocalDate date, String kind, BigDecimal amount, String reference, String purpose, BigDecimal balanceAfter) {
    }

    public record TrustDto(BigDecimal balance, List<TrustEntryDto> entries) {
    }

    public record MessageDto(Long id, boolean fromClient, String senderName, String body, Instant createdAt) {
    }

    public record MatterDto(Long id, String number, String title, MatterStatus status, String practiceArea, String jurisdiction, String court, String courtCaseNumber, String leadName,
                            LocalDate openedDate, LocalDate closedDate, String clientUpdate, Instant clientUpdateAt, FeaturesDto features, List<DeadlineDto> deadlines,
                            List<DocumentDto> documents, List<UploadDto> uploads, List<InvoiceDto> invoices, TrustDto trust, List<MessageDto> messages) {
    }

    // ---- staff side

    public record AccountDto(Long id, String name, String email, boolean active, boolean accepted, Instant inviteExpiresAt, Instant lastLoginAt, boolean locked) {
    }

    public record AdminDeadlineDto(Long id, String title, DeadlineType type, LocalDate dueDate, DeadlineStatus status, boolean visibleToClient) {
    }

    public record AdminMessageDto(Long id, boolean fromClient, String senderName, String body, Instant createdAt, boolean read) {
    }

    public record AdminDto(Long matterId, String number, String title, String clientName, boolean firmPortalEnabled, boolean enabled, boolean showDeadlines, boolean showDocuments,
                           boolean showInvoices, boolean showTrust, boolean allowUpload, boolean allowMessages, String clientUpdate, Instant clientUpdateAt, String clientUpdateBy,
                           boolean canManage, boolean canWork, List<AccountDto> accounts, List<AdminDeadlineDto> deadlines, List<AdminMessageDto> messages, long unread,
                           long pendingUploads) {
    }

    public record InviteResultDto(AccountDto account, String link, boolean emailSent, String emailNote) {
    }
}
