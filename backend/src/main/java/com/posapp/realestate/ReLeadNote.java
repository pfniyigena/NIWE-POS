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

/** One call, meeting or note in a lead's history. */
@Getter
@Setter
@Entity
@Table(name = "re_lead_note", indexes = @Index(columnList = "lead_id"))
public class ReLeadNote extends BaseEntity {

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteKind kind = NoteKind.NOTE;

    @Column(nullable = false, length = 2000)
    private String text;

    private Long createdByUserId;
    private String createdByName;
}
