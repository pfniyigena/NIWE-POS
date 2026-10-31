package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** One line of an asset's history. Never edited or deleted. */
@Getter
@Setter
@Entity
public class AssetEvent extends BaseEntity {
    @Column(name = "asset_id", nullable = false)
    private Long assetId;
    @Column(nullable = false, length = 30)
    private String kind;
    @Column(length = 1000)
    private String detail;
    private String createdByName;
}
