package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A photo, invoice, warranty card or other file kept with an asset. */
@Getter
@Setter
@Entity
public class AssetAttachment extends BaseEntity {
    @Column(name = "asset_id", nullable = false)
    private Long assetId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    private String uploadedByName;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
