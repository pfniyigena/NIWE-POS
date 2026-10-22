package com.posapp.hotel.dto;

import java.time.LocalDate;

public final class GuestRequests {

    private GuestRequests() {
    }

    /** Merge the guest {@code duplicateId} into the guest in the path; everything of theirs moves over, then they are removed. */
    public record MergeRequest(Long duplicateId) {
    }

    public record GuestDocumentRequest(String docType, String name, String docNumber, LocalDate expiryDate) {
    }
}
