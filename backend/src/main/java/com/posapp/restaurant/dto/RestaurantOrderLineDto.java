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
        String station,
        /** Price x quantity before any discount. */
        BigDecimal grossTotal,
        /** Everything taken off this line: its own discount plus its share of the order discount. */
        BigDecimal discountTotal,
        BigDecimal discountPercent,
        String discountReason,
        /** What the guest picked for this line (sizes, extras...). */
        java.util.List<LineOptionDto> options,
        /** The options' price for one unit, already included in {@code grossTotal}. */
        BigDecimal optionsPrice,
        int course,
        /** True while the line waits for its course to be fired. */
        boolean held,
        String allergens,
        /** Allergens of this line that the guests at the order told us they cannot have (comma separated), else null. */
        String allergyConflict,
        /** The menu item this line was ordered from (null on lines taken before options existed). */
        Long menuItemId,
        Integer seat,
        boolean comped,
        /** How many times the dish was sent back to the kitchen. */
        int remakeCount
) {
    public record LineOptionDto(Long optionId, String groupName, String name, BigDecimal priceDelta) {
    }

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
                line.getStation(),
                RestaurantOrderCalculator.lineGross(line),
                RestaurantOrderCalculator.lineDiscount(line),
                line.getDiscountPercent(),
                line.getDiscountReason(),
                line.getOptions().stream().map(o -> new LineOptionDto(o.getOptionId(), o.getGroupName(), o.getOptionName(), o.getPriceDelta())).toList(),
                line.getOptionsPrice() == null ? BigDecimal.ZERO : line.getOptionsPrice(),
                line.getCourse(),
                line.getKitchenStatus() == com.posapp.restaurant.KitchenStatus.PENDING && line.getOrder() != null && line.getCourse() > line.getOrder().getFiredCourse(),
                line.getAllergens(),
                line.getOrder() == null ? null : conflict(line),
                line.getMenuItemId(),
                line.getSeat(),
                line.isComped(),
                line.getRemakeCount()
        );
    }

    private static String conflict(RestaurantOrderLine line) {
        var hits = com.posapp.restaurant.RestaurantAllergens.overlap(line.getAllergens(), line.getOrder().getGuestAllergens());
        return hits.isEmpty() ? null : String.join(",", hits);
    }
}
