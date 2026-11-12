package com.posapp.onlinestore;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A product put on the shop. Only products with a listing that is switched on are ever shown or sold online. The photo is the product's own picture. */
@Getter
@Setter
@Entity
public class OnlineListing extends BaseEntity {
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;
    private boolean listed = true;
    private String webTitle;
    @Column(columnDefinition = "TEXT")
    private String webDescription;
    /** Empty = the product's normal selling price. */
    private BigDecimal onlinePrice;
    private int sortOrder = 0;
    /** Staff's picks for the rows of the shop's front page. */
    private boolean featured = false;
    private boolean bestSeller = false;
    private boolean specialOffer = false;
    private boolean newArrival = false;
}
