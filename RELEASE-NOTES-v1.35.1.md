# Release 1.35.1 - Online Store: clearer "Products on the shop" list

The products list in Shop settings had become a 12-column table that overflowed and crowded its columns now that the four row tags were added. Each product is now a **card**: name, normal price, stock and a "No picture yet" note on top with its Save / Remove buttons; below, the title, online price and order fields in a grid that wraps to the screen width; below that, the checkboxes **Shown, Featured, Best seller, Offer, New** with their words next to them.

Frontend only (`store-setup.component.ts`); no new texts, no migration. Strict type-check passes; not run in a browser.
