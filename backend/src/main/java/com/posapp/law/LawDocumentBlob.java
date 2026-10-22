package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The bytes of one document version, in a table of their own so lists of documents and versions never load files. */
@Getter
@Setter
@Entity
public class LawDocumentBlob extends BaseEntity {

    @Column(name = "version_id", nullable = false, unique = true)
    private Long versionId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
