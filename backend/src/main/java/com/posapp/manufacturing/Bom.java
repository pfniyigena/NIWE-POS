package com.posapp.manufacturing;

import com.posapp.catalog.Product;
import com.posapp.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manufacturing Module (Fourier: "Add Manufacturing Module", first item on his own list: "Bill of
 * Materials"). The recipe for one finished-good {@link Product}: a list of {@link BomLine}
 * components and, per component, how many units it takes to make exactly ONE unit of {@code
 * product} — see {@link BomLine#getQuantityPerUnit()}'s javadoc for why quantities are always
 * per-single-unit rather than per-batch.
 *
 * <p>{@code routing} is optional (see {@link Routing}'s javadoc) — a Bom without one still works
 * for the material-consumption/finished-goods side of a Manufacturing Order, it just has no
 * operation-time estimate attached.
 *
 * <p>A product can have more than one Bom (e.g. a "Standard" recipe and an alternate one using a
 * substitute component) — {@code active} marks which one is offered by default when creating a
 * Manufacturing Order for that product; an inactive Bom stays visible on any Manufacturing Order
 * that already referenced it, same "retire, don't delete" convention as every other lookup entity
 * in this app. Deliberately single-level: a component is always a plain {@link Product}, never
 * another product's own Bom (Fourier wasn't asked and this keeps cost roll-up and stock
 * consumption a one-pass calculation with no cycle-detection needed — see BomService's javadoc for
 * where to extend this if sub-assemblies are ever wanted).
 */
@Getter
@Setter
@Entity
public class Bom extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    /** Auto-assigned at creation, same "BOM-" + millis shape as SaleOrder.invoiceNumber and friends — stable even if the product is later renamed. */
    private String code;

    /** A short label distinguishing this Bom from another one on the same product (e.g. "Standard", "Alt — no walnut") — purely informational. */
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Routing routing;

    private boolean active = true;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomLine> lines = new ArrayList<>();
}
