package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A business that pays the shop to show its adverts (see {@link OnlineAd}). */
@Getter
@Setter
@Entity
public class OnlineAdvertiser extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String contactName;

    @Column(length = 160)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
