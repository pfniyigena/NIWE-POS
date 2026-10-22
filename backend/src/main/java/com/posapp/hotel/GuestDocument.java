package com.posapp.hotel;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A scan or photo of a guest's passport, ID, licence or visa. The bytes are in {@link GuestDocumentBlob}, so lists never load them. */
@Getter
@Setter
@Entity
public class GuestDocument extends BaseEntity {

    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private GuestDocType docType = GuestDocType.OTHER;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "doc_number", length = 120)
    private String docNumber;

    private LocalDate expiryDate;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    private String uploadedByName;
}
