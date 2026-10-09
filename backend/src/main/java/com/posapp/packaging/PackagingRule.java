package com.posapp.packaging;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "I want to add in the POS: Product Packaging &amp; Breakdown / Unit Conversion.
 * Whiskey Breakdown &amp; Beer Breakdown (in the bar a bottle of whiskey can be splited into shot.
 * the same a caisse of beer can be sold in bottle)." The saved "recipe" — deliberately generic
 * (any parent {@link Product} breaks down into any child {@link Product} at a fixed ratio) rather
 * than two hardcoded "whiskey"/"beer" feature types, since those are just this app's first two
 * examples of the same one-parent-to-N-children shape. {@code unitsPerParent} is how many whole
 * units of {@code childProduct} one single unit of {@code parentProduct} yields when broken down —
 * e.g. parent "Whiskey 750ml bottle", child "Whiskey shot", unitsPerParent 25; parent "Beer case
 * (24)", child "Beer bottle", unitsPerParent 24.
 *
 * <p>Same "configuration vs. execution" split {@link com.posapp.manufacturing.Bom} already has
 * from {@code ManufacturingOrderService#produce}: a rule is just the recipe and is never gated by
 * {@code StoreSettings.productBreakdownEnabled} — only {@link PackagingService#breakdown}, the
 * actual stock-moving action, is. {@code active} is "toggle, don't delete" (same convention as
 * {@code Bom.active}/{@code Subscription.active}) — a paused rule stays visible on its own past
 * {@link PackagingBreakdown} history, it just can't be used for a new breakdown until resumed.
 *
 * <p>More than one rule can share the same {@code parentProduct} (e.g. a whiskey bottle broken
 * down into shots AND, separately, into half-bottles) — only the exact same (parent, child) pair
 * is rejected as a duplicate, see {@code PackagingService#applyRequest}.
 */
@Getter
@Setter
@Entity
public class PackagingRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product parentProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product childProduct;

    /** How many whole units of childProduct one unit of parentProduct breaks down into — see class javadoc. */
    private int unitsPerParent;

    /** Purely informational, e.g. "Count shots carefully — spillage is normal" — same role as Bom having no equivalent free-text field but PackagingRule needing one since there's no routing/BOM-line structure to carry notes instead. */
    private String notes;

    private boolean active = true;
}
