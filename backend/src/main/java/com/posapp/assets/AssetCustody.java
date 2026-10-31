package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One spell of an asset being held by one person: checked out on a date, checked in later (open while {@code checkedInOn} is null). */
@Getter
@Setter
@Entity
public class AssetCustody extends BaseEntity {
    @Column(name = "asset_id", nullable = false)
    private Long assetId;
    @Column(name = "custodian_user_id")
    private Long custodianUserId;
    @Column(nullable = false)
    private String custodianName;
    @Column(nullable = false)
    private LocalDate checkedOutOn;
    private LocalDate checkedInOn;
    private String outCondition;
    private String inCondition;
    @Column(length = 1000)
    private String note;
    private String createdByName;
}
