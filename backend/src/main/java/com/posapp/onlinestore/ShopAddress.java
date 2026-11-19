package com.posapp.onlinestore;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A delivery address a signed-in shop customer saved (home, work, ...). At most one of a customer's addresses is the default. */
@Getter
@Setter
@Entity
public class ShopAddress extends BaseEntity {
    private Long shopCustomerId;
    private String label;
    /** The delivery area (zone) of the address, when the shop has areas; the zone may be removed later, so it is only a hint. */
    private Long zoneId;
    private String address;
    private boolean defaultAddress;
}
