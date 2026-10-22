package com.posapp.pricing;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One product's price on a list, from {@code minQuantity} upward (1 = the normal price on this list; 10 = the price when buying ten or more). */
@Entity
@Table(name = "price_list_entry")
public class PriceListEntry extends BaseEntity {

    @Column(name = "price_list_id", nullable = false)
    private Long priceListId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "min_quantity", nullable = false)
    private int minQuantity = 1;
    @Column(nullable = false)
    private BigDecimal price;

    public Long getPriceListId() { return priceListId; }
    public void setPriceListId(Long priceListId) { this.priceListId = priceListId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getMinQuantity() { return minQuantity; }
    public void setMinQuantity(int minQuantity) { this.minQuantity = minQuantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
