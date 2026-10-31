package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
public class AssetVerificationLine extends BaseEntity {
    public static final String PENDING = "PENDING";
    public static final String FOUND = "FOUND";
    public static final String MISSING = "MISSING";
    public static final String DAMAGED = "DAMAGED";

    @Column(name = "verification_id", nullable = false)
    private Long verificationId;
    @Column(name = "asset_id", nullable = false)
    private Long assetId;
    @Column(nullable = false)
    private String assetCode;
    @Column(nullable = false)
    private String assetName;
    private String expectedLocation;
    private String expectedCustodian;
    @Column(nullable = false)
    private BigDecimal bookValue = BigDecimal.ZERO;
    @Column(nullable = false, length = 20)
    private String result = PENDING;
    private String foundLocation;
    @Column(length = 500)
    private String note;
    private Instant checkedAt;
    private String checkedByName;
    @Column(nullable = false)
    private boolean writtenOff = false;
}
