package com.posapp.helpdesk.dto;

import com.posapp.helpdesk.HelpDeskAttachment;

import java.time.Instant;

public record HelpDeskAttachmentDto(Long id, String fileName, String contentType, long sizeBytes, String uploadedBy, boolean fromRequester,
                                      boolean visibleToRequester, Instant createdAt, boolean image) {
    public static HelpDeskAttachmentDto from(HelpDeskAttachment a) {
        return new HelpDeskAttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getUploadedByName(), a.isFromRequester(),
                a.isVisibleToRequester() || a.isFromRequester(), a.getCreatedAt(), a.getContentType().startsWith("image/"));
    }
}
