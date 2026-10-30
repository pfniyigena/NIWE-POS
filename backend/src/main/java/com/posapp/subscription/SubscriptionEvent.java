package com.posapp.subscription;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** One line of a subscription's history. Never edited or deleted. */
@Getter
@Setter
@Entity
public class SubscriptionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionEventKind kind;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private String createdByName;
}
