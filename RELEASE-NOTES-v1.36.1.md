# Release 1.36.1 - Online Store: all four product rows slide

Best-selling products, Special offers and discounts, and New arrivals now move left and right like Featured products: a rail with arrow buttons (shown when a row has more than 4 products), finger/trackpad scrolling, and a slow automatic movement back to the start after the last product. The rows move in turn (one every 1.2 seconds), so they do not all jump together; a row stops while it is pointed at, touched or focused, and when the browser tab is hidden. Visitors with "reduce motion" set get no automatic movement.

Frontend only (`shop.component.ts`); no new texts, no migration. Strict type-check passes; not run in a browser.
