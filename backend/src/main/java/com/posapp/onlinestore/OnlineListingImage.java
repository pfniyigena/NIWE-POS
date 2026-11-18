package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** An extra picture of a product on the shop (besides the product's own picture), uploaded by staff for the product page's gallery. */
@Getter
@Setter
@Entity
public class OnlineListingImage extends BaseEntity {
    @Column(nullable = false)
    private Long listingId;
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
