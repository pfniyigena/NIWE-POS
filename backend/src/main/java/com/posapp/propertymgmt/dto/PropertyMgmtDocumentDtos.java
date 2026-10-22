package com.posapp.propertymgmt.dto;

import com.posapp.propertymgmt.PmDocCategory;
import com.posapp.propertymgmt.PmDocSubject;

import java.time.Instant;
import java.time.LocalDate;

/** What the document screens read and send. */
public final class PropertyMgmtDocumentDtos {

    private PropertyMgmtDocumentDtos() {
    }

    /** expiryState: NONE (no expiry date), OK, SOON (within 30 days) or EXPIRED. */
    public record DocumentDto(Long id, PmDocSubject subjectType, Long subjectId, String subjectLabel, String name, PmDocCategory category, String description, LocalDate expiryDate,
                              String expiryState, Long daysToExpiry, String fileName, String contentType, long sizeBytes, String uploadedByName, Instant createdAt) {
    }

    /** Edits a document's details (not the file): anything left out stays as it was; a blank text clears it. clearExpiry removes the expiry date. */
    public record DocumentRequest(String name, PmDocCategory category, String description, LocalDate expiryDate, Boolean clearExpiry) {
    }
}
