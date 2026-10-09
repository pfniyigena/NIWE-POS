package com.posapp.branch;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * Fourier: "How business branches can be introduced?" — a branch is a trading location/outlet of
 * the business (e.g. "Kampala Road", "Entebbe"), the level above warehouses: a branch owns one or
 * more {@code Warehouse}s (and, through a register's warehouse, its tills). Documents (sales,
 * purchases, expenses) snapshot the branch they belong to as a plain {@code branchId}, so reports
 * can be filtered per branch.
 *
 * <p>Exactly one branch is the {@link #headquarters} ("Head Office", seeded on first startup).
 * It is what every legacy record — anything created before branches existed, whose {@code
 * branch_id} column is null — implicitly belongs to, so a single-branch shop never has to do
 * anything and sees no UI change. Like {@code Warehouse.primary} it is never admin-settable and
 * can't be deactivated.
 *
 * <p>The contact/receipt, numbering-prefix and tax/EFRIS identity fields are stored now; using
 * them on printed documents, document numbers and fiscal submissions is a later step.
 */
@Getter
@Setter
@Entity
public class Branch extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, unique = true)
    private String name;

    private String address;
    private String phone;
    private String email;

    /** Free text printed at the top of this branch's receipts (later step). */
    @Column(name = "receipt_header", columnDefinition = "TEXT")
    private String receiptHeader;

    /** Branch-level taxpayer identity (TIN etc.); null = use the business-wide one. */
    @Column(name = "tax_id")
    private String taxId;

    /** Branch code registered with the fiscal authority (EFRIS); null = use the business-wide one. */
    @Column(name = "efris_branch_code")
    private String efrisBranchCode;

    /** Prepended to document numbers (e.g. "KLA-"); null = no prefix. Not yet applied. */
    @Column(name = "document_prefix")
    private String documentPrefix;

    private boolean active = true;

    /** See class javadoc. Explicit column name: avoids any reserved-word surprises, same reason as {@code Warehouse.primary}. */
    @Column(name = "is_headquarters")
    private boolean headquarters = false;
}
