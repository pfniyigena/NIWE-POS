package com.posapp.propertymgmt;

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

/** A file attached to a property, unit, owner, tenant, lease or repair request. The bytes are in {@link PmDocumentBlob}, so lists never load them. */
@Getter
@Setter
@Entity
@Table(name = "pm_document", indexes = {@Index(columnList = "subject_type, subject_id"), @Index(columnList = "expiry_date")})
public class PmDocument extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private PmDocSubject subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PmDocCategory category = PmDocCategory.OTHER;

    @Column(length = 1000)
    private String description;

    /** For things that lapse: an ID card, an insurance cover, a permit, a signed notice period. */
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
