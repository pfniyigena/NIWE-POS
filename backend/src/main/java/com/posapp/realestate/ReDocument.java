package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A file attached to a property, project, unit, deal, sale, lead or valuation. The bytes are in {@link ReDocumentBlob}, so lists never load them. */
@Getter
@Setter
@Entity
@Table(name = "re_document", indexes = {@Index(columnList = "subject_type, subject_id"), @Index(columnList = "expiry_date")})
public class ReDocument extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private DocSubject subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReDocCategory category = ReDocCategory.OTHER;

    @Column(length = 1000)
    private String description;

    /** For things that lapse (a permit, a lease, an insurance cover). */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    private String uploadedByName;
}
