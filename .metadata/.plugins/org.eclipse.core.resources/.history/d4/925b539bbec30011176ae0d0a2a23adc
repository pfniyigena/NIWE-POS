package com.posapp.catalog.dto;

/** One product that is low on stock within a single warehouse — see CatalogService#listLowStockInWarehouse. {@code quantity} is that warehouse's sellable quantity, not Product.stockQuantity (which only ever reflects the primary warehouse's sales location). */
public record WarehouseLowStockRow(Long productId, String sku, String productName, int quantity) {
}
