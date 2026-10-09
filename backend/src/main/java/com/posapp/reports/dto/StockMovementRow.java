package com.posapp.reports.dto;

import java.time.Instant;

/**
 * Task 1: "Inventory Report" — "Stock movement history" half of the user's "Both" answer. One row
 * per dated event that changed a product's stock quantity, merged from every structured source of
 * such events in the app (see ReportService.stockMovementHistory) — as of the Restaurant module's
 * "Restaurants need a dedicated wastage module", that is twelve of: a {@code StockAdjustment} (type
 * {@code "Adjustment"} — manual corrections/write-offs), a {@code StockReceipt} (type {@code
 * "Purchase receipt"} — goods received against a purchase order), an {@code OrderLine} from a
 * completed POS sale (type {@code "POS sale"}), a {@code SalesReturnLine} from a POS return (type
 * {@code "POS return"}), a {@code SalesOrderFulfillment} (type {@code "Sale order fulfillment"}),
 * a {@code SalesOrderReturnLine} (type {@code "Sale order return"}), a {@code PurchaseReturnLine}
 * (type {@code "Purchase return"}), a {@code ManufacturingOrderRun}'s own finished-good output
 * (type {@code "Manufacturing output"}), a {@code ManufacturingOrderConsumption} (type {@code
 * "Manufacturing consumption"} — one row per component a production run consumed), a checked-out
 * {@code RestaurantOrder}'s own non-recipe line (type {@code "Restaurant sale"}), a {@code
 * RestaurantOrderConsumption} (type {@code "Restaurant ingredient consumption"} — one row per
 * ingredient a recipe-backed restaurant line consumed at checkout), and a {@code
 * RestaurantWastage} entry (type {@code "Restaurant wastage"} — a burnt batch, a dropped plate, a
 * spoiled ingredient; only the stock-tracked-product subset shows up here — see {@code
 * ReportService.restaurantWastage} for the complete wastage log including untracked/service
 * items). Earlier
 * revisions of this javadoc claimed sales and returns never touch {@code Product.stockQuantity} —
 * that was already false by the time it was written (checkout has always decremented stock; POS
 * returns have always incremented it back) and is corrected here now that every one of those
 * events is actually reported. {@code quantityDelta} is signed — positive wherever stock increased
 * (a receipt, a positive adjustment, a return of any kind, a manufacturing output) and negative
 * wherever it decreased (a write-off/loss adjustment, a POS sale, a sale order fulfillment, a
 * manufacturing consumption, a restaurant sale/ingredient consumption/wastage) — so a reader can
 * sum a column of these and land on the same number
 * {@link StockValuationRow#stockQuantity()} would show today, modulo entries outside the chosen
 * date range. {@code stockBefore}/{@code stockAfter} (Task: "add the previous quantity and current
 * quantity after transaction") are that same product's stock immediately before/after this one
 * event, snapshotted at the time each happened — not recomputed from today's stock, so this row
 * still reads correctly after later events move the product's stock again. A row for any of these
 * event types is simply absent for any line/run whose product wasn't stock-tracked at the time
 * (see {@code Product.trackStock}), had no product at all (nothing to snapshot against), or was
 * recorded before its type's snapshot fields existed.
 */
public record StockMovementRow(
        Instant occurredAt,
        /** "Adjustment", "Purchase receipt", "POS sale", "POS return", "Sale order fulfillment", "Sale order return", "Purchase return", "Manufacturing output", "Manufacturing consumption", "Restaurant sale", "Restaurant ingredient consumption", or "Restaurant wastage" — see this record's own javadoc. */
        String type,
        String sku,
        String productName,
        String categoryName,
        int quantityDelta,
        int stockBefore,
        int stockAfter,
        /** The adjustment's reason code, the purchase order's order number, the sale's invoice number, or the relevant return/order number — whichever this row's {@code type} is. */
        String reference,
        /** Free-text note — an adjustment's own note, or a return's reason; null for a receipt/sale/fulfillment row. */
        String note,
        /** Which warehouse the movement happened in; the Main warehouse's name for a row whose source recorded none. */
        String warehouseName,
        /** The product's id; null when the source line had no product. Lets other reports (stock aging) fold movements per product. */
        Long productId
) {
}
