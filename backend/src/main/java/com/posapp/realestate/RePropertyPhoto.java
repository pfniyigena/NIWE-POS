package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A photo of a property. The picture itself is in {@link RePhotoBlob}, so lists never load the bytes. */
@Getter
@Setter
@Entity
@Table(name = "re_property_photo", indexes = @Index(columnList = "property_id"))
public class RePropertyPhoto extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    private boolean cover = false;

    @Column(nullable = false)
    private int sortOrder = 0;
}
