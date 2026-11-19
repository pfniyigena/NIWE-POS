package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A customer's rating (the stars) and optional review (the words) of a product they bought on a completed order. The stars count as soon as they are given
 * (unless staff took the rating out); the words show on the shop only after staff approve them, so the status below is about the words.
 */
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
    /** Whether the stars count in the averages (v1.46.0). */
    private boolean ratingCounted = true;
    /** For the words: PENDING (waiting for staff), APPROVED (shown, or nothing to check when there are no words) or HIDDEN. */
    private String status = "PENDING";
    private String moderatedBy;
    private Instant moderatedAt;
}
