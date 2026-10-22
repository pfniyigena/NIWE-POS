package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/** An entry in the matter diary: a file note, a call, a meeting, an e-mail, a court event. */
@Getter
@Setter
@Entity
public class LawMatterNote extends BaseEntity {

    @Column(nullable = false)
    private Long matterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteKind kind = NoteKind.NOTE;

    @Column(nullable = false, length = 4000)
    private String text;

    private Long createdByUserId;
    private String createdByName;
}
