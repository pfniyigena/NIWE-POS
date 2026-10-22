package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A letter or contract with {{placeholders}} that are filled in from a matter. Typed text, or an uploaded Word file. */
@Getter
@Setter
@Entity
public class LawDocTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocCategory category = DocCategory.CORRESPONDENCE;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TemplateKind kind = TemplateKind.TEXT;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String fileName;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] data;

    @Column(nullable = false)
    private boolean active = true;

    private String createdByName;
}
