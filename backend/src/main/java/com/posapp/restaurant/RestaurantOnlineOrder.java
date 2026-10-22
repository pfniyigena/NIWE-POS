package com.posapp.restaurant;

import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order a guest placed from the QR menu or the ordering page. It waits here, away from the floor, until staff accept it; only then does it
 * become a real {@link RestaurantOrder} that goes to the kitchen. Prices shown to the guest are an estimate made when the order was placed.
 */
@Getter
@Setter
@Entity
public class RestaurantOnlineOrder extends BaseEntity {

    /** What the guest quotes and tracks the order with. */
    private String code;
    /** TABLE, TAKEAWAY or DELIVERY. */
    private String mode;
    /** PENDING, ACCEPTED, REJECTED, CANCELLED (by the guest) or EXPIRED (nobody answered in time). */
    private String status = "PENDING";
    /** QR or WEB. */
    private String source = "WEB";
    private Long tableId;
    private String tableName;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private Long zoneId;
    private String zoneName;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private String notes;
    private BigDecimal estimatedSubtotal = BigDecimal.ZERO;
    private BigDecimal estimatedTotal = BigDecimal.ZERO;
    private Integer etaMinutes;
    private Instant decidedAt;
    private String decidedBy;
    private String rejectReason;
    private Long restaurantOrderId;
    /** The lines this request added to that order (comma separated ids), so the tracker can follow just these dishes at a shared table. */
    private String restaurantLineIds;

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RestaurantOnlineOrderLine> lines = new ArrayList<>();
}
