package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A document on a matter. The file itself lives in its versions; this row is the document's name, flags and lock. */
@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "matter_id"))
public class LawDocument extends BaseEntity {

    @Column(name = "matter_id", nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocCategory category = DocCategory.OTHER;

    @Column(length = 1000)
    private String description;

    /** Only the matter's team and admins can see it (even Operations viewers and managers cannot). */
    @Column(nullable = false)
    private boolean confidential = false;

    /** The client may see it (used by the client portal). */
    @Column(nullable = false)
    private boolean sharedWithClient = false;

    /** Hidden from the list (not deleted; every version is kept). */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(nullable = false)
    private int currentVersion = 1;

    /** Details of the current version, kept here so the list needs no join. */
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes = 0;

    private Long checkedOutByUserId;
    private String checkedOutByName;
    private Instant checkedOutAt;

    private Long templateId;

    private Long createdByUserId;
    private String createdByName;

    /** Sent by the client through the portal. */
    @Column(nullable = false)
    private boolean fromClient = false;

    /** For a document from the client: waiting for a lawyer, accepted into the matter, or rejected. Empty for the firm's own documents. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DocReview reviewStatus;

    @Column(length = 500)
    private String reviewNote;

    private String reviewedByName;
}
