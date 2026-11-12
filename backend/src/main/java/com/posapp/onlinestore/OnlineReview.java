package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A customer's review of a product they bought on a completed order. It shows on the shop only after staff approve it. */
@Getter
@Setter
@Entity
public class OnlineReview extends BaseEntity {
    private Long onlineOrderId;
    private Long productId;
    private String productName;
    private Long shopCustomerId;
    private String reviewerName;
    private int rating;
    @Column(name = "review_text")
    private String reviewText;
    /** PENDING, APPROVED or HIDDEN. */
    private String status = "PENDING";
    private String moderatedBy;
    private Instant moderatedAt;
}
