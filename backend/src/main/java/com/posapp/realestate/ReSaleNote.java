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

/** One call, promise to pay, note, reminder or statement in the collection history of a unit sale. */
@Getter
@Setter
@Entity
@Table(name = "re_sale_note", indexes = @Index(columnList = "sale_id"))
public class ReSaleNote extends BaseEntity {

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleNoteKind kind = SaleNoteKind.NOTE;

    @Column(nullable = false, length = 2000)
    private String text;

    /** For a promise: the day the buyer said the money would come. */
    private LocalDate promiseDate;

    private Long createdByUserId;
    private String createdByName;
}
