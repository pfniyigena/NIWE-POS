package com.posapp.restaurant.dto;

import com.posapp.catalog.Product;
import com.posapp.catalog.dto.ProductDto;
import com.posapp.restaurant.MenuItem;

import java.math.BigDecimal;

public record MenuItemDto(
        Long id,
        Long categoryId,
        String categoryName,
        ProductDto product,
        int sortOrder,
        boolean active,
        BigDecimal priceOverride,
        /** priceOverride if set, else the product's own unitPrice — what the ordering screen should actually charge. */
        BigDecimal effectivePrice,
        /**
         * Whether this item's product has any bill of materials (Fourier: "Recipes / ingredient
         * consumption") — purely informational, badges the item in Menu Admin so staff know
         * checkout will consume ingredients instead of the item's own stock. Set up under Admin &gt;
         * Manufacturing &gt; Bill of materials; there is no separate Restaurant recipe screen.
         */
        boolean hasRecipe,
        /** Free-text kitchen prep area (Fourier: "Kitchen stations") — see MenuItem.station's javadoc. */
        String station
) {
    public static MenuItemDto from(MenuItem item, boolean hasRecipe) {
        var category = item.getCategory();
        Product product = item.getProduct();
        ProductDto productDto = product != null ? ProductDto.from(product) : null;
        BigDecimal effective = item.getPriceOverride() != null ? item.getPriceOverride()
                : (product != null ? product.getUnitPrice() : BigDecimal.ZERO);
        return new MenuItemDto(
                item.getId(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                productDto,
                item.getSortOrder(),
                item.isActive(),
                item.getPriceOverride(),
                effective,
                hasRecipe,
                item.getStation()
        );
    }
}
