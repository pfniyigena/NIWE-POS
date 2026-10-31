package com.posapp.assets;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The one row of Assets rules: how early expiry alerts show, what "fully depreciated soon" means, and the disposal approval limit. */
@Getter
@Setter
@Entity
public class AssetSettings extends BaseEntity {
    /** Warranty and insurance count as "expiring" this many days ahead. */
    @Column(nullable = false)
    private int alertDays = 30;
    /** An asset is "fully depreciated soon" when it will reach salvage value within this many months. */
    @Column(nullable = false)
    private int soonMonths = 3;
    /** Disposing an asset with a book value above this needs the "Approve large asset disposals" permission (0 = no limit). */
    @Column(nullable = false)
    private BigDecimal disposalApprovalLimit = BigDecimal.ZERO;
}
