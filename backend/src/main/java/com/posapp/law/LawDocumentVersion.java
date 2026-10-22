package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One saved version of a document. Never changed after it is saved; the bytes are in {@link LawDocumentBlob}. */
@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "document_id"))
public class LawDocumentVersion extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 64)
    private String sha256;

    @Column(length = 500)
    private String note;

    private Long uploadedByUserId;
    private String uploadedByName;
}
