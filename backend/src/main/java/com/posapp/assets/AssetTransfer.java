package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One move of an asset to another branch and/or location. Branch ids are plain ids (null = head office); names are snapshots. */
@Getter
@Setter
@Entity
public class AssetTransfer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FixedAsset asset;

    @Column(nullable = false)
    private LocalDate transferDate;

    @Column(name = "from_branch_id")
    private Long fromBranchId;
    private String fromBranchName;

    @Column(name = "to_branch_id")
    private Long toBranchId;
    private String toBranchName;

    private String fromLocation;
    private String toLocation;

    @Column(columnDefinition = "TEXT")
    private String reason;
}
