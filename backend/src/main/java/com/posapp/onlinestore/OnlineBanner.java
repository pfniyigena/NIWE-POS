package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A banner picture of the shop's front page, uploaded by staff. The pictures slide one after the other, lowest sort order first. */
@Getter
@Setter
@Entity
public class OnlineBanner extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
