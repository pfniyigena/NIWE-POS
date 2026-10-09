package com.posapp.restaurant.dto;

import com.posapp.restaurant.RestaurantOrderCalculator;
import com.posapp.restaurant.RestaurantOrderLine;

import java.math.BigDecimal;

public record RestaurantOrderLineDto(
        Long id,
        Long productId,
        String description,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        BigDecimal taxRatePercent,
        BigDecimal lineTax,
        /**
         * What this line actually contributes to {@code RestaurantOrderDto.total} — summing this
         * across every line always equals the order's total to the penny, regardless of whether
         * each product's tax is inclusive or exclusive (see TaxRate.inclusive's javadoc): for an
         * exclusive-tax line that's {@code lineTotal + lineTax} (tax adds on top); for an
         * inclusive-tax line {@code lineTotal} already has the tax folded in, so it's just {@code
         * lineTotal}. Exposed as its own field, rather than making the frontend reconstruct it from
         * lineTotal/lineTax/inclusive itself, for the "split by item" bill-splitting UI (Fourier:
         * "Include split & merge bills" — see the ChatGPT-sourced spec's "Split by Item" option) —
         * summing this field for the lines assigned to one guest is exactly that guest's fair share.
         */
        BigDecimal lineGrandTotal,
        String notes,
        String kitchenStatus,
        /** Snapshotted kitchen prep area (Fourier: "Kitchen stations") — see RestaurantOrderLine.station's javadoc. Null if the menu item had no station assigned. */
        String station
) {
    public static RestaurantOrderLineDto from(RestaurantOrderLine line) {
        return new RestaurantOrderLineDto(
                line.getId(),
                line.getProduct() != null ? line.getProduct().getId() : null,
                line.getDescription(),
                line.getUnitPrice(),
                line.getQuantity(),
                RestaurantOrderCalculator.lineTotal(line),
                line.getTaxRatePercent(),
                RestaurantOrderCalculator.lineTax(line),
                RestaurantOrderCalculator.lineNetTotal(line).add(RestaurantOrderCalculator.lineTax(line)),
                line.getNotes(),
                line.getKitchenStatus().name(),
                line.getStation()
        );
    }
}
