package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One advert of an advertiser: a picture that links to the advertiser's address, shown in one or more spaces of the shop ({@code placements} is a
 * comma-separated list of HOME_TOP, PRODUCT, ORDER) between two optional dates. {@code fee} is what the advertiser is charged (a note and a paid
 * flag help the shop keep track); nothing is invoiced automatically.
 */
@Getter
@Setter
@Entity
public class OnlineAd extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertiser_id", nullable = false)
    private OnlineAdvertiser advertiser;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 60)
    private String placements;

    @Column(nullable = false, length = 500)
    private String linkUrl;

    @Column(length = 160)
    private String altText;

    @Column(nullable = false, length = 200)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    private LocalDate startsOn;

    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(precision = 14, scale = 2)
    private BigDecimal fee;

    @Column(length = 160)
    private String feeNote;

    @Column(nullable = false)
    private boolean feePaid;

    @Column(nullable = false)
    private int sortOrder;
}
