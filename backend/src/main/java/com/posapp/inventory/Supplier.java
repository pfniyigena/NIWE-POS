package com.posapp.inventory;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Task: "Inventory depth ... A complete POS usually adds purchase orders and supplier records."
 * A supplier a {@link PurchaseOrder} is placed with — deliberately a flat, small record (no
 * multi-address/multi-contact modeling) since nothing in this app needs more than "who do we call
 * to reorder this." {@code active} lets an admin retire a supplier from the "new purchase order"
 * picker without losing its name off every historical PO that already references it (same
 * soft-delete convention as {@code Product.active}); a hard delete is still allowed, but only
 * while no PurchaseOrder references this supplier yet — see {@code SupplierService.delete}.
 */
@Getter
@Setter
@Entity
public class Supplier extends BaseEntity {

    private String name;

    /** Nullable — a supplier's own point of contact, not required to save the record. */
    private String contactName;

    private String phone;

    private String email;

    /** VAT/tax registration number for this supplier (Task: "add TAX ID on supplier") — same purely-informational treatment as Customer.taxId; nothing in purchasing math reads this field. Nullable — most suppliers won't need one recorded. */
    private String taxId;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String address;

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    private String notes;

    private boolean active = true;
}
