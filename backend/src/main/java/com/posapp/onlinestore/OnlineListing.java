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
    /** The specifications table of the product page: a JSON list of {label, value}. */
    @Column(columnDefinition = "TEXT")
    private String specs;
    /** A variant of a template on the shop: staff switched this option off (it is not offered, but the rest of the product stays). */
    @Column(name = "variant_off", nullable = false)
    private boolean variantOff = false;
    /** v1.66.0: units kept back for this product only (empty = the shop-wide buffer). */
    @Column(name = "stock_buffer")
    private Integer stockBuffer;
    /** v1.69.0: the price the ERP gives this product (price list, promotions) when staff set no online price; kept up to date by OnlineOffers. Empty = the normal price. */
    @Column(name = "offer_price")
    private BigDecimal offerPrice;
    /** What the offer price comes from (the promotion or price list name), for staff. */
    @Column(name = "offer_note")
    private String offerNote;
    /** v1.69.0: sold by weight or length: the unit shown ("kg", "m"); the customer may order decimal quantities. Empty = sold by the piece. */
    @Column(name = "measure_unit")
    private String measureUnit;
}
