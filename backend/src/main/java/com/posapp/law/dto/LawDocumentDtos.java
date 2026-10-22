package com.posapp.law.dto;

import com.posapp.law.DocCategory;
import com.posapp.law.DocReview;
import com.posapp.law.TemplateKind;

import java.time.Instant;
import java.util.List;

/** What the document screens return. */
public final class LawDocumentDtos {

    private LawDocumentDtos() {
    }

    public record DocDto(Long id, String name, DocCategory category, String description, boolean confidential, boolean sharedWithClient, boolean archived,
                         int currentVersion, String fileName, String contentType, long sizeBytes, Long checkedOutByUserId, String checkedOutByName,
                         Instant checkedOutAt, boolean checkedOutByMe, String createdByName, Instant createdAt, Instant updatedAt, Long templateId,
                         boolean canEdit, boolean canLock, boolean canRelease, boolean canManage, boolean fromClient, DocReview reviewStatus, String reviewNote) {
    }

    public record DocListDto(Long matterId, String number, String title, List<DocDto> documents, long totalBytes, boolean canUpload, boolean canManage) {
    }

    public record VersionDto(Long id, int versionNo, String fileName, String contentType, long sizeBytes, String sha256, String note, String uploadedByName,
                             Instant createdAt) {
    }

    public record VersionsDto(DocDto document, List<VersionDto> versions) {
    }

    public record TemplateDto(Long id, String name, DocCategory category, String description, TemplateKind kind, String body, String fileName, boolean active,
                              String createdByName, Instant updatedAt, long uses) {
    }

    public record PlaceholderDto(String key, String description) {
    }

    public record GenerateResult(DocDto document, List<String> unresolved) {
    }
}
