package com.posapp.realestate;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A share of the deal's commission that goes to one person: the selling agent, the listing agent, a referrer, another agency. */
@Getter
@Setter
@Entity
@Table(name = "re_deal_commission", indexes = {@Index(columnList = "deal_id")})
public class ReDealCommission extends BaseEntity {

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    /** What they did: Selling agent, Listing agent, Referral, Co-broker... */
    @Column(nullable = false, length = 60)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayeeKind payeeKind = PayeeKind.STAFF;

    private Long payeeUserId;

    @Column(nullable = false, length = 200)
    private String payeeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionBasis basis = CommissionBasis.SHARE_PERCENT;

    /** Percent of the deal's commission (SHARE_PERCENT only). */
    @Column(precision = 7, scale = 2)
    private BigDecimal sharePercent;

    /** The amount, when FIXED. */
    @Column(precision = 18, scale = 2)
    private BigDecimal fixedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommissionStatus status = CommissionStatus.PLANNED;

    /** The expense that was logged when the commission was posted to the accounts. */
    private Long expenseId;

    @Column(length = 300)
    private String note;
}
