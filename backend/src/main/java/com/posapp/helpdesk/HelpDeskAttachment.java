package com.posapp.helpdesk;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A screenshot or file on a ticket, kept in the database so a backup holds it. */
@Getter
@Setter
@Entity
public class HelpDeskAttachment extends BaseEntity {
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    private String uploadedByName;
    @Column(nullable = false)
    private boolean fromRequester;
    /** Staff files are internal unless this is set; the requester's own files are always visible to them. */
    @Column(nullable = false)
    private boolean visibleToRequester;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
